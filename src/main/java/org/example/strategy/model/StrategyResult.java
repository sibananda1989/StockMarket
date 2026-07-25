package org.example.strategy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable result from a single strategy evaluation.
 *
 * @param signal          the directional signal (BUY, SELL, HOLD)
 * @param confidence      0.0–1.0 confidence in the signal
 * @param reason          human-readable explanation of why this signal was emitted
 * @param strategyName    name of the originating strategy
 * @param priority        weighting priority (1–10, higher = more influential)
 * @param contribution    weighted contribution to the aggregate score (signal * priority * confidence)
 * @param latestVolume    latest trading volume (for volume strategy tooltip)
 * @param avgVolume       average trading volume over the lookback period (for volume strategy tooltip)
 * @param spikeThreshold  volume spike threshold (VOLUME_SPIKE_FACTOR * avgVolume) (for volume strategy tooltip)
 */
public record StrategyResult(
        StrategySignal signal,
        double confidence,
        String reason,
        String strategyName,
        int priority,
        double contribution,
        Long latestVolume,
        Double avgVolume,
        Double spikeThreshold
) {

    /**
     * Creates a StrategyResult without contribution and without volume data (for backward compatibility).
     */
    public static StrategyResult withoutContribution(
            StrategySignal signal,
            double confidence,
            String reason,
            String strategyName,
            int priority) {
        return new StrategyResult(signal, confidence, reason, strategyName, priority, 0.0, null, null, null);
    }

    /**
     * Creates a StrategyResult without contribution but with volume data.
     */
    public static StrategyResult withoutContributionWithVolume(
            StrategySignal signal,
            double confidence,
            String reason,
            String strategyName,
            int priority,
            Long latestVolume,
            Double avgVolume,
            Double spikeThreshold) {
        return new StrategyResult(signal, confidence, reason, strategyName, priority, 0.0, latestVolume, avgVolume, spikeThreshold);
    }
}
