package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Volume-based strategy. Detects volume spikes (>= 1.5x the 20-day average)
 * using the pre-computed VOLUME_RATIO indicator, combined with price direction
 * to confirm momentum.
 *
 * <p>The VOLUME_RATIO indicator is computed per-candle by
 * {@link org.example.service.calculator.VolumeRatioCalculator} and stored in
 * the database, exactly like RSI and MACD indicators. This allows the strategy
 * signal to change per candle when viewing different timeframes.</p>
 *
 * <ul>
 *   <li>High volume + rising price → BUY (accumulation)</li>
 *   <li>High volume + falling price → SELL (distribution)</li>
 *   <li>Normal volume → HOLD</li>
 * </ul>
 */
public class VolumeStrategy extends TradingStrategy {

    private static final int LOOKBACK_DAYS = 20;
    private final double spikeFactor;

    public VolumeStrategy(int priority, @Value("${strategy.volume.spike-factor:1.5}") double spikeFactor) {
        super("VOLUME", priority);
        this.spikeFactor = spikeFactor;
    }

    // Close-position bands (Fix 6): (close - low) / (high - low)
    private static final double STRONG_ACCUMULATION = 0.7;
    private static final double MILD_ACCUMULATION = 0.5;
    private static final double STRONG_DISTRIBUTION = 0.3;

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        // Resolve the stored VOLUME_RATIO indicator (like RSIStrategy resolves RSI)
        Optional<Double> volumeRatioOpt = resolveIndicator(indicators, IndicatorType.VOLUME_RATIO);
        if (volumeRatioOpt.isEmpty() || prices == null || prices.size() < 2) {
            return insufficientData();
        }

        double volumeRatio = volumeRatioOpt.get();

        // Compute display values from raw price data (for tooltip)
        int window = Math.min(LOOKBACK_DAYS, prices.size() - 1);
        double avgVolume = prices.stream()
                .skip(1)
                .limit(window)
                .mapToLong(p -> p.getVolume() != null ? p.getVolume() : 0L)
                .average()
                .orElse(0.0);

        DailyPrice latest = prices.get(prices.size() - 1);
        long latestVolume = latest.getVolume() != null ? latest.getVolume() : 0L;
        double spikeThreshold = spikeFactor * avgVolume;

        String reason;
        StrategySignal signal;
        double confidence;

        // Resolve OBV for secondary confirmation (Fix 3)
        Optional<Double> obvOpt = resolveIndicator(indicators, IndicatorType.OBV);

        if (volumeRatio >= spikeFactor) {
            // Use the indicator ratio for signal decision, price data for direction
            if (latest.getClosingPrice().compareTo(latest.getOpeningPrice()) > 0) {
                // High volume + price up = accumulation
                signal = StrategySignal.BUY;
            } else {
                // High volume + price down = distribution
                signal = StrategySignal.SELL;
            }

            // Fix 5: logarithmic confidence curve (smooth growth, no premature cap)
            confidence = Math.min(1.0, 0.7 * (Math.log(volumeRatio) / Math.log(spikeFactor)));

            // Fix 6: close-position-in-range for accumulation/distribution strength
            double closePosition = computeClosePosition(latest);
            String positionDesc;
            if (latest.getHighPrice() != null && latest.getLowPrice() != null
                    && latest.getHighPrice().compareTo(latest.getLowPrice()) == 0) {
                positionDesc = "zero-range (neutral)";
            } else if (closePosition >= STRONG_ACCUMULATION) {
                positionDesc = "strong accumulation";
                confidence = Math.min(1.0, confidence + 0.05);
            } else if (closePosition <= STRONG_DISTRIBUTION) {
                positionDesc = "strong distribution";
                confidence = Math.min(1.0, confidence + 0.05);
            } else if (closePosition >= MILD_ACCUMULATION) {
                positionDesc = "mild accumulation";
            } else if (closePosition <= MILD_ACCUMULATION) {
                positionDesc = "mild distribution";
            } else {
                positionDesc = "neutral range";
            }

            // Fix 3: OBV secondary confirmation layer
            String obvStatus = "OBV n/a";
            if (obvOpt.isPresent()) {
                ObvTrend obvTrend = resolveObvTrend(indicators);
                obvStatus = "OBV " + obvTrend.name().toLowerCase();
                if (signal == StrategySignal.BUY && obvTrend == ObvTrend.UP) {
                    // Volume spike + price up + OBV up → boosted BUY
                    confidence = Math.min(1.0, confidence + 0.1);
                } else if (signal == StrategySignal.SELL && obvTrend == ObvTrend.DOWN) {
                    // Volume spike + price down + OBV down → boosted SELL
                    confidence = Math.min(1.0, confidence + 0.1);
                } else if (obvTrend != ObvTrend.NEUTRAL
                        && ((signal == StrategySignal.BUY && obvTrend == ObvTrend.DOWN)
                            || (signal == StrategySignal.SELL && obvTrend == ObvTrend.UP))) {
                    // OBV contradicts price direction → divergence warning, reduce confidence 15%
                    confidence = confidence * 0.85;
                }
            }

            reason = String.format(
                    "Volume spike %.1fx avg (%.0f vs %.0f avg), %s — %s | close pos %.0f%% | %s",
                    volumeRatio, (double) latestVolume, avgVolume,
                    positionDesc, signal == StrategySignal.BUY ? "accumulation" : "distribution",
                    closePosition * 100, obvStatus);
        } else {
            signal = StrategySignal.HOLD;
            confidence = 0.3;
            reason = String.format("Normal volume (%.0f vs %.0f avg, threshold %.0f, ratio %.2f)",
                    (double) latestVolume, avgVolume, spikeThreshold, volumeRatio);
        }

