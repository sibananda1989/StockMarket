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
 * Bollinger Band strategy. Identifies mean-reversion opportunities when
 * price touches or breaches the upper or lower band.
 *
 * <ul>
 *   <li>Price < lower band → BUY (oversold)</li>
 *   <li>Price > upper band → SELL (overbought)</li>
 *   <li>Within bands → HOLD</li>
 * </ul>
 */
public class BollingerBandStrategy extends TradingStrategy {

    public BollingerBandStrategy(int priority) {
        super("BOLLINGER", priority);
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        Optional<Double> upperOpt = resolveIndicator(indicators, IndicatorType.BOLLINGER_UPPER);
        Optional<Double> lowerOpt = resolveIndicator(indicators, IndicatorType.BOLLINGER_LOWER);

        if (upperOpt.isEmpty() || lowerOpt.isEmpty()) {
            return insufficientData();
        }
        if (prices == null || prices.isEmpty()) {
            return insufficientData();
        }

        double upper = upperOpt.get();
        double lower = lowerOpt.get();
        double price = prices.get(prices.size() - 1).getClosingPrice().doubleValue();

        // Determine market regime from ADX (Bollinger Rule 6/7/8: band tags are NOT reversal signals in trends)
        boolean isTrending = indicators.stream()
                .filter(ti -> ti.getIndicatorType() == IndicatorType.ADX)
                .findFirst()
                .map(ti -> ti.getValue().doubleValue() > 25.0)
                .orElse(false);

        if (price < lower) {
            if (isTrending) {
                // Bollinger Rule 7/8: In trends, price walking lower band = downtrend continuation
                return StrategyResult.withoutContribution(StrategySignal.SELL, 0.75,
                        String.format("Trending: price below lower band (price=%.2f, lower=%.2f)", price, lower),
                        getName(), getPriority());
            } else {
                // Mean-reversion: below lower band = oversold bounce
                return StrategyResult.withoutContribution(StrategySignal.BUY, 0.75,
                        String.format("Price below lower band (price=%.2f, lower=%.2f)", price, lower),
                        getName(), getPriority());
            }
        } else if (price > upper) {
            if (isTrending) {
                // Bollinger Rule 7/8: In trends, price walking upper band = uptrend continuation
                return StrategyResult.withoutContribution(StrategySignal.BUY, 0.75,
                        String.format("Trending: price above upper band (price=%.2f, upper=%.2f)", price, upper),
                        getName(), getPriority());
            } else {
                // Mean-reversion: above upper band = overbought pullback
                return StrategyResult.withoutContribution(StrategySignal.SELL, 0.75,
                        String.format("Price above upper band (price=%.2f, upper=%.2f)", price, upper),
                        getName(), getPriority());
            }
        } else {
            double mid = (upper + lower) / 2.0;
            double bandWidth = upper - lower;
            double position = bandWidth > 0 ? (price - mid) / (bandWidth / 2.0) : 0.0;
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.30,
                    String.format("Price within bands (position=%.0f%%)", position * 100),
                    getName(), getPriority());
        }
    }
}
