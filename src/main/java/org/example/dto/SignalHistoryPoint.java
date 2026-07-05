package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalHistoryPoint {
    private LocalDate priceDate;
    private String recommendation;
    private int compositeScore;
    private BigDecimal latestPrice;
    private BigDecimal forwardReturn;  // 10-day forward return (or 5d fallback)
    private Boolean wasAccurate;       // true/false if forward return matched signal direction

    // Factor breakdown for tooltip display
    private int rsiScore;
    private int smaScore;
    private int bollingerScore;
    private int macdScore;
    private int trendDirectionScore;
    private int candlestickScore;
    private String candlestickPattern;
    private int divergenceScore;
    private int weeklyConfluenceScore;
    private int fiidiiScore;
    private BigDecimal rsi14;
    private BigDecimal adx;
    private BigDecimal sma20;
    private BigDecimal sma50;

    // Intermediate computation values (for signal trace)
    private int rawTrendScore;
    private int rawMomentumScore;
    private int rawStructureScore;
    private BigDecimal adxMultiplier;
    private int scoreAfterAdx;
    private int scoreAfterCandlestick;
    private int scoreAfterReversal;
    private int scoreAfterDiscount;

    // Multi-strategy breakdown (populated for engine-computed points)
    private List<StrategyBreakdownDTO> strategyBreakdown;
    private Double buyThreshold;
    private Double sellThreshold;
    private Double rawScore;
}
