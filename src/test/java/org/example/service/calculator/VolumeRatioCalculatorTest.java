package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VolumeRatioCalculatorTest {

    private VolumeRatioCalculator calculator = new VolumeRatioCalculator();

    /**
     * Builds a list of price bars with per-day volume. The list is ordered
     * oldest-first so the last element is the "current" day.
     */
    private List<DailyPrice> buildPrices(long... volumes) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate date = LocalDate.now().minusDays(volumes.length - 1);
        for (int i = 0; i < volumes.length; i++) {
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal("100.0"),
                    new BigDecimal("100.0"),
                    new BigDecimal("100.0"),
                    new BigDecimal("100.0"),
                    volumes[i],
                    date.plusDays(i)
            ));
        }
        return prices;
    }

    @Test
    void testExact21DayMinimum() {
        // Minimum data = 21 days (1 current + 20 prior)
        List<DailyPrice> prices = buildPrices(
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                2000L // current day: 2x the 20-day average
        );
        BigDecimal ratio = calculator.calculate(prices);
        assertNotNull(ratio);
        assertEquals(2.0, ratio.doubleValue(), 0.01);
    }

    @Test
    void testRatioNormal() {
        // Current day equal to average → ratio ~1.0
        List<DailyPrice> prices = buildPrices(
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L
        );
        BigDecimal ratio = calculator.calculate(prices);
        assertEquals(1.0, ratio.doubleValue(), 0.001);
    }

    @Test
    void testRatioSpike() {
        // Current day 1.5x average → spike
        List<DailyPrice> prices = buildPrices(
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1500L
        );
        BigDecimal ratio = calculator.calculate(prices);
        assertEquals(1.5, ratio.doubleValue(), 0.001);
    }

    @Test
    void testRatioStrongSpike() {
        // Current day 3x average → strong spike
        List<DailyPrice> prices = buildPrices(
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                3000L
        );
        BigDecimal ratio = calculator.calculate(prices);
        assertEquals(3.0, ratio.doubleValue(), 0.001);
    }

    @Test
    void testZeroVolumeReturnsZero() {
        // Current day zero volume → ZERO (not a division error)
        List<DailyPrice> prices = buildPrices(
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                0L
        );
        assertEquals(BigDecimal.ZERO, calculator.calculate(prices));
    }

    @Test
    void testNullVolumesTreatedAsZero() {
        // Null volumes in the lookback window are treated as 0
        List<DailyPrice> prices = buildPrices(
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                2000L
        );
        // Introduce nulls for a few prior days
        prices.get(0).setVolume(null);
        prices.get(5).setVolume(null);
        prices.get(19).setVolume(null);

        BigDecimal ratio = calculator.calculate(prices);
        // avg = (17 * 1000) / 20 = 850; 2000 / 850 ≈ 2.35
        assertNotNull(ratio);
        assertEquals(2000.0 / 850.0, ratio.doubleValue(), 0.01);
    }

    @Test
    void testLatestDayExcludedFromAverage() {
        // The current day's volume must NOT pollute the 20-day average.
        // Prior 20 days all 1000, current day huge (9999). Average must still be 1000.
        List<DailyPrice> prices = buildPrices(
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                1000L, 1000L, 1000L, 1000L, 1000L,
                3000L
        );
        BigDecimal ratio = calculator.calculate(prices);
        assertEquals(3.0, ratio.doubleValue(), 0.001);
    }

    @Test
    void testGetSupportedType() {
        assertEquals(IndicatorType.VOLUME_RATIO, calculator.getSupportedType());
    }
}
