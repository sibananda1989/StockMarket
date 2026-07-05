package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class SmaCalculator implements IndicatorCalculator {

    private final int period;

    public SmaCalculator(int period) {
        this.period = period;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Need at least " + period + " days of price data to calculate SMA-" + period);
        }

        // Get the last 'period' prices
        int size = prices.size();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = size - period; i < size; i++) {
            sum = sum.add(prices.get(i).getClosingPrice());
        }

        return sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
    }
}