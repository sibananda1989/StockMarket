package org.example.repository;

import org.example.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    
    Optional<Stock> findBySymbol(String symbol);
    
    boolean existsBySymbol(String symbol);

    List<Stock> findBySymbolStartingWith(String prefix);

    @Query("SELECT DISTINCT s.sector FROM Stock s WHERE s.sector IS NOT NULL AND s.sector <> '' ORDER BY s.sector")
    List<String> findDistinctSectors();
}
