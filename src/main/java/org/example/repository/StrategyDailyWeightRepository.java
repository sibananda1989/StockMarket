package org.example.repository;

import org.example.entity.StrategyDailyWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StrategyDailyWeightRepository extends JpaRepository<StrategyDailyWeight, Long> {

    List<StrategyDailyWeight> findByStockIdAndSignalDateBetweenOrderBySignalDateAsc(
        Long stockId, LocalDate startDate, LocalDate endDate);

    List<StrategyDailyWeight> findByStockIdAndSignalDateAfterOrderBySignalDateAsc(
        Long stockId, LocalDate signalDate);

    List<StrategyDailyWeight> findByStockIdAndStrategyNameAndSignalDateBetweenOrderBySignalDateAsc(
        Long stockId, String strategyName, LocalDate startDate, LocalDate endDate);

    @Query("SELECT DISTINCT sdw.signalDate FROM StrategyDailyWeight sdw " +
           "WHERE sdw.stock.id = :stockId AND sdw.signalDate >= :startDate " +
           "ORDER BY sdw.signalDate ASC")
    List<LocalDate> findDistinctDatesByStockIdAndDateAfter(@Param("stockId") Long stockId,
                                                            @Param("startDate") LocalDate startDate);

    void deleteByStockIdAndSignalDateBetween(Long stockId, LocalDate startDate, LocalDate endDate);

    void deleteByStockId(Long stockId);

    @Query("SELECT COUNT(sdw) FROM StrategyDailyWeight sdw WHERE sdw.stock.id = :stockId")
    long countByStockId(@Param("stockId") Long stockId);
}
