package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class MacdSignalCalculator implements IndicatorCalculator {

    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int SIGNAL_PERIOD = 9;

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < (MACD_SLOW + SIGNAL_PERIOD)) {
            throw new IllegalArgumentException("Need at least " + (MACD_SLOW + SIGNAL_PERIOD) + " days of price data to calculate MACD Signal");
        }

        List<BigDecimal> closes = new ArrayList<>(prices.size());
        for (DailyPrice p : prices) {
            closes.add(p.getClosingPrice());
        }

        // Compute MACD line series in a single O(n) pass
        List<BigDecimal> macdLineSeries = computeMacdLineSeries(closes);

        // Compute EMA(9) on the MACD line series for the signal line
        return computeEma(macdLineSeries, SIGNAL_PERIOD);
    }

    private List<BigDecimal> computeMacdLineSeries(List<BigDecimal> closes) {
        List<BigDecimal> macdValues = new ArrayList<>();

        // Initial SMA for EMA12 and EMA26
        BigDecimal sma12Sum = BigDecimal.ZERO;
        for (int i = 0; i < MACD_FAST; i++) {
            sma12Sum = sma12Sum.add(closes.get(i));
        }
        BigDecimal sma26Sum = BigDecimal.ZERO;
        for (int i = 0; i < MACD_SLOW; i++) {
            sma26Sum = sma26Sum.add(closes.get(i));
        }

        BigDecimal ema12 = sma12Sum.divide(BigDecimal.valueOf(MACD_FAST), 4, RoundingMode.HALF_UP);
        BigDecimal ema26 = sma26Sum.divide(BigDecimal.valueOf(MACD_SLOW), 4, RoundingMode.HALF_UP);
        BigDecimal k12 = BigDecimal.valueOf(2.0 / (MACD_FAST + 1));
        BigDecimal k26 = BigDecimal.valueOf(2.0 / (MACD_SLOW + 1));
        BigDecimal oneMinusK12 = BigDecimal.ONE.subtract(k12);
        BigDecimal oneMinusK26 = BigDecimal.ONE.subtract(k26);

        // Run EMA12 up to MACD_SLOW to sync both EMAs
        for (int i = MACD_FAST; i < MACD_SLOW; i++) {
            ema12 = closes.get(i).multiply(k12).add(ema12.multiply(oneMinusK12));
        }

        // Compute MACD line values from MACD_SLOW onward in a single pass
        for (int i = MACD_SLOW; i < closes.size(); i++) {
            BigDecimal c = closes.get(i);
            ema12 = c.multiply(k12).add(ema12.multiply(oneMinusK12));
            ema26 = c.multiply(k26).add(ema26.multiply(oneMinusK26));
            macdValues.add(ema12.subtract(ema26));
        }

        return macdValues;
    }

    private BigDecimal computeEma(List<BigDecimal> values, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(values.get(i));
        }
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal oneMinusK = BigDecimal.ONE.subtract(k);

        for (int i = period; i < values.size(); i++) {
            ema = values.get(i).multiply(k).add(ema.multiply(oneMinusK));
        }

        return ema.setScale(4, RoundingMode.HALF_UP);
    }
}