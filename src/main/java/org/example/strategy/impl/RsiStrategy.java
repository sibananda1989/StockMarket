package org.example.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.util.List;
import java.util.Optional;

/**
 * RSI-based strategy. Uses the Relative Strength Index to identify
 * overbought (RSI > 70) and oversold (RSI < 30) conditions.
 */
@Slf4j
public class RsiStrategy extends TradingStrategy {

    public RsiStrategy(int priority) {
        super("RSI", priority);
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        Optional<Double> rsiOpt = resolveIndicator(indicators, IndicatorType.RSI);
        if (rsiOpt.isEmpty()) {
            return insufficientData();
        }

        double rsi = rsiOpt.get();

        if (rsi <= 30) {
            return StrategyResult.withoutContribution(StrategySignal.BUY, 0.85,
                    String.format("RSI oversold (%.1f)", rsi), getName(), getPriority());
        } else if (rsi >= 70) {
            return StrategyResult.withoutContribution(StrategySignal.SELL, 0.80,
                    String.format("RSI overbought (%.1f)", rsi), getName(), getPriority());
        } else {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.45,
                    String.format("RSI neutral (%.1f)", rsi), getName(), getPriority());
        }
    }
}
