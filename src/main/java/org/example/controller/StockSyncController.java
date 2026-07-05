package org.example.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ApiResponse;
import org.example.service.StockSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
@Validated
public class StockSyncController {

    private final StockSyncService stockSyncService;

    @PostMapping("/sync-history")
    public ResponseEntity<ApiResponse<String>> syncAllHistory() {
        log.info("Manual trigger: backfill all stocks from Yahoo Finance");
        int saved = stockSyncService.backfillAllStocks();
        return ResponseEntity.ok(ApiResponse.success("Backfill complete. Total records saved: " + saved, null));
    }

    @PostMapping("/{id}/sync-history")
    public ResponseEntity<ApiResponse<String>> syncStockHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "730") @Min(30) @Max(3650) int days) {
        log.info("Manual trigger: sync {} days of history for stock {}", days, id);
        long saved = stockSyncService.syncStockHistory(id, days);
        return ResponseEntity.ok(ApiResponse.success("History synced for stock " + id + ". Records saved: " + saved, null));
    }
}
