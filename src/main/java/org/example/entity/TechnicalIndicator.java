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
@Table(name = "technical_indicators", 
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "indicatorType", "calculation_date"}),
        indexes = @Index(name = "idx_stock_indicator_date", columnList = "stock_id, indicatorType, calculation_date DESC"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    @NotNull(message = "Stock is required")
    private Stock stock;

    @NotNull(message = "Indicator type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IndicatorType indicatorType;

    @NotNull(message = "Indicator value is required")
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal value;

    @NotNull(message = "Calculation date is required")
    @PastOrPresent(message = "Calculation date cannot be in the future")
    @Column(name = "calculation_date", nullable = false)
    private LocalDate calculationDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public TechnicalIndicator(Stock stock, IndicatorType indicatorType, BigDecimal value, LocalDate calculationDate) {
        this.stock = stock;
        this.indicatorType = indicatorType;
        this.value = value;
        this.calculationDate = calculationDate;
    }
}