package org.example.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Smart Money Concepts (SMC/ICT) Pattern DTO.
 * <p>
 * Detected patterns include:
 * <ul>
 *   <li><b>BOS (Break of Structure)</b> — price breaks above/below a swing pivot</li>
 *   <li><b>CHoCH (Change of Character)</b> — trend reversal confirmation</li>
 *   <li><b>FVG (Fair Value Gap)</b> — 3-candle imbalance zone</li>
 *   <li><b>Liquidity Sweep</b> — price spikes into liquidity then reverses</li>
 *   <li><b>Order Blocks</b> — the candle before a strong impulse move</li>
 *   <li><b>Mitigation Blocks</b> — order blocks that have been revisited</li>
 *   <li><b>Premium & Discount Zones</b> — upper (sell) / lower (buy) half of range</li>
 *   <li><b>Inducement</b> — false breakout that traps retail traders</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SMCPatternDTO {

    private Long stockId;
    private String symbol;
    private LocalDate calculationDate;
    private int totalPatterns;

    // Existing
    private List<BOSEntry> breakOfStructure;
    private List<CHoCHEntry> changeOfCharacter;
    private List<FVGEntry> fairValueGaps;

    // New
    private List<LiquiditySweepEntry> liquiditySweeps;
    private List<OrderBlockEntry> orderBlocks;
    private PremiumDiscountZones premiumDiscountZones;
    private List<InducementEntry> inducements;

    // LuxAlgo additions
    private List<EqualHighLowEntry> equalHighsLows;
    private List<BOSEntry> internalBreakOfStructure;
    private List<CHoCHEntry> internalChangeOfCharacter;
    private List<OrderBlockEntry> internalOrderBlocks;
    private SwingHighLow swingHighLow;  // Strong/Weak High/Low labels

    // ── BOS ──────────────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BOSEntry {
        private LocalDate date;
        private BigDecimal price;
        private String direction;   // "BULLISH" or "BEARISH"
        private BigDecimal swingLevel;
        private String label;
    }

    // ── CHoCH ────────────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CHoCHEntry {
        private LocalDate date;
        private BigDecimal price;
        private String direction;
        private String label;
    }

    // ── FVG ──────────────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FVGEntry {
        private LocalDate date;
        private BigDecimal price;
        private BigDecimal topPrice;
        private BigDecimal bottomPrice;
        private String direction;
        private String label;
        private BigDecimal gapSize;
        private String state;           // "OPEN" | "PARTIALLY_FILLED" | "FILLED"
    }

    // ── Liquidity Sweep ──────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class LiquiditySweepEntry {
        private LocalDate date;
        private BigDecimal price;
        private String direction;       // "BULLISH" (swept below a low) or "BEARISH" (swept above a high)
        private BigDecimal sweptLevel;  // the swing level that was swept
        private String label;           // e.g. "LS ↑" or "LS ↓"
        private String poolType;        // "SINGLE_SWING" | "EQUAL_HIGH_POOL" | "EQUAL_LOW_POOL"
    }

    // ── Order Block ──────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OrderBlockEntry {
        private LocalDate date;
        private BigDecimal price;
        private BigDecimal topPrice;    // zone top
        private BigDecimal bottomPrice; // zone bottom
        private String direction;       // "BULLISH" or "BEARISH"
        private boolean mitigated;      // true if price revisited the OB zone (derived from mitigationState)
        private String label;           // "OB ↑" (unmitigated) or "MB ↑" (mitigated)
        private String mitigationState; // "UNTOUCHED" | "PARTIALLY_MITIGATED" | "FULLY_MITIGATED" | "INVALIDATED"
        private String score;           // "STRONG" | "MEDIUM" | "WEAK"
        /** If linked to a BOS, this holds the BOS date for traceability. */
        private LocalDate associatedBOSDate;
    }

    // ── Premium & Discount Zones ─────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PremiumDiscountZones {
        private BigDecimal periodHigh;   // highest high in the lookback
        private BigDecimal periodLow;    // lowest low in the lookback
        private BigDecimal midpoint;     // (high + low) / 2 — the 50% level
        private BigDecimal premiumStart; // midpoint to high (sell zone)
        private BigDecimal discountEnd;  // low to midpoint (buy zone)
        private String label;
    }

    // ── Inducement ───────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InducementEntry {
        private LocalDate date;
        private BigDecimal price;
        private String direction;       // "BULLISH" or "BEARISH"
        private BigDecimal inducedLevel; // the level that was falsely broken
        private String label;           // e.g. "IND ↑" or "IND ↓"
    }

    // ── Equal Highs / Equal Lows (LuxAlgo) ───────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class EqualHighLowEntry {
        private LocalDate date;
        private BigDecimal price;
        private boolean equalHigh;      // true = EQH, false = EQL
        private BigDecimal level;       // the matched swing level
        private String label;           // "EQH" or "EQL"
    }

    // ── Strong/Weak Highs and Lows (LuxAlgo trailing extremes) ───────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SwingHighLow {
        private BigDecimal strongHigh;    // trailing top (swing high) in bearish trend
        private BigDecimal strongLow;     // trailing bottom (swing low) in bullish trend
        private BigDecimal weakHigh;      // trailing top in bullish trend
        private BigDecimal weakLow;       // trailing bottom in bearish trend
        private String trendBias;         // "BULLISH" or "BEARISH"
        private String label;
    }
}
