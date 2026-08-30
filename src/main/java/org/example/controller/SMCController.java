package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.SMCPatternDTO;
import org.example.service.SMCDetectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Smart Money Concepts (SMC/ICT) pattern detection.
 * <p>
 * Provides endpoints to detect BOS (Break of Structure), CHoCH (Change of Character),
 * and FVG (Fair Value Gap) patterns directly from OHLC price data.
 */
@RestController
@RequestMapping("/api/smc")
@RequiredArgsConstructor
public class SMCController {

    private final SMCDetectionService smcDetectionService;

    /**
     * Detect SMC patterns for a stock.
     *
     * @param stockId      the stock ID
     * @param lookbackDays number of days of price history to analyse (default: 365)
     * @return SMCPatternDTO with BOS, CHoCH, and FVG entries
     */
    @GetMapping("/{stockId}")
    public ResponseEntity<ApiResponse<SMCPatternDTO>> detect(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "365") int lookbackDays) {
        if (lookbackDays < 30) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("lookbackDays must be at least 30 for meaningful SMC detection"));
        }
        SMCPatternDTO result = smcDetectionService.detect(stockId, lookbackDays);
        if (result == null) {
            return ResponseEntity.ok(ApiResponse.success("Insufficient price data. Need at least 30 days.", null));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
