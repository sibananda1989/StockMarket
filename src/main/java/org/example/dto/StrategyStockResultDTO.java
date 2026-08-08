package org.example.dto;

/**
 * One stock's row for a strategy from the latest snapshot day (strategy expansion).
 */
public record StrategyStockResultDTO(
        Long stockId,
        String symbol,
        String signal,
        Double confidence,
        String reason
) {
}
