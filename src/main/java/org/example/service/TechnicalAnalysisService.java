package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.IndicatorDto;
import org.example.dto.IndicatorHistoryDto;
import org.example.entity.*;
import org.example.entity.IndicatorType;
import org.example.repository.*;
import org.example.service.calculator.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TechnicalAnalysisService {

    private final TechnicalIndicatorRepository indicatorRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final StockService stockService;
    private final CacheManager cacheManager;
    private final TechnicalIndicatorPersistenceService persistenceService;
    private final YahooFinanceSyncService yahooFinanceSyncService;

    @Value("${indicator.auto-backfill.enabled:true}")
    private boolean autoBackfillEnabled;

    @Value("${indicator.auto-backfill.buffer-days:10}")
    private int autoBackfillBufferDays;

    @Value("${indicator.auto-backfill.max-stocks-per-run:5}")
    private int autoBackfillMaxStocksPerRun;

    /**
     * Parses "Need at least N day[s] of price data to calculate X" messages
     * thrown by the indicator calculator classes in service/calculator/.
     * Returns -1 if no match. Accepts both singular ("day") and plural ("days").
     */
    private static final Pattern NEEDS_N_DAYS_PATTERN =
            Pattern.compile("Need at least (\\d+) (?:day|days)");

    /**
     * Per-instance accumulation of which stocks need deeper price-history
     * syncs because one of their indicators skipped due to insufficient data.
     * Key: stockId, Value: minimum number of days required (we take the max
     * across the stock's skipped indicators).
     *
     * Cleared once per calculateForAllStocks() invocation.
     */
    private final Map<Long, Integer> needyStocks = new ConcurrentHashMap<>();
    private final AtomicBoolean backfillInProgress = new AtomicBoolean(false);

    private final Map<IndicatorType, IndicatorCalculator> calculators = Map.ofEntries(
            Map.entry(IndicatorType.RSI, new RsiCalculator(12)),
            Map.entry(IndicatorType.SMA_20, new SmaCalculator(20)),
            Map.entry(IndicatorType.SMA_50, new SmaCalculator(50)),
            Map.entry(IndicatorType.SMA_200, new SmaCalculator(200)),
            Map.entry(IndicatorType.EMA_20, new EmaCalculator(20)),
            Map.entry(IndicatorType.MACD_LINE, new MacdLineCalculator()),
            Map.entry(IndicatorType.MACD_SIGNAL, new MacdSignalCalculator()),
            Map.entry(IndicatorType.BOLLINGER_UPPER, new BollingerUpperCalculator(20, 2.5)),
            Map.entry(IndicatorType.BOLLINGER_LOWER, new BollingerLowerCalculator(20, 2.5)),
            Map.entry(IndicatorType.STOCH_K, new StochKCalculator(14)),
            Map.entry(IndicatorType.STOCH_D, new StochDCalculator(14, 3)),
            Map.entry(IndicatorType.WILLIAMS_R, new WilliamsRCalculator(14)),
            Map.entry(IndicatorType.ATR, new ATRCalculator(14)),
            Map.entry(IndicatorType.CCI, new CCICalculator(20)),
            // New indicators (June 2026)
            Map.entry(IndicatorType.STOCH_RSI, new StochRsiCalculator(14, 14)),
            Map.entry(IndicatorType.ADX, new AdxCalculator(14)),
            Map.entry(IndicatorType.PLUS_DI, new PlusDiCalculator()),
            Map.entry(IndicatorType.MINUS_DI, new MinusDiCalculator()),
            Map.entry(IndicatorType.ULTIMATE_OSC, new UltimateOscillatorCalculator(7, 14, 28)),
            Map.entry(IndicatorType.ROC_12, new RocCalculator(12)),
            Map.entry(IndicatorType.OBV, new ObvCalculator()),
            // VWAP and Ichimoku (July 2026)
            Map.entry(IndicatorType.VWAP, new VwapCalculator()),
            Map.entry(IndicatorType.TENKAN_SEN, new IchimokuCalculator(IndicatorType.TENKAN_SEN)),
            Map.entry(IndicatorType.KIJUN_SEN, new IchimokuCalculator(IndicatorType.KIJUN_SEN)),
            Map.entry(IndicatorType.SENKOU_SPAN_A, new IchimokuCalculator(IndicatorType.SENKOU_SPAN_A)),
            Map.entry(IndicatorType.SENKOU_SPAN_B, new IchimokuCalculator(IndicatorType.SENKOU_SPAN_B)),
            Map.entry(IndicatorType.CHIKOU_SPAN, new IchimokuCalculator(IndicatorType.CHIKOU_SPAN))
    );

    public void calculateIndicatorsForStock(Long stockId) {
        log.info("Calculating indicators for stock ID: {}", stockId);

        // 1. Fetch price history (ordered by date ASC is critical for EMAs/RSI)
        List<DailyPrice> prices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId);

        if (prices.isEmpty()) {
            // No price data at all -> record need for a baseline sync.
            // Defer to a 30-day sync (will be picked up by auto-backfill loop).
            needyStocks.merge(stockId, 30, Math::max);
            log.warn("No price data found for stock ID: {}. Auto-backfill candidate.", stockId);
            return;
        }

        Stock stock = stockService.getStockById(stockId);
        LocalDate today = LocalDate.now();

        // 2. Iterate through all configured calculators
        calculators.forEach((type, calculator) -> {
            try {
                BigDecimal value = calculator.calculate(prices);
                persistenceService.saveOrUpdateIndicator(stock, type, value, today);
            } catch (IllegalArgumentException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                int requiredDays = parseRequiredDays(msg);
                if (requiredDays > 0) {
                    needyStocks.merge(stockId, requiredDays, Math::max);
                }
                if (log.isWarnEnabled()) {
                    log.warn("Skipping {} for stock {}: {} (auto-backfill stale)", type, stock.getSymbol(), msg);
                }
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error("Unexpected error calculating {} for stock {}: {}", type, stock.getSymbol(), e.getMessage(), e);
                }
            }
        });

        // Evict cached latest indicators for this stock so next fetch gets fresh data
        Cache latestCache = cacheManager.getCache("latestIndicators");
        if (latestCache != null) {
            latestCache.evict(stockId);
        }
        // BUG FIX: Removed historyCache.clear() which was wiping the entire cache after each stock,
        // causing a severe performance bottleneck. The indicatorHistory cache will be naturally
        // refreshed on next read.
    }

    /**
     * Parses the calculator exception message "Need at least N days..."
     * Returns -1 if the message does not match the expected pattern.
     */
    static int parseRequiredDays(String message) {
        if (message == null) return -1;
        Matcher m = NEEDS_N_DAYS_PATTERN.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    // No @Transactional here: persistenceService handles transactions per stock with REQUIRES_NEW.
    public void calculateForAllStocks() {
        log.info("Starting batch technical analysis for all stocks");
        List<Stock> stocks = stockService.getAllStocks();

        // Clear per-run accumulator before this batch.
        needyStocks.clear();

        int count = 0;
        for (Stock stock : stocks) {
            calculateIndicatorsForStock(stock.getId());
            count++;
        }

        // Process backfill queue collected during this run (bounded).
        if (autoBackfillEnabled && !needyStocks.isEmpty()) {
            processBackfillQueue();
        }

        log.info("Completed technical analysis for {} stocks", count);
    }

    /**
     * Triggers Yahoo Finance history syncs for stocks that lacked sufficient
     * data to compute one of their indicators. Capped at
     * {@code auto-backfill.max-stocks-per-run} to avoid blocking the popup
     * for an extended period and to stay friendly to API rate limits.
     */
    private void processBackfillQueue() {
        if (needyStocks.isEmpty()) return;

        // Sort by required depth descending: deepest-gap stocks get first chance today.
        List<Map.Entry<Long, Integer>> sortedEntries = needyStocks.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(autoBackfillMaxStocksPerRun)
                .collect(Collectors.toList());

        log.info("Auto-backfill enqueued for {} stocks (max per run: {}):",
                sortedEntries.size(), autoBackfillMaxStocksPerRun);
        for (Map.Entry<Long, Integer> entry : sortedEntries) {
            Long stockId = entry.getKey();
            int requiredDays = entry.getValue();
            int daysToSync = requiredDays + autoBackfillBufferDays;
            try {
                log.info("Stock {} needs {} days; syncing {} days from Yahoo Finance",
                        stockId, requiredDays, daysToSync);
                int saved = yahooFinanceSyncService.syncHistory(stockId, daysToSync);
                log.info("Backfill complete for stock {}: {} rows saved", stockId, saved);

                // Recalculate indicators now that we have sufficient price data.
                // Without this, the indicators that failed due to insufficient data
                // would remain missing until the next scheduled run (Gap 2 fix).
                try {
                    calculateIndicatorsForStock(stockId);
                    log.info("Recalculated indicators for stock {} after auto-backfill sync", stockId);
                } catch (Exception e) {
                    log.warn("Indicator recalculation after auto-backfill failed for stock {}: {}", stockId, e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Auto-backfill failed for stock {}: {}", stockId, e.getMessage());
            }
        }
    }

    /**
     * Backfills historical technical indicator values for all stocks.
     * For each stock, walks through every trading day in the specified date range
     * and computes + persists all 27+ indicators for that day using only price
     * data available up to that point. Skips dates where all indicators already
     * exist (idempotent).
     *
     * <h3>Performance</h3>
     * This is an O(N × M) operation per stock where N = number of trading days
     * and M = number of calculators (27). For ~252 trading days and ~30 stocks,
     * expect roughly 6800 calculator calls × 30 stocks ≈ 204k executions. Each
     * execution processes O(N) prices, so total time is a few minutes depending
     * on database speed.
     *
     * @param fromDate the earliest date to backfill (inclusive). Typically
     *                 {@code LocalDate.now().minusDays(365)} for a 1-year backfill.
     * @return total number of indicator records saved across all stocks
     */
    public int backfillHistoricalIndicators(LocalDate fromDate) {
        if (!backfillInProgress.compareAndSet(false, true)) {
            log.warn("Backfill already in progress — rejecting duplicate request");
            return -1;
        }
        try {
            return doBackfillHistoricalIndicators(fromDate);
        } finally {
            backfillInProgress.set(false);
        }
    }

    private int doBackfillHistoricalIndicators(LocalDate fromDate) {
        long startTime = System.currentTimeMillis();
        List<Stock> stocks = stockService.getAllStocks();
        int totalSaved = 0;

        if (fromDate == null) {
            fromDate = LocalDate.now().minusDays(365);
        }

        log.info("Starting historical indicator backfill from {} for {} stocks", fromDate, stocks.size());

        for (Stock stock : stocks) {
            try {
                List<DailyPrice> prices = dailyPriceRepository
                        .findAllByStockIdOrderByPriceDateAsc(stock.getId());

                if (prices.size() < 20) {
                    log.warn("Insufficient price data for {} ({} rows), skipping backfill",
                            stock.getSymbol(), prices.size());
                    continue;
                }

                int stockCount = 0;
                int totalCalculators = calculators.size();

                for (int i = 0; i < prices.size(); i++) {
                    DailyPrice currentPrice = prices.get(i);
                    LocalDate currentDate = currentPrice.getPriceDate();

                    // Skip dates outside the requested window
                    if (currentDate.isBefore(fromDate)) continue;
                    if (currentDate.isAfter(LocalDate.now())) break;

                    // Skip if ALL indicators already exist for this date (idempotent)
                    long existingCount = indicatorRepository.countByStockIdAndCalculationDate(
                            stock.getId(), currentDate);
                    if (existingCount >= totalCalculators) continue;

                    // Compute indicators using only prices up to this date
                    List<DailyPrice> pricesUpToDate = prices.subList(0, i + 1);

                    for (Map.Entry<IndicatorType, IndicatorCalculator> entry : calculators.entrySet()) {
                        try {
                            // No need for a separate existence check —
                            // saveOrUpdateIndicator() already performs an upsert
                            // (find-by-stock+type+date, update if exists, create if not).
                            // Removing the redundant per-indicator check cuts DB
                            // queries from 2 per indicator to 1 (~204k fewer queries
                            // across the full backfill).
                            BigDecimal value = entry.getValue().calculate(pricesUpToDate);
                            persistenceService.saveOrUpdateIndicator(
                                    stock, entry.getKey(), value, currentDate);
                            stockCount++;
                        } catch (IllegalArgumentException e) {
                            // Not enough price data for this indicator at this date
                            log.trace("Cannot compute {} for {} on {}: {}",
                                    entry.getKey(), stock.getSymbol(), currentDate, e.getMessage());
                        } catch (Exception e) {
                            log.warn("Error computing {} for {} on {}: {}",
                                    entry.getKey(), stock.getSymbol(), currentDate, e.getMessage());
                        }
                    }

                    if (stockCount % 500 == 0 && stockCount > 0) {
                        log.info("[{}] Progress: {} indicator records saved so far (at {})",
                                stock.getSymbol(), stockCount, currentDate);
                    }
                }

                totalSaved += stockCount;
                log.info("[{}] Backfill complete: {} indicator records saved (from {} to {})",
                        stock.getSymbol(), stockCount, fromDate, LocalDate.now());

            } catch (Exception e) {
                log.error("Failed to backfill indicators for stock {}: {}",
                        stock.getSymbol(), e.getMessage(), e);
            }
        }

        // Evict caches after full backfill
        Cache latestCache = cacheManager.getCache("latestIndicators");
        if (latestCache != null) latestCache.clear();
        Cache historyCache = cacheManager.getCache("indicatorHistory");
        if (historyCache != null) historyCache.clear();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Historical indicator backfill complete: {} records saved across {} stocks in {} ms",
                totalSaved, stocks.size(), elapsed);

        return totalSaved;
    }

    /**
     * Fills missing indicators for all stocks over the last N days.
     * For each date in the range, checks if all indicators exist for that stock+date.
     * If any are missing, recalculates them using prices up to that date.
     *
     * @param days number of days to look back (default: 7)
     * @return total number of indicator records filled
     */
    public int fillIndicatorGaps(int days) {
        long startTime = System.currentTimeMillis();
        List<Stock> stocks = stockService.getAllStocks();
        LocalDate fromDate = LocalDate.now().minusDays(days);
        int totalFilled = 0;

        log.info("Filling indicator gaps for last {} days (from {}) across {} stocks", days, fromDate, stocks.size());

        for (Stock stock : stocks) {
            try {
                List<DailyPrice> prices = dailyPriceRepository
                        .findAllByStockIdOrderByPriceDateAsc(stock.getId());

                if (prices.isEmpty()) {
                    log.warn("No price data for {}, skipping", stock.getSymbol());
                    continue;
                }

                int stockFilled = 0;
                int totalCalculators = calculators.size();

                for (int i = 0; i < prices.size(); i++) {
                    DailyPrice currentPrice = prices.get(i);
                    LocalDate currentDate = currentPrice.getPriceDate();

                    if (currentDate.isBefore(fromDate)) continue;
                    if (currentDate.isAfter(LocalDate.now())) break;

                    // Count existing indicators for this date
                    long existingCount = indicatorRepository.countByStockIdAndCalculationDate(
                            stock.getId(), currentDate);
                    if (existingCount >= totalCalculators) continue;

                    // Compute missing indicators using prices up to this date
                    List<DailyPrice> pricesUpToDate = prices.subList(0, i + 1);

                    for (Map.Entry<IndicatorType, IndicatorCalculator> entry : calculators.entrySet()) {
                        try {
                            BigDecimal value = entry.getValue().calculate(pricesUpToDate);
                            persistenceService.saveOrUpdateIndicator(
                                    stock, entry.getKey(), value, currentDate);
                            stockFilled++;
                        } catch (IllegalArgumentException e) {
                            // Not enough price data for this indicator at this date
                            log.trace("Cannot compute {} for {} on {}: {}",
                                    entry.getKey(), stock.getSymbol(), currentDate, e.getMessage());
                        } catch (Exception e) {
                            log.warn("Error computing {} for {} on {}: {}",
                                    entry.getKey(), stock.getSymbol(), currentDate, e.getMessage());
                        }
                    }
                }

                totalFilled += stockFilled;
                if (stockFilled > 0) {
                    log.info("[{}] Filled {} indicator records for last {} days",
                            stock.getSymbol(), stockFilled, days);
                }
            } catch (Exception e) {
                log.error("Failed to fill indicator gaps for {}: {}", stock.getSymbol(), e.getMessage(), e);
            }
        }

        // Evict caches after filling gaps
        Cache latestCache = cacheManager.getCache("latestIndicators");
        if (latestCache != null) latestCache.clear();
        Cache historyCache = cacheManager.getCache("indicatorHistory");
        if (historyCache != null) historyCache.clear();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Indicator gap fill complete: {} records filled across {} stocks in {} ms",
                totalFilled, stocks.size(), elapsed);

        return totalFilled;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "indicatorHistory", key = "#stockId + ':' + #type.name()")
    public List<IndicatorDto> getIndicatorHistory(Long stockId, IndicatorType type) {
        // Validate stock exists
        stockService.getStockById(stockId);
        List<TechnicalIndicator> entities = indicatorRepository
                .findByStockIdAndIndicatorTypeOrderByCalculationDateDesc(stockId, type);
        return entities.stream()
                .map(entity -> {
                    IndicatorDto dto = new IndicatorDto();
                    dto.setType(entity.getIndicatorType());
                    dto.setValue(entity.getValue());
                    dto.setCalculationDate(entity.getCalculationDate());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get all indicator values for a stock within a date range, grouped by calculation date.
     * Returns one IndicatorDto per indicator type per date, sorted by date ASC.
     */
    @Transactional(readOnly = true)
    public List<IndicatorDto> getIndicatorHistoryForRange(Long stockId, LocalDate fromDate, LocalDate toDate) {
        stockService.getStockById(stockId);
        List<TechnicalIndicator> entities = indicatorRepository
                .findByStockIdAndCalculationDateBetween(stockId, fromDate, toDate);
        return entities.stream()
                .map(entity -> {
                    IndicatorDto dto = new IndicatorDto();
                    dto.setType(entity.getIndicatorType());
                    dto.setValue(entity.getValue());
                    dto.setCalculationDate(entity.getCalculationDate());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "latestIndicators", key = "#stockId")
    @Transactional(readOnly = true)
    public List<IndicatorDto> getLatestIndicators(Long stockId) {
        stockService.getStockById(stockId);
        List<TechnicalIndicator> entities = indicatorRepository.findLatestForStock(stockId);
        List<IndicatorDto> result = new ArrayList<>(entities.size());
        for (TechnicalIndicator ti : entities) {
            IndicatorDto dto = new IndicatorDto();
            dto.setType(ti.getIndicatorType());
            dto.setValue(ti.getValue());
            dto.setCalculationDate(ti.getCalculationDate());
            result.add(dto);
        }
        return result;
    }
}
