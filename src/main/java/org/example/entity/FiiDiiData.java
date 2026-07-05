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
@Table(name = "fiidii_data", uniqueConstraints = @UniqueConstraint(columnNames = "date"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FiiDiiData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "fii_buy", precision = 18, scale = 4)
    private BigDecimal fiiBuy;

    @Column(name = "fii_sell", precision = 18, scale = 4)
    private BigDecimal fiiSell;

    @Column(name = "fii_net", precision = 18, scale = 4)
    private BigDecimal fiiNet;

    @Column(name = "dii_buy", precision = 18, scale = 4)
    private BigDecimal diiBuy;

    @Column(name = "dii_sell", precision = 18, scale = 4)
    private BigDecimal diiSell;

    @Column(name = "dii_net", precision = 18, scale = 4)
    private BigDecimal diiNet;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
