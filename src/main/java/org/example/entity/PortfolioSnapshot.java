package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_snapshots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "stock_id", "snapshot_date"}),
       indexes = {
           @Index(name = "idx_snapshot_stock_date", columnList = "stock_id, snapshot_date DESC"),
           @Index(name = "idx_snapshot_portfolio_date", columnList = "portfolio_id, snapshot_date")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column
    private Integer quantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal avgPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal lastTradedPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal investment;

    @Column(precision = 12, scale = 2)
    private BigDecimal currentValue;

    @Column(precision = 12, scale = 2)
    private BigDecimal pnl;

    @Column(precision = 8, scale = 2)
    private BigDecimal pnlPercent;

    @Column
    private Long volume;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
