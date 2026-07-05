package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.IndicatorCoverageDto;
import org.example.service.TechnicalIndicatorCoverageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the indicator-completeness report for a single stock.
 *
 * Used by the dashboard's "X indicators missing" badge so the user
 * can see exactly which long-look-back metrics (e.g. SMA-200,
 * MACD-Signal, Senkou-Span-B) aren't yet available — usually
 * because the stock lacks sufficient price history.
 */
@RestController
@RequestMapping("/api/indicators/coverage")
@RequiredArgsConstructor
public class IndicatorCoverageController {

    private final TechnicalIndicatorCoverageService coverageService;

    @GetMapping("/{stockId}")
    public ResponseEntity<ApiResponse<IndicatorCoverageDto>> getCoverage(@PathVariable Long stockId) {
        IndicatorCoverageDto coverage = coverageService.getCoverage(stockId);
        return ResponseEntity.ok(ApiResponse.success(coverage));
    }
}
