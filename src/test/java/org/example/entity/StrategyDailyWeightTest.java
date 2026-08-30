package org.example.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class StrategyDailyWeightTest {

    @Test
    void testBuilder() {
        Stock stock = new Stock("RELIANCE", "Reliance Industries", "Energy");

        StrategyDailyWeight weight = StrategyDailyWeight.builder()
                .stock(stock)
                .strategyName("VOLUME")
                .signalDate(LocalDate.of(2026, 7, 15))
                .signalType("BUY")
                .confidence(0.85)
                .contribution(3.5)
                .priority(5)
                .reason("Volume spike detected")
                .configVersion(1)
                .latestVolume(1500000L)
                .avgVolume(800000.0)
                .spikeThreshold(1200000.0)
                .computedAt(LocalDateTime.now())
                .build();

        assertEquals(stock, weight.getStock());
        assertEquals("VOLUME", weight.getStrategyName());
        assertEquals(LocalDate.of(2026, 7, 15), weight.getSignalDate());
        assertEquals("BUY", weight.getSignalType());
        assertEquals(0.85, weight.getConfidence(), 0.001);
        assertEquals(3.5, weight.getContribution(), 0.001);
        assertEquals(5, weight.getPriority());
        assertEquals("Volume spike detected", weight.getReason());
        assertEquals(1, weight.getConfigVersion());
        assertEquals(1500000L, weight.getLatestVolume());
        assertEquals(800000.0, weight.getAvgVolume(), 0.001);
        assertEquals(1200000.0, weight.getSpikeThreshold(), 0.001);
        assertNotNull(weight.getComputedAt());
    }

    @Test
    void testNoArgsConstructor() {
        StrategyDailyWeight weight = new StrategyDailyWeight();
        assertNull(weight.getStock());
        assertNull(weight.getStrategyName());
        assertNull(weight.getSignalDate());
        assertNull(weight.getSignalType());
        assertEquals(0.0, weight.getConfidence(), 0.001);
        assertEquals(0.0, weight.getContribution(), 0.001);
        assertEquals(0, weight.getPriority());
        assertNull(weight.getReason());
        assertEquals(0, weight.getConfigVersion());
        assertNull(weight.getLatestVolume());
        assertNull(weight.getAvgVolume());
        assertNull(weight.getSpikeThreshold());
        assertNull(weight.getComputedAt());
    }

    @Test
    void testAllArgsConstructor() {
        Stock stock = new Stock("TCS", "Tata Consultancy Services", "IT");

        StrategyDailyWeight weight = new StrategyDailyWeight(
                1L,
                stock,
                "RSI",
                LocalDate.of(2026, 7, 10),
                "SELL",
                0.75,
                -2.0,
                7,
                "RSI overbought",
                2,
                null,
                null,
                null,
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );

        assertEquals(1L, weight.getId());
        assertEquals(stock, weight.getStock());
        assertEquals("RSI", weight.getStrategyName());
        assertEquals(LocalDate.of(2026, 7, 10), weight.getSignalDate());
        assertEquals("SELL", weight.getSignalType());
        assertEquals(0.75, weight.getConfidence(), 0.001);
        assertEquals(-2.0, weight.getContribution(), 0.001);
        assertEquals(7, weight.getPriority());
        assertEquals("RSI overbought", weight.getReason());
        assertEquals(2, weight.getConfigVersion());
        assertNull(weight.getLatestVolume());
        assertNull(weight.getAvgVolume());
        assertNull(weight.getSpikeThreshold());
    }

    @Test
    void testSignalTypes() {
        Stock stock = new Stock("INFY", "Infosys", "IT");

        StrategyDailyWeight buyWeight = StrategyDailyWeight.builder()
                .stock(stock).strategyName("MACD").signalDate(LocalDate.now())
                .signalType("BUY").confidence(0.9).contribution(4.0)
                .priority(7).configVersion(1).computedAt(LocalDateTime.now())
                .build();

        StrategyDailyWeight sellWeight = StrategyDailyWeight.builder()
                .stock(stock).strategyName("MACD").signalDate(LocalDate.now())
                .signalType("SELL").confidence(0.8).contribution(-3.5)
                .priority(7).configVersion(1).computedAt(LocalDateTime.now())
                .build();

        StrategyDailyWeight holdWeight = StrategyDailyWeight.builder()
                .stock(stock).strategyName("MACD").signalDate(LocalDate.now())
                .signalType("HOLD").confidence(0.5).contribution(0.0)
                .priority(7).configVersion(1).computedAt(LocalDateTime.now())
                .build();

        assertEquals("BUY", buyWeight.getSignalType());
        assertEquals("SELL", sellWeight.getSignalType());
        assertEquals("HOLD", holdWeight.getSignalType());
    }
}
