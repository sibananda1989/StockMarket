package org.example.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_resistance_levels",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "level_type", "calculation_date", "level_order"}),
       indexes = {
           @Index(name = "idx_sr_stock_date", columnList = "stock_id, calculation_date DESC"),
           @Index(name = "idx_sr_stock_type", columnList = "stock_id, level_type")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportResistanceLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    @NotNull(message = "Stock is required")
    private Stock stock;

    @NotNull(message = "Level type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "level_type", nullable = false, length = 30)
    private LevelType levelType;

    @NotNull(message = "Level value is required")
    @DecimalMin(value = "0.01", message = "Level value must be greater than 0")
    @Column(name = "level_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal levelValue;

    @Column(name = "level_order", nullable = false)
    private int levelOrder = 0;

    @NotNull(message = "Calculation date is required")
    @Column(name = "calculation_date", nullable = false)
    private LocalDate calculationDate;

    @Column(name = "strength_score", precision = 5, scale = 2)
    private BigDecimal strengthScore;

    @Column(name = "touch_count")
    private Integer touchCount;

    @Column(name = "lookback_days")
    private Integer lookbackDays;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
