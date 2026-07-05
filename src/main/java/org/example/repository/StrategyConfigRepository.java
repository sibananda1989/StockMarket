package org.example.repository;

import org.example.entity.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {

    Optional<StrategyConfig> findByStrategyName(String name);

    List<StrategyConfig> findAllByOrderByStrategyNameAsc();
}
