package org.example.dto;

import java.time.LocalDate;

/**
 * One stock's row for a strategy from the latest snapshot day (strategy expansion).
 *
 * @param eventDate date of the underlying signal event (e.g. the EMA cross day); null for state-based strategies
 */
public record StrategyStockResultDTO(
        Long stockId,
        String symbol,
        String signal,
        Double confidence,
        String reason,
        LocalDate eventDate
) {
}