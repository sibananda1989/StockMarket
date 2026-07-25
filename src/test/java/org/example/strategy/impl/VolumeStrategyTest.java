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

class VolumeStrategyTest {

    private VolumeStrategy strategy;
    private List<DailyPrice> prices;
    private List<TechnicalIndicator> indicators;

    // Helper: build a price bar with explicit OHLC + volume
    private DailyPrice bar(double close, double open, double high, double low, long vol, int dayOffset) {
        return new DailyPrice(null,
                BigDecimal.valueOf(close), BigDecimal.valueOf(open),
                BigDecimal.valueOf(high), BigDecimal.valueOf(low),
                vol, LocalDate.now().minusDays(dayOffset));
    }

    @BeforeEach
    void setUp() {
        strategy = new VolumeStrategy(5, 1.5);
        prices = List.of(
                bar(105.00, 100.00, 110.00, 95.00, 15000L, 0),
                bar(100.00, 95.00, 105.00, 90.00, 10000L, 1),
                bar(95.00, 90.00, 100.00, 85.00, 10000L, 2)
        );
        indicators = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now())
        );
    }

    @Test
    void testVolumeBuy_SpikeWithRisingPrice() {
        // Ratio 1.5 = spike threshold, close > open = up = accumulation
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        assertEquals(0.7, result.confidence(), 0.0001);
        assertTrue(result.reason().contains("Volume spike"));
        assertTrue(result.reason().contains("accumulation"));
    }

    @Test
    void testVolumeSell_SpikeWithFallingPrice() {
        prices = List.of(
                bar(95.00, 100.00, 105.00, 90.00, 15000L, 0),
                bar(100.00, 95.00, 105.00, 90.00, 10000L, 1)
        );
        StrategyResult result = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.SELL, result.signal());
        // Log curve at threshold = 0.7; SELL also gets close-position boost (strong distribution)
        // close=95, low=90, high=105 → (95-90)/(105-90)=0.333 → mild distribution, no boost
        assertEquals(0.7, result.confidence(), 0.0001);
        assertTrue(result.reason().contains("Volume spike"));
        assertTrue(result.reason().contains("distribution"));
    }

    @Test
    void testVolumeHold_NormalVolume() {
        // Volume ratio below spike threshold
        List<TechnicalIndicator> normalIndicators = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.00"), LocalDate.now())
        );
        prices = List.of(
                bar(105.00, 100.00, 110.00, 95.00, 10000L, 0),
                bar(100.00, 95.00, 105.00, 90.00, 10000L, 1)
        );
        StrategyResult result = strategy.evaluate(1L, normalIndicators, prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.3, result.confidence(), 0.0001);
        assertTrue(result.reason().contains("Normal volume"));
    }

    @Test
    void testVolumeHold_NoIndicator() {
        // No VOLUME_RATIO indicator = insufficient data
        StrategyResult result = strategy.evaluate(1L, List.of(), prices);
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence(), 0.0001);
    }

    @Test
    void testVolumeHold_InsufficientPriceData() {
        StrategyResult result = strategy.evaluate(1L, indicators, List.of());
        assertEquals(StrategySignal.HOLD, result.signal());
        assertEquals(0.0, result.confidence(), 0.0001);
    }

    @Test
    void testVolumeHold_StrongSpikeWithRisingPrice() {
        // Strong spike (ratio 3.0 = 2x the 1.5 threshold)
        List<TechnicalIndicator> strongIndicators = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("3.00"), LocalDate.now())
        );
        StrategyResult result = strategy.evaluate(1L, strongIndicators, prices);
        assertEquals(StrategySignal.BUY, result.signal());
        // Log curve: 0.7 * (log(3.0) / log(1.5)) = 0.7 * 1.7095 ≈ 1.197 → capped at 1.0
        assertEquals(1.0, result.confidence(), 0.0001);
    }

    // ── Fix 5: Logarithmic confidence curve ──

    @Test
    void testConfidence_LogCurve_Ratio1_5() {
        // Exactly at threshold → 0.7
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now()));
        prices = List.of(bar(105.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        // close pos = (105-95)/(110-95) = 0.667 → mild accumulation, no boost
        assertEquals(0.7, r.confidence(), 0.0001);
    }

    @Test
    void testConfidence_LogCurve_Ratio2_15() {
        // Math.log is natural log: 0.7 * (ln(2.15)/ln(1.5)) = 0.7 * 1.888 ≈ 1.32 → capped at 1.0
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("2.15"), LocalDate.now()));
        prices = List.of(bar(105.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        // close pos (105-95)/(110-95)=0.667 → mild, no position boost
        assertEquals(1.0, r.confidence(), 0.0001);
    }

    @Test
    void testConfidence_LogCurve_Ratio5_0() {
        // Very high spike → near cap
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("5.00"), LocalDate.now()));
        prices = List.of(bar(105.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        // 0.7 * (log(5)/log(1.5)) = 0.7 * 1.797 ≈ 1.258 → capped at 1.0
        assertEquals(1.0, r.confidence(), 0.0001);
    }

    // ── Fix 2: Configurable spike factor ──

    @Test
    void testConfigurableSpikeFactorInjected() {
        VolumeStrategy custom = new VolumeStrategy(5, 2.0);
        // ratio 1.8 < 2.0 spike factor → should be HOLD, not BUY
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.80"), LocalDate.now()));
        prices = List.of(bar(105.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = custom.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.HOLD, r.signal());

        // ratio 2.5 > 2.0 → BUY
        List<TechnicalIndicator> ind2 = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("2.50"), LocalDate.now()));
        StrategyResult r2 = custom.evaluate(1L, ind2, prices);
        assertEquals(StrategySignal.BUY, r2.signal());
    }

    // ── Fix 6: Close position in range ──

    @Test
    void testClosePosition_StrongAccumulation() {
        // close=109, open=100, high=110, low=95 → (109-95)/(110-95)=0.933 → strong accumulation +0.05
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now()));
        prices = List.of(bar(109.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.BUY, r.signal());
        // 0.7 + 0.05 = 0.75
        assertEquals(0.75, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("strong accumulation"));
        assertTrue(r.reason().contains("close pos"));
    }

    @Test
    void testClosePosition_StrongDistribution() {
        // close=96, open=100, high=110, low=95 → (96-95)/(110-95)=0.067 → strong distribution +0.05
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now()));
        prices = List.of(bar(96.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.SELL, r.signal());
        // 0.7 + 0.05 = 0.75
        assertEquals(0.75, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("strong distribution"));
    }

    @Test
    void testZeroRangeDay_Neutral() {
        // high == low → close position neutral (0.5), no accumulation/distribution boost
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now()));
        prices = List.of(bar(100.0, 99.0, 100.0, 100.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.BUY, r.signal());
        // close > open → BUY; zero range → neutral, no position boost → 0.7
        assertEquals(0.7, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("zero-range"));
    }

    // ── Fix 3: OBV integration ──

    private TechnicalIndicator obv(double value, int dayOffset) {
        return new TechnicalIndicator(null, IndicatorType.OBV,
                BigDecimal.valueOf(value), LocalDate.now().minusDays(dayOffset));
    }

    @Test
    void testObvUpWithSpike_Buy() {
        // OBV trending up + volume spike + price up → boosted BUY
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now()),
                obv(200.0, 0), obv(190.0, 1), obv(180.0, 2), obv(170.0, 3), obv(160.0, 4), obv(150.0, 5)
        );
        prices = List.of(bar(105.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.BUY, r.signal());
        // 0.7 + 0.1 (close pos mild) ... actually close pos 0.667 mild, no boost;
        // OBV up adds +0.1 → 0.8
        assertEquals(0.8, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("OBV up"));
    }

    @Test
    void testObvDownWithSpike_Sell() {
        // OBV trending down + volume spike + price down → boosted SELL
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now()),
                obv(100.0, 0), obv(110.0, 1), obv(120.0, 2), obv(130.0, 3), obv(140.0, 4), obv(150.0, 5)
        );
        prices = List.of(bar(96.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.SELL, r.signal());
        // 0.7 + 0.05 (strong distribution close pos) + 0.1 (OBV down boost) = 0.85
        assertEquals(0.85, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("OBV down"));
    }

    @Test
    void testObvDivergenceReducesConfidence() {
        // Price up (BUY) but OBV trending down → divergence warning, reduce 15%
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now()),
                obv(100.0, 0), obv(110.0, 1), obv(120.0, 2), obv(130.0, 3), obv(140.0, 4), obv(150.0, 5)
        );
        prices = List.of(bar(105.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.BUY, r.signal());
        // 0.7 (no boost: close pos mild) * 0.85 (divergence) = 0.595
        assertEquals(0.595, r.confidence(), 0.0001);
    }

    @Test
    void testObvAbsentFallsBack() {
        // No OBV in indicators → volume-only logic, no OBV in reason
        prices = List.of(bar(105.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, indicators, prices);
        assertEquals(StrategySignal.BUY, r.signal());
        assertEquals(0.7, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("OBV n/a"));
    }

    @Test
    void testObvNeutralWithSinglePoint() {
        // Only one OBV point → neutral trend, no boost/reduction
        List<TechnicalIndicator> ind = List.of(
                new TechnicalIndicator(null, IndicatorType.VOLUME_RATIO, new BigDecimal("1.50"), LocalDate.now()),
                obv(200.0, 0)
        );
        prices = List.of(bar(105.0, 100.0, 110.0, 95.0, 15000L, 0),
                bar(100.0, 95.0, 105.0, 90.0, 10000L, 1));
        StrategyResult r = strategy.evaluate(1L, ind, prices);
        assertEquals(StrategySignal.BUY, r.signal());
        assertEquals(0.7, r.confidence(), 0.0001);
        assertTrue(r.reason().contains("OBV neutral"));
    }
}
