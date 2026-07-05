package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Ema20Calculator implements IndicatorCalculator {

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.EMA_20;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        return new EmaCalculator(20).calculate(prices);
    }
}