package org.example.repository;

import org.example.entity.PortfolioDailyValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioDailyValueRepository extends JpaRepository<PortfolioDailyValue, Long> {

    Optional<PortfolioDailyValue> findByPortfolio_IdAndValueDate(Long portfolioId, LocalDate valueDate);

    List<PortfolioDailyValue> findByPortfolio_IdOrderByValueDateAsc(Long portfolioId);

    @Query("SELECT p FROM PortfolioDailyValue p WHERE p.portfolio.id = :portfolioId AND p.valueDate BETWEEN :from AND :to ORDER BY p.valueDate ASC")
    List<PortfolioDailyValue> findByPortfolioIdAndDateRange(@Param("portfolioId") Long portfolioId,
                                                             @Param("from") LocalDate from,
                                                             @Param("to") LocalDate to);

    @Query("SELECT p FROM PortfolioDailyValue p WHERE p.valueDate BETWEEN :from AND :to ORDER BY p.valueDate ASC")
    List<PortfolioDailyValue> findAllByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<PortfolioDailyValue> findByValueDateOrderByPortfolio_Id(LocalDate valueDate);

    @Query("SELECT p.valueDate FROM PortfolioDailyValue p WHERE p.portfolio.id = :portfolioId ORDER BY p.valueDate DESC")
    List<LocalDate> findDistinctDatesByPortfolioId(@Param("portfolioId") Long portfolioId);

    void deleteByPortfolio_IdAndValueDate(Long portfolioId, LocalDate valueDate);
}
