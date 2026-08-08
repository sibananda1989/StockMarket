package org.example.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.SupportResistanceDto;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.service.SupportResistanceService;
import org.example.service.TechnicalAnalysisUtils;
import org.example.service.calculator.ATRCalculator;
import org.example.service.calculator.AdxCalculator;
import org.example.service.calculator.CandlestickPatternCalculator;
import org.example.service.calculator.RsiCalculator;
import org.example.service.calculator.SmaCalculator;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class Sma44PullbackBounceStrategy extends TradingStrategy {

    private static final int MIN_PRICES = 49;
    private static final int SMA_PERIOD = 44;
    private static final int SMA_SLOPE_DAYS = 5;
    private static final double PULLBACK_TOLERANCE = 0.005;
    private static final double VOLUME_SPIKE_FACTOR = 1.5;
    private static final int VOLUME_AVG_PERIOD = 20;
    private static final double RSI_OVERSOLD = 35.0;
    private static final double ADX_STRENGTH = 25.0;
    private static final double RESISTANCE_MIN_PCT = 2.0;
    private static final double RESISTANCE_BONUS_PCT = 5.0;
    private static final int SWING_LOOKBACK = 20;
    private static final int SWING_PIVOT_BARS = 2;
    private static final double ATR_RANGE_FRACTION = 0.8;

    private static final Map<Integer, Double> CONFIDENCE_MAP = Map.of(
            5, 0.38,  6, 0.46,  7, 0.54,  8, 0.62,
            9, 0.69, 10, 0.77, 11, 0.85, 12, 0.92,
            13, 1.00
    );

    private final SupportResistanceService supportResistanceService;

    public Sma44PullbackBounceStrategy(int priority, SupportResistanceService supportResistanceService) {
        super("SMA44_PULLBACK", priority);
        this.supportResistanceService = supportResistanceService;
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        if (prices == null || prices.size() < MIN_PRICES) {
            return insufficientData();
        }

        DailyPrice latest = prices.get(prices.size() - 1);
        BigDecimal close = latest.getClosingPrice();
        BigDecimal low = latest.getLowPrice();
        BigDecimal high = latest.getHighPrice();
        BigDecimal open = latest.getOpeningPrice();
        BigDecimal range = high.subtract(low);

        // Resolve SMA44 (today)
        double sma44Today = resolveIndicator(indicators, IndicatorType.SMA_44)
                .orElseGet(() -> {
                    try {
                        return new SmaCalculator(SMA_PERIOD).calculate(prices).doubleValue();
                    } catch (Exception e) {
                        return Double.NaN;
                    }
                });
        if (Double.isNaN(sma44Today)) {
            return insufficientData();
        }

        // SMA44 (5 days ago) for slope check
        double sma44_5d;
        try {
            sma44_5d = new SmaCalculator(SMA_PERIOD)
                    .calculate(prices.subList(0, prices.size() - SMA_SLOPE_DAYS))
                    .doubleValue();
        } catch (Exception e) {
            return insufficientData();
        }

        // === 1. Trend Filter (REJECT if fail) ===
        boolean trendPass = isSMA44TrendingUp(close, sma44Today, sma44_5d);
        if (!trendPass) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.0,
                    "SMA44_PULLBACK: trend filter failed (close<=SMA44 or SMA44 not rising)",
                    getName(), getPriority());
        }

        // === 2. HH+HL Structure (optional +1) ===
        boolean[] hhHl = detectHigherHighHigherLow(prices, SWING_LOOKBACK, SWING_PIVOT_BARS);
        boolean hasHH = hhHl[0];
        boolean hasHL = hhHl[1];
        boolean hhHlPass = hasHH && hasHL;

        // === 3. Pullback (REJECT if fail) ===
        boolean[] pb = detectPullback(low, sma44Today);
        boolean pulledBack = pb[0];
        boolean touchedOrBelowSma44 = pb[1];
        if (!pulledBack) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.0,
                    String.format("SMA44_PULLBACK: price did not reach SMA44 (low=%.2f, sma44=%.2f)",
                            low.doubleValue(), sma44Today),
                    getName(), getPriority());
        }

        // === 4. Bounce Confirmation (REJECT if fail) ===
        boolean[] bounce = detectBullishBounce(latest, prices, sma44Today);
        boolean bounceConfirmed = bounce[0];
        boolean strongBounce = bounce[1];
        if (!bounceConfirmed) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.0,
                    "SMA44_PULLBACK: bounce not confirmed (weak candle)",
                    getName(), getPriority());
        }

        // === 5. ATR Filter (REJECT if fail) ===
        double rangeVal = range.doubleValue();
        double atr14 = resolveIndicator(indicators, IndicatorType.ATR)
                .orElseGet(() -> {
                    try {
                        return new ATRCalculator(14).calculate(prices).doubleValue();
                    } catch (Exception e) {
                        return 0.0;
                    }
                });
        if (rangeVal < ATR_RANGE_FRACTION * atr14) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.0,
                    String.format("SMA44_PULLBACK: candle range < 0.8x ATR (range=%.2f, atr=%.2f)",
                            rangeVal, atr14),
                    getName(), getPriority());
        }

        // === 6. Resistance Filter (REJECT if fail) ===
        SupportResistanceDto srLevels = supportResistanceService.getLatestLevels(stockId);
        Double resistancePct = isNearResistance(close, srLevels);
        if (resistancePct != null && resistancePct < RESISTANCE_MIN_PCT) {
            return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.0,
                    String.format("SMA44_PULLBACK: nearest resistance too close (%.1f%%)", resistancePct),
                    getName(), getPriority());
        }

        // === 7. Confidence Scoring ===
        double rsi = resolveRsi(indicators, prices);
        double adx = resolveAdx(indicators, prices);
        boolean volSpike = detectRisingVolume(latest, prices);
        boolean bullishDivergence = detectBullishDivergence(prices);

        int score = calculateSMA44Confidence(
                volSpike,
                rsi < RSI_OVERSOLD,
                bullishDivergence,
                adx > ADX_STRENGTH,
                strongBounce,
                hhHlPass,
                touchedOrBelowSma44,
                resistancePct != null && resistancePct > RESISTANCE_BONUS_PCT);

        double confidence = CONFIDENCE_MAP.getOrDefault(score, 1.0);
        String label = signalLabel(score);
        String reason = String.format("SMA44 pullback bounce: score=%d/13, rsi=%.1f, adx=%.1f",
                score, rsi, adx);

        // === 8. Log BUY ===
        log.info("SMA44_PULLBACK BUY | stock={} | trend=PASS | hh/hl={} | pullback=PASS | bounce=PASS | volume={} | rsi={} | adx={} | resistance={}% | confidence={}/13 | signal={}",
                stockId,
                (hhHlPass ? "PASS" : "SKIP"),
                (volSpike ? "PASS" : "FAIL"),
                String.format("%.1f", rsi),
                String.format("%.1f", adx),
                (resistancePct != null ? String.format("%.1f", resistancePct) : "N/A"),
                score, label);

        return StrategyResult.withoutContribution(StrategySignal.BUY, confidence, reason, getName(), getPriority());
    }

    // === Trend Filter: Close > SMA44 AND SMA44(today) > SMA44(5 days ago) ===
    private boolean isSMA44TrendingUp(BigDecimal close, double sma44Today, double sma44_5d) {
        return close.compareTo(BigDecimal.valueOf(sma44Today)) > 0 && sma44Today > sma44_5d;
    }

    // === HH+HL Detection (returns [hasHigherHigh, hasHigherLow]) ===
    private boolean[] detectHigherHighHigherLow(List<DailyPrice> allPrices, int lookback, int pivotBars) {
        int size = allPrices.size();
        int start = Math.max(0, size - lookback);
        List<DailyPrice> window = allPrices.subList(start, size);

        List<DailyPrice> swingHighs = findSwings(window, true, pivotBars);
        List<DailyPrice> swingLows = findSwings(window, false, pivotBars);

        boolean hasHH = swingHighs.size() >= 2
                && swingHighs.get(swingHighs.size() - 1).getHighPrice()
                    .compareTo(swingHighs.get(swingHighs.size() - 2).getHighPrice()) > 0;
        boolean hasHL = swingLows.size() >= 2
                && swingLows.get(swingLows.size() - 1).getLowPrice()
                    .compareTo(swingLows.get(swingLows.size() - 2).getLowPrice()) > 0;

        return new boolean[]{hasHH, hasHL};
    }

    private List<DailyPrice> findSwings(List<DailyPrice> prices, boolean isHigh, int pivotBars) {
        List<DailyPrice> swings = new ArrayList<>();
        for (int i = pivotBars; i < prices.size() - pivotBars; i++) {
            DailyPrice candidate = prices.get(i);
            boolean isSwing = true;
            for (int j = 1; j <= pivotBars; j++) {
                if (isHigh) {
                    if (prices.get(i - j).getHighPrice().compareTo(candidate.getHighPrice()) >= 0
                            || prices.get(i + j).getHighPrice().compareTo(candidate.getHighPrice()) >= 0) {
                        isSwing = false;
                        break;
                    }
                } else {
                    if (prices.get(i - j).getLowPrice().compareTo(candidate.getLowPrice()) <= 0
                            || prices.get(i + j).getLowPrice().compareTo(candidate.getLowPrice()) <= 0) {
                        isSwing = false;
                        break;
                    }
                }
            }
            if (isSwing) swings.add(candidate);
        }
        return swings;
    }

    // === Pullback Detection (returns [pulledBack, touchedOrBelowSma44]) ===
    private boolean[] detectPullback(BigDecimal low, double sma44) {
        double lowVal = low.doubleValue();
        boolean touchedOrBelow = lowVal <= sma44;
        boolean withinTolerance = Math.abs(lowVal - sma44) / sma44 <= PULLBACK_TOLERANCE;
        return new boolean[]{touchedOrBelow || withinTolerance, touchedOrBelow};
    }

    // === Bounce Detection (returns [confirmed, strongPattern]) ===
    private boolean[] detectBullishBounce(DailyPrice latest, List<DailyPrice> prices, double sma44) {
        BigDecimal open = latest.getOpeningPrice();
        BigDecimal close = latest.getClosingPrice();
        BigDecimal high = latest.getHighPrice();
        BigDecimal low = latest.getLowPrice();
        BigDecimal rng = high.subtract(low);

        if (rng.compareTo(BigDecimal.ZERO) <= 0) return new boolean[]{false, false};
        if (close.compareTo(open) <= 0) return new boolean[]{false, false};       // must be bullish
        if (close.compareTo(BigDecimal.valueOf(sma44)) <= 0) return new boolean[]{false, false}; // must close above SMA44

        BigDecimal body = close.subtract(open).abs();
        BigDecimal bodyRatio = body.divide(rng, 4, RoundingMode.HALF_UP);

        // Body must be > 50% of range
        if (bodyRatio.compareTo(BigDecimal.valueOf(0.5)) <= 0) return new boolean[]{false, false};

        // Lower wick >= 2x body
        BigDecimal lowerWick = close.min(open).subtract(low);
        boolean longLowerWick = lowerWick.compareTo(body.multiply(BigDecimal.valueOf(2))) >= 0;
        // Strong bullish marubozu: body >= 90% of range
        boolean strongMarubozu = bodyRatio.compareTo(BigDecimal.valueOf(0.90)) >= 0;

        // Use CandlestickPatternCalculator for multi-candle patterns
        CandlestickPatternCalculator.Result cr = new CandlestickPatternCalculator().detect(prices);
        boolean hammer = cr.pattern() == CandlestickPatternCalculator.Pattern.HAMMER;
        boolean engulfing = cr.pattern() == CandlestickPatternCalculator.Pattern.BULLISH_ENGULFING;

        boolean recognized = longLowerWick || hammer || engulfing || strongMarubozu;
        if (!recognized) return new boolean[]{false, false};

        // Strong pattern = recognized reversal pattern (not just a long wick)
        boolean strong = hammer || engulfing || strongMarubozu;

        return new boolean[]{true, strong};
    }

    // === Resistance Filter: returns null if no resistance above close, or percentage distance ===
    private Double isNearResistance(BigDecimal close, SupportResistanceDto levels) {
        if (levels == null) return null;

        List<BigDecimal> resistancePrices = new ArrayList<>();

        if (levels.getPivots() != null) {
            if (levels.getPivots().getR1() != null) resistancePrices.add(levels.getPivots().getR1());
            if (levels.getPivots().getR2() != null) resistancePrices.add(levels.getPivots().getR2());
            if (levels.getPivots().getR3() != null) resistancePrices.add(levels.getPivots().getR3());
        }
        if (levels.getMajorLevels() != null) {
            for (SupportResistanceDto.MajorLevel level : levels.getMajorLevels()) {
                if ("resistance".equals(level.getType()) && level.getPrice() != null) {
                    resistancePrices.add(level.getPrice());
                }
            }
        }
        if (levels.getSwingHighs() != null) {
            for (SupportResistanceDto.SwingLevel level : levels.getSwingHighs()) {
                if (level.getPrice() != null) {
                    resistancePrices.add(level.getPrice());
                }
            }
        }

        BigDecimal nearest = null;
        for (BigDecimal res : resistancePrices) {
            if (res.compareTo(close) > 0) {
                if (nearest == null || res.compareTo(nearest) < 0) {
                    nearest = res;
                }
            }
        }
        if (nearest == null) return null;

        double pct = nearest.subtract(close).divide(close, 6, RoundingMode.HALF_UP).doubleValue() * 100;
        return pct;
    }

    // === RSI Resolution ===
    private double resolveRsi(List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        return resolveIndicator(indicators, IndicatorType.RSI)
                .orElseGet(() -> {
                    try {
                        BigDecimal rsi = TechnicalAnalysisUtils.calculateRsi14(prices);
                        return rsi != null ? rsi.doubleValue() : 50.0;
                    } catch (Exception e) {
                        return 50.0;
                    }
                });
    }

    // === ADX Resolution ===
    private double resolveAdx(List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        return resolveIndicator(indicators, IndicatorType.ADX)
                .orElseGet(() -> {
                    try {
                        return new AdxCalculator(14).calculate(prices).doubleValue();
                    } catch (Exception e) {
                        return 0.0;
                    }
                });
    }

    // === Rising Volume Detection ===
    private boolean detectRisingVolume(DailyPrice latest, List<DailyPrice> prices) {
        Long vol = latest.getVolume();
        if (vol == null || vol <= 0) return false;

        int size = prices.size();
        int start = Math.max(0, size - 1 - VOLUME_AVG_PERIOD);
        int end = size - 1;

        long sum = 0;
        int count = 0;
        for (int i = start; i < end; i++) {
            Long v = prices.get(i).getVolume();
            if (v != null && v > 0) {
                sum += v;
                count++;
            }
        }
        if (count == 0) return false;

        double avg = (double) sum / count;
        return vol > VOLUME_SPIKE_FACTOR * avg;
    }

    // === Bullish Divergence Detection ===
    private boolean detectBullishDivergence(List<DailyPrice> prices) {
        if (prices.size() < 20) return false;

        try {
            Map<LocalDate, BigDecimal> allRsi = new RsiCalculator(14).calculateAll(prices);
            int size = prices.size();
            List<DailyPrice> last20 = prices.subList(size - 20, size);
            List<BigDecimal> rsiSeries = new ArrayList<>();
            for (DailyPrice p : last20) {
                BigDecimal rsi = allRsi.get(p.getPriceDate());
                if (rsi != null) {
                    rsiSeries.add(rsi);
                }
            }
            if (rsiSeries.size() < 20) return false;
            return TechnicalAnalysisUtils.hasBullishDivergence(last20, rsiSeries);
        } catch (Exception e) {
            return false;
        }
    }

    // === Confidence Scoring ===
    private int calculateSMA44Confidence(
            boolean risingVolume, boolean rsiOversold, boolean bullishDivergence,
            boolean adxStrong, boolean strongCandlestick,
            boolean hhHl, boolean touchedOrBelowSma44, boolean resistanceFar) {
        int score = 5;
        if (risingVolume) score++;
        if (rsiOversold) score++;
        if (bullishDivergence) score++;
        if (adxStrong) score++;
        if (strongCandlestick) score++;
        if (hhHl) score++;
        if (touchedOrBelowSma44) score++;
        if (resistanceFar) score++;
        return Math.min(score, 13);
    }

    // === Signal Label ===
    private String signalLabel(int score) {
        if (score <= 6) return "BUY";
        if (score <= 8) return "STRONG BUY";
        if (score <= 10) return "VERY STRONG BUY";
        return "INSTITUTIONAL BUY";
    }
}
