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
 * MACD crossover strategy. Detects actual crossovers by comparing today's
 * MACD line/signal relationship with yesterday's.
 * <ul>
 *   <li>Line crosses above signal → BUY (bullish crossover)</li>
 *   <li>Line crosses below signal → SELL (bearish crossover)</li>
 *   <li>No crossover → HOLD</li>
 * </ul>
 * Confidence scales with the gap magnitude (capped at 0.90).
 * Falls back to position-based signal when previous data is unavailable.
 */
public class MacdStrategy extends TradingStrategy {

    private static final double CONFIDENCE_SCALE = 0.05;
    private static final double MAX_CONFIDENCE = 0.90;
    private static final double POSITION_CONFIDENCE = 0.40;

    public MacdStrategy(int priority) {
        super("MACD", priority);
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        Optional<Double> macdLineOpt = resolveIndicator(indicators, IndicatorType.MACD_LINE);
        Optional<Double> macdSignalOpt = resolveIndicator(indicators, IndicatorType.MACD_SIGNAL);

        if (macdLineOpt.isEmpty() || macdSignalOpt.isEmpty()) {
            return insufficientData();
        }

        double macdLine = macdLineOpt.get();
        double macdSignal = macdSignalOpt.get();
        double gap = macdLine - macdSignal;
        double absGap = Math.abs(gap);
        double confidence = Math.min(absGap * CONFIDENCE_SCALE, MAX_CONFIDENCE);

        // Level 1: Zero-line crossover (highest priority — confirms major trend shift)
        Optional<Double> prevMacdLineOpt = resolvePreviousIndicator(indicators, IndicatorType.MACD_LINE);
        if (prevMacdLineOpt.isPresent()) {
            double prevMacdLine = prevMacdLineOpt.get();
            boolean wasBelowZero = prevMacdLine < 0;
            boolean isAboveZero = macdLine > 0;
            boolean wasAboveZero = prevMacdLine > 0;
            boolean isBelowZero = macdLine < 0;

            if (wasBelowZero && isAboveZero) {
                return StrategyResult.withoutContribution(StrategySignal.BUY, Math.min(confidence, MAX_CONFIDENCE),
                        String.format("MACD zero-line bullish crossover (line=%.2f crossed above 0)", macdLine),
                        getName(), getPriority());
            }
            if (wasAboveZero && isBelowZero) {
                return StrategyResult.withoutContribution(StrategySignal.SELL, Math.min(confidence, MAX_CONFIDENCE),
                        String.format("MACD zero-line bearish crossover (line=%.2f crossed below 0)", macdLine),
                        getName(), getPriority());
            }
        }

        // Level 2: Signal-line crossover
        Optional<Double> prevMacdSignalOpt = resolvePreviousIndicator(indicators, IndicatorType.MACD_SIGNAL);
        if (prevMacdLineOpt.isPresent() && prevMacdSignalOpt.isPresent()) {
            double prevMacdLine = prevMacdLineOpt.get();
            double prevMacdSignal = prevMacdSignalOpt.get();
            boolean wasBelow = prevMacdLine < prevMacdSignal;
            boolean wasAbove = prevMacdLine > prevMacdSignal;
            boolean isAbove = gap > 0;
            boolean isBelow = gap < 0;

            if (wasBelow && isAbove) {
                return StrategyResult.withoutContribution(StrategySignal.BUY, Math.min(confidence, MAX_CONFIDENCE),
                        String.format("MACD bullish crossover (line=%.2f, signal=%.2f, gap=%.2f)", macdLine, macdSignal, gap),
                        getName(), getPriority());
            } else if (wasAbove && isBelow) {
                return StrategyResult.withoutContribution(StrategySignal.SELL, Math.min(confidence, MAX_CONFIDENCE),
                        String.format("MACD bearish crossover (line=%.2f, signal=%.2f, gap=%.2f)", macdLine, macdSignal, gap),
                        getName(), getPriority());
            }
        }

        // Level 3: Position-based (no crossover detected — relative position with zero-line context)
        if (gap > 0) {
            // MACD line above signal — bullish bias
            String context = (macdLine > 0 && macdSignal > 0) ? "both above zero" : "below zero";
            return StrategyResult.withoutContribution(StrategySignal.BUY, Math.min(confidence, POSITION_CONFIDENCE),
                    String.format("MACD line above signal (line=%.2f, signal=%.2f, %s)", macdLine, macdSignal, context),
                    getName(), getPriority());
        } else if (gap < 0) {
            // MACD line below signal — bearish bias
            String context = (macdLine < 0 && macdSignal < 0) ? "both below zero" : "above zero";
            return StrategyResult.withoutContribution(StrategySignal.SELL, Math.min(confidence, POSITION_CONFIDENCE),
                    String.format("MACD line below signal (line=%.2f, signal=%.2f, %s)", macdLine, macdSignal, context),
                    getName(), getPriority());
        } else {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.30,
                    "MACD line equals signal line", getName(), getPriority());
        }
    }
}
