package org.example.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Stock;
import org.example.repository.StockRepository;
import org.example.service.PortfolioSnapshotService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * On app startup, ensures today's portfolio snapshot exists for every stock.
 * Creates per-portfolio snapshots for all portfolios that hold each stock,
 * and falls back to an un-scoped legacy snapshot for stocks not in any portfolio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioSnapshotStartupTask implements StartupTask {

    private final StockRepository stockRepository;
    private final PortfolioSnapshotService snapshotService;

    @Override
    public String getId() {
        return "portfolio-snapshot";
    }

    @Override
    public String getName() {
        return "Today's Snapshots";
    }

    @Override
    public String getDescription() {
        return "Ensure portfolio snapshots exist for each stock today";
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
        return false;
    }

    @Transactional
    @Override
    public void execute() {
        LocalDate today = LocalDate.now();
        List<Stock> stocks = stockRepository.findAll();

        if (stocks.isEmpty()) {
            log.debug("No stocks found, skipping today's snapshot check");
            return;
        }

        int created = 0;
        for (Stock stock : stocks) {
            var snapshots = snapshotService.saveOrUpdate(stock, today);
            created += snapshots.size();
        }

        if (created > 0) {
            log.info("Created {} missing portfolio snapshot(s) for today ({})", created, today);
        } else {
            log.info("All portfolio snapshots already exist for today ({})", today);
        }
    }
}
