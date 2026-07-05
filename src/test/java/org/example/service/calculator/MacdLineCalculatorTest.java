package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MacdLineCalculatorTest {

    @Test
    void testMacdLine_Uptrend_ReturnsPositive() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 30);
        MacdLineCalculator calculator = new MacdLineCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0,
                "MACD should be positive in uptrend, got: " + result);
    }

    @Test
    void testMacdLine_Downtrend_ReturnsNegative() {
        List<DailyPrice> prices = createPriceSeries(200, -1.0, 30);
        MacdLineCalculator calculator = new MacdLineCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) < 0,
                "MACD should be negative in downtrend, got: " + result);

    }

    @Test
    void testMacdLine_ConstantPrices_ReturnsZero() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 1000L, LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        MacdLineCalculator calculator = new MacdLineCalculator();
        BigDecimal result = calculator.calculate(prices);
        assertEquals(BigDecimal.ZERO.setScale(4), result);
    }

    @Test
    void testMacdLine_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createPriceSeries(100, 1.0, 25);
        MacdLineCalculator calculator = new MacdLineCalculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    @Test
    void testMacdLine_MACD_Equals_EMA12_Minus_EMA26() {
        List<DailyPrice> prices = createPriceSeries(100, 1.5, 30);
        MacdLineCalculator calculator = new MacdLineCalculator();
        BigDecimal macd = calculator.calculate(prices);

        EmaCalculator ema12 = new EmaCalculator(12);
        EmaCalculator ema26 = new EmaCalculator(26);
        BigDecimal ema12Val = ema12.calculate(prices);
        BigDecimal ema26Val = ema26.calculate(prices);
        BigDecimal expected = ema12Val.subtract(ema26Val).setScale(4, BigDecimal.ROUND_HALF_UP);
        assertEquals(expected, macd);
    }

    @Test
    void testMacdLine_GetSupportedType_ReturnsNull() {
        MacdLineCalculator calculator = new MacdLineCalculator();
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
