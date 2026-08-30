package org.example.repository;

import org.example.entity.FundamentalData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FundamentalDataRepository extends JpaRepository<FundamentalData, Long> {

    Optional<FundamentalData> findByStockId(Long stockId);

    void deleteByStockId(Long stockId);

    @Query("SELECT f FROM FundamentalData f JOIN FETCH f.stock WHERE f.stock.id IN :stockIds")
    List<FundamentalData> findByStockIdIn(@Param("stockIds") List<Long> stockIds);

    @Query("SELECT DISTINCT f.sector FROM FundamentalData f WHERE f.sector IS NOT NULL ORDER BY f.sector")
    List<String> findDistinctSectors();

    @Query("SELECT COUNT(DISTINCT f.stock.id) FROM FundamentalData f")
    long countDistinctStocks();
}
