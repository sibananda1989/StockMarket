package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks which startup tasks have been executed on a given day.
 * Used to determine whether the startup task popup should be shown.
 */
@Entity
@Table(name = "startup_task_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartupTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
