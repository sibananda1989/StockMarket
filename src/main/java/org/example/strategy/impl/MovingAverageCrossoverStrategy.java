package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.util.List;
import java.util.Optional;

/**
 * Moving average crossover strategy. Detects SMA crossovers (golden/death cross)
 * as primary signal, falls back to alignment check when previous data is unavailable.
 *
 * <ul>
 *   <li>SMA20 crosses above SMA50 → BUY (golden cross, high confidence)</li>
 *   <li>SMA20 crosses below SMA50 → SELL (death cross, high confidence)</li>
 *   <li>Price > SMA20 > SMA50 → BUY (bullish alignment, lower confidence)</li>
 *   <li>Price < SMA20 < SMA50 → SELL (bearish alignment, lower confidence)</li>
 *   <li>Mixed → HOLD</li>
 * </ul>
 */
public class MovingAverageCrossoverStrategy extends TradingStrategy {

    private static final double CROSSOVER_CONFIDENCE = 0.90;
    private static final double ALIGNMENT_CONFIDENCE = 0.60;

    public MovingAverageCrossoverStrategy(int priority) {
        super("MA_CROSSOVER", priority);
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        Optional<Double> sma20Opt = resolveIndicator(indicators, IndicatorType.SMA_20);
        Optional<Double> sma50Opt = resolveIndicator(indicators, IndicatorType.SMA_50);

        if (sma20Opt.isEmpty() || sma50Opt.isEmpty()) {
            return insufficientData();
        }
        if (prices == null || prices.isEmpty()) {
            return insufficientData();
        }

        double sma20 = sma20Opt.get();
        double sma50 = sma50Opt.get();
        double price = prices.get(prices.size() - 1).getClosingPrice().doubleValue();

        // Detect SMA crossover
        Optional<Double> prevSma20Opt = resolvePreviousIndicator(indicators, IndicatorType.SMA_20);
        Optional<Double> prevSma50Opt = resolvePreviousIndicator(indicators, IndicatorType.SMA_50);

        if (prevSma20Opt.isPresent() && prevSma50Opt.isPresent()) {
            double prevSma20 = prevSma20Opt.get();
            double prevSma50 = prevSma50Opt.get();
            boolean wasBelow = prevSma20 < prevSma50;
            boolean isAbove = sma20 > sma50;

            if (wasBelow && isAbove) {
                return StrategyResult.withoutContribution(StrategySignal.BUY, CROSSOVER_CONFIDENCE,
                        String.format("Golden cross: SMA20=%.2f crossed above SMA50=%.2f", sma20, sma50),
                        getName(), getPriority());
            }

            boolean wasAbove = prevSma20 > prevSma50;
            boolean isBelow = sma20 < sma50;

            if (wasAbove && isBelow) {
                return StrategyResult.withoutContribution(StrategySignal.SELL, CROSSOVER_CONFIDENCE,
                        String.format("Death cross: SMA20=%.2f crossed below SMA50=%.2f", sma20, sma50),
                        getName(), getPriority());
            }
        }

        // Fallback to alignment check when no crossover or no previous data
        if (price > sma20 && sma20 > sma50) {
            return StrategyResult.withoutContribution(StrategySignal.BUY, ALIGNMENT_CONFIDENCE,
                    String.format("Bullish alignment: price=%.2f > SMA20=%.2f > SMA50=%.2f", price, sma20, sma50),
                    getName(), getPriority());
        } else if (price < sma20 && sma20 < sma50) {
            return StrategyResult.withoutContribution(StrategySignal.SELL, ALIGNMENT_CONFIDENCE,
                    String.format("Bearish alignment: price=%.2f < SMA20=%.2f < SMA50=%.2f", price, sma20, sma50),
                    getName(), getPriority());
        } else {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50,
                    String.format("Mixed alignment: price=%.2f, SMA20=%.2f, SMA50=%.2f", price, sma20, sma50),
                    getName(), getPriority());
        }
    }
}
