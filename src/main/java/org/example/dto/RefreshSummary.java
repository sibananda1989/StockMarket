package org.example.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Summary returned by a strategy snapshot refresh.
 */
public record RefreshSummary(
        int rowsWritten,
        int stocksEvaluated,
        LocalDate snapshotDate,
        LocalDateTime computedAt
) {
}
