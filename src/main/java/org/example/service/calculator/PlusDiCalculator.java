package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.util.List;

/**
 * +DI (Positive Directional Indicator) Calculator
 *
 * Part of the ADX system. Measures upward trend strength.
 *
 * Formula:
 *   +DI = (Smoothed +DM / Smoothed TR) × 100
 *
 * Parameters: period = 14 (default)
 * Range: 0-100
 *
 * Signals:
 *   +DI > -DI → Uptrend
 *   +DI > 25 → Strong uptrend momentum
 */
public class PlusDiCalculator implements IndicatorCalculator {

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < 29) {
            throw new IllegalArgumentException("Need at least 29 days of price data to calculate +DI");
        }
        return AdxCalculator.computeAdxResult(prices, 14).plusDi;
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.PLUS_DI;
    }
}
