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
        
        if (levels == null || levels.getMajorLevels() == null || levels.getMajorLevels().isEmpty()) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, "No support/resistance levels available", getName(), getPriority());
        }

        BigDecimal currentPrice = prices.get(0).getClosingPrice();

        switch (result.pattern()) {
            case HAMMER, BULLISH_ENGULFING, MORNING_STAR, BULLISH_HARAMI, PIERCING_LINE:
                if (isNearSupport(currentPrice, levels.getMajorLevels())) {
                    return StrategyResult.withoutContribution(StrategySignal.BUY, 0.75, 
                        String.format("%s on support (score: %d)", result.label(), result.score()), 
                        getName(), getPriority());
                } else {
                    return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, 
                        String.format("%s but not near support", result.label()), 
                        getName(), getPriority());
                }
            case SHOOTING_STAR, BEARISH_ENGULFING, EVENING_STAR, BEARISH_HARAMI, DARK_CLOUD_COVER:
                if (isNearResistance(currentPrice, levels.getMajorLevels())) {
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

    private boolean isNearSupport(BigDecimal price, List<SupportResistanceDto.MajorLevel> levels) {
        for (SupportResistanceDto.MajorLevel level : levels) {
            if ("support".equals(level.getType())) {
                BigDecimal distance = price.subtract(level.getPrice()).abs();
                BigDecimal threshold = level.getPrice().multiply(new BigDecimal("0.02"));
                if (distance.compareTo(threshold) <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNearResistance(BigDecimal price, List<SupportResistanceDto.MajorLevel> levels) {
        for (SupportResistanceDto.MajorLevel level : levels) {
            if ("resistance".equals(level.getType())) {
                BigDecimal distance = price.subtract(level.getPrice()).abs();
                BigDecimal threshold = level.getPrice().multiply(new BigDecimal("0.02"));
                if (distance.compareTo(threshold) <= 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
