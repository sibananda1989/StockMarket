package org.example.service.institutional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.InstitutionalHoldingDTO;
import org.example.entity.InstitutionalHolding;
import org.example.entity.Stock;
import org.example.repository.InstitutionalHoldingRepository;
import org.example.repository.StockRepository;
import org.example.service.ApiRateLimiter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InstitutionalHoldingService {

    private static final String NSE_SHAREHOLDING_URL =
            "https://www.nseindia.com/api/corporate-share-holdings-master";
    private static final long NSE_RATE_LIMIT_MS = 2000L;
    private static final DateTimeFormatter NSE_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    private final InstitutionalHoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final ApiRateLimiter rateLimiter;
    private final NseSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public InstitutionalHoldingService(InstitutionalHoldingRepository holdingRepository,
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

    /**
     * Fetches NSE shareholding data for ALL stocks.
     * Uses the NSE corporate-share-holdings-master API which returns
     * promoter%, public%, and employeeTrusts% per quarter.
     * Evicts cached institutional scores so they are recomputed with fresh data.
     */
    @CacheEvict(value = "institutionalScores", allEntries = true)
    @Transactional
    public int fetchForAllStocks() {
        List<Stock> stocks = stockRepository.findAll();
        log.info("Fetching shareholding data for {} stocks", stocks.size());

        sessionManager.ensureSession();

        int savedCount = 0;
        int errorCount = 0;

        for (Stock stock : stocks) {
            try {
                rateLimiter.acquire("nse-shareholding", NSE_RATE_LIMIT_MS);
                if (fetchForSingleStock(stock)) savedCount++;
            } catch (Exception e) {
                errorCount++;
                if (errorCount > 5) {
                    log.warn("Too many errors ({}) fetching shareholding, stopping", errorCount);
                    break;
                }
                log.debug("Failed to fetch for {}: {}", stock.getSymbol(), e.getMessage());
                // Reset session and retry
                if (e.getMessage() != null && e.getMessage().contains("401")) {
                    sessionManager.refreshSession();
                }
            }
        }

        log.info("Saved shareholding data for {} stocks ({} errors)", savedCount, errorCount);
        return savedCount;
    }

    /**
     * Fetches shareholding data for a single stock from NSE API.
     * The NSE API returns: symbol, date, pr_and_prgrp (promoter%), public_val, employeeTrusts.
     */
    private boolean fetchForSingleStock(Stock stock) {
        try {
            String url = NSE_SHAREHOLDING_URL + "?index=equities&symbol=" + stock.getSymbol();
            HttpRequest request = sessionManager.createRequest(url)
                    .header("Referer", "https://www.nseindia.com/get-quotes/equity?symbol=" + stock.getSymbol())
                    .build();

            HttpResponse<String> response = sessionManager.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.debug("NSE returned HTTP {} for {}", response.statusCode(), stock.getSymbol());
                return false;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root == null || !root.isArray() || root.isEmpty()) {
                return false;
            }

            // Process each quarter record (ordered most recent first)
            List<InstitutionalHolding> records = new ArrayList<>();
            InstitutionalHolding previousHolding = null;

            for (JsonNode node : root) {
                String dateStr = node.has("date") ? node.get("date").asText() : null;
                if (dateStr == null) continue;

                LocalDate quarterDate = LocalDate.parse(dateStr, NSE_DATE_FMT);

                // Check if we already have this quarter's data
                Optional<InstitutionalHolding> existing =
                        holdingRepository.findByStockIdAndQuarterEndDate(stock.getId(), quarterDate);
                if (existing.isPresent()) {
                    previousHolding = existing.get();
                    continue;
                }

                BigDecimal promoterPct = parseBigDecimal(node, "pr_and_prgrp");
                BigDecimal publicPct = parseBigDecimal(node, "public_val");
                BigDecimal employeeTrusts = parseBigDecimal(node, "employeeTrusts");

                InstitutionalHolding holding = new InstitutionalHolding();
                holding.setStock(stock);
                holding.setQuarterEndDate(quarterDate);
                holding.setPromoterHoldingPct(promoterPct);
                holding.setPublicHoldingPct(publicPct);
                holding.setDataSource("NSE_API");
                holding.setFetchedDate(LocalDate.now());

                // For FII/DII/MF we mark as null (not available from NSE)
                holding.setFiiHoldingPct(null);
                holding.setDiiHoldingPct(null);
                holding.setMutualFundHoldingPct(null);
                holding.setInsuranceHoldingPct(null);

                // Compute QoQ changes if we have previous data
                if (previousHolding != null) {
                    holding.setPromoterChangeQoq(safeSubtract(promoterPct, previousHolding.getPromoterHoldingPct()));
                    holding.setFiiChangeQoq(null);  // Not available
                    holding.setDiiChangeQoq(null);
                    holding.setMutualFundChangeQoq(null);
                }

                records.add(holding);
                previousHolding = holding;
            }

            if (!records.isEmpty()) {
                holdingRepository.saveAll(records);
                log.debug("Saved {} quarters for {}", records.size(), stock.getSymbol());
                return true;
            }
            return false;

        } catch (Exception e) {
            log.debug("Error fetching shareholding for {}: {}", stock.getSymbol(), e.getMessage());
            return false;
        }
    }

    /**
     * Fetches shareholding for a single stock by stock ID (public API method).
     * Evicts the 'all' scores cache so the aggregated view stays fresh.
     */
    @CacheEvict(value = "institutionalScores", allEntries = true)
    @Transactional
    public InstitutionalHoldingDTO fetchForStock(Long stockId) {
        Stock stock = stockRepository.findById(stockId).orElse(null);
        if (stock == null) return null;

        fetchForSingleStock(stock);
        return getLatestHolding(stockId);
    }

    /**
     * Returns the latest shareholding record for a stock.
     */
    @Transactional(readOnly = true)
    public InstitutionalHoldingDTO getLatestHolding(Long stockId) {
        Optional<InstitutionalHolding> holding = holdingRepository.findLatestHolding(stockId);
        return holding.map(this::toDTO).orElse(null);
    }

    /**
     * Returns the latest holding for a stock by symbol.
     */
    @Transactional(readOnly = true)
    public InstitutionalHoldingDTO getLatestHoldingBySymbol(String symbol) {
        List<InstitutionalHolding> holdings = holdingRepository.findBySymbolOrderByQuarterEndDateDesc(symbol);
        if (holdings.isEmpty()) return null;
        return toDTO(holdings.get(0));
    }

    /**
     * Returns ALL quarterly records for a stock with QoQ changes computed.
     */
    @Transactional(readOnly = true)
    public List<InstitutionalHoldingDTO> getHoldingHistory(Long stockId) {
        List<InstitutionalHolding> holdings = holdingRepository.findByStockIdOrderByQuarterEndDateDesc(stockId);
        return holdings.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Returns all stocks' latest holdings.
     */
    @Transactional(readOnly = true)
    public List<InstitutionalHoldingDTO> getAllLatestHoldings() {
        List<LocalDate> dates = holdingRepository.findDistinctQuarterEndDates();
        if (dates.isEmpty()) return Collections.emptyList();

        LocalDate latest = dates.get(0);
        List<InstitutionalHolding> holdings = holdingRepository.findAllByQuarterEndDate(latest);

        return holdings.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ---- BSE XBRL Integration Points (Phase 2) ----

    /**
     * Placeholder for BSE XBRL-based detailed shareholding parsing.
     * This will be implemented in Phase 2 to get granular FII/DII/MF data.
     */
    @Transactional
    public int fetchDetailedFromBseXbrl() {
        log.info("BSE XBRL parsing not yet implemented - Phase 2 enhancement");
        // TODO: Implement BSE iXBRL download and parsing
        // 1. For each stock, get BSE scrip code
        // 2. Call BSE API to get filing index
        // 3. Download iXBRL files for recent quarters
        // 4. Parse XBRL using namespace-aware XML parser
        // 5. Extract FII/FPI, MF, Insurance, Bank, DII sub-categories
        // 6. Save separate FII%, DII%, MF% fields
        return 0;
    }

    /**
     * Updates FII/DII/MF holding data from a parsed source.
     */
    @Transactional
    public void updateDetailedHoldings(Long stockId, LocalDate quarterDate,
                                       BigDecimal fiiPct, BigDecimal diiPct,
                                       BigDecimal mfPct, BigDecimal insurancePct) {
        Optional<InstitutionalHolding> existing =
                holdingRepository.findByStockIdAndQuarterEndDate(stockId, quarterDate);

        InstitutionalHolding holding;
        boolean isNew = false;
        if (existing.isPresent()) {
            holding = existing.get();
        } else {
            Stock stock = stockRepository.findById(stockId).orElse(null);
            if (stock == null) return;
            holding = new InstitutionalHolding();
            holding.setStock(stock);
            holding.setQuarterEndDate(quarterDate);
            isNew = true;
        }

        holding.setFiiHoldingPct(fiiPct);
        holding.setDiiHoldingPct(diiPct);
        holding.setMutualFundHoldingPct(mfPct);
        holding.setInsuranceHoldingPct(insurancePct);
        holding.setDataSource("BSE_XBRL");
        holding.setFetchedDate(LocalDate.now());

        // Recompute QoQ changes
        List<InstitutionalHolding> history = holdingRepository.findByStockIdOrderByQuarterEndDateDesc(stockId);
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getQuarterEndDate().equals(quarterDate)) {
                if (i + 1 < history.size()) {
                    InstitutionalHolding prev = history.get(i + 1);
                    holding.setFiiChangeQoq(safeSubtract(fiiPct, prev.getFiiHoldingPct()));
                    holding.setDiiChangeQoq(safeSubtract(diiPct, prev.getDiiHoldingPct()));
                    holding.setMutualFundChangeQoq(safeSubtract(mfPct, prev.getMutualFundHoldingPct()));
                    holding.setPromoterChangeQoq(safeSubtract(holding.getPromoterHoldingPct(), prev.getPromoterHoldingPct()));
                }
                break;
            }
        }

        if (isNew) {
            holdingRepository.save(holding);
        } else {
            holdingRepository.save(holding);
        }
    }

    // ---- Helper methods ----

    private BigDecimal parseBigDecimal(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            try {
                return new BigDecimal(node.get(field).asText());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        return a.subtract(b).setScale(4, RoundingMode.HALF_UP);
    }

    private InstitutionalHoldingDTO toDTO(InstitutionalHolding h) {
        return InstitutionalHoldingDTO.builder()
                .id(h.getId())
                .stockId(h.getStock().getId())
                .symbol(h.getStock().getSymbol())
                .name(h.getStock().getName())
                .quarterEndDate(h.getQuarterEndDate())
                .promoterHoldingPct(h.getPromoterHoldingPct())
                .fiiHoldingPct(h.getFiiHoldingPct())
                .diiHoldingPct(h.getDiiHoldingPct())
                .mutualFundHoldingPct(h.getMutualFundHoldingPct())
                .insuranceHoldingPct(h.getInsuranceHoldingPct())
                .publicHoldingPct(h.getPublicHoldingPct())
                .promoterChangeQoq(h.getPromoterChangeQoq())
                .fiiChangeQoq(h.getFiiChangeQoq())
                .diiChangeQoq(h.getDiiChangeQoq())
                .mutualFundChangeQoq(h.getMutualFundChangeQoq())
                .dataSource(h.getDataSource())
                .filingDate(h.getFilingDate())
                .build();
    }
}
