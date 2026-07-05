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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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

        Set<String> effectiveActive = (activeStrategyNames == null || activeStrategyNames.isEmpty())
                ? strategyConfigService.getActiveStrategyNames()
                : activeStrategyNames;
        AggregatedSignalResult result = aggregator.aggregate(stockId, indicators, prices, effectiveActive);
        log.info("Multi-strategy signal for stock {}: {} (score={}, active={})",
                stockId, result.finalSignal(), result.score(), effectiveActive);

        return result;
    }

    /**
     * Evaluates multi-strategy signals across historical dates for a stock.
     * Computes the aggregated signal for each price date using active strategy names.
     *
     * @param stockId             the stock identifier
     * @param days               how far back to go from today
     * @param activeStrategyNames optional set of strategy names to evaluate;
     *                            if null or empty all active strategies are used
     * @return chronologically sorted list of signal history points (oldest first)
     */
    public List<SignalHistoryPoint> evaluateHistory(Long stockId, int days, Set<String> activeStrategyNames) {
        long startTime = System.currentTimeMillis();
        List<DailyPrice> prices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId);
        if (prices.size() < 20) return List.of();

        LocalDate cutoff = LocalDate.now().minusDays(days);
        List<DailyPrice> windowed = prices.stream()
                .filter(p -> !p.getPriceDate().isBefore(cutoff))
                .collect(Collectors.toList());

        if (windowed.size() < 20) return List.of();

        Set<String> effectiveActive = (activeStrategyNames == null || activeStrategyNames.isEmpty())
                ? strategyConfigService.getActiveStrategyNames()
                : activeStrategyNames;

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

        // Adaptive step for performance (CRITICAL: reduces computations by 90%+)
        int step;
        if (days <= 30) step = 1;
        else if (days <= 90) step = 5;
        else step = 10; // For 365 days: only ~37 computations instead of ~365

        List<SignalHistoryPoint> history = new ArrayList<>();
        int computedCount = 0;

        for (int i = firstWindowedIndex; i < prices.size(); i++) {
            if (prices.get(i).getPriceDate().isBefore(cutoff)) {
                continue;
            }
            if ((i - firstWindowedIndex) % step != 0) {
                continue; // SKIP most dates based on step
            }
            
            List<DailyPrice> subList = prices.subList(0, i + 1);
            LocalDate date = prices.get(i).getPriceDate();
            try {
                // Compute indicators for this date
                List<TechnicalIndicator> computed = new ArrayList<>(indicatorComputationService.computeIndicators(stockId, date, subList));
                computedCount++;
                
                // Compute previous day's indicators for crossover detection
                if (i > 0) {
                    try {
                        List<DailyPrice> prevSubList = prices.subList(0, i);
                        LocalDate prevDate = prices.get(i - 1).getPriceDate();
                        List<TechnicalIndicator> prevComputed = indicatorComputationService.computeIndicators(stockId, prevDate, prevSubList);
                        computed.addAll(prevComputed);
                    } catch (Exception e) {
                        log.debug("Could not compute previous indicators for stock {} on date {}: {}", stockId, date, e.getMessage());
                    }
                }
                
                AggregatedSignalResult result = aggregator.aggregate(stockId, computed, subList, effectiveActive);
                List<StrategyBreakdownDTO> breakdown = result.breakdown().stream()
                        .map(sr -> toBreakdownDTO(sr, strategyConfigService.getPriority(sr.strategyName())))
                        .collect(Collectors.toList());
                SignalHistoryPoint point = new SignalHistoryPoint(
                        date,
                        result.finalSignal().name(),
                        (int) Math.round(result.score()),
                        prices.get(i).getClosingPrice(),
                        null, null,
                        0, 0, 0, 0, 0, 0, null, 0, 0, 0,
                        null, null, null, null,
                        0, 0, 0, null, 0, 0, 0, 0,
                        breakdown, buyThreshold, sellThreshold, result.score()
                );
                history.add(point);
            } catch (Exception e) {
                log.warn("Failed to evaluate multi-strategy signal for stock {} on date {}: {}",
                        stockId, date, e.getMessage());
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[Stock {}] Multi-strategy history: {} points in {} ms (step={}, actual_computations={})",
                stockId, history.size(), totalTime, step, computedCount);

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
