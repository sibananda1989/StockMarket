package org.example.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.example.dto.*;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.exception.PriceAlreadyExistsException;
import org.example.service.DailyPriceService;
import org.example.service.StockHistoryService;
import org.example.service.StockService;
import org.example.service.TechnicalAnalysisService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Validated
public class StockHistoryController {

    private final StockHistoryService stockHistoryService;
    private final StockService stockService;
    private final DailyPriceService dailyPriceService;
    private final CacheManager cacheManager;
    private final TechnicalAnalysisService technicalAnalysisService;
    private static final Logger log = LoggerFactory.getLogger(StockHistoryController.class);

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<StockSummaryDTO>> getHistory(
            @RequestParam
            @NotBlank(message = "Symbol is required")
            @Pattern(regexp = "^[A-Z0-9.\\-^=]+$", message = "Invalid symbol format")
            String symbol,

            @RequestParam(defaultValue = "30")
            @Min(value = 1,   message = "Days must be at least 1")
            @Max(value = 365, message = "Days cannot exceed 365")
            int days) {

        StockSummaryDTO summary = stockHistoryService.getSummary(symbol, days);
        return ResponseEntity.ok(ApiResponse.success("History fetched successfully", summary));
    }

    @GetMapping("/history/data")
    public ResponseEntity<ApiResponse<List<StockHistoryDTO>>> getHistoryData(
            @RequestParam
            @NotBlank(message = "Symbol is required")
            @Pattern(regexp = "^[A-Z0-9.\\-^=]+$", message = "Invalid symbol format")
            String symbol,

            @RequestParam(defaultValue = "30")
            @Min(value = 1,   message = "Days must be at least 1")
            @Max(value = 365, message = "Days cannot exceed 365")
            int days) {

        List<StockHistoryDTO> history = stockHistoryService.getHistoryData(symbol, days);
        return ResponseEntity.ok(ApiResponse.success("History fetched successfully", history));
    }

    @PostMapping("/cache/clear")
    public ResponseEntity<ApiResponse<String>> clearCache() {
        Cache cache = cacheManager.getCache("stockHistory");
        if (cache != null) {
            cache.clear();
            return ResponseEntity.ok(ApiResponse.success("Cache cleared successfully", "Cache cleared"));
        }
        return ResponseEntity.ok(ApiResponse.success("No cache found", "No cache to clear"));
    }

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<String>> testEndpoint() {
        return ResponseEntity.ok(ApiResponse.success("API is working", "Stock History API is operational"));
    }

