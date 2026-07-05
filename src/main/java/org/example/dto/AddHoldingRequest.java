package org.example.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddHoldingRequest {

    @NotNull(message = "stockId is required")
    @Positive(message = "stockId must be positive")
    private Long stockId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @PositiveOrZero(message = "Average price must be zero or positive")
    private BigDecimal avgPrice;
}
