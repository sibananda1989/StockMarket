package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmaCalculatorTest {

    @Test
    void testSMA_CalculatesCorrectly() {
        List<DailyPrice> prices = createPriceSeries(100, 2.0, 20);
        SmaCalculator calculator = new SmaCalculator(5);
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
    }

    @Test
    void testSMA_KnownValues() {
        List<DailyPrice> prices = new ArrayList<>();
        double[] closes = {10, 20, 30, 40, 50};
        for (int i = 0; i < closes.length; i++) {
            prices.add(new DailyPrice(null, new BigDecimal(String.valueOf(closes[i])),
                    new BigDecimal("100.00"), new BigDecimal("110.00"), new BigDecimal("90.00"),
                    1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        SmaCalculator calculator = new SmaCalculator(3);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("40.00"), result);
    }

    @Test
    void testSMA_ConstantPrices() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        SmaCalculator calculator = new SmaCalculator(5);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testSMA_SinglePeriod() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 10);
        SmaCalculator calculator = new SmaCalculator(1);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("109.00"), result);
    }

    @Test
    void testSMA_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 5);
        SmaCalculator calculator = new SmaCalculator(10);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testSMA_GetSupportedType_ReturnsNull() {
        SmaCalculator calculator = new SmaCalculator(20);
        assertNull(calculator.getSupportedType());
    }

    private List<DailyPrice> createPriceSeries(double start, double increment, int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = start + i * increment;
            prices.add(new DailyPrice(null, new BigDecimal(String.valueOf(close)),
                    new BigDecimal("100.00"), new BigDecimal("110.00"),
                    new BigDecimal("90.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        return prices;
    }
}
