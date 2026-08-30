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
 * <p>Verifies the align-only behavior: a BUY is emitted only when a bullish
 * candlestick pattern occurs near a support level. Every other combination
 * (bearish pattern near support, any pattern not near support, missing data)
 * falls back to HOLD.</p>
 */
class CandlestickAtSupportStrategyTest {

    private static final int PRIORITY = 4;
    private final SupportResistanceService srs = mock(SupportResistanceService.class);
    private final CandlestickContextStrategy strategy =
            new CandlestickContextStrategy(PRIORITY, srs, true);

    // ── Price builders ────────────────────────────────────────────────────
    // Prices are in ASCENDING order (oldest first); prices.get(size-1) is latest.

    /** Latest candle forms a HAMMER (small body, long lower wick, no upper wick). */
    private List<DailyPrice> hammerPrices() {
        return List.of(
                price("100", "99", "101", "98"),   // c1 (prior, bearish)
                price("100", "102", "103", "99"),  // c2 (bullish)
                price("99", "100", "100.5", "97")  // c3 HAMMER: body 1, range 3.5, lowerWick 2, upperWick 0.5
        );
    }

    /** Latest candle forms a SHOOTING_STAR (small body, long upper wick, no lower wick). */
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

    private SupportResistanceDto supportDto(BigDecimal supportPrice) {
        return levelsDto("support", supportPrice);
    }

    private SupportResistanceDto levelsDto(String type, BigDecimal price) {
        SupportResistanceDto.MajorLevel level = new SupportResistanceDto.MajorLevel(
                price, type, 1, BigDecimal.ONE);
        SupportResistanceDto dto = new SupportResistanceDto();
        dto.setMajorLevels(List.of(level));
        return dto;
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void testInsufficientData() {
        // null prices
        StrategyResult nullResult = strategy.evaluate(1L, null, null);
        assertEquals(StrategySignal.HOLD, nullResult.signal());
        assertEquals(0.50, nullResult.confidence());
        assertTrue(nullResult.reason().contains("Insufficient data"));

        // fewer than 3 prices
        List<DailyPrice> tooFew = List.of(price("100", "101", "102", "99"));
        StrategyResult fewResult = strategy.evaluate(1L, null, tooFew);
        assertEquals(StrategySignal.HOLD, fewResult.signal());
        assertEquals(0.50, fewResult.confidence());
        assertTrue(fewResult.reason().contains("Insufficient data"));
    }

    @Test
    void testNoPatternDetected() {
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(supportDto(new BigDecimal("100")));

        StrategyResult result = strategy.evaluate(1L, null, neutralPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("No candlestick pattern"));
    }

    @Test
    void testNoLevels() {
        when(srs.getLatestLevels(ArgumentMatchers.anyLong())).thenReturn(null);

        StrategyResult result = strategy.evaluate(1L, null, hammerPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("No support/resistance levels"));
    }

    @Test
    void testBullishPatternOnSupport_Buy() {
        // close of hammer candle = 100; support level at 100 (within 2% band)
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(supportDto(new BigDecimal("100")));

        StrategyResult result = strategy.evaluate(1L, null, hammerPrices());
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.75, result.confidence());
        assertTrue(result.reason().contains("on support"));
    }

    @Test
    void testBullishPatternNotNearSupport_Hold() {
        // support level far from close (100), well outside 2% band
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(supportDto(new BigDecimal("50")));

        StrategyResult result = strategy.evaluate(1L, null, hammerPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("but not near support"));
    }

    @Test
    void testBearishPatternOnSupport_HoldMismatch() {
        // bearish SHOOTING_STAR near support → mismatch (strategy expects bullish context)
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(supportDto(new BigDecimal("101"))); // close of star = 101

        StrategyResult result = strategy.evaluate(1L, null, shootingStarPrices());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.50, result.confidence());
        assertTrue(result.reason().contains("mismatch"));
    }
}
