package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioSnapshot;
import org.example.entity.Stock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Dedicated bean for writing portfolio snapshots with REQUIRES_NEW propagation.
 * Must be injected as a separate bean (not self-invoked) so that the
 * @Transactional propagation takes effect.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioSnapshotWriter {

    private final PortfolioSnapshotService snapshotService;

    /**
     * Saves a snapshot for the given stock/date across all portfolios that hold it.
     * Runs in its own transaction so it commits even if the caller's transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<PortfolioSnapshot> saveSnapshots(Stock stock, LocalDate date) {
        return snapshotService.saveOrUpdate(stock, date);
    }

    /**
     * Saves a snapshot for the given stock/date in the specific portfolio.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PortfolioSnapshot saveSnapshot(Stock stock, LocalDate date, Portfolio portfolio) {
        return snapshotService.saveOrUpdate(stock, date, portfolio);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rebuildSnapshotsFrom(Stock stock, LocalDate fromDate) {
        snapshotService.rebuildSnapshotsForStockFrom(stock, fromDate);
    }
}
