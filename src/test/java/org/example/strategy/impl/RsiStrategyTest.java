package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RsiStrategyTest {

    private RsiStrategy strategy;
    private List<TechnicalIndicator> indicators;
    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        strategy = new RsiStrategy(7);
        prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), LocalDate.now())
        );
    }

    @Test
    void testRsiBuy_Oversold() {
        indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.RSI, new BigDecimal("25.0"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.85, result.confidence());
        assertEquals("RSI oversold (25.0)", result.reason());
    }

    @Test
    void testRsiSell_Overbought() {
        indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.RSI, new BigDecimal("75.0"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.SELL, result.signal());
        assertEquals(0.80, result.confidence());
        assertEquals("RSI overbought (75.0)", result.reason());
    }

    @Test
    void testRsiHold_NeutralLow() {
        indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.RSI, new BigDecimal("40.0"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertEquals("RSI neutral-low (40.0)", result.reason());
    }

    @Test
    void testRsiHold_NeutralHigh() {
        indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.RSI, new BigDecimal("60.0"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.40, result.confidence());
        assertEquals("RSI neutral-high (60.0)", result.reason());
    }

    @Test
    void testRsiInsufficientData() {
        StrategyResult result = strategy.evaluate(1L, null, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence());
        assertEquals("Insufficient data", result.reason());
    }
}
