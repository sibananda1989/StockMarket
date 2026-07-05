package org.example.service.institutional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.BulkDealDTO;
import org.example.entity.BulkDeal;
import org.example.entity.Stock;
import org.example.repository.BulkDealRepository;
import org.example.repository.StockRepository;
import org.example.service.ApiRateLimiter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BulkDealService {

    private static final String NSE_BULK_DEALS_URL =
            "https://www.nseindia.com/api/historicalOR/bulk-block-short-deals";
    private static final String NSE_CURRENT_BULK_URL =
            "https://archives.nseindia.com/content/equities/bulk.csv";
    private static final long NSE_RATE_LIMIT_MS = 2000L;
    private static final DateTimeFormatter NSE_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter NSE_CSV_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    private final BulkDealRepository bulkDealRepository;
    private final StockRepository stockRepository;
    private final ApiRateLimiter rateLimiter;
    private final NseSessionManager sessionManager;
    private final ClientClassifier clientClassifier;
    private final ObjectMapper objectMapper;

    public BulkDealService(BulkDealRepository bulkDealRepository,
                           StockRepository stockRepository,
                           ApiRateLimiter rateLimiter,
                           NseSessionManager sessionManager,
                           ClientClassifier clientClassifier,
                           ObjectMapper objectMapper) {
        this.bulkDealRepository = bulkDealRepository;
        this.stockRepository = stockRepository;
        this.rateLimiter = rateLimiter;
        this.sessionManager = sessionManager;
        this.clientClassifier = clientClassifier;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches bulk deals for the specified date range from NSE.
     * Evicts cached institutional scores so they reflect fresh deal data.
     */
    @CacheEvict(value = "institutionalScores", allEntries = true)
    @Transactional
    public int fetchBulkDeals(LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null) to = LocalDate.now();

        log.info("Fetching bulk deals from {} to {}", from, to);
        sessionManager.ensureSession();

        int savedCount = 0;
        try {
            rateLimiter.acquire("nse-bulk-deals", NSE_RATE_LIMIT_MS);

            String url = NSE_BULK_DEALS_URL + "?optionType=bulk_deals&from="
                    + from.format(NSE_DATE_FMT) + "&to=" + to.format(NSE_DATE_FMT);

            HttpRequest request = sessionManager.createRequest(url).build();
            HttpResponse<String> response = sessionManager.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("NSE bulk deals API returned HTTP {}", response.statusCode());
                return 0;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.isArray()) {
                for (JsonNode node : root) {
                    try {
                        if (saveBulkDealFromJson(node)) savedCount++;
                    } catch (Exception e) {
                        log.debug("Skipping bulk deal entry: {}", e.getMessage());
                    }
                }
            }
            log.info("Saved {} new bulk deals", savedCount);
        } catch (Exception e) {
            log.warn("Failed to fetch bulk deals: {}", e.getMessage());
        }
        return savedCount;
    }

    /**
     * Fetches only today's bulk deals from the CSV endpoint.
     * Evicts cached institutional scores so they reflect fresh deal data.
     */
    @CacheEvict(value = "institutionalScores", allEntries = true)
    @Transactional
    public int fetchTodayBulkDeals() {
        log.info("Fetching today's bulk deals from NSE archive");
        int savedCount = 0;
        try {
            rateLimiter.acquire("nse-bulk-deals-today", 1000L);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(NSE_CURRENT_BULK_URL))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = sessionManager.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return 0;
            }

            String body = response.body();
            String[] lines = body.split("\n");
            for (int i = 1; i < lines.length; i++) { // Skip header row
                String line = lines[i].trim();
                if (line.isBlank()) continue;
                try {
                    if (saveBulkDealFromCsv(line)) savedCount++;
                } catch (Exception e) {
                    log.debug("Skipping CSV line: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch today's bulk deals: {}", e.getMessage());
        }
        return savedCount;
    }

    private boolean saveBulkDealFromJson(JsonNode node) {
        String symbol = node.has("BD_SYMBOL") ? node.get("BD_SYMBOL").asText() : null;
        if (symbol == null || symbol.isBlank()) return false;

        Stock stock = stockRepository.findBySymbol(symbol).orElse(null);
        if (stock == null) return false;

        LocalDate dealDate = parseNseDate(node.has("BD_DT_DATE") ? node.get("BD_DT_DATE").asText() : null);
        String clientName = node.has("BD_CLIENT_NAME") ? node.get("BD_CLIENT_NAME").asText() : null;
        String buySell = node.has("BD_BUY_SELL") ? node.get("BD_BUY_SELL").asText() : null;
        long qty = node.has("BD_QTY_TRD") ? node.get("BD_QTY_TRD").asLong() : 0;
        BigDecimal price = node.has("BD_TP_WATP") && !node.get("BD_TP_WATP").isNull()
                ? BigDecimal.valueOf(node.get("BD_TP_WATP").asDouble()) : null;
        String remarks = node.has("BD_REMARKS") && !node.get("BD_REMARKS").isNull()
                ? node.get("BD_REMARKS").asText() : null;

        if (dealDate == null || clientName == null || buySell == null) return false;

        // Check for duplicates
        if (bulkDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                stock.getId(), dealDate, clientName, buySell)) {
            return false;
        }

        BulkDeal deal = new BulkDeal();
        deal.setStock(stock);
        deal.setDealDate(dealDate);
        deal.setClientName(clientName);
        deal.setBuySell(buySell);
        deal.setQuantity(qty);
        deal.setTradePrice(price);
        deal.setRemarks(remarks);

        // Classify client
        String category = clientClassifier.classify(clientName);
        deal.setClientCategory(category);
        deal.setIsInstitutional(clientClassifier.isInstitutional(clientName));

        bulkDealRepository.save(deal);
        return true;
    }

    private boolean saveBulkDealFromCsv(String csvLine) {
        String[] fields = csvLine.split(",");
        if (fields.length < 6) return false;

        String symbol = fields[0].trim();
        String clientName = fields[2].trim();
        String buySell = fields[3].trim();
        if (symbol.isBlank() || clientName.isBlank()) return false;

        Stock stock = stockRepository.findBySymbol(symbol).orElse(null);
        if (stock == null) return false;

        LocalDate dealDate = LocalDate.now(); // CSV is for today
        long qty = Long.parseLong(fields[4].trim());
        BigDecimal price = new BigDecimal(fields[5].trim());

        if (bulkDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                stock.getId(), dealDate, clientName, buySell)) {
            return false;
        }

        BulkDeal deal = new BulkDeal();
        deal.setStock(stock);
        deal.setDealDate(dealDate);
        deal.setClientName(clientName);
        deal.setBuySell(buySell);
        deal.setQuantity(qty);
        deal.setTradePrice(price);
        deal.setRemarks(fields.length > 6 ? fields[6].trim() : null);

        String category = clientClassifier.classify(clientName);
        deal.setClientCategory(category);
        deal.setIsInstitutional(clientClassifier.isInstitutional(clientName));

        bulkDealRepository.save(deal);
        return true;
    }

    private LocalDate parseNseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
        } catch (Exception e) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    // ---- Query methods ----

    public List<BulkDealDTO> getDealsForStock(Long stockId) {
        return bulkDealRepository.findByStockIdOrderByDealDateDesc(stockId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BulkDealDTO> getDealsInDateRange(LocalDate from, LocalDate to) {
        return bulkDealRepository.findByDealDateBetweenOrderByDealDateDesc(from, to).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BulkDealDTO> getInstitutionalBuysSince(Long stockId, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        return bulkDealRepository.findInstitutionalBuysSince(stockId, since).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public int countInstitutionalBuysSince(Long stockId, int days) {
        return bulkDealRepository.countInstitutionalBuysSince(stockId, LocalDate.now().minusDays(days));
    }

    public List<BulkDealDTO> getTopInstitutionalDeals(int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        return bulkDealRepository.findTopInstitutionalDealsByValue(since).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public boolean hasRecentBulkDeal(Long stockId, int days) {
        return countInstitutionalBuysSince(stockId, days) > 0;
    }

    private BulkDealDTO toDTO(BulkDeal deal) {
        return BulkDealDTO.builder()
                .id(deal.getId())
                .stockId(deal.getStock().getId())
                .symbol(deal.getStock().getSymbol())
                .name(deal.getStock().getName())
                .dealDate(deal.getDealDate())
                .clientName(deal.getClientName())
                .buySell(deal.getBuySell())
                .quantity(deal.getQuantity())
                .tradePrice(deal.getTradePrice())
                .dealValue(deal.getDealValue())
                .clientCategory(deal.getClientCategory())
                .isInstitutional(deal.getIsInstitutional())
                .remarks(deal.getRemarks())
                .build();
    }
}
