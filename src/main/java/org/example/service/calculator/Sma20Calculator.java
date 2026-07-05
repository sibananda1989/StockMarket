package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Sma20Calculator implements IndicatorCalculator {

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        return new SmaCalculator(20).calculate(prices);
    }
}