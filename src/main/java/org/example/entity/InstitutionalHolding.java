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
@Table(name = "institutional_holdings",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"stock_id", "quarter_end_date"})
       },
       indexes = @Index(name = "idx_inst_hold_stock_date", columnList = "stock_id, quarter_end_date DESC"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionalHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "quarter_end_date", nullable = false)
    private LocalDate quarterEndDate;

    // --- Raw percentages from source data ---

    @Column(name = "promoter_holding_pct", precision = 8, scale = 4)
    private BigDecimal promoterHoldingPct;

    @Column(name = "fii_holding_pct", precision = 8, scale = 4)
    private BigDecimal fiiHoldingPct;

    @Column(name = "dii_holding_pct", precision = 8, scale = 4)
    private BigDecimal diiHoldingPct;

    @Column(name = "mutual_fund_holding_pct", precision = 8, scale = 4)
    private BigDecimal mutualFundHoldingPct;

    @Column(name = "insurance_holding_pct", precision = 8, scale = 4)
    private BigDecimal insuranceHoldingPct;

    @Column(name = "public_holding_pct", precision = 8, scale = 4)
    private BigDecimal publicHoldingPct;

    @Column(name = "total_shares")
    private Long totalShares;

    // --- Derived QoQ changes (computed at fetch time) ---

    @Column(name = "promoter_change_qoq", precision = 8, scale = 4)
    private BigDecimal promoterChangeQoq;

    @Column(name = "fii_change_qoq", precision = 8, scale = 4)
    private BigDecimal fiiChangeQoq;

    @Column(name = "dii_change_qoq", precision = 8, scale = 4)
    private BigDecimal diiChangeQoq;

    @Column(name = "mutual_fund_change_qoq", precision = 8, scale = 4)
    private BigDecimal mutualFundChangeQoq;

    // --- Metadata ---

    @Column(name = "data_source", length = 20)
    private String dataSource;  // "NSE_XBRL", "NSE_API", "COMPUTED"

    @Column(name = "filing_date")
    private LocalDate filingDate;

    @Column(name = "fetched_date")
    private LocalDate fetchedDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
