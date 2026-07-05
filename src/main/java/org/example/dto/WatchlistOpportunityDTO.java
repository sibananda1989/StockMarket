package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistOpportunityDTO {
    private Long stockId;
    private String symbol;
    private String companyName;
    private BigDecimal currentPrice;
    private Integer rank;
    private BigDecimal compositeScore;
    private String signal;                  // "STRONG BUY", "BUY", "HOLD", "SELL", "STRONG SELL"
    private EntryZoneDTO entryZone;
    private BigDecimal stopLoss;
    private BigDecimal target;
    private BigDecimal riskRewardRatio;
    private Double indicatorAgreement;      // % of directional indicators agreeing
    private List<ReasonDTO> reasons;
    private LocalDateTime lastComputed;
    private Integer confidenceScore;
}
