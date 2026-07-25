package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Liquidity score data transfer object.
 * Provides a composite liquidity assessment of a stock to the frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiquidityScoreDTO {
    /** Composite liquidity score 0-100 */
    private int compositeScore;

    /** Amihud illiquidity sub-score 0-10 (higher = more liquid) */
    private int amihudScore;

    /** Dollar volume sub-score 0-10 (higher = more liquid) */
    private int dollarVolumeScore;

    /** Turnover ratio sub-score 0-10 (higher = more liquid) */
    private int turnoverScore;

    /** Volume stability sub-score 0-10 (higher = more stable/liquid) */
    private int stabilityScore;

    /** Human-readable label: VERY_HIGH, HIGH, MEDIUM, LOW, VERY_LOW */
    private String label;

    /** CSS-compatible color hex for badge rendering */
    private String color;

    /** Raw Amihud illiquidity value (scientific notation) for tooltip */
    private String amihudRaw;

    /** Average daily turnover percentage for tooltip */
    private Double avgTurnoverPct;

    /** Average daily dollar volume in rupees for tooltip */
    private Long avgDollarVolume;
}
