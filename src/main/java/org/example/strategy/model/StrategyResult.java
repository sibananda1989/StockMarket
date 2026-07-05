package org.example.strategy.model;

/**
 * Immutable result from a single strategy evaluation.
 *
 * @param signal      the directional signal (BUY, SELL, HOLD)
 * @param confidence  0.0–1.0 confidence in the signal
 * @param reason      human-readable explanation of why this signal was emitted
 * @param strategyName name of the originating strategy
 * @param priority    weighting priority (1–10, higher = more influential)
 * @param contribution weighted contribution to the aggregate score (signal * priority * confidence)
 */
public record StrategyResult(
        StrategySignal signal,
        double confidence,
        String reason,
        String strategyName,
        int priority,
        double contribution
) {

    /**
     * Creates a StrategyResult without contribution (for backward compatibility).
     */
    public static StrategyResult withoutContribution(
            StrategySignal signal,
            double confidence,
            String reason,
            String strategyName,
            int priority) {
        return new StrategyResult(signal, confidence, reason, strategyName, priority, 0.0);
    }
}
