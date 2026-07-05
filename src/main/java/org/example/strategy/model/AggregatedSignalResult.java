package org.example.strategy.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Aggregated result combining all individual strategy outputs.
 *
 * @param finalSignal   the consolidated signal after weighted aggregation
 * @param score         weighted score from all strategies
 * @param breakdown     per-strategy results for transparency
 * @param totalPriority sum of all strategy priorities used in aggregation
 * @param confidence    overall confidence in the aggregated signal (0.0-1.0)
 * @param supporting    list of strategies supporting the final signal
 * @param opposing      list of strategies opposing the final signal
 * @param categorySummary map of signal type to count of strategies
 * @param contributions map of strategy name to its contribution to the score
 */
public record AggregatedSignalResult(
        StrategySignal finalSignal,
        double score,
        List<StrategyResult> breakdown,
        int totalPriority,
        double confidence,
        List<String> supporting,
        List<String> opposing,
        Map<String, Integer> categorySummary,
        Map<String, Double> contributions
) {

    /**
     * Creates a simplified AggregatedSignalResult from existing fields.
     * Used for backward compatibility when constructing from legacy code.
     */
    public static AggregatedSignalResult simple(
            StrategySignal finalSignal,
            double score,
            List<StrategyResult> breakdown,
            int totalPriority) {
        return new AggregatedSignalResult(
                finalSignal,
                score,
                breakdown,
                totalPriority,
                0.0,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                new HashMap<>()
        );
    }
}
