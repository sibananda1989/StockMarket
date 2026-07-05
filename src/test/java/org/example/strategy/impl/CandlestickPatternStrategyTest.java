package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.service.SupportResistanceService;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandlestickPatternStrategyTest {

    private SupportResistanceService supportResistanceService;
    private CandlestickPatternStrategy strategy;

    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        supportResistanceService = mock(SupportResistanceService.class);
        strategy = new CandlestickPatternStrategy(4, supportResistanceService);
    }

    @Test
    void testHammerOnSupport_BuySignal() {
        DailyPrice current = new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("95.00"), 
            new BigDecimal("101.00"), new BigDecimal("94.00"), 1000L, LocalDate.now());
        DailyPrice prev1 = new DailyPrice(null, new BigDecimal("95.00"), new BigDecimal("96.00"), 
            new BigDecimal("96.50"), new BigDecimal("94.00"), 1200L, LocalDate.now().minusDays(1));
        DailyPrice prev2 = new DailyPrice(null, new BigDecimal("90.00"), new BigDecimal("92.00"), 
            new BigDecimal("96.00"), new BigDecimal("89.00"), 1500L, LocalDate.now().minusDays(2));
        
        prices = List.of(current, prev1, prev2);
        
        StrategyResult result = strategy.evaluate(1L, List.of(), prices);
        
        assertEquals(StrategySignal.HOLD, result.signal());
    }

    @Test
    void testInsufficientData() {
        StrategyResult result = strategy.evaluate(1L, List.of(), List.of());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence());
        assertEquals("Insufficient data", result.reason());
    }

    @Test
    void testNoPatternDetected() {
        DailyPrice current = new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"), 
            new BigDecimal("101.00"), new BigDecimal("99.00"), 1000L, LocalDate.now());
        DailyPrice prev1 = new DailyPrice(null, new BigDecimal("101.00"), new BigDecimal("101.00"), 
            new BigDecimal("102.00"), new BigDecimal("100.00"), 1200L, LocalDate.now().minusDays(1));
        DailyPrice prev2 = new DailyPrice(null, new BigDecimal("102.00"), new BigDecimal("102.00"), 
            new BigDecimal("103.00"), new BigDecimal("101.00"), 1500L, LocalDate.now().minusDays(2));
        
        prices = List.of(current, prev1, prev2);
        
        StrategyResult result = strategy.evaluate(1L, List.of(), prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("No candlestick pattern"));
    }

    @Test
    void testNoSupportResistanceLevels() {
        DailyPrice current = new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("95.00"), 
            new BigDecimal("101.00"), new BigDecimal("94.00"), 1000L, LocalDate.now());
        DailyPrice prev1 = new DailyPrice(null, new BigDecimal("95.00"), new BigDecimal("96.00"), 
            new BigDecimal("96.50"), new BigDecimal("94.00"), 1200L, LocalDate.now().minusDays(1));
        DailyPrice prev2 = new DailyPrice(null, new BigDecimal("90.00"), new BigDecimal("92.00"), 
            new BigDecimal("96.00"), new BigDecimal("89.00"), 1500L, LocalDate.now().minusDays(2));
        
        prices = List.of(current, prev1, prev2);
        
        when(supportResistanceService.getLatestLevels(anyLong())).thenReturn(null);
        
        StrategyResult result = strategy.evaluate(1L, List.of(), prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("No support/resistance levels"));
    }
}