        return new StrategyResult(
                signal,
                confidence,
                reason,
                getName(),
                getPriority(),
                0.0, // contribution will be calculated by engine
                latestVolume,
                avgVolume,
                spikeThreshold
        );
    }

    /**
     * Computes the close position within the day's range: (close - low) / (high - low).
     * Returns 0.5 (neutral) when the range is zero.
     */
    private double computeClosePosition(DailyPrice latest) {
        BigDecimal high = latest.getHighPrice();
        BigDecimal low = latest.getLowPrice();
        BigDecimal close = latest.getClosingPrice();
        if (high == null || low == null || close == null || high.compareTo(low) == 0) {
            return 0.5; // neutral
        }
        double range = high.subtract(low).doubleValue();
        return (close.subtract(low).doubleValue()) / range;
    }

    /**
     * Resolves the 5-day OBV trend from the indicators list (mirrors
     * {@code StrategyConditionService.evaluateObvTrend()} which uses repository access).
     * Current OBV vs OBV 5 days ago (sorted by date descending, skip 4, take 5th).
     */
    private ObvTrend resolveObvTrend(List<TechnicalIndicator> indicators) {
        if (indicators == null) {
            return ObvTrend.NEUTRAL;
        }
        List<TechnicalIndicator> obvList = indicators.stream()
                .filter(ti -> ti.getIndicatorType() == IndicatorType.OBV)
                .sorted(java.util.Comparator
                        .comparing(org.example.entity.TechnicalIndicator::getCalculationDate).reversed())
                .toList();
        if (obvList.size() < 2) {
            return ObvTrend.NEUTRAL;
        }
        double current = obvList.get(0).getValue().doubleValue();
        // 5th most recent: skip 4, take the next (or last available if fewer than 5)
        int idx = Math.min(4, obvList.size() - 1);
        double fiveDaysAgo = obvList.get(idx).getValue().doubleValue();
        if (current > fiveDaysAgo) {
            return ObvTrend.UP;
        } else if (current < fiveDaysAgo) {
            return ObvTrend.DOWN;
        }
        return ObvTrend.NEUTRAL;
    }

    /**
     * OBV trend direction used for secondary confirmation.
     */
    private enum ObvTrend {
        UP, DOWN, NEUTRAL
    }

    @Override
    protected StrategyResult insufficientData() {
        return new StrategyResult(
                StrategySignal.HOLD,
                0.0,
                "Insufficient price data for volume analysis",
                getName(),
                getPriority(),
                0.0,
                null,
                null,
                null
        );
    }
}
