package org.example.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial update of an existing transaction. Every field is optional — a null
 * field is left untouched, which lets the UI send only what the user changed.
 *
 * <p>The transaction's stock and BUY/SELL type are intentionally not editable:
 * changing either would break the lot linkage held by linked_buy_id and the
 * realized P/L already stored against it.
 */
@Data
public class UpdateTransactionRequest {

    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;

    @PositiveOrZero(message = "Fees must be zero or positive")
    private BigDecimal fees;

    private LocalDate transactionDate;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
}