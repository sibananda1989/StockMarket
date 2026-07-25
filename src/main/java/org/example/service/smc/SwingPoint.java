package org.example.service.smc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A significant swing point detected from daily price data.
 * <p>
 * Classification:
 * <ul>
 *   <li><b>HH</b> (Higher High) — swing high higher than previous swing high</li>
 *   <li><b>HL</b> (Higher Low) — swing low higher than previous swing low</li>
 *   <li><b>LH</b> (Lower High) — swing high lower than previous swing high</li>
 *   <li><b>LL</b> (Lower Low) — swing low lower than previous swing low</li>
 * </ul>
 */
public record SwingPoint(
    int index,          // index in the price array
    BigDecimal price,   // price level (high for swing high, low for swing low)
    LocalDate date,     // candle date
    boolean isHigh,     // true = swing high, false = swing low
    BigDecimal strength, // ATR multiple (how many ATRs this swing is from prior)
    String label        // "HH" | "HL" | "LH" | "LL" | "NEW" (first detected)
) {}
