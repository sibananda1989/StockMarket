package org.example.strategy.impl;

import org.example.dto.SupportResistanceDto;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.Stock;
import org.example.entity.TechnicalIndicator;
import org.example.service.SupportResistanceService;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Sma44PullbackBounceStrategyTest {

    private static final int PRIORITY = 8;
    private final SupportResistanceService srs = mock(SupportResistanceService.class);
    private final Sma44PullbackBounceStrategy strategy =
            new Sma44PullbackBounceStrategy(PRIORITY, srs);

    private TechnicalIndicator ti(IndicatorType type, double value) {
        return new TechnicalIndicator(null, type, BigDecimal.valueOf(value), LocalDate.now());
    }

    private DailyPrice price(String open, String close, String high, String low, Long volume) {
        return new DailyPrice((Stock) null,
                new BigDecimal(close), new BigDecimal(open),
                new BigDecimal(high), new BigDecimal(low),
                volume, LocalDate.now());
    }

    private SupportResistanceDto srWithResistance(BigDecimal... resistances) {
        SupportResistanceDto dto = new SupportResistanceDto();
        List<SupportResistanceDto.MajorLevel> levels = new ArrayList<>();
        for (BigDecimal r : resistances) {
            levels.add(new SupportResistanceDto.MajorLevel(r, "resistance", 1, BigDecimal.ONE));
        }
        dto.setMajorLevels(levels);
        return dto;
    }

    private List<DailyPrice> trendingUp(int n, double start, double step) {
        List<DailyPrice> prices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double p = start + i * step;
            prices.add(price(
                    String.valueOf(p),
                    String.valueOf(p + 0.3),
                    String.valueOf(p + 0.5),
                    String.valueOf(p),
                    1000L + i
            ));
        }
        return prices;
    }

    @Test
    void testInsufficientData() {
        List<DailyPrice> tooFew = trendingUp(48, 100.0, 0.5);
        StrategyResult result = strategy.evaluate(1L, List.of(), tooFew);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence(), 0.001);
        assertTrue(result.reason().contains("Insufficient data"));
    }

    @Test
    void testNullPrices() {
        StrategyResult result = strategy.evaluate(1L, List.of(), null);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence(), 0.001);
        assertTrue(result.reason().contains("Insufficient data"));
    }

    @Test
    void testTrendFilterFails_CloseBelowSma() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        List<TechnicalIndicator> ind = List.of(ti(IndicatorType.SMA_44, 200.0));
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(400)));
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertTrue(result.reason().contains("trend filter"));
    }

    @Test
    void testPullbackNotDetected() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        List<TechnicalIndicator> ind = List.of(ti(IndicatorType.SMA_44, 120.0));
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(400)));
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertTrue(result.reason().contains("did not reach SMA44"));
    }

    @Test
    void testBounceNotConfirmed_WeakBody() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        prices.set(49, price("121.8", "122.1", "123.0", "121.5", 2000L));
        List<TechnicalIndicator> ind = List.of(
                ti(IndicatorType.SMA_44, 122.0),
                ti(IndicatorType.ATR, 0.5)
        );
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(400)));
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertTrue(result.reason().contains("bounce not confirmed"));
    }

    @Test
    void testATRFilterFails() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        prices.set(49, price("113.5", "115.0", "115.1", "113.5", 2000L));
        List<TechnicalIndicator> ind = List.of(
                ti(IndicatorType.SMA_44, 114.0),
                ti(IndicatorType.ATR, 2.5)
        );
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(400)));
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertTrue(result.reason().contains("candle range"));
    }

    @Test
    void testResistanceTooClose() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        prices.set(49, price("113.5", "115.0", "115.1", "113.5", 2000L));
        List<TechnicalIndicator> ind = List.of(
                ti(IndicatorType.SMA_44, 114.0),
                ti(IndicatorType.ATR, 0.5),
                ti(IndicatorType.RSI, 30.0),
                ti(IndicatorType.ADX, 30.0)
        );
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(116.15)));
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertTrue(result.reason().contains("resistance too close"));
    }

    @Test
    void testFullBuyScenario() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        prices.set(49, price("113.5", "115.0", "115.1", "113.5", 2000L));
        List<TechnicalIndicator> ind = List.of(
                ti(IndicatorType.SMA_44, 114.0),
                ti(IndicatorType.ATR, 0.5),
                ti(IndicatorType.RSI, 30.0),
                ti(IndicatorType.ADX, 30.0)
        );
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(126.5)));
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertTrue(result.confidence() > 0.0);
        assertTrue(result.reason().contains("SMA44 pullback bounce"));
    }

    @Test
    void testNullIndicators_NoCrash() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(400)));
        StrategyResult result = strategy.evaluate(1L, null, prices);
        assertNotNull(result);
        assertNotNull(result.signal());
    }

    @Test
    void testNoResistanceLevels_PassesFilter() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        prices.set(49, price("113.5", "115.0", "115.1", "113.5", 2000L));
        List<TechnicalIndicator> ind = List.of(
                ti(IndicatorType.SMA_44, 114.0),
                ti(IndicatorType.ATR, 0.5),
                ti(IndicatorType.RSI, 30.0),
                ti(IndicatorType.ADX, 30.0)
        );
        when(srs.getLatestLevels(ArgumentMatchers.anyLong())).thenReturn(null);
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertTrue(result.confidence() > 0.0);
    }

    @Test
    void testFullBuy_HighConfidence() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        prices.set(49, price("113.5", "115.0", "115.1", "113.5", 2000L));
        List<TechnicalIndicator> ind = List.of(
                ti(IndicatorType.SMA_44, 114.0),
                ti(IndicatorType.ATR, 0.5),
                ti(IndicatorType.RSI, 30.0),
                ti(IndicatorType.ADX, 30.0)
        );
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(126.5)));
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        // score=11 → confidence=0.85
        assertEquals(0.85, result.confidence(), 0.001);
    }

    @Test
    void testTrendFilterFails_SmaDeclining() {
        List<DailyPrice> prices = trendingUp(50, 100.0, 0.5);
        // sma44_5d ≈ 111.55 computed from prices.subList(0,45)
        // sma44Today=110 < 111.55 → slope check fails
        List<TechnicalIndicator> ind = List.of(ti(IndicatorType.SMA_44, 110.0));
        when(srs.getLatestLevels(ArgumentMatchers.anyLong()))
                .thenReturn(srWithResistance(BigDecimal.valueOf(400)));
        StrategyResult result = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertTrue(result.reason().contains("trend filter"));
    }
}
