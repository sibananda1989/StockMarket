package org.example.strategy.impl;

import org.example.dto.SupportResistanceDto;
import org.example.entity.DailyPrice;
import org.example.service.SupportResistanceService;
import org.example.service.calculator.CandlestickPatternCalculator;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.math.BigDecimal;
import java.util.List;

public class CandlestickPatternStrategy extends TradingStrategy {

    private final SupportResistanceService supportResistanceService;

    public CandlestickPatternStrategy(int priority, SupportResistanceService supportResistanceService) {
        super("CANDLESTICK", priority);
        this.supportResistanceService = supportResistanceService;
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<org.example.entity.TechnicalIndicator> indicators, List<DailyPrice> prices) {
        if (prices == null || prices.size() < 3) {
            return insufficientData();
        }

        CandlestickPatternCalculator calculator = new CandlestickPatternCalculator();
        CandlestickPatternCalculator.Result result = calculator.detect(prices);

        if (result.pattern() == CandlestickPatternCalculator.Pattern.NONE) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, "No candlestick pattern detected", getName(), getPriority());
        }

        SupportResistanceDto levels = supportResistanceService.getLatestLevels(stockId);
        
        if (levels == null) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, "No support/resistance levels available", getName(), getPriority());
        }

        BigDecimal currentPrice = prices.get(prices.size() - 1).getClosingPrice();

        switch (result.pattern()) {
            case HAMMER, BULLISH_ENGULFING, MORNING_STAR, BULLISH_HARAMI, PIERCING_LINE:
                if (isNearSupport(currentPrice, levels)) {
                    return StrategyResult.withoutContribution(StrategySignal.BUY, 0.75, 
                        String.format("%s on support (score: %d)", result.label(), result.score()), 
                        getName(), getPriority());
                } else {
                    return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, 
                        String.format("%s but not near support", result.label()), 
                        getName(), getPriority());
                }
            case SHOOTING_STAR, BEARISH_ENGULFING, EVENING_STAR, BEARISH_HARAMI, DARK_CLOUD_COVER:
                if (isNearResistance(currentPrice, levels)) {
                    return StrategyResult.withoutContribution(StrategySignal.SELL, 0.75, 
                        String.format("%s on resistance (score: %d)", result.label(), result.score()), 
                        getName(), getPriority());
                } else {
                    return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, 
                        String.format("%s but not near resistance", result.label()), 
                        getName(), getPriority());
                }
            default:
                return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, 
                    String.format("Unknown pattern: %s", result.pattern()), 
                    getName(), getPriority());
        }
    }

    private boolean isNearSupport(BigDecimal price, SupportResistanceDto levels) {
        // Check major support levels
        if (levels.getMajorLevels() != null) {
            for (SupportResistanceDto.MajorLevel level : levels.getMajorLevels()) {
                if ("support".equals(level.getType()) && isWithinThreshold(price, level.getPrice())) {
                    return true;
                }
            }
        }
        // Check pivot support levels (s1, s2, s3)
        if (levels.getPivots() != null) {
            if (isWithinThreshold(price, levels.getPivots().getS1())) return true;
            if (isWithinThreshold(price, levels.getPivots().getS2())) return true;
            if (isWithinThreshold(price, levels.getPivots().getS3())) return true;
        }
        // Check swing lows
        if (levels.getSwingLows() != null) {
            for (SupportResistanceDto.SwingLevel swing : levels.getSwingLows()) {
                if (isWithinThreshold(price, swing.getPrice())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNearResistance(BigDecimal price, SupportResistanceDto levels) {
        // Check major resistance levels
        if (levels.getMajorLevels() != null) {
            for (SupportResistanceDto.MajorLevel level : levels.getMajorLevels()) {
                if ("resistance".equals(level.getType()) && isWithinThreshold(price, level.getPrice())) {
                    return true;
                }
            }
        }
        // Check pivot resistance levels (r1, r2, r3)
        if (levels.getPivots() != null) {
            if (isWithinThreshold(price, levels.getPivots().getR1())) return true;
            if (isWithinThreshold(price, levels.getPivots().getR2())) return true;
            if (isWithinThreshold(price, levels.getPivots().getR3())) return true;
        }
        // Check swing highs
        if (levels.getSwingHighs() != null) {
            for (SupportResistanceDto.SwingLevel swing : levels.getSwingHighs()) {
                if (isWithinThreshold(price, swing.getPrice())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isWithinThreshold(BigDecimal price, BigDecimal levelPrice) {
        if (price == null || levelPrice == null) return false;
        BigDecimal distance = price.subtract(levelPrice).abs();
        BigDecimal threshold = levelPrice.multiply(new BigDecimal("0.02"));
        return distance.compareTo(threshold) <= 0;
    }
}
