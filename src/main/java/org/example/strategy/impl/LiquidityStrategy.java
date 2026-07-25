package org.example.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.FundamentalData;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.repository.FundamentalDataRepository;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Liquidity Identification Strategy.
 *
 * Evaluates stock liquidity using three complementary measures:
 * <ol>
 *   <li><b>Amihud Illiquidity Ratio</b> — price impact per rupee of trading.
 *       Lower values = more liquid. Pre-computed and stored in the DB.</li>
 *   <li><b>Dollar Volume</b> — average rupee value traded per day.
 *       Higher values = more liquid. Pre-computed and stored in the DB.</li>
 *   <li><b>Turnover Ratio</b> — trading volume relative to shares outstanding.
 *       Higher values = more active. Computed on-the-fly from FundamentalData.</li>
 * </ol>
 *
 * <p>These three factors are combined into a composite liquidity score (0-100).
 * The strategy emits:</p>
 * <ul>
 *   <li><b>BUY</b> (score ≥ 60) — stock is liquid, easy to trade, confident position sizing</li>
 *   <li><b>HOLD</b> (score 40-59) — moderate liquidity, neutral</li>
 *   <li><b>SELL</b> (score &lt; 40) — illiquid, risk warning, use limit orders</li>
 * </ul>
 *
 * <p>The SELL signal here is a <i>risk advisory</i>, not a directional trade signal.
 * It means the stock is difficult to trade without significant price impact.</p>
 *
 * <p>Priority: 3 (lowest — liquidity is an auxiliary filter, not a primary signal).
 * Configured via {@code strategy.liquidity.priority}.</p>
 */
@Slf4j
public class LiquidityStrategy extends TradingStrategy {

    private static final int AMIHUD_LOOKBACK = 20;
    private static final int DOLLAR_VOL_LOOKBACK = 20;

    // Scoring thresholds (calibrated for Indian NSE stocks)
    private static final double AMIHUD_HIGH_LIQUIDITY_THRESHOLD = 1e-10;  // ≤ this = very liquid
    private static final double AMIHUD_LOW_LIQUIDITY_THRESHOLD = 1e-8;    // ≥ this = very illiquid
    private static final double DOLLAR_VOL_HIGH = 1_000_000_000.0;        // ₹100 Cr+ daily = high liquidity
    private static final double DOLLAR_VOL_LOW = 50_000_000.0;            // ₹5 Cr daily = low liquidity
    private static final double TURNOVER_HIGH = 0.01;                     // 1%+ daily turnover
    private static final double TURNOVER_LOW = 0.001;                     // 0.1% daily turnover

    // Composite thresholds
    private static final double BUY_THRESHOLD = 60.0;
    private static final double SELL_THRESHOLD = 40.0;

    // Reason strings
    private static final String INSUFFICIENT_DATA_REASON = "Insufficient data for liquidity analysis";

    private final FundamentalDataRepository fundamentalDataRepository;

    public LiquidityStrategy(int priority, FundamentalDataRepository fundamentalDataRepository) {
        super("LIQUIDITY", priority);
        this.fundamentalDataRepository = fundamentalDataRepository;
    }

