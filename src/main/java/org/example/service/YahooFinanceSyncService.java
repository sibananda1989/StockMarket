package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.StockHistoryDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class YahooFinanceSyncService {

    private final YahooFinanceService yahooFinanceService;
    private final DailyPriceRepository dailyPriceRepository;
    private final StockService stockService;
    private final StockSplitDetector splitDetector;
    // Intentionally NOT injecting TechnicalAnalysisService to avoid a circular
    // dependency (TechnicalAnalysisService depends on this service for auto-backfill).
    // Instead, callers of syncHistory() trigger indicator recalculation themselves.

    @Transactional
    public int syncHistory(Long stockId, int days) {
        Stock stock = stockService.getStockById(stockId);
        String symbol = stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol();

        log.info("Syncing {} days of history for stock: {} ({})", days, stock.getSymbol(), symbol);

        List<StockHistoryDTO> history = yahooFinanceService.fetchHistory(symbol, days);

        if (history == null || history.isEmpty()) {
            log.warn("No history data returned from Yahoo Finance for {}", symbol);
            return 0;
        }

        int savedCount = 0;
        for (StockHistoryDTO dto : history) {
            DailyPrice price = dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), dto.getDate())
                    .orElse(new DailyPrice(stock, BigDecimal.ZERO, dto.getDate()));

            price.setClosingPrice(BigDecimal.valueOf(dto.getClose()));
            price.setOpeningPrice(BigDecimal.valueOf(dto.getOpen()));
            price.setHighPrice(BigDecimal.valueOf(dto.getHigh()));
            price.setLowPrice(BigDecimal.valueOf(dto.getLow()));
            price.setVolume(dto.getVolume());

            dailyPriceRepository.save(price);
            savedCount++;
        }

        log.info("Successfully synced {} records for {}", savedCount, stock.getSymbol());

        if (!history.isEmpty()) {
            StockHistoryDTO latest = history.get(history.size() - 1);
            stock.setVolume(latest.getVolume());
            stockService.saveStock(stock);
        }

        // Check for stock splits after syncing
        int splitAdjustments = splitDetector.detectAndFixSplits(stockId);
        if (splitAdjustments > 0) {
            log.warn("Applied split adjustment to {} pre-split records for {}", splitAdjustments, stock.getSymbol());
        }

        // Indicator recalculation is handled by callers of syncHistory() (
        // StockSyncService, StartupDataSyncTask, TechnicalAnalysisService auto-backfill)
        // to avoid a circular dependency between this service and TechnicalAnalysisService.

        return savedCount;
    }
}
