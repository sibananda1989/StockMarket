package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.entity.Stock;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.StockRepository;
import org.example.service.StrategyDailyWeightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/strategy-daily-weight")
@RequiredArgsConstructor
public class StrategyDailyWeightController {

    private final StrategyDailyWeightService weightService;
    private final StockRepository stockRepository;

    /**
     * Triggers backfill for all stocks.
     *
     * @param days number of days to backfill (default: 365)
     */
    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<String>> backfillAllStocks(
            @RequestParam(defaultValue = "365") int days) {
        weightService.backfillAllStocks(days);
        return ResponseEntity.ok(ApiResponse.success("Backfill started for " + days + " days"));
    }

    /**
     * Triggers backfill for a specific stock.
     *
     * @param stockId the stock identifier
     * @param days    number of days to backfill (default: 365)
     */
    @PostMapping("/backfill/{stockId}")
    public ResponseEntity<ApiResponse<String>> backfillStock(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "365") int days) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found"));
        weightService.backfillForStock(stock, days);
        return ResponseEntity.ok(ApiResponse.success("Backfill started for stock " + stock.getSymbol()));
    }

    /**
     * Returns the current status of the weight computation system.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("totalStocks", stockRepository.count());
        status.put("stocksProcessed", weightService.getProcessedStockCount());
        status.put("lastComputed", weightService.getLastComputedTime());
        status.put("totalWeightRecords", weightService.getTotalWeightCount());
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
