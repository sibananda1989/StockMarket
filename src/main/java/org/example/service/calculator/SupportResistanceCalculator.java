package org.example.service.calculator;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.SupportResistanceDto;
import org.example.entity.DailyPrice;
import org.example.entity.LevelType;
import org.example.entity.Stock;
import org.example.entity.SupportResistanceLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SupportResistanceCalculator {

    private static final int SWING_LOOKBACK = 5;
    private static final BigDecimal SIGNIFICANCE_THRESHOLD = BigDecimal.valueOf(0.01); // 1% of average
    private static final BigDecimal CLUSTER_TOLERANCE = BigDecimal.valueOf(0.02); // 2% clustering
    private static final int PIVOT_LOOKBACK_DAYS = 20;
    private static final int HISTORICAL_LOOKBACK_DAYS = 180; // 6 months

    /**
     * Full calculation pipeline: returns all S/R levels for a stock.
     */
    public Result calculate(Stock stock, List<DailyPrice> prices) {
        LocalDate today = LocalDate.now();
        List<SupportResistanceLevel> levels = new ArrayList<>();
        SupportResistanceDto dto = new SupportResistanceDto();
        dto.setStockId(stock.getId());
        dto.setSymbol(stock.getSymbol());
        dto.setCalculationDate(today);

        // 1. 20-day Swing Highs and Lows
        List<DailyPrice> recentPrices = getLookback(prices, PIVOT_LOOKBACK_DAYS);
        List<SupportResistanceDto.SwingLevel> swingHighs = detectSwingHighs(recentPrices);
        List<SupportResistanceDto.SwingLevel> swingLows = detectSwingLows(recentPrices);
        dto.setSwingHighs(swingHighs);
        dto.setSwingLows(swingLows);

        // Persist swing levels — use list index as levelOrder to satisfy the
        // (stock_id, level_type, calculation_date, level_order) unique constraint
        // when multiple swing highs/lows are detected in the same window.
        for (int i = 0; i < swingHighs.size(); i++) {
            SupportResistanceDto.SwingLevel sh = swingHighs.get(i);
            levels.add(buildLevel(stock, LevelType.SWING_HIGH, sh.getPrice(), i, today,
                    sh.getStrength(), null, PIVOT_LOOKBACK_DAYS));
        }
        for (int i = 0; i < swingLows.size(); i++) {
            SupportResistanceDto.SwingLevel sl = swingLows.get(i);
            levels.add(buildLevel(stock, LevelType.SWING_LOW, sl.getPrice(), i, today,
                    sl.getStrength(), null, PIVOT_LOOKBACK_DAYS));
        }

        // 2. Standard Pivot Points from 20-day OHLC
        SupportResistanceDto.PivotLevels pivots = calculatePivotPoints(recentPrices);
        dto.setPivots(pivots);

        if (pivots != null) {
            persistPivot(levels, stock, today, LevelType.PIVOT_P, pivots.getPivot(), 0);
            persistPivot(levels, stock, today, LevelType.PIVOT_S1, pivots.getS1(), 1);
            persistPivot(levels, stock, today, LevelType.PIVOT_S2, pivots.getS2(), 2);
            persistPivot(levels, stock, today, LevelType.PIVOT_S3, pivots.getS3(), 3);
            persistPivot(levels, stock, today, LevelType.PIVOT_R1, pivots.getR1(), 1);
            persistPivot(levels, stock, today, LevelType.PIVOT_R2, pivots.getR2(), 2);
            persistPivot(levels, stock, today, LevelType.PIVOT_R3, pivots.getR3(), 3);
        }

        // 3. Major Historical Pivots (6-month)
        List<DailyPrice> historicalPrices = getLookback(prices, HISTORICAL_LOOKBACK_DAYS);
        List<SupportResistanceDto.MajorLevel> majorLevels = detectMajorLevels(historicalPrices);
        dto.setMajorLevels(majorLevels);

        for (int i = 0; i < majorLevels.size(); i++) {
            SupportResistanceDto.MajorLevel ml = majorLevels.get(i);
            LevelType lt = "support".equals(ml.getType()) ? LevelType.MAJOR_SUPPORT : LevelType.MAJOR_RESISTANCE;
            // Use index (rank by strength) as levelOrder to avoid unique-constraint collisions
            // when multiple major levels share the same touch count
            levels.add(buildLevel(stock, lt, ml.getPrice(), i, today,
                    ml.getStrength(), ml.getTouches(), HISTORICAL_LOOKBACK_DAYS));
        }

        return new Result(dto, levels);
    }

    // ========== Swing Detection ==========

    /**
     * Detects swing highs: a candle whose high is the highest within ±SWING_LOOKBACK bars.
     * Only includes "significant" swings (high > avg * (1 + threshold)).
     */
    public List<SupportResistanceDto.SwingLevel> detectSwingHighs(List<DailyPrice> prices) {
        return detectSwings(prices, true);
    }

    /**
     * Detects swing lows: a candle whose low is the lowest within ±SWING_LOOKBACK bars.
     * Only includes "significant" swings (low < avg * (1 - threshold)).
     */
    public List<SupportResistanceDto.SwingLevel> detectSwingLows(List<DailyPrice> prices) {
        return detectSwings(prices, false);
    }

    private List<SupportResistanceDto.SwingLevel> detectSwings(List<DailyPrice> prices, boolean isHigh) {
        List<SupportResistanceDto.SwingLevel> swings = new ArrayList<>();
        if (prices.size() < 2 * SWING_LOOKBACK + 1) {
            return swings; // Not enough data
        }

        // Calculate average price for significance threshold
        BigDecimal avgPrice = prices.stream()
                .map(DailyPrice::getClosingPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 4, RoundingMode.HALF_UP);

        for (int i = SWING_LOOKBACK; i < prices.size() - SWING_LOOKBACK; i++) {
            DailyPrice candidate = prices.get(i);
            boolean isExtremum = true;

            for (int j = i - SWING_LOOKBACK; j <= i + SWING_LOOKBACK; j++) {
                if (j == i) continue;
                DailyPrice other = prices.get(j);

                if (isHigh) {
                    BigDecimal candidateHigh = candidate.getHighPrice() != null ? candidate.getHighPrice() : candidate.getClosingPrice();
                    BigDecimal otherHigh = other.getHighPrice() != null ? other.getHighPrice() : other.getClosingPrice();
                    if (otherHigh.compareTo(candidateHigh) > 0) {
                        isExtremum = false;
                        break;
                    }
                } else {
                    BigDecimal candidateLow = candidate.getLowPrice() != null ? candidate.getLowPrice() : candidate.getClosingPrice();
                    BigDecimal otherLow = other.getLowPrice() != null ? other.getLowPrice() : other.getClosingPrice();
                    if (otherLow.compareTo(candidateLow) < 0) {
                        isExtremum = false;
                        break;
                    }
                }
            }

            if (isExtremum) {
                BigDecimal price = isHigh
                        ? (candidate.getHighPrice() != null ? candidate.getHighPrice() : candidate.getClosingPrice())
                        : (candidate.getLowPrice() != null ? candidate.getLowPrice() : candidate.getClosingPrice());

                // Check significance: price must deviate from average by threshold
                BigDecimal deviation = price.subtract(avgPrice).abs()
                        .divide(avgPrice, 4, RoundingMode.HALF_UP);
                if (deviation.compareTo(SIGNIFICANCE_THRESHOLD) > 0) {
                    BigDecimal strength = calculateSwingStrength(prices, price, isHigh);
                    swings.add(new SupportResistanceDto.SwingLevel(price, candidate.getPriceDate(), strength, 0));
                }
            }
        }

        return swings;
    }

    /**
     * Calculates strength of a swing level based on how many times price approached it.
     */
    private BigDecimal calculateSwingStrength(List<DailyPrice> prices, BigDecimal level, boolean isHigh) {
        int touches = 0;
        BigDecimal tolerance = level.multiply(CLUSTER_TOLERANCE).setScale(4, RoundingMode.HALF_UP);

        for (DailyPrice dp : prices) {
            BigDecimal high = dp.getHighPrice() != null ? dp.getHighPrice() : dp.getClosingPrice();
            BigDecimal low = dp.getLowPrice() != null ? dp.getLowPrice() : dp.getClosingPrice();

            if (isHigh && high.subtract(level).abs().compareTo(tolerance) <= 0) {
                touches++;
            } else if (!isHigh && low.subtract(level).abs().compareTo(tolerance) <= 0) {
                touches++;
            }
        }

        // Strength = min(100, touches * 15)
        return BigDecimal.valueOf(Math.min(100, touches * 15)).setScale(2, RoundingMode.HALF_UP);
    }

    // ========== Pivot Points ==========

    /**
     * Standard pivot points from 20-day OHLC data:
     * P = (H20 + L20 + C20) / 3
     * S1 = 2*P - H20    R1 = 2*P - L20
     * S2 = P - (H20-L20)  R2 = P + (H20-L20)
     * S3 = L20 - 2*(H20-P) R3 = H20 + 2*(P-L20)
     */
    public SupportResistanceDto.PivotLevels calculatePivotPoints(List<DailyPrice> prices) {
        if (prices.isEmpty()) return null;

        BigDecimal h20 = prices.stream()
                .map(p -> p.getHighPrice() != null ? p.getHighPrice() : p.getClosingPrice())
                .max(BigDecimal::compareTo)
                .orElse(null);
        BigDecimal l20 = prices.stream()
                .map(p -> p.getLowPrice() != null ? p.getLowPrice() : p.getClosingPrice())
                .min(BigDecimal::compareTo)
                .orElse(null);
        BigDecimal c20 = prices.get(prices.size() - 1).getClosingPrice(); // latest close

        if (h20 == null || l20 == null || c20 == null) return null;

        BigDecimal pivot = h20.add(l20).add(c20).divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        BigDecimal range = h20.subtract(l20);

        BigDecimal s1 = pivot.multiply(BigDecimal.valueOf(2)).subtract(h20).setScale(4, RoundingMode.HALF_UP);
        BigDecimal r1 = pivot.multiply(BigDecimal.valueOf(2)).subtract(l20).setScale(4, RoundingMode.HALF_UP);
        BigDecimal s2 = pivot.subtract(range).setScale(4, RoundingMode.HALF_UP);
        BigDecimal r2 = pivot.add(range).setScale(4, RoundingMode.HALF_UP);
        BigDecimal s3 = l20.subtract(h20.subtract(pivot).multiply(BigDecimal.valueOf(2))).setScale(4, RoundingMode.HALF_UP);
        BigDecimal r3 = h20.add(pivot.subtract(l20).multiply(BigDecimal.valueOf(2))).setScale(4, RoundingMode.HALF_UP);

        return new SupportResistanceDto.PivotLevels(pivot, s1, s2, s3, r1, r2, r3);
    }

    // ========== Major Historical Levels ==========

    /**
     * Detects major support/resistance levels by finding significant swing points
     * across 6 months and clustering nearby levels.
     */
    public List<SupportResistanceDto.MajorLevel> detectMajorLevels(List<DailyPrice> prices) {
        List<SupportResistanceDto.MajorLevel> majorLevels = new ArrayList<>();
        if (prices.size() < 20) return majorLevels;

        // Detect all swings in the historical data
        List<SupportResistanceDto.SwingLevel> allHighs = detectSwingHighs(prices);
        List<SupportResistanceDto.SwingLevel> allLows = detectSwingLows(prices);

        // Cluster nearby levels
        List<ClusteredLevel> clusters = new ArrayList<>();

        for (SupportResistanceDto.SwingLevel sh : allHighs) {
            addOrCreateCluster(clusters, sh.getPrice(), "resistance", sh.getStrength());
        }
        for (SupportResistanceDto.SwingLevel sl : allLows) {
            addOrCreateCluster(clusters, sl.getPrice(), "support", sl.getStrength());
        }

        // Convert to DTOs, sorted by strength
        for (ClusteredLevel cluster : clusters) {
            if (cluster.touches >= 2) { // At least 2 touches to be "major"
                BigDecimal avgStrength = cluster.totalStrength
                        .divide(BigDecimal.valueOf(cluster.touches), 2, RoundingMode.HALF_UP);
                majorLevels.add(new SupportResistanceDto.MajorLevel(
                        cluster.avgPrice, cluster.type, cluster.touches, avgStrength));
            }
        }

        majorLevels.sort(Comparator.comparing(SupportResistanceDto.MajorLevel::getStrength).reversed());
        return majorLevels.stream().limit(10).collect(Collectors.toList()); // Top 10
    }

    private void addOrCreateCluster(List<ClusteredLevel> clusters, BigDecimal price, String type, BigDecimal strength) {
        BigDecimal tolerance = price.multiply(CLUSTER_TOLERANCE).setScale(4, RoundingMode.HALF_UP);

        for (ClusteredLevel cluster : clusters) {
            if (cluster.type.equals(type) && cluster.avgPrice.subtract(price).abs().compareTo(tolerance) <= 0) {
                // Merge into existing cluster
                cluster.totalPrice = cluster.totalPrice.add(price);
                cluster.totalStrength = cluster.totalStrength.add(strength);
                cluster.touches++;
                cluster.avgPrice = cluster.totalPrice.divide(BigDecimal.valueOf(cluster.touches), 4, RoundingMode.HALF_UP);
                return;
            }
        }

        // New cluster
        ClusteredLevel newCluster = new ClusteredLevel();
        newCluster.type = type;
        newCluster.totalPrice = price;
        newCluster.avgPrice = price;
        newCluster.totalStrength = strength;
        newCluster.touches = 1;
        clusters.add(newCluster);
    }

    // ========== Helpers ==========

    private List<DailyPrice> getLookback(List<DailyPrice> prices, int days) {
        if (prices.size() <= days) return prices;
        return prices.subList(prices.size() - days, prices.size());
    }

    private SupportResistanceLevel buildLevel(Stock stock, LevelType type, BigDecimal value,
                                               int order, LocalDate date, BigDecimal strength,
                                               Integer touchCount, Integer lookbackDays) {
        SupportResistanceLevel level = new SupportResistanceLevel();
        level.setStock(stock);
        level.setLevelType(type);
        level.setLevelValue(value.setScale(4, RoundingMode.HALF_UP));
        level.setLevelOrder(order);
        level.setCalculationDate(date);
        level.setStrengthScore(strength);
        level.setTouchCount(touchCount);
        level.setLookbackDays(lookbackDays);
        return level;
    }

    private void persistPivot(List<SupportResistanceLevel> levels, Stock stock,
                               LocalDate today, LevelType type, BigDecimal value, int order) {
        if (value != null) {
            levels.add(buildLevel(stock, type, value, order, today,
                    BigDecimal.valueOf(80), null, PIVOT_LOOKBACK_DAYS));
        }
    }

    // ========== Inner Classes ==========

    public static class Result {
        public final SupportResistanceDto dto;
        public final List<SupportResistanceLevel> levels;

        public Result(SupportResistanceDto dto, List<SupportResistanceLevel> levels) {
            this.dto = dto;
            this.levels = levels;
        }
    }

    private static class ClusteredLevel {
        String type;
        BigDecimal totalPrice;
        BigDecimal avgPrice;
        BigDecimal totalStrength;
        int touches;
    }
}
