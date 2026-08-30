package org.example.repository;

import org.example.entity.SignalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SignalRecordRepository extends JpaRepository<SignalRecord, Long> {

    Optional<SignalRecord> findByStockIdAndRecordedAt(Long stockId, LocalDate recordedAt);

    List<SignalRecord> findByStockIdOrderByRecordedAtDesc(Long stockId);

    @Query("SELECT sr FROM SignalRecord sr WHERE sr.stockId = :stockId AND sr.recordedAt >= :since ORDER BY sr.recordedAt DESC")
    List<SignalRecord> findRecentByStockId(@Param("stockId") Long stockId, @Param("since") LocalDate since);

    @Query("SELECT sr FROM SignalRecord sr WHERE sr.forwardReturn5d IS NULL AND sr.recordedAt <= :cutoffDate")
    List<SignalRecord> findUnmarkedRecordsOlderThan(@Param("cutoffDate") LocalDate cutoffDate);

    @Modifying
    @Query("DELETE FROM SignalRecord sr WHERE sr.stockId = :stockId")
    int deleteByStockId(@Param("stockId") Long stockId);

    @Query("SELECT COUNT(DISTINCT sr.stockId) FROM SignalRecord sr")
    long countDistinctStocks();
}
