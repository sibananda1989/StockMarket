package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "signal_historical_performance",
       indexes = @Index(name = "idx_shp_rec_days", columnList = "recommendation, days_forward"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalHistoricalPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation", nullable = false, length = 20)
    private String recommendation;

    @Column(name = "days_forward", nullable = false)
    private Integer daysForward;

    @Column(name = "avg_return", precision = 10, scale = 4)
    private BigDecimal avgReturn;

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "last_updated", nullable = false)
    private LocalDate lastUpdated;
}
