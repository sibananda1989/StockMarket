package org.example.strategy.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.SignalHistoryPoint;
import org.example.dto.StrategyBreakdownDTO;
import org.example.entity.DailyPrice;
import org.example.entity.TechnicalIndicator;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.DailyPriceRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.StrategyConfigService;
import org.example.service.calculator.IndicatorComputationService;
import org.example.strategy.aggregator.StrategySignalAggregator;
import org.example.strategy.model.AggregatedSignalResult;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Public entry point for the multi-strategy signal system.
 * Fetches required data and delegates to the aggregator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiStrategySignalEngine {

    private final DailyPriceRepository dailyPriceRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final IndicatorComputationService indicatorComputationService;
    private final StrategySignalAggregator aggregator;
    private final StrategyConfigService strategyConfigService;

    @Value("${strategy.aggregator.buy-threshold:3.0}")
    private double buyThreshold;

    @Value("${strategy.aggregator.sell-threshold:-3.0}")
    private double sellThreshold;

    /**
     * Evaluates all strategies for a given stock.
     *
     * @param stockId the stock identifier
     * @return aggregated signal result
     * @throws ResourceNotFoundException if stockId is not found
     */
    public AggregatedSignalResult evaluate(Long stockId) {
        return evaluate(stockId, null);
    }

    /**
     * Evaluates strategies for a given stock, filtering by active strategy names.
     *
     * @param stockId             the stock identifier
     * @param activeStrategyNames optional set of strategy names to evaluate;
     *                            if null or empty all strategies are evaluated
     * @return aggregated signal result
     * @throws ResourceNotFoundException if stockId is not found
     */
    public AggregatedSignalResult evaluate(Long stockId, Set<String> activeStrategyNames) {
        log.info("Evaluating multi-strategy signal for stock {}", stockId);

        List<DailyPrice> prices = dailyPriceRepository.findByStockIdOrderByPriceDateDesc(stockId);
        if (prices.isEmpty()) {
            throw new ResourceNotFoundException("No price data found for stock " + stockId);
        }

        List<TechnicalIndicator> indicators = new ArrayList<>(technicalIndicatorRepository.findLatestForStock(stockId));
        boolean computedOnFly = false;
        if (indicators.isEmpty()) {
            log.warn("No technical indicators found for stock {} in DB, computing on-the-fly", stockId);
            indicators = new ArrayList<>(indicatorComputationService.computeIndicators(stockId, LocalDate.now(), prices));
            computedOnFly = true;
        }

        // Include previous day's indicators for crossover detection
        try {
            List<TechnicalIndicator> prevIndicators;
            if (computedOnFly && prices.size() >= 2) {
                List<DailyPrice> pricesAsc = new ArrayList<>(prices);
                Collections.reverse(pricesAsc);
                LocalDate prevDate = pricesAsc.get(pricesAsc.size() - 2).getPriceDate();
                prevIndicators = indicatorComputationService.computeIndicators(stockId, prevDate, pricesAsc.subList(0, pricesAsc.size() - 1));
            } else {
                List<LocalDate> latestDates = technicalIndicatorRepository.findLatestTwoCalculationDates(stockId);
                if (latestDates.size() >= 2) {
                    prevIndicators = technicalIndicatorRepository.findByStockIdAndCalculationDate(stockId, latestDates.get(1));
                } else {
                    prevIndicators = List.of();
                }
            }
            indicators.addAll(prevIndicators);
        } catch (Exception e) {
            log.debug("Could not load previous indicators for stock {}: {}", stockId, e.getMessage());
        }

        // If activeStrategyNames is explicitly passed, use it (even if empty = all disabled).
        // If null, fall back to system config.
        Set<String> effectiveActive = (activeStrategyNames != null)
                ? activeStrategyNames
                : strategyConfigService.getActiveStrategyNames();
        AggregatedSignalResult result = aggregator.aggregate(stockId, indicators, prices, effectiveActive);
        log.info("Multi-strategy signal for stock {}: {} (score={}, active={})",
                stockId, result.finalSignal(), result.score(), effectiveActive);

        return result;
    }

    /**
     * Shared thread pool for parallel signal history computation.
     * Reused across calls to avoid repeated thread creation overhead.
     */
    private static final ForkJoinPool SIGNAL_HISTORY_POOL = new ForkJoinPool(4);

    /**
     * Caffeine cache for multi-strategy signal history results.
     * Key is stockId + "|" + days + "|" + sorted strategy names.
     * Entries expire 5 minutes after write. Max 500 entries prevents memory leak.
     */
    private final Cache<String, List<SignalHistoryPoint>> historyCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(500)
            .recordStats()
            .build();

    /**
     * Builds a cache key for signal history results.
     * Includes strategies in the key so strategy changes produce cache misses.
     */
    private String buildHistoryCacheKey(Long stockId, int days, Set<String> activeStrategyNames) {
        return stockId + "|" + days + "|" + (activeStrategyNames != null
                ? activeStrategyNames.stream().sorted().collect(Collectors.joining(","))
                : "default");
    }

    /**
     * Evaluates multi-strategy signals across historical dates for a stock.
     * Computes the aggregated signal for EVERY price date using active strategy names.
     * Uses parallel execution with Caffeine caching for performance.
     *
     * @param stockId             the stock identifier
     * @param days               how far back to go from today
     * @param activeStrategyNames optional set of strategy names to evaluate;
     *                            if null or empty all active strategies are used
     * @return chronologically sorted list of signal history points (oldest first)
     */
    public List<SignalHistoryPoint> evaluateHistory(Long stockId, int days, Set<String> activeStrategyNames) {
        // Check cache first (Caffeine handles TTL and size limits automatically)
        String cacheKey = buildHistoryCacheKey(stockId, days, activeStrategyNames);
        List<SignalHistoryPoint> cached = historyCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("[Stock {}] Multi-strategy history cache hit for {}d (key={})", stockId, days, cacheKey);
            return cached;
        }

        long startTime = System.currentTimeMillis();
        List<DailyPrice> prices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId);
        if (prices.size() < 20) return List.of();

        LocalDate cutoff = LocalDate.now().minusDays(days);
        List<DailyPrice> windowed = prices.stream()
                .filter(p -> !p.getPriceDate().isBefore(cutoff))
                .collect(Collectors.toList());

        if (windowed.size() < 20) return List.of();

        // If activeStrategyNames is explicitly passed, use it (even if empty = all disabled).
        // If null, fall back to system config.
        Set<String> effectiveActive = (activeStrategyNames != null)
                ? activeStrategyNames
                : strategyConfigService.getActiveStrategyNames();

        int firstWindowedIndex = 0;
        if (!windowed.isEmpty()) {
            LocalDate firstWindowedDate = windowed.get(0).getPriceDate();
            for (int i = 0; i < prices.size(); i++) {
                if (!prices.get(i).getPriceDate().isBefore(firstWindowedDate)) {
                    firstWindowedIndex = i;
                    break;
                }
            }
        }

        // Compute signal for EVERY trading date — no adaptive stepping
        // Caffeine cache ensures subsequent loads are fast even for 1-year ranges
        List<CompletableFuture<SignalHistoryPoint>> futures = new ArrayList<>();

        for (int i = firstWindowedIndex; i < prices.size(); i++) {
            if (prices.get(i).getPriceDate().isBefore(cutoff)) {
                continue;
            }
            
            final int idx = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<DailyPrice> subList = prices.subList(0, idx + 1);
                LocalDate date = prices.get(idx).getPriceDate();
                try {
                    // Compute indicators for this date
                    List<TechnicalIndicator> computed = new ArrayList<>(
                            indicatorComputationService.computeIndicators(stockId, date, subList));
                    
                    // Compute previous day's indicators for crossover detection
                    if (idx > 0) {
                        try {
                            List<DailyPrice> prevSubList = prices.subList(0, idx);
                            LocalDate prevDate = prices.get(idx - 1).getPriceDate();
                            List<TechnicalIndicator> prevComputed =
                                    indicatorComputationService.computeIndicators(stockId, prevDate, prevSubList);
                            computed.addAll(prevComputed);
                        } catch (Exception e) {
                            log.debug("Could not compute previous indicators for stock {} on date {}: {}", stockId, date, e.getMessage());
                        }
                    }
                    
                    AggregatedSignalResult result = aggregator.aggregate(stockId, computed, subList, effectiveActive);
                    List<StrategyBreakdownDTO> breakdown = result.breakdown().stream()
                            .map(sr -> toBreakdownDTO(sr, strategyConfigService.getPriority(sr.strategyName())))
                            .collect(Collectors.toList());
                    return new SignalHistoryPoint(
                            date,
                            result.finalSignal().name(),
                            (int) Math.round(result.score()),
                            prices.get(idx).getClosingPrice(),
                            null, null,
                            0, 0, 0, 0, 0, 0, null, 0, 0, 0,
                            null, null, null, null,
                            0, 0, 0, null, 0, 0, 0, 0,
                            breakdown, buyThreshold, sellThreshold, result.score()
                    );
                } catch (Exception e) {
                    log.warn("Failed to evaluate multi-strategy signal for stock {} on date {}: {}",
                            stockId, date, e.getMessage());
                    return null;
                }
            }, SIGNAL_HISTORY_POOL));
        }

        List<SignalHistoryPoint> history = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return null; } })
                .filter(p -> p != null)
                .sorted(Comparator.comparing(SignalHistoryPoint::getPriceDate))
                .collect(Collectors.toList());
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[Stock {}] Multi-strategy history: {} points in {} ms (all dates, days={})",
                stockId, history.size(), totalTime, days);

        // Cache the result (Caffeine handles TTL and size limits automatically).
        // Skip caching empty results so next load retries.
        if (!history.isEmpty()) {
            historyCache.put(cacheKey, history);
        }

        return history;
    }

    private static StrategyBreakdownDTO toBreakdownDTO(StrategyResult sr, int actualPriority) {
        double signalNumeric = switch (sr.signal()) {
            case BUY -> 1.0;
            case SELL -> -1.0;
            case HOLD -> 0.0;
        };
        return new StrategyBreakdownDTO(
                sr.strategyName(),
                sr.signal().name(),
                sr.confidence(),
                actualPriority,
                signalNumeric * actualPriority * sr.confidence(),
                sr.reason()
        );
    }
}
