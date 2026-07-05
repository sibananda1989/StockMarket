package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class MacdLineCalculator implements IndicatorCalculator {

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        if (prices.size() < 26) {
            throw new IllegalArgumentException("Need at least 26 days of price data to calculate MACD Line");
        }

        EmaCalculator ema12 = new EmaCalculator(12);
        EmaCalculator ema26 = new EmaCalculator(26);

        BigDecimal ema12Value = ema12.calculate(prices);
        BigDecimal ema26Value = ema26.calculate(prices);

        return ema12Value.subtract(ema26Value).setScale(4, RoundingMode.HALF_UP);
    }
}