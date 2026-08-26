package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_daily_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "value_date"}),
        indexes = {
            @Index(name = "idx_pdv_portfolio_date", columnList = "portfolio_id, value_date DESC"),
            @Index(name = "idx_pdv_date", columnList = "value_date DESC")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioDailyValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "total_investment", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalInvestment;

    @Column(name = "total_current_value", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCurrentValue;

    @Column(name = "total_pnl", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalPnl;

    @Column(name = "total_pnl_percent", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalPnlPercent;

    @Column(name = "holdings_count", nullable = false)
    private Integer holdingsCount;

    @JsonProperty("portfolioId")
    public Long getPortfolioId() {
        return portfolio != null ? portfolio.getId() : null;
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PortfolioDailyValue(Portfolio portfolio, LocalDate valueDate,
                               BigDecimal totalInvestment, BigDecimal totalCurrentValue,
                               BigDecimal totalPnl, BigDecimal totalPnlPercent,
                               Integer holdingsCount) {
        this.portfolio = portfolio;
        this.valueDate = valueDate;
        this.totalInvestment = totalInvestment;
        this.totalCurrentValue = totalCurrentValue;
        this.totalPnl = totalPnl;
        this.totalPnlPercent = totalPnlPercent;
        this.holdingsCount = holdingsCount;
    }
}
