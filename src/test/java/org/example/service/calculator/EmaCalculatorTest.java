package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmaCalculatorTest {

    @Test
    void testEMA_CalculatesCorrectly() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 10);
        EmaCalculator calculator = new EmaCalculator(5);
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testEMA_AcceleratingPrices_EMAFollows() {
        List<DailyPrice> prices = createPriceSeries(100, 3.0, 10);
        EmaCalculator calculator = new EmaCalculator(5);
        BigDecimal result = calculator.calculate(prices);
        assertTrue(result.compareTo(new BigDecimal("100.00")) > 0);
        assertTrue(result.compareTo(new BigDecimal("127.00")) < 0);
    }

    @Test
    void testEMA_DecliningPrices() {
        List<DailyPrice> prices = createPriceSeries(100, -2.0, 10);
        EmaCalculator calculator = new EmaCalculator(5);
        BigDecimal result = calculator.calculate(prices);
        assertTrue(result.compareTo(new BigDecimal("100.00")) < 0);
        assertTrue(result.compareTo(new BigDecimal("80.00")) > 0);
    }

    @Test
    void testEMA_ConstantPrices_ReturnsConstant() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        EmaCalculator calculator = new EmaCalculator(5);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testEMA_ExactPeriodCount_Works() {
        List<DailyPrice> prices = createPriceSeries(50, 2.0, 6);
        EmaCalculator calculator = new EmaCalculator(6);
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
    }

    @Test
    void testEMA_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 4);
        EmaCalculator calculator = new EmaCalculator(5);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testEMA_GetSupportedType_ReturnsNull() {
        EmaCalculator calculator = new EmaCalculator(10);
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
