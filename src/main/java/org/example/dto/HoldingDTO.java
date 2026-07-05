package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoldingDTO {

    private Long id;
    private Long portfolioId;
    private Long stockId;
    private String symbol;
    private String name;
    private String sector;
    private String yahooSymbol;
    private Integer quantity;
    private BigDecimal avgPrice;
    private BigDecimal lastTradedPrice;
    private BigDecimal investment;
    private BigDecimal currentValue;
    private BigDecimal pnl;
    private BigDecimal pnlPercent;
}
