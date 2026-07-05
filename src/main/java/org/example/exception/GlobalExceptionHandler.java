package org.example.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(StockNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleStockNotFoundException(StockNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(sanitize(ex.getMessage()), "STOCK_NOT_FOUND"));
    }

    /**
     * Stock exists but no price/RSI data is available yet (history sync
     * still in progress, or stock was just added to a watchlist).
     * Returns 404 with a distinct error code so the frontend can show a
     * "data syncing" placeholder instead of a generic "stock not found" error.
     */
    @ExceptionHandler(NoPriceDataException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoPriceDataException(NoPriceDataException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(sanitize(ex.getMessage()), "NO_PRICE_DATA"));
    }

    @ExceptionHandler(StockHistoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleStockHistoryNotFoundException(StockHistoryNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(sanitize(ex.getMessage()), "HISTORY_NOT_FOUND"));
    }

    @ExceptionHandler(PriceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handlePriceAlreadyExistsException(PriceAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(sanitize(ex.getMessage()), "PRICE_ALREADY_EXISTS"));
    }

    @ExceptionHandler(InvalidPriceException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidPriceException(InvalidPriceException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(sanitize(ex.getMessage()), "INVALID_PRICE"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = sanitize(error.getDefaultMessage());
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>("error", "Validation failed", errors, LocalDateTime.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(sanitize(ex.getMessage()), "ILLEGAL_ARGUMENT"));
    }

    /**
     * Handles constraint violations from @Validated + @Positive on path variables.
     * Returns 400 Bad Request with the violation message.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(sanitize(ex.getMessage()), "CONSTRAINT_VIOLATION"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(sanitize(ex.getMessage()), "NOT_FOUND"));
    }

    /**
     * Silently handles client disconnect errors (broken pipe, client abort).
     * These occur when users navigate away before the server finishes responding.
     * Log at WARN level without stack trace to reduce noise.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof org.apache.catalina.connector.ClientAbortException) {
                log.warn("Client disconnected during async request: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success("Client disconnected", null));
            }
            if (cause.getMessage() != null && cause.getMessage().contains("Broken pipe")) {
                log.warn("Client disconnected (broken pipe): {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success("Client disconnected", null));
            }
            cause = cause.getCause();
        }
        // Not a client abort, log as generic error
        log.debug("Async request not usable (non-client abort): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
            .body(ApiResponse.success("Request cancelled", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        // Skip logging for client abort errors that slipped through
        if (isClientAbort(ex)) {
            log.debug("Client disconnected (suppressed): {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        
        // Sanitize before logging to prevent log injection (CWE-117/CWE-93)
        log.error("Unhandled exception: {}", sanitize(ex.getMessage()), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR"));
    }

    /**
     * Checks if the exception chain indicates a client abort/broken pipe.
     */
    private boolean isClientAbort(Exception ex) {
        Throwable cause = ex;
        while (cause != null) {
            String className = cause.getClass().getName();
            if (className.contains("ClientAbortException") || 
                className.contains("BrokenPipe") ||
                (cause.getMessage() != null && cause.getMessage().contains("Broken pipe"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    // Strips newlines and carriage returns to prevent log injection (CWE-117)
    private String sanitize(String input) {
        if (input == null) return "Unknown error";
        return input.replaceAll("[\r\n\t]", " ").trim();
    }
}
