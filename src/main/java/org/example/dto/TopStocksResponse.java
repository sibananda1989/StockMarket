package org.example.dto;

import java.util.List;

/**
 * Response for the ranked Top 5 feature — separate BUY and SELL leaderboards
 * sorted by consensus count (how many strategies agree), high → low.
 */
public record TopStocksResponse(
        List<TopStockDTO> topBuys,
        List<TopStockDTO> topSells
) {
}
