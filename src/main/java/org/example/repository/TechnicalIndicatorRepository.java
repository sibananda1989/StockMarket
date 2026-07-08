package org.example.repository;

import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TechnicalIndicatorRepository extends JpaRepository<TechnicalIndicator, Long> {

    List<TechnicalIndicator> findByStockIdAndIndicatorTypeOrderByCalculationDateDesc(Long stockId, IndicatorType type);

    Optional<TechnicalIndicator> findFirstByStockIdAndIndicatorTypeOrderByCalculationDateDesc(Long stockId, IndicatorType type);

    List<TechnicalIndicator> findByStockIdAndIndicatorTypeInOrderByCalculationDateDesc(Long stockId, List<IndicatorType> types);

    boolean existsByStockIdAndIndicatorTypeAndCalculationDate(Long stockId, IndicatorType type, LocalDate date);

    Optional<TechnicalIndicator> findByStockIdAndIndicatorTypeAndCalculationDate(Long stockId, IndicatorType type, LocalDate date);

    long countByStockIdAndCalculationDate(Long stockId, LocalDate date);

    @Query(value = "SELECT ti.* FROM technical_indicators ti INNER JOIN " +
            "(SELECT indicator_type, MAX(calculation_date) AS max_date FROM technical_indicators WHERE stock_id = :stockId GROUP BY indicator_type) latest " +
            "ON ti.indicator_type = latest.indicator_type AND ti.calculation_date = latest.max_date WHERE ti.stock_id = :stockId", nativeQuery = true)
    List<TechnicalIndicator> findLatestForStock(@Param("stockId") Long stockId);
    
    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.stock.id = :stockId AND ti.calculationDate = :date")
    List<TechnicalIndicator> findByStockIdAndCalculationDate(@Param("stockId") Long stockId, @Param("date") LocalDate date);

    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.stock.id = :stockId AND ti.calculationDate BETWEEN :fromDate AND :toDate ORDER BY ti.calculationDate ASC")
    List<TechnicalIndicator> findByStockIdAndCalculationDateBetween(@Param("stockId") Long stockId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query(value = "SELECT DISTINCT ti.calculation_date FROM technical_indicators ti WHERE ti.stock_id = :stockId ORDER BY ti.calculation_date DESC LIMIT 2", nativeQuery = true)
    List<LocalDate> findLatestTwoCalculationDates(@Param("stockId") Long stockId);

    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.stock.id IN :stockIds " +
           "AND ti.indicatorType IN :types " +
           "AND ti.calculationDate = (" +
           "    SELECT MAX(ti2.calculationDate) FROM TechnicalIndicator ti2 " +
           "    WHERE ti2.stock.id = ti.stock.id AND ti2.indicatorType = ti.indicatorType" +
           ")")
    List<TechnicalIndicator> findLatestByStockIdsAndTypes(
        @Param("stockIds") List<Long> stockIds,
        @Param("types") List<IndicatorType> types);
}