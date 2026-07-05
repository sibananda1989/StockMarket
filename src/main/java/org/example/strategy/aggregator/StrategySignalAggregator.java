package org.example.strategy.aggregator;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.StrategyConditionGroup;
import org.example.entity.TechnicalIndicator;
import org.example.service.StrategyConditionService;
import org.example.service.StrategyConfigService;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.AggregatedSignalResult;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Aggregates results from all trading strategies into a single weighted signal.
 *
 * <p>Weighted score formula:
 * {@code score = SUM(signal_numeric * priority * confidence)}
 * where BUY=+1, SELL=-1, HOLD=0.</p>
 *
 * <p>Final signal determined by configurable thresholds:
 * <ul>
 *   <li>score >= buyThreshold → BUY</li>
 *   <li>score <= sellThreshold → SELL</li>
 *   <li>otherwise → HOLD</li>
 * </ul></p>
 *
 * <p>Features:
 * <ul>
 *   <li>Fail-safe: continues execution even if a strategy fails</li>
 *   <li>Confidence: calculates overall confidence based on agreement</li>
 *   <li>Categorization: identifies supporting/opposing strategies</li>
 *   <li>Transparency: provides contribution breakdown per strategy</li>
 * </ul></p>
 */
@Component
@Slf4j
public class StrategySignalAggregator {

    private final List<TradingStrategy> strategies;
    private final double buyThreshold;
    private final double sellThreshold;
    private final StrategyConditionService strategyConditionService;
    private final StrategyConfigService strategyConfigService;

    public StrategySignalAggregator(
            List<TradingStrategy> strategies,
            StrategyConditionService strategyConditionService,
            StrategyConfigService strategyConfigService,
            @Value("${strategy.aggregator.buy-threshold:3.0}") double buyThreshold,
            @Value("${strategy.aggregator.sell-threshold:-3.0}") double sellThreshold) {
        this.strategies = strategies;
        this.strategyConditionService = strategyConditionService;
        this.strategyConfigService = strategyConfigService;
        this.buyThreshold = buyThreshold;
        this.sellThreshold = sellThreshold;
    }

    /**
     * Evaluates all (or a filtered subset of) strategies against the provided data
     * and aggregates results with enhanced analysis.
     *
     * @param stockId             the stock identifier
     * @param indicators          latest technical indicators for the stock
     * @param prices              recent daily price records
     * @param activeStrategyNames optional set of strategy names to evaluate;
     *                            if null or empty all strategies are evaluated
     * @return enhanced aggregated signal result with full analysis
     */
    public AggregatedSignalResult aggregate(Long stockId,
                                            List<TechnicalIndicator> indicators,
                                            List<DailyPrice> prices,
                                            Set<String> activeStrategyNames) {
        List<TradingStrategy> strategiesToEvaluate = (activeStrategyNames == null || activeStrategyNames.isEmpty())
                ? this.strategies
                : this.strategies.stream()
                        .filter(s -> activeStrategyNames.contains(s.getName()))
                        .toList();

        List<StrategyResult> breakdown = new ArrayList<>();
        double score = 0.0;
        int totalPriority = 0;
        Map<String, Double> contributions = new HashMap<>();
        Map<String, Integer> categorySummary = new HashMap<>();
        List<String> supporting = new ArrayList<>();
        List<String> opposing = new ArrayList<>();
        int successfulStrategies = 0;

        for (TradingStrategy strategy : strategiesToEvaluate) {
            try {
                StrategyResult result = strategy.evaluate(stockId, indicators, prices);
                
                // Skip if result is null (insufficient data case)
                if (result == null) {
                    log.debug("Strategy {} returned null result for stock {}", strategy.getName(), stockId);
                    continue;
                }

                // Check if DB conditions exist and override result
                List<StrategyConditionGroup> conditions = strategyConditionService.getConditions(strategy.getName());
                if (conditions != null && !conditions.isEmpty()) {
                    for (StrategyConditionGroup condition : conditions) {
                        if (!condition.isEnabled()) continue;
                        boolean matches = strategyConditionService.evaluateConditionForStock(stockId, condition);
                        if (matches) {
                            double conf = switch (condition.getConfidence()) {
                                case "high" -> 0.85;
                                case "mid" -> 0.60;
                                default -> 0.40;
                            };
                            result = new StrategyResult(
                                StrategySignal.valueOf(condition.getSignalType()),
                                conf,
                                condition.getConditionId(),
                                strategy.getName(),
                                strategyConfigService.getPriority(strategy.getName()),
                                0.0
                            );
                            break;
                        }
                    }
                }

                int priority = strategyConfigService.getPriority(strategy.getName());
                double signalNumeric = switch (result.signal()) {
                    case BUY -> 1.0;
                    case SELL -> -1.0;
                    case HOLD -> 0.0;
                };
                double contribution = signalNumeric * priority * result.confidence();
                
                StrategyResult resultWithContribution = new StrategyResult(
                    result.signal(),
                    result.confidence(),
                    result.reason(),
                    result.strategyName(),
                    priority,
                    contribution
                );
                
                breakdown.add(resultWithContribution);
                totalPriority += priority;
                score += contribution;
                contributions.put(strategy.getName(), contribution);
                successfulStrategies++;

                // Track category summary
                String signalName = result.signal().name();
                categorySummary.put(signalName, categorySummary.getOrDefault(signalName, 0) + 1);

                // Categorize supporting/opposing
                if (result.signal() != StrategySignal.HOLD) {
                    String strategyName = strategy.getName();
                    if (result.signal() == StrategySignal.BUY) {
                        supporting.add(strategyName);
                    } else {
                        opposing.add(strategyName);
                    }
                }

            } catch (Exception e) {
                log.warn("Strategy {} failed for stock {}: {}", strategy.getName(), stockId, e.getMessage());
                // Continue with other strategies - fail-safe behavior
            }
        }

        StrategySignal finalSignal;
        if (score >= buyThreshold) {
            finalSignal = StrategySignal.BUY;
        } else if (score <= sellThreshold) {
            finalSignal = StrategySignal.SELL;
        } else {
            finalSignal = StrategySignal.HOLD;
        }

        // Calculate overall confidence based on:
        // 1. Agreement among strategies (higher agreement = higher confidence)
        // 2. Average confidence of individual strategies
        // 3. Strength of signal (distance from threshold)
        double confidence = calculateConfidence(
            breakdown, score, finalSignal, successfulStrategies
        );

        // Build supporting/opposing lists from breakdown
        supporting = breakdown.stream()
                .filter(r -> r.signal() == StrategySignal.BUY)
                .map(StrategyResult::strategyName)
                .collect(Collectors.toList());
        opposing = breakdown.stream()
                .filter(r -> r.signal() == StrategySignal.SELL)
                .map(StrategyResult::strategyName)
                .collect(Collectors.toList());

        log.debug("Aggregated signal for stock {}: {} (score={}, strategies={})",
                stockId, finalSignal, score, breakdown.size());

        return new AggregatedSignalResult(
            finalSignal,
            score,
            breakdown,
            totalPriority,
            confidence,
            supporting,
            opposing,
            categorySummary,
            contributions
        );
    }

