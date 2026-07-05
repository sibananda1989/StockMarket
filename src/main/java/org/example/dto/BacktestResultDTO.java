package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
}
