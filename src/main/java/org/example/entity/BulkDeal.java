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
@Table(name = "bulk_deals",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"stock_id", "deal_date", "client_name", "buy_sell", "quantity", "trade_price"})
       },
       indexes = @Index(name = "idx_bulk_deal_screener", columnList = "buy_sell, is_institutional, deal_date"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "deal_date", nullable = false)
    private LocalDate dealDate;

    @Column(name = "client_name", length = 255, nullable = false)
    private String clientName;

    @Column(name = "buy_sell", length = 4, nullable = false)
    private String buySell;  // "BUY" or "SELL"

    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "trade_price", precision = 12, scale = 2)
    private BigDecimal tradePrice;

    @Column(name = "remarks", length = 255)
    private String remarks;

    // ---- Computed classification ----

    @Column(name = "client_category", length = 20)
    private String clientCategory;  // "FII", "DII", "MF", "INSURANCE", "PROMOTER", "RETAIL", "UNKNOWN"

    @Column(name = "is_institutional")
    private Boolean isInstitutional;

    @Column(name = "deal_value", precision = 18, scale = 2)
    private BigDecimal dealValue;  // quantity * tradePrice

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (dealValue == null && quantity != null && tradePrice != null) {
            dealValue = BigDecimal.valueOf(quantity).multiply(tradePrice);
        }
    }
}
