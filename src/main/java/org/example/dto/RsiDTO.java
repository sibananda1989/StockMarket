package org.example.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsiDTO {
    private Long id;
    
    @NotNull(message = "Stock ID is required")
    private Long stockId;
    
    @NotNull(message = "RSI 14 value is required")
    @DecimalMin(value = "0.00", message = "RSI must be at least 0")
    @DecimalMax(value = "100.00", message = "RSI must be at most 100")
    private BigDecimal rsi14;
    
    @NotNull(message = "Calculation date is required")
    @PastOrPresent(message = "Calculation date cannot be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate calculationDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime createdAt;
}
