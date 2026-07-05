package org.example.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CsvImportRequest {
    private String symbol;
    private String name;
    private String sector;
    private Integer quantity;
    private BigDecimal avgPrice;
    private BigDecimal lastTradedPrice;
    private BigDecimal investment;
    private BigDecimal currentValue;
    private BigDecimal pnl;
    private BigDecimal pnlPercent;
}
