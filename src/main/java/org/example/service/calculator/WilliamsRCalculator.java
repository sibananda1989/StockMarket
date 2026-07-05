package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class WilliamsRCalculator implements IndicatorCalculator {

    private final int period;

    public WilliamsRCalculator(int period) {
        this.period = period;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Need at least " + period + " days of price data to calculate Williams %R");
        }

        int start = prices.size() - period;
        List<DailyPrice> periodPrices = prices.subList(start, prices.size());

        // Find highest high and lowest low in the period
        DailyPrice first = periodPrices.get(0);
        BigDecimal highestHigh = first.getHighPrice() != null ? first.getHighPrice() : first.getClosingPrice();
        BigDecimal lowestLow = first.getLowPrice() != null ? first.getLowPrice() : first.getClosingPrice();
        for (DailyPrice dp : periodPrices) {
            BigDecimal high = dp.getHighPrice() != null ? dp.getHighPrice() : dp.getClosingPrice();
            BigDecimal low = dp.getLowPrice() != null ? dp.getLowPrice() : dp.getClosingPrice();
            if (high.compareTo(highestHigh) > 0) {
                highestHigh = high;
            }
            if (low.compareTo(lowestLow) < 0) {
                lowestLow = low;
            }
        }

        DailyPrice today = prices.get(prices.size() - 1);
        BigDecimal close = today.getClosingPrice();

        // %R = (HighestHigh - Close) / (HighestHigh - LowestLow) * -100
        BigDecimal numerator = highestHigh.subtract(close);
        BigDecimal denominator = highestHigh.subtract(lowestLow);
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // avoid division by zero
        }
        BigDecimal williamsR = numerator.divide(denominator, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(-100));
        return williamsR.setScale(2, RoundingMode.HALF_UP);
    }
}