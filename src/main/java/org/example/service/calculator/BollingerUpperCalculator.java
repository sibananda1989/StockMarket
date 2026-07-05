package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class BollingerUpperCalculator implements IndicatorCalculator {

    private final int period;
    private final double multiplier;

    public BollingerUpperCalculator(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Need at least " + period + " days of price data to calculate Bollinger Upper");
        }

        // Typical price? Usually Bollinger Bands use closing price.
        // Calculate SMA of closing prices
        double sum = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i).getClosingPrice().doubleValue();
        }
        double sma = sum / period;

        // Calculate standard deviation
        double varianceSum = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            double diff = prices.get(i).getClosingPrice().doubleValue() - sma;
            varianceSum += diff * diff;
        }
        double stddev = Math.sqrt(varianceSum / (period - 1));

        double upper = sma + (multiplier * stddev);
        return BigDecimal.valueOf(upper).setScale(2, RoundingMode.HALF_UP);
    }
}