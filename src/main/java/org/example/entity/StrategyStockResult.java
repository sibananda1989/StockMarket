package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-stock per-strategy per-day snapshot of the multi-strategy engine output.
 * One snapshot per day; history accumulates across days. A refresh overwrites
 * only the current day's rows (same-day overwrite).
 */
@Entity
@Table(name = "strategy_stock_result",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "strategy_name", "snapshot_date"}),
       indexes = @Index(name = "idx_strategy_snapshot", columnList = "snapshot_date,strategy_name,signal_type"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategyStockResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "strategy_name", nullable = false, length = 50)
    private String strategyName;

    @Column(name = "signal_type", nullable = false, length = 20)
    private String signalType;  // BUY, SELL, HOLD

    @Column(columnDefinition = "TEXT")
    private String reason;

    private Double confidence;

    private Integer priority;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}
