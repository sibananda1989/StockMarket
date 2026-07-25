package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "score_parameter_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreParameterConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "param_key", nullable = false, unique = true, length = 60)
    private String paramKey;

    @Column(nullable = false, length = 30)
    private String category; // TREND / MOMENTUM / STRUCTURE / MODIFIERS / POST_SCORE / ENGINE

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "score_system", nullable = false, length = 20)
    private String scoreSystem; // LEGACY or ENGINE
}
