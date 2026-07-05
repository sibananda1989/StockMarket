package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IchimokuCalculatorTest {

    // Constructor: (stock, closingPrice, openingPrice, highPrice, lowPrice, volume, priceDate)

    // ---- Tenkan-sen (9-period midpoint) ----

    @Test
    void testTenkanSen_CalculatesCorrectly() {
        // 9 days: high=101+i, low=91+i → highest=109, lowest=91
        // Tenkan = (109+91)/2 = 100.00
        List<DailyPrice> prices = createNineDays();

        IchimokuCalculator calculator = new IchimokuCalculator(IndicatorType.TENKAN_SEN);
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void testTenkanSen_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createNineDays().subList(0, 8);

        IchimokuCalculator calculator = new IchimokuCalculator(IndicatorType.TENKAN_SEN);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    // ---- Kijun-sen (26-period midpoint) ----

    @Test
    void testKijunSen_CalculatesCorrectly() {
        // 26 days: high=100+i, low=80+i
        // highest=125 (day 25), lowest=80 (day 0)
        // Kijun = (125+80)/2 = 102.50
        List<DailyPrice> prices = createTwentySixDays();

        IchimokuCalculator calculator = new IchimokuCalculator(IndicatorType.KIJUN_SEN);
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("102.50"), result);
    }

    @Test
    void testKijunSen_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createTwentySixDays().subList(0, 25);

        IchimokuCalculator calculator = new IchimokuCalculator(IndicatorType.KIJUN_SEN);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    // ---- Senkou Span A ----

    @Test
    void testSenkouSpanA_AverageOfTenkanAndKijun() {
        // 26 days: high=100+i, low=80+i
        // Tenkan (last 9, days 17-25): highest=125, lowest=97 → (125+97)/2=111.00
        // Kijun (last 26, days 0-25): highest=125, lowest=80 → (125+80)/2=102.50
        // Senkou A = (111.00+102.50)/2 = 106.75
        List<DailyPrice> prices = createTwentySixDays();

        IchimokuCalculator calculator = new IchimokuCalculator(IndicatorType.SENKOU_SPAN_A);
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("106.75"), result);
    }

    // ---- Senkou Span B (52-period midpoint) ----

    @Test
    void testSenkouSpanB_CalculatesCorrectly() {
        // 52 days: high=100+i, low=80+i
        // highest=151 (day 51), lowest=80 (day 0)
        // Senkou B = (151+80)/2 = 115.50
        List<DailyPrice> prices = createFiftyTwoDays();

        IchimokuCalculator calculator = new IchimokuCalculator(IndicatorType.SENKOU_SPAN_B);
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("115.50"), result);
    }

    @Test
    void testSenkouSpanB_InsufficientData_ThrowsException() {
        List<DailyPrice> prices = createFiftyTwoDays().subList(0, 51);

        IchimokuCalculator calculator = new IchimokuCalculator(IndicatorType.SENKOU_SPAN_B);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(prices));
    }

    // ---- Chikou Span ----

    @Test
    void testChikouSpan_ReturnsCurrentClose() {
        // Last close = 115.00
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("105.00"), new BigDecimal("100.00"),
                        new BigDecimal("110.00"), new BigDecimal("90.00"), 5000L,
                        LocalDate.of(2023, 6, 1)),
                new DailyPrice(null, new BigDecimal("110.00"), new BigDecimal("105.00"),
                        new BigDecimal("115.00"), new BigDecimal("95.00"), 5000L,
                        LocalDate.of(2023, 6, 2)),
                new DailyPrice(null, new BigDecimal("115.00"), new BigDecimal("110.00"),
                        new BigDecimal("120.00"), new BigDecimal("100.00"), 5000L,
                        LocalDate.of(2023, 6, 3))
        );

        IchimokuCalculator calculator = new IchimokuCalculator(IndicatorType.CHIKOU_SPAN);
        BigDecimal result = calculator.calculate(prices);

        assertEquals(new BigDecimal("115.00"), result);
    }

    // ---- Get supported type ----

    @Test
    void testGetSupportedType_ReturnsCorrectType() {
        assertEquals(IndicatorType.TENKAN_SEN,
                new IchimokuCalculator(IndicatorType.TENKAN_SEN).getSupportedType());
        assertEquals(IndicatorType.KIJUN_SEN,
                new IchimokuCalculator(IndicatorType.KIJUN_SEN).getSupportedType());
        assertEquals(IndicatorType.SENKOU_SPAN_A,
                new IchimokuCalculator(IndicatorType.SENKOU_SPAN_A).getSupportedType());
        assertEquals(IndicatorType.SENKOU_SPAN_B,
                new IchimokuCalculator(IndicatorType.SENKOU_SPAN_B).getSupportedType());
        assertEquals(IndicatorType.CHIKOU_SPAN,
                new IchimokuCalculator(IndicatorType.CHIKOU_SPAN).getSupportedType());
    }

    // ---- Helpers ----

    private List<DailyPrice> createNineDays() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal(String.valueOf(101 + i)),
                    new BigDecimal(String.valueOf(91 + i)),
                    5000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        return prices;
    }

    private List<DailyPrice> createTwentySixDays() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal(String.valueOf(100 + i)),
                    new BigDecimal(String.valueOf(80 + i)),
                    5000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        return prices;
    }

    private List<DailyPrice> createFiftyTwoDays() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal(String.valueOf(100 + i)),
                    new BigDecimal(String.valueOf(80 + i)),
                    5000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        return prices;
    }
}
