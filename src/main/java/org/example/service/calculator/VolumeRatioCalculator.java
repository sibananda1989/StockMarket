package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Volume Ratio Calculator.
 *
 * Computes the ratio of each day's trading volume to its 20-day average volume.
 * This is a per-candle indicator that shows how today's volume compares to recent
 * norms — exactly like RSI or MACD indicators are computed per candle.
 *
 * Formula:
 *   volumeRatio = todayVolume / averageVolumeOfPrevious20Days
 *
 * Parameters: 20-day lookback window
 * Range: ≥ 0 (values >= 1.5 indicate a significant volume spike)
 * Minimum data: 21 days (1 current + 20 prior for the average)
 *
 * Signals:
 *   ratio ≥ 1.5 → Volume spike (used by VolumeStrategy)
 *   ratio < 1.5 → Normal volume
 */
public class VolumeRatioCalculator implements IndicatorCalculator {

    private static final int LOOKBACK_DAYS = 20;

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices == null || prices.size() <= LOOKBACK_DAYS) {
            throw new IllegalArgumentException(
                    "Need at least " + (LOOKBACK_DAYS + 1) + " days of price data to calculate Volume Ratio");
        }

        // Calculate latest ratio: last day's volume / average of previous 20 days
        DailyPrice latest = prices.get(prices.size() - 1);
        long latestVolume = latest.getVolume() != null ? latest.getVolume() : 0L;

        if (latestVolume <= 0) {
            return BigDecimal.ZERO;
        }

        double sumVolume = 0.0;
        for (int i = prices.size() - LOOKBACK_DAYS - 1; i < prices.size() - 1; i++) {
            Long v = prices.get(i).getVolume();
            sumVolume += (v != null ? v : 0L);
        }
        double avgVolume = sumVolume / LOOKBACK_DAYS;

        if (avgVolume <= 0) {
            return BigDecimal.ZERO;
        }

        double ratio = (double) latestVolume / avgVolume;
        return BigDecimal.valueOf(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.VOLUME_RATIO;
    }
}
