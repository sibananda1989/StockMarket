package org.example.repository;

import org.example.entity.PortfolioTransaction;
import org.example.entity.TransactionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {

    @EntityGraph(attributePaths = {"stock", "portfolio"})
    List<PortfolioTransaction> findByPortfolioIdOrderByTransactionDateAscIdAsc(Long portfolioId);

    @EntityGraph(attributePaths = {"stock", "portfolio"})
    List<PortfolioTransaction> findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(Long portfolioId, Long stockId);

    @EntityGraph(attributePaths = {"stock", "portfolio"})
    List<PortfolioTransaction> findByStockIdOrderByTransactionDateAscIdAsc(Long stockId);

    /** All SELL rows that consume quantity from the given BUY lot. */
    List<PortfolioTransaction> findByLinkedBuyIdOrderByTransactionDateAscIdAsc(Long linkedBuyId);

    /** SELL rows for a portfolio that are not yet mapped to a BUY lot (legacy data). */
    @EntityGraph(attributePaths = {"stock", "portfolio"})
    List<PortfolioTransaction> findByPortfolioIdAndLinkedBuyIdIsNullAndType(Long portfolioId, TransactionType type);

    /** BUY lots for a portfolio, optionally per stock. */
    @EntityGraph(attributePaths = {"stock", "portfolio"})
    List<PortfolioTransaction> findByPortfolioIdAndTypeOrderByTransactionDateAscIdAsc(Long portfolioId, TransactionType type);

    @EntityGraph(attributePaths = {"stock", "portfolio"})
    List<PortfolioTransaction> findByPortfolioIdAndStockIdAndTypeOrderByTransactionDateAscIdAsc(Long portfolioId, Long stockId, TransactionType type);

    long countByPortfolioId(Long portfolioId);

    @Transactional
    void deleteByPortfolioId(Long portfolioId);

    @Transactional
    void deleteByStockId(Long stockId);
}
