package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ApiResponse;
import org.example.service.DhanSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dhan")
@RequiredArgsConstructor
@Slf4j
public class DhanController {

    private final DhanSyncService dhanSyncService;

    /**
     * POST /api/dhan/sync-holdings
     * Pulls all holdings from Dhan and upserts them as stocks in the DB.
     */
    @PostMapping("/sync-holdings")
    public ResponseEntity<ApiResponse<String>> syncHoldings() {
        int count = dhanSyncService.syncHoldings();
        return ResponseEntity.ok(ApiResponse.success("Synced " + count + " holdings from Dhan", null));
    }

    /**
     * POST /api/dhan/sync-prices
     * Pulls last 30 days of OHLCV for all holdings and recalculates RSI14.
     */
    @PostMapping("/sync-prices")
    public ResponseEntity<ApiResponse<String>> syncPrices() {
        dhanSyncService.syncDailyPricesAndRsi();
        return ResponseEntity.ok(ApiResponse.success("Prices and RSI14 synced from Dhan", null));
    }
}
