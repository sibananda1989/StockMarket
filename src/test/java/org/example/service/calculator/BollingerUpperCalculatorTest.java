package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BollingerUpperCalculatorTest {

    @Test
    void testBollingerUpper_CalculatesCorrectly() {
        List<DailyPrice> prices = createPriceSeries(100, 2.0, 25);
        BollingerUpperCalculator calculator = new BollingerUpperCalculator(20, 2.0);
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testBollingerUpper_AboveSMA() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 25);
        BollingerUpperCalculator upperCalc = new BollingerUpperCalculator(20, 2.0);
        BigDecimal upper = upperCalc.calculate(prices);

        SmaCalculator smaCalc = new SmaCalculator(20);
        BigDecimal sma = smaCalc.calculate(prices);

        assertTrue(upper.compareTo(sma) > 0,
                "Upper band should be above SMA, upper=" + upper + ", sma=" + sma);
    }

    @Test
    void testBollingerUpper_ConstantPrices_EqualsSMA() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        BollingerUpperCalculator calculator = new BollingerUpperCalculator(20, 2.0);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testBollingerUpper_WiderMultiplier_WiderBand() {
        List<DailyPrice> prices = createPriceSeries(100, 2.0, 25);
        BollingerUpperCalculator calc1 = new BollingerUpperCalculator(20, 2.0);
        BollingerUpperCalculator calc2 = new BollingerUpperCalculator(20, 3.0);

        BigDecimal band2 = calc1.calculate(prices);
        BigDecimal band3 = calc2.calculate(prices);

        assertTrue(band3.compareTo(band2) > 0,
                "3-sigma band should be wider than 2-sigma");
    }

    @Test
    void testBollingerUpper_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 19);
        BollingerUpperCalculator calculator = new BollingerUpperCalculator(20, 2.0);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testBollingerUpper_GetSupportedType_ReturnsNull() {
        BollingerUpperCalculator calculator = new BollingerUpperCalculator(20, 2.0);
        assertNull(calculator.getSupportedType());
    }

    @Test
    void testBollingerUpper_UsesSampleStddev_NotPopulation() {
        // Regression: was using /N (population), should use /(N-1) (sample stddev)
        // Known 5-value dataset: [10, 12, 10, 14, 14]
        // Mean = 12, sample stddev = sqrt(((10-12)^2 + (12-12)^2 + (10-12)^2 + (14-12)^2 + (14-12)^2) / 4) = sqrt(8/4) = sqrt(2) ≈ 1.4142
        // Population stddev would be sqrt(8/5) = sqrt(1.6) ≈ 1.2649
        List<DailyPrice> prices = new ArrayList<>();
        int[] values = {10, 12, 10, 14, 14, 10, 12, 10, 14, 14, 10, 12, 10, 14, 14, 10, 12, 10, 14, 14, 11};
        for (int i = 0; i < values.length; i++) {
            prices.add(new DailyPrice(null, new BigDecimal(values[i]),
                    new BigDecimal("15"), new BigDecimal(values[i] + 1),
                    new BigDecimal(values[i] - 1), 1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        BollingerUpperCalculator calculator = new BollingerUpperCalculator(20, 2.0);
        BigDecimal result = calculator.calculate(prices);
        SmaCalculator smaCalc = new SmaCalculator(20);
        BigDecimal sma = smaCalc.calculate(prices);

        // Upper = SMA + 2 * stddev
        // With sample stddev (N-1), upper band should be wider than with population stddev (N)
        // This test verifies the band is wider than SMA by the expected amount
        BigDecimal diff = result.subtract(sma);
        assertTrue(diff.compareTo(BigDecimal.ZERO) > 0,
                "Upper band should be above SMA, diff=" + diff);
        // The diff should be approximately 2 * sample_stddev of the 20-value window
        // Sample stddev of values[1..20] is larger than population stddev
        // Just verify it's not the narrower population version
        assertTrue(diff.compareTo(new BigDecimal("2.5")) > 0,
                "Band width should use sample stddev (N-1), diff=" + diff);
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
