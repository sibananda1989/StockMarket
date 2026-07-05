package org.example.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.service.StockService;
import org.example.service.TechnicalAnalysisService;
import org.example.service.YahooFinanceSyncService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupDataSyncTask implements StartupTask {

    private final StockService stockService;
    private final YahooFinanceSyncService yahooFinanceSyncService;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final DailyPriceRepository dailyPriceRepository;

    private static final int SYNC_DAYS = 7;

    @Override
    public String getId() {
        return "data-sync";
    }

    @Override
    public String getName() {
        return "Price History Sync";
    }

    @Override
    public String getDescription() {
        return "Sync 7 days of price history for stocks missing today's data";
    }

    @Override
    public String getCategory() {
        return "Data Sync";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public void execute() {
        List<Stock> stocks = stockService.getAllStocks();

        if (stocks.isEmpty()) {
            log.info("No stocks found, skipping startup data sync");
            return;
        }

        // Skip stocks that already have today's price data (saves Yahoo API calls)
        List<Stock> staleStocks = stocks.stream()
                .filter(stock -> {
                    Optional<DailyPrice> latest = dailyPriceRepository
                            .findFirstByStockIdOrderByPriceDateDesc(stock.getId());
                    return latest.isEmpty() || !latest.get().getPriceDate().equals(LocalDate.now());
                })
                .collect(Collectors.toList());

        int skipped = stocks.size() - staleStocks.size();
        if (skipped > 0) {
            log.info("Skipping {} stocks with fresh data, syncing {} stale stocks", skipped, staleStocks.size());
        }

        if (staleStocks.isEmpty()) {
            log.info("All stocks already have today's data, nothing to sync");
            return;
        }

        log.info("Starting data sync for {} stocks (last {} days)...", staleStocks.size(), SYNC_DAYS);

        int synced = 0;
        int failed = 0;
        for (Stock stock : staleStocks) {
            try {
                int saved = yahooFinanceSyncService.syncHistory(stock.getId(), SYNC_DAYS);
                synced += saved;
                if (saved > 0) {
                    log.debug("Synced {} records for {}", saved, stock.getSymbol());
                    // Recalculate indicators after syncing new price data
                    try {
                        technicalAnalysisService.calculateIndicatorsForStock(stock.getId());
                    } catch (Exception e) {
                        log.warn("Indicator recalculation failed after sync for {}: {}", stock.getSymbol(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                failed++;
                log.warn("Failed to sync {}: {}", stock.getSymbol(), e.getMessage());
            }
        }

        log.info("Startup data sync complete: {} records synced across {} stocks ({} failed)",
                synced, staleStocks.size(), failed);
    }
}
