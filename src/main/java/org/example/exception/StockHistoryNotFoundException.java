package org.example.exception;

public class StockHistoryNotFoundException extends RuntimeException {
    public StockHistoryNotFoundException(String message) {
        super(message);
    }
}
