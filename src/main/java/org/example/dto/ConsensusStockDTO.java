package org.example.dto;

import java.util.List;

/**
 * One stock ranked by cross-strategy agreement, derived from a persisted
 * {@code strategy_stock_result} snapshot day. Unlike {@link TopStockDTO} this
 * carries the stock name and the denominator behind {@code agreeCount}, so the
 * UI can show "3 of 12 strategies agree" rather than a bare count.
 *
 * @param stockId              stock identifier
 * @param symbol               ticker symbol
 * @param name                 full stock name (may be blank if unknown)
 * @param signal               the signal all agreeing strategies emitted (BUY or SELL)
 * @param agreeCount           number of strategies that emitted {@code signal}
 * @param totalStrategies      strategies evaluated for this stock in the snapshot
 * @param avgConfidence        mean confidence across the agreeing strategies
 * @param strategyNames        strategy keys that agreed
 * @param strategyDisplayNames display names of the agreeing strategies
 */
public record ConsensusStockDTO(
        Long stockId,
        String symbol,
        String name,
        String signal,
        int agreeCount,
        int totalStrategies,
        double avgConfidence,
        List<String> strategyNames,
        List<String> strategyDisplayNames
) {
}