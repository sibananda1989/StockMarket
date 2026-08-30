package org.example.service.smc;

import java.util.List;

/**
 * Determines the current market structure from a sequence of swing points.
 * <p>
 * Rules:
 * <ul>
 *   <li><b>BULLISH</b> — last 2 swing highs are HH and last 2 swing lows are HL</li>
 *   <li><b>BEARISH</b> — last 2 swing highs are LH and last 2 swing lows are LL</li>
 *   <li><b>SIDEWAYS</b> — mixed or insufficient swings</li>
 * </ul>
 */
public enum MarketStructure {
    BULLISH,
    BEARISH,
    SIDEWAYS;

    /** Detect current market structure from the ordered swing list. */
    public static MarketStructure detect(List<SwingPoint> swings) {
        SwingPoint lastHigh = null, secondLastHigh = null;
        SwingPoint lastLow = null, secondLastLow = null;

        for (int i = swings.size() - 1; i >= 0; i--) {
            SwingPoint sp = swings.get(i);
            if (sp.isHigh()) {
                if (lastHigh == null) lastHigh = sp;
                else if (secondLastHigh == null) secondLastHigh = sp;
            } else {
                if (lastLow == null) lastLow = sp;
                else if (secondLastLow == null) secondLastLow = sp;
            }
            if (secondLastHigh != null && secondLastLow != null) break;
        }

        boolean bullishHighs = secondLastHigh != null && lastHigh != null
                && "HH".equals(lastHigh.label()) && "HH".equals(secondLastHigh.label());
        boolean bullishLows = secondLastLow != null && lastLow != null
                && "HL".equals(lastLow.label()) && "HL".equals(secondLastLow.label());

        boolean bearishHighs = secondLastHigh != null && lastHigh != null
                && "LH".equals(lastHigh.label()) && "LH".equals(secondLastHigh.label());
        boolean bearishLows = secondLastLow != null && lastLow != null
                && "LL".equals(lastLow.label()) && "LL".equals(secondLastLow.label());

        if (bullishHighs && bullishLows) return BULLISH;
        if (bearishHighs && bearishLows) return BEARISH;
        return SIDEWAYS;
    }

    /** Get the latest sequence description like "HH HL HH HL". */
    public static String describe(List<SwingPoint> swings) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = swings.size() - 1; i >= 0 && count < 6; i--) {
            if (sb.length() > 0) sb.insert(0, " ");
            sb.insert(0, swings.get(i).label());
            count++;
        }
        return sb.toString();
    }
}
