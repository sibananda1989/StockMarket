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
@Table(name = "daily_prices", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "price_date"}),
       indexes = @Index(name = "idx_stock_date", columnList = "stock_id, price_date DESC"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    @NotNull(message = "Stock is required")
    private Stock stock;

    @NotNull(message = "Closing price is required")
    @DecimalMin(value = "0.01", message = "Closing price must be greater than 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal closingPrice;

    @DecimalMin(value = "0.01", message = "Opening price must be greater than 0")
    @Column(precision = 10, scale = 2)
    private BigDecimal openingPrice;

    @DecimalMin(value = "0.01", message = "High price must be greater than 0")
    @Column(precision = 10, scale = 2)
    private BigDecimal highPrice;

    @DecimalMin(value = "0.01", message = "Low price must be greater than 0")
    @Column(precision = 10, scale = 2)
    private BigDecimal lowPrice;

    @Min(value = 0, message = "Volume must be non-negative")
    @Column
    private Long volume;

    @NotNull(message = "Price date is required")
    @PastOrPresent(message = "Price date cannot be in the future")
    @Column(nullable = false)
    private LocalDate priceDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public DailyPrice(Stock stock, BigDecimal closingPrice, LocalDate priceDate) {
        this.stock = stock;
        this.closingPrice = closingPrice;
        this.priceDate = priceDate;
    }

    public DailyPrice(Stock stock, BigDecimal closingPrice, BigDecimal openingPrice, 
                      BigDecimal highPrice, BigDecimal lowPrice, Long volume, LocalDate priceDate) {
        this.stock = stock;
        this.closingPrice = closingPrice;
        this.openingPrice = openingPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
        this.priceDate = priceDate;
    }
}
