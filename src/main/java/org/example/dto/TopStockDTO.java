package org.example.dto;

import java.util.List;

/**
 * One stock ranked by how many strategies agree on a signal (consensus count).
 * Used for the "Top 5 Buy / Top 5 Sell" feature.
 *
 * @param count number of strategies that emitted the queried signal for this stock
 */
public record TopStockDTO(
        Long stockId,
        String symbol,
        int count,
        double avgConfidence,
        List<String> strategyNames,
        List<String> strategyDisplayNames
) {
}
