package org.example.service.smc;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Detects significant swing highs and lows using configurable pivot length,
 * ATR-based filtering, and HH/HL/LH/LL classification.
 * <p>
 * Pivot length = 5 (default): a candle needs 5 lower/higher candles on each side.
 * Swings smaller than 1 ATR from the prior swing are discarded.
 */
public class SwingDetector {

    private final int pivotLength;
    private final double minATRMultiplier;

    public SwingDetector() {
        this(5, 1.0);
    }

    public SwingDetector(int pivotLength, double minATRMultiplier) {
        this.pivotLength = pivotLength;
        this.minATRMultiplier = minATRMultiplier;
    }

    /**
     * Detect all significant swing points from daily prices.
     * @return ordered list (oldest first) of swing points with HH/HL/LH/LL labels
     */
    public List<SwingPoint> detect(List<DailyPrice> prices, BigDecimal atr) {
        // 1. Find raw pivot points
        Set<Integer> rawHighIdx = new HashSet<>();
        Set<Integer> rawLowIdx = new HashSet<>();
        findPivots(prices, rawHighIdx, rawLowIdx);

        // 2. Filter by ATR significance and build ordered swing list
        List<SwingPoint> swings = new ArrayList<>();
        List<Integer> highList = new ArrayList<>(rawHighIdx);
        List<Integer> lowList = new ArrayList<>(rawLowIdx);
        Collections.sort(highList);
        Collections.sort(lowList);

        // Filter weak swings: require distance from previous swing ≥ minATRMultiplier × ATR
        SwingPoint prevHigh = null;
        for (int idx : highList) {
            BigDecimal price = prices.get(idx).getHighPrice();
            LocalDate date = prices.get(idx).getPriceDate();
            if (prevHigh != null && atr != null) {
                BigDecimal diff = price.subtract(prevHigh.price()).abs();
                if (!ATRProvider.meetsMinATR(diff, atr, minATRMultiplier)) continue;
            }
            SwingPoint sp = new SwingPoint(idx, price, date, true, BigDecimal.ZERO, classifyHigh(price, prevHigh));
            swings.add(sp);
            prevHigh = sp;
        }

        SwingPoint prevLow = null;
        for (int idx : lowList) {
            BigDecimal price = prices.get(idx).getLowPrice();
            LocalDate date = prices.get(idx).getPriceDate();
            if (prevLow != null && atr != null) {
                BigDecimal diff = price.subtract(prevLow.price()).abs();
                if (!ATRProvider.meetsMinATR(diff, atr, minATRMultiplier)) continue;
            }
            SwingPoint sp = new SwingPoint(idx, price, date, false, BigDecimal.ZERO, classifyLow(price, prevLow));
            swings.add(sp);
            prevLow = sp;
        }

        // Sort by index
        swings.sort(Comparator.comparingInt(SwingPoint::index));
        return swings;
    }

    private void findPivots(List<DailyPrice> prices, Set<Integer> highs, Set<Integer> lows) {
        int n = prices.size();
        for (int i = pivotLength; i < n - pivotLength; i++) {
            BigDecimal high = prices.get(i).getHighPrice();
            BigDecimal low = prices.get(i).getLowPrice();
            boolean isHigh = true, isLow = true;
            for (int w = 1; w <= pivotLength; w++) {
                if (high.compareTo(prices.get(i - w).getHighPrice()) <= 0
                        || high.compareTo(prices.get(i + w).getHighPrice()) <= 0) isHigh = false;
                if (low.compareTo(prices.get(i - w).getLowPrice()) >= 0
                        || low.compareTo(prices.get(i + w).getLowPrice()) >= 0) isLow = false;
            }
            if (isHigh) highs.add(i);
            if (isLow) lows.add(i);
        }
    }

    private String classifyHigh(BigDecimal price, SwingPoint prev) {
        if (prev == null) return "NEW";
        return price.compareTo(prev.price()) > 0 ? "HH" : "LH";
    }

    private String classifyLow(BigDecimal price, SwingPoint prev) {
        if (prev == null) return "NEW";
        return price.compareTo(prev.price()) > 0 ? "HL" : "LL";
    }
}
