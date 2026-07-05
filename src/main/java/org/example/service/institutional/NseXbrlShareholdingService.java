package org.example.service.institutional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.InstitutionalHolding;
import org.example.entity.Stock;
import org.example.repository.InstitutionalHoldingRepository;
import org.example.repository.StockRepository;
import org.example.service.ApiRateLimiter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/**
 * Parses NSE XBRL shareholding filing documents to extract detailed
 * FII (Foreign Portfolio Investor), Mutual Fund, Insurance, and DII
 * holding percentages per stock per quarter.
 * <p>
 * The NSE corporate-share-holdings-master API returns an {@code xbrl} URL
 * pointing to the detailed XML filing on nsearchives.nseindia.com.
 * This service downloads and parses that XBRL to populate the
 * FII/DII/MF/Insurance fields in {@link InstitutionalHolding}.
 * <p>
 * XBRL Taxonomy: BSE Shareholding Pattern (in-bse-shp)
 * Namespace: http://www.bseindia.com/xbrl/shp/2025-10-31/in-bse-shp
 * Dimension: CategoryOfShareholdersAxis
 * Members (extracted):
 * - ForeignPortfolioInvestorMember → FII
 * - MutualFundsMember → Mutual Fund
 * - InsuranceCompaniesMember → Insurance
 * - BanksMember → DII (banks portion)
 * - AlternateInvestmentFundsMember → DII
 * - ProvidentFundsOrPensionFundsMember → DII
 * - NBFCsRegisteredWithRBIMember → Other
 * - ForeignDirectInvestmentMember → Other
 * - IndividualsOrHinduUndividedFamilyMember → Retail
 * - OtherIndianShareholdersMember → Other
 */
@Service
@Slf4j
public class NseXbrlShareholdingService {

    private static final long NSE_RATE_LIMIT_MS = 1500L;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter XBRL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter XBRL_PARSE_FMT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd-MMM-yyyy")
            .toFormatter(Locale.ENGLISH);

