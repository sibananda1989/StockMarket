package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockSyncService {

    private final StockService stockService;
    private final YahooFinanceSyncService yahooFinanceSyncService;
    private final DailyPriceRepository dailyPriceRepository;
    private final TechnicalAnalysisService technicalAnalysisService;

    private static final int TARGET_DAYS = 365 * 2;

    public int backfillAllStocks() {
        List<Stock> stocks = stockService.getAllStocks();
        int total = 0;
        int skipped = 0;
        int failed = 0;
        for (Stock stock : stocks) {
            long count = dailyPriceRepository.countByStockId(stock.getId());
            if (count >= 365) {
                skipped++;
                continue;
            }
            try {
                int saved = yahooFinanceSyncService.syncHistory(stock.getId(), TARGET_DAYS);
                total += saved;
                log.info("Backfilled {} records for {} (had {} before)", saved, stock.getSymbol(), count);

                // Recalculate indicators after syncing new price data
                if (saved > 0) {
                    try {
                        technicalAnalysisService.calculateIndicatorsForStock(stock.getId());
                    } catch (Exception e) {
                        log.warn("Indicator recalculation failed after backfill for {}: {}", stock.getSymbol(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                failed++;
                log.warn("Could not backfill {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
        log.info("Backfill complete: {} stocks backfilled, {} skipped (≥365 days), {} failed",
                total > 0 ? "~" + stocks.size() + " processed" : "0", skipped, failed);
        return total;
    }

    public int backfillStock(Long stockId) {
        Stock stock = stockService.getStockById(stockId);
        long count = dailyPriceRepository.countByStockId(stockId);
        if (count >= 365) {
            log.info("Stock {} already has {} days of data, skipping", stock.getSymbol(), count);
            return 0;
        }
        int saved = yahooFinanceSyncService.syncHistory(stockId, TARGET_DAYS);
        log.info("Backfilled {} records for {} (had {} before)", saved, stock.getSymbol(), count);

        // Recalculate indicators after syncing new price data
        if (saved > 0) {
            try {
                technicalAnalysisService.calculateIndicatorsForStock(stockId);
            } catch (Exception e) {
                log.warn("Indicator recalculation failed after backfill for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }

        return saved;
    }

    /**
     * Sync history for a specific stock with user-specified days.
     * Unlike backfillStock, this does NOT skip stocks based on existing record count.
     */
    public long syncStockHistory(Long stockId, int days) {
        Stock stock = stockService.getStockById(stockId);
        log.info("Syncing {} days of history for stock {}", days, stock.getSymbol());
        int saved = yahooFinanceSyncService.syncHistory(stockId, days);
        log.info("Synced {} records for {} (requested {} days)", saved, stock.getSymbol(), days);

        // Recalculate indicators after syncing new price data
        if (saved > 0) {
            try {
                technicalAnalysisService.calculateIndicatorsForStock(stockId);
            } catch (Exception e) {
                log.warn("Indicator recalculation failed after sync for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }

        return saved;
    }
}
