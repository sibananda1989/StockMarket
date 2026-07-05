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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "strategy_condition_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConditionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_name", nullable = false, length = 50)
    private String strategyName;

    @Column(name = "condition_id", nullable = false, length = 80)
    private String conditionId;

    @Column(name = "signal_type", nullable = false, length = 10)
    private String signalType;

    @Column(name = "field_label", nullable = false, length = 100)
    private String fieldLabel;

    @Column(nullable = false, length = 30)
    private String operator;

    @Column(name = "threshold_value", precision = 10, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_value2", precision = 10, scale = 4)
    private BigDecimal thresholdValue2;

    @Column(nullable = false, length = 10)
    private String confidence;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
