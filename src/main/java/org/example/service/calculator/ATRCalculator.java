package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ATRCalculator implements IndicatorCalculator {

    private final int period;

    public ATRCalculator(int period) {
        this.period = period;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Need at least " + period + " days of price data to calculate ATR");
        }

        // True Range for each day: max(high-low, abs(high-previous close), abs(low-previous close))
        // We need previous close, so start from index 1
        BigDecimal[] tr = new BigDecimal[prices.size()];
        for (int i = 0; i < prices.size(); i++) {
            DailyPrice today = prices.get(i);
            BigDecimal high = today.getHighPrice() != null ? today.getHighPrice() : today.getClosingPrice();
            BigDecimal low = today.getLowPrice() != null ? today.getLowPrice() : today.getClosingPrice();
            BigDecimal highLow = high.subtract(low);
            BigDecimal highPrevClose = BigDecimal.ZERO;
            BigDecimal lowPrevClose = BigDecimal.ZERO;
            if (i > 0) {
                DailyPrice yesterday = prices.get(i - 1);
                BigDecimal prevClose = yesterday.getClosingPrice();
                highPrevClose = high.subtract(prevClose).abs();
                lowPrevClose = low.subtract(prevClose).abs();
            }
            tr[i] = highLow.max(highPrevClose).max(lowPrevClose);
        }

        // ATR uses Wilder's smoothing (exponential) — industry standard
        // First ATR = SMA of first 'period' True Range values
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(tr[i]);
        }
        BigDecimal atr = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // Wilder's smoothing: ATR = (prevATR * (period-1) + currentTR) / period
        for (int i = period; i < tr.length; i++) {
            atr = atr.multiply(BigDecimal.valueOf(period - 1))
                    .add(tr[i])
                    .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        }

        return atr.setScale(2, RoundingMode.HALF_UP);
    }
}