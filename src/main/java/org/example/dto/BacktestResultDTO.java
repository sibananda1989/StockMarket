package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultDTO {
    private String symbol;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private int stoppedOutTrades;
    private int signalExits;
    private int eventsSkipped;
    private double winRate;
    private BigDecimal totalReturn;
    private BigDecimal averageReturnPerTrade;
    private BigDecimal maxDrawdown;
    private BigDecimal finalPortfolioValue;

    // Advanced risk metrics
    private Double sharpeRatio;
    private Double calmarRatio;
    private Double sortinoRatio;
    private Integer longestDrawdownDays;
    private Double profitFactor;
    private BigDecimal avgWinningTrade;
    private BigDecimal avgLosingTrade;
    private Integer largestWinnerCount;
    private Integer largestLoserCount;

    // Individual trade records
    private List<TradeRecordDTO> tradeHistory;

    // Equity curve
    private List<Double> equityCurve;
}
