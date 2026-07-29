package org.example.service;

/**
 * Single source of truth for all magic numbers used in the signal scoring logic.
 *
 * <p>Extracted from {@link SignalService} so that thresholds, multipliers, and
 * breakpoint boundaries are named, documented, and centrally tunable without
 * changing scoring behaviour.</p>
 */
public final class SignalThresholds {

    private SignalThresholds() {
        // Utility class — do not instantiate.
    }

    // ── Recommendation thresholds (mapRecommendation / mapEngineRecommendation) ──
    public static final int STRONG_BUY_THRESHOLD = 7;
    public static final int BUY_THRESHOLD = 3;
    public static final int SELL_THRESHOLD = -4;
    public static final int STRONG_SELL_THRESHOLD = -7;

    // ── Bearish trend graduated discount (SMA20 < SMA50) ──
    public static final double BEARISH_TREND_DISCOUNT = 0.85;
    public static final double BEARISH_TREND_DISCOUNT_REVERSAL_FULL = 1.0;
    public static final double BEARISH_TREND_DISCOUNT_REVERSAL_MINIMAL = 0.9;
    public static final double BEARISH_TREND_DISCOUNT_REVERSAL_FLOOR = 0.9;
    public static final int REVERSAL_SCORE_THRESHOLD = 2;
    public static final double RSI_REVERSAL_RSI_MIN = 50.0;
    public static final double WEEKLY_RSI_REVERSAL_MIN = 45.0;

    // ── Rolling accuracy penalty (signal suppressed on poor history) ──
    public static final int ACCURACY_TOTAL_MIN_FOR_PENALTY = 5;
    public static final double ACCURACY_PENALTY_LOW = 35.0;
    public static final int ACCURACY_PENALTY_LOW_SCORE = 3;
    public static final double ACCURACY_PENALTY_HIGH = 45.0;
    public static final int ACCURACY_PENALTY_HIGH_SCORE = 1;

    // ── 3-Dimension High-Confidence BUY Gate ──
    public static final double GATE_RSI_MOMENTUM_MAX = 75.0;
    public static final int GATE_CANDLESTICK_SCORE_THRESHOLD = 2;
    public static final double GATE_PRICE_RATIO_CANDLESTICK_RELAXED = 1.05;
    public static final double GATE_PRICE_RATIO_DEFAULT = 1.0;

    // ── Channel trading override (52-week range) ──
    public static final double CHANNEL_WEEKLY_RSI_TREND_MIN = 40.0;
    public static final double CHANNEL_BOTTOM_PCT = 15.0;
    public static final double CHANNEL_TOP_PCT = 85.0;
    public static final int CHANNEL_OVERRIDE_BUY_FLOOR_SCORE = -2;
    public static final int CHANNEL_OVERRIDE_SELL_CEIL_SCORE = 2;

    // ── MACD crossover thresholds ──
    public static final double MACD_ABOVE_ZERO_THRESHOLD = 1.0;

    // ── Bearish trend dampener (cap bullish trend contributions when SMA20 < SMA50) ──
    public static final double BEARISH_DAMPENER_MULTIPLIER = 0.5;
    public static final int BEARISH_DAMPENER_SUBTRACT = 2;

    // ── Factor group caps (prevent single category dominance) ──
    public static final int MOMENTUM_SCORE_CAP = 5;
    public static final int STRUCTURE_SCORE_CAP = 3;

    // ── Continuous ADX-based multiplier breakpoints ──
    public static final double ADX_NO_TREND_MULTIPLIER = 0.3;       // ADX < 15
    public static final double ADX_WEAK_BASE_MULTIPLIER = 0.5;      // ADX 15-25 base
    public static final double ADX_WEAK_SLOPE = 0.02;               // per ADX unit over 15
    public static final double ADX_MOD_BASE_MULTIPLIER = 0.7;       // ADX 25-35 base
    public static final double ADX_MOD_SLOPE = 0.01;               // per ADX unit over 25
    public static final double ADX_STRONG_MULTIPLIER = 1.0;         // ADX >= 35
    public static final double ADX_WEAK_THRESHOLD = 15.0;
    public static final double ADX_MODERATE_THRESHOLD = 25.0;
    public static final double ADX_STRONG_THRESHOLD = 35.0;
    public static final double ADX_COUNTER_TREND_MULTIPLIER = 0.7; // signal opposes ADX direction

    // ── Compound overbought dampener ──
    public static final double OVERBOUGHT_DAMPENER_MULTIPLIER = 0.7;
    public static final int OVERBOUGHT_THRESHOLD_BULLISH_ADX = 4;
    public static final int OVERBOUGHT_THRESHOLD_DEFAULT = 3;
    public static final double OVERBOUGHT_STOCH_RSI = 85.0;
    public static final double OVERBOUGHT_CCI = 150.0;
    public static final double OVERBOUGHT_STOCH_K = 80.0;
    public static final double OVERBOUGHT_WILLIAMS_R = -20.0;

    // ── Minimum data thresholds ──
    public static final int MIN_HISTORY_FOR_SIGNALS = 20;

}
