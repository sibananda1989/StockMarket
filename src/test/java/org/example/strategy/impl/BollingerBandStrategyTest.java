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

class BollingerBandStrategyTest {

    private BollingerBandStrategy strategy;
    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        strategy = new BollingerBandStrategy(6);
        prices = List.of(
                new DailyPrice(null, new BigDecimal("90.00"), LocalDate.now())
        );
    }

    @Test
    void testBollingerBuy_BelowLowerBand() {
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.BOLLINGER_LOWER, new BigDecimal("95.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.BOLLINGER_UPPER, new BigDecimal("105.00"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.75, result.confidence());
        assertEquals("Price below lower band (price=90.00, lower=95.00)", result.reason());
    }

    @Test
    void testBollingerSell_AboveUpperBand() {
        prices = List.of(
                new DailyPrice(null, new BigDecimal("110.00"), LocalDate.now())
        );
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.BOLLINGER_LOWER, new BigDecimal("95.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.BOLLINGER_UPPER, new BigDecimal("105.00"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.SELL, result.signal());
        assertEquals(0.75, result.confidence());
        assertEquals("Price above upper band (price=110.00, upper=105.00)", result.reason());
    }

    @Test
    void testBollingerHold_WithinBands() {
        prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), LocalDate.now())
        );
        List<TechnicalIndicator> indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.BOLLINGER_LOWER, new BigDecimal("95.00"), LocalDate.now()),
                new TechnicalIndicator(null, IndicatorType.BOLLINGER_UPPER, new BigDecimal("105.00"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.30, result.confidence());
        assertTrue(result.reason().contains("Price within bands"));
    }

    @Test
    void testBollingerInsufficientData() {
        StrategyResult result = strategy.evaluate(1L, null, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence());
        assertEquals("Insufficient data", result.reason());
    }
}
