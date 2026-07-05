package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Ultimate Oscillator (UO) Calculator
 *
 * Combines 3 timeframes (7, 14, 28) into one oscillator.
 * Reduces false signals by weighting short-term more heavily.
 *
 * Formula:
 *   BP = Close - min(Low, PriorClose)
 *   TR = max(High, PriorClose) - min(Low, PriorClose)
 *
 *   Avg7  = Sum(BP, 7) / Sum(TR, 7)
 *   Avg14 = Sum(BP, 14) / Sum(TR, 14)
 *   Avg28 = Sum(BP, 28) / Sum(TR, 28)
 *
 *   UO = 100 × [(4 × Avg7) + (2 × Avg14) + (1 × Avg28)] / 7
 *
 * Parameters: 7, 14, 28 periods (default)
 * Range: 0-100
 * Minimum data: 29 days (28 + 1 for prior close)
 *
 * Signals:
 *   UO < 30 → Oversold (buy)
 *   UO > 70 → Overbought (sell)
 *   UO crossing above 30 from oversold → Bullish confirmation
 *   UO crossing below 70 from overbought → Bearish confirmation
 */
public class UltimateOscillatorCalculator implements IndicatorCalculator {

    private final int shortPeriod;
    private final int mediumPeriod;
    private final int longPeriod;

    public UltimateOscillatorCalculator() {
        this(7, 14, 28);
    }

    public UltimateOscillatorCalculator(int shortPeriod, int mediumPeriod, int longPeriod) {
        this.shortPeriod = shortPeriod;
        this.mediumPeriod = mediumPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        int minRequired = longPeriod + 1;
        if (prices.size() < minRequired) {
            throw new IllegalArgumentException(
                    "Need at least " + minRequired + " days of price data to calculate Ultimate Oscillator");
        }

        // Calculate Buying Pressure (BP) and True Range (TR) for each day
        BigDecimal[] bp = new BigDecimal[prices.size()];
        BigDecimal[] tr = new BigDecimal[prices.size()];

        for (int i = 1; i < prices.size(); i++) {
            DailyPrice today = prices.get(i);
            DailyPrice yesterday = prices.get(i - 1);

            BigDecimal close = today.getClosingPrice();
            BigDecimal low = today.getLowPrice() != null ? today.getLowPrice() : close;
            BigDecimal high = today.getHighPrice() != null ? today.getHighPrice() : close;
            BigDecimal prevClose = yesterday.getClosingPrice();

            // BP = Close - min(Low, PriorClose)
            BigDecimal minLowPrevClose = low.min(prevClose);
            bp[i] = close.subtract(minLowPrevClose);

            // TR = max(High, PriorClose) - min(Low, PriorClose)
            BigDecimal maxHighPrevClose = high.max(prevClose);
            tr[i] = maxHighPrevClose.subtract(minLowPrevClose);
        }

        // Calculate sums for each period
        BigDecimal sumBp7 = sumArray(bp, prices.size() - 1, shortPeriod);
        BigDecimal sumTr7 = sumArray(tr, prices.size() - 1, shortPeriod);
        BigDecimal sumBp14 = sumArray(bp, prices.size() - 1, mediumPeriod);
        BigDecimal sumTr14 = sumArray(tr, prices.size() - 1, mediumPeriod);
        BigDecimal sumBp28 = sumArray(bp, prices.size() - 1, longPeriod);
        BigDecimal sumTr28 = sumArray(tr, prices.size() - 1, longPeriod);

        // Avoid division by zero
        if (sumTr7.compareTo(BigDecimal.ZERO) == 0 ||
            sumTr14.compareTo(BigDecimal.ZERO) == 0 ||
            sumTr28.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(50); // neutral value
        }

        // Calculate averages
        BigDecimal avg7 = sumBp7.divide(sumTr7, 6, RoundingMode.HALF_UP);
        BigDecimal avg14 = sumBp14.divide(sumTr14, 6, RoundingMode.HALF_UP);
        BigDecimal avg28 = sumBp28.divide(sumTr28, 6, RoundingMode.HALF_UP);

        // UO = 100 × [(4 × Avg7) + (2 × Avg14) + (1 × Avg28)] / 7
        BigDecimal weighted = avg7.multiply(BigDecimal.valueOf(4))
                .add(avg14.multiply(BigDecimal.valueOf(2)))
                .add(avg28);

        BigDecimal uo = weighted.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);

        return uo;
    }

    private BigDecimal sumArray(BigDecimal[] array, int endIndex, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endIndex; i > endIndex - period && i >= 1; i--) {
            sum = sum.add(array[i]);
        }
        return sum;
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.ULTIMATE_OSC;
    }
}
