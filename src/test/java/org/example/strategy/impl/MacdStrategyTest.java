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

class MacdStrategyTest {

    private MacdStrategy strategy;
    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        strategy = new MacdStrategy(7);
        prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), LocalDate.now())
        );
    }

    @Test
    void testMacdBuy_ActualBullishCrossover() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("5.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("3.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("2.0"), LocalDate.now().minusDays(1)),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("4.0"), LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.10, result.confidence(), 0.001);
        assertTrue(result.reason().contains("bullish crossover"));
    }

    @Test
    void testMacdSell_ActualBearishCrossover() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("2.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("4.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("5.0"), LocalDate.now().minusDays(1)),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("3.0"), LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.SELL, result.signal());
        assertTrue(result.reason().contains("bearish crossover"));
    }

    @Test
    void testMacdBuy_BullishAlignmentFallback() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("5.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("3.0"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertTrue(result.confidence() <= 0.40);
        assertTrue(result.reason().contains("line above signal"));
    }

    @Test
    void testMacdSell_BearishAlignmentFallback() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("2.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("4.0"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.SELL, result.signal());
        assertTrue(result.confidence() <= 0.40);
        assertTrue(result.reason().contains("line below signal"));
    }

    @Test
    void testMacdHold_NoCrossoverSamePosition() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("5.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("3.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("4.0"), LocalDate.now().minusDays(1)),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("2.0"), LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertTrue(result.reason().contains("line above signal"));
    }

    @Test
    void testMacdHold_EqualLines() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("3.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("3.0"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.30, result.confidence());
        assertEquals("MACD line equals signal line", result.reason());
    }

    @Test
    void testMacdInsufficientData() {
        StrategyResult result = strategy.evaluate(1L, null, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence());
        assertEquals("Insufficient data", result.reason());
    }

    @Test
    void testMacdConfidenceCappedAtMax() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("20.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("0.0"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.MACD_LINE, new BigDecimal("5.0"), LocalDate.now().minusDays(1)),
                new TechnicalIndicator(null, IndicatorType.MACD_SIGNAL, new BigDecimal("10.0"), LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.90, result.confidence(), 0.001); // Capped at 0.90
    }
}
