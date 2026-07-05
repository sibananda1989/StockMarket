package org.example.repository;

import org.example.entity.StrategyConditionStatsCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategyConditionStatsCacheRepository extends JpaRepository<StrategyConditionStatsCache, Long> {

    Optional<StrategyConditionStatsCache> findByStrategyNameAndConditionId(String strategyName, String conditionId);

    List<StrategyConditionStatsCache> findByStrategyName(String strategyName);

    void deleteByStrategyName(String strategyName);
}