    /**
     * Calculates overall confidence in the aggregated signal.
     * 
     * @param breakdown list of strategy results
     * @param score the aggregate score
     * @param finalSignal the final aggregated signal
     * @param successfulStrategies number of strategies that executed successfully
     * @return confidence score between 0.0 and 1.0
     */
    private double calculateConfidence(
            List<StrategyResult> breakdown,
            double score,
            StrategySignal finalSignal,
            int successfulStrategies) {
        
        if (successfulStrategies == 0) {
            return 0.0;
        }

        // Factor 1: Agreement (consistency of signals)
        long buyCount = breakdown.stream().filter(r -> r.signal() == StrategySignal.BUY).count();
        long sellCount = breakdown.stream().filter(r -> r.signal() == StrategySignal.SELL).count();
        long holdCount = breakdown.stream().filter(r -> r.signal() == StrategySignal.HOLD).count();
        
        double agreement = 0.0;
        if (finalSignal == StrategySignal.BUY && buyCount > 0) {
            agreement = (double) buyCount / successfulStrategies;
        } else if (finalSignal == StrategySignal.SELL && sellCount > 0) {
            agreement = (double) sellCount / successfulStrategies;
        } else if (finalSignal == StrategySignal.HOLD) {
            // High confidence if majority holds or signals cancel out
            double strongestSide = Math.max(buyCount, sellCount);
            agreement = holdCount > 0 ? (double) holdCount / successfulStrategies : 0.5;
            if (buyCount == sellCount && buyCount > 0) {
                agreement = 0.7; // Equal opposing forces = strong hold
            }
        }

        // Factor 2: Average confidence of contributing strategies
        double avgConfidence = breakdown.stream()
                .mapToDouble(StrategyResult::confidence)
                .average()
                .orElse(0.0);

        // Factor 3: Signal strength (distance from neutral zone)
        double signalStrength = 0.0;
        double neutralZone = Math.max(Math.abs(buyThreshold), Math.abs(sellThreshold));
        if (finalSignal == StrategySignal.BUY) {
            signalStrength = Math.min(score / buyThreshold, 2.0) / 2.0; // Normalize to 0-1
        } else if (finalSignal == StrategySignal.SELL) {
            signalStrength = Math.min(Math.abs(score) / Math.abs(sellThreshold), 2.0) / 2.0;
        } else {
            // In neutral zone - lower confidence unless strong agreement
            signalStrength = 0.3;
        }

        // Weighted combination (agreement weighted heaviest)
        double confidence = (agreement * 0.5) + (avgConfidence * 0.3) + (signalStrength * 0.2);

        return Math.round(confidence * 100.0) / 100.0; // Round to 2 decimal places
    }
}
