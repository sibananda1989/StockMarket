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

/**
 * Candlestick strategy that emits a signal only when a detected candlestick pattern
 * aligns with a specific support or resistance context.
 *
 * <p>In SUPPORT mode: emits BUY when a bullish pattern occurs near support.
 * In RESISTANCE mode: emits SELL when a bearish pattern occurs near resistance.</p>
 */
public class CandlestickContextStrategy extends TradingStrategy {

    private final SupportResistanceService supportResistanceService;
    private final boolean isSupport;

    public CandlestickContextStrategy(int priority, SupportResistanceService srs, boolean isSupport) {
        super(isSupport ? "CANDLESTICK_AT_SUPPORT" : "CANDLESTICK_AT_RESISTANCE", priority);
        this.supportResistanceService = srs;
        this.isSupport = isSupport;
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<org.example.entity.TechnicalIndicator> indicators, List<DailyPrice> prices) {
        if (prices == null || prices.size() < 3) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, "Insufficient data", getName(), getPriority());
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

        boolean matchesContext = isSupport ? isNearSupport(currentPrice, levels) : isNearResistance(currentPrice, levels);
        String contextLabel = isSupport ? "support" : "resistance";

        if (!matchesContext) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50,
                    String.format("%s but not near %s", result.label(), contextLabel),
                    getName(), getPriority());
        }

        // Use result.score() > 0 to determine bullishness (positive = bullish, negative = bearish)
        if ((result.score() > 0) == isSupport) {
            StrategySignal signal = isSupport ? StrategySignal.BUY : StrategySignal.SELL;
            return StrategyResult.withoutContribution(signal, 0.75,
                    String.format("%s on %s (score: %d)", result.label(), contextLabel, result.score()),
                    getName(), getPriority());
        } else {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50,
                    String.format("%s near %s but %s (mismatch)", result.label(), contextLabel,
                            result.score() > 0 ? "bullish" : "bearish"),
                    getName(), getPriority());
        }
    }

    private boolean isNearSupport(BigDecimal price, SupportResistanceDto levels) {
        if (levels.getMajorLevels() != null) {
            for (SupportResistanceDto.MajorLevel level : levels.getMajorLevels()) {
                if ("support".equals(level.getType()) && isWithinThreshold(price, level.getPrice())) {
                    return true;
                }
            }
        }
        if (levels.getPivots() != null) {
            if (isWithinThreshold(price, levels.getPivots().getS1())) return true;
            if (isWithinThreshold(price, levels.getPivots().getS2())) return true;
            if (isWithinThreshold(price, levels.getPivots().getS3())) return true;
        }
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
        if (levels.getMajorLevels() != null) {
            for (SupportResistanceDto.MajorLevel level : levels.getMajorLevels()) {
                if ("resistance".equals(level.getType()) && isWithinThreshold(price, level.getPrice())) {
                    return true;
                }
            }
        }
        if (levels.getPivots() != null) {
            if (isWithinThreshold(price, levels.getPivots().getR1())) return true;
            if (isWithinThreshold(price, levels.getPivots().getR2())) return true;
            if (isWithinThreshold(price, levels.getPivots().getR3())) return true;
        }
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
