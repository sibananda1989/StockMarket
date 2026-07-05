package org.example.startup;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.Stock;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioRepository;
import org.example.repository.StockRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time migration that runs on startup to transition from the single-implicit-portfolio
 * model to the multi-portfolio model.
 *
 * 1. Creates a "Default Portfolio" if none exists.
 * 2. **Gated** — Only migrates legacy stocks to PortfolioHoldings if NO holdings exist yet.
 *    Once any PortfolioHolding record exists, this step is skipped forever to prevent
 *    silent auto-addition of stocks to the Default portfolio on every restart.
 * 3. For every portfolio_snapshot with null portfolio_id, sets it to the default portfolio's ID.
 *
 * @see <a href="https://github.com/orgs/community/discussions/...#root-cause">Root-cause analysis</a>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioMigrationStartupTask implements StartupTask {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final EntityManager entityManager;

    @Override
    public String getId() {
        return "portfolio-migration";
    }

    @Override
    public String getName() {
        return "Portfolio Migration";
    }

    @Override
    public String getDescription() {
        return "Migrate legacy holdings to multi-portfolio schema";
    }

    @Override
    public String getCategory() {
        return "Portfolio";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Transactional
    @Override
    public void execute() {
        Portfolio defaultPort = portfolioRepository.findByIsDefaultTrue().orElse(null);
        if (defaultPort == null) {
            defaultPort = portfolioRepository.save(
                    new Portfolio("Default Portfolio", "Auto-created default portfolio", true));
            log.info("Created default portfolio (id={})", defaultPort.getId());
        } else {
            log.debug("Default portfolio already exists (id={})", defaultPort.getId());
        }

        // Step 2: One-time migration of legacy stocks with quantity > 0 to PortfolioHoldings.
        //
        // ╔══════════════════════════════════════════════════════════════════╗
        // ║  IMPORTANT: This step runs ONLY if NO PortfolioHolding records  ║
        // ║  exist yet. Once any holding exists, we skip this migration     ║
        // ║  forever — otherwise stocks that happen to have quantity > 0    ║
        // ║  on the 'stocks' table (e.g. from Dhan sync, CSV import) would ║
        // ║  be silently auto-added to the Default portfolio on every       ║
        // ║  application restart. See CLAUDE.md for root-cause analysis.    ║
        // ╚══════════════════════════════════════════════════════════════════╝
        long existingHoldingCount = holdingRepository.count();
        if (existingHoldingCount == 0) {
            List<Stock> stocksWithHoldings = stockRepository.findAll();
            int holdingsCreated = 0;
            int holdingsSkipped = 0;
            for (Stock stock : stocksWithHoldings) {
                if (stock.getQuantity() != null && stock.getQuantity() > 0) {
                    if (holdingRepository.findByStockId(stock.getId()).isEmpty()) {
                        PortfolioHolding holding = new PortfolioHolding();
                        holding.setPortfolio(defaultPort);
                        holding.setStock(stock);
                        holding.setQuantity(stock.getQuantity());
                        holding.setAvgPrice(stock.getAvgPrice());
                        try {
                            // Rely on DB unique constraint uk_portfolio_holding_portfolio_stock
                            // (declared on PortfolioHolding entity) to make the save atomic
                            // against concurrent app instances. Pre-check + insert inside
                            // @Transactional is NOT sufficient across separate JVMs.
                            holdingRepository.save(holding);
                            holdingsCreated++;
                        } catch (DataIntegrityViolationException e) {
                            // Concurrent startup race — another instance inserted the same
                            // (portfolio_id, stock_id) row first. Safe to ignore.
                            holdingsSkipped++;
                            log.info("Skipped duplicate PortfolioHolding for stock {} (created by concurrent instance)",
                                    stock.getSymbol());
                        }
                    }
                }
            }
            if (holdingsCreated > 0) {
                log.info("Migrated {} stocks to PortfolioHoldings in default portfolio", holdingsCreated);
            }
            if (holdingsSkipped > 0) {
                log.info("Skipped {} duplicate PortfolioHoldings due to concurrent migration", holdingsSkipped);
            }
        } else {
            log.info("Portfolio migration already complete ({} existing holding(s) found). Skipping Step 2 " +
                     "to prevent auto-adding stocks to the Default portfolio.", existingHoldingCount);
        }

        // Step 3: Update portfolio_snapshots with null portfolio_id to default portfolio
        Query updateSnapshots = entityManager.createNativeQuery(
                "UPDATE portfolio_snapshots SET portfolio_id = :portfolioId WHERE portfolio_id IS NULL");
        updateSnapshots.setParameter("portfolioId", defaultPort.getId());
        int snapshotsUpdated = updateSnapshots.executeUpdate();
        if (snapshotsUpdated > 0) {
            log.info("Updated {} portfolio_snapshots with portfolio_id={}", snapshotsUpdated, defaultPort.getId());
        }

        log.info("Portfolio migration complete. Default portfolio id={}", defaultPort.getId());
    }
}
