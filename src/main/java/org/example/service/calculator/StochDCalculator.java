package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class StochDCalculator implements IndicatorCalculator {

    private final int kPeriod;
    private final int dPeriod;

    public StochDCalculator(int kPeriod, int dPeriod) {
        this.kPeriod = kPeriod;
        this.dPeriod = dPeriod;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        // We need enough data to calculate %K for each day and then have dPeriod of those %K values
        // Minimum required: kPeriod (for first %K) + (dPeriod - 1) for the smoothing = kPeriod + dPeriod - 1
        if (prices.size() < kPeriod + dPeriod - 1) {
            throw new IllegalArgumentException("Need at least " + (kPeriod + dPeriod - 1) + " days of price data to calculate Stochastic %D");
        }

        List<BigDecimal> kValues = new ArrayList<>();
        // Calculate %K for each day starting from index (kPeriod-1) to the end
        for (int i = kPeriod - 1; i < prices.size(); i++) {
            int startIdx = i - (kPeriod - 1);
            List<DailyPrice> subList = prices.subList(startIdx, i + 1);
            StochKCalculator kCalc = new StochKCalculator(kPeriod);
            BigDecimal k = kCalc.calculate(subList);
            kValues.add(k);
        }

        // Now calculate the simple moving average of the last dPeriod %K values
        if (kValues.size() < dPeriod) {
            throw new IllegalArgumentException("Not enough %K values to calculate Stochastic %D");
        }
        int startIdx = kValues.size() - dPeriod;
        List<BigDecimal> dPeriodKValues = kValues.subList(startIdx, kValues.size());
        BigDecimal sum = dPeriodKValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(new BigDecimal(dPeriod), 2, RoundingMode.HALF_UP);
        return avg;
    }
}