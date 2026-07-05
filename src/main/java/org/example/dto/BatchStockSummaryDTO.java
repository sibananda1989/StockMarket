package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchStockSummaryDTO {
    private String symbol;
    private String yahooSymbol;
    private boolean success;
    private String error;
    private StockSummaryDTO summary;
}
