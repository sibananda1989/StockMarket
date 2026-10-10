package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shadow_signal_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "recorded_at", "shadow_version"}),
        indexes = @Index(name = "idx_shadow_signal_stock_date_version", columnList = "stock_id, recorded_at DESC, shadow_version"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShadowSignalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @Column(name = "shadow_version", nullable = false, length = 20)
    private String shadowVersion;

    @Column(name = "recommendation", nullable = false, length = 20)
    private String recommendation;

    @Column(name = "composite_score", nullable = false)
    private int compositeScore;

    @Column(name = "confidence_score")
    private Long confidenceScore;

    @Column(name = "indicator_coverage")
    private int indicatorCoverage;

    @Column(name = "price_at_signal", precision = 14, scale = 2)
    private BigDecimal priceAtSignal;

    @Column(name = "rsi_14", precision = 10, scale = 4)
    private BigDecimal rsi14;

    @Column(name = "sma_20", precision = 14, scale = 4)
    private BigDecimal sma20;

    @Column(name = "sma_50", precision = 14, scale = 4)
    private BigDecimal sma50;

    @Column(name = "macd", precision = 14, scale = 4)
    private BigDecimal macd;

    @Column(name = "macd_signal", precision = 14, scale = 4)
    private BigDecimal macdSignal;

    @Column(name = "macd_histogram", precision = 14, scale = 4)
    private BigDecimal macdHistogram;

    @Column(name = "bollinger_upper", precision = 14, scale = 4)
    private BigDecimal bollingerUpper;

    @Column(name = "bollinger_lower", precision = 14, scale = 4)
    private BigDecimal bollingerLower;

    @Column(name = "divergence_score")
    private int divergenceScore;

    @Column(name = "weekly_confluence_score")
    private int weeklyConfluenceScore;

    @Column(name = "monthly_confluence_score")
    private int monthlyConfluenceScore;

    @Column(name = "rsi_score")
    private int rsiScore;

    @Column(name = "bollinger_score")
    private int bollingerScore;

    @Column(name = "sma_score")
    private int smaScore;

    @Column(name = "week52_score")
    private int week52Score;

    @Column(name = "macd_score")
    private int macdScore;

    @Column(name = "stoch_score")
    private int stochScore;

    @Column(name = "adx_filter_applied")
    private int adxFilterApplied;

    @Column(name = "stoch_rsi_score")
    private int stochRsiScore;

    @Column(name = "ultimate_osc_score")
    private int ultimateOscScore;

    @Column(name = "roc_score")
    private int rocScore;

    @Column(name = "williams_r_score")
    private int williamsRScore;

    @Column(name = "cci_score")
    private int cciScore;

    @Column(name = "sr_proximity_score")
    private int srProximityScore;

    @Column(name = "vwap_score")
    private int vwapScore;

    @Column(name = "ichimoku_score")
    private int ichimokuScore;

    @Column(name = "trend_direction_score")
    private int trendDirectionScore;

    @Column(name = "breakout_score")
    private int breakoutScore;

    @Column(name = "candlestick_score")
    private int candlestickScore;

    @Column(name = "reversal_score")
    private int reversalScore;

    @Column(name = "raw_trend_score")
    private int rawTrendScore;

    @Column(name = "raw_momentum_score")
    private int rawMomentumScore;

    @Column(name = "raw_structure_score")
    private int rawStructureScore;

    @Column(name = "adx_multiplier", precision = 10, scale = 4)
    private BigDecimal adxMultiplier;

    @Column(name = "score_after_adx")
    private int scoreAfterAdx;

    @Column(name = "score_after_candlestick")
    private int scoreAfterCandlestick;

    @Column(name = "score_after_reversal")
    private int scoreAfterReversal;

    @Column(name = "score_after_discount")
    private int scoreAfterDiscount;

    @Column(name = "gate_blocked")
    private boolean gateBlocked;

    @OneToMany(mappedBy = "shadowSignalRecord", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    private List<ShadowSignalGateBlockReason> gateBlockReasons = new ArrayList<>();

    @Column(name = "is_divergent")
    private Boolean isDivergent;

    @Column(name = "divergence_reason", length = 255)
    private String divergenceReason;
}