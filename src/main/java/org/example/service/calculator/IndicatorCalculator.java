package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.util.List;

public interface IndicatorCalculator {
    BigDecimal calculate(List<DailyPrice> prices);
    default IndicatorType getSupportedType() {
        return null;
    }
}