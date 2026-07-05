package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MacdSignalCalculatorTest {

    @Test
    void testMacdSignal_CalculatesCorrectly() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 40);
        MacdSignalCalculator calculator = new MacdSignalCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
    }

    @Test
    void testMacdSignal_Uptrend_ReturnsPositive() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 50);
        MacdSignalCalculator calculator = new MacdSignalCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0,
                "MACD Signal should be positive in uptrend, got: " + result);
    }

    @Test
    void testMacdSignal_Downtrend_ReturnsNegative() {
        List<DailyPrice> prices = createPriceSeries(200, -1.0, 50);
        MacdSignalCalculator calculator = new MacdSignalCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertTrue(result.compareTo(BigDecimal.ZERO) < 0,
                "MACD Signal should be negative in downtrend, got: " + result);
    }

    @Test
    void testMacdSignal_ConstantPrices_ReturnsZero() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        MacdSignalCalculator calculator = new MacdSignalCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertEquals(BigDecimal.ZERO.setScale(4), result);
    }

    @Test
    void testMacdSignal_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 34);
        MacdSignalCalculator calculator = new MacdSignalCalculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testMacdSignal_GetSupportedType_ReturnsNull() {
        MacdSignalCalculator calculator = new MacdSignalCalculator();
        assertNull(calculator.getSupportedType());
    }

    private List<DailyPrice> createPriceSeries(double start, double increment, int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = start + i * increment;
            prices.add(new DailyPrice(null, new BigDecimal(String.valueOf(close)),
                    new BigDecimal("100.00"), new BigDecimal(String.valueOf(close + 5)),
                    new BigDecimal(String.valueOf(close - 5)), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        return prices;
    }
}
