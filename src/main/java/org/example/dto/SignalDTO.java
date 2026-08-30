package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalDTO {
    private Long stockId;
    private String symbol;
    private String name;
    private String sector;
    private String industry;
    private BigDecimal latestPrice;

    private BigDecimal rsi14;
    private BigDecimal sma20;
    private BigDecimal sma50;
    private BigDecimal ema12;
    private BigDecimal ema26;
    private BigDecimal macd;
    private BigDecimal macdSignal;
    private BigDecimal macdHistogram;

    private BigDecimal bollingerUpper;
    private BigDecimal bollingerMiddle;
    private BigDecimal bollingerLower;
    private BigDecimal priceVsSma20;

    private BigDecimal high52Week;
    private BigDecimal low52Week;
    private BigDecimal pctFrom52WHigh;
    private BigDecimal pctFrom52WLow;

    private BigDecimal supportLevel;
    private BigDecimal resistanceLevel;
    private BigDecimal trendStrength;
    private BigDecimal volatility;

    private BigDecimal pnlPercent;
    
    private BigDecimal weeklyRsi;
    private BigDecimal monthlyRsi;
    private boolean bullishDivergence;
    private boolean bearishDivergence;
    private boolean volumeConfirmed;

    // Signal breakdown fields (populated by computeWeightedScore)
    private boolean eventRisk;
    private List<String> eventPurposes;
    private int divergenceScore;
    private int weeklyConfluenceScore;
    private int monthlyConfluenceScore;
    private int rsiScore;
    private int bollingerScore;
    private int smaScore;
    private int week52Score;
    private boolean volumePenaltyApplied;
    private int fiidiiScore;

    private int compositeScore;
    private String recommendation;

    // Actionable Dashboard fields
    private BigDecimal atr;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal stochK;
    private BigDecimal stochD;
    private BigDecimal williamsR;
    private BigDecimal cci;
    private BigDecimal targetPrice;
    private BigDecimal stopLoss;
    private Long confidenceScore;       // 0-100 integer
    private BigDecimal suggestedPositionSize; // % of portfolio (e.g., 2.5)
    private Long signalAge;             // days
    private int indicatorCoverage;      // 9-16, how many of 16 scoring factors had backing data
    private BigDecimal historicalReturn5d;
    private BigDecimal historicalReturn10d;
    private BigDecimal historicalReturn20d;

    // New technical indicators (June 2026)
    private BigDecimal stochRsi;
    private BigDecimal adx;
    private BigDecimal plusDi;
    private BigDecimal minusDi;
    private BigDecimal ultimateOsc;
    private BigDecimal roc;
    private BigDecimal obv;

    // VWAP and Ichimoku (July 2026)
    private BigDecimal vwap;
    private BigDecimal tenkanSen;
    private BigDecimal kijunSen;
    private BigDecimal senkouSpanA;
    private BigDecimal senkouSpanB;
    private BigDecimal chikouSpan;

    // Rolling signal accuracy (nullable until enough records exist)
    private BigDecimal signalAccuracy30d;   // e.g., 70.0 for 70%
    private int signalAccuracyTotal30d;     // total signals evaluated in last 30 records
    private int signalAccuracyCorrect30d;   // correct signals in last 30 records

    // New scoring breakdown fields
    private int macdScore;
    private int stochScore;
    private int adxFilterApplied; // 0 = no filter, 1 = counter-trend suppressed
    private int stochRsiScore;
    private int ultimateOscScore;
    private int rocScore;
    private int williamsRScore;
    private int cciScore;
    private int obvScore;
    private int srProximityScore;
    private int vwapScore;
    private int ichimokuScore;
    private int trendDirectionScore; // Trend direction penalty (drawdown, decline, lower-highs)

    // Breakout detection
    private boolean volumeBreakout;
    private boolean gapUp;
    private boolean gapDown;
    private boolean rangeBreakout;
    private int breakoutScore;

    // BUY gate (3-dimension high-confidence filter)
    private boolean gateBlocked;
    private List<String> gateBlockReasons;

    // Candlestick pattern detection
    private int candlestickScore;
    private String candlestickPattern;

    // Strategy influence tracking
    private double strategyInfluenceMultiplier;
    private String strategyInfluenceReason;

    // Reversal detection
    private int reversalScore;
    private int reversalFlags; // bitmask: bit0=RSI rising, bit1=MACD improving, bit2=bullish divergence, bit3=OBV rising, bit4=higher low, bit5=price reclaiming SMA20

    // Intermediate computation values (for signal history trace)
    private int rawTrendScore;
    private int rawMomentumScore;
    private int rawStructureScore;
    private double adxMultiplier;
    private int scoreAfterAdx;
    private int scoreAfterCandlestick;
    private int scoreAfterReversal;
    private int scoreAfterDiscount;

    // Multi-strategy engine breakdown (strategy contributions to this signal)
    private List<StrategyBreakdownDTO> strategyBreakdown;

    // Liquidity assessment (computed from Amihud, dollar volume, turnover)
    private LiquidityScoreDTO liquidityScore;
}
