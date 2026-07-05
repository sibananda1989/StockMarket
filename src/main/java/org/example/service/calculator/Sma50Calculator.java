package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Sma50Calculator implements IndicatorCalculator {

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.SMA_50;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        return new SmaCalculator(50).calculate(prices);
    }
}