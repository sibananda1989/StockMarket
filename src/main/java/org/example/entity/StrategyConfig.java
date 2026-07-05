package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "strategy_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_name", nullable = false, unique = true, length = 50)
    private String strategyName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "priority")
    private int priority;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public StrategyConfig(String strategyName, boolean active, String displayName, int priority) {
        this.strategyName = strategyName;
        this.active = active;
        this.displayName = displayName;
        this.priority = priority;
    }
}
