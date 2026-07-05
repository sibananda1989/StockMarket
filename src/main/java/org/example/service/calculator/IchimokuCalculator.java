package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Ichimoku Cloud Calculator
 *
 * Five components:
 *   Tenkan-sen (Conversion Line):  (9-period high + 9-period low) / 2
 *   Kijun-sen (Base Line):         (26-period high + 26-period low) / 2
 *   Senkou Span A (Leading Span A): (Tenkan + Kijun) / 2
 *   Senkou Span B (Leading Span B): (52-period high + 52-period low) / 2
 *   Chikou Span (Lagging Span):    Current close (plotted 26 periods back)
 *
 * Signals:
 *   Price above cloud → bullish
 *   Price below cloud → bearish
 *   Tenkan crosses above Kijun → bullish TK cross
 *   Cloud color: Senkou A > Senkou B = green (bullish), else red (bearish)
 *
 * Minimum data: 52 days (for Senkou Span B)
 */
public class IchimokuCalculator implements IndicatorCalculator {

    private static final int TENKAN_PERIOD = 9;
    private static final int KIJUN_PERIOD = 26;
    private static final int SENKOU_B_PERIOD = 52;

    private final IndicatorType type;

    public IchimokuCalculator(IndicatorType type) {
        this.type = type;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        switch (type) {
            case TENKAN_SEN:
                return calculateTenkan(prices);
            case KIJUN_SEN:
                return calculateKijun(prices);
            case SENKOU_SPAN_A:
                return calculateSenkouA(prices);
            case SENKOU_SPAN_B:
                return calculateSenkouB(prices);
            case CHIKOU_SPAN:
                return calculateChikou(prices);
            default:
                throw new IllegalArgumentException("Unsupported Ichimoku type: " + type);
        }
    }

    /**
     * Tenkan-sen (Conversion Line): midpoint of 9-period high and low
     */
    private BigDecimal calculateTenkan(List<DailyPrice> prices) {
        if (prices.size() < TENKAN_PERIOD) {
            throw new IllegalArgumentException(
                    "Need at least " + TENKAN_PERIOD + " days of price data to calculate Tenkan-sen");
        }
        List<DailyPrice> period = prices.subList(prices.size() - TENKAN_PERIOD, prices.size());
        return midpoint(period);
    }

    /**
     * Kijun-sen (Base Line): midpoint of 26-period high and low
     */
    private BigDecimal calculateKijun(List<DailyPrice> prices) {
        if (prices.size() < KIJUN_PERIOD) {
            throw new IllegalArgumentException(
                    "Need at least " + KIJUN_PERIOD + " days of price data to calculate Kijun-sen");
        }
        List<DailyPrice> period = prices.subList(prices.size() - KIJUN_PERIOD, prices.size());
        return midpoint(period);
    }

    /**
     * Senkou Span A (Leading Span A): average of Tenkan and Kijun
     */
    private BigDecimal calculateSenkouA(List<DailyPrice> prices) {
        if (prices.size() < KIJUN_PERIOD) {
            throw new IllegalArgumentException(
                    "Need at least " + KIJUN_PERIOD + " days of price data to calculate Senkou Span A");
        }
        BigDecimal tenkan = calculateTenkan(prices);
        BigDecimal kijun = calculateKijun(prices);
        return tenkan.add(kijun).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /**
     * Senkou Span B (Leading Span B): midpoint of 52-period high and low
     */
    private BigDecimal calculateSenkouB(List<DailyPrice> prices) {
        if (prices.size() < SENKOU_B_PERIOD) {
            throw new IllegalArgumentException(
                    "Need at least " + SENKOU_B_PERIOD + " days of price data to calculate Senkou Span B");
        }
        List<DailyPrice> period = prices.subList(prices.size() - SENKOU_B_PERIOD, prices.size());
        return midpoint(period);
    }

    /**
     * Chikou Span (Lagging Span): current closing price
     */
    private BigDecimal calculateChikou(List<DailyPrice> prices) {
        if (prices.isEmpty()) {
            throw new IllegalArgumentException("Need at least 1 day of price data to calculate Chikou Span");
        }
        return prices.get(prices.size() - 1).getClosingPrice();
    }

    /**
     * Calculates midpoint of highest high and lowest low in the period
     */
    private BigDecimal midpoint(List<DailyPrice> period) {
        DailyPrice first = period.get(0);
        BigDecimal high = first.getHighPrice() != null ? first.getHighPrice() : first.getClosingPrice();
        BigDecimal low = first.getLowPrice() != null ? first.getLowPrice() : first.getClosingPrice();

        for (DailyPrice dp : period) {
            BigDecimal h = dp.getHighPrice() != null ? dp.getHighPrice() : dp.getClosingPrice();
            BigDecimal l = dp.getLowPrice() != null ? dp.getLowPrice() : dp.getClosingPrice();
            if (h.compareTo(high) > 0) high = h;
            if (l.compareTo(low) < 0) low = l;
        }

        return high.add(low).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    @Override
    public IndicatorType getSupportedType() {
        return type;
    }
}
