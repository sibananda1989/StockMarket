package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Rate of Change (ROC) Calculator
 *
 * Formula: ROC = [(Current Close - Close N periods ago) / Close N periods ago] × 100
 *
 * Parameters: period (default 12)
 * Range: Unbounded (positive = uptrend, negative = downtrend)
 * Minimum data: period + 1 days
 *
 * Signals:
 *   ROC > 0 → Upward momentum
 *   ROC < 0 → Downward momentum
 *   ROC crossing above 0 → Bullish crossover
 *   ROC crossing below 0 → Bearish crossover
 *   Accelerating (increasing) → Trend strengthening
 *   Decelerating (decreasing) → Trend weakening
 */
public class RocCalculator implements IndicatorCalculator {

    private final int period;

    public RocCalculator(int period) {
        this.period = period;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < period + 1) {
            throw new IllegalArgumentException(
                    "Need at least " + (period + 1) + " days of price data to calculate ROC-" + period);
        }

        int currentIndex = prices.size() - 1;
        int pastIndex = currentIndex - period;

        BigDecimal currentClose = prices.get(currentIndex).getClosingPrice();
        BigDecimal pastClose = prices.get(pastIndex).getClosingPrice();

        if (pastClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal roc = currentClose.subtract(pastClose)
                .divide(pastClose, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return roc.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.ROC_12;
    }
}
