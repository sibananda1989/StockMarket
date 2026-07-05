package org.example.exception;

public class StockNotFoundException extends RuntimeException {
    
    public StockNotFoundException(String message) {
        super(message);
    }
    
    public StockNotFoundException(Long stockId) {
        super("Stock not found with id: " + stockId);
    }
}
