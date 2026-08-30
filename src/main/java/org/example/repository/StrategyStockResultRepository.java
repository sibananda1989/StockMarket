package org.example.repository;

import org.example.entity.StrategyStockResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StrategyStockResultRepository extends JpaRepository<StrategyStockResult, Long> {

    List<StrategyStockResult> findAllBySnapshotDate(LocalDate date);

    List<StrategyStockResult> findAllByStrategyNameAndSnapshotDate(String strategyName, LocalDate date);

    void deleteBySnapshotDate(LocalDate date);

    /**
     * Latest snapshot day. Returns null when the table is empty — callers must null-guard.
     */
    @Query("SELECT MAX(s.snapshotDate) FROM StrategyStockResult s")
    LocalDate findMaxSnapshotDate();
}
