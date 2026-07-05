package org.example.exception;

/**
 * Thrown when a stock exists in the database but has no price/RSI data yet
 * (e.g. just added to a watchlist, history sync hasn't completed).
 *
 * Distinct from {@link StockNotFoundException} — the stock itself DOES exist;
 * only the related data is missing. The frontend should show a "data syncing"
 * placeholder, not a "stock not found" error.
 */
public class NoPriceDataException extends RuntimeException {

    private final Long stockId;

    public NoPriceDataException(Long stockId, String dataType) {
        super("No " + dataType + " data available for stock id: " + stockId
                + " (sync may still be in progress)");
        this.stockId = stockId;
    }

    public Long getStockId() {
        return stockId;
    }
}