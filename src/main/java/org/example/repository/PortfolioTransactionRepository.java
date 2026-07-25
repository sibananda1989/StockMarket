package org.example.repository;

import org.example.entity.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {

    List<PortfolioTransaction> findByPortfolioIdOrderByTransactionDateAscIdAsc(Long portfolioId);

    List<PortfolioTransaction> findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(Long portfolioId, Long stockId);

    long countByPortfolioId(Long portfolioId);

    @Transactional
    void deleteByPortfolioId(Long portfolioId);

    @Transactional
    void deleteByStockId(Long stockId);
}
