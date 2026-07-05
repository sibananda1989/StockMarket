package org.example.repository;

import org.example.entity.ShadowSignalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShadowSignalRecordRepository extends JpaRepository<ShadowSignalRecord, Long> {
    
    Optional<ShadowSignalRecord> findByStockIdAndRecordedAtAndShadowVersion(Long stockId, LocalDate recordedAt, String shadowVersion);
    
    List<ShadowSignalRecord> findByStockIdAndRecordedAtBetween(Long stockId, LocalDate startDate, LocalDate endDate);
    
    List<ShadowSignalRecord> findByRecordedAtAndShadowVersion(LocalDate recordedAt, String shadowVersion);
    
    @Query("SELECT s FROM ShadowSignalRecord s WHERE s.recordedAt = :recordedAt AND s.shadowVersion = :shadowVersion AND s.isDivergent = true")
    List<ShadowSignalRecord> findDivergentSignals(@Param("recordedAt") LocalDate recordedAt, @Param("shadowVersion") String shadowVersion);
    
    @Query("SELECT COUNT(s) FROM ShadowSignalRecord s WHERE s.recordedAt = :recordedAt AND s.shadowVersion = :shadowVersion AND s.isDivergent = true")
    long countDivergentSignals(@Param("recordedAt") LocalDate recordedAt, @Param("shadowVersion") String shadowVersion);
    
    @Query("SELECT s.stockId, COUNT(s) FROM ShadowSignalRecord s WHERE s.recordedAt BETWEEN :startDate AND :endDate AND s.shadowVersion = :shadowVersion AND s.isDivergent = true GROUP BY s.stockId")
    List<Object[]> countDivergentSignalsByStock(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("shadowVersion") String shadowVersion);
}