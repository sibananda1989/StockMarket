package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class EmaCalculator implements IndicatorCalculator {

    private final int period;

    public EmaCalculator(int period) {
        this.period = period;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Need at least " + period + " days of price data to calculate EMA-" + period);
        }

        // Step 1: Calculate initial SMA as the first EMA value
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(prices.get(i).getClosingPrice());
        }
        BigDecimal currentEma = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // Step 2: Calculate K (multiplier)
        // K = 2 / (period + 1)
        BigDecimal k = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1), 4, RoundingMode.HALF_UP);

        // Step 3: Iteratively calculate EMA for the rest of the prices
        for (int i = period; i < prices.size(); i++) {
            BigDecimal close = prices.get(i).getClosingPrice();

            // EMA = (Close - EMA_yesterday) * K + EMA_yesterday
            BigDecimal diff = close.subtract(currentEma);
            currentEma = diff.multiply(k).add(currentEma);
        }

        return currentEma.setScale(2, RoundingMode.HALF_UP);
    }
}