    @PostMapping("/history/save")
    public ResponseEntity<ApiResponse<String>> saveHistoryData(@RequestBody SaveHistoryRequest request) {
        try {
            // Get or create stock
            Stock stock = stockService.getOrCreateStock(
                request.getSymbol(),
                request.getName() != null ? request.getName() : request.getSymbol(),
                request.getSector() != null ? request.getSector() : "Other",
                request.getIndustry()
            );

            // Save daily prices
            int savedCount = 0;
            int skippedCount = 0;

            for (StockHistoryDTO priceData : request.getHistory()) {
                DailyPriceDTO priceDto = new DailyPriceDTO();
                priceDto.setStockId(stock.getId());
                priceDto.setPriceDate(priceData.getDate());
                priceDto.setOpeningPrice(BigDecimal.valueOf(priceData.getOpen()));
                priceDto.setHighPrice(BigDecimal.valueOf(priceData.getHigh()));
                priceDto.setLowPrice(BigDecimal.valueOf(priceData.getLow()));
                priceDto.setClosingPrice(BigDecimal.valueOf(priceData.getClose()));
                priceDto.setVolume(priceData.getVolume());

                try {
                    dailyPriceService.saveDailyPrice(priceDto);
                    savedCount++;
                } catch (PriceAlreadyExistsException e) {
                    // Skip duplicates
                    skippedCount++;
                }
            }

            try {
                int filled = technicalAnalysisService.fillIndicatorGapsForStock(stock.getId(), 365);
                log.info("Indicator backfill complete for stock {}: {} records filled", stock.getSymbol(), filled);
            } catch (Exception e) {
                log.error("Indicator backfill failed for stock {}: {}", stock.getSymbol(), e.getMessage(), e);
            }

            return ResponseEntity.ok(ApiResponse.success(
                String.format("Saved %d price entries for %s, skipped %d duplicates", savedCount, stock.getSymbol(), skippedCount),
                "Data saved successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error("Failed to save stock history: " + e.getMessage())
            );
        }
    }

    @PostMapping("/portfolio/sync")
    public ResponseEntity<ApiResponse<PortfolioSyncResultDTO>> syncPortfolioHistory() {
        List<Stock> stocks = stockService.getAllStocks();
        int successCount = 0;
        int failedCount = 0;
        int totalSaved = 0;
        int totalSkipped = 0;
        Map<String, PortfolioSyncResultDTO.StockSyncResult> perStock = new LinkedHashMap<>();

        for (Stock stock : stocks) {
            int saved = 0;
            int skipped = 0;
            String message;
            String status;
            String yahooSymbol = stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol();

            try {
                List<StockHistoryDTO> history = stockHistoryService.getHistoryData(yahooSymbol, 30);
                for (StockHistoryDTO priceData : history) {
                    DailyPriceDTO priceDto = new DailyPriceDTO();
                    priceDto.setStockId(stock.getId());
                    priceDto.setPriceDate(priceData.getDate());
                    priceDto.setOpeningPrice(BigDecimal.valueOf(priceData.getOpen()));
                    priceDto.setHighPrice(BigDecimal.valueOf(priceData.getHigh()));
                    priceDto.setLowPrice(BigDecimal.valueOf(priceData.getLow()));
                    priceDto.setClosingPrice(BigDecimal.valueOf(priceData.getClose()));
                    priceDto.setVolume(priceData.getVolume());

                    try {
                        dailyPriceService.saveDailyPrice(priceDto);
                        saved++;
                    } catch (PriceAlreadyExistsException e) {
                        skipped++;
                    }
                }
                status = "SUCCESS";
                message = String.format("Saved %d, skipped %d", saved, skipped);
                successCount++;
                totalSaved += saved;
                totalSkipped += skipped;
            } catch (Exception e) {
                status = "FAILED";
                message = e.getMessage();
                failedCount++;
            }

            perStock.put(stock.getSymbol(), new PortfolioSyncResultDTO.StockSyncResult(
                status, saved, skipped, message
            ));
        }

        PortfolioSyncResultDTO result = new PortfolioSyncResultDTO(
            stocks.size(), successCount, failedCount, totalSaved, totalSkipped, perStock
        );
        return ResponseEntity.ok(ApiResponse.success(
            String.format("Synced %d stocks: %d saved, %d skipped, %d failed",
                stocks.size(), totalSaved, totalSkipped, failedCount),
            result
        ));
    }

    @GetMapping("/history/summary/all")
    public ResponseEntity<ApiResponse<List<BatchStockSummaryDTO>>> getAllSummaries(
            @RequestParam(defaultValue = "30") int days) {
        List<Stock> stocks = stockService.getAllStocks();
        List<BatchStockSummaryDTO> results = stocks.parallelStream().map(stock -> {
            try {
                String yahooSymbol = stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol();
                StockSummaryDTO summary = stockHistoryService.getSummary(yahooSymbol, days);
                if (summary.getHistory() != null) {
                    for (StockHistoryDTO h : summary.getHistory()) {
                        DailyPriceDTO dto = new DailyPriceDTO();
                        dto.setStockId(stock.getId());
                        dto.setPriceDate(h.getDate());
                        dto.setOpeningPrice(BigDecimal.valueOf(h.getOpen()));
                        dto.setHighPrice(BigDecimal.valueOf(h.getHigh()));
                        dto.setLowPrice(BigDecimal.valueOf(h.getLow()));
                        dto.setClosingPrice(BigDecimal.valueOf(h.getClose()));
                        dto.setVolume(h.getVolume());
                        try {
                            dailyPriceService.saveDailyPrice(dto);
                        } catch (PriceAlreadyExistsException e) {
                            // skip duplicate
                        }
                    }
                }
                return new BatchStockSummaryDTO(stock.getSymbol(), yahooSymbol, true, null, summary);
            } catch (Exception e) {
                return new BatchStockSummaryDTO(stock.getSymbol(),
                    stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol(),
                    false, e.getMessage(), null);
            }
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @GetMapping("/history/summary/local")
    public ResponseEntity<ApiResponse<List<BatchStockSummaryDTO>>> getLocalSummaries(
            @RequestParam(defaultValue = "30") int days) {
        List<Stock> stocks = stockService.getAllStocks();
        List<BatchStockSummaryDTO> results = stocks.parallelStream().map(stock -> {
            try {
                StockSummaryDTO summary = stockHistoryService.getLocalSummary(stock, days);
                String yahooSymbol = stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol();
                return new BatchStockSummaryDTO(stock.getSymbol(), yahooSymbol, true, null, summary);
            } catch (Exception e) {
                return new BatchStockSummaryDTO(stock.getSymbol(),
                    stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol(),
                    false, e.getMessage(), null);
            }
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
