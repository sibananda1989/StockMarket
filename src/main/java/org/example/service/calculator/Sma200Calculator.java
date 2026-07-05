package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Sma200Calculator implements IndicatorCalculator {

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.SMA_200;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        return new SmaCalculator(200).calculate(prices);
    }
}