package org.example.exception;

public class PriceAlreadyExistsException extends RuntimeException {
    
    public PriceAlreadyExistsException(String message) {
        super(message);
    }
    
    public PriceAlreadyExistsException(Long stockId, String date) {
        super("Price already exists for stock id " + stockId + " on date " + date);
    }
}
