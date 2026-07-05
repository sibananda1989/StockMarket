package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UltimateOscillatorCalculatorTest {

    @Test
    void testUO_CalculatesCorrectly() {
        // Given: 30 days of sample price data
        List<DailyPrice> prices = createSamplePrices(30, 100, 5);

        // When: Calculating Ultimate Oscillator
        UltimateOscillatorCalculator calculator = new UltimateOscillatorCalculator(7, 14, 28);
        BigDecimal result = calculator.calculate(prices);

        // Then: Result should be between 0 and 100
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0,
                "UO should be >= 0, got: " + result);
        assertTrue(result.compareTo(new BigDecimal("100")) <= 0,
                "UO should be <= 100, got: " + result);
    }

    @Test
    void testUO_InsufficientData_ThrowsException() {
        // Given: Only 25 days of price data (need 29)
        List<DailyPrice> prices = createSamplePrices(25, 100, 5);

        // When/Then: Should throw exception
        UltimateOscillatorCalculator calculator = new UltimateOscillatorCalculator();
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(prices));
    }

    @Test
    void testUO_StrongUptrend_ReturnsHighValue() {
        // Given: Consistent uptrend with buying pressure (close near high)
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double base = 100 + i * 2;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base + 1.5)), // close near high
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base + 2)),     // high
                    new BigDecimal(String.valueOf(base - 0.5)),   // low
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating Ultimate Oscillator
        UltimateOscillatorCalculator calculator = new UltimateOscillatorCalculator();
        BigDecimal result = calculator.calculate(prices);

        // Then: Strong buying pressure should give UO > 50
        assertTrue(result.compareTo(new BigDecimal("50")) > 0,
                "Strong uptrend UO should be > 50, got: " + result);
    }

    @Test
    void testUO_StrongDowntrend_ReturnsLowValue() {
        // Given: Consistent downtrend with selling pressure (close near low)
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double base = 200 - i * 2;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base - 1.5)), // close near low
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base + 0.5)),   // high
                    new BigDecimal(String.valueOf(base - 2)),     // low
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating Ultimate Oscillator
        UltimateOscillatorCalculator calculator = new UltimateOscillatorCalculator();
        BigDecimal result = calculator.calculate(prices);

        // Then: Strong selling pressure should give UO < 50
        assertTrue(result.compareTo(new BigDecimal("50")) < 0,
                "Strong downtrend UO should be < 50, got: " + result);
    }

    @Test
    void testUO_ConstantPrices_ReturnsNeutral() {
        // Given: All prices are the same (flat market)
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal("100.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("100.00"),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating Ultimate Oscillator
        UltimateOscillatorCalculator calculator = new UltimateOscillatorCalculator();
        BigDecimal result = calculator.calculate(prices);

        // Then: All buying pressure = 0, should return neutral (50)
        assertEquals(new BigDecimal("50"), result);
    }

    @Test
    void testUO_GetSupportedType_ReturnsULTIMATE_OSC() {
        UltimateOscillatorCalculator calculator = new UltimateOscillatorCalculator();
        assertEquals(org.example.entity.IndicatorType.ULTIMATE_OSC, calculator.getSupportedType());
    }

    private List<DailyPrice> createSamplePrices(int count, double startPrice, double increment) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double base = startPrice + i * increment;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base - 1)),
                    new BigDecimal(String.valueOf(base + 3)),
                    new BigDecimal(String.valueOf(base - 3)),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }
        return prices;
    }
}