    @Override
    public StrategyResult evaluate(Long stockId,
                                    List<TechnicalIndicator> indicators,
                                    List<DailyPrice> prices) {
        if (indicators == null || prices == null || prices.size() < 2) {
            return insufficientData();
        }

        // ── Factor 1: Amihud Illiquidity Ratio (pre-computed in DB) ───────────
        Optional<Double> amihudOpt = resolveIndicator(indicators, IndicatorType.AMIHUD_ILLIQUIDITY);
        if (amihudOpt.isEmpty()) {
            log.debug("AMIHUD indicator not found for stock {}, computing inline", stockId);
        }

        // Compute Amihud inline if DB doesn't have it yet (new indicator, backfill may be pending)
        double amihudRaw = amihudOpt.orElseGet(() -> computeAmihudInline(prices));

        // Amihud score: lower raw = more liquid = higher score (invert)
        // Raw ranges ~10^-12 to 10^-6. We use log10 for a human-readable scale.
        double amihudScore;
        if (amihudRaw <= 0) {
            amihudScore = 5.0; // neutral if we can't compute
        } else {
            double logAmihud = Math.log10(amihudRaw);
            // Map log10(-12) to 10, log10(-6) to 0, clamp to [0, 10]
            amihudScore = Math.max(0, Math.min(10, (-(logAmihud + 12.0) / 6.0) * 10.0));
        }

        // ── Factor 2: Dollar Volume (pre-computed in DB) ─────────────────────
        Optional<Double> dollarVolOpt = resolveIndicator(indicators, IndicatorType.DOLLAR_VOLUME);
        double avgDollarVolume = dollarVolOpt.orElseGet(() -> computeDollarVolumeInline(prices));

        // Dollar volume score: scale ₹5 Cr → 0, ₹100 Cr → 10
        double dollarVolScore;
        if (avgDollarVolume >= DOLLAR_VOL_HIGH) {
            dollarVolScore = 10.0;
        } else if (avgDollarVolume <= DOLLAR_VOL_LOW) {
            dollarVolScore = 0.0;
        } else {
            dollarVolScore = ((avgDollarVolume - DOLLAR_VOL_LOW)
                    / (DOLLAR_VOL_HIGH - DOLLAR_VOL_LOW)) * 10.0;
        }

        // ── Factor 3: Turnover Ratio (computed on-the-fly from FundamentalData) ─
        double turnoverScore = computeTurnoverScore(stockId, prices);

        // ── Factor 4: Volume Stability (coefficient of variation) ─────────────
        double stabilityScore = computeStabilityScore(prices);

        // ── Composite Score (0-100) ───────────────────────────────────────────
        double composite = amihudScore * 3.5      // 35% weight
                         + dollarVolScore * 2.5   // 25% weight
                         + turnoverScore * 2.5    // 25% weight
                         + stabilityScore * 1.5;  // 15% weight

        // Determine signal
        StrategySignal signal;
        double confidence;
        String reason;

        if (composite >= BUY_THRESHOLD) {
            signal = StrategySignal.BUY;
            confidence = Math.min(1.0, 0.5 + (composite - 60.0) / 80.0);
            reason = String.format(
                    "High liquidity (score=%.0f) — Amihud:%.1f/10, DollarVol:%.1f/10, Turnover:%.1f/10, Stable:%.1f/10",
                    composite, amihudScore, dollarVolScore, turnoverScore, stabilityScore);
        } else if (composite <= SELL_THRESHOLD) {
            signal = StrategySignal.SELL; // Risk advisory: illiquid
            confidence = Math.min(1.0, 0.4 + (40.0 - composite) / 80.0);
            reason = String.format(
                    "Low liquidity (score=%.0f) — use limit orders. Amihud:%.1f/10, DollarVol:%.1f/10, Turnover:%.1f/10, Stable:%.1f/10",
                    composite, amihudScore, dollarVolScore, turnoverScore, stabilityScore);
        } else {
            signal = StrategySignal.HOLD;
            confidence = 0.30;
            reason = String.format(
                    "Moderate liquidity (score=%.0f) — Amihud:%.1f/10, DollarVol:%.1f/10, Turnover:%.1f/10, Stable:%.1f/10",
                    composite, amihudScore, dollarVolScore, turnoverScore, stabilityScore);
        }

        log.debug("Liquidity strategy for stock {}: composite={}, signal={}, amihudRaw={}",
                stockId, composite, signal, amihudRaw);

        return StrategyResult.withoutContribution(signal, confidence, reason, getName(), getPriority());
    }

