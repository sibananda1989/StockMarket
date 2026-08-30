package org.example.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.TechnicalIndicator;
import org.example.service.calculator.EmaSeriesCalculator;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * EMA-20 / EMA-50 crossover strategy (port of the HeRAI "20 EMA ↑ 50 EMA" screen).
 *
 * <p>Event-based: detects a FRESH crossover — EMA-20 crossing above (BUY, golden
 * cross) or below (SELL, death cross) EMA-50 — within the last
 * {@value #FRESH_WINDOW_DAYS} trading days. Confidence decays with the age of the
 * cross so a 5-day-old signal weighs far less than today's.</p>
 *
 * <p>Self-contained: computes both EMA series on-the-fly from the full ascending
 * price history the engine already hands over (like {@link BreakoutStrategy}
 * operates on raw prices) — it does not read the indicator table and does not
 * depend on the standalone {@code EmaCrossScreenerService}.</p>
 *
 * <p>Crosses whose day carries a single-day close jump above
 * {@value #SPLIT_JUMP_THRESHOLD} (100%) are ignored — unadjusted stock splits
 * create spurious crosses.</p>
 */
@Slf4j
public class EmaCrossoverStrategy extends TradingStrategy {

    private static final int EMA_20_PERIOD = 20;
    private static final int EMA_50_PERIOD = 50;
    private static final int FRESH_WINDOW_DAYS = 5;
    private static final double SPLIT_JUMP_THRESHOLD = 0.30;

    private static final double FRESH_CONFIDENCE = 0.85;
    private static final double CONFIDENCE_DECAY_PER_DAY = 0.075;
    private static final double MIN_CONFIDENCE = 0.55;
    private static final double NO_CROSS_CONFIDENCE = 0.30;

    public EmaCrossoverStrategy(int priority) {
        super("EMA_CROSSOVER", priority);
    }

    @Override
    public StrategyResult evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices) {
        if (prices == null || prices.size() < EMA_50_PERIOD + 1) {
            return insufficientData();
        }

        List<BigDecimal> ema20;
        List<BigDecimal> ema50;
        try {
            ema20 = new EmaSeriesCalculator(EMA_20_PERIOD).calculate(prices);
            ema50 = new EmaSeriesCalculator(EMA_50_PERIOD).calculate(prices);
        } catch (Exception e) {
            log.debug("EMA_CROSSOVER: could not compute EMA series for stock {}: {}", stockId, e.getMessage());
            return insufficientData();
        }

        int lastIdx = prices.size() - 1;

        // Scan the freshest bars first; the first non-artifact cross found is the
        // latest valid one (HeRAI shows one row per stock).
        for (int i = lastIdx; i >= lastIdx - FRESH_WINDOW_DAYS + 1 && i >= EMA_50_PERIOD; i--) {
            int prevIdx = i - 1;
            BigDecimal prev20 = ema20.get(prevIdx);
            BigDecimal prev50 = ema50.get(prevIdx);
            BigDecimal cur20 = ema20.get(i);
            BigDecimal cur50 = ema50.get(i);

            boolean upCross = prev20.compareTo(prev50) <= 0 && cur20.compareTo(cur50) > 0;
            boolean downCross = prev20.compareTo(prev50) >= 0 && cur20.compareTo(cur50) < 0;
            if (!upCross && !downCross) {
                continue;
            }

            BigDecimal prevClose = prices.get(prevIdx).getClosingPrice();
            BigDecimal curClose = prices.get(i).getClosingPrice();
            if (isSplitJump(prevClose, curClose)) {
                continue; // artifact — keep looking at older bars in the window
            }

            int daysAgo = lastIdx - i;
            double confidence = Math.max(MIN_CONFIDENCE, FRESH_CONFIDENCE - CONFIDENCE_DECAY_PER_DAY * daysAgo);
            LocalDate crossDate = prices.get(i).getPriceDate();
            if (upCross) {
                return StrategyResult.withoutContributionWithEventDate(StrategySignal.BUY, confidence,
                        String.format("EMA-20 crossed above EMA-50 (%.2f > %.2f) %d day(s) ago",
                                cur20, cur50, daysAgo), getName(), getPriority(), crossDate);
            }
            return StrategyResult.withoutContributionWithEventDate(StrategySignal.SELL, confidence,
                    String.format("EMA-20 crossed below EMA-50 (%.2f < %.2f) %d day(s) ago",
                            cur20, cur50, daysAgo), getName(), getPriority(), crossDate);
        }

        return StrategyResult.withoutContribution(StrategySignal.HOLD, NO_CROSS_CONFIDENCE,
                "No fresh EMA-20/50 cross in " + FRESH_WINDOW_DAYS + " days", getName(), getPriority());
    }

    private boolean isSplitJump(BigDecimal prevClose, BigDecimal curClose) {
        if (prevClose == null || curClose == null || prevClose.signum() == 0) {
            return false;
        }
        BigDecimal pct = curClose.subtract(prevClose).abs()
                .divide(prevClose.abs(), 4, RoundingMode.HALF_UP);
        return pct.doubleValue() > SPLIT_JUMP_THRESHOLD;
    }
}