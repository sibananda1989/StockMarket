package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "strategy_daily_weight",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "strategy_name", "signal_date"}),
       indexes = {
           @Index(name = "idx_strategy_daily_stock_date", columnList = "stock_id, signal_date DESC"),
           @Index(name = "idx_strategy_daily_date", columnList = "signal_date DESC")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyDailyWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "strategy_name", nullable = false, length = 50)
    private String strategyName;

    @Column(name = "signal_date", nullable = false)
    private LocalDate signalDate;

    @Column(name = "signal_type", nullable = false, length = 10)
    private String signalType;  // BUY, SELL, HOLD

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private double contribution;

    @Column(nullable = false)
    private int priority;

    @Column(length = 500)
    private String reason;

    @Column(name = "config_version", nullable = false)
    private long configVersion;

    @Column(name = "latest_volume")
    private Long latestVolume;

    @Column(name = "avg_volume")
    private Double avgVolume;

    @Column(name = "spike_threshold")
    private Double spikeThreshold;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
