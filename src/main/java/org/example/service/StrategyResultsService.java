package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ConsensusStockDTO;
import org.example.dto.RefreshSummary;
import org.example.dto.StrategyCountDTO;
import org.example.dto.StrategyStockResultDTO;
import org.example.dto.TopStockDTO;
import org.example.dto.TopStocksResponse;
import org.example.entity.Stock;
import org.example.entity.StrategyConfig;
import org.example.entity.StrategyStockResult;
import org.example.repository.StockRepository;
import org.example.repository.StrategyStockResultRepository;
import org.example.strategy.engine.MultiStrategySignalEngine;
import org.example.strategy.model.AggregatedSignalResult;
import org.example.strategy.model.StrategyResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Standalone "Strategy Results" page support.
 *
 * Recomputes the multi-strategy engine for every stock in the DB and stores one
 * per-day snapshot (rows keyed by stock_id + strategy_name + snapshot_date).
 * Reads always reflect the latest snapshot day only. Manual refresh overwrites
 * only the current day's rows; first visit auto-seeds when the table is empty.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyResultsService {

    /** Upper bound on rows returned by {@link #getConsensus}. */
    public static final int CONSENSUS_MAX_ROWS = 10;

    private final StrategyStockResultRepository strategyResultsRepository;
    private final StockRepository stockRepository;
    private final MultiStrategySignalEngine engine;
    private final StrategyConfigService strategyConfigService;

    /**
     * Recomputes all stocks through the multi-strategy engine and persists today's
     * snapshot, overwriting only today's rows (same-day overwrite). Prior days' history
     * is untouched. Skips stocks whose evaluation throws (e.g. no price data).
     */
    @Transactional
    public RefreshSummary refreshAll() {
        List<Stock> stocks = stockRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // Evaluate ALL strategies (active + disabled) so the results page can show every
        // strategy. The live dashboard uses the 1-arg evaluate() (active-only), so the real
        // aggregate signal is unaffected. Never empty (config always has strategies).
        Set<String> allStrategies = strategyConfigService.getAllConfigs().stream()
                .map(StrategyConfig::getStrategyName)
                .collect(Collectors.toSet());

        strategyResultsRepository.deleteBySnapshotDate(today);

        List<StrategyStockResult> rows = new ArrayList<>();
        int evaluated = 0;
        for (Stock stock : stocks) {
            try {
                AggregatedSignalResult agg = engine.evaluate(stock.getId(), allStrategies);
                for (StrategyResult sr : agg.breakdown()) {
                    StrategyStockResult row = new StrategyStockResult();
                    row.setStockId(stock.getId());
                    row.setStrategyName(sr.strategyName());
                    // Store AS-IS: the enum only emits BUY/SELL/HOLD (no STRONG_* values today)
                    row.setSignalType(sr.signal().name());
                    row.setReason(sr.reason());
                    row.setConfidence(sr.confidence());
                    row.setPriority(sr.priority());
                    row.setSnapshotDate(today);
                    row.setEventDate(sr.eventDate());
                    row.setComputedAt(now);
                    rows.add(row);
                }
                evaluated++;
            } catch (RuntimeException e) {
                log.warn("StrategyResultsService: skipping stock {} — evaluate failed: {}", stock.getId(), e.getMessage());
            }
        }

        strategyResultsRepository.saveAll(rows);
        return new RefreshSummary(rows.size(), evaluated, today, now);
    }

    /**
     * Aggregate BUY/SELL/HOLD counts per strategy for the latest snapshot day.
     * Auto-seeds today's snapshot when the table is empty (first visit).
     */
    public List<StrategyCountDTO> getCounts() {
        if (strategyResultsRepository.count() == 0) {
            refreshAll();
        }

        LocalDate maxDate = strategyResultsRepository.findMaxSnapshotDate();
        if (maxDate == null) {
            return List.of();
        }

        List<StrategyStockResult> rows = strategyResultsRepository.findAllBySnapshotDate(maxDate);

        List<StrategyConfig> configs = strategyConfigService.getAllConfigs();
        Map<String, String> displayNames = configs.stream()
                .collect(Collectors.toMap(StrategyConfig::getStrategyName,
                        c -> c.getDisplayName() == null ? c.getStrategyName() : c.getDisplayName(),
                        (a, b) -> a));
        Map<String, Boolean> activeFlags = configs.stream()
                .collect(Collectors.toMap(StrategyConfig::getStrategyName, StrategyConfig::isActive, (a, b) -> a));

        Map<String, StrategyCountDTO> countsByName = new LinkedHashMap<>();
        for (StrategyStockResult row : rows) {
            String name = row.getStrategyName();
            StrategyCountDTO current = countsByName.computeIfAbsent(name,
                    n -> new StrategyCountDTO(n, displayNames.getOrDefault(n, n), 0, 0, 0,
                            row.getPriority() == null ? 0 : row.getPriority(), activeFlags.getOrDefault(n, false)));
            int buy = current.buyCount() + ("BUY".equals(row.getSignalType()) ? 1 : 0);
            int sell = current.sellCount() + ("SELL".equals(row.getSignalType()) ? 1 : 0);
            int hold = current.holdCount() + ("HOLD".equals(row.getSignalType()) ? 1 : 0);
            countsByName.put(name,
                    new StrategyCountDTO(name, displayNames.getOrDefault(name, name), buy, sell, hold,
                            current.priority(), current.active()));
        }

        return countsByName.values().stream()
                .sorted(Comparator.comparingInt(StrategyCountDTO::priority).reversed()
                        .thenComparing(StrategyCountDTO::strategyName))
                .collect(Collectors.toList());
    }

    /**
     * Top 5 consensus — for each stock, count how many (active) strategies agree on
     * BUY (or SELL). Sort high→low by count, tie-breaker avgConfidence DESC, then symbol.
     * Respects the includeInactive toggle: when false, disabled strategies are excluded from the count.
     */
    public TopStocksResponse getTopStocks(int limit, boolean includeInactive) {
        if (strategyResultsRepository.count() == 0) {
            refreshAll();
        }
        LocalDate maxDate = strategyResultsRepository.findMaxSnapshotDate();
        if (maxDate == null) {
            return new TopStocksResponse(List.of(), List.of());
        }
        List<StrategyStockResult> rows = strategyResultsRepository.findAllBySnapshotDate(maxDate);
        if (rows.isEmpty()) {
            return new TopStocksResponse(List.of(), List.of());
        }

        List<StrategyConfig> configs = strategyConfigService.getAllConfigs();
        Map<String, Boolean> activeFlags = configs.stream()
                .collect(Collectors.toMap(StrategyConfig::getStrategyName, StrategyConfig::isActive, (a, b) -> a));
        Map<String, String> displayNames = configs.stream()
                .collect(Collectors.toMap(StrategyConfig::getStrategyName,
                        c -> c.getDisplayName() == null ? c.getStrategyName() : c.getDisplayName(), (a, b) -> a));

        List<StrategyStockResult> filtered = includeInactive ? rows
                : rows.stream().filter(r -> Boolean.TRUE.equals(activeFlags.get(r.getStrategyName()))).toList();

        Map<Long, String> symbolById = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getSymbol, (a, b) -> a));

        return new TopStocksResponse(
                rankBySignal(filtered, symbolById, displayNames, "BUY", limit),
                rankBySignal(filtered, symbolById, displayNames, "SELL", limit)
        );
    }

    /**
     * Full cross-strategy consensus ranking for one signal, derived from the
     * latest persisted snapshot day.
     *
     * <p>Reuses the same snapshot fetch as {@link #getTopStocks} and the same
     * agreement grouping, so it costs no extra query. Unlike that method it
     * returns more rows (up to {@code CONSENSUS_MAX_ROWS}) and carries the stock
     * name plus the denominator behind each agreement count.
     *
     * <p>Only directional signals are meaningful here, so HOLD is not accepted.
     *
     * @param signal          BUY or SELL
     * @param includeInactive when false, disabled strategies are excluded
     * @param limit           maximum rows to return, clamped to [1, {@value #CONSENSUS_MAX_ROWS}]
     */
    public List<ConsensusStockDTO> getConsensus(String signal, boolean includeInactive, int limit) {
        if (signal == null || (!"BUY".equalsIgnoreCase(signal) && !"SELL".equalsIgnoreCase(signal))) {
            throw new IllegalArgumentException("signal must be BUY or SELL");
        }
        String target = signal.toUpperCase();

        LocalDate maxDate = strategyResultsRepository.findMaxSnapshotDate();
        if (maxDate == null) {
            return List.of();
        }
        List<StrategyStockResult> rows = strategyResultsRepository.findAllBySnapshotDate(maxDate);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<StrategyConfig> configs = strategyConfigService.getAllConfigs();
        Map<String, Boolean> activeFlags = configs.stream()
                .collect(Collectors.toMap(StrategyConfig::getStrategyName, StrategyConfig::isActive, (a, b) -> a));
        Map<String, String> displayNames = configs.stream()
                .collect(Collectors.toMap(StrategyConfig::getStrategyName,
                        c -> c.getDisplayName() == null ? c.getStrategyName() : c.getDisplayName(), (a, b) -> a));

        List<StrategyStockResult> filtered = includeInactive ? rows
                : rows.stream().filter(r -> Boolean.TRUE.equals(activeFlags.get(r.getStrategyName()))).toList();

        Map<Long, Stock> stocksById = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getId, s -> s, (a, b) -> a));

        // Denominator: strategies that produced any verdict for this stock in
        // the filtered set, so "3 of 12" reflects the strategies that actually ran.
        Map<Long, Long> evaluatedPerStock = filtered.stream()
                .collect(Collectors.groupingBy(StrategyStockResult::getStockId, Collectors.counting()));

        Map<Long, List<StrategyStockResult>> agreeing = filtered.stream()
                .filter(r -> target.equals(r.getSignalType()))
                .collect(Collectors.groupingBy(StrategyStockResult::getStockId));

        return agreeing.entrySet().stream()
                .map(e -> {
                    Long stockId = e.getKey();
                    List<StrategyStockResult> list = e.getValue();
                    Stock stock = stocksById.get(stockId);
                    double avgConf = list.stream()
                            .mapToDouble(r -> r.getConfidence() == null ? 0.0 : r.getConfidence())
                            .average().orElse(0.0);
                    List<String> names = list.stream().map(StrategyStockResult::getStrategyName).sorted().toList();
                    List<String> disp = names.stream().map(n -> displayNames.getOrDefault(n, n)).toList();
                    return new ConsensusStockDTO(
                            stockId,
                            stock == null ? "" : stock.getSymbol(),
                            stock == null || stock.getName() == null ? "" : stock.getName(),
                            target,
                            list.size(),
                            evaluatedPerStock.getOrDefault(stockId, (long) list.size()).intValue(),
                            avgConf,
                            names,
                            disp);
                })
                .sorted(Comparator.comparingInt(ConsensusStockDTO::agreeCount).reversed()
                        .thenComparing(Comparator.comparingDouble(ConsensusStockDTO::avgConfidence).reversed())
                        .thenComparing(ConsensusStockDTO::symbol))
                .limit(Math.max(1, Math.min(limit, CONSENSUS_MAX_ROWS)))
                .toList();
    }

    private List<TopStockDTO> rankBySignal(List<StrategyStockResult> rows, Map<Long, String> symbolById,
                                           Map<String, String> displayNames, String signal, int limit) {
        Map<Long, List<StrategyStockResult>> byStock = rows.stream()
                .filter(r -> signal.equals(r.getSignalType()))
                .collect(Collectors.groupingBy(StrategyStockResult::getStockId));

        return byStock.entrySet().stream()
                .map(e -> {
                    Long stockId = e.getKey();
                    List<StrategyStockResult> list = e.getValue();
                    int count = list.size();
                    double avgConf = list.stream().mapToDouble(r -> r.getConfidence() == null ? 0.0 : r.getConfidence()).average().orElse(0.0);
                    List<String> names = list.stream().map(StrategyStockResult::getStrategyName).toList();
                    List<String> disp = names.stream().map(n -> displayNames.getOrDefault(n, n)).toList();
                    return new TopStockDTO(stockId, symbolById.getOrDefault(stockId, ""), count, avgConf, names, disp);
                })
                .sorted(Comparator.comparingInt(TopStockDTO::count).reversed()
                        .thenComparing(Comparator.comparingDouble(TopStockDTO::avgConfidence).reversed())
                        .thenComparing(TopStockDTO::symbol))
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();
    }

    /**
     * All rows for one strategy from the latest snapshot day (strategy expansion).
     * Returns every signal — the frontend filters out HOLD rows.
     */
    public List<StrategyStockResultDTO> getStocksFor(String strategyName) {
        LocalDate maxDate = strategyResultsRepository.findMaxSnapshotDate();
        if (maxDate == null) {
            return List.of();
        }

        List<Stock> allStocks = stockRepository.findAll();
        Map<Long, String> symbolById = allStocks.stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getSymbol));
        Map<Long, String> nameById = allStocks.stream()
                .collect(Collectors.toMap(Stock::getId, s -> s.getName() == null ? "" : s.getName(),
                        (a, b) -> a));

        return strategyResultsRepository.findAllByStrategyNameAndSnapshotDate(strategyName, maxDate).stream()
                .map(row -> new StrategyStockResultDTO(
                        row.getStockId(),
                        symbolById.getOrDefault(row.getStockId(), ""),
                        nameById.getOrDefault(row.getStockId(), ""),
                        row.getSignalType(),
                        row.getConfidence(),
                        row.getReason(),
                        row.getEventDate()))
                .collect(Collectors.toList());
    }
}
