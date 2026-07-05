package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RsiCalculatorTest {

    @Test
    void testRSI_CalculatesCorrectly_Uptrend() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 16);
        RsiCalculator calculator = new RsiCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.valueOf(50)) > 0);
        assertTrue(result.compareTo(BigDecimal.valueOf(100)) <= 0);
    }

    @Test
    void testRSI_CalculatesCorrectly_Downtrend() {
        List<DailyPrice> prices = createPriceSeries(100, -1.0, 16);
        RsiCalculator calculator = new RsiCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.valueOf(50)) < 0);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void testRSI_AllUpDays_Returns100() {
        List<DailyPrice> prices = new ArrayList<>();
        prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                new BigDecimal("105.00"), new BigDecimal("95.00"), 1000L, LocalDate.of(2023, 1, 1)));
        for (int i = 1; i <= 14; i++) {
            prices.add(new DailyPrice(null, new BigDecimal(String.valueOf(100 + i)), new BigDecimal("100.00"),
                    new BigDecimal("105.00"), new BigDecimal("95.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        RsiCalculator calculator = new RsiCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result));
    }

    @Test
    void testRSI_AllDownDays_Returns0() {
        List<DailyPrice> prices = new ArrayList<>();
        prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                new BigDecimal("105.00"), new BigDecimal("95.00"), 1000L, LocalDate.of(2023, 1, 1)));
        for (int i = 1; i <= 14; i++) {
            prices.add(new DailyPrice(null, new BigDecimal(String.valueOf(100 - i)), new BigDecimal("100.00"),
                    new BigDecimal("105.00"), new BigDecimal("95.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        RsiCalculator calculator = new RsiCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void testRSI_ConstantPrices_Returns100() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("105.00"), new BigDecimal("95.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        RsiCalculator calculator = new RsiCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result));
    }

    @Test
    void testRSI_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 14);
        RsiCalculator calculator = new RsiCalculator(14);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testRSI_GetSupportedType_ReturnsNull() {
        RsiCalculator calculator = new RsiCalculator(14);
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
