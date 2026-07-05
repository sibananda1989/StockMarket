package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class VwapCalculator implements IndicatorCalculator {

    private static final int VWAP_LOOKBACK = 20;

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.isEmpty()) {
            throw new IllegalArgumentException("Need at least 1 day of price data to calculate VWAP");
        }

        BigDecimal pvSum = BigDecimal.ZERO;
        BigDecimal volSum = BigDecimal.ZERO;
        int lookback = Math.min(VWAP_LOOKBACK, prices.size());

        for (int i = prices.size() - lookback; i < prices.size(); i++) {
            DailyPrice dp = prices.get(i);
            BigDecimal high = dp.getHighPrice() != null ? dp.getHighPrice() : dp.getClosingPrice();
            BigDecimal low = dp.getLowPrice() != null ? dp.getLowPrice() : dp.getClosingPrice();
            BigDecimal close = dp.getClosingPrice();
            BigDecimal tp = high.add(low).add(close)
                    .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
            long vol = dp.getVolume() != null ? dp.getVolume() : 0L;
            pvSum = pvSum.add(tp.multiply(BigDecimal.valueOf(vol)));
            volSum = volSum.add(BigDecimal.valueOf(vol));
        }

        if (volSum.compareTo(BigDecimal.ZERO) == 0) {
            // Fallback to simple average if no volume data
            DailyPrice today = prices.get(prices.size() - 1);
            BigDecimal high = today.getHighPrice() != null ? today.getHighPrice() : today.getClosingPrice();
            BigDecimal low = today.getLowPrice() != null ? today.getLowPrice() : today.getClosingPrice();
            BigDecimal close = today.getClosingPrice();
            return high.add(low).add(close).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        }

        return pvSum.divide(volSum, 2, RoundingMode.HALF_UP);
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.VWAP;
    }
}
