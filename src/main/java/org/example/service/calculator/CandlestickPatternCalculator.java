package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class CandlestickPatternCalculator {

    private static final BigDecimal DOJI_THRESHOLD = new BigDecimal("0.005");

    public enum Pattern {
        NONE,
        BULLISH_ENGULFING, BEARISH_ENGULFING,
        HAMMER, SHOOTING_STAR,
        PIERCING_LINE, DARK_CLOUD_COVER,
        MORNING_STAR, EVENING_STAR,
        BULLISH_HARAMI, BEARISH_HARAMI,
        // Triple-candle reversal (gap-based)
        BULLISH_ABANDONED_BABY, BEARISH_ABANDONED_BABY,
        // Triple-candle continuation & warning
        THREE_WHITE_SOLDIERS, THREE_BLACK_CROWS,
        ADVANCE_BLOCK, DELIBERATION,
        THREE_INSIDE_UP, THREE_INSIDE_DOWN,
        THREE_OUTSIDE_UP, THREE_OUTSIDE_DOWN,
        // Double-candle patterns
        TWEEZER_TOP, TWEEZER_BOTTOM,
        BULLISH_KICKER, BEARISH_KICKER,
        BULLISH_MEETING_LINES, BEARISH_MEETING_LINES,
        // Single-candle momentum
        BULLISH_MARUBOZU, BEARISH_MARUBOZU,
        BULLISH_BELT_HOLD, BEARISH_BELT_HOLD,
        // Single-candle indecision
        SPINNING_TOP,
        // Doji variants
        DOJI, DRAGONFLY_DOJI, GRAVESTONE_DOJI, LONG_LEGGED_DOJI
    }

    public record Result(Pattern pattern, int score, String label) {}

    public Result detect(List<DailyPrice> prices) {
        if (prices == null || prices.size() < 3) {
            return new Result(Pattern.NONE, 0, null);
        }

        DailyPrice c1 = prices.get(prices.size() - 3);
        DailyPrice c2 = prices.get(prices.size() - 2);
        DailyPrice c3 = prices.get(prices.size() - 1);

        boolean c3Bullish = isBullish(c3);
        boolean c2Bullish = isBullish(c2);
        boolean c1Bullish = isBullish(c1);

        boolean c3Doji = isDoji(c3);
        boolean c2Doji = isDoji(c2);

        BigDecimal c3Body = bodyLength(c3);
        BigDecimal c2Body = bodyLength(c2);
        BigDecimal c1Body = bodyLength(c1);

        BigDecimal c3UpperWick = upperWick(c3);
        BigDecimal c3LowerWick = lowerWick(c3);

        // ── 3-Candle Patterns (priority) ──

        // Bullish Abandoned Baby: bearish → doji (gapped down) → bullish (gapped up), confirms above midpoint
        if (c1Body.compareTo(BigDecimal.ZERO) > 0 && !c1Bullish && !c3Doji
                && c2Doji && c3Bullish
                && c2.getHighPrice().compareTo(c1.getLowPrice()) < 0
                && c3.getLowPrice().compareTo(c2.getHighPrice()) > 0
                && c3.getClosingPrice().compareTo(midpoint(c1)) > 0) {
            return new Result(Pattern.BULLISH_ABANDONED_BABY, 3, "Bullish Abandoned Baby");
        }

        // Bearish Abandoned Baby: bullish → doji (gapped up) → bearish (gapped down), confirms below midpoint
        if (c1Body.compareTo(BigDecimal.ZERO) > 0 && c1Bullish && !c3Doji
                && c2Doji && !c3Bullish
                && c2.getLowPrice().compareTo(c1.getHighPrice()) > 0
                && c3.getHighPrice().compareTo(c2.getLowPrice()) < 0
                && c3.getClosingPrice().compareTo(midpoint(c1)) < 0) {
            return new Result(Pattern.BEARISH_ABANDONED_BABY, -3, "Bearish Abandoned Baby");
        }

        // Morning Star: long red, small doji, long green closing above midpoint of first
        if (c1Body.compareTo(BigDecimal.ZERO) > 0 && !c1Bullish && !c3Doji
                && c2Doji && c3Bullish
                && c3.getClosingPrice().compareTo(midpoint(c1)) > 0) {
            return new Result(Pattern.MORNING_STAR, 3, "Morning Star");
        }

        // Evening Star: long green, small doji, long red closing below midpoint of first
        if (c1Body.compareTo(BigDecimal.ZERO) > 0 && c1Bullish && !c3Doji
                && c2Doji && !c3Bullish
                && c3.getClosingPrice().compareTo(midpoint(c1)) < 0) {
            return new Result(Pattern.EVENING_STAR, -3, "Evening Star");
        }

        // ── Triple-Candle Patterns ──

        // Advance Block: three bullish candles where each body shrinks — trend weakening
        if (c1Bullish && c2Bullish && c3Bullish
                && c2.getClosingPrice().compareTo(c1.getClosingPrice()) > 0
                && c3.getClosingPrice().compareTo(c2.getClosingPrice()) > 0
                && c2Body.compareTo(c1Body) < 0
                && c3Body.compareTo(c2Body) < 0) {
            return new Result(Pattern.ADVANCE_BLOCK, -1, "Advance Block");
        }

        // Deliberation: three bullish, third body smaller — exhaustion ahead
        if (c1Bullish && c2Bullish && c3Bullish
                && c2.getClosingPrice().compareTo(c1.getClosingPrice()) > 0
                && c3.getClosingPrice().compareTo(c2.getClosingPrice()) > 0
                && c3Body.compareTo(c2Body) < 0
                && c3Body.compareTo(c1Body) < 0) {
            return new Result(Pattern.DELIBERATION, -1, "Deliberation");
        }

        // Three White Soldiers: three consecutive bullish candles with rising closes
        if (c1Bullish && c2Bullish && c3Bullish
                && c2.getClosingPrice().compareTo(c1.getClosingPrice()) > 0
                && c3.getClosingPrice().compareTo(c2.getClosingPrice()) > 0) {
            return new Result(Pattern.THREE_WHITE_SOLDIERS, 3, "Three White Soldiers");
        }

        // Three Black Crows: three consecutive bearish candles with falling closes
        if (!c1Bullish && !c2Bullish && !c3Bullish
                && c2.getClosingPrice().compareTo(c1.getClosingPrice()) < 0
                && c3.getClosingPrice().compareTo(c2.getClosingPrice()) < 0) {
            return new Result(Pattern.THREE_BLACK_CROWS, -3, "Three Black Crows");
        }

        // Three Inside Up: bearish → harami (bullish inside) → bullish closes above first open
        if (!c1Bullish && c2Bullish && c3Bullish
                && c1Body.compareTo(c2Body) > 0
                && c2.getHighPrice().compareTo(c1.getOpeningPrice()) < 0
                && c2.getLowPrice().compareTo(c1.getClosingPrice()) > 0
                && c3.getClosingPrice().compareTo(c1.getOpeningPrice()) > 0) {
            return new Result(Pattern.THREE_INSIDE_UP, 2, "Three Inside Up");
        }

        // Three Inside Down: bullish → harami (bearish inside) → bearish closes below first open
        if (c1Bullish && !c2Bullish && !c3Bullish
                && c1Body.compareTo(c2Body) > 0
                && c2.getHighPrice().compareTo(c1.getClosingPrice()) < 0
                && c2.getLowPrice().compareTo(c1.getOpeningPrice()) > 0
                && c3.getClosingPrice().compareTo(c1.getOpeningPrice()) < 0) {
            return new Result(Pattern.THREE_INSIDE_DOWN, -2, "Three Inside Down");
        }

        // Three Outside Up: bearish → bullish engulfing → higher close confirmation
        if (!c1Bullish && c2Bullish && c3Bullish
                && c2.getOpeningPrice().compareTo(c1.getClosingPrice()) <= 0
                && c2.getClosingPrice().compareTo(c1.getOpeningPrice()) > 0
                && c3.getClosingPrice().compareTo(c2.getClosingPrice()) > 0) {
            return new Result(Pattern.THREE_OUTSIDE_UP, 3, "Three Outside Up");
        }

        // Three Outside Down: bullish → bearish engulfing → lower close confirmation
        if (c1Bullish && !c2Bullish && !c3Bullish
                && c2.getOpeningPrice().compareTo(c1.getClosingPrice()) >= 0
                && c2.getClosingPrice().compareTo(c1.getOpeningPrice()) < 0
                && c3.getClosingPrice().compareTo(c2.getClosingPrice()) < 0) {
            return new Result(Pattern.THREE_OUTSIDE_DOWN, -3, "Three Outside Down");
        }

        // ── 2-Candle Patterns ──

        // Bullish Engulfing: red candle fully engulfed by green
        if (!c2Bullish && c3Bullish
                && c3.getOpeningPrice().compareTo(c2.getClosingPrice()) <= 0
                && c3.getClosingPrice().compareTo(c2.getOpeningPrice()) > 0) {
            return new Result(Pattern.BULLISH_ENGULFING, 2, "Bullish Engulfing");
        }

        // Bearish Engulfing: green candle fully engulfed by red
        if (c2Bullish && !c3Bullish
                && c3.getOpeningPrice().compareTo(c2.getClosingPrice()) >= 0
                && c3.getClosingPrice().compareTo(c2.getOpeningPrice()) < 0) {
            return new Result(Pattern.BEARISH_ENGULFING, -2, "Bearish Engulfing");
        }

        // Piercing Line: red candle, then green opens below previous low, closes above midpoint
        if (!c2Bullish && c3Bullish
                && c3.getOpeningPrice().compareTo(c2.getLowPrice()) < 0
                && c3.getClosingPrice().compareTo(midpoint(c2)) > 0) {
            return new Result(Pattern.PIERCING_LINE, 2, "Piercing Line");
        }

        // Dark Cloud Cover: green candle, then red opens above previous high, closes below midpoint
        if (c2Bullish && !c3Bullish
                && c3.getOpeningPrice().compareTo(c2.getHighPrice()) > 0
                && c3.getClosingPrice().compareTo(midpoint(c2)) < 0) {
            return new Result(Pattern.DARK_CLOUD_COVER, -2, "Dark Cloud Cover");
        }

        // Bullish Harami: large red followed by small green inside its body
        if (!c2Bullish && c3Bullish
                && c2Body.compareTo(c3Body) > 0
                && c3.getHighPrice().compareTo(c2.getOpeningPrice()) < 0
                && c3.getLowPrice().compareTo(c2.getClosingPrice()) > 0) {
            return new Result(Pattern.BULLISH_HARAMI, 1, "Bullish Harami");
        }

        // Bearish Harami: large green followed by small red inside its body
        if (c2Bullish && !c3Bullish
                && c2Body.compareTo(c3Body) > 0
                && c3.getHighPrice().compareTo(c2.getClosingPrice()) < 0
                && c3.getLowPrice().compareTo(c2.getOpeningPrice()) > 0) {
            return new Result(Pattern.BEARISH_HARAMI, -1, "Bearish Harami");
        }

        // ── 2-Candle Patterns (continued) ──

        // --- Kicker: gap-based reversal (very strong, score ±3) ---
        // Bullish Kicker: bearish candle → gap up → bullish candle, no overlap
        if (!c2Bullish && c3Bullish
                && c3.getOpeningPrice().compareTo(c2.getHighPrice()) > 0) {
            return new Result(Pattern.BULLISH_KICKER, 3, "Bullish Kicker");
        }

        // Bearish Kicker: bullish candle → gap down → bearish candle, no overlap
        if (c2Bullish && !c3Bullish
                && c3.getOpeningPrice().compareTo(c2.getLowPrice()) < 0) {
            return new Result(Pattern.BEARISH_KICKER, -3, "Bearish Kicker");
        }

        // --- Tweezer Top: same high, bullish then bearish (rejection at resistance) ---
        BigDecimal tweezerTolerance = new BigDecimal("0.001"); // 0.1% price tolerance
        if (c2Bullish && !c3Bullish
                && approxEqual(c2.getHighPrice(), c3.getHighPrice(), tweezerTolerance)) {
            return new Result(Pattern.TWEEZER_TOP, -2, "Tweezer Top");
        }

        // --- Tweezer Bottom: same low, bearish then bullish (bounce at support) ---
        if (!c2Bullish && c3Bullish
                && approxEqual(c2.getLowPrice(), c3.getLowPrice(), tweezerTolerance)) {
            return new Result(Pattern.TWEEZER_BOTTOM, 2, "Tweezer Bottom");
        }

        // --- Meeting Lines: gap open then close at previous close (reversal/indecision) ---
        // Bullish Meeting Lines: bearish candle → gap down → bullish closes at same close
        if (!c2Bullish && c3Bullish
                && c3.getOpeningPrice().compareTo(c2.getClosingPrice()) < 0
                && approxEqual(c3.getClosingPrice(), c2.getClosingPrice(), tweezerTolerance)) {
            return new Result(Pattern.BULLISH_MEETING_LINES, 2, "Bullish Meeting Lines");
        }

        // Bearish Meeting Lines: bullish candle → gap up → bearish closes at same close
        if (c2Bullish && !c3Bullish
                && c3.getOpeningPrice().compareTo(c2.getClosingPrice()) > 0
                && approxEqual(c3.getClosingPrice(), c2.getClosingPrice(), tweezerTolerance)) {
            return new Result(Pattern.BEARISH_MEETING_LINES, -2, "Bearish Meeting Lines");
        }

        // ── Single-Candle Patterns ──

        BigDecimal c3Range = c3.getHighPrice().subtract(c3.getLowPrice());

        // --- Marubozu: full body candle with tiny or no wicks, strong momentum ---
        if (c3Body.compareTo(BigDecimal.ZERO) > 0 && c3Range.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal wickThreshold = c3Body.multiply(new BigDecimal("0.05")); // 5% of body
            boolean tinyUpperWick = c3UpperWick.compareTo(wickThreshold) <= 0;
            boolean tinyLowerWick = c3LowerWick.compareTo(wickThreshold) <= 0;
            if (tinyUpperWick && tinyLowerWick) {
                if (c3Bullish) {
                    return new Result(Pattern.BULLISH_MARUBOZU, 2, "Bullish Marubozu");
                } else {
                    return new Result(Pattern.BEARISH_MARUBOZU, -2, "Bearish Marubozu");
                }
            }
        }

        // Hammer / Shooting Star: small body, long wick on one side
        if (c3Body.compareTo(BigDecimal.ZERO) > 0 && c3Body.compareTo(c3Range.multiply(new BigDecimal("0.3"))) <= 0) {
            // Lower wick at least 2x body, upper wick ≤ body
            if (c3LowerWick.compareTo(c3Body.multiply(BigDecimal.valueOf(2))) >= 0
                    && c3UpperWick.compareTo(c3Body) <= 0) {
                return new Result(Pattern.HAMMER, 2, "Hammer");
            }
            // Upper wick at least 2x body, lower wick ≤ body
            if (c3UpperWick.compareTo(c3Body.multiply(BigDecimal.valueOf(2))) >= 0
                    && c3LowerWick.compareTo(c3Body) <= 0) {
                return new Result(Pattern.SHOOTING_STAR, -2, "Shooting Star");
            }
        }

        // ── Doji variants (checked before Spinning Top since doji has stricter body threshold) ──
        if (c3Body.compareTo(BigDecimal.ZERO) == 0 || isDoji(c3)) {
            // Dragonfly Doji: close/open at high, long lower wick
            BigDecimal smallThreshold = c3Range.multiply(new BigDecimal("0.10")); // 10% of range
            BigDecimal longWickThreshold = c3Range.multiply(new BigDecimal("0.50")); // 50% of range

            // Dragonfly Doji: tiny or no upper wick, long lower wick
            if (c3UpperWick.compareTo(smallThreshold) <= 0
                    && c3LowerWick.compareTo(longWickThreshold) >= 0) {
                return new Result(Pattern.DRAGONFLY_DOJI, 2, "Dragonfly Doji");
            }

            // Gravestone Doji: tiny or no lower wick, long upper wick
            if (c3LowerWick.compareTo(smallThreshold) <= 0
                    && c3UpperWick.compareTo(longWickThreshold) >= 0) {
                return new Result(Pattern.GRAVESTONE_DOJI, -2, "Gravestone Doji");
            }

            // Long-legged Doji: both wicks substantial
            BigDecimal moderateThreshold = c3Range.multiply(new BigDecimal("0.40")); // 40% of range
            if (c3UpperWick.compareTo(moderateThreshold) >= 0
                    && c3LowerWick.compareTo(moderateThreshold) >= 0) {
                return new Result(Pattern.LONG_LEGGED_DOJI, 0, "Long-legged Doji");
            }

            // Plain Doji: none of the above variants
            return new Result(Pattern.DOJI, 0, "Doji");
        }

        // Spinning Top: small body (≤30% range), both upper AND lower wicks > body
        if (c3Body.compareTo(BigDecimal.ZERO) > 0
                && c3Body.compareTo(c3Range.multiply(new BigDecimal("0.3"))) <= 0
                && c3UpperWick.compareTo(c3Body) > 0
                && c3LowerWick.compareTo(c3Body) > 0) {
            return new Result(Pattern.SPINNING_TOP, 0, "Spinning Top");
        }

        // --- Belt Hold: opens at one extreme, closes near the other ---
        if (c3Body.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tinyThreshold = c3Range.multiply(new BigDecimal("0.01")); // 1% of range
            BigDecimal moderateThreshold = c3Range.multiply(new BigDecimal("0.35")); // 35% of range
            // Bullish Belt Hold: opens at/near low (no lower wick), close near high
            if (c3Bullish && c3LowerWick.compareTo(tinyThreshold) <= 0
                    && c3UpperWick.compareTo(moderateThreshold) <= 0) {
                return new Result(Pattern.BULLISH_BELT_HOLD, 2, "Bullish Belt Hold");
            }
            // Bearish Belt Hold: opens at/near high (no upper wick), close near low
            if (!c3Bullish && c3UpperWick.compareTo(tinyThreshold) <= 0
                    && c3LowerWick.compareTo(moderateThreshold) <= 0) {
                return new Result(Pattern.BEARISH_BELT_HOLD, -2, "Bearish Belt Hold");
            }
        }

        // ── Fuzzy Pattern Detection (≥90% match threshold) ──
        // If no exact pattern matched, check for near-misses with continuous scoring
        if (c3Body.compareTo(BigDecimal.ZERO) > 0 && c3Range.compareTo(BigDecimal.ZERO) > 0) {
            double bodyPct = c3Body.divide(c3Range, 6, RoundingMode.HALF_UP).doubleValue() * 100;
            double lwRatio = c3LowerWick.divide(c3Body, 4, RoundingMode.HALF_UP).doubleValue();
            double uwRatio = c3UpperWick.divide(c3Body, 4, RoundingMode.HALF_UP).doubleValue();

            // Fuzzy Hammer: score each condition continuously
            double hammerPct = 0;
            // Condition 1: body > 0 (already passed)
            hammerPct += 100;
            // Condition 2: body ≤ 30% of range
            hammerPct += bodyPct <= 30 ? 100 : Math.min(100, 30.0 / bodyPct * 100);
            // Condition 3: lower wick ≥ 2× body
            hammerPct += lwRatio >= 2 ? 100 : Math.min(100, lwRatio / 2.0 * 100);
            // Condition 4: upper wick ≤ body
            hammerPct += uwRatio <= 1 ? 100 : Math.min(100, 1.0 / uwRatio * 100);
            double hammerAvg = hammerPct / 4;

            if (hammerAvg >= 90) {
                return new Result(Pattern.HAMMER, (int) Math.round(hammerAvg),
                        String.format("Hammer (%.0f%%)", hammerAvg));
            }

            // Fuzzy Shooting Star
            double starPct = 0;
            starPct += 100; // body > 0
            starPct += bodyPct <= 30 ? 100 : Math.min(100, 30.0 / bodyPct * 100);
            starPct += uwRatio >= 2 ? 100 : Math.min(100, uwRatio / 2.0 * 100);
            starPct += lwRatio <= 1 ? 100 : Math.min(100, 1.0 / lwRatio * 100);
            double starAvg = starPct / 4;

            if (starAvg >= 90) {
                return new Result(Pattern.SHOOTING_STAR, -(int) Math.round(starAvg),
                        String.format("Shooting Star (%.0f%%)", starAvg));
            }
        }

        return new Result(Pattern.NONE, 0, null);
    }

    private boolean isBullish(DailyPrice c) {
        return c.getClosingPrice().compareTo(c.getOpeningPrice()) >= 0;
    }

    private boolean isDoji(DailyPrice c) {
        BigDecimal diff = c.getClosingPrice().subtract(c.getOpeningPrice()).abs();
        BigDecimal range = c.getHighPrice().subtract(c.getLowPrice());
        return range.compareTo(BigDecimal.ZERO) > 0
                && diff.divide(range, 4, RoundingMode.HALF_UP).compareTo(DOJI_THRESHOLD) <= 0;
    }

    private BigDecimal bodyLength(DailyPrice c) {
        return c.getClosingPrice().subtract(c.getOpeningPrice()).abs();
    }

    private BigDecimal upperWick(DailyPrice c) {
        BigDecimal top = c.getClosingPrice().max(c.getOpeningPrice());
        return c.getHighPrice().subtract(top);
    }

    private BigDecimal lowerWick(DailyPrice c) {
        BigDecimal bottom = c.getClosingPrice().min(c.getOpeningPrice());
        return bottom.subtract(c.getLowPrice());
    }

    private BigDecimal midpoint(DailyPrice c) {
        return c.getOpeningPrice().add(c.getClosingPrice()).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    /**
     * Checks if two prices are approximately equal within a given tolerance percentage.
     * Uses the average of the two prices as the base for the tolerance calculation.
     */
    private boolean approxEqual(BigDecimal a, BigDecimal b, BigDecimal tolerancePct) {
        if (a == null || b == null) return false;
        BigDecimal diff = a.subtract(b).abs();
        BigDecimal avg = a.add(b).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        BigDecimal threshold = avg.multiply(tolerancePct).abs();
        return diff.compareTo(threshold) <= 0;
    }
}
