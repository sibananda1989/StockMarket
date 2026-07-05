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
public class DailyPriceDTO {
    private Long id;
    
    @NotNull(message = "Stock ID is required")
    private Long stockId;
    
    @NotNull(message = "Closing price is required")
    @DecimalMin(value = "0.01", message = "Closing price must be greater than 0")
    private BigDecimal closingPrice;
    
    @DecimalMin(value = "0.01", message = "Opening price must be greater than 0")
    private BigDecimal openingPrice;
    
    @DecimalMin(value = "0.01", message = "High price must be greater than 0")
    private BigDecimal highPrice;
    
    @DecimalMin(value = "0.01", message = "Low price must be greater than 0")
    private BigDecimal lowPrice;
    
    @Min(value = 0, message = "Volume must be non-negative")
    private Long volume;
    
    @NotNull(message = "Price date is required")
    @PastOrPresent(message = "Price date cannot be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate priceDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime createdAt;
}