    private final InstitutionalHoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final ApiRateLimiter rateLimiter;
    private final NseSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public NseXbrlShareholdingService(InstitutionalHoldingRepository holdingRepository,
                                      StockRepository stockRepository,
                                      ApiRateLimiter rateLimiter,
                                      NseSessionManager sessionManager,
                                      ObjectMapper objectMapper) {
        this.holdingRepository = holdingRepository;
        this.stockRepository = stockRepository;
        this.rateLimiter = rateLimiter;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    /**
     * Fetches XBRL shareholding data for ALL stocks that have a ticker in NSE.
     * Iterates known stocks, fetches the NSE master list, then parses XBRL.
     * Evicts cached institutional scores so they reflect the fresh XBRL data.
     */
    @CacheEvict(value = "institutionalScores", allEntries = true)
    @Transactional
    public int fetchForAllStocks() {
        List<Stock> stocks = stockRepository.findAll();
        log.info("XBRL: Fetching detailed shareholding for {} stocks", stocks.size());

        int successCount = 0;
        int errorCount = 0;

        for (Stock stock : stocks) {
            try {
                rateLimiter.acquire("nse-xbrl", NSE_RATE_LIMIT_MS);
                boolean ok = fetchAndParseForStock(stock);
                if (ok) successCount++;
            } catch (Exception e) {
                errorCount++;
                if (errorCount > 10) {
                    log.warn("XBRL: Too many errors ({}), stopping", errorCount);
                    break;
                }
                log.debug("XBRL error for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }

        log.info("XBRL: Updated {} stocks with detailed data ({} errors)", successCount, errorCount);
        return successCount;
    }

    /**
     * Fetches XBRL for a single stock by its database ID.
     * Evicts cached institutional scores so they reflect fresh data.
     */
    @CacheEvict(value = "institutionalScores", allEntries = true)
    @Transactional
    public boolean fetchForStockId(Long stockId) {
        Stock stock = stockRepository.findById(stockId).orElse(null);
        if (stock == null) {
            log.warn("XBRL: Stock {} not found", stockId);
            return false;
        }
        return fetchAndParseForStock(stock);
    }

    // ─── Core: Fetch NSE Master + Parse XBRL ───────────────────────────────

    /**
     * For a given stock: fetch the NSE share-holdings-master JSON (to get XBRL URL),
     * download and parse each quarter's XBRL, update InstitutionalHolding records.
     * Protected by a circuit breaker to stop hammering NSE when it is degraded.
     */
    @CircuitBreaker(name = "nseXbrl", fallbackMethod = "xbrlFallback")
    boolean fetchAndParseForStock(Stock stock) {
        try {
            // 1. Fetch NSE master JSON to get XBRL URLs
            String masterJson = fetchNseMasterJson(stock.getSymbol());
            if (masterJson == null) return false;

            // 2. Parse the JSON to extract XBRL URLs per quarter
            List<XbrlRecord> records = parseMasterJson(masterJson);
            if (records.isEmpty()) return false;

            // 3. Download and parse each XBRL filing
            boolean anyUpdated = false;
            for (XbrlRecord rec : records) {
                try {
                    rateLimiter.acquire("nse-xbrl-download", NSE_RATE_LIMIT_MS);
                    boolean ok = downloadAndParseXbrl(stock, rec);
                    if (ok) anyUpdated = true;
                } catch (Exception e) {
                    log.debug("XBRL parse error for {} quarter {}: {}",
                            stock.getSymbol(), rec.quarterEnd, e.getMessage());
                }
            }

            // 4. Recompute QoQ changes after updates
            if (anyUpdated) {
                recomputeQoqChanges(stock.getId());
            }

            return anyUpdated;
        } catch (Exception e) {
            log.debug("XBRL failed for {}: {}", stock.getSymbol(), e.getMessage());
            return false;
        }
    }

    /**
     * Fallback for when the XBRL circuit breaker is open.
     */
    private boolean xbrlFallback(Stock stock, Exception e) {
        log.warn("XBRL circuit breaker open for {}, skipping: {}", stock.getSymbol(), e.getMessage());
        return false;
    }

    // ─── NSE Master JSON ────────────────────────────────────────────────────

    String fetchNseMasterJson(String symbol) {
        try {
            String url = "https://www.nseindia.com/api/corporate-share-holdings-master" +
                    "?index=equities&symbol=" + symbol;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Referer", "https://www.nseindia.com/get-quotes/equity?symbol=" + symbol)
                    .build();
            HttpResponse<String> resp = sessionManager.getHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                return resp.body();
            }
        } catch (Exception e) {
            log.debug("NSE master fetch error for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    /**
     * Extracts XBRL URLs + quarter-end dates from the NSE master JSON array.
     * Each element has: date, xbrl, pr_and_prgrp, public_val, employeeTrusts
     */
    List<XbrlRecord> parseMasterJson(String json) {
        List<XbrlRecord> records = new ArrayList<>();
        try {
            List<Map<String, Object>> rawRecords = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> raw : rawRecords) {
                XbrlRecord rec = new XbrlRecord();
                rec.dateStr = (String) raw.get("date");
                rec.xbrlUrl = (String) raw.get("xbrl");
                rec.promoterPct = toBigDecimal(raw.get("pr_and_prgrp"));
                rec.publicPct = toBigDecimal(raw.get("public_val"));
                rec.employeeTrusts = toBigDecimal(raw.get("employeeTrusts"));

                if (rec.dateStr != null && rec.xbrlUrl != null && !rec.xbrlUrl.isBlank()) {
                    try {
                        rec.quarterEnd = LocalDate.parse(rec.dateStr, XBRL_PARSE_FMT);
                        records.add(rec);
                    } catch (Exception ignored) {}
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("XBRL JSON parse error: {}", e.getMessage());
        }
        records.sort((a, b) -> b.quarterEnd.compareTo(a.quarterEnd));
        return records;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ─── XBRL Download & Parse ──────────────────────────────────────────────

    /**
     * Downloads the XBRL XML and extracts FII/DII/MF/Insurance percentages.
     */
    boolean downloadAndParseXbrl(Stock stock, XbrlRecord rec) {
        try {
            String xml = downloadXbrlXml(rec.xbrlUrl);
            if (xml == null) return false;

            // Parse XBRL to get detailed holdings
            Map<String, BigDecimal> categories = parseXbrlCategories(xml);
            if (categories.isEmpty()) {
                log.debug("XBRL: No category data in filing for {}", stock.getSymbol());
                return false;
            }

            // Map to our fields
            BigDecimal fiiPct = categories.get("FII");
            BigDecimal mfPct = categories.get("MF");
            BigDecimal insurancePct = categories.get("INSURANCE");
            BigDecimal bankPct = categories.get("BANK");
            BigDecimal aifPct = categories.get("AIF");
            BigDecimal pensionPct = categories.get("PENSION");

            // DII = Banks + AIF + Pension (categories that are domestic institutions)
            BigDecimal diiPct = sum(bankPct, aifPct, pensionPct);

            // Update the holding record
            updateDetailedHoldings(stock, rec.quarterEnd, fiiPct, diiPct, mfPct, insurancePct,
                    rec.promoterPct, rec.publicPct);

            return true;
        } catch (Exception e) {
            log.debug("XBRL download/parse failed: {}", e.getMessage());
            return false;
        }
    }

    String downloadXbrlXml(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/xml")
                    .build();
            HttpResponse<String> resp = sessionManager.getHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body();
            }
        } catch (Exception e) {
            log.debug("XBRL download error from {}: {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * Parses an XBRL shareholding document and returns a map of
     * shareholder categories to their percentage holdings.
     * <p>
     * The XBRL uses the BSE Shareholding Pattern taxonomy (in-bse-shp).
     * Each category is represented by an explicit member of the
     * CategoryOfShareholdersAxis dimension, with a numeric value
     * for percentageOfShareholding or sharesHeld fact.
     */
    Map<String, BigDecimal> parseXbrlCategories(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));

        // Find all context elements with explicit member for CategoryOfShareholdersAxis
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        // Get all contexts that have a CategoryOfShareholdersAxis dimension
        NodeList contextNodes = doc.getElementsByTagNameNS("*", "context");
        if (contextNodes.getLength() == 0) {
            // Try without namespace
            contextNodes = doc.getElementsByTagName("xbrli:context");
            if (contextNodes.getLength() == 0) {
                contextNodes = doc.getElementsByTagName("context");
            }
        }

        // Build context → category mapping
        Map<String, String> contextToCategory = new HashMap<>();
        for (int i = 0; i < contextNodes.getLength(); i++) {
            Element ctx = (Element) contextNodes.item(i);
            String ctxId = ctx.getAttribute("id");
            if (ctxId == null || ctxId.isEmpty()) continue;

            // Find explicit member for CategoryOfShareholdersAxis
            NodeList members = ctx.getElementsByTagNameNS("*", "explicitMember");
            for (int j = 0; j < members.getLength(); j++) {
                Element member = (Element) members.item(j);
                String dimension = member.getAttribute("dimension");
                if (dimension != null &&
                        (dimension.contains("CategoryOfShareholdersAxis") ||
                         dimension.contains("CategoryOfShareholders"))) {
                    String value = member.getTextContent().trim();
                    String category = mapCategory(value);
                    if (category != null) {
                        contextToCategory.put(ctxId, category);
                    }
                    break;
                }
            }
        }

        // Find numeric facts that reference these contexts
        NodeList allElements = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element elem = (Element) allElements.item(i);
            String tagName = elem.getLocalName();
            if (tagName == null) continue;

            String contextRef = elem.getAttribute("contextRef");
            if (contextRef == null || contextRef.isEmpty()) continue;

            String category = contextToCategory.get(contextRef);
            if (category == null) continue;

            // Check if this is a percentage fact (we want % holdings)
            boolean isPct = tagName.toLowerCase().contains("percentage") ||
                    tagName.toLowerCase().contains("percnt") ||
                    tagName.toLowerCase().contains("pct");

            String textContent = elem.getTextContent();
            if (textContent == null || textContent.isBlank()) continue;

            try {
                BigDecimal value = new BigDecimal(textContent.trim());

                // Only store percentage facts (e.g., PercentageOfShareholding)
                // Skip shares count, face value, and other non-percentage facts
                if (isPct) {
                    // Store if not already present (first encountered = latest = preferred)
                    if (!result.containsKey(category)) {
                        result.put(category, value);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Convert decimal ratios to percentages
        // XBRL taxonomy expresses percentages as 0-to-1 ratios (e.g., 0.1831 = 18.31%)
        if (!result.isEmpty()) {
            BigDecimal maxVal = result.values().stream()
                    .max(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
            if (maxVal.compareTo(BigDecimal.ONE) <= 0) {
                // All values are decimal ratios → multiply by 100
                result.replaceAll((k, v) -> v.multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP));
            }
        }

        return result;
    }

    /**
     * Maps XBRL category member values to our internal category codes.
     * <p>
     * XBRL member values from NSE use CamelCase (e.g. "ForeignPortfolioInvestorMember").
     * We normalize by uppercasing and stripping separators for robust matching.
     */
    String mapCategory(String memberValue) {
        if (memberValue == null) return null;
        // Normalize: uppercase and strip underscores/hyphens/dots
        // XBRL uses CamelCase like "ForeignPortfolioInvestorMember" → "FOREIGNPORTFOLIOINVESTORMEMBER"
        String v = memberValue.toUpperCase().replace("_", "").replace("-", "").replace(".", "").replace(" ", "");

        // Order: most specific first
        if (v.contains("FOREIGNPORTFOLIOINVESTOR") || v.contains("FPI") ||
            v.contains("FOREIGNPORTFOLIO") || v.contains("FII"))
            return "FII";

        if (v.contains("MUTUALFUND"))
            return "MF";

        if (v.contains("INSURANCE"))
            return "INSURANCE";

        if (v.contains("BANK"))
            return "BANK";

        if (v.contains("ALTERNATEINVESTMENTFUND") || v.contains("AIF") || v.contains("VCFFUND") ||
            v.contains("VCF") || v.contains("VENTURECAPITAL") || v.contains("CATEGORYIII"))
            return "AIF";

        if (v.contains("PROVIDENTFUND") || v.contains("PENSION") || v.contains("GRATUITY") ||
            v.contains("SUPERANNUATION") || v.contains("RETIREMENT"))
            return "PENSION";

        if (v.contains("FOREIGNDIRECT"))
            return "FDI";

        if (v.contains("NBFC") || v.contains("NONBANKING"))
            return "NBFC";

        if (v.contains("HUF") || v.contains("INDIVIDUALS") || v.contains("HINDUUNDIVIDED"))
            return "RETAIL";

        if (v.contains("GOVERNMENT"))
            return "GOVERNMENT";

        if (v.contains("PROMOTER"))
            return "PROMOTER";

        if (v.contains("EMPLOYEE") || v.contains("TRUST") || v.contains("ESOP") ||
            v.contains("ESOS") || v.contains("EMPLOYEES"))
            return "EMPLOYEE";

        if (v.contains("OTHER") || v.contains("OTHERS") || v.contains("UNIDENTIFIED") ||
            v.contains("UNKNOWN") || v.contains("RESIDUAL"))
            return "OTHER";

        return null; // Unmapped
    }

    // ─── Database Update ────────────────────────────────────────────────────

    /**
     * Updates or creates an InstitutionalHolding with the parsed data.
     * Uses the existing NSE data (promoter/public) as base and fills in
     * the detailed institutional categories from XBRL.
     */
    private void updateDetailedHoldings(Stock stock, LocalDate quarterEnd,
                                         BigDecimal fiiPct, BigDecimal diiPct,
                                         BigDecimal mfPct, BigDecimal insurancePct,
                                         BigDecimal promoterPct, BigDecimal publicPct) {
        Optional<InstitutionalHolding> existing =
                holdingRepository.findByStockIdAndQuarterEndDate(stock.getId(), quarterEnd);

        InstitutionalHolding holding;
        boolean isNew = false;
        if (existing.isPresent()) {
            holding = existing.get();
        } else {
            holding = new InstitutionalHolding();
            holding.setStock(stock);
            holding.setQuarterEndDate(quarterEnd);
            isNew = true;
        }

        // Fill in values — only overwrite if the new data is not null
        if (promoterPct != null) holding.setPromoterHoldingPct(promoterPct);
        if (publicPct != null) holding.setPublicHoldingPct(publicPct);
        if (fiiPct != null) holding.setFiiHoldingPct(fiiPct);
        if (diiPct != null) holding.setDiiHoldingPct(diiPct);
        if (mfPct != null) holding.setMutualFundHoldingPct(mfPct);
        if (insurancePct != null) holding.setInsuranceHoldingPct(insurancePct);

        // Mark the data source
        if (fiiPct != null || diiPct != null || mfPct != null) {
            holding.setDataSource("NSE_XBRL");
        } else {
            holding.setDataSource("NSE_API");
        }
        holding.setFetchedDate(LocalDate.now());

        holdingRepository.save(holding);
        log.debug("XBRL: Updated {} for quarter {} (FII={}, DII={}, MF={}, Ins={})",
                stock.getSymbol(), quarterEnd,
                fiiPct != null ? fiiPct + "%" : "N/A",
                diiPct != null ? diiPct + "%" : "N/A",
                mfPct != null ? mfPct + "%" : "N/A",
                insurancePct != null ? insurancePct + "%" : "N/A");
    }

    /**
     * Recomputes QoQ changes for all holding records of a stock.
     */
    private void recomputeQoqChanges(Long stockId) {
        List<InstitutionalHolding> history =
                holdingRepository.findByStockIdOrderByQuarterEndDateDesc(stockId);

        for (int i = 0; i < history.size(); i++) {
            InstitutionalHolding current = history.get(i);
            InstitutionalHolding prev = (i + 1 < history.size()) ? history.get(i + 1) : null;

            if (prev != null) {
                current.setPromoterChangeQoq(safeSubtract(
                        current.getPromoterHoldingPct(), prev.getPromoterHoldingPct()));
                current.setFiiChangeQoq(safeSubtract(
                        current.getFiiHoldingPct(), prev.getFiiHoldingPct()));
                current.setDiiChangeQoq(safeSubtract(
                        current.getDiiHoldingPct(), prev.getDiiHoldingPct()));
                current.setMutualFundChangeQoq(safeSubtract(
                        current.getMutualFundHoldingPct(), prev.getMutualFundHoldingPct()));
            } else {
                current.setPromoterChangeQoq(null);
                current.setFiiChangeQoq(null);
                current.setDiiChangeQoq(null);
                current.setMutualFundChangeQoq(null);
            }
        }

        holdingRepository.saveAll(history);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        return a.subtract(b).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasValue = false;
        for (BigDecimal v : values) {
            if (v != null) {
                total = total.add(v);
                hasValue = true;
            }
        }
        return hasValue ? total : null;
    }

    // ─── Internal Record ────────────────────────────────────────────────────

    static class XbrlRecord {
        String dateStr;
        LocalDate quarterEnd;
        String xbrlUrl;
        BigDecimal promoterPct;
        BigDecimal publicPct;
        BigDecimal employeeTrusts;
    }
}
