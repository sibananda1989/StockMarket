package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "corporate_events", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "event_date", "purpose"}),
       indexes = @Index(name = "idx_event_symbol_date", columnList = "symbol, event_date"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorporateEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(nullable = false, length = 255)
    private String purpose;

    @Column(name = "bm_desc", columnDefinition = "TEXT")
    private String bmDesc;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
