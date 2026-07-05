package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.util.List;

/**
 * -DI (Negative Directional Indicator) Calculator
 *
 * Part of the ADX system. Measures downward trend strength.
 *
 * Formula:
 *   -DI = (Smoothed -DM / Smoothed TR) × 100
 *
 * Parameters: period = 14 (default)
 * Range: 0-100
 *
 * Signals:
 *   -DI > +DI → Downtrend
 *   -DI > 25 → Strong downtrend momentum
 */
public class MinusDiCalculator implements IndicatorCalculator {

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < 29) {
            throw new IllegalArgumentException("Need at least 29 days of price data to calculate -DI");
        }
        return AdxCalculator.computeAdxResult(prices, 14).minusDi;
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.MINUS_DI;
    }
}
