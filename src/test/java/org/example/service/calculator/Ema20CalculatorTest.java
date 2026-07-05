package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Ema20CalculatorTest {

    @Test
    void testEMA20_CalculatesCorrectly() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 25);
        Ema20Calculator calculator = new Ema20Calculator();
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testEMA20_Uptrend() {
        List<DailyPrice> prices = createPriceSeries(100, 2.0, 25);
        Ema20Calculator calculator = new Ema20Calculator();
        BigDecimal result = calculator.calculate(prices);
        assertTrue(result.compareTo(new BigDecimal("100.00")) > 0);
    }

    @Test
    void testEMA20_ConstantPrices() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        Ema20Calculator calculator = new Ema20Calculator();
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testEMA20_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 19);
        Ema20Calculator calculator = new Ema20Calculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testEMA20_GetSupportedType_ReturnsEMA_20() {
        Ema20Calculator calculator = new Ema20Calculator();
        assertEquals(IndicatorType.EMA_20, calculator.getSupportedType());
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