    /**
     * Computes the turnover ratio score from shares outstanding and recent volume.
     * Score = min(10, avgDailyTurnoverPct * 1000) — so 1% daily turnover → score 10.
     */
    private double computeTurnoverScore(Long stockId, List<DailyPrice> prices) {
        Optional<FundamentalData> fundData = fundamentalDataRepository.findByStockId(stockId);
        if (fundData.isEmpty() || fundData.get().getSharesOutstanding() == null
                || fundData.get().getSharesOutstanding() <= 0) {
            return 5.0; // neutral if no fundamental data
        }

        long sharesOutstanding = fundData.get().getSharesOutstanding();

        // Compute average daily volume over the lookback period
        int lookback = Math.min(DOLLAR_VOL_LOOKBACK, prices.size());
        long totalVolume = 0;
        int validDays = 0;

        for (int i = 0; i < lookback; i++) {
            DailyPrice p = prices.get(i);
            if (p.getVolume() != null && p.getVolume() > 0) {
                totalVolume += p.getVolume();
                validDays++;
            }
        }

        if (validDays == 0) {
            return 5.0;
        }

        double avgDailyVolume = (double) totalVolume / validDays;
        double avgDailyTurnover = avgDailyVolume / sharesOutstanding;

        // Score: 0-10, cap at 10 for 1%+ daily turnover
        double score = Math.min(10.0, avgDailyTurnover * 1000.0);
        return Math.max(0, score);
    }

    /**
     * Computes volume stability score from the coefficient of variation.
     * Low CV = stable, predictable volume = higher score.
     * Score = max(0, 10 - CV * 5). A CV of 0 gives 10, CV of 2 gives 0.
     */
    private double computeStabilityScore(List<DailyPrice> prices) {
        int lookback = Math.min(DOLLAR_VOL_LOOKBACK, prices.size());
        if (lookback < 5) {
            return 5.0; // neutral
        }

        double sum = 0;
        double sumSq = 0;
        int count = 0;

        for (int i = 0; i < lookback; i++) {
            Long vol = prices.get(i).getVolume();
            if (vol != null && vol > 0) {
                double v = vol.doubleValue();
                sum += v;
                sumSq += v * v;
                count++;
            }
        }

        if (count < 5) {
            return 5.0;
        }

        double mean = sum / count;
        if (mean <= 0) {
            return 5.0;
        }

        double variance = (sumSq / count) - (mean * mean);
        double stdDev = Math.sqrt(Math.max(0, variance));
        double cv = stdDev / mean;

        return Math.max(0, Math.min(10, 10.0 - cv * 5.0));
    }

    /**
     * Fallback inline computation of Amihud ratio when DB indicator is not yet available.
     */
    private double computeAmihudInline(List<DailyPrice> prices) {
        if (prices.size() <= AMIHUD_LOOKBACK) return -1;

        double sumRatio = 0.0;
        int validDays = 0;

        int start = prices.size() - AMIHUD_LOOKBACK;
        for (int i = Math.max(1, start); i < prices.size(); i++) {
            DailyPrice prev = prices.get(i - 1);
            DailyPrice curr = prices.get(i);

            BigDecimal prevClose = prev.getClosingPrice();
            BigDecimal currClose = curr.getClosingPrice();
            Long volume = curr.getVolume();

            if (prevClose == null || currClose == null || volume == null
                    || volume <= 0 || prevClose.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal dailyReturn = currClose.subtract(prevClose).abs()
                    .divide(prevClose, 10, RoundingMode.HALF_UP);
            BigDecimal dollarVolume = currClose.multiply(BigDecimal.valueOf(volume));

            sumRatio += dailyReturn.divide(dollarVolume, 15, RoundingMode.HALF_UP).doubleValue();
            validDays++;
        }

        return validDays > 0 ? sumRatio / validDays : -1;
    }

    /**
     * Fallback inline computation of dollar volume when DB indicator is not yet available.
     */
    private double computeDollarVolumeInline(List<DailyPrice> prices) {
        int lookback = Math.min(DOLLAR_VOL_LOOKBACK, prices.size());
        double sum = 0;
        int valid = 0;

        for (int i = 0; i < lookback; i++) {
            DailyPrice p = prices.get(i);
            if (p.getClosingPrice() != null && p.getVolume() != null && p.getVolume() > 0) {
                sum += p.getClosingPrice().multiply(BigDecimal.valueOf(p.getVolume())).doubleValue();
                valid++;
            }
        }

        return valid > 0 ? sum / valid : 0;
    }

    @Override
    protected StrategyResult insufficientData() {
        return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.0,
                INSUFFICIENT_DATA_REASON, getName(), getPriority());
    }
}
