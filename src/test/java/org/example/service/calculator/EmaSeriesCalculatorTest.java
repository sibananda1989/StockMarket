package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmaSeriesCalculatorTest {

    private final EmaSeriesCalculator ema20 = new EmaSeriesCalculator(20);
    private final EmaSeriesCalculator ema50 = new EmaSeriesCalculator(50);

    private List<DailyPrice> ascendingCloses(double... closes) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 1, 1);
        for (double c : closes) {
            prices.add(new DailyPrice(null, BigDecimal.valueOf(c), day));
            day = day.plusDays(1);
        }
        return prices;
    }

    @Test
    void lastValue_matchesSingleValueEmaCalculator() {
        // Same input, deterministic trend: both calculators must agree on the last EMA.
        List<DailyPrice> prices = ascendingCloses(
                100, 102, 104, 103, 105, 107, 109, 108, 110, 112,
                111, 113, 115, 114, 116, 118, 120, 119, 121, 123,
                122, 124, 126, 125, 127, 129, 131, 130, 132, 134);
        List<BigDecimal> series = ema20.calculate(prices);

        // Series length = number of bars.
        assertEquals(30, series.size());
        // Last element matches EmaCalculator single-value output (scale 2).
        assertEquals(new EmaCalculator(20).calculate(prices), ema20.calculateLast(prices));
        // Seed at index period-1 is the SMA of the first 20 closes: (100+...+123)/20 = 111.50.
        assertEquals(new BigDecimal("111.50"),
                series.get(19).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void seedIsSmaOfFirstPeriodCloses() {
        // SMA of the first 4 (as a scaled stand-in for period 20) is the period-1 element.
        List<DailyPrice> prices = ascendingCloses(10, 20, 30, 40);
        EmaSeriesCalculator e4 = new EmaSeriesCalculator(4);
        BigDecimal seed = e4.calculate(prices).get(3);
        assertEquals(new BigDecimal("25.00"), seed.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void seriesIsMonotonicForRisingTrend() {
        // Prices continuously rising -> EMA-50 series must be strictly increasing (post seed).
        List<DailyPrice> prices = ascendingCloses(
                100, 102, 104, 103, 105, 107, 109, 108, 110, 112,
                111, 113, 115, 114, 116, 118, 120, 119, 121, 123,
                122, 124, 126, 125, 127, 129, 131, 130, 132, 134,
                136, 138, 137, 139, 141, 143, 145, 144, 146, 148,
                150, 152, 154, 153, 155, 157, 159, 158, 160, 162,
                161, 163, 165, 164, 166, 168, 170, 169, 171, 173);
        List<BigDecimal> series = ema50.calculate(prices);
        for (int i = 1; i < series.size(); i++) {
            if (i >= 50) {
                assertEquals(1, series.get(i).compareTo(series.get(i - 1)),
                        "EMA-50 should keep rising in a persistent uptrend at index " + i);
            }
        }
    }

    @Test
    void throwsWhenFewerBarsThanPeriod() {
        List<DailyPrice> prices = ascendingCloses(100, 101, 102);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ema50.calculate(prices));
        assertEquals("Need at least 50 days of price data to calculate EMA-50", e.getMessage());
    }

    @Test
    void calculatesLastMatchesForBothPeriods() {
        // Sufficiently long series: both EMA-20 and EMA-50 last values are sane.
        List<DailyPrice> prices = ascendingCloses(
                100, 102, 104, 103, 105, 107, 109, 108, 110, 112,
                111, 113, 115, 114, 116, 118, 120, 119, 121, 123,
                122, 124, 126, 125, 127, 129, 131, 130, 132, 134,
                136, 138, 137, 139, 141, 143, 145, 144, 146, 148,
                150, 152, 154, 153, 155, 157, 159, 158, 160, 162,
                161, 163, 165, 164, 166, 168, 170, 169, 171, 173);
        assertEquals(60, ema20.calculate(prices).size());
        assertEquals(60, ema50.calculate(prices).size());
        // EMA-20 (faster) should be higher than EMA-50 in a rising market.
        assert (ema20.calculateLast(prices).compareTo(ema50.calculateLast(prices)) > 0);
    }
}