package org.example.repository;

import org.example.entity.CorporateEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CorporateEventRepository extends JpaRepository<CorporateEvent, Long> {
    List<CorporateEvent> findBySymbolAndEventDateBetweenOrderByEventDateAsc(String symbol, LocalDate from, LocalDate to);
    boolean existsBySymbolAndEventDateBetween(String symbol, LocalDate from, LocalDate to);
    boolean existsBySymbolAndEventDateAndPurpose(String symbol, LocalDate eventDate, String purpose);
    List<CorporateEvent> findByEventDateBetween(LocalDate from, LocalDate to);
    
    @Query("SELECT ce FROM CorporateEvent ce WHERE ce.symbol IN :symbols " +
           "AND ce.eventDate BETWEEN :fromDate AND :toDate ORDER BY ce.symbol")
    List<CorporateEvent> findBySymbolInAndEventDateBetween(
        @Param("symbols") List<String> symbols,
        @Param("fromDate") LocalDate from,
        @Param("toDate") LocalDate to);
}
