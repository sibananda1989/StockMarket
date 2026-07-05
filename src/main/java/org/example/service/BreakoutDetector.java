package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.LevelType;
import org.example.entity.SupportResistanceLevel;
import org.example.repository.SupportResistanceLevelRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BreakoutDetector {

    private final SupportResistanceLevelRepository srRepository;

    /**
     * Result of breakout detection for a stock.
     */
    public static class BreakoutResult {
        public boolean volumeBreakout;   // price breaks resistance with 2x+ volume
        public boolean gapUp;            // today's low > yesterday's high
        public boolean gapDown;          // today's high < yesterday's low
        public boolean rangeBreakout;    // price exits consolidation zone
        public int score;                // net score contribution (-4 to +4)
        public String description;       // human-readable summary

        public BreakoutResult() {
            this.description = "No breakout detected";
        }
    }

    /**
     * Detects breakouts for a stock given its price history.
     *
     * @param prices  price history ordered ASC (oldest first), last element = today
     * @param stockId stock ID for S/R level lookup
     * @return breakout detection result with score contribution
     */
    public BreakoutResult detect(List<DailyPrice> prices, Long stockId) {
        BreakoutResult result = new BreakoutResult();

        if (prices == null || prices.size() < 20) {
            return result;
        }

        DailyPrice today = prices.get(prices.size() - 1);
        DailyPrice yesterday = prices.get(prices.size() - 2);

        // 1. Volume breakout: price closes above resistance with 2x avg volume
        detectVolumeBreakout(prices, stockId, today, result);

        // 2. Gap detection
        detectGaps(yesterday, today, result);

        // 3. Range breakout: Bollinger Band squeeze followed by expansion
        detectRangeBreakout(prices, today, result);

        // Compute net score
        result.score = 0;
        if (result.volumeBreakout) result.score += 2;
        if (result.gapUp) result.score += 2;
        if (result.gapDown) result.score -= 2;
        if (result.rangeBreakout) result.score += 1;

        // Build description
        StringBuilder desc = new StringBuilder();
        if (result.volumeBreakout) desc.append("Volume breakout above resistance; ");
        if (result.gapUp) desc.append("Gap up; ");
        if (result.gapDown) desc.append("Gap down; ");
        if (result.rangeBreakout) desc.append("Range breakout from consolidation; ");
        if (desc.isEmpty()) desc.append("No breakout detected");
        result.description = desc.toString();

        return result;
    }

    /**
     * Volume breakout: today's close > resistance AND volume >= 2x 20-day average.
     */
    private void detectVolumeBreakout(List<DailyPrice> prices, Long stockId, DailyPrice today, BreakoutResult result) {
        if (today.getClosingPrice() == null || today.getVolume() == null) return;

        // Get latest resistance levels
        List<SupportResistanceLevel> allLevels = srRepository.findLatestLevels(stockId);
        BigDecimal resistance = null;
        for (SupportResistanceLevel level : allLevels) {
            if (level.getLevelType() == LevelType.MAJOR_RESISTANCE
                    || level.getLevelType() == LevelType.PIVOT_R1
                    || level.getLevelType() == LevelType.PIVOT_R2
                    || level.getLevelType() == LevelType.PIVOT_R3) {
                if (level.getLevelValue() != null && level.getLevelValue().compareTo(BigDecimal.ZERO) > 0) {
                    if (resistance == null || level.getLevelValue().compareTo(resistance) > 0) {
                        resistance = level.getLevelValue();
                    }
                }
            }
        }

        if (resistance == null) return;

        // Check if price closed above resistance
        if (today.getClosingPrice().compareTo(resistance) <= 0) return;

        // Check volume spike: today's volume >= 2x 20-day average
        int lookback = Math.min(20, prices.size() - 1);
        long totalVol = 0;
        for (int i = prices.size() - 1 - lookback; i < prices.size() - 1; i++) {
            Long vol = prices.get(i).getVolume();
            totalVol += (vol != null ? vol : 0L);
        }
        long avgVol = totalVol / lookback;

        if (today.getVolume() >= avgVol * 2) {
            result.volumeBreakout = true;
        }
    }

    /**
     * Gap detection: opening gap up or down.
     * Gap up: today's low > yesterday's high
     * Gap down: today's high < yesterday's low
     */
    private void detectGaps(DailyPrice yesterday, DailyPrice today, BreakoutResult result) {
        if (today.getHighPrice() == null || today.getLowPrice() == null
                || yesterday.getHighPrice() == null || yesterday.getLowPrice() == null) return;

        if (today.getLowPrice().compareTo(yesterday.getHighPrice()) > 0) {
            result.gapUp = true;
        } else if (today.getHighPrice().compareTo(yesterday.getLowPrice()) < 0) {
            result.gapDown = true;
        }
    }

    /**
     * Range breakout: Bollinger Band squeeze (bandwidth < 5% of middle) followed by
     * price closing above upper band or below lower band.
     */
    private void detectRangeBreakout(List<DailyPrice> prices, DailyPrice today, BreakoutResult result) {
        if (prices.size() < 20) return;

        // Calculate 20-day SMA, upper/lower Bollinger Bands
        List<DailyPrice> last20 = prices.subList(prices.size() - 20, prices.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (DailyPrice dp : last20) {
            sum = sum.add(dp.getClosingPrice());
        }
        BigDecimal sma20 = sum.divide(BigDecimal.valueOf(20), 4, RoundingMode.HALF_UP);

        // Calculate standard deviation
        BigDecimal sumSqDiff = BigDecimal.ZERO;
        for (DailyPrice dp : last20) {
            BigDecimal diff = dp.getClosingPrice().subtract(sma20);
            sumSqDiff = sumSqDiff.add(diff.multiply(diff));
        }
        BigDecimal variance = sumSqDiff.divide(BigDecimal.valueOf(20), 8, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

        BigDecimal upper = sma20.add(stdDev.multiply(BigDecimal.valueOf(2)));
        BigDecimal lower = sma20.subtract(stdDev.multiply(BigDecimal.valueOf(2)));

        // Bandwidth = (upper - lower) / sma20
        BigDecimal bandwidth = upper.subtract(lower).divide(sma20, 4, RoundingMode.HALF_UP);

        // Squeeze: bandwidth < 5% (0.05)
        boolean isSqueeze = bandwidth.compareTo(new BigDecimal("0.05")) < 0;

        // Check previous day for squeeze (squeeze happened recently)
        if (prices.size() >= 21) {
            List<DailyPrice> prev20 = prices.subList(prices.size() - 21, prices.size() - 1);
            BigDecimal prevSum = BigDecimal.ZERO;
            for (DailyPrice dp : prev20) {
                prevSum = prevSum.add(dp.getClosingPrice());
            }
            BigDecimal prevSma = prevSum.divide(BigDecimal.valueOf(20), 4, RoundingMode.HALF_UP);
            BigDecimal prevSumSq = BigDecimal.ZERO;
            for (DailyPrice dp : prev20) {
                BigDecimal d = dp.getClosingPrice().subtract(prevSma);
                prevSumSq = prevSumSq.add(d.multiply(d));
            }
            BigDecimal prevVar = prevSumSq.divide(BigDecimal.valueOf(20), 8, RoundingMode.HALF_UP);
            BigDecimal prevStd = BigDecimal.valueOf(Math.sqrt(prevVar.doubleValue()));
            BigDecimal prevUpper = prevSma.add(prevStd.multiply(BigDecimal.valueOf(2)));
            BigDecimal prevLower = prevSma.subtract(prevStd.multiply(BigDecimal.valueOf(2)));
            BigDecimal prevBw = prevUpper.subtract(prevLower).divide(prevSma, 4, RoundingMode.HALF_UP);

            // Breakout: was squeeze, now price outside bands
            if (prevBw.compareTo(new BigDecimal("0.05")) < 0) {
                if (today.getClosingPrice().compareTo(upper) > 0
                        || today.getClosingPrice().compareTo(lower) < 0) {
                    result.rangeBreakout = true;
                }
            }
        }
    }
}
