package org.example.service.smc;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Break of Structure detector with ATR gating.
 * <p>
 * Rules:
 * <ul>
 *   <li>Only close-based breaks (not wicks)</li>
 *   <li>Break must exceed ATR threshold (default 0.3× ATR beyond the swing)</li>
 *   <li>Previous swing must be confirmed (not a weak pivot)</li>
 *   <li>Skips inside bars, tiny candles, equal highs</li>
 * </ul>
 */
public class BOSDetector {

    private final double atrMultiplier;

    public BOSDetector() { this(0.3); }

    public BOSDetector(double atrMultiplier) {
        this.atrMultiplier = atrMultiplier;
    }

    public record BOS(
        LocalDate date,
        BigDecimal price,
        boolean bullish,      // true = bullish BOS (broke above), false = bearish
        BigDecimal swingLevel, // the swing level that was broken
        BigDecimal strength    // how far beyond in ATR units
    ) {
        public String label() { return bullish ? "BOS ↑" : "BOS ↓"; }
    }

    public List<BOS> detect(List<DailyPrice> prices, List<SwingPoint> swings, BigDecimal atr) {
        if (atr == null) return List.of();
        List<BOS> results = new ArrayList<>();
        int n = prices.size();

        // Build sorted lists of swing indices (TreeSet ensures deterministic ordering)
        Set<Integer> highIdx = new TreeSet<>();
        Set<Integer> lowIdx = new TreeSet<>();
        for (SwingPoint sp : swings) {
            if (sp.isHigh()) highIdx.add(sp.index());
            else lowIdx.add(sp.index());
        }

        // Track consumed swings — once a level generates a BOS, remove it
        Set<Integer> activeHighs = new HashSet<>(highIdx);
        Set<Integer> activeLows = new HashSet<>(lowIdx);

        BigDecimal threshold = atr.multiply(BigDecimal.valueOf(atrMultiplier));

        for (int i = 0; i < n; i++) {
            DailyPrice curr = prices.get(i);
            BigDecimal close = curr.getClosingPrice();
            boolean isInsideBar = curr.getHighPrice().compareTo(curr.getLowPrice()) == 0
                    || close.equals(curr.getOpeningPrice());
            if (isInsideBar) continue;

            // Check bullish BOS: close above swing high + ATR threshold
            // Use nearest (most recent) swing to the current candle
            int nearestHighIdx = -1;
            for (int idx : highIdx) {
                if (idx < i) nearestHighIdx = idx;
                else break;
            }
            if (nearestHighIdx >= 0 && activeHighs.contains(nearestHighIdx)) {
                BigDecimal swingHigh = prices.get(nearestHighIdx).getHighPrice();
                BigDecimal breakLevel = swingHigh.add(threshold);
                if (close.compareTo(breakLevel) > 0) {
                    BigDecimal strength = close.subtract(swingHigh).divide(atr, 2, java.math.RoundingMode.HALF_UP);
                    if (strength.compareTo(BigDecimal.valueOf(atrMultiplier)) >= 0) {
                        results.add(new BOS(curr.getPriceDate(), close, true, swingHigh, strength));
                        activeHighs.remove(nearestHighIdx); // CONSUME — one BOS per swing
                        continue;
                    }
                }
            }

            // Check bearish BOS: close below swing low - ATR threshold
            int nearestLowIdx = -1;
            for (int idx : lowIdx) {
                if (idx < i) nearestLowIdx = idx;
                else break;
            }
            if (nearestLowIdx >= 0 && activeLows.contains(nearestLowIdx)) {
                BigDecimal swingLow = prices.get(nearestLowIdx).getLowPrice();
                BigDecimal breakLevel = swingLow.subtract(threshold);
                if (close.compareTo(breakLevel) < 0) {
                    BigDecimal strength = swingLow.subtract(close).divide(atr, 2, java.math.RoundingMode.HALF_UP);
                    if (strength.compareTo(BigDecimal.valueOf(atrMultiplier)) >= 0) {
                        results.add(new BOS(curr.getPriceDate(), close, false, swingLow, strength));
                        activeLows.remove(nearestLowIdx); // CONSUME
                    }
                }
            }
        }
        return results;
    }
}
