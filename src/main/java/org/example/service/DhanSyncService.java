package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.DhanCandleResponseDTO;
import org.example.dto.SymbolValidationResult;
import org.example.dto.DhanHoldingDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DhanSyncService {

    private final DhanApiService dhanApiService;
    private final StockService stockService;
    private final DailyPriceRepository dailyPriceRepository;
    private final PortfolioSnapshotService snapshotService;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final PortfolioRepository portfolioRepository;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final YahooFinanceService yahooFinanceService;

    // Locale.ROOT fixes I18N finding — these are internal date formats, not user-facing
    private static final DateTimeFormatter DHAN_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    @Transactional
    public int syncHoldings() {
        List<DhanHoldingDTO> holdings = dhanApiService.getHoldings();
        if (holdings == null || holdings.isEmpty()) {
            log.warn("No holdings returned from Dhan");
            return 0;
        }

        List<String> symbols = holdings.stream()
                .map(h -> h.getTradingSymbol().trim())
                .collect(Collectors.toList());
        Map<String, SymbolValidationResult> validated = yahooFinanceService.validateSymbols(symbols);

        LocalDate today = LocalDate.now();
        for (DhanHoldingDTO h : holdings) {
            String rawSymbol = h.getTradingSymbol().trim();
            String exchange = h.getExchange();

            SymbolValidationResult yahoo = validated.get(rawSymbol);
            if (yahoo == null || !yahoo.isValid()) {
                yahoo = yahooFinanceService.validateSymbol(rawSymbol);
            }
            String name = (yahoo.getCompanyName() != null && !yahoo.getCompanyName().isBlank())
                    ? yahoo.getCompanyName() : rawSymbol;
            String sector = (yahoo.getSector() != null && !yahoo.getSector().isBlank())
                    ? yahoo.getSector() : exchange;

            Stock stock = stockService.getOrCreateStock(rawSymbol, name, sector);
            if (h.getTotalQty() != null) {
                stock.setQuantity(h.getTotalQty().intValue());
            }
            if (h.getAvgCostPrice() != null) {
                stock.setAvgPrice(BigDecimal.valueOf(h.getAvgCostPrice()));
            }
            if (h.getLastTradedPrice() != null) {
                stock.setLastTradedPrice(BigDecimal.valueOf(h.getLastTradedPrice()));
            }
            stockService.recalculatePortfolio(stock);

            // Ensure a PortfolioHolding exists for this Dhan-synced stock.
            // Dhan holdings are real brokerage positions — they should be tracked
            // in the Default portfolio. Without this, the stock would remain an
            // "orphan" with quantity > 0 on the stock entity but no PortfolioHolding,
            // and the PortfolioMigrationStartupTask (if un-gated) used to silently
            // add such orphans on the next restart — the root cause of the bug.
            List<PortfolioHolding> existing = portfolioHoldingRepository.findByStockId(stock.getId());
            if (existing.isEmpty()) {
                Portfolio defaultPort = portfolioRepository.findByIsDefaultTrue()
                        .orElseGet(() -> portfolioRepository.save(
                                new Portfolio("Default Portfolio", "Auto-created default portfolio", true)));
                PortfolioHolding holding = new PortfolioHolding();
                holding.setPortfolio(defaultPort);
                holding.setStock(stock);
                holding.setQuantity(stock.getQuantity());
                holding.setAvgPrice(stock.getAvgPrice());
                portfolioHoldingRepository.save(holding);
            } else {
                // Update existing holdings with latest quantity/price from Dhan
                for (PortfolioHolding ph : existing) {
                    ph.setQuantity(stock.getQuantity());
                    ph.setAvgPrice(stock.getAvgPrice());
                }
                portfolioHoldingRepository.saveAll(existing);
            }

            snapshotService.saveOrUpdate(stock, today);
            log.info("Synced holding: {} (qty={}, avg={}, ltp={})",
                    h.getTradingSymbol(), h.getTotalQty(), h.getAvgCostPrice(), h.getLastTradedPrice());
        }
        return holdings.size();
    }

    @Transactional
    @CacheEvict(value = "signals", allEntries = true)
    public void syncDailyPricesAndRsi() {
        List<DhanHoldingDTO> holdings = dhanApiService.getHoldings();
        if (holdings == null || holdings.isEmpty()) return;

        for (DhanHoldingDTO holding : holdings) {
            try {
                syncPricesForHolding(holding);
            } catch (IllegalArgumentException | IllegalStateException e) {
                // CWE-755: include exception object so stack trace is preserved in logs
                log.error("Failed to sync prices for {}: {}", holding.getTradingSymbol(), e.getMessage(), e);
            } catch (RuntimeException e) {
                log.error("Unexpected error syncing prices for {}", holding.getTradingSymbol(), e);
            }
        }
    }

    private void syncPricesForHolding(DhanHoldingDTO holding) {
        String symbol = holding.getTradingSymbol();
        String exchangeSegment = "BSE".equalsIgnoreCase(holding.getExchange()) ? "BSE_EQ" : "NSE_EQ";
        Stock stock = stockService.getOrCreateStock(symbol, symbol, holding.getExchange());

        DhanCandleResponseDTO candles = dhanApiService.getDailyCandles(
                holding.getSecurityId(), exchangeSegment, 60);

        if (candles == null || candles.getClose() == null || candles.getClose().isEmpty()) {
            log.warn("No candle data returned for {}", symbol);
            return;
        }

        int saved = saveCandlesToDb(stock, candles);
        log.info("Saved {} new price records for {}", saved, symbol);

        if (candles.getVolume() != null && !candles.getVolume().isEmpty()) {
            stock.setVolume(candles.getVolume().get(candles.getVolume().size() - 1));
            stockService.saveStock(stock);
        }


        // Recalculate all technical indicators for this stock immediately after
        // syncing prices. This ensures indicators are fresh whether triggered by
        // the scheduler or the manual controller endpoint (Gap 5 fix).
        try {
            technicalAnalysisService.calculateIndicatorsForStock(stock.getId());
        } catch (Exception e) {
            log.warn("Indicator recalculation failed for {} after Dhan sync: {}", symbol, e.getMessage());
        }

        stockService.recalculatePortfolio(stock);
        snapshotService.saveOrUpdate(stock, LocalDate.now());
    }

    // Extracted to reduce cyclomatic complexity of syncPricesForHolding (CWE cyclomatic finding)
    private int saveCandlesToDb(Stock stock, DhanCandleResponseDTO candles) {
        List<String> timestamps = candles.getTimestamp();
        List<Double> opens = candles.getOpen();
        List<Double> highs = candles.getHigh();
        List<Double> lows = candles.getLow();
        List<Double> closes = candles.getClose();
        List<Long> volumes = candles.getVolume();

        int saved = 0;
        for (int i = 0; i < closes.size(); i++) {
            LocalDate priceDate = parseDate(timestamps.get(i));
            if (priceDate == null) continue;

            DailyPrice price = dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), priceDate)
                    .orElse(new DailyPrice(stock, BigDecimal.ZERO, priceDate));
            price.setClosingPrice(BigDecimal.valueOf(closes.get(i)));
            price.setOpeningPrice(BigDecimal.valueOf(opens.get(i)));
            price.setHighPrice(BigDecimal.valueOf(highs.get(i)));
            price.setLowPrice(BigDecimal.valueOf(lows.get(i)));
            price.setVolume(volumes != null && i < volumes.size() ? volumes.get(i) : 0L);

            dailyPriceRepository.save(price);
            saved++;
        }
        return saved;
    }

    private LocalDate parseDate(String timestamp) {
        if (timestamp == null) return null;
        try {
            String datePart = timestamp.length() > 10 ? timestamp.substring(0, 10) : timestamp;
            return LocalDate.parse(datePart, DHAN_DATE_FMT);
        } catch (DateTimeParseException e) {
            // CWE-755: include exception object so stack trace is preserved
            log.warn("Could not parse date '{}': {}", timestamp, e.getMessage(), e);
            return null;
        }
    }
}
