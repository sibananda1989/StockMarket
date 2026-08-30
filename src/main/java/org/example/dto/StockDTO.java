package org.example.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDTO {
    private Long id;
    private String symbol;
    private String name;
    private String sector;
    private String industry;
    private String yahooSymbol;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime updatedAt;
    
    private BigDecimal latestPrice;
    private BigDecimal latestRsi;
    private BigDecimal percentChange;

    // Portfolio fields from CSV
    private Integer quantity;
    private BigDecimal avgPrice;
    private BigDecimal lastTradedPrice;
    private BigDecimal investment;
    private BigDecimal currentValue;
    private BigDecimal pnl;
    private BigDecimal pnlPercent;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime addedAt;

    private Long recordCount;
}
