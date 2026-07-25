package org.example.strategy.aggregator;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
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
import java.util.Optional;
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
     *                            if null all strategies are evaluated;
     *                            if empty no strategies are evaluated (returns HOLD)
     * @return enhanced aggregated signal result with full analysis
     */
    public AggregatedSignalResult aggregate(Long stockId,
                                            List<TechnicalIndicator> indicators,
                                            List<DailyPrice> prices,
                                            Set<String> activeStrategyNames) {
        // When activeStrategyNames is explicitly empty (e.g. all strategies disabled),
        // return HOLD with no breakdown — don't fall back to evaluating all strategies.
        if (activeStrategyNames != null && activeStrategyNames.isEmpty()) {
            log.debug("No active strategies for stock {}, returning HOLD", stockId);
            return new AggregatedSignalResult(
                StrategySignal.HOLD, 0.0, List.of(), 0, 0.0,
                List.of(), List.of(), Map.of(), Map.of()
            );
        }

        List<TradingStrategy> strategiesToEvaluate = (activeStrategyNames == null)
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

        // ── Market Regime Filter: resolve ADX once for all strategies ──
        MarketRegime regime = MarketRegime.UNKNOWN;
        Optional<Double> adxOpt = resolveAdxFromIndicators(indicators);
        if (adxOpt.isPresent()) {
            double adxValue = adxOpt.get();
            if (adxValue > 25.0) {
                regime = MarketRegime.TRENDING;
                log.debug("Market regime for stock {}: TRENDING (ADX={})", stockId, adxValue);
            } else if (adxValue < 20.0) {
                regime = MarketRegime.RANGING;
                log.debug("Market regime for stock {}: RANGING (ADX={})", stockId, adxValue);
            } else {
                regime = MarketRegime.TRANSITIONAL;
                log.debug("Market regime for stock {}: TRANSITIONAL (ADX={})", stockId, adxValue);
            }
        }

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
                                0.0,
                                result.latestVolume(),
                                result.avgVolume(),
                                result.spikeThreshold()
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
                
                        // ── Apply ADX Market Regime confidence adjustment ──
                StrategyResult adjustedResult = applyAdxRegimeFilter(result, regime, strategy.getName());

                // Recalculate contribution with adjusted confidence
                double adjustedSignalNumeric = switch (adjustedResult.signal()) {
                    case BUY -> 1.0;
                    case SELL -> -1.0;
                    case HOLD -> 0.0;
                };
                double adjustedContribution = adjustedSignalNumeric * priority * adjustedResult.confidence();

                StrategyResult resultWithContribution = new StrategyResult(
                    adjustedResult.signal(),
                    adjustedResult.confidence(),
                    adjustedResult.reason(),
                    adjustedResult.strategyName(),
                    priority,
                    adjustedContribution,
                    adjustedResult.latestVolume(),
                    adjustedResult.avgVolume(),
                    adjustedResult.spikeThreshold()
                );
                
                breakdown.add(resultWithContribution);
                totalPriority += priority;
                score += adjustedContribution;
                contributions.put(strategy.getName(), adjustedContribution);
                successfulStrategies++;

                // Track category summary
                String signalName = result.signal().name();
                categorySummary.put(signalName, categorySummary.getOrDefault(signalName, 0) + 1);

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
     * Resolves ADX value from the indicators list.
     */
    private Optional<Double> resolveAdxFromIndicators(List<TechnicalIndicator> indicators) {
        if (indicators == null || indicators.isEmpty()) {
            return Optional.empty();
        }
        return indicators.stream()
                .filter(ti -> ti.getIndicatorType() == IndicatorType.ADX)
                .findFirst()
                .map(ti -> ti.getValue().doubleValue());
    }

    /**
     * Applies ADX-based market regime confidence adjustments.
     * This is the key insight from world championship winners:
     * - In TRENDING markets (ADX > 25): boost trend-following, suppress mean-reversion
     * - In RANGING markets (ADX < 20): boost mean-reversion, suppress trend-following
     * - In TRANSITIONAL markets (ADX 20-25): mixed, slight adjustment
     */
    private StrategyResult applyAdxRegimeFilter(StrategyResult result, MarketRegime regime, String strategyName) {
        if (regime == MarketRegime.UNKNOWN) {
            return result; // No ADX data, no adjustment
        }

        double confidence = result.confidence();
        String reason = result.reason();

        switch (regime) {
            case TRENDING:
                if ("MA_CROSSOVER".equals(strategyName) || "BOLLINGER".equals(strategyName)) {
                    // Trending: MA crossover & Bollinger continuation signals get boost
                    confidence = Math.min(1.0, confidence * REGIME_TREND_FOLLOWING_BOOST);
                    reason = reason + " | ADX trending: trend-following boosted";
                } else if ("RSI".equals(strategyName)) {
                    // Trending: mean-reversion is unreliable, reduce confidence
                    confidence = confidence * REGIME_MEAN_REVERSION_REDUCE;
                    reason = reason + " | ADX trending: mean-reversion reduced";
                } else if ("VOLUME".equals(strategyName)) {
                    // Trending: volume confirms the trend → boost confidence
                    confidence = Math.min(1.0, confidence * REGIME_VOLUME_TRENDING_BOOST);
                    reason = reason + " | ADX trending: volume confirmation boosted";
                }
                break;

            case RANGING:
                if ("RSI".equals(strategyName) || "BOLLINGER".equals(strategyName)) {
                    // Ranging: mean-reversion works well, boost confidence
                    confidence = Math.min(1.0, confidence * REGIME_MEAN_REVERSION_BOOST);
                    reason = reason + " | ADX ranging: mean-reversion boosted";
                } else if ("MA_CROSSOVER".equals(strategyName)) {
                    // Ranging: trend-following generates false signals, reduce
                    confidence = confidence * REGIME_TREND_FOLLOWING_REDUCE;
                    reason = reason + " | ADX ranging: trend-following reduced";
                } else if ("VOLUME".equals(strategyName)) {
                    // Ranging: volume spikes are often noise → reduce confidence
                    confidence = confidence * REGIME_VOLUME_RANGING_REDUCE;
                    reason = reason + " | ADX ranging: volume noise reduced";
                }
                break;

            case TRANSITIONAL:
                // Moderate adjustment: slight tilt based on ADX direction
                if ("MA_CROSSOVER".equals(strategyName)) {
                    confidence = Math.min(1.0, confidence * 1.1);
                    reason = reason + " | ADX transitional: slight trend boost";
                } else if ("RSI".equals(strategyName) || "BOLLINGER".equals(strategyName)) {
                    confidence = Math.min(1.0, confidence * 1.05);
                    reason = reason + " | ADX transitional: slight mean-reversion boost";
                } else if ("VOLUME".equals(strategyName)) {
                    // Transitional: benefit of the doubt for volume signals
                    confidence = Math.min(1.0, confidence * REGIME_VOLUME_TRANSITIONAL_BOOST);
                    reason = reason + " | ADX transitional: volume slight boost";
                }
                break;
        }

        return new StrategyResult(result.signal(), confidence, reason, result.strategyName(),
                result.priority(), 0.0, result.latestVolume(), result.avgVolume(), result.spikeThreshold());
    }

    // ── Regime filter constants ──
    private static final double REGIME_TREND_FOLLOWING_BOOST = 1.25;
    private static final double REGIME_MEAN_REVERSION_REDUCE = 0.60;
    private static final double REGIME_MEAN_REVERSION_BOOST = 1.20;
    private static final double REGIME_TREND_FOLLOWING_REDUCE = 0.65;
    private static final double REGIME_VOLUME_TRENDING_BOOST = 1.20;
    private static final double REGIME_VOLUME_RANGING_REDUCE = 0.85;
    private static final double REGIME_VOLUME_TRANSITIONAL_BOOST = 1.05;

    /**
     * Market regime enum for ADX-based classification.
     */
    private enum MarketRegime {
        TRENDING,     // ADX > 25 — strong trend, favor trend-following
        RANGING,      // ADX < 20 — no trend, favor mean-reversion
        TRANSITIONAL, // ADX 20-25 — mixed, slight tilt
        UNKNOWN       // No ADX data available
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
