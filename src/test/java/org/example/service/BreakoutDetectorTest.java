package org.example.service;

import org.example.entity.DailyPrice;
import org.example.entity.LevelType;
import org.example.entity.SupportResistanceLevel;
import org.example.repository.SupportResistanceLevelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BreakoutDetectorTest {

    @Mock
    private SupportResistanceLevelRepository srRepository;

    @InjectMocks
    private BreakoutDetector breakoutDetector;

    private final Long stockId = 1L;

    private DailyPrice dp(double close, double high, double low, int daysAgo) {
        return new DailyPrice(null,
                BigDecimal.valueOf(close), BigDecimal.valueOf(close - 1),
                BigDecimal.valueOf(high), BigDecimal.valueOf(low),
                5000L, LocalDate.now().minusDays(daysAgo));
    }

    @Test
    void testVolumeBreakout_AboveResistance() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) prices.add(dp(100, 102, 98, 20 - i));
        // close=110 > resistance=105, volume=20000 (4x avg of 5000)
        prices.add(new DailyPrice(null,
                BigDecimal.valueOf(110), BigDecimal.valueOf(109),
                BigDecimal.valueOf(112), BigDecimal.valueOf(107),
                20000L, LocalDate.now()));

        SupportResistanceLevel r = new SupportResistanceLevel();
        r.setLevelType(LevelType.MAJOR_RESISTANCE);
        r.setLevelValue(BigDecimal.valueOf(105));
        when(srRepository.findLatestLevels(stockId)).thenReturn(List.of(r));

        BreakoutDetector.BreakoutResult result = breakoutDetector.detect(prices, stockId);
        assertTrue(result.volumeBreakout, result.description);
    }

    @Test
    void testGapUp() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) prices.add(dp(100, 102, 98, 20 - i));
        // yesterday high=102, today low=103 → gap up
        prices.set(19, dp(100, 102, 98, 1));
        prices.add(dp(104, 106, 103, 0)); // low=103 > yesterday high=102

        when(srRepository.findLatestLevels(stockId)).thenReturn(List.of());

        BreakoutDetector.BreakoutResult result = breakoutDetector.detect(prices, stockId);
        assertTrue(result.gapUp, result.description);
    }

    @Test
    void testGapDown() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) prices.add(dp(100, 102, 98, 20 - i));
        // yesterday low=98, today high=97 → gap down
        prices.set(19, dp(100, 102, 98, 1));
        prices.add(dp(96, 97, 94, 0)); // high=97 < yesterday low=98

        when(srRepository.findLatestLevels(stockId)).thenReturn(List.of());

        BreakoutDetector.BreakoutResult result = breakoutDetector.detect(prices, stockId);
        assertTrue(result.gapDown, result.description);
    }

    @Test
    void testNoBreakout() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) prices.add(dp(100, 102, 98, 25 - i));
        when(srRepository.findLatestLevels(stockId)).thenReturn(List.of());

        BreakoutDetector.BreakoutResult result = breakoutDetector.detect(prices, stockId);
        assertFalse(result.volumeBreakout);
        assertFalse(result.gapUp);
        assertFalse(result.gapDown);
        assertFalse(result.rangeBreakout);
        assertEquals(0, result.score);
    }

    @Test
    void testInsufficientData() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 5; i++) prices.add(dp(100, 102, 98, 5 - i));

        BreakoutDetector.BreakoutResult result = breakoutDetector.detect(prices, stockId);
        assertFalse(result.volumeBreakout);
        assertEquals(0, result.score);
    }
}
