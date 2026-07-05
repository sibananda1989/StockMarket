package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Stochastic RSI (StochRSI) Calculator
 *
 * Applies the Stochastic formula to RSI values instead of price.
 * More sensitive than plain RSI — catches overbought/oversold conditions faster.
 *
 * Formula:
 *   Step 1: Compute RSI(14) for each bar → RSI series
 *   Step 2: StochRSI = (Current RSI - Lowest RSI in N periods) / (Highest RSI in N periods - Lowest RSI in N periods) × 100
 *
 * Parameters:
 *   rsiPeriod: 14 (default)
 *   stochLookback: 14 (default)
 *
 * Range: 0-100
 * Minimum data: ~28 days (14 for RSI warmup + 14 for Stochastic lookback)
 *
 * Signals:
 *   StochRSI < 20 → Oversold (buy signal)
 *   StochRSI > 80 → Overbought (sell signal)
 *   StochRSI crossing above 20 → Bullish
 *   StochRSI crossing below 80 → Bearish
 */
public class StochRsiCalculator implements IndicatorCalculator {

    private final int rsiPeriod;
    private final int stochLookback;

    public StochRsiCalculator() {
        this(14, 14);
    }

    public StochRsiCalculator(int rsiPeriod, int stochLookback) {
        this.rsiPeriod = rsiPeriod;
        this.stochLookback = stochLookback;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        int minRequired = rsiPeriod + stochLookback + 1;
        if (prices.size() < minRequired) {
            throw new IllegalArgumentException(
                    "Need at least " + minRequired + " days of price data to calculate StochRSI");
        }

        // Step 1: Compute RSI series
        List<BigDecimal> rsiSeries = computeRsiSeries(prices);

        // Step 2: Apply Stochastic formula to RSI series
        int n = rsiSeries.size();
        int lookbackStart = n - stochLookback;

        // Find highest and lowest RSI in the lookback window
        BigDecimal highestRsi = rsiSeries.get(lookbackStart);
        BigDecimal lowestRsi = rsiSeries.get(lookbackStart);

        for (int i = lookbackStart + 1; i < n; i++) {
            BigDecimal rsi = rsiSeries.get(i);
            if (rsi.compareTo(highestRsi) > 0) {
                highestRsi = rsi;
            }
            if (rsi.compareTo(lowestRsi) < 0) {
                lowestRsi = rsi;
            }
        }

        BigDecimal currentRsi = rsiSeries.get(n - 1);
        BigDecimal denominator = highestRsi.subtract(lowestRsi);

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(50); // RSI is flat, return neutral
        }

        // StochRSI = (Current RSI - Lowest RSI) / (Highest RSI - Lowest RSI) × 100
        BigDecimal stochRsi = currentRsi.subtract(lowestRsi)
                .divide(denominator, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return stochRsi.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes RSI for each bar using Wilder's smoothing method.
     * Returns a list of RSI values (first rsiPeriod values are null/skipped).
     */
    private List<BigDecimal> computeRsiSeries(List<DailyPrice> prices) {
        List<BigDecimal> rsiValues = new ArrayList<>();

        // Compute gains and losses
        BigDecimal[] gains = new BigDecimal[prices.size() - 1];
        BigDecimal[] losses = new BigDecimal[prices.size() - 1];
        for (int i = 0; i < gains.length; i++) {
            BigDecimal change = prices.get(i + 1).getClosingPrice()
                    .subtract(prices.get(i).getClosingPrice());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains[i] = change;
                losses[i] = BigDecimal.ZERO;
            } else {
                gains[i] = BigDecimal.ZERO;
                losses[i] = change.abs();
            }
        }

        // Initial SMA for first rsiPeriod
        BigDecimal sumGain = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;
        for (int i = 0; i < rsiPeriod; i++) {
            sumGain = sumGain.add(gains[i]);
            sumLoss = sumLoss.add(losses[i]);
        }
        BigDecimal avgGain = sumGain.divide(BigDecimal.valueOf(rsiPeriod), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = sumLoss.divide(BigDecimal.valueOf(rsiPeriod), 4, RoundingMode.HALF_UP);

        // Wilder's smoothing for remaining periods
        for (int i = rsiPeriod; i < gains.length; i++) {
            avgGain = avgGain.multiply(BigDecimal.valueOf(rsiPeriod - 1))
                    .add(gains[i])
                    .divide(BigDecimal.valueOf(rsiPeriod), 4, RoundingMode.HALF_UP);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(rsiPeriod - 1))
                    .add(losses[i])
                    .divide(BigDecimal.valueOf(rsiPeriod), 4, RoundingMode.HALF_UP);

            BigDecimal rsi;
            if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
                rsi = BigDecimal.valueOf(100);
            } else {
                BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
                rsi = BigDecimal.valueOf(100)
                        .subtract(BigDecimal.valueOf(100).divide(
                                BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
            }
            rsiValues.add(rsi);
        }

        return rsiValues;
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.STOCH_RSI;
    }
}
