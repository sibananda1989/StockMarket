package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Sma50CalculatorTest {

    @Test
    void testSMA50_CalculatesCorrectly() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 50);
        Sma50Calculator calculator = new Sma50Calculator();
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testSMA50_KnownValues() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            prices.add(new DailyPrice(null, new BigDecimal(String.valueOf(100 + i)),
                    new BigDecimal("100.00"), new BigDecimal("110.00"), new BigDecimal("90.00"),
                    1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        Sma50Calculator calculator = new Sma50Calculator();
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("124.50"), result);
    }

    @Test
    void testSMA50_ConstantPrices() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        Sma50Calculator calculator = new Sma50Calculator();
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testSMA50_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 49);
        Sma50Calculator calculator = new Sma50Calculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testSMA50_GetSupportedType_ReturnsSMA_50() {
        Sma50Calculator calculator = new Sma50Calculator();
        assertEquals(IndicatorType.SMA_50, calculator.getSupportedType());
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
