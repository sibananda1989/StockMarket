package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdxCalculatorTest {

    @Test
    void testADX_CalculatesCorrectly() {
        // Given: 30 days of sample price data
        List<DailyPrice> prices = createSamplePrices(30, 100, 2);

        // When: Calculating ADX
        AdxCalculator calculator = new AdxCalculator(14);
        BigDecimal result = calculator.calculate(prices);

        // Then: Result should be between 0 and 100
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0,
                "ADX should be >= 0, got: " + result);
        assertTrue(result.compareTo(new BigDecimal("100")) <= 0,
                "ADX should be <= 100, got: " + result);
    }

    @Test
    void testADX_InsufficientData_ThrowsException() {
        // Given: Only 20 days of price data (need 29 for period 14)
        List<DailyPrice> prices = createSamplePrices(20, 100, 2);

        // When/Then: Should throw exception
        AdxCalculator calculator = new AdxCalculator(14);
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(prices));
    }

    @Test
    void testADX_StrongUptrend_ReturnsHighADX() {
        // Given: Consistent uptrend (each high > previous high, each low > previous low)
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double base = 100 + i * 3;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base - 2)),
                    new BigDecimal(String.valueOf(base + 3)),    // new high each day
                    new BigDecimal(String.valueOf(base - 1)),     // new low each day (higher)
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating ADX
        AdxCalculator calculator = new AdxCalculator(14);
        BigDecimal result = calculator.calculate(prices);

        // Then: Strong trend should produce ADX > 20
        assertTrue(result.compareTo(new BigDecimal("20")) > 0,
                "Strong uptrend ADX should be > 20, got: " + result);
    }

    @Test
    void testADX_StrongDowntrend_ReturnsHighADX() {
        // Given: Consistent downtrend
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double base = 200 - i * 3;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base - 2)),
                    new BigDecimal(String.valueOf(base + 1)),     // lower high
                    new BigDecimal(String.valueOf(base - 3)),      // new low each day
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating ADX
        AdxCalculator calculator = new AdxCalculator(14);
        BigDecimal result = calculator.calculate(prices);

        // Then: Strong downtrend should still produce high ADX (ADX measures strength, not direction)
        assertTrue(result.compareTo(new BigDecimal("20")) > 0,
                "Strong downtrend ADX should be > 20, got: " + result);
    }

    @Test
    void testPlusDI_MeasuresUpwardTrend() {
        // Given: 30 days of uptrend
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double base = 100 + i * 3;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base - 2)),
                    new BigDecimal(String.valueOf(base + 3)),
                    new BigDecimal(String.valueOf(base - 1)),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating +DI and -DI
        PlusDiCalculator plusDiCalc = new PlusDiCalculator();
        MinusDiCalculator minusDiCalc = new MinusDiCalculator();
        BigDecimal plusDi = plusDiCalc.calculate(prices);
        BigDecimal minusDi = minusDiCalc.calculate(prices);

        // Then: In uptrend, +DI should be > -DI
        assertTrue(plusDi.compareTo(minusDi) > 0,
                "In uptrend +DI should be > -DI, got +DI=" + plusDi + " -DI=" + minusDi);
    }

    @Test
    void testMinusDI_MeasuresDownwardTrend() {
        // Given: 30 days of downtrend
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double base = 200 - i * 3;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(base)),
                    new BigDecimal(String.valueOf(base - 2)),
                    new BigDecimal(String.valueOf(base + 1)),
                    new BigDecimal(String.valueOf(base - 3)),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating +DI and -DI
        PlusDiCalculator plusDiCalc = new PlusDiCalculator();
        MinusDiCalculator minusDiCalc = new MinusDiCalculator();
        BigDecimal plusDi = plusDiCalc.calculate(prices);
        BigDecimal minusDi = minusDiCalc.calculate(prices);

        // Then: In downtrend, -DI should be > +DI
        assertTrue(minusDi.compareTo(plusDi) > 0,
                "In downtrend -DI should be > +DI, got +DI=" + plusDi + " -DI=" + minusDi);
    }

    @Test
    void testPlusDi_InsufficientData_ThrowsException() {
        // Given: Only 20 days of price data
        List<DailyPrice> prices = createSamplePrices(20, 100, 2);

        // When/Then: Should throw exception
        PlusDiCalculator calculator = new PlusDiCalculator();
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(prices));
    }

    @Test
    void testMinusDi_InsufficientData_ThrowsException() {
        // Given: Only 20 days of price data
        List<DailyPrice> prices = createSamplePrices(20, 100, 2);

        // When/Then: Should throw exception
        MinusDiCalculator calculator = new MinusDiCalculator();
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(prices));
    }

    @Test
    void testADX_AllThreeValues_Consistent() {
        // Given: Same price data
        List<DailyPrice> prices = createSamplePrices(30, 100, 2);

        // When: Calculating all three values
        AdxCalculator adxCalc = new AdxCalculator(14);
        PlusDiCalculator plusDiCalc = new PlusDiCalculator();
        MinusDiCalculator minusDiCalc = new MinusDiCalculator();

        BigDecimal adx = adxCalc.calculate(prices);
        BigDecimal plusDi = plusDiCalc.calculate(prices);
        BigDecimal minusDi = minusDiCalc.calculate(prices);

        // Then: All should be valid values
        assertNotNull(adx);
        assertNotNull(plusDi);
        assertNotNull(minusDi);
        assertTrue(adx.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(plusDi.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(minusDi.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(adx.compareTo(new BigDecimal("100")) <= 0);
        assertTrue(plusDi.compareTo(new BigDecimal("100")) <= 0);
        assertTrue(minusDi.compareTo(new BigDecimal("100")) <= 0);
    }

    @Test
    void testADX_GetSupportedType_ReturnsADX() {
        AdxCalculator calculator = new AdxCalculator();
        assertEquals(org.example.entity.IndicatorType.ADX, calculator.getSupportedType());
    }

    @Test
    void testPlusDi_GetSupportedType_ReturnsPLUS_DI() {
        PlusDiCalculator calculator = new PlusDiCalculator();
        assertEquals(org.example.entity.IndicatorType.PLUS_DI, calculator.getSupportedType());
    }

    @Test
    void testMinusDi_GetSupportedType_ReturnsMINUS_DI() {
        MinusDiCalculator calculator = new MinusDiCalculator();
        assertEquals(org.example.entity.IndicatorType.MINUS_DI, calculator.getSupportedType());
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
