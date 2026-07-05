package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VwapCalculatorTest {

    @Test
    void testVWAP_TypicalPriceCalculation() {
        // Constructor: (stock, closingPrice, openingPrice, highPrice, lowPrice, volume, priceDate)
        // H=110, L=90, C=100 → VWAP = (110+90+100)/3 = 100.00
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("105.00"),
                        new BigDecimal("110.00"), new BigDecimal("90.00"), 5000L,
                        LocalDate.of(2023, 6, 1))
        );

        VwapCalculator calculator = new VwapCalculator();
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testVWAP_AsymmetricPrices() {
        // H=120, L=80, C=100 → VWAP = (120+80+100)/3 = 100.00
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("110.00"),
                        new BigDecimal("120.00"), new BigDecimal("80.00"), 5000L,
                        LocalDate.of(2023, 6, 1))
        );

        VwapCalculator calculator = new VwapCalculator();
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testVWAP_VolumeWeightedAcrossDays() {
        // 2 days with equal volume — VWAP is volume-weighted average of both days
        // Day 1: H=95, L=85, C=90 → TP = 90.00
        // Day 2: H=120, L=100, C=115 → TP = 111.67
        // VWAP = (90.00*5000 + 111.67*5000) / 10000 = 100.83
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("90.00"), new BigDecimal("92.00"),
                        new BigDecimal("95.00"), new BigDecimal("85.00"), 5000L,
                        LocalDate.of(2023, 6, 1)),
                new DailyPrice(null, new BigDecimal("115.00"), new BigDecimal("112.00"),
                        new BigDecimal("120.00"), new BigDecimal("100.00"), 5000L,
                        LocalDate.of(2023, 6, 2))
        );

        VwapCalculator calculator = new VwapCalculator();
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("100.83"), result);
    }

    @Test
    void testVWAP_NullHighLow_UsesClose() {
        // H=null, L=null, C=100 → VWAP = (100+100+100)/3 = 100.00
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                        null, null, 5000L,
                        LocalDate.of(2023, 6, 1))
        );

        VwapCalculator calculator = new VwapCalculator();
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testVWAP_EmptyPrices_ThrowsException() {
        VwapCalculator calculator = new VwapCalculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of()));
    }

    @Test
    void testVWAP_GetSupportedType_ReturnsVWAP() {
        VwapCalculator calculator = new VwapCalculator();
        assertEquals(IndicatorType.VWAP, calculator.getSupportedType());
    }
}
