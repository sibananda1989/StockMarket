package org.example.repository;

import org.example.entity.ScoreParameterConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScoreParameterConfigRepository extends JpaRepository<ScoreParameterConfig, Long> {

    Optional<ScoreParameterConfig> findByParamKey(String paramKey);

    List<ScoreParameterConfig> findByScoreSystem(String scoreSystem);

    List<ScoreParameterConfig> findByEnabledFalse();
}
