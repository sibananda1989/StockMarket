package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fundamental_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false, unique = true)
    private Stock stock;

    @Column(precision = 10, scale = 2)
    private BigDecimal peRatio;

    @Column(precision = 10, scale = 2)
    private BigDecimal forwardPe;

    @Column(precision = 10, scale = 2)
    private BigDecimal epsTtm;

    @Column(precision = 10, scale = 2)
    private BigDecimal epsForward;

    @Column(precision = 10, scale = 2)
    private BigDecimal bookValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceToBook;

    @Column(precision = 8, scale = 4)
    private BigDecimal dividendYield;

    @Column(precision = 8, scale = 4)
    private BigDecimal roe;

    @Column(precision = 10, scale = 4)
    private BigDecimal debtToEquity;

    @Column(precision = 8, scale = 4)
    private BigDecimal profitMargin;

    @Column
    private Long revenueTtm;

    @Column(length = 100)
    private String sector;

    @Column(length = 100)
    private String industry;

    @Column(columnDefinition = "TEXT")
    private String businessSummary;

    @Column
    private Long sharesOutstanding;

    @Column(precision = 8, scale = 4)
    private BigDecimal beta;

    @Column(precision = 10, scale = 2)
    private BigDecimal fiftyTwoWeekHigh;

    @Column(precision = 10, scale = 2)
    private BigDecimal fiftyTwoWeekLow;

    @Column(nullable = false)
    private LocalDate fetchedDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
