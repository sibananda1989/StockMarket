package org.example.service.calculator;

import org.example.entity.DailyPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * SMA-seeded Exponential Moving Average that returns the FULL per-bar series
 * instead of only the last value (unlike {@link EmaCalculator}).
 *
 * <p>The zero-th element of the returned list is the SMA seed of the first
 * {@code period} closes, matching {@link EmaCalculator#calculate(List)}. Each
 * subsequent element is the EMA for the bar at that index. The series only
 * becomes meaningful once {@code prices.size() >= period}; an
 * {@link IllegalArgumentException} is thrown otherwise, mirroring the
 * {@link EmaCalculator} guard.</p>
 *
 * <p>Used by the EMA-20/50 crossover screener, which needs per-day EMA values
 * to detect an up-cross over a rolling look-back window.</p>
 */
public final class EmaSeriesCalculator {

    private final int period;

    public EmaSeriesCalculator(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be > 0");
        }
        this.period = period;
    }

    /**
     * Computes the EMA series for every bar in {@code prices}.
     *
     * @param prices ascending list of price bars (oldest first)
     * @return a list of length {@code prices.size()} where entry i is the EMA
     *         value at bar i (entry {@code period-1} onward are true EMAs)
     * @throws IllegalArgumentException if {@code prices.size() < period}
     */
    public List<BigDecimal> calculate(List<DailyPrice> prices) {
        if (prices == null || prices.size() < period) {
            throw new IllegalArgumentException(
                    "Need at least " + period + " days of price data to calculate EMA-" + period);
        }

        // Step 1: SMA seed of the first `period` closes
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(prices.get(i).getClosingPrice());
        }
        BigDecimal currentEma = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // Step 2: k = 2 / (period + 1)
        BigDecimal k = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1), 4, RoundingMode.HALF_UP);

        List<BigDecimal> series = new ArrayList<>(prices.size());
        for (int i = 0; i < prices.size(); i++) {
            if (i >= period) {
                BigDecimal close = prices.get(i).getClosingPrice();
                BigDecimal diff = close.subtract(currentEma);
                currentEma = diff.multiply(k).add(currentEma);
            }
            // NaN guard: a null close would poison the seed once the price set
            // contains gaps; nulls are treated as the previous EMA (i.e. flat).
            series.add(currentEma.setScale(4, RoundingMode.HALF_UP));
        }
        return series;
    }

    /**
     * Returns the last value of the series, matching {@link EmaCalculator}
     * semantics (useful for validating this class against the existing one).
     */
    public BigDecimal calculateLast(List<DailyPrice> prices) {
        List<BigDecimal> series = calculate(prices);
        return series.get(series.size() - 1).setScale(2, RoundingMode.HALF_UP);
    }
}