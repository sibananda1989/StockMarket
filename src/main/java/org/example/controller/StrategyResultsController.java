package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.RefreshSummary;
import org.example.dto.StrategyCountDTO;
import org.example.dto.StrategyStockResultDTO;
import org.example.service.StrategyResultsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the standalone "Strategy Results" page.
 */
@RestController
@RequestMapping("/api/strategy-results")
@RequiredArgsConstructor
public class StrategyResultsController {

    private final StrategyResultsService strategyResultsService;

    /**
     * Aggregate BUY/SELL/HOLD counts per strategy for the latest snapshot day.
     * Auto-seeds today's snapshot when the table is empty (first visit).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StrategyCountDTO>>> getCounts() {
        return ResponseEntity.ok(ApiResponse.success(strategyResultsService.getCounts()));
    }

    /**
     * Rows for one strategy from the latest snapshot day (strategy expansion).
     */
    @GetMapping("/{strategyName}")
    public ResponseEntity<ApiResponse<List<StrategyStockResultDTO>>> getStocksFor(
            @PathVariable String strategyName) {
        return ResponseEntity.ok(ApiResponse.success(strategyResultsService.getStocksFor(strategyName)));
    }

    /**
     * Recomputes all stocks through the engine and persists today's snapshot,
     * overwriting only today's rows.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshSummary>> refresh() {
        return ResponseEntity.ok(ApiResponse.success(strategyResultsService.refreshAll()));
    }
}
