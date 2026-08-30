package org.example.entity;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single buy or sell trade in a portfolio, stored as an immutable ledger row.
 * Holdings (quantity + avg cost) and realized P&L are derived from these rows.
 * References Portfolio and Stock directly (not the holding) so it survives holding deletion.
 */
@Entity
@Table(name = "portfolio_transactions", indexes = {
        @Index(name = "idx_txn_portfolio_stock_date", columnList = "portfolio_id, stock_id, transaction_date, id"),
        @Index(name = "idx_txn_linked_buy", columnList = "linked_buy_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * For SELL rows: the BUY transaction (lot) this sell consumes quantity from.
     * Null for BUY rows. A SELL always maps to exactly one BUY lot; its
     * {@code quantity} is the amount taken from that specific lot.
     */
    @Column(name = "linked_buy_id")
    private Long linkedBuyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "fees", precision = 10, scale = 2)
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(name = "realized_pnl", precision = 14, scale = 2)
    private BigDecimal realizedPnl;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate = LocalDate.now();

    @Column(name = "notes", length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
