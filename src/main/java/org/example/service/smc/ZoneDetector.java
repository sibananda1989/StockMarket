package org.example.service.smc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Premium and Discount Zones based on the LATEST major swing.
 * <p>
 * Uses the most recent swing high and swing low to define the range:
 * <ul>
 *   <li><b>Premium</b> (Sell Zone) — midpoint to swing high (top 50%)</li>
 *   <li><b>Discount</b> (Buy Zone) — swing low to midpoint (bottom 50%)</li>
 *   <li><b>Equilibrium</b> — the 50% midpoint level</li>
 * </ul>
 */
public class ZoneDetector {

    private BigDecimal swingHigh;
    private BigDecimal swingLow;
    private BigDecimal midpoint;
    private BigDecimal atr;

    public ZoneDetector detect(List<SwingPoint> swings, BigDecimal atr) {
        this.atr = atr;

        // Find latest swing high and low
        SwingPoint latestHigh = null;
        SwingPoint latestLow = null;
        for (int i = swings.size() - 1; i >= 0; i--) {
            SwingPoint sp = swings.get(i);
            if (sp.isHigh() && latestHigh == null) latestHigh = sp;
            if (!sp.isHigh() && latestLow == null) latestLow = sp;
            if (latestHigh != null && latestLow != null) break;
        }

        this.swingHigh = latestHigh != null ? latestHigh.price() : null;
        this.swingLow = latestLow != null ? latestLow.price() : null;

        if (swingHigh != null && swingLow != null) {
            this.midpoint = swingHigh.add(swingLow).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        }
        return this;
    }

    public BigDecimal getSwingHigh() { return swingHigh; }
    public BigDecimal getSwingLow() { return swingLow; }
    public BigDecimal getMidpoint() { return midpoint; }
    public BigDecimal getATR() { return atr; }

    /** Returns true if price is in the discount zone (below midpoint). */
    public boolean isDiscount(BigDecimal price) {
        return price != null && midpoint != null && price.compareTo(midpoint) <= 0;
    }

    /** Returns true if price is in the premium zone (above midpoint). */
    public boolean isPremium(BigDecimal price) {
        return price != null && midpoint != null && price.compareTo(midpoint) > 0;
    }

    /** Optimal trade entry: discount zone's upper boundary (just below midpoint). */
    public BigDecimal getOptimalEntry() {
        if (midpoint == null) return null;
        if (atr != null) return midpoint.subtract(atr.multiply(BigDecimal.valueOf(0.3)));
        return midpoint;
    }
}
