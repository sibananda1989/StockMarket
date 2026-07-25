package org.example.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Symbol is required")
    @Size(max = 20, message = "Symbol must be at most 20 characters")
    @Column(unique = true, nullable = false, length = 20)
    private String symbol;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @Size(max = 100, message = "Sector must be at most 100 characters")
    @Column(length = 100)
    private String sector;

    @Size(max = 100, message = "Industry must be at most 100 characters")
    @Column(length = 100)
    private String industry;

    @Size(max = 20, message = "Yahoo symbol must be at most 20 characters")
    @Column(name = "yahoo_symbol", length = 20)
    private String yahooSymbol;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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

    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DailyPrice> dailyPrices = new ArrayList<>();

    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TechnicalIndicator> technicalIndicators = new ArrayList<>();

    public Stock(String symbol, String name, String sector) {
        this(symbol, name, sector, null);
    }

    public Stock(String symbol, String name, String sector, String industry) {
        this.symbol = symbol;
        this.name = name;
        this.sector = sector;
        this.industry = industry;
    }
}
