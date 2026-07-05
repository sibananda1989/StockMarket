package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockSummaryDTO {

    private String symbol;
    private double currentPrice;
    private double highestPrice;
    private double lowestPrice;
    private double averageClose;
    private double percentChange;   // (last close - first close) / first close * 100
    private int totalDays;
    private List<StockHistoryDTO> history;
}
