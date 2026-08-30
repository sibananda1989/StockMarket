package org.example.service;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalAnalysisUtilsTest {

    @Test
    void testCalculateRsi14_SufficientData() {
        // Create 30 days of alternating up/down prices
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double close = 100 + (i % 2 == 0 ? 2 : -1);
            prices.add(createPrice(close, i));
        }

        BigDecimal rsi = TechnicalAnalysisUtils.calculateRsi12(prices);

        assertNotNull(rsi);
        assertTrue(rsi.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(rsi.compareTo(BigDecimal.valueOf(100)) <= 0);
    }

    @Test
    void testCalculateRsi14_InsufficientData() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            prices.add(createPrice(100, i));
        }

        BigDecimal rsi = TechnicalAnalysisUtils.calculateRsi12(prices);

        assertNull(rsi);
    }

    @Test
    void testCalculateRsi14_NullInput() {
        assertNull(TechnicalAnalysisUtils.calculateRsi12(null));
    }

    @Test
    void testHasBullishDivergence_Detected() {
        // Price makes lower low, RSI makes higher low
        List<DailyPrice> prices = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();

        // Build a pattern with two troughs:
        // Trough 1 at index 5: price=95, RSI=35
        // Trough 2 at index 15: price=90 (lower), RSI=40 (higher)
        double[] closes = {
            100, 102, 104, 103, 101, 95, 98, 100, 102, 104,
            106, 108, 107, 105, 103, 90, 93, 96, 99, 101
        };
        double[] rsi = {
            55, 60, 65, 62, 58, 35, 45, 55, 60, 65,
            70, 75, 72, 65, 58, 40, 50, 60, 65, 70
        };

        for (int i = 0; i < closes.length; i++) {
            prices.add(createPrice(closes[i], i));
            rsiValues.add(BigDecimal.valueOf(rsi[i]));
        }

        assertTrue(TechnicalAnalysisUtils.hasBullishDivergence(prices, rsiValues));
    }

    @Test
    void testHasBullishDivergence_NotDetected() {
        // Price makes higher low, RSI makes higher low — no divergence
        List<DailyPrice> prices = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();

        double[] closes = {
            100, 102, 104, 103, 101, 98, 100, 102, 104, 106,
            108, 110, 109, 107, 105, 102, 104, 106, 108, 110
        };
        double[] rsi = {
            55, 60, 65, 62, 58, 45, 55, 60, 65, 70,
            75, 80, 78, 70, 62, 50, 60, 65, 70, 75
        };

        for (int i = 0; i < closes.length; i++) {
            prices.add(createPrice(closes[i], i));
            rsiValues.add(BigDecimal.valueOf(rsi[i]));
        }

        assertFalse(TechnicalAnalysisUtils.hasBullishDivergence(prices, rsiValues));
    }

    @Test
    void testHasBearishDivergence_Detected() {
        // Price makes higher high, RSI makes lower high
        List<DailyPrice> prices = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();

        double[] closes = {
            100, 98, 96, 97, 99, 105, 103, 101, 99, 97,
            95, 93, 94, 96, 98, 108, 106, 104, 102, 100
        };
        double[] rsi = {
            50, 45, 40, 43, 48, 65, 58, 52, 48, 44,
            40, 38, 42, 48, 55, 60, 55, 50, 46, 42
        };

        for (int i = 0; i < closes.length; i++) {
            prices.add(createPrice(closes[i], i));
            rsiValues.add(BigDecimal.valueOf(rsi[i]));
        }

        assertTrue(TechnicalAnalysisUtils.hasBearishDivergence(prices, rsiValues));
    }

    @Test
    void testHasBearishDivergence_NotDetected() {
        // Price makes higher high, RSI makes higher high — no divergence
        List<DailyPrice> prices = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();

        double[] closes = {
            100, 98, 96, 97, 99, 105, 103, 101, 99, 97,
            95, 93, 94, 96, 98, 110, 108, 106, 104, 102
        };
        double[] rsi = {
            50, 45, 40, 43, 48, 65, 58, 52, 48, 44,
            40, 38, 42, 48, 55, 72, 68, 62, 56, 50
        };

        for (int i = 0; i < closes.length; i++) {
            prices.add(createPrice(closes[i], i));
            rsiValues.add(BigDecimal.valueOf(rsi[i]));
        }

        assertFalse(TechnicalAnalysisUtils.hasBearishDivergence(prices, rsiValues));
    }

    @Test
    void testHasDivergence_InsufficientData() {
        List<DailyPrice> prices = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            prices.add(createPrice(100, i));
            rsiValues.add(BigDecimal.valueOf(50));
        }

        assertFalse(TechnicalAnalysisUtils.hasBullishDivergence(prices, rsiValues));
        assertFalse(TechnicalAnalysisUtils.hasBearishDivergence(prices, rsiValues));
    }

    private DailyPrice createPrice(double close, int daysAgo) {
        return new DailyPrice(null,
                new BigDecimal(String.valueOf(close)),
                new BigDecimal(String.valueOf(close - 1)),
                new BigDecimal(String.valueOf(close + 2)),
                new BigDecimal(String.valueOf(close - 2)),
                5000L,
                LocalDate.now().minusDays(daysAgo));
    }
}
