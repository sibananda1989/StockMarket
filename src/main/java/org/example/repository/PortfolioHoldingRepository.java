package org.example.repository;

import org.example.entity.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {

    /**
     * Fetch all holdings for a portfolio, eagerly joining the Stock entity,
     * and sort by Stock.symbol ascending.
     *
     * NOTE: A derived query such as {@code findByPortfolioIdOrderByStockSymbolAsc}
     * would resolve against a {@code stockSymbol} field on PortfolioHolding,
     * which does NOT exist (the field is a {@code @ManyToOne Stock stock}).
     * Spring Data throws a {@code PropertyReferenceException} at startup
     * for such a method name. This explicit JPQL is the safe alternative.
     */
    @Query("SELECT ph FROM PortfolioHolding ph JOIN FETCH ph.stock s " +
           "WHERE ph.portfolio.id = :portfolioId ORDER BY s.symbol ASC")
    List<PortfolioHolding> findHoldingsOrderBySymbol(@Param("portfolioId") Long portfolioId);

    Optional<PortfolioHolding> findByPortfolioIdAndStockId(Long portfolioId, Long stockId);

    List<PortfolioHolding> findByStockId(Long stockId);

    boolean existsByPortfolioIdAndStockId(Long portfolioId, Long stockId);

    long countByPortfolioId(Long portfolioId);

    List<PortfolioHolding> findByPortfolioId(Long portfolioId);

    void deleteByPortfolioId(Long portfolioId);

    @Modifying
    @Query("DELETE FROM PortfolioHolding ph WHERE ph.stock.id = :stockId")
    int deleteByStockId(@Param("stockId") Long stockId);

    @Modifying
    @Query("DELETE FROM PortfolioHolding ph WHERE ph.portfolio.id = :portfolioId AND ph.stock.id = :stockId")
    int deleteByPortfolioIdAndStockId(@Param("portfolioId") Long portfolioId, @Param("stockId") Long stockId);
}
