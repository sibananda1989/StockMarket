package org.example.repository;

import org.example.entity.LevelType;
import org.example.entity.SupportResistanceLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupportResistanceLevelRepository extends JpaRepository<SupportResistanceLevel, Long> {

    List<SupportResistanceLevel> findByStockIdAndCalculationDateOrderByLevelTypeAscLevelOrderAsc(
            Long stockId, LocalDate calculationDate);

    List<SupportResistanceLevel> findByStockIdAndLevelTypeAndCalculationDateOrderByLevelOrderAsc(
            Long stockId, LevelType levelType, LocalDate calculationDate);

    @Query("SELECT sr FROM SupportResistanceLevel sr WHERE sr.stock.id = :stockId " +
           "AND sr.calculationDate = " +
           "(SELECT MAX(sr2.calculationDate) FROM SupportResistanceLevel sr2 " +
           "WHERE sr2.stock.id = :stockId AND sr2.calculationDate <= :asOfDate) " +
           "ORDER BY sr.levelType ASC, sr.levelOrder ASC")
    List<SupportResistanceLevel> findLatestLevelsAsOfDate(
            @Param("stockId") Long stockId, @Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT sr FROM SupportResistanceLevel sr WHERE sr.stock.id = :stockId " +
           "AND sr.calculationDate = " +
           "(SELECT MAX(sr2.calculationDate) FROM SupportResistanceLevel sr2 " +
           "WHERE sr2.stock.id = :stockId) " +
           "ORDER BY sr.levelType ASC, sr.levelOrder ASC")
    List<SupportResistanceLevel> findLatestLevels(@Param("stockId") Long stockId);

    List<SupportResistanceLevel> findByStockIdAndCalculationDateBetweenOrderByCalculationDateAsc(
            Long stockId, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT DISTINCT sr.calculationDate FROM SupportResistanceLevel sr " +
           "WHERE sr.stock.id = :stockId ORDER BY sr.calculationDate DESC")
    List<LocalDate> findDistinctCalculationDates(@Param("stockId") Long stockId);

    /**
     * Bulk delete of all S/R levels for a stock on a given date.
     * Uses an explicit @Modifying JPQL DELETE with flush+clear so any pending
     * INSERTs in the same transaction are flushed BEFORE the delete runs,
     * and the persistence context is cleared afterwards (preventing the
     * derived "select-then-delete-each" anti-pattern that leaves stale rows).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SupportResistanceLevel sr WHERE sr.stock.id = :stockId AND sr.calculationDate = :calculationDate")
    int deleteByStockIdAndCalculationDateBulk(
            @Param("stockId") Long stockId, @Param("calculationDate") LocalDate calculationDate);

    @Modifying
    @Query("DELETE FROM SupportResistanceLevel sr WHERE sr.stock.id = :stockId")
    int deleteByStockId(@Param("stockId") Long stockId);
    
    @Query("SELECT sr FROM SupportResistanceLevel sr WHERE sr.stock.id IN :stockIds " +
           "AND sr.calculationDate = (" +
           "    SELECT MAX(sr2.calculationDate) FROM SupportResistanceLevel sr2 " +
           "    WHERE sr2.stock.id = sr.stock.id" +
           ") ORDER BY sr.stock.id")
    List<SupportResistanceLevel> findLatestByStockIds(@Param("stockIds") List<Long> stockIds);
}
