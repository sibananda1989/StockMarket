package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * On-Balance Volume (OBV) Calculator
 *
 * Formula:
 *   If Close > PrevClose:  OBV = PrevOBV + Volume
 *   If Close < PrevClose:  OBV = PrevOBV - Volume
 *   If Close == PrevClose: OBV = PrevOBV (unchanged)
 *
 * Parameters: None (cumulative from first data point)
 * Range: Unbounded (grows with cumulative volume)
 * Minimum data: 2 days
 *
 * Signals:
 *   OBV rising + Price rising → Strong uptrend (volume confirms)
 *   OBV falling + Price falling → Strong downtrend
 *   OBV rising + Price falling → Bullish divergence (smart money buying)
 *   OBV falling + Price rising → Bearish divergence (smart money selling)
 *   OBV making new highs before price → Leading indicator
 */
public class ObvCalculator implements IndicatorCalculator {

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices == null || prices.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 days of price data to calculate OBV");
        }

        BigDecimal obv = BigDecimal.ZERO;

        for (int i = 1; i < prices.size(); i++) {
            BigDecimal currentClose = prices.get(i).getClosingPrice();
            BigDecimal previousClose = prices.get(i - 1).getClosingPrice();
            long volume = prices.get(i).getVolume() != null ? prices.get(i).getVolume() : 0L;

            int comparison = currentClose.compareTo(previousClose);
            if (comparison > 0) {
                // Price went up → add volume
                obv = obv.add(BigDecimal.valueOf(volume));
            } else if (comparison < 0) {
                // Price went down → subtract volume
                obv = obv.subtract(BigDecimal.valueOf(volume));
            }
            // If equal, OBV stays the same
        }

        return obv.setScale(0, RoundingMode.HALF_UP);
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.OBV;
    }
}