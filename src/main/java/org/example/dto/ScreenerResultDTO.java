package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerResultDTO {
    private Long stockId;
    private String symbol;
    private String name;
    private String sector;

    // Screeners it matches
    private boolean fiiAccumulation;
    private boolean diiAccumulation;
    private boolean mutualFundAccumulation;
    private boolean institutionalStrongBuy;
    private boolean institutionalAndPriceActionBuy;
    private boolean recentBulkDeal;
    private boolean recentBlockDeal;

    // Supporting data
    private BigDecimal latestPrice;
    private BigDecimal fiiHoldingPct;
    private BigDecimal diiHoldingPct;
    private BigDecimal mutualFundHoldingPct;
    private BigDecimal fiiChangeQoq;
    private BigDecimal diiChangeQoq;
    private BigDecimal mutualFundChangeQoq;
    private int institutionalScore;
    private String signalRecommendation;
    private Map<String, Object> additionalData;
}
