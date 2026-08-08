package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.RefreshSummary;
import org.example.dto.StrategyCountDTO;
import org.example.dto.StrategyStockResultDTO;
import org.example.entity.Stock;
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

    private final StrategyStockResultRepository strategyResultsRepository;
    private final StockRepository stockRepository;
    private final MultiStrategySignalEngine engine;

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

        strategyResultsRepository.deleteBySnapshotDate(today);

        List<StrategyStockResult> rows = new ArrayList<>();
        int evaluated = 0;
        for (Stock stock : stocks) {
            try {
                AggregatedSignalResult agg = engine.evaluate(stock.getId());
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

        Map<String, StrategyCountDTO> countsByName = new LinkedHashMap<>();
        for (StrategyStockResult row : rows) {
            StrategyCountDTO current = countsByName.computeIfAbsent(row.getStrategyName(),
                    name -> new StrategyCountDTO(name, 0, 0, 0, row.getPriority() == null ? 0 : row.getPriority()));
            int buy = current.buyCount() + ("BUY".equals(row.getSignalType()) ? 1 : 0);
            int sell = current.sellCount() + ("SELL".equals(row.getSignalType()) ? 1 : 0);
            int hold = current.holdCount() + ("HOLD".equals(row.getSignalType()) ? 1 : 0);
            countsByName.put(row.getStrategyName(),
                    new StrategyCountDTO(row.getStrategyName(), buy, sell, hold, current.priority()));
        }

        return countsByName.values().stream()
                .sorted(Comparator.comparingInt(StrategyCountDTO::priority).reversed()
                        .thenComparing(StrategyCountDTO::strategyName))
                .collect(Collectors.toList());
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

        Map<Long, String> symbolById = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getSymbol));

        return strategyResultsRepository.findAllByStrategyNameAndSnapshotDate(strategyName, maxDate).stream()
                .map(row -> new StrategyStockResultDTO(
                        row.getStockId(),
                        symbolById.getOrDefault(row.getStockId(), ""),
                        row.getSignalType(),
                        row.getConfidence(),
                        row.getReason()))
                .collect(Collectors.toList());
    }
}
