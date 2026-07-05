package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionalScoreDTO {
    private Long stockId;
    private String symbol;
    private String name;
    private String sector;

    // Component scores
    private int fiiHoldingScore;          // 0-20
    private int diiHoldingScore;          // 0-15
    private int mutualFundHoldingScore;   // 0-15
    private int bulkDealScore;            // 0-15
    private int blockDealScore;           // 0-10
    private int volumeScore;              // 0-10
    private int priceActionScore;         // 0-5
    private int deliveryScore;            // 0-10

    // Total
    private int totalScore;               // 0-100

    // Classification
    private String institutionalGrade;    // "STRONG_BUY", "BUY", "NEUTRAL", "SELL"

    // Latest values
    private BigDecimal latestPrice;
    private BigDecimal fiiHoldingPct;
    private BigDecimal diiHoldingPct;
    private BigDecimal mutualFundHoldingPct;
    private BigDecimal fiiChangeQoq;
    private BigDecimal diiChangeQoq;
    private BigDecimal mutualFundChangeQoq;
    private LocalDate latestQuarterEnd;
    private Integer recentBulkBuys;       // Count of institutional bulk buys in last 90 days
    private Integer recentBlockBuys;      // Count of institutional block buys in last 90 days
    private Long averageVolume;
    private Long latestVolume;
    private String signalRecommendation;
}
