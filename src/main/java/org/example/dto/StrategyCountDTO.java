package org.example.dto;

/**
 * Aggregate BUY/SELL/HOLD counts for one strategy from the latest snapshot day.
 */
public record StrategyCountDTO(
        String strategyName,
        int buyCount,
        int sellCount,
        int holdCount,
        int priority
) {
}
