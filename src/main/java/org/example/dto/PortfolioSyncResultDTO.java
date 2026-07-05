package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSyncResultDTO {
    private int totalStocks;
    private int successCount;
    private int failedCount;
    private int totalSaved;
    private int totalSkipped;
    private Map<String, StockSyncResult> perStockResults;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockSyncResult {
        private String status;
        private int saved;
        private int skipped;
        private String message;
    }
}
