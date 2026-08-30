package org.example.strategy.impl;

import org.example.dto.SupportResistanceDto;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.service.SupportResistanceService;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CandlestickContextStrategy}.
 *
 * <p>Verifies the align-only behavior: a SELL is emitted only when a bearish
 * candlestick pattern occurs near a resistance level. Every other combination
 * (bullish pattern near resistance, any pattern not near resistance, missing
 * data) falls back to HOLD.</p>
 */
class CandlestickAtResistanceStrategyTest {

    private static final int PRIORITY = 4;
    private final SupportResistanceService srs = mock(SupportResistanceService.class);
    private final CandlestickContextStrategy strategy =
            new CandlestickContextStrategy(PRIORITY, srs, false);

    // ── Price builders ────────────────────────────────────────────────────
    // Prices are in ASCENDING order (oldest first); prices.get(size-1) is latest.

    /** Latest candle forms a HAMMER (bullish single-candle pattern). */
    private List<DailyPrice> hammerPrices() {
        return List.of(
                price("100", "99", "101", "98"),   // c1 (prior, bearish)
                price("100", "102", "103", "99"),  // c2 (bullish)
                price("99", "100", "100.5", "97")  // c3 HAMMER: body 1, range 3.5, lowerWick 2, upperWick 0.5
        );
    }

    /** Latest candle forms a SHOOTING_STAR (bearish single-candle pattern). */
    private List<DailyPrice> shootingStarPrices() {
        return List.of(
                price("100", "99", "101", "98"),    // c1 (prior, bearish)
                price("100", "102", "103", "99"),   // c2 (bullish)
                price("100", "101", "103.5", "99")  // c3 SHOOTING_STAR: body 1, range 4.5, upperWick 2.5, lowerWick 1
        );
    }

    /** Alternating up/down candles that trigger no pattern. */
    private List<DailyPrice> neutralPrices() {
        return List.of(
                price("100", "102", "103", "99"),   // c1: bullish
                price("102", "98",  "104", "96"),   // c2: bearish (breaks pattern)
                price("98",  "101", "102", "97")    // c3: moderately bullish, no single-candle pattern
        );
    }

    private DailyPrice price(String open, String close, String high, String low) {
        return new DailyPrice((Stock) null, new BigDecimal(close), new BigDecimal(open),
                new BigDecimal(high), new BigDecimal(low), null, LocalDate.now());
    }

    // ── SupportResistanceDto builders ─────────────────────────────────────

    private SupportResistanceDto resistanceDto(BigDecimal resistancePrice) {
        SupportResistanceDto.MajorLevel level = new SupportResistanceDto.MajorLevel(
                resistancePrice, "resistance", 1, BigDecimal.ONE);
        SupportResistanceDto dto = new SupportResistanceDto();
        dto.setMajorLevels(List.of(level));
        return dto;
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void testInsufficientData() {
        StrategyResult nullResult = strategy.evaluate(1L, null, null);
        assertEquals(StrategySignal.HOLD, nullResult.signal());
        assertEquals(0.50, nullResult.confidence());
        assertTrue(nullResult.reason().contains("Insufficient data"));

        List<DailyPrice> tooFew = List.of(price("100", "101", "102", "99"));
        StrategyResult fewResult = strategy.evaluate(1L, null, tooFew);
        assertEquals(StrategySignal.HOLD, fewResult.signal());
        assertEquals(0.50, fewResult.confidence());
        assertTrue(fewResult.reason().contains("Insufficient data"));
    }

    @Test
    void testNoPatternDetected() {
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(resistanceDto(new BigDecimal("100")));

        StrategyResult result = strategy.evaluate(1L, null, neutralPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("No candlestick pattern"));
    }

    @Test
    void testNoLevels() {
        when(srs.getLatestLevels(ArgumentMatchers.anyLong())).thenReturn(null);

        StrategyResult result = strategy.evaluate(1L, null, shootingStarPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("No support/resistance levels"));
    }

    @Test
    void testBearishPatternOnResistance_Sell() {
        // close of shooting star = 101; resistance level at 101 (within 2% band)
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(resistanceDto(new BigDecimal("101")));

        StrategyResult result = strategy.evaluate(1L, null, shootingStarPrices());
        assertEquals(StrategySignal.SELL, result.signal());
        assertEquals(0.75, result.confidence());
        assertTrue(result.reason().contains("on resistance"));
    }

    @Test
    void testBullishPatternNearResistance_HoldMismatch() {
        // bullish HAMMER near resistance → mismatch (strategy expects bearish context)
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(resistanceDto(new BigDecimal("100"))); // close of hammer = 100

        StrategyResult result = strategy.evaluate(1L, null, hammerPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("mismatch"));
    }

    @Test
    void testBullishPatternNotNearResistance_Hold() {
        // resistance level far from close (100), well outside 2% band
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(resistanceDto(new BigDecimal("200")));

        StrategyResult result = strategy.evaluate(1L, null, hammerPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("but not near resistance"));
    }

    @Test
    void testBearishPatternNotNearResistance_Hold() {
        // bearish pattern exists but resistance level far from close
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(resistanceDto(new BigDecimal("200")));

        StrategyResult result = strategy.evaluate(1L, null, shootingStarPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("but not near resistance"));
    }
}
