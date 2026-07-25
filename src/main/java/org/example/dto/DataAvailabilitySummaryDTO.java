package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAvailabilitySummaryDTO {
    private long totalStocks;
    private long stocksWithPriceHistory;
    private long stocksWithSignals;
    private long stocksWithIndicatorsAndSr;
    private long fiidiiRecords;         // market-level: number of trading day records
    private boolean fiidiiAvailable;
    private long stocksWithFundamentals;
    private long portfolioCount;
    private long holdingsCount;
    private long stocksWithHoldings;
}
