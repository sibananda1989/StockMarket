package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.entity.TechnicalIndicator;
import org.example.service.calculator.CandlestickPatternCalculator;
import org.example.service.calculator.CandlestickPatternCalculator.Pattern;
import org.example.service.calculator.CandlestickPatternCalculator.Result;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.util.List;

/**
 * Pure candlestick pattern strategy that detects individual candlestick patterns
 * without requiring support/resistance context.
 *
 * <p>Bullish patterns → BUY signal (confidence scales with pattern score).
 * Bearish patterns → SELL signal (confidence scales with pattern score).
 * No pattern detected → HOLD.</p>
 *
 * <p>Pattern strength is derived from the pattern score returned by
 * {@link CandlestickPatternCalculator}:
 * <ul>
 *   <li>Score ±3 (morning/evening star): confidence 0.85</li>
 *   <li>Score ±2 (engulfing, piercing, dark cloud, hammer, shooting star): confidence 0.70</li>
 *   <li>Score ±1 (harami): confidence 0.55</li>
 * </ul></p>
 */
public class CandlestickPatternStrategy extends TradingStrategy {

    public CandlestickPatternStrategy(int priority) {
        super("CANDLESTICK_PATTERN", priority);
    }

    @Override
    public StrategyResult evaluate(Long stockId,
                                   List<TechnicalIndicator> indicators,
                                   List<DailyPrice> prices) {
        if (prices == null || prices.size() < 3) {
            return insufficientData();
        }

        CandlestickPatternCalculator calculator = new CandlestickPatternCalculator();
        Result result = calculator.detect(prices);

        if (result.pattern() == Pattern.NONE || result.label() == null) {
            return StrategyResult.withoutContribution(
                    StrategySignal.HOLD, 0.50,
                    "No candlestick pattern detected",
                    getName(), getPriority());
        }

        // Determine signal and confidence from pattern type and score
        if (result.score() > 0) {
            double confidence = confidenceFromScore(result.score());
            return StrategyResult.withoutContribution(
                    StrategySignal.BUY, confidence,
                    String.format("Bullish pattern: %s (score: %d)", result.label(), result.score()),
                    getName(), getPriority());
        }

        if (result.score() < 0) {
            double confidence = confidenceFromScore(Math.abs(result.score()));
            return StrategyResult.withoutContribution(
                    StrategySignal.SELL, confidence,
                    String.format("Bearish pattern: %s (score: %d)", result.label(), result.score()),
                    getName(), getPriority());
        }

        // Unknown pattern (shouldn't happen if calculator returns known patterns)
        return StrategyResult.withoutContribution(
                StrategySignal.HOLD, 0.30,
                String.format("Pattern detected but not classified: %s", result.label()),
                getName(), getPriority());
    }

    /**
     * Maps pattern strength score to confidence.
     * Score ±3 (star patterns) → high confidence
     * Score ±2 (engulfing, hammer, etc.) → medium-high confidence
     * Score ±1 (harami) → moderate confidence
     * Fuzzy matches with percentage → scaled proportionally
     */
    private double confidenceFromScore(int absScore) {
        if (absScore >= 90) {
            // Fuzzy match with very high match percentage
            return 0.80;
        } else if (absScore >= 3) {
            // Star patterns (morning/evening star)
            return 0.85;
        } else if (absScore >= 2) {
            // Strong patterns (engulfing, hammer, shooting star, piercing line, dark cloud)
            return 0.70;
        } else if (absScore >= 1) {
            // Moderate patterns (harami)
            return 0.55;
        }
        // Low-score or fuzzy patterns
        return 0.45;
    }
}
