package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class CandlestickPatternCalculator {

    private static final BigDecimal DOJI_THRESHOLD = new BigDecimal("0.005");

    public enum Pattern {
        NONE, BULLISH_ENGULFING, BEARISH_ENGULFING,
        HAMMER, SHOOTING_STAR,
        PIERCING_LINE, DARK_CLOUD_COVER,
        MORNING_STAR, EVENING_STAR,
        BULLISH_HARAMI, BEARISH_HARAMI
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

        // ── Single-Candle Patterns ──

        // Hammer: small body, long lower wick (2x+ body), small/no upper wick, in context of pullback
        BigDecimal c3Range = c3.getHighPrice().subtract(c3.getLowPrice());
        if (c3Body.compareTo(BigDecimal.ZERO) > 0 && c3Body.compareTo(c3Range.multiply(new BigDecimal("0.3"))) <= 0) {
            // Lower wick at least 2x body
            if (c3LowerWick.compareTo(c3Body.multiply(BigDecimal.valueOf(2))) >= 0
                    && c3UpperWick.compareTo(c3Body) <= 0) {
                return new Result(Pattern.HAMMER, 2, "Hammer");
            }
            // Shooting Star: small body, long upper wick (2x+ body), small/no lower wick
            if (c3UpperWick.compareTo(c3Body.multiply(BigDecimal.valueOf(2))) >= 0
                    && c3LowerWick.compareTo(c3Body) <= 0) {
                return new Result(Pattern.SHOOTING_STAR, -2, "Shooting Star");
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
}
