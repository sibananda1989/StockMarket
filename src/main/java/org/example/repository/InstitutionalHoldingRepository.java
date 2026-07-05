package org.example.repository;

import org.example.entity.InstitutionalHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstitutionalHoldingRepository extends JpaRepository<InstitutionalHolding, Long> {

    Optional<InstitutionalHolding> findByStockIdAndQuarterEndDate(Long stockId, LocalDate quarterEndDate);

    List<InstitutionalHolding> findByStockIdOrderByQuarterEndDateDesc(Long stockId);

    Optional<InstitutionalHolding> findFirstByStockIdOrderByQuarterEndDateDesc(Long stockId);

    List<InstitutionalHolding> findByStockIdInAndQuarterEndDateOrderByStockId(List<Long> stockIds, LocalDate quarterEndDate);

    @Query("SELECT h FROM InstitutionalHolding h WHERE h.stock.id = :stockId ORDER BY h.quarterEndDate DESC")
    List<InstitutionalHolding> findLatestByStockId(@Param("stockId") Long stockId);

    @Query("SELECT h1 FROM InstitutionalHolding h1 WHERE h1.stock.id = :stockId " +
           "AND h1.quarterEndDate = (SELECT MAX(h2.quarterEndDate) FROM InstitutionalHolding h2 WHERE h2.stock.id = :stockId)")
    Optional<InstitutionalHolding> findLatestHolding(@Param("stockId") Long stockId);

    @Query("SELECT DISTINCT h.quarterEndDate FROM InstitutionalHolding h ORDER BY h.quarterEndDate DESC")
    List<LocalDate> findDistinctQuarterEndDates();

    @Query("SELECT h FROM InstitutionalHolding h WHERE h.quarterEndDate = :date")
    List<InstitutionalHolding> findAllByQuarterEndDate(@Param("date") LocalDate date);

    @Query("SELECT h FROM InstitutionalHolding h WHERE h.stock.symbol = :symbol ORDER BY h.quarterEndDate DESC")
    List<InstitutionalHolding> findBySymbolOrderByQuarterEndDateDesc(@Param("symbol") String symbol);

    @Modifying
    @Query("DELETE FROM InstitutionalHolding ih WHERE ih.stock.id = :stockId")
    int deleteByStockId(@Param("stockId") Long stockId);
}
