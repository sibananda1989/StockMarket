package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class CCICalculator implements IndicatorCalculator {

    private final int period;
    private static final BigDecimal CONSTANT = BigDecimal.valueOf(0.015);

    public CCICalculator(int period) {
        this.period = period;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Need at least " + period + " days of price data to calculate CCI");
        }

        int start = prices.size() - period;
        List<DailyPrice> periodPrices = prices.subList(start, prices.size());

        // Typical Price = (High + Low + Close) / 3
        BigDecimal[] tp = new BigDecimal[period];
        BigDecimal sumTP = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            DailyPrice dp = periodPrices.get(i);
            BigDecimal high = dp.getHighPrice() != null ? dp.getHighPrice() : dp.getClosingPrice();
            BigDecimal low = dp.getLowPrice() != null ? dp.getLowPrice() : dp.getClosingPrice();
            BigDecimal typicalPrice = high
                    .add(low)
                    .add(dp.getClosingPrice())
                    .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
            tp[i] = typicalPrice;
            sumTP = sumTP.add(typicalPrice);
        }
        BigDecimal smaTP = sumTP.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // Mean Deviation
        BigDecimal sumDev = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            BigDecimal deviation = tp[i].subtract(smaTP).abs();
            sumDev = sumDev.add(deviation);
        }
        BigDecimal meanDev = sumDev.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        if (meanDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // avoid division by zero
        }

        // CCI = (TypicalPrice - SMA(TP)) / (0.015 * MeanDeviation)
        // We calculate for the last period (today's typical price)
        DailyPrice today = prices.get(prices.size() - 1);
        BigDecimal todayHigh = today.getHighPrice() != null ? today.getHighPrice() : today.getClosingPrice();
        BigDecimal todayLow = today.getLowPrice() != null ? today.getLowPrice() : today.getClosingPrice();
        BigDecimal todaysTP = todayHigh
                .add(todayLow)
                .add(today.getClosingPrice())
                .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        BigDecimal numerator = todaysTP.subtract(smaTP);
        BigDecimal denominator = CONSTANT.multiply(meanDev);
        BigDecimal cci = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        return cci;
    }
}