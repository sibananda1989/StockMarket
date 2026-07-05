package org.example.repository;

import org.example.entity.BlockDeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BlockDealRepository extends JpaRepository<BlockDeal, Long> {

    List<BlockDeal> findByStockIdOrderByDealDateDesc(Long stockId);

    List<BlockDeal> findByDealDateBetweenOrderByDealDateDesc(LocalDate from, LocalDate to);

    List<BlockDeal> findByStockIdAndDealDateBetweenOrderByDealDateDesc(Long stockId, LocalDate from, LocalDate to);

    @Query("SELECT b FROM BlockDeal b WHERE b.stock.id = :stockId AND b.buySell = 'BUY' " +
           "AND b.isInstitutional = true AND b.dealDate >= :since")
    List<BlockDeal> findInstitutionalBuysSince(@Param("stockId") Long stockId, @Param("since") LocalDate since);

    @Query("SELECT b FROM BlockDeal b WHERE b.buySell = 'BUY' AND b.isInstitutional = true " +
           "AND b.dealDate >= :since")
    List<BlockDeal> findAllInstitutionalBuysSince(@Param("since") LocalDate since);

    @Query("SELECT COUNT(b) FROM BlockDeal b WHERE b.stock.id = :stockId AND " +
           "b.buySell = 'BUY' AND b.isInstitutional = true AND b.dealDate >= :since")
    int countInstitutionalBuysSince(@Param("stockId") Long stockId, @Param("since") LocalDate since);

    @Query("SELECT b.stock.id, COUNT(b) FROM BlockDeal b WHERE b.buySell = 'BUY' " +
           "AND b.isInstitutional = true AND b.dealDate >= :since " +
           "GROUP BY b.stock.id ORDER BY COUNT(b) DESC")
    List<Object[]> countInstitutionalBuysGroupedByStock(@Param("since") LocalDate since);

    boolean existsByStockIdAndDealDateAndClientNameAndBuySell(
            Long stockId, LocalDate dealDate, String clientName, String buySell);

    @Modifying
    @Query("DELETE FROM BlockDeal bd WHERE bd.stock.id = :stockId")
    int deleteByStockId(@Param("stockId") Long stockId);
}
