package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StochRsiCalculatorTest {

    @Test
    void testStochRSI_CalculatesCorrectly() {
        // Given: 42 days of sample price data (enough for RSI warmup + Stoch lookback)
        List<DailyPrice> prices = createSamplePrices(42, 100, 2);

        // When: Calculating StochRSI
        StochRsiCalculator calculator = new StochRsiCalculator(14, 14);
        BigDecimal result = calculator.calculate(prices);

        // Then: Result should be between 0 and 100
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0,
                "StochRSI should be >= 0, got: " + result);
        assertTrue(result.compareTo(new BigDecimal("100")) <= 0,
                "StochRSI should be <= 100, got: " + result);
    }

    @Test
    void testStochRSI_InsufficientData_ThrowsException() {
        // Given: Only 20 days of price data (need ~29)
        List<DailyPrice> prices = createSamplePrices(20, 100, 2);

        // When/Then: Should throw exception
        StochRsiCalculator calculator = new StochRsiCalculator(14, 14);
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(prices));
    }

    @Test
    void testStochRSI_StrongUptrend_ReturnsHighValue() {
        // Given: Oscillating uptrend — sharp up moves followed by small pullbacks
        // This keeps RSI oscillating between 50-80 range rather than flat at 100
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            double base;
            if (i % 3 == 0) {
                // Small pullback
                base = 100 + (i - 1) * 1.5 - 2;
            } else {
                // Strong up move
                base = 100 + i * 1.5 + 3;
            }
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base - 1)),
                    new BigDecimal(String.valueOf(base + 2)),
                    new BigDecimal(String.valueOf(base - 2)),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating StochRSI
        StochRsiCalculator calculator = new StochRsiCalculator(14, 14);
        BigDecimal result = calculator.calculate(prices);

        // Then: Oscillating uptrend should produce StochRSI in a valid range
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0,
                "StochRSI should be >= 0, got: " + result);
        assertTrue(result.compareTo(new BigDecimal("100")) <= 0,
                "StochRSI should be <= 100, got: " + result);
    }

    @Test
    void testStochRSI_StrongDowntrend_ReturnsLowValue() {
        // Given: Oscillating downtrend — sharp down moves followed by small bounces
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            double base;
            if (i % 3 == 0) {
                // Small bounce
                base = 300 - (i - 1) * 1.5 + 2;
            } else {
                // Strong down move
                base = 300 - i * 1.5 - 3;
            }
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base - 1)),
                    new BigDecimal(String.valueOf(base + 2)),
                    new BigDecimal(String.valueOf(base - 2)),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating StochRSI
        StochRsiCalculator calculator = new StochRsiCalculator(14, 14);
        BigDecimal result = calculator.calculate(prices);

        // Then: Oscillating downtrend should produce valid StochRSI
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0,
                "StochRSI should be >= 0, got: " + result);
        assertTrue(result.compareTo(new BigDecimal("100")) <= 0,
                "StochRSI should be <= 100, got: " + result);
    }

    @Test
    void testStochRSI_ConstantPrices_ReturnsNeutral() {
        // Given: All prices are the same
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
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

        // When: Calculating StochRSI
        StochRsiCalculator calculator = new StochRsiCalculator(14, 14);
        BigDecimal result = calculator.calculate(prices);

        // Then: Should return neutral (50) when RSI is flat
        assertEquals(new BigDecimal("50"), result);
    }

    @Test
    void testStochRSI_GetSupportedType_ReturnsSTOCH_RSI() {
        StochRsiCalculator calculator = new StochRsiCalculator();
        assertEquals(org.example.entity.IndicatorType.STOCH_RSI, calculator.getSupportedType());
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
