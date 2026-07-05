package org.example.repository;

import org.example.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    long countByStockId(Long stockId);

    List<PortfolioSnapshot> findByStockIdOrderBySnapshotDateAsc(Long stockId);

    Optional<PortfolioSnapshot> findByStockIdAndSnapshotDate(Long stockId, LocalDate snapshotDate);

    @Query("SELECT ps FROM PortfolioSnapshot ps WHERE ps.stock.id = :stockId AND ps.portfolio.id = :portfolioId AND ps.snapshotDate = :snapshotDate")
    Optional<PortfolioSnapshot> findByStockIdAndPortfolioIdAndSnapshotDate(@Param("stockId") Long stockId, @Param("portfolioId") Long portfolioId, @Param("snapshotDate") LocalDate snapshotDate);

    boolean existsByStockIdAndSnapshotDate(Long stockId, LocalDate snapshotDate);

    @Query(value = "SELECT * FROM portfolio_snapshots WHERE stock_id = :stockId AND snapshot_date >= DATE_SUB(CURRENT_DATE, INTERVAL :days DAY) ORDER BY snapshot_date ASC", nativeQuery = true)
    List<PortfolioSnapshot> findLastNSnapshots(@Param("stockId") Long stockId, @Param("days") int days);

    @Query("SELECT ps FROM PortfolioSnapshot ps WHERE ps.snapshotDate = :date ORDER BY ps.pnlPercent DESC")
    List<PortfolioSnapshot> findAllBySnapshotDate(@Param("date") LocalDate date);

    /**
     * Bulk-delete all snapshots for a stock. Used by {@code StockService.deleteStock}
     * to (a) avoid the FK violation {@code portfolio_snapshots.stock_id -> stocks.id}
     * and (b) shrink the race window with the snapshot scheduler.
     */
    long deleteByStockId(Long stockId);

    @Modifying
    @Query("DELETE FROM PortfolioSnapshot ps WHERE ps.stock.id = :stockId AND ps.portfolio.id = :portfolioId")
    int deleteByPortfolioIdAndStockId(@Param("portfolioId") Long portfolioId, @Param("stockId") Long stockId);

    // ── Portfolio-scoped aggregated queries ─────────────────────────────

    @Query(value = "SELECT ps.snapshot_date, " +
           "COALESCE(SUM(ps.investment), 0), " +
           "COALESCE(SUM(ps.current_value), 0), " +
           "COALESCE(SUM(ps.pnl), 0), " +
           "COUNT(ps.id) " +
           "FROM portfolio_snapshots ps " +
           "WHERE ps.snapshot_date >= :fromDate " +
           "AND ps.portfolio_id = :portfolioId " +
           "GROUP BY ps.snapshot_date " +
           "ORDER BY ps.snapshot_date ASC", nativeQuery = true)
    List<Object[]> findAggregatedHistory(@Param("fromDate") LocalDate fromDate,
                                         @Param("portfolioId") Long portfolioId);

    @Query(value = "SELECT ps.snapshot_date, " +
           "COALESCE(SUM(ps.investment), 0), " +
           "COALESCE(SUM(ps.current_value), 0), " +
           "COALESCE(SUM(ps.pnl), 0), " +
           "COUNT(ps.id) " +
           "FROM portfolio_snapshots ps " +
           "WHERE ps.portfolio_id = :portfolioId " +
           "GROUP BY ps.snapshot_date " +
           "ORDER BY ps.snapshot_date ASC", nativeQuery = true)
    List<Object[]> findAllAggregatedHistory(@Param("portfolioId") Long portfolioId);

    // ── Backward compat: un-scoped queries (for transition) ────────────

    @Query(value = "SELECT ps.snapshot_date, " +
           "COALESCE(SUM(ps.investment), 0), " +
           "COALESCE(SUM(ps.current_value), 0), " +
           "COALESCE(SUM(ps.pnl), 0), " +
           "COUNT(ps.id) " +
           "FROM portfolio_snapshots ps " +
           "WHERE ps.snapshot_date >= :fromDate " +
           "GROUP BY ps.snapshot_date " +
           "ORDER BY ps.snapshot_date ASC", nativeQuery = true)
    List<Object[]> findAggregatedHistoryAll(@Param("fromDate") LocalDate fromDate);

    @Query(value = "SELECT ps.snapshot_date, " +
           "COALESCE(SUM(ps.investment), 0), " +
           "COALESCE(SUM(ps.current_value), 0), " +
           "COALESCE(SUM(ps.pnl), 0), " +
           "COUNT(ps.id) " +
           "FROM portfolio_snapshots ps " +
           "GROUP BY ps.snapshot_date " +
           "ORDER BY ps.snapshot_date ASC", nativeQuery = true)
    List<Object[]> findAllAggregatedHistoryAll();
}
