package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Amihud Illiquidity Ratio Calculator.
 *
 * Measures price impact — how much a stock's price moves per unit of trading volume.
 * This is a widely used academic measure of stock liquidity (Amihud, 2002).
 *
 * Formula:
 *   Amihud_iLLIQ = avg( |R_d| / (P_d × V_d) ) over N days (default: 20)
 *   Where R_d = (close_d - close_{d-1}) / close_{d-1}  (daily return)
 *         P_d = closing price on day d
 *         V_d = volume on day d
 *         P_d × V_d = dollar volume (rupee value traded)
 *
 * Parameters: 20-day lookback window
 * Range: Typically 10^-12 to 10^-6 (depends on stock and currency)
 *   Lower values = more liquid (price barely moves when traded)
 *   Higher values = less liquid (small trades cause large price moves)
 *   For Indian large-caps: ~10^-12 to 10^-10
 *   For Indian small-caps: ~10^-8 to 10^-6
 *
 * Minimum data: 21 days (20 returns + 1 to start)
 */
public class AmihudCalculator implements IndicatorCalculator {

    private static final int LOOKBACK_DAYS = 20;

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices == null || prices.size() <= LOOKBACK_DAYS) {
            throw new IllegalArgumentException(
                    "Need at least " + (LOOKBACK_DAYS + 1) + " days of price data to calculate Amihud Illiquidity Ratio");
        }

        double sumRatio = 0.0;
        int validDays = 0;

        // Use the last LOOKBACK_DAYS pairs of consecutive days
        int start = prices.size() - LOOKBACK_DAYS;
        for (int i = start; i < prices.size(); i++) {
            DailyPrice prev = prices.get(i - 1);
            DailyPrice curr = prices.get(i);

            BigDecimal prevClose = prev.getClosingPrice();
            BigDecimal currClose = curr.getClosingPrice();
            Long volume = curr.getVolume();

            if (prevClose == null || currClose == null || volume == null || volume <= 0
                    || prevClose.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Daily return: |close_d - close_{d-1}| / close_{d-1}
            BigDecimal dailyReturn = currClose.subtract(prevClose).abs()
                    .divide(prevClose, 10, RoundingMode.HALF_UP);

            // Dollar volume: close_d × volume_d
            BigDecimal dollarVolume = currClose.multiply(BigDecimal.valueOf(volume));

            if (dollarVolume.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Ratio = |return| / dollarVolume
            sumRatio += dailyReturn.divide(dollarVolume, 15, RoundingMode.HALF_UP).doubleValue();
            validDays++;
        }

        if (validDays == 0) {
            return BigDecimal.ZERO;
        }

        // Average over valid days
        return BigDecimal.valueOf(sumRatio / validDays)
                .setScale(12, RoundingMode.HALF_UP);
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.AMIHUD_ILLIQUIDITY;
    }
}
