package org.example.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to sell quantity from a specific BUY lot.
 * The buy transaction id is mandatory — every SELL maps to exactly one BUY.
 */
@Data
public class SellFromLotRequest {

    @NotNull(message = "buyTransactionId is required")
    private Long buyTransactionId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;

    @PositiveOrZero(message = "Fees must be zero or positive")
    private BigDecimal fees;

    private LocalDate transactionDate;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
}
