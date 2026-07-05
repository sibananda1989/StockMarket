package org.example.repository;

import org.example.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByWatchlistIdOrderByAddedAtAsc(Long watchlistId);

    List<WatchlistItem> findAllByStockId(Long stockId);

    Optional<WatchlistItem> findByWatchlistIdAndStockId(Long watchlistId, Long stockId);

    boolean existsByWatchlistIdAndStockId(Long watchlistId, Long stockId);

    void deleteByWatchlistIdAndStockId(Long watchlistId, Long stockId);

    @Modifying
    @Query("DELETE FROM WatchlistItem wi WHERE wi.stock.id = :stockId")
    int deleteByStockId(@Param("stockId") Long stockId);

    long countByWatchlistId(Long watchlistId);
}
