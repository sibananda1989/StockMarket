package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "strategy_condition_stats_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConditionStatsCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_name", nullable = false, length = 50)
    private String strategyName;

    @Column(name = "condition_id", nullable = false, length = 80)
    private String conditionId;

    @Column(name = "stock_count", nullable = false)
    private int stockCount;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
