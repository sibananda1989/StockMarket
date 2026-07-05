package org.example.service.institutional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.BlockDealDTO;
import org.example.entity.BlockDeal;
import org.example.entity.Stock;
import org.example.repository.BlockDealRepository;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BlockDealService {

    private static final String NSE_BLOCK_DEALS_URL =
            "https://www.nseindia.com/api/historicalOR/bulk-block-short-deals";
    private static final String NSE_CURRENT_BLOCK_URL =
            "https://archives.nseindia.com/content/equities/block.csv";
    private static final long NSE_RATE_LIMIT_MS = 2000L;
    private static final DateTimeFormatter NSE_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final BlockDealRepository blockDealRepository;
    private final StockRepository stockRepository;
    private final ApiRateLimiter rateLimiter;
    private final NseSessionManager sessionManager;
    private final ClientClassifier clientClassifier;
    private final ObjectMapper objectMapper;

    public BlockDealService(BlockDealRepository blockDealRepository,
                            StockRepository stockRepository,
                            ApiRateLimiter rateLimiter,
                            NseSessionManager sessionManager,
                            ClientClassifier clientClassifier,
                            ObjectMapper objectMapper) {
        this.blockDealRepository = blockDealRepository;
        this.stockRepository = stockRepository;
        this.rateLimiter = rateLimiter;
        this.sessionManager = sessionManager;
        this.clientClassifier = clientClassifier;
        this.objectMapper = objectMapper;
    }

    @CacheEvict(value = "institutionalScores", allEntries = true)
    @Transactional
    public int fetchBlockDeals(LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null) to = LocalDate.now();

        log.info("Fetching block deals from {} to {}", from, to);
        sessionManager.ensureSession();

        int savedCount = 0;
        try {
            rateLimiter.acquire("nse-block-deals", NSE_RATE_LIMIT_MS);

            String url = NSE_BLOCK_DEALS_URL + "?optionType=block_deals&from="
                    + from.format(NSE_DATE_FMT) + "&to=" + to.format(NSE_DATE_FMT);

            HttpRequest request = sessionManager.createRequest(url).build();
            HttpResponse<String> response = sessionManager.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("NSE block deals API returned HTTP {}", response.statusCode());
                return 0;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.isArray()) {
                for (JsonNode node : root) {
                    try {
                        if (saveBlockDealFromJson(node)) savedCount++;
                    } catch (Exception e) {
                        log.debug("Skipping block deal entry: {}", e.getMessage());
                    }
                }
            }
            log.info("Saved {} new block deals", savedCount);
        } catch (Exception e) {
            log.warn("Failed to fetch block deals: {}", e.getMessage());
        }
        return savedCount;
    }

    @CacheEvict(value = "institutionalScores", allEntries = true)
    @Transactional
    public int fetchTodayBlockDeals() {
        log.info("Fetching today's block deals from NSE archive");
        int savedCount = 0;
        try {
            rateLimiter.acquire("nse-block-deals-today", 1000L);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(NSE_CURRENT_BLOCK_URL))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = sessionManager.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) return 0;

            String body = response.body();
            String[] lines = body.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isBlank()) continue;
                try {
                    if (saveBlockDealFromCsv(line)) savedCount++;
                } catch (Exception e) {
                    log.debug("Skipping CSV line: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch today's block deals: {}", e.getMessage());
        }
        return savedCount;
    }

    private boolean saveBlockDealFromJson(JsonNode node) {
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

        if (blockDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                stock.getId(), dealDate, clientName, buySell)) {
            return false;
        }

        BlockDeal deal = new BlockDeal();
        deal.setStock(stock);
        deal.setDealDate(dealDate);
        deal.setClientName(clientName);
        deal.setBuySell(buySell);
        deal.setQuantity(qty);
        deal.setTradePrice(price);
        deal.setRemarks(remarks);

        String category = clientClassifier.classify(clientName);
        deal.setClientCategory(category);
        deal.setIsInstitutional(clientClassifier.isInstitutional(clientName));

        blockDealRepository.save(deal);
        return true;
    }

    private boolean saveBlockDealFromCsv(String csvLine) {
        String[] fields = csvLine.split(",");
        if (fields.length < 6) return false;

        String symbol = fields[0].trim();
        String clientName = fields[2].trim();
        String buySell = fields[3].trim();
        if (symbol.isBlank() || clientName.isBlank()) return false;

        Stock stock = stockRepository.findBySymbol(symbol).orElse(null);
        if (stock == null) return false;

        LocalDate dealDate = LocalDate.now();
        long qty = Long.parseLong(fields[4].trim());
        BigDecimal price = new BigDecimal(fields[5].trim());

        if (blockDealRepository.existsByStockIdAndDealDateAndClientNameAndBuySell(
                stock.getId(), dealDate, clientName, buySell)) {
            return false;
        }

        BlockDeal deal = new BlockDeal();
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

        blockDealRepository.save(deal);
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

    public List<BlockDealDTO> getDealsForStock(Long stockId) {
        return blockDealRepository.findByStockIdOrderByDealDateDesc(stockId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BlockDealDTO> getDealsInDateRange(LocalDate from, LocalDate to) {
        return blockDealRepository.findByDealDateBetweenOrderByDealDateDesc(from, to).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BlockDealDTO> getInstitutionalBuysSince(Long stockId, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        return blockDealRepository.findInstitutionalBuysSince(stockId, since).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public int countInstitutionalBuysSince(Long stockId, int days) {
        return blockDealRepository.countInstitutionalBuysSince(stockId, LocalDate.now().minusDays(days));
    }

    public boolean hasRecentBlockDeal(Long stockId, int days) {
        return countInstitutionalBuysSince(stockId, days) > 0;
    }

    private BlockDealDTO toDTO(BlockDeal deal) {
        return BlockDealDTO.builder()
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
