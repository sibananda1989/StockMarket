package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.BacktestResultDTO;
import org.example.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    @GetMapping("/stock/{stockId}")
    public ResponseEntity<ApiResponse<BacktestResultDTO>> runBacktest(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "true") boolean stopLoss,
            @RequestParam(defaultValue = "0.02") double positionSizePct,
            @RequestParam(defaultValue = "0") int days) {
        return ResponseEntity.status(501).body(ApiResponse.error("Backtest feature is disabled for performance optimization"));
    }
}
