package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VolumeStrategyTest {

    private VolumeStrategy strategy;
    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        strategy = new VolumeStrategy(5);
        prices = List.of(
                new DailyPrice(null, new BigDecimal("105.00"), new BigDecimal("100.00"), new BigDecimal("110.00"), new BigDecimal("95.00"), 15000L, LocalDate.now()),
                new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("105.00"), new BigDecimal("90.00"), 10000L, LocalDate.now().minusDays(1)),
                new DailyPrice(null, new BigDecimal("95.00"), new BigDecimal("90.00"), new BigDecimal("100.00"), new BigDecimal("85.00"), 10000L, LocalDate.now().minusDays(2))
        );
    }

    @Test
    void testVolumeBuy_SpikeWithRisingPrice() {
        StrategyResult result = strategy.evaluate(1L, null, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.65, result.confidence());
        assertTrue(result.reason().contains("Volume spike with rising price"));
    }

    @Test
    void testVolumeSell_SpikeWithFallingPrice() {
        prices = List.of(
                new DailyPrice(null, new BigDecimal("95.00"), new BigDecimal("100.00"), new BigDecimal("105.00"), new BigDecimal("90.00"), 15000L, LocalDate.now()),
                new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("105.00"), new BigDecimal("90.00"), 10000L, LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, null, prices);
        assertEquals(StrategySignal.SELL, result.signal());
        assertEquals(0.65, result.confidence());
        assertTrue(result.reason().contains("Volume spike with falling price"));
    }

    @Test
    void testVolumeHold_NormalVolume() {
        prices = List.of(
                new DailyPrice(null, new BigDecimal("105.00"), new BigDecimal("100.00"), new BigDecimal("110.00"), new BigDecimal("95.00"), 10000L, LocalDate.now()),
                new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("105.00"), new BigDecimal("90.00"), 10000L, LocalDate.now().minusDays(1))
        );
        StrategyResult result = strategy.evaluate(1L, null, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.20, result.confidence());
        assertTrue(result.reason().contains("Normal volume"));
    }

    @Test
    void testVolumeHold_InsufficientData() {
        StrategyResult result = strategy.evaluate(1L, null, List.of());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence());
        assertEquals("Insufficient data", result.reason());
    }
}
