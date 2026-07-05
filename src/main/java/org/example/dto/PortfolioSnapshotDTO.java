package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PortfolioSnapshotDTO {
    private Long id;
    private Long stockId;
    private String symbol;
    private String name;
    private LocalDate snapshotDate;
    private Integer quantity;
    private BigDecimal avgPrice;
    private BigDecimal lastTradedPrice;
    private BigDecimal investment;
    private BigDecimal currentValue;
    private BigDecimal pnl;
    private BigDecimal pnlPercent;
    private Long volume;

}
