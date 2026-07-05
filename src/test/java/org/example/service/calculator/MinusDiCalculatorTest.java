package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MinusDiCalculatorTest {

    @Test
    void testMinusDi_CalculatesCorrectly() {
        List<DailyPrice> prices = createDownTrendData(30);
        MinusDiCalculator calculator = new MinusDiCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(result.compareTo(new BigDecimal("100")) <= 0);
    }

    @Test
    void testMinusDi_Downtrend_GreaterThanPlusDi() {
        List<DailyPrice> prices = createDownTrendData(35);
        PlusDiCalculator plusCalc = new PlusDiCalculator();
        MinusDiCalculator minusCalc = new MinusDiCalculator();

        BigDecimal plusDi = plusCalc.calculate(prices);
        BigDecimal minusDi = minusCalc.calculate(prices);

        assertTrue(minusDi.compareTo(plusDi) > 0,
                "-DI should be > +DI in downtrend, +DI=" + plusDi + ", -DI=" + minusDi);
    }

    @Test
    void testMinusDi_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createDownTrendData(28);
        MinusDiCalculator calculator = new MinusDiCalculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testMinusDi_GetSupportedType_ReturnsMINUS_DI() {
        MinusDiCalculator calculator = new MinusDiCalculator();
        assertEquals(IndicatorType.MINUS_DI, calculator.getSupportedType());
    }

    private List<DailyPrice> createDownTrendData(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        double close = 200;
        double high = 205;
        double low = 195;
        for (int i = 0; i < count; i++) {
            close -= 0.8;
            high = close + 1;
            low = close - 2;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(close)),
                    new BigDecimal(String.valueOf(close + 0.5)),
                    new BigDecimal(String.valueOf(high)),
                    new BigDecimal(String.valueOf(low)),
                    1000L,
                    LocalDate.of(2024, 1, 1).plusDays(i)
            ));
        }
        return prices;
    }
}
