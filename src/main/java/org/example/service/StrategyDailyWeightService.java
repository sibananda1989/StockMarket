package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.SignalHistoryPoint;
import org.example.dto.StrategyBreakdownDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.entity.StrategyDailyWeight;
import org.example.entity.TechnicalIndicator;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StockRepository;
import org.example.repository.StrategyDailyWeightRepository;
import org.example.service.calculator.IndicatorComputationService;
import org.example.strategy.aggregator.StrategySignalAggregator;
import org.example.strategy.model.AggregatedSignalResult;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StrategyDailyWeightService {

    private final StrategyDailyWeightRepository weightRepository;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final IndicatorComputationService indicatorComputationService;
    private final StrategySignalAggregator aggregator;
    private final StrategyConfigService strategyConfigService;
    private final Executor backfillExecutor;

    public StrategyDailyWeightService(
            StrategyDailyWeightRepository weightRepository,
            StockRepository stockRepository,
            DailyPriceRepository dailyPriceRepository,
            IndicatorComputationService indicatorComputationService,
            StrategySignalAggregator aggregator,
            StrategyConfigService strategyConfigService,
            @Qualifier("backfillExecutor") Executor backfillExecutor) {
        this.weightRepository = weightRepository;
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorComputationService = indicatorComputationService;
        this.aggregator = aggregator;
        this.strategyConfigService = strategyConfigService;
        this.backfillExecutor = backfillExecutor;
    }

    private volatile long lastComputedCount = 0;
    private volatile LocalDateTime lastComputedTime = null;

    /**
     * Computes and stores per-strategy weights for a single stock on a given date.
     *
     * @param stock the stock to compute for
     * @param date  the signal date
     * @return the number of strategy weights saved (0 if insufficient data)
     */
    @Transactional
    public int computeForStock(Stock stock, LocalDate date) {
        List<DailyPrice> allPrices = dailyPriceRepository
                .findAllByStockIdOrderByPriceDateAsc(stock.getId());

        if (allPrices.size() < 20) {
            return 0;
        }

        // Find prices up to and including the target date
        List<DailyPrice> pricesUpToDate = allPrices.stream()
                .filter(p -> !p.getPriceDate().isAfter(date))
                .collect(Collectors.toList());

        if (pricesUpToDate.size() < 20) {
            return 0;
        }

        // Compute indicators for this date
        List<TechnicalIndicator> indicators = new ArrayList<>(
                indicatorComputationService.computeIndicators(stock.getId(), date, pricesUpToDate));

        // Include previous day's indicators for crossover detection
        if (pricesUpToDate.size() >= 2) {
            try {
                LocalDate prevDate = pricesUpToDate.get(pricesUpToDate.size() - 2).getPriceDate();
                List<DailyPrice> prevPrices = pricesUpToDate.subList(0, pricesUpToDate.size() - 1);
                List<TechnicalIndicator> prevIndicators =
                        indicatorComputationService.computeIndicators(stock.getId(), prevDate, prevPrices);
                indicators.addAll(prevIndicators);
            } catch (Exception e) {
                log.debug("Could not compute previous indicators for stock {} on {}: {}",
                        stock.getId(), date, e.getMessage());
            }
        }

        // Get active strategies and config version
        Set<String> activeStrategyNames = strategyConfigService.getActiveStrategyNames();
        long configVersion = strategyConfigService.getConfigVersion();

        // Run aggregation
        AggregatedSignalResult result = aggregator.aggregate(
                stock.getId(), indicators, pricesUpToDate, activeStrategyNames);

        // Save individual strategy weights
        List<StrategyDailyWeight> weights = new ArrayList<>();
        for (StrategyResult sr : result.breakdown()) {
            StrategyDailyWeight weight = StrategyDailyWeight.builder()
                    .stock(stock)
                    .strategyName(sr.strategyName())
                    .signalDate(date)
                    .signalType(sr.signal().name())
                    .confidence(sr.confidence())
                    .contribution(sr.contribution())
                    .priority(sr.priority())
                    .reason(sr.reason())
                    .configVersion(configVersion)
                    .latestVolume(sr.latestVolume())
                    .avgVolume(sr.avgVolume())
                    .spikeThreshold(sr.spikeThreshold())
                    .computedAt(LocalDateTime.now())
                    .build();
            weights.add(weight);
        }

        if (!weights.isEmpty()) {
            weightRepository.saveAll(weights);
        }

        return weights.size();
    }

    /**
     * Computes and stores per-strategy weights for all stocks on a given date.
     * Uses parallel execution for performance.
     *
     * @param date the signal date
     */
    @Transactional
    public void computeForAllStocks(LocalDate date) {
        long startTime = System.currentTimeMillis();
        List<Stock> stocks = stockRepository.findAll();

        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (Stock stock : stocks) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return computeForStock(stock, date);
                } catch (Exception e) {
                    log.warn("Failed to compute weights for stock {} on {}: {}",
                            stock.getSymbol(), date, e.getMessage());
                    return 0;
                }
            }, backfillExecutor));
        }

        int totalWeights = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .mapToInt(Integer::intValue)
                .sum();

        lastComputedCount = stocks.size();
        lastComputedTime = LocalDateTime.now();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Computed strategy weights for {} stocks ({} total weights) on {} in {} ms",
                stocks.size(), totalWeights, date, elapsed);
    }

    /**
     * Backfills per-strategy weights for a single stock over a date range.
     * Uses batch insert for efficiency.
     *
     * @param stock the stock to backfill
     * @param days  number of days to backfill
     */
    @Transactional
    public void backfillForStock(Stock stock, int days) {
        long startTime = System.currentTimeMillis();
        log.info("Starting backfill for stock {} ({} days)...", stock.getSymbol(), days);

        List<DailyPrice> allPrices = dailyPriceRepository
                .findAllByStockIdOrderByPriceDateAsc(stock.getId());

        if (allPrices.size() < 20) {
            log.warn("Insufficient price data for stock {} ({} prices), skipping backfill",
                    stock.getSymbol(), allPrices.size());
            return;
        }

        LocalDate cutoff = LocalDate.now().minusDays(days);
        List<LocalDate> datesToCompute = allPrices.stream()
                .map(DailyPrice::getPriceDate)
                .filter(d -> !d.isBefore(cutoff))
                .collect(Collectors.toList());

        // Delete existing weights in the range to avoid duplicates
        weightRepository.deleteByStockIdAndSignalDateBetween(
                stock.getId(), cutoff, LocalDate.now());

        long configVersion = strategyConfigService.getConfigVersion();
        List<StrategyDailyWeight> batch = new ArrayList<>();
        int computedCount = 0;

        for (LocalDate date : datesToCompute) {
            try {
                List<DailyPrice> pricesUpToDate = allPrices.stream()
                        .filter(p -> !p.getPriceDate().isAfter(date))
                        .collect(Collectors.toList());

                if (pricesUpToDate.size() < 20) {
                    continue;
                }

                List<TechnicalIndicator> indicators = new ArrayList<>(
                        indicatorComputationService.computeIndicators(
                                stock.getId(), date, pricesUpToDate));

                // Include previous day's indicators for crossover detection
                if (pricesUpToDate.size() >= 2) {
                    try {
                        LocalDate prevDate = pricesUpToDate.get(pricesUpToDate.size() - 2).getPriceDate();
                        List<DailyPrice> prevPrices = pricesUpToDate.subList(0, pricesUpToDate.size() - 1);
                        List<TechnicalIndicator> prevIndicators =
                                indicatorComputationService.computeIndicators(
                                        stock.getId(), prevDate, prevPrices);
                        indicators.addAll(prevIndicators);
                    } catch (Exception e) {
                        log.debug("Could not compute prev indicators for stock {} on {}: {}",
                                stock.getId(), date, e.getMessage());
                    }
                }

                Set<String> activeStrategyNames = strategyConfigService.getActiveStrategyNames();
                AggregatedSignalResult result = aggregator.aggregate(
                        stock.getId(), indicators, pricesUpToDate, activeStrategyNames);

                for (StrategyResult sr : result.breakdown()) {
                    batch.add(StrategyDailyWeight.builder()
                            .stock(stock)
                            .strategyName(sr.strategyName())
                            .signalDate(date)
                            .signalType(sr.signal().name())
                            .confidence(sr.confidence())
                            .contribution(sr.contribution())
                            .priority(sr.priority())
                            .reason(sr.reason())
                            .configVersion(configVersion)
                            .latestVolume(sr.latestVolume())
                            .avgVolume(sr.avgVolume())
                            .spikeThreshold(sr.spikeThreshold())
                            .computedAt(LocalDateTime.now())
                            .build());
                }

                computedCount++;

                // Flush batch every 50 dates to avoid memory issues
                if (batch.size() >= 500) {
                    weightRepository.saveAll(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                log.debug("Failed to compute weights for stock {} on {}: {}",
                        stock.getSymbol(), date, e.getMessage());
            }
        }

        // Save remaining batch
        if (!batch.isEmpty()) {
            weightRepository.saveAll(batch);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Backfill completed for stock {}: {} dates computed in {} ms",
                stock.getSymbol(), computedCount, elapsed);
    }

    /**
     * Backfills per-strategy weights for all stocks in parallel.
     *
     * @param days number of days to backfill
     */
    @Async
    public void backfillAllStocks(int days) {
        long startTime = System.currentTimeMillis();
        log.info("Starting parallel backfill for all stocks ({} days)...", days);

        List<Stock> stocks = stockRepository.findAll();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Stock stock : stocks) {
            try {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        backfillForStock(stock, days);
                    } catch (Exception e) {
                        log.warn("Backfill failed for stock {}: {}", stock.getSymbol(), e.getMessage());
                    }
                }, backfillExecutor));
            } catch (RejectedExecutionException e) {
                log.error("Backfill task rejected for stock {}: {}", stock.getSymbol(), e.getMessage());
                // Continue with next stock instead of killing the loop
            }
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Parallel backfill completed for {} stocks in {} ms", stocks.size(), elapsed);
    }

    /**
     * Retrieves pre-calculated weighted history for a stock.
     * Aggregates per-date strategy weights into SignalHistoryPoint objects.
     *
     * @param stockId   the stock identifier
     * @param startDate start date (inclusive)
     * @param endDate   end date (inclusive)
     * @return list of SignalHistoryPoint sorted chronologically, or null if no data
     */
    @Transactional(readOnly = true)
    public List<SignalHistoryPoint> getWeightedHistory(Long stockId, LocalDate startDate, LocalDate endDate) {
        List<StrategyDailyWeight> weights = weightRepository
                .findByStockIdAndSignalDateBetweenOrderBySignalDateAsc(stockId, startDate, endDate);

        if (weights.isEmpty()) {
            return null;
        }

        // Group by date
        Map<LocalDate, List<StrategyDailyWeight>> byDate = weights.stream()
                .collect(Collectors.groupingBy(StrategyDailyWeight::getSignalDate));

        // Get latest prices for the stock for price display
        List<DailyPrice> prices = dailyPriceRepository
                .findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(stockId, startDate, endDate);
        Map<LocalDate, BigDecimal> priceByDate = prices.stream()
                .collect(Collectors.toMap(
                        DailyPrice::getPriceDate,
                        DailyPrice::getClosingPrice,
                        (a, b) -> a));

        // Get config thresholds
        double buyThreshold = 3.0;
        double sellThreshold = -3.0;

        List<SignalHistoryPoint> history = new ArrayList<>();

        for (Map.Entry<LocalDate, List<StrategyDailyWeight>> entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<StrategyDailyWeight> dayWeights = entry.getValue();

            // Aggregate: sum contributions, determine final signal
            double totalScore = dayWeights.stream()
                    .mapToDouble(StrategyDailyWeight::getContribution)
                    .sum();

            String finalSignal;
            if (totalScore >= buyThreshold) {
                finalSignal = "BUY";
            } else if (totalScore <= sellThreshold) {
                finalSignal = "SELL";
            } else {
                finalSignal = "HOLD";
            }

            // Build strategy breakdown
            List<StrategyBreakdownDTO> breakdown = dayWeights.stream()
                    .map(w -> {
                        double signalNumeric = switch (w.getSignalType()) {
                            case "BUY" -> 1.0;
                            case "SELL" -> -1.0;
                            default -> 0.0;
                        };
                        return new StrategyBreakdownDTO(
                                w.getStrategyName(),
                                w.getSignalType(),
                                w.getConfidence(),
                                w.getPriority(),
                                signalNumeric * w.getPriority() * w.getConfidence(),
                                w.getReason(),
                                w.getLatestVolume(),
                                w.getAvgVolume(),
                                w.getSpikeThreshold());
                    })
                    .collect(Collectors.toList());

            BigDecimal latestPrice = priceByDate.getOrDefault(date, BigDecimal.ZERO);

            SignalHistoryPoint point = new SignalHistoryPoint(
                    date,
                    finalSignal,
                    (int) Math.round(totalScore),
                    latestPrice,
                    null, null,
                    0, 0, 0, 0, 0, 0, null, 0, 0, 0,
                    null, null, null, null,
                    0, 0, 0, null, 0, 0, 0, 0,
                    breakdown, buyThreshold, sellThreshold, totalScore);

            history.add(point);
        }

        return history.stream()
                .sorted(Comparator.comparing(SignalHistoryPoint::getPriceDate))
                .collect(Collectors.toList());
    }

    /**
     * Invalidates (deletes) stale pre-calculated weights that were computed
     * with an older config version.
     *
     * @param oldVersion the config version to invalidate
     */
    @Transactional
    public void invalidateByConfigVersion(long oldVersion) {
        long currentVersion = strategyConfigService.getConfigVersion();
        if (oldVersion >= currentVersion) {
            log.debug("No config version change, skipping invalidation");
            return;
        }

        List<Stock> stocks = stockRepository.findAll();
        int totalDeleted = 0;
        for (Stock stock : stocks) {
            List<StrategyDailyWeight> stale = weightRepository
                    .findByStockIdAndSignalDateAfterOrderBySignalDateAsc(
                            stock.getId(), LocalDate.now().minusDays(365));
            List<StrategyDailyWeight> toDelete = stale.stream()
                    .filter(w -> w.getConfigVersion() < currentVersion)
                    .toList();
            if (!toDelete.isEmpty()) {
                weightRepository.deleteAll(toDelete);
                totalDeleted += toDelete.size();
            }
        }

        if (totalDeleted > 0) {
            log.info("Invalidated {} stale weight records (config version < {})",
                    totalDeleted, currentVersion);
        }
    }

    /**
     * Returns the total number of stocks processed in the last computation.
     */
    public long getProcessedStockCount() {
        return lastComputedCount;
    }

    /**
     * Returns the time of the last computation.
     */
    public LocalDateTime getLastComputedTime() {
        return lastComputedTime;
    }

    /**
     * Counts total weight records in the database.
     */
    @Transactional(readOnly = true)
    public long getTotalWeightCount() {
        return weightRepository.count();
    }

    /**
     * Counts weight records for a specific stock.
     */
    @Transactional(readOnly = true)
    public long getWeightCountForStock(Long stockId) {
        return weightRepository.countByStockId(stockId);
    }
}
