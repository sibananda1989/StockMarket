package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link EmaCrossoverStrategy}.
 *
 * <p>All price series are ascending (oldest first) as the engine hands them over
 * ({@code MultiStrategySignalEngine} fetches ASC). Flat segments keep both EMAs
 * exactly equal (SMA seed 100), so a single-bar jump creates a clean cross on
 * that bar; the 20% test jumps stay under the 30% split threshold while the
 * artifact test uses 50%.</p>
 */
class EmaCrossoverStrategyTest {

    private EmaCrossoverStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new EmaCrossoverStrategy(7);
    }

    private List<DailyPrice> bars(double... closes) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 1, 1);
        for (double c : closes) {
            prices.add(new DailyPrice(null, BigDecimal.valueOf(c), day));
            day = day.plusDays(1);
        }
        return prices;
    }

    private List<DailyPrice> flat55Then(double... tail) {
        double[] all = new double[55 + tail.length];
        for (int i = 0; i < 55; i++) all[i] = 100.0;
        for (int i = 0; i < tail.length; i++) all[55 + i] = tail[i];
        return bars(all);
    }

    @Test
    void freshUpCrossOnLatestBar_BuyAtFullConfidence() {
        // 55 flat bars + final +20% jump => up-cross at the latest bar (daysAgo 0).
        StrategyResult r = strategy.evaluate(1L, List.of(), flat55Then(120.0));

        assertEquals(StrategySignal.BUY, r.signal());
        assertEquals(0.85, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("crossed above"));
        assertTrue(r.reason().contains("0 day(s) ago"));
        assertEquals("EMA_CROSSOVER", r.strategyName());
        assertEquals(LocalDate.of(2026, 1, 1).plusDays(55), r.eventDate());
    }

    @Test
    void freshDownCrossOnLatestBar_SellAtFullConfidence() {
        // 55 flat bars + final -20% drop => down-cross at the latest bar.
        StrategyResult r = strategy.evaluate(1L, List.of(), flat55Then(80.0));

        assertEquals(StrategySignal.SELL, r.signal());
        assertEquals(0.85, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("crossed below"));
        assertEquals(LocalDate.of(2026, 1, 1).plusDays(55), r.eventDate());
    }

    @Test
    void crossThreeDaysAgo_ConfidenceDecayed() {
        // Flat 52 bars, +20% jump at index 52 (3 bars before the end), then 3 gentle up bars.
        double[] closes = new double[56];
        for (int i = 0; i < 52; i++) closes[i] = 100.0;
        closes[52] = 120.0;
        closes[53] = 121.0;
        closes[54] = 122.0;
        closes[55] = 123.0;

        StrategyResult r = strategy.evaluate(1L, List.of(), bars(closes));

        assertEquals(StrategySignal.BUY, r.signal());
        assertEquals(0.625, r.confidence(), 0.0001); // 0.85 - 3*0.075
        assertTrue(r.reason().contains("3 day(s) ago"));
        assertEquals(LocalDate.of(2026, 1, 1).plusDays(52), r.eventDate());
    }

    @Test
    void crossOlderThanFiveDays_NoSignal() {
        // Flat 50 bars, +20% jump at index 50 (daysAgo 5 — just outside the window),
        // then 5 gentle up bars (no newer cross).
        double[] closes = new double[56];
        for (int i = 0; i < 50; i++) closes[i] = 100.0;
        closes[50] = 120.0;
        closes[51] = 121.0;
        closes[52] = 122.0;
        closes[53] = 123.0;
        closes[54] = 124.0;
        closes[55] = 125.0;

        StrategyResult r = strategy.evaluate(1L, List.of(), bars(closes));

        assertEquals(StrategySignal.HOLD, r.signal());
        assertEquals(0.30, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("No fresh"));
    }

    @Test
    void splitArtifactCrossOnLatestBar_Rejected() {
        // 55 flat bars + final +50% jump (unadjusted split) => would be an up-cross,
        // but the jump exceeds the 30% artifact threshold => no signal.
        StrategyResult r = strategy.evaluate(1L, List.of(), flat55Then(150.0));

        assertEquals(StrategySignal.HOLD, r.signal());
        assertEquals(0.30, r.confidence(), 0.0001);
    }

    @Test
    void flatPrices_NoCross() {
        double[] closes = new double[60];
        for (int i = 0; i < 60; i++) closes[i] = 100.0;

        StrategyResult r = strategy.evaluate(1L, List.of(), bars(closes));

        assertEquals(StrategySignal.HOLD, r.signal());
        assertEquals(0.30, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("No fresh"));
        assertNull(r.eventDate());
    }

    @Test
    void insufficientHistory_ReturnsInsufficientData() {
        StrategyResult r = strategy.evaluate(1L, List.of(), bars(100.0, 101.0, 102.0, 103.0, 104.0));
        assertEquals(StrategySignal.HOLD, r.signal());
        assertEquals(0.0, r.confidence(), 0.0001);
        assertEquals("Insufficient data", r.reason());
    }

    @Test
    void nullPrices_ReturnsInsufficientData() {
        StrategyResult r = strategy.evaluate(1L, List.of(), null);
        assertEquals(StrategySignal.HOLD, r.signal());
        assertEquals(0.0, r.confidence(), 0.0001);
    }
}