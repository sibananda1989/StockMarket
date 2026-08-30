package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Links a stock to a portfolio with position details (quantity, avg price).
 * Replaces the portfolio fields that were previously on the Stock entity.
 *
 * Computed fields (investment, currentValue, pnl, pnlPercent) are calculated
 * on read via PortfolioService — they are not persisted here.
 */
@Entity
@Table(name = "portfolio_holdings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "stock_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal avgPrice;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Timestamp of the last manual edit via PATCH /holdings/{id}.
     * Used by the healer to skip auto-heal for user-overridden positions.
     */
    @Column(name = "last_manual_edit_at")
    private LocalDateTime lastManualEditAt;

    // ── Computed fields (not persisted) ──────────────────────────────────

    @Transient
    private BigDecimal investment;

    @Transient
    private BigDecimal currentValue;

    @Transient
    private BigDecimal pnl;

    @Transient
    private BigDecimal pnlPercent;

    @Transient
    private BigDecimal lastTradedPrice;
}
