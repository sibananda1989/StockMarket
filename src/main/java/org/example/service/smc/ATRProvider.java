package org.example.service.smc;

import org.example.service.calculator.ATRCalculator;
import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.util.List;

/**
 * Provides ATR (Average True Range) values for the SMC scanner.
 * Wraps the existing {@link ATRCalculator} for consistency.
 */
public class ATRProvider {

    private static final int DEFAULT_PERIOD = 14;
    private final ATRCalculator calculator;

    public ATRProvider() {
        this.calculator = new ATRCalculator(DEFAULT_PERIOD);
    }

    /** Compute ATR(14) from daily prices. Returns null if insufficient data. */
    public BigDecimal getATR(List<DailyPrice> prices) {
        if (prices.size() < DEFAULT_PERIOD + 1) return null;
        try {
            return calculator.calculate(prices);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a price difference exceeds the ATR minimum threshold.
     * @param diff  absolute price difference
     * @param atr   the ATR value
     * @param min   minimum ATR multiplier (e.g. 1.0 = 1 ATR)
     */
    public static boolean meetsMinATR(BigDecimal diff, BigDecimal atr, double min) {
        if (diff == null || atr == null || atr.compareTo(BigDecimal.ZERO) <= 0) return false;
        return diff.compareTo(atr.multiply(BigDecimal.valueOf(min))) >= 0;
    }

    /**
     * Scale a price by ATR: price + atr * multiplier (for stops/targets).
     */
    public static BigDecimal scaleByATR(BigDecimal price, BigDecimal atr, double multiplier) {
        if (price == null || atr == null) return price;
        return price.add(atr.multiply(BigDecimal.valueOf(multiplier)));
    }
}
