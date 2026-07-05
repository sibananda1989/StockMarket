package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "signal_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "recorded_at"}),
        indexes = @Index(name = "idx_signal_record_stock_date", columnList = "stock_id, recorded_at DESC"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

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

    @Column(name = "forward_return_5d", precision = 10, scale = 4)
    private BigDecimal forwardReturn5d;

    @Column(name = "forward_return_10d", precision = 10, scale = 4)
    private BigDecimal forwardReturn10d;

    @Column(name = "forward_return_20d", precision = 10, scale = 4)
    private BigDecimal forwardReturn20d;

    @Column(name = "was_accurate_5d")
    private Boolean wasAccurate5d;

    @Column(name = "was_accurate_10d")
    private Boolean wasAccurate10d;

    @Column(name = "was_accurate_20d")
    private Boolean wasAccurate20d;
}
