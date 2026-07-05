package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.ComparisonDTO;
import org.example.dto.SignalDTO;
import org.example.dto.SignalHistoryPoint;
import org.example.service.SignalService;
import org.example.strategy.engine.MultiStrategySignalEngine;
import org.example.strategy.model.AggregatedSignalResult;
import org.example.strategy.model.StrategyResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST controller for the multi-strategy signal system.
 * Exposes endpoints to retrieve signals and compare with the existing system.
 */
@RestController
@RequestMapping("/api/signals/multi-strategy")
@RequiredArgsConstructor
public class MultiStrategySignalController {

    private final MultiStrategySignalEngine engine;
    private final SignalService signalService;

    /**
     * Retrieves the aggregated multi-strategy signal for a stock.
     *
     * @param stockId the stock identifier
     * @param active  optional set of active strategy names to evaluate
     * @return ApiResponse with AggregatedSignalResult
     */
    @GetMapping("/{stockId}")
    public ResponseEntity<ApiResponse<AggregatedSignalResult>> getSignal(
            @PathVariable Long stockId,
            @RequestParam(name = "active", required = false) Set<String> active) {
        AggregatedSignalResult result = engine.evaluate(stockId, active);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Retrieves the per-strategy breakdown for a stock.
     *
     * @param stockId the stock identifier
     * @param active  optional set of active strategy names to evaluate
     * @return ApiResponse with List of StrategyResult
     */
    @GetMapping("/{stockId}/breakdown")
    public ResponseEntity<ApiResponse<List<StrategyResult>>> getBreakdown(
            @PathVariable Long stockId,
            @RequestParam(name = "active", required = false) Set<String> active) {
        AggregatedSignalResult result = engine.evaluate(stockId, active);
        return ResponseEntity.ok(ApiResponse.success(result.breakdown()));
    }

    /**
     * Compares the new multi-strategy signal with the existing signal.
     *
     * @param stockId the stock identifier
     * @param active  optional set of active strategy names to evaluate
     * @return ApiResponse with ComparisonDTO
     */
    @GetMapping("/compare/{stockId}")
    public ResponseEntity<ApiResponse<ComparisonDTO>> compareSignals(
            @PathVariable Long stockId,
            @RequestParam(name = "active", required = false) Set<String> active) {
        AggregatedSignalResult multiStrategy = engine.evaluate(stockId, active);
        SignalDTO existing = signalService.getComputedSignal(stockId);

        ComparisonDTO comparison = new ComparisonDTO(existing, multiStrategy);
        return ResponseEntity.ok(ApiResponse.success(comparison));
    }

    /**
     * Retrieves multi-strategy signal history enriched with legacy factor scores.
     * Merges multi-strategy recommendations (from active strategies) with
     * legacy signal factor breakdowns for rich tooltip display.
     *
     * @param stockId the stock identifier
     * @param days    how far back to go from today
     * @param active  optional set of active strategy names to evaluate
     * @return ApiResponse with List of SignalHistoryPoint enriched with factor scores
     */
    @GetMapping("/{stockId}/history")
    public ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> getSignalHistory(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(name = "active", required = false) Set<String> active) {
        // 1. Get multi-strategy history (recommendations from active strategies)
        List<SignalHistoryPoint> multiStrategyHistory = engine.evaluateHistory(stockId, days, active);

        // 2. Get legacy signal history (full factor breakdowns)
        List<SignalHistoryPoint> legacyHistory = signalService.getSignalHistory(stockId, days);

        // 3. Merge: use multi-strategy recommendations, enrich with legacy factor scores
        Map<LocalDate, SignalHistoryPoint> legacyByDate = legacyHistory.stream()
                .collect(Collectors.toMap(SignalHistoryPoint::getPriceDate, Function.identity()));

        multiStrategyHistory.forEach(multiPoint -> {
            SignalHistoryPoint legacyPoint = legacyByDate.get(multiPoint.getPriceDate());
            if (legacyPoint != null) {
                multiPoint.setRsiScore(legacyPoint.getRsiScore());
                multiPoint.setSmaScore(legacyPoint.getSmaScore());
                multiPoint.setBollingerScore(legacyPoint.getBollingerScore());
                multiPoint.setMacdScore(legacyPoint.getMacdScore());
                multiPoint.setTrendDirectionScore(legacyPoint.getTrendDirectionScore());
                multiPoint.setCandlestickScore(legacyPoint.getCandlestickScore());
                multiPoint.setCandlestickPattern(legacyPoint.getCandlestickPattern());
                multiPoint.setDivergenceScore(legacyPoint.getDivergenceScore());
                multiPoint.setWeeklyConfluenceScore(legacyPoint.getWeeklyConfluenceScore());
                multiPoint.setFiidiiScore(legacyPoint.getFiidiiScore());
                multiPoint.setRsi14(legacyPoint.getRsi14());
                multiPoint.setAdx(legacyPoint.getAdx());
                multiPoint.setSma20(legacyPoint.getSma20());
                multiPoint.setSma50(legacyPoint.getSma50());
            }
        });

        return ResponseEntity.ok(ApiResponse.success(multiStrategyHistory));
    }
}
