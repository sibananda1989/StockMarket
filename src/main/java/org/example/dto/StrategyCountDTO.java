package org.example.dto;

/**
 * Aggregate BUY/SELL/HOLD counts for one strategy from the latest snapshot day.
 *
 * @param strategyName internal strategy key (e.g. "EMA_CROSSOVER") — used for detail lookups
 * @param displayName  user-facing label (e.g. "EMA 20/50 Cross") — used for display
 * @param active       whether the strategy is currently enabled; false = reference-only
 *                     (its signals do NOT count toward the live aggregate signal)
 */
public record StrategyCountDTO(
        String strategyName,
        String displayName,
        int buyCount,
        int sellCount,
        int holdCount,
        int priority,
        boolean active
) {
}
