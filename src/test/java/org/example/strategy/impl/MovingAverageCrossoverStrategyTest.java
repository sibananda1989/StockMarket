package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovingAverageCrossoverStrategyTest {

    private MovingAverageCrossoverStrategy strategy;
    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        strategy = new MovingAverageCrossoverStrategy(8);
        prices = List.of(
                new DailyPrice(null, new BigDecimal("110.00"), LocalDate.now())
        );
    }

    @Test
    void testMaBuy_GoldenCross() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("105.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("100.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("95.00"), LocalDate.now().minusDays(1)),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("100.00"), LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.90, result.confidence());
        assertTrue(result.reason().contains("Golden cross"));
    }

    @Test
    void testMaSell_DeathCross() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("95.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("100.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("105.00"), LocalDate.now().minusDays(1)),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("100.00"), LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.SELL, result.signal());
        assertEquals(0.90, result.confidence());
        assertTrue(result.reason().contains("Death cross"));
    }

    @Test
    void testMaBuy_BullishAlignmentFallback() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("105.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("100.00"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.60, result.confidence());
        assertTrue(result.reason().contains("Bullish alignment"));
    }

    @Test
    void testMaSell_BearishAlignmentFallback() {
        prices = List.of(
                new DailyPrice(null, new BigDecimal("90.00"), LocalDate.now())
        );
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("95.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("100.00"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.SELL, result.signal());
        assertEquals(0.60, result.confidence());
        assertTrue(result.reason().contains("Bearish alignment"));
    }

    @Test
    void testMaHold_MixedAlignment() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("105.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("110.00"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("Mixed alignment"));
    }

    @Test
    void testMaHold_NoCrossSameAlignment() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("105.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("100.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.SMA_20, new BigDecimal("102.00"), LocalDate.now().minusDays(1)),
                new TechnicalIndicator(null, IndicatorType.SMA_50, new BigDecimal("98.00"), LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.60, result.confidence());
        assertTrue(result.reason().contains("Bullish alignment"));
    }

    @Test
    void testMaInsufficientData_NoPrices() {
        StrategyResult result = strategy.evaluate(1L, List.of(), null);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence());
        assertEquals("Insufficient data", result.reason());
    }

    @Test
    void testMaInsufficientData_NoIndicators() {
        StrategyResult result = strategy.evaluate(1L, null, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence());
        assertEquals("Insufficient data", result.reason());
    }
}
