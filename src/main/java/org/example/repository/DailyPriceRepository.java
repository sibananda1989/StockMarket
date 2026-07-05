package org.example.repository;

import java.math.BigDecimal;
import org.example.entity.DailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {
    
    List<DailyPrice> findByStockIdOrderByPriceDateDesc(Long stockId);
    
    List<DailyPrice> findByStockIdAndPriceDateBetweenOrderByPriceDateDesc(
            Long stockId, LocalDate fromDate, LocalDate toDate);

    List<DailyPrice> findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(
            Long stockId, LocalDate fromDate, LocalDate toDate);
    
    Optional<DailyPrice> findFirstByStockIdOrderByPriceDateDesc(Long stockId);

    Optional<DailyPrice> findByStockIdAndPriceDate(Long stockId, LocalDate priceDate);
    
    @Query("SELECT dp FROM DailyPrice dp WHERE dp.stock.id = :stockId ORDER BY dp.priceDate ASC")
    List<DailyPrice> findAllByStockIdOrderByPriceDateAsc(@Param("stockId") Long stockId);

    @Query("SELECT dp FROM DailyPrice dp WHERE dp.stock.id = :stockId ORDER BY dp.priceDate DESC")
    List<DailyPrice> findLast14Days(@Param("stockId") Long stockId);
    
    @Query(value = "SELECT * FROM daily_prices WHERE stock_id = :stockId ORDER BY price_date DESC LIMIT :days", nativeQuery = true)
    List<DailyPrice> findLastNDays(@Param("stockId") Long stockId, @Param("days") int days);
    
    boolean existsByStockIdAndPriceDate(Long stockId, LocalDate priceDate);

    long countByStockId(Long stockId);

  @Query("SELECT DISTINCT dp.priceDate FROM DailyPrice dp WHERE dp.priceDate >= :fromDate ORDER BY dp.priceDate ASC")
  List<LocalDate> findDistinctTradingDatesAfter(@Param("fromDate") LocalDate fromDate);

  @Query("SELECT dp.closingPrice FROM DailyPrice dp WHERE dp.stock.id = :stockId AND dp.priceDate <= :targetDate ORDER BY dp.priceDate DESC LIMIT 1")
  Optional<BigDecimal> findClosingPriceOnOrBeforeDate(@Param("stockId") Long stockId, @Param("targetDate") LocalDate targetDate);

  @Query("SELECT dp FROM DailyPrice dp WHERE dp.stock.id IN :stockIds AND dp.priceDate = " +
         "(SELECT MAX(dp2.priceDate) FROM DailyPrice dp2 WHERE dp2.stock.id = dp.stock.id)")
  List<DailyPrice> findLatestPriceForStockIds(@Param("stockIds") List<Long> stockIds);

  @Query("SELECT dp FROM DailyPrice dp WHERE dp.stock.id IN :stockIds AND dp.priceDate >= :fromDate ORDER BY dp.stock.id, dp.priceDate DESC")
  List<DailyPrice> findPricesForStockIdsSince(@Param("stockIds") List<Long> stockIds, @Param("fromDate") LocalDate fromDate);
}
