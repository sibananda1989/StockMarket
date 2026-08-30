package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Dollar Volume Calculator.
 *
 * Measures absolute liquidity by computing the average rupee value traded
 * per day. This captures the total money flow — a stock can have moderate
 * turnover ratio but still be liquid if its price is high.
 *
 * Formula:
 *   AvgDollarVolume = avg( ClosePrice_d × Volume_d ) over N days (default: 20)
 *
 * Parameters: 20-day lookback window
 * Range: Unlimited (depends on stock size and price)
 *   Large-cap NSE stocks: ₹100 Cr+ per day
 *   Mid-cap NSE stocks: ₹10-100 Cr per day
 *   Small-cap NSE stocks: < ₹10 Cr per day
 *
 * Minimum data: 1 day
 *
 * Usage: Higher values = more liquid (more money changes hands)
 *   Combined with Amihud ratio for a complete liquidity picture.
 */
public class DollarVolumeCalculator implements IndicatorCalculator {

    private static final int LOOKBACK_DAYS = 20;

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException(
                    "Need at least 1 day of price data to calculate Dollar Volume");
        }

        int lookback = Math.min(LOOKBACK_DAYS, prices.size());
        double sumDollarVolume = 0.0;
        int validDays = 0;

        for (int i = prices.size() - lookback; i < prices.size(); i++) {
            DailyPrice dp = prices.get(i);
            BigDecimal close = dp.getClosingPrice();
            Long volume = dp.getVolume();

            if (close == null || volume == null || volume <= 0) {
                continue;
            }

            sumDollarVolume += close.multiply(BigDecimal.valueOf(volume)).doubleValue();
            validDays++;
        }

        if (validDays == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(sumDollarVolume / validDays)
                .setScale(0, RoundingMode.HALF_UP);
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.DOLLAR_VOLUME;
    }
}
