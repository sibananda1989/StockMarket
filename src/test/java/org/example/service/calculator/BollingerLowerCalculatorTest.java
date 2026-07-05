package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BollingerLowerCalculatorTest {

    @Test
    void testBollingerLower_CalculatesCorrectly() {
        List<DailyPrice> prices = createPriceSeries(100, 2.0, 25);
        BollingerLowerCalculator calculator = new BollingerLowerCalculator(20, 2.0);
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
    }

    @Test
    void testBollingerLower_BelowSMA() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 25);
        BollingerLowerCalculator lowerCalc = new BollingerLowerCalculator(20, 2.0);
        BigDecimal lower = lowerCalc.calculate(prices);

        SmaCalculator smaCalc = new SmaCalculator(20);
        BigDecimal sma = smaCalc.calculate(prices);

        assertTrue(lower.compareTo(sma) < 0,
                "Lower band should be below SMA, lower=" + lower + ", sma=" + sma);
    }

    @Test
    void testBollingerLower_ConstantPrices_EqualsSMA() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        BollingerLowerCalculator calculator = new BollingerLowerCalculator(20, 2.0);
        BigDecimal result = calculator.calculate(prices);
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testBollingerLower_WiderMultiplier_WiderBand() {
        List<DailyPrice> prices = createPriceSeries(100, 2.0, 25);
        BollingerLowerCalculator calc1 = new BollingerLowerCalculator(20, 2.0);
        BollingerLowerCalculator calc2 = new BollingerLowerCalculator(20, 3.0);

        BigDecimal band2 = calc1.calculate(prices);
        BigDecimal band3 = calc2.calculate(prices);

        assertTrue(band3.compareTo(band2) < 0,
                "3-sigma lower band should be lower than 2-sigma");
    }

    @Test
    void testBollingerLower_SymmetricWithUpper() {
        List<DailyPrice> prices = createPriceSeries(100, 1.5, 25);
        BollingerUpperCalculator upperCalc = new BollingerUpperCalculator(20, 2.0);
        BollingerLowerCalculator lowerCalc = new BollingerLowerCalculator(20, 2.0);

        BigDecimal upper = upperCalc.calculate(prices);
        BigDecimal lower = lowerCalc.calculate(prices);
        SmaCalculator smaCalc = new SmaCalculator(20);
        BigDecimal sma = smaCalc.calculate(prices);

        BigDecimal upperDist = upper.subtract(sma);
        BigDecimal lowerDist = sma.subtract(lower);

        assertEquals(upperDist, lowerDist,
                "Upper and lower bands should be symmetric around SMA");
    }

    @Test
    void testBollingerLower_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 19);
        BollingerLowerCalculator calculator = new BollingerLowerCalculator(20, 2.0);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testBollingerLower_GetSupportedType_ReturnsNull() {
        BollingerLowerCalculator calculator = new BollingerLowerCalculator(20, 2.0);
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
