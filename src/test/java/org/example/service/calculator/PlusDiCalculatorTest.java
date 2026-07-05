package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlusDiCalculatorTest {

    @Test
    void testPlusDi_CalculatesCorrectly() {
        List<DailyPrice> prices = createUpTrendData(30);
        PlusDiCalculator calculator = new PlusDiCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(result.compareTo(new BigDecimal("100")) <= 0);
    }

    @Test
    void testPlusDi_Uptrend_GreaterThanMinusDi() {
        List<DailyPrice> prices = createUpTrendData(35);
        PlusDiCalculator plusCalc = new PlusDiCalculator();
        MinusDiCalculator minusCalc = new MinusDiCalculator();

        BigDecimal plusDi = plusCalc.calculate(prices);
        BigDecimal minusDi = minusCalc.calculate(prices);

        assertTrue(plusDi.compareTo(minusDi) > 0,
                "+DI should be > -DI in uptrend, +DI=" + plusDi + ", -DI=" + minusDi);
    }

    @Test
    void testPlusDi_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createUpTrendData(28);
        PlusDiCalculator calculator = new PlusDiCalculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testPlusDi_GetSupportedType_ReturnsPLUS_DI() {
        PlusDiCalculator calculator = new PlusDiCalculator();
        assertEquals(IndicatorType.PLUS_DI, calculator.getSupportedType());
    }

    private List<DailyPrice> createUpTrendData(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        double close = 100;
        double high = 105;
        double low = 95;
        for (int i = 0; i < count; i++) {
            close += 0.8;
            high = close + 2;
            low = close - 1;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(close)),
                    new BigDecimal(String.valueOf(close - 0.5)),
                    new BigDecimal(String.valueOf(high)),
                    new BigDecimal(String.valueOf(low)),
                    1000L,
                    LocalDate.of(2024, 1, 1).plusDays(i)
            ));
        }
        return prices;
    }
}
