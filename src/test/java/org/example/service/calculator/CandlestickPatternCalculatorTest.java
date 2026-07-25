package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link CandlestickPatternCalculator}.
 *
 * <p>Tests cover all 35 pattern enum values (excluding NONE) plus edge cases.
 * Prices are in ASCENDING order (oldest first); prices.get(size-1) is the latest (c3).</p>
 *
 * <p>IMPORTANT: The isDoji() threshold is diff/range ≤ 0.005 (0.5%). All doji test data
 * must respect this strict threshold. Non-doji data must exceed it (diff/range > 0.005).</p>
 */
class CandlestickPatternCalculatorTest {

    private final CandlestickPatternCalculator calculator = new CandlestickPatternCalculator();

    // ── Helpers ───────────────────────────────────────────────────────────

    private DailyPrice p(String open, String close, String high, String low) {
        return new DailyPrice((Stock) null, new BigDecimal(close), new BigDecimal(open),
                new BigDecimal(high), new BigDecimal(low), 0L, LocalDate.now());
    }

    private void assertPattern(CandlestickPatternCalculator.Pattern expected, int expectedScore,
                                String expectedLabelContains, CandlestickPatternCalculator.Result result) {
        assertNotNull(result, "Result should not be null");
        assertEquals(expected, result.pattern(),
                "Expected " + expected + " but got " + result.pattern() + " (" + result.label() + ")");
        assertEquals(expectedScore, result.score(),
                "Expected score " + expectedScore + " but got " + result.score());
        if (expectedLabelContains != null) {
            assertTrue(result.label() != null && result.label().contains(expectedLabelContains),
                    "Expected label to contain '" + expectedLabelContains + "' but got '" + result.label() + "'");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EDGE CASES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testNullPricesReturnsNone() {
        assertPattern(CandlestickPatternCalculator.Pattern.NONE, 0, null, calculator.detect(null));
    }

    @Test
    void testEmptyPricesReturnsNone() {
        assertPattern(CandlestickPatternCalculator.Pattern.NONE, 0, null, calculator.detect(List.of()));
    }

    @Test
    void testLessThanThreePricesReturnsNone() {
        assertPattern(CandlestickPatternCalculator.Pattern.NONE, 0, null,
                calculator.detect(List.of(p("100", "101", "102", "99"), p("101", "102", "103", "100"))));
    }

    @Test
    void testNoPatternDetectedReturnsNone() {
        // Three candles that match no specific pattern
        List<DailyPrice> prices = List.of(
                p("100", "100", "102", "98"),   // c1: doji-like (close=open)
                p("101", "103", "104", "100"),  // c2: bullish
                p("104", "102", "105", "101")   // c3: bearish (but mathes no 2-candle pattern)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.NONE, 0, null, calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3-CANDLE: ABANDONED BABY — checked BEFORE Morning/Evening Star
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testBullishAbandonedBaby() {
        // c1: bearish (110→100). c2: doji gapped BELOW c1 (c2.high < c1.low).
        // c3: bullish gapped ABOVE c2 (c3.low > c2.high), close > c1.midpoint.
        // c1 midpoint = (110+100)/2 = 105
        // c2: diff=0.01, range=2, diff/range=0.5% ≤ 0.5% ✓ doji
        // c2.high=95 < c1.low=96 ✓ (gap down)
        // c3.low=106 > c2.high=95 ✓ (gap up); c3.close=115 > 105 ✓
        List<DailyPrice> prices = List.of(
                p("110", "100", "112", "96"),   // c1: bearish
                p("94", "94.01", "95", "93"),    // c2: doji (0.5% threshold)
                p("108", "115", "116", "106")    // c3: bullish, confirms
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BULLISH_ABANDONED_BABY, 3,
                "Bullish Abandoned Baby", calculator.detect(prices));
    }

    @Test
    void testBearishAbandonedBaby() {
        // c1: bullish (100→110). c2: doji gapped ABOVE c1 (c2.low > c1.high).
        // c3: bearish gapped BELOW c2 (c3.high < c2.low), close < c1.midpoint.
        // c1 midpoint = (100+110)/2 = 105
        // c2: diff=0.01, range=3, diff/range=0.33% < 0.5% ✓ doji
        // c2.low=115 > c1.high=113 ✓ (gap up)
        // c3.high=104 < c2.low=115 ✓ (gap down); c3.close=95 < 105 ✓
        List<DailyPrice> prices = List.of(
                p("100", "110", "113", "98"),    // c1: bullish
                p("116", "116.01", "118", "115"), // c2: doji (0.33%)
                p("103", "95", "104", "93")       // c3: bearish, confirms
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BEARISH_ABANDONED_BABY, -3,
                "Bearish Abandoned Baby", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3-CANDLE: MORNING STAR / EVENING STAR
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testMorningStar() {
        // c1: bearish, c2: doji, c3: bullish close above c1.midpoint (105)
        // c2: diff=0.01, range=2, 0.5% ≤ 0.5% ✓ doji
        List<DailyPrice> prices = List.of(
                p("110", "100", "112", "98"),
                p("101", "101.01", "102", "100"), // doji (0.5%)
                p("106", "115", "116", "105")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.MORNING_STAR, 3, "Morning Star",
                calculator.detect(prices));
    }

    @Test
    void testEveningStar() {
        // c1: bullish, c2: doji, c3: bearish close below c1.midpoint (105)
        // c2: diff=0.01, range=2, 0.5% ≤ 0.5% ✓ doji
        List<DailyPrice> prices = List.of(
                p("100", "110", "112", "98"),
                p("109", "109.01", "110", "108"), // doji (0.5%)
                p("104", "95", "105", "93")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.EVENING_STAR, -3, "Evening Star",
                calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3-CANDLE: ADVANCE BLOCK / DELIBERATION / THREE SOLDIERS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testAdvanceBlock() {
        // Three bullish, rising closes, each body progressively smaller
        List<DailyPrice> prices = List.of(
                p("100", "110", "111", "99"),   // c1: body=10
                p("108", "115", "116", "107"),  // c2: body=7 (<10), close(115)>c1.close(110)
                p("113", "118", "119", "112")   // c3: body=5 (<7), close(118)>c2.close(115)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.ADVANCE_BLOCK, -1, "Advance Block",
                calculator.detect(prices));
    }

    @Test
    void testDeliberation() {
        // Three bullish, rising closes, THIRD body smaller than both previous
        // But c2 body NOT smaller than c1 (so Advance Block doesn't trigger)
        List<DailyPrice> prices = List.of(
                p("100", "110", "111", "99"),   // c1: body=10
                p("108", "120", "121", "107"),  // c2: body=12 (>10), close(120)>c1.close(110)
                p("119", "121", "122", "118")   // c3: body=2 (<12 and <10), close(121)>c2.close(120)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.DELIBERATION, -1, "Deliberation",
                calculator.detect(prices));
    }

    @Test
    void testThreeWhiteSoldiers() {
        // Three bullish with rising closes (no body shrinkage)
        List<DailyPrice> prices = List.of(
                p("100", "108", "109", "99"),
                p("106", "116", "117", "105"),
                p("114", "124", "125", "113")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.THREE_WHITE_SOLDIERS, 3,
                "Three White Soldiers", calculator.detect(prices));
    }

    @Test
    void testThreeBlackCrows() {
        // Three bearish with falling closes
        List<DailyPrice> prices = List.of(
                p("110", "100", "111", "99"),
                p("102", "92", "103", "91"),
                p("94", "84", "95", "83")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.THREE_BLACK_CROWS, -3,
                "Three Black Crows", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3-CANDLE: THREE INSIDE UP / DOWN
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testThreeInsideUp() {
        // c1: bearish, c2: bullish inside c1 (harami), c3: bullish closes above c1.open
        List<DailyPrice> prices = List.of(
                p("110", "100", "112", "98"),   // c1: bearish, body=10
                p("105", "108", "108", "104"),  // c2: bullish inside c1 (high=108<110✓, low=104>100✓)
                p("109", "115", "116", "108")   // c3: bullish, close(115)>c1.open(110)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.THREE_INSIDE_UP, 2,
                "Three Inside Up", calculator.detect(prices));
    }

    @Test
    void testThreeInsideDown() {
        // c1: bullish, c2: bearish inside c1, c3: bearish closes below c1.open
        List<DailyPrice> prices = List.of(
                p("100", "110", "112", "98"),   // c1: bullish, body=10
                p("105", "102", "106", "101"),  // c2: bearish, inside c1 (high=106<110✓, low=101>100✓)
                p("103", "96", "104", "95")     // c3: bearish, close(96)<c1.open(100)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.THREE_INSIDE_DOWN, -2,
                "Three Inside Down", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3-CANDLE: THREE OUTSIDE UP / DOWN
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testThreeOutsideUp() {
        // c1: bearish, c2: bullish engulfs c1, c3: bullish closes above c2.close
        List<DailyPrice> prices = List.of(
                p("110", "100", "112", "98"),   // c1: bearish
                p("99", "115", "116", "98"),    // c2: bullish engulfs c1 (open=99≤100✓, close=115>110✓)
                p("114", "120", "121", "113")   // c3: bullish, close(120)>c2.close(115)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.THREE_OUTSIDE_UP, 3,
                "Three Outside Up", calculator.detect(prices));
    }

    @Test
    void testThreeOutsideDown() {
        // c1: bullish, c2: bearish engulfs c1, c3: bearish closes below c2.close
        List<DailyPrice> prices = List.of(
                p("100", "110", "112", "98"),   // c1: bullish
                p("111", "98", "113", "97"),    // c2: bearish engulfs c1 (open=111≥110✓, close=98<100✓)
                p("99", "94", "100", "93")      // c3: bearish, close(94)<c2.close(98)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.THREE_OUTSIDE_DOWN, -3,
                "Three Outside Down", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2-CANDLE: ENGULFING
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testBullishEngulfing() {
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("110", "100", "112", "98"),    // c2: bearish
                p("99", "115", "116", "98")      // c3: bullish, engulfs c2
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BULLISH_ENGULFING, 2,
                "Bullish Engulfing", calculator.detect(prices));
    }

    @Test
    void testBearishEngulfing() {
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "110", "112", "98"),    // c2: bullish
                p("111", "96", "113", "95")      // c3: bearish, engulfs c2
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BEARISH_ENGULFING, -2,
                "Bearish Engulfing", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2-CANDLE: PIERCING LINE / DARK CLOUD COVER
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testPiercingLine() {
        // c2: bearish, c3: bullish opens below c2.low, closes above c2.midpoint
        // c2 midpoint = (110+100)/2 = 105
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("110", "100", "112", "106"),  // c2: bearish, low=106
                p("104", "108", "109", "103")   // c3: open(104)<c2.low(106)✓, close(108)>mid(105)✓
        );
        assertPattern(CandlestickPatternCalculator.Pattern.PIERCING_LINE, 2,
                "Piercing Line", calculator.detect(prices));
    }

    @Test
    void testDarkCloudCover() {
        // c2: bullish, c3: bearish opens above c2.high, closes below c2.midpoint
        // c2: open=100, close=110, high=112, low=98, midpoint=(100+110)/2=105
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "110", "112", "98"),   // c2: bullish, high=112
                p("113", "103", "114", "102")   // c3: open(113)>c2.high(112)✓, close(103)<mid(105)✓
        );
        assertPattern(CandlestickPatternCalculator.Pattern.DARK_CLOUD_COVER, -2,
                "Dark Cloud Cover", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2-CANDLE: HARAMI
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testBullishHarami() {
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("110", "100", "112", "98"),   // c2: bearish, body=10
                p("105", "108", "108", "104")   // c3: bullish inside c2 (high=108<110✓, low=104>100✓)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BULLISH_HARAMI, 1,
                "Bullish Harami", calculator.detect(prices));
    }

    @Test
    void testBearishHarami() {
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "110", "112", "98"),   // c2: bullish, body=10
                p("105", "102", "106", "101")   // c3: bearish inside c2 (high=106<110✓, low=101>100✓)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BEARISH_HARAMI, -1,
                "Bearish Harami", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2-CANDLE: KICKER
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testBullishKicker() {
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("110", "100", "112", "98"),   // c2: bearish, high=112
                p("115", "120", "121", "114")   // c3: bullish, open(115)>c2.high(112)✓
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BULLISH_KICKER, 3,
                "Bullish Kicker", calculator.detect(prices));
    }

    @Test
    void testBearishKicker() {
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "110", "112", "98"),   // c2: bullish, low=98
                p("95", "90", "96", "89")       // c3: bearish, open(95)<c2.low(98)✓
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BEARISH_KICKER, -3,
                "Bearish Kicker", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2-CANDLE: TWEEZER
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testTweezerTop() {
        // c2: bullish, c3: bearish, same high
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "108", "110", "99"),   // c2: bullish, high=110
                p("110", "105", "110", "104")   // c3: bearish, high=110 (same)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.TWEEZER_TOP, -2,
                "Tweezer Top", calculator.detect(prices));
    }

    @Test
    void testTweezerBottom() {
        // c2: bearish, c3: bullish, same low
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("108", "100", "109", "95"),   // c2: bearish, low=95
                p("95", "102", "103", "95")     // c3: bullish, low=95 (same)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.TWEEZER_BOTTOM, 2,
                "Tweezer Bottom", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2-CANDLE: MEETING LINES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testBullishMeetingLines() {
        // c2: bearish, c3: bullish opens below c2.close, closes ≈ c2.close
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("110", "100", "112", "98"),   // c2: bearish, close=100
                p("98", "100.05", "101", "97")  // c3: open(98)<close(100)✓, close≈100 (0.05%≤0.1%)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BULLISH_MEETING_LINES, 2,
                "Bullish Meeting Lines", calculator.detect(prices));
    }

    @Test
    void testBearishMeetingLines() {
        // c2: bullish, c3: bearish opens above c2.close, closes ≈ c2.close
        // Need c2.high HIGH enough so DCC doesn't match (c3.open must NOT > c2.high)
        // c2: open=100, close=110, high=115, low=98 → c3.open=111 < c2.high=115, DCC doesn't match
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "110", "115", "98"),   // c2: bullish, high=115, close=110
                p("111", "110.02", "112", "109") // c3: open(111)>close(110)✓, close≈110 (0.018%<0.1%)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BEARISH_MEETING_LINES, -2,
                "Bearish Meeting Lines", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SINGLE-CANDLE: MARUBOZU
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testBullishMarubozu() {
        // Both wicks ≤ 5% of body. body=10, 5%=0.5. upper=0.4(≤0.5✓), lower=0.1(≤0.5✓)
        // c1/c2 must NOT match any 3-candle or 2-candle pattern
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("100", "110", "110.4", "99.9")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BULLISH_MARUBOZU, 2,
                "Bullish Marubozu", calculator.detect(prices));
    }

    @Test
    void testBearishMarubozu() {
        // c1/c2 must NOT match Dark Cloud Cover (need c3.open ≤ c2.high)
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "115", "99"),  // c2: bullish with high=115
                p("110", "100", "110.5", "99.9") // c3: bearish, open(110)≤c2.high(115)✓, DCC doesn't match
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BEARISH_MARUBOZU, -2,
                "Bearish Marubozu", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SINGLE-CANDLE: HAMMER / SHOOTING STAR
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testHammer() {
        // body≤30% range, lowerWick≥2×body, upperWick≤body
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("99", "100", "100.5", "97")   // body=1, range=3.5(28.6%<30%✓), lower=2(≥2✓), upper=0.5(≤1✓)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.HAMMER, 2, "Hammer",
                calculator.detect(prices));
    }

    @Test
    void testShootingStar() {
        // body≤30% range, upperWick≥2×body, lowerWick≤body
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("100", "101", "103.5", "99")  // body=1, range=4.5(22%<30%✓), upper=2.5(≥2✓), lower=1(≤1✓)
        );
        assertPattern(CandlestickPatternCalculator.Pattern.SHOOTING_STAR, -2, "Shooting Star",
                calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SINGLE-CANDLE: DOJI VARIANTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testDragonflyDoji() {
        // Doji with tiny upper wick (≤10% range), long lower wick (≥50% range)
        // c3: diff/range must be ≤ 0.5%
        // open=100, close=100.025, high=100.5, low=95
        // diff=0.025, range=5.5, diff/range=0.455% < 0.5% ✓ doji
        // upperWick=100.5-100.025=0.475; 10%×5.5=0.55; 0.475≤0.55 ✓
        // lowerWick=100-95=5; 50%×5.5=2.75; 5≥2.75 ✓
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("100", "100.025", "100.5", "95")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.DRAGONFLY_DOJI, 2,
                "Dragonfly Doji", calculator.detect(prices));
    }

    @Test
    void testGravestoneDoji() {
        // Doji with tiny lower wick (≤10% range), long upper wick (≥50% range)
        // open=100, close=100.02, high=105.5, low=99.8
        // diff=0.02, range=5.7, diff/range=0.351% < 0.5% ✓ doji
        // lowerWick=100-99.8=0.2; 10%×5.7=0.57; 0.2≤0.57 ✓
        // upperWick=105.5-100.02=5.48; 50%×5.7=2.85; 5.48≥2.85 ✓
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("100", "100.02", "105.5", "99.8")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.GRAVESTONE_DOJI, -2,
                "Gravestone Doji", calculator.detect(prices));
    }

    @Test
    void testLongLeggedDoji() {
        // Doji with both wicks ≥ 40% of range
        // open=100, close=100.03, high=106, low=94
        // diff=0.03, range=12, diff/range=0.25% < 0.5% ✓ doji
        // upperWick=106-100.03=5.97; 40%×12=4.8; 5.97≥4.8 ✓
        // lowerWick=100-94=6; 40%×12=4.8; 6≥4.8 ✓
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("100", "100.03", "106", "94")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.LONG_LEGGED_DOJI, 0,
                "Long-legged Doji", calculator.detect(prices));
    }

    @Test
    @Disabled("Plain Doji is practically unreachable with 0.5% doji threshold — "
            + "any valid doji will match Dragonfly, Gravestone, or Long-legged first")
    void testPlainDoji() {
        // A valid doji (diff/range ≤ 0.5%) with both wicks < 40% of range
        // would reach plain DOJI. This is extremely tight mathematically.
        // Left as a placeholder should the doji threshold ever be relaxed.
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SINGLE-CANDLE: SPINNING TOP
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testSpinningTop() {
        // Small body (≤30% range), both wicks > body, NOT a doji (body/range > 0.5%)
        // body=1.5, range=12, body/range=12.5% (>0.5%✓, <30%✓)
        // upperWick=106-101.5=4.5 > body=1.5 ✓
        // lowerWick=100-94=6 > body=1.5 ✓
        // Not a doji (12.5% > 0.5%) ✓
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("100", "101.5", "106", "94")
        );
        assertPattern(CandlestickPatternCalculator.Pattern.SPINNING_TOP, 0,
                "Spinning Top", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SINGLE-CANDLE: BELT HOLD
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testBullishBeltHold() {
        // Bullish, opens at/near low, closes near high
        // Ensure NOT a Marubozu (need upperWick > 5% of body)
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("100", "110", "110.6", "99.95") // upper=0.6 > 5%×10=0.5, not Marubozu
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BULLISH_BELT_HOLD, 2,
                "Bullish Belt Hold", calculator.detect(prices));
    }

    @Test
    void testBearishBeltHold() {
        // Bearish, opens at/near high (tiny upper wick), closes near low
        // Upper wick must be ≤ 1% of range (tiny open side)
        // Lower wick must be > 5% of body to avoid Marubozu, but ≤ 35% of range
        // c3: open=110, close=100, high=110.05, low=99.4
        // body=10, range=10.65
        // upperWick=0.05 ≤ 1%×10.65=0.1065 ✓ (tiny upper wick)
        // lowerWick=0.6 > 5%×10=0.5 (not Marubozu) ✓, ≤ 35%×10.65=3.7275 ✓
        // DCC: c3.open=110 ≤ c2.high=115 ✓ (doesn't match)
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "110", "115", "98"),   // c2: bullish, high=115
                p("110", "100", "110.05", "99.4") // c3: bearish belt hold
        );
        assertPattern(CandlestickPatternCalculator.Pattern.BEARISH_BELT_HOLD, -2,
                "Bearish Belt Hold", calculator.detect(prices));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FUZZY DETECTION (≥90% match for near-miss hammer/shooting star)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testFuzzyHammer() {
        // Near-hammer: good on 3/4 conditions, slightly off on upper wick
        // Must NOT match any pattern BEFORE fuzzy (Spinning Top, Belt Hold, etc.)
        // Spinning Top requires both wicks > body. So make upperWick ≤ body.
        // body=1, range=3, bodyPct=33.3% >30%, so range score=30/33.3*100=90.1
        // lowerWick=99-97.5=1.5; 1.5<2×1=2, so lower score=1.5/2*100=75
        // upperWick=100.5-100=0.5 ≤ body=1, upper score=100
        // hammerAvg=(100+90.1+75+100)/4=91.3% ≥ 90 ✓
        // Not exact Hammer (lowerWick=1.5<2✗, upperWick=0.5≤1✓ — lower fails)
        // Not Spinning Top (upperWick=0.5 ≤ body=1 → upper wick NOT > body)
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("99", "100", "100.5", "97.5") // fuzzy hammer (~91%)
        );
        CandlestickPatternCalculator.Result r = calculator.detect(prices);
        assertEquals(CandlestickPatternCalculator.Pattern.HAMMER, r.pattern());
        assertTrue(r.score() >= 90, "Fuzzy hammer score should be >= 90 but was " + r.score());
        assertTrue(r.label().contains("Hammer"), "Label should contain 'Hammer' but was '" + r.label() + "'");
    }

    @Test
    void testFuzzyShootingStar() {
        // Near-shooting star with ≥90% match
        // Must NOT match any pattern before fuzzy
        List<DailyPrice> prices = List.of(
                p("100", "101", "102", "99"),
                p("100", "101", "102", "99"),
                p("100", "101", "103", "99.9") // near-shooting star
        );
        CandlestickPatternCalculator.Result r = calculator.detect(prices);
        assertEquals(CandlestickPatternCalculator.Pattern.SHOOTING_STAR, r.pattern());
        assertTrue(r.score() < 0, "Fuzzy shooting star score should be negative but was " + r.score());
        assertTrue(r.label().contains("Shooting Star"), "Label should contain 'Shooting Star' but was '" + r.label() + "'");
    }
}
