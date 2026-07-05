package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.entity.TechnicalIndicator;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.util.List;

/**
 * Volume-based strategy. Detects volume spikes (>= 1.5x the 20-day average)
 * combined with price direction to confirm momentum.
 *
 * <ul>
 *   <li>High volume + rising price → BUY (accumulation)</li>
 *   <li>High volume + falling price → SELL (distribution)</li>
 *   <li>Normal volume → HOLD</li>
 * </ul>
 */
public class VolumeStrategy extends TradingStrategy {

    private static final double VOLUME_SPIKE_FACTOR = 1.5;

    public VolumeStrategy(int priority) {
        super("VOLUME", priority);
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        if (prices == null || prices.size() < 2) {
            return insufficientData();
        }

        // Calculate average over past 20 days, excluding the latest day
        int window = Math.min(20, prices.size() - 1);
        double avgVolume = prices.stream()
                .skip(1)  // Skip the latest day
                .limit(window)
                .mapToLong(p -> p.getVolume() != null ? p.getVolume() : 0L)
                .average()
                .orElse(0.0);

        if (avgVolume <= 0) {
            return insufficientData();
        }

        DailyPrice latest = prices.get(0);
        DailyPrice previous = prices.get(1);
        long latestVolume = latest.getVolume() != null ? latest.getVolume() : 0L;
        double priceChange = latest.getClosingPrice().doubleValue() - previous.getClosingPrice().doubleValue();

        if (latestVolume >= VOLUME_SPIKE_FACTOR * avgVolume) {
            if (priceChange > 0) {
                return StrategyResult.withoutContribution(StrategySignal.BUY, 0.65,
                        String.format("Volume spike with rising price (vol=%d, avg=%.0f, 1.5x=%.0f)",
                                latestVolume, avgVolume, VOLUME_SPIKE_FACTOR * avgVolume),
                        getName(), getPriority());
            } else if (priceChange < 0) {
                return StrategyResult.withoutContribution(StrategySignal.SELL, 0.65,
                        String.format("Volume spike with falling price (vol=%d, avg=%.0f, 1.5x=%.0f)",
                                latestVolume, avgVolume, VOLUME_SPIKE_FACTOR * avgVolume),
                        getName(), getPriority());
            } else {
                return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.30,
                        "Volume spike but price unchanged", getName(), getPriority());
            }
        }

        return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.20,
                String.format("Normal volume (vol=%d, avg=%.0f)", latestVolume, avgVolume),
                getName(), getPriority());
    }
}
