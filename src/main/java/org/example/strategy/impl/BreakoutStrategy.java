package org.example.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.SupportResistanceLevel;
import org.example.entity.TechnicalIndicator;
import org.example.service.BreakoutDetector;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.util.List;
import java.util.Optional;

/**
 * Breakout strategy based on price action and volume.
 * Leverages the BreakoutDetector service to identify patterns.
 */
@Slf4j
public class BreakoutStrategy extends TradingStrategy {

    private final BreakoutDetector breakoutDetector;

    public BreakoutStrategy(int priority, BreakoutDetector breakoutDetector) {
        super("BREAKOUT", priority);
        this.breakoutDetector = breakoutDetector;
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        if (prices == null || prices.size() < 20) {
            return insufficientData();
        }

        BreakoutDetector.BreakoutResult breakout = breakoutDetector.detect(prices, stockId);

        if (breakout == null || breakout.score == 0) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.10, "No significant breakout detected", getName(), getPriority());
        }

        double confidence;
        StrategySignal signal;
        String reason;

        if (breakout.volumeBreakout) {
            confidence = 0.70;
            signal = StrategySignal.BUY;
            reason = String.format("Volume breakout above resistance: %s", breakout.description);
        } else if (breakout.gapUp) {
            confidence = 0.60;
            signal = StrategySignal.BUY;
            reason = String.format("Gap up: %s", breakout.description);
        } else if (breakout.gapDown) {
            confidence = 0.60;
            signal = StrategySignal.SELL;
            reason = String.format("Gap down: %s", breakout.description);
        } else if (breakout.rangeBreakout) {
            confidence = 0.55;
            signal = (breakout.score > 0) ? StrategySignal.BUY : StrategySignal.SELL; // Default direction from score
            reason = String.format("Range breakout: %s", breakout.description);
        } else {
            // Fallback for unexpected scenarios, though score should handle it
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.10, breakout.description, getName(), getPriority());
        }

        return StrategyResult.withoutContribution(signal, confidence, reason, getName(), getPriority());
    }
}
