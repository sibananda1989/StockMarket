package org.example.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ConditionStatDTO;
import org.example.dto.StrategyConditionGroupDTO;
import org.example.dto.StrategyConditionFullDTO;
import org.example.dto.StrategyFullDTO;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.Stock;
import org.example.entity.StrategyConditionGroup;
import org.example.entity.StrategyConditionStatsCache;
import org.example.entity.TechnicalIndicator;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StrategyConditionGroupRepository;
import org.example.repository.StrategyConditionStatsCacheRepository;
import org.example.repository.StockRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.calculator.CandlestickPatternCalculator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyConditionService {

    private final StrategyConditionGroupRepository conditionRepository;
    private final StrategyConditionStatsCacheRepository statsCacheRepository;
    private final TechnicalIndicatorRepository indicatorRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final StockRepository stockRepository;
    private final StrategyConfigService strategyConfigService;

    private static final Set<String> VALID_SIGNALS = Set.of("BUY", "SELL", "HOLD");
    private static final Set<String> VALID_OPERATORS = Set.of(
            "<", ">", "<=", ">=", "==", "between", "within",
            "crossover_above", "crossover_below", "both_above", "both_below",
            "alignment_bullish", "alignment_bearish", "golden_cross", "death_cross",
            "mixed", "obv_up", "obv_down",
            "hammer", "bullish_engulfing", "morning_star", "shooting_star", 
            "bearish_engulfing", "evening_star", "no_pattern"
    );
    private static final Set<String> VALID_CONFIDENCE = Set.of("high", "mid", "low");

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        seedDefaultConditions();
        ensureBreakoutConditionsExist();
    }

    /**
     * Ensures the breakout strategy conditions exist for existing databases where
     * seedDefaultConditions() was skipped because the table already had data.
     */
    private void ensureBreakoutConditionsExist() {
        boolean breakoutConditionsExist = conditionRepository.findAll().stream()
                .anyMatch(c -> "BREAKOUT".equals(c.getStrategyName()));
        if (!breakoutConditionsExist) {
            List<StrategyConditionGroup> seeds = buildBreakoutConditions();
            try {
                conditionRepository.saveAll(seeds);
                log.info("Seeded {} BREAKOUT strategy conditions", seeds.size());
            } catch (DataIntegrityViolationException e) {
                log.warn("BREAKOUT condition seed skipped (concurrent): {}", e.getMessage());
            }
        }
    }

    /**
     * Seeds default conditions for all 5 strategies if the table is empty.
     * Each strategy gets 5 conditions with varying signals and confidence levels.
     */
    public void seedDefaultConditions() {
        if (conditionRepository.count() > 0) {
            return;
        }
        List<StrategyConditionGroup> seeds = new ArrayList<>();
        seeds.addAll(buildRsiConditions());
        seeds.addAll(buildMacdConditions());
        seeds.addAll(buildMaCrossoverConditions());
        seeds.addAll(buildBollingerConditions());
        seeds.addAll(buildVolumeConditions());
        seeds.addAll(buildCandlestickConditions());
        seeds.addAll(buildBreakoutConditions());
        try {
            conditionRepository.saveAll(seeds);
            log.info("Seeded {} strategy conditions", seeds.size());
        } catch (DataIntegrityViolationException e) {
            log.warn("Strategy condition seed skipped (concurrent): {}", e.getMessage());
        }
    }

    private List<StrategyConditionGroup> buildRsiConditions() {
        return List.of(
                cond("RSI", "rsi_strong_buy", "BUY", "RSI", "<", val(30), null, "high", 0),
                cond("RSI", "rsi_buy", "BUY", "RSI", "between", val(30), val(40), "mid", 1),
                cond("RSI", "rsi_neutral", "HOLD", "RSI", "between", val(40), val(60), "mid", 2),
                cond("RSI", "rsi_sell", "SELL", "RSI", "between", val(60), val(70), "mid", 3),
                cond("RSI", "rsi_strong_sell", "SELL", "RSI", ">", val(70), null, "high", 4)
        );
    }

    private List<StrategyConditionGroup> buildMacdConditions() {
        return List.of(
                cond("MACD", "macd_cross_above", "BUY", "MACD vs Signal", "crossover_above", null, null, "high", 0),
                cond("MACD", "macd_both_pos", "BUY", "MACD & Signal", "both_above", null, null, "mid", 1),
                cond("MACD", "macd_cross_below", "SELL", "MACD vs Signal", "crossover_below", null, null, "high", 2),
                cond("MACD", "macd_both_neg", "SELL", "MACD & Signal", "both_below", null, null, "mid", 3),
                cond("MACD", "macd_neutral", "HOLD", "MACD gap", "within", val("0.1"), null, "low", 4)
        );
    }

    private List<StrategyConditionGroup> buildMaCrossoverConditions() {
        return List.of(
                cond("MA_CROSSOVER", "ma_bull_align", "BUY", "Price>SMA20>SMA50", "alignment_bullish", null, null, "high", 0),
                cond("MA_CROSSOVER", "ma_golden_cross", "BUY", "SMA20 vs SMA50", "golden_cross", null, null, "high", 1),
                cond("MA_CROSSOVER", "ma_bear_align", "SELL", "Price<SMA20<SMA50", "alignment_bearish", null, null, "high", 2),
                cond("MA_CROSSOVER", "ma_death_cross", "SELL", "SMA20 vs SMA50", "death_cross", null, null, "high", 3),
                cond("MA_CROSSOVER", "ma_mixed", "HOLD", "Price vs SMAs", "mixed", null, null, "low", 4)
        );
    }

    private List<StrategyConditionGroup> buildBollingerConditions() {
        return List.of(
                cond("BOLLINGER", "bb_strong_buy", "BUY", "Band position %", "<", val(5), null, "high", 0),
                cond("BOLLINGER", "bb_buy", "BUY", "Band position %", "<", val(20), null, "mid", 1),
                cond("BOLLINGER", "bb_strong_sell", "SELL", "Band position %", ">", val(95), null, "high", 2),
                cond("BOLLINGER", "bb_sell", "SELL", "Band position %", ">", val(80), null, "mid", 3),
                cond("BOLLINGER", "bb_neutral", "HOLD", "Band position %", "between", val(20), val(80), "low", 4)
        );
    }

    private List<StrategyConditionGroup> buildVolumeConditions() {
        return List.of(
                cond("VOLUME", "vol_buy_spike", "BUY", "Volume vs avg", ">=", val("1.5"), null, "high", 0),
                cond("VOLUME", "vol_buy_obv", "BUY", "Volume vs avg", "obv_up", null, null, "mid", 1),
                cond("VOLUME", "vol_sell_spike", "SELL", "Volume vs avg", ">=", val("1.5"), null, "high", 2),
                cond("VOLUME", "vol_sell_obv", "SELL", "Volume vs avg", "obv_down", null, null, "mid", 3),
                cond("VOLUME", "vol_neutral", "HOLD", "Volume vs avg", "<", val("1.2"), null, "low", 4)
        );
    }

    private List<StrategyConditionGroup> buildCandlestickConditions() {
        return List.of(
                cond("CANDLESTICK", "candle_buy_hammer", "BUY", "Hammer on support", "Hammer", null, null, "high", 0),
                cond("CANDLESTICK", "candle_buy_bullish_engulfing", "BUY", "Bullish Engulfing on support", "Bullish Engulfing", null, null, "high", 1),
                cond("CANDLESTICK", "candle_buy_morning_star", "BUY", "Morning Star on support", "Morning Star", null, null, "high", 2),
                cond("CANDLESTICK", "candle_sell_shooting_star", "SELL", "Shooting Star on resistance", "Shooting Star", null, null, "high", 3),
                cond("CANDLESTICK", "candle_sell_bearish_engulfing", "SELL", "Bearish Engulfing on resistance", "Bearish Engulfing", null, null, "high", 4),
                cond("CANDLESTICK", "candle_sell_evening_star", "SELL", "Evening Star on resistance", "Evening Star", null, null, "high", 5),
                cond("CANDLESTICK", "candle_hold_no_pattern", "HOLD", "No candlestick pattern", "NONE", null, null, "low", 6)
        );
    }

    private List<StrategyConditionGroup> buildBreakoutConditions() {
        return List.of(
                cond("BREAKOUT", "bo_buy_volume", "BUY", "Volume breakout above resistance", "Volume Breakout", null, null, "high", 0),
                cond("BREAKOUT", "bo_buy_gap_up", "BUY", "Gap up (low > prev high)", "Gap Up", null, null, "mid", 1),
                cond("BREAKOUT", "bo_sell_gap_down", "SELL", "Gap down (high < prev low)", "Gap Down", null, null, "mid", 2),
                cond("BREAKOUT", "bo_buy_range", "BUY", "Range breakout from consolidation", "Range Breakout Up", null, null, "mid", 3),
                cond("BREAKOUT", "bo_sell_range", "SELL", "Range breakdown from consolidation", "Range Breakout Down", null, null, "mid", 4),
                cond("BREAKOUT", "bo_hold_none", "HOLD", "No significant breakout detected", "NONE", null, null, "low", 5)
        );
    }

    private static StrategyConditionGroup cond(String strategyName, String conditionId, String signal,
                                                String fieldLabel, String operator,
                                                BigDecimal thresholdValue, BigDecimal thresholdValue2,
                                                String confidence, int displayOrder) {
        return StrategyConditionGroup.builder()
                .strategyName(strategyName)
                .conditionId(conditionId)
                .signalType(signal)
                .fieldLabel(fieldLabel)
                .operator(operator)
                .thresholdValue(thresholdValue)
                .thresholdValue2(thresholdValue2)
                .confidence(confidence)
                .displayOrder(displayOrder)
                .enabled(true)
                .build();
    }

    private static BigDecimal val(double v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal val(String v) {
        return new BigDecimal(v);
    }

    /**
     * Returns all conditions for a given strategy, ordered by display_order.
     */
    public List<StrategyConditionGroup> getConditions(String strategyName) {
        return conditionRepository.findByStrategyNameOrderByDisplayOrder(strategyName);
    }

    /**
     * Returns all conditions grouped by strategy name.
     */
    public Map<String, List<StrategyConditionGroup>> getAllConditions() {
        List<StrategyConditionGroup> all = conditionRepository.findAll();
        return all.stream().collect(Collectors.groupingBy(StrategyConditionGroup::getStrategyName));
    }

    /**
     * Validates and saves a new condition list for a strategy.
     * Deletes old conditions and inserts new ones.
     * Evicts stats cache entries for this strategy after save.
     */
    @Transactional
    public void saveConditions(String strategyName, List<StrategyConditionGroupDTO> conditions) {
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("Strategy name is required");
        }
        if (conditions == null) {
            throw new IllegalArgumentException("Conditions list is required");
        }
        for (StrategyConditionGroupDTO dto : conditions) {
            if (dto.getConditionId() == null || dto.getConditionId().isBlank()) {
                throw new IllegalArgumentException("conditionId is required for each condition");
            }
            if (dto.getSignal() == null || !VALID_SIGNALS.contains(dto.getSignal())) {
                throw new IllegalArgumentException("Invalid signal: " + dto.getSignal()
                        + ". Allowed: BUY, SELL, HOLD");
            }
            if (dto.getOperator() == null || !VALID_OPERATORS.contains(dto.getOperator())) {
                throw new IllegalArgumentException("Invalid operator: " + dto.getOperator());
            }
            if (dto.getConfidence() == null || !VALID_CONFIDENCE.contains(dto.getConfidence())) {
                throw new IllegalArgumentException("Invalid confidence: " + dto.getConfidence()
                        + ". Allowed: high, mid, low");
            }
            if (dto.getFieldLabel() == null || dto.getFieldLabel().isBlank()) {
                throw new IllegalArgumentException("fieldLabel is required for each condition");
            }
        }

        conditionRepository.deleteByStrategyName(strategyName);

        List<StrategyConditionGroup> entities = new ArrayList<>();
        for (int i = 0; i < conditions.size(); i++) {
            StrategyConditionGroupDTO dto = conditions.get(i);
            entities.add(StrategyConditionGroup.builder()
                    .strategyName(strategyName)
                    .conditionId(dto.getConditionId())
                    .signalType(dto.getSignal())
                    .fieldLabel(dto.getFieldLabel())
                    .operator(dto.getOperator())
                    .thresholdValue(dto.getThresholdValue())
                    .thresholdValue2(dto.getThresholdValue2())
                    .confidence(dto.getConfidence())
                    .displayOrder(i)
                    .enabled(dto.isEnabled())
                    .build());
        }
        conditionRepository.saveAll(entities);

        statsCacheRepository.deleteByStrategyName(strategyName);
    }

    /**
     * Asynchronously computes stock counts for every condition across all strategies.
     * Fetches latest indicators and daily prices for each stock, evaluates each condition,
     * and stores results in the stats cache table.
     */
    @Async("syncExecutor")
    public CompletableFuture<Void> computeAndCacheStats() {
        log.info("Starting strategy stats computation...");
        List<Long> stockIds = stockRepository.findAll().stream()
                .map(Stock::getId)
                .collect(Collectors.toList());
        if (stockIds.isEmpty()) {
            log.warn("No stocks found, skipping stats computation");
            return CompletableFuture.completedFuture(null);
        }

        Map<String, List<StrategyConditionGroup>> conditionsByStrategy = getAllConditions();
        Map<String, Map<String, Integer>> results = new HashMap<>();

        for (Map.Entry<String, List<StrategyConditionGroup>> strategyEntry : conditionsByStrategy.entrySet()) {
            String strategyName = strategyEntry.getKey();
            Map<String, Integer> conditionCounts = new HashMap<>();
            for (StrategyConditionGroup condition : strategyEntry.getValue()) {
                if (!condition.isEnabled()) continue;
                int count = 0;
                for (Long stockId : stockIds) {
                    if (evaluateConditionForStock(stockId, condition)) {
                        count++;
                    }
                }
                conditionCounts.put(condition.getConditionId(), count);
            }
            results.put(strategyName, conditionCounts);
        }

        List<StrategyConditionStatsCache> cacheEntries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Map<String, Integer>> strategyEntry : results.entrySet()) {
            for (Map.Entry<String, Integer> conditionEntry : strategyEntry.getValue().entrySet()) {
                cacheEntries.add(StrategyConditionStatsCache.builder()
                        .strategyName(strategyEntry.getKey())
                        .conditionId(conditionEntry.getKey())
                        .stockCount(conditionEntry.getValue())
                        .computedAt(now)
                        .build());
            }
        }
        statsCacheRepository.deleteAll();
        statsCacheRepository.saveAll(cacheEntries);

        log.info("Strategy stats computed for {} strategies, {} conditions",
                results.size(), cacheEntries.size());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Evaluates whether a single stock matches a given condition.
     * Returns false if any required indicator data is missing.
     */
    public boolean evaluateConditionForStock(Long stockId, StrategyConditionGroup condition) {
        List<TechnicalIndicator> latestIndicators = indicatorRepository.findLatestForStock(stockId);
        if (latestIndicators == null || latestIndicators.isEmpty()) {
            return false;
        }
        Map<IndicatorType, BigDecimal> indicatorMap = new HashMap<>();
        for (TechnicalIndicator ti : latestIndicators) {
            indicatorMap.put(ti.getIndicatorType(), ti.getValue());
        }

        var latestPriceOpt = dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(stockId);
        if (latestPriceOpt.isEmpty()) {
            return false;
        }
        BigDecimal closingPrice = latestPriceOpt.get().getClosingPrice();
        LocalDate latestDate = latestPriceOpt.get().getPriceDate();

        String operator = condition.getOperator();
        return evaluateOperator(operator, condition, indicatorMap, closingPrice, stockId, latestDate);
    }

    private boolean evaluateOperator(String operator, StrategyConditionGroup condition,
                                      Map<IndicatorType, BigDecimal> indicatorMap,
                                      BigDecimal closingPrice, Long stockId, LocalDate latestDate) {
        BigDecimal threshold = condition.getThresholdValue();
        BigDecimal threshold2 = condition.getThresholdValue2();

        return switch (operator) {
            case "<" -> {
                BigDecimal val = getNumericValue(condition, indicatorMap, closingPrice, stockId);
                yield val != null && threshold != null && val.compareTo(threshold) < 0;
            }
            case ">" -> {
                BigDecimal val = getNumericValue(condition, indicatorMap, closingPrice, stockId);
                yield val != null && threshold != null && val.compareTo(threshold) > 0;
            }
            case "<=" -> {
                BigDecimal val = getNumericValue(condition, indicatorMap, closingPrice, stockId);
                yield val != null && threshold != null && val.compareTo(threshold) <= 0;
            }
            case ">=" -> {
                BigDecimal val = getNumericValue(condition, indicatorMap, closingPrice, stockId);
                yield val != null && threshold != null && val.compareTo(threshold) >= 0;
            }
            case "==" -> {
                BigDecimal val = getNumericValue(condition, indicatorMap, closingPrice, stockId);
                yield val != null && threshold != null && val.compareTo(threshold) == 0;
            }
            case "between" -> {
                BigDecimal val = getNumericValue(condition, indicatorMap, closingPrice, stockId);
                yield val != null && threshold != null && threshold2 != null
                        && val.compareTo(threshold) >= 0 && val.compareTo(threshold2) <= 0;
            }
            case "within" -> evaluateWithin(indicatorMap, threshold);
            case "crossover_above" -> hasCrossover(stockId, latestDate, IndicatorType.MACD_LINE, IndicatorType.MACD_SIGNAL, true);
            case "crossover_below" -> hasCrossover(stockId, latestDate, IndicatorType.MACD_LINE, IndicatorType.MACD_SIGNAL, false);
            case "both_above" -> {
                BigDecimal macd = indicatorMap.get(IndicatorType.MACD_LINE);
                BigDecimal sig = indicatorMap.get(IndicatorType.MACD_SIGNAL);
                yield macd != null && sig != null
                        && macd.compareTo(BigDecimal.ZERO) > 0
                        && sig.compareTo(BigDecimal.ZERO) > 0;
            }
            case "both_below" -> {
                BigDecimal macd = indicatorMap.get(IndicatorType.MACD_LINE);
                BigDecimal sig = indicatorMap.get(IndicatorType.MACD_SIGNAL);
                yield macd != null && sig != null
                        && macd.compareTo(BigDecimal.ZERO) < 0
                        && sig.compareTo(BigDecimal.ZERO) < 0;
            }
            case "alignment_bullish" -> evaluateBullishAlignment(closingPrice, indicatorMap);
            case "alignment_bearish" -> evaluateBearishAlignment(closingPrice, indicatorMap);
            case "golden_cross" -> hasCrossed(stockId, latestDate, true);
            case "death_cross" -> hasCrossed(stockId, latestDate, false);
            case "mixed" -> evaluateMixedAlignment(closingPrice, indicatorMap);
            case "obv_up" -> evaluateObvTrend(stockId, true);
            case "obv_down" -> evaluateObvTrend(stockId, false);
            case "hammer", "bullish_engulfing", "morning_star", "shooting_star", 
                 "bearish_engulfing", "evening_star", "no_pattern" -> 
                evaluateCandlestickPattern(stockId, latestDate, operator);
            default -> false;
        };
    }

    private BigDecimal getNumericValue(StrategyConditionGroup condition,
                                        Map<IndicatorType, BigDecimal> indicatorMap,
                                        BigDecimal closingPrice, Long stockId) {
        return switch (condition.getFieldLabel()) {
            case "RSI" -> indicatorMap.get(IndicatorType.RSI);
            case "Band position %" -> calculateBandPosition(indicatorMap, closingPrice);
            case "Volume vs avg" -> calculateVolumeRatio(stockId);
            default -> null;
        };
    }

    private BigDecimal calculateBandPosition(Map<IndicatorType, BigDecimal> indicatorMap, BigDecimal closingPrice) {
        BigDecimal upper = indicatorMap.get(IndicatorType.BOLLINGER_UPPER);
        BigDecimal lower = indicatorMap.get(IndicatorType.BOLLINGER_LOWER);
        if (upper == null || lower == null || closingPrice == null
                || upper.compareTo(lower) == 0) {
            return null;
        }
        BigDecimal range = upper.subtract(lower);
        return closingPrice.subtract(lower)
                .divide(range, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateVolumeRatio(Long stockId) {
        var latestPrice = dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(stockId);
        if (latestPrice.isEmpty() || latestPrice.get().getVolume() == null) {
            return null;
        }
        long latestVolume = latestPrice.get().getVolume();
        var last20 = dailyPriceRepository.findLastNDays(stockId, 20);
        if (last20.size() < 2) {
            return null;
        }
        long sum = 0;
        int count = 0;
        for (var dp : last20) {
            if (dp.getVolume() != null) {
                sum += dp.getVolume();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        double avg = (double) sum / count;
        if (avg == 0) {
            return null;
        }
        return BigDecimal.valueOf(latestVolume / avg).setScale(4, RoundingMode.HALF_UP);
    }

    private boolean evaluateWithin(Map<IndicatorType, BigDecimal> indicatorMap, BigDecimal threshold) {
        BigDecimal macd = indicatorMap.get(IndicatorType.MACD_LINE);
        BigDecimal signal = indicatorMap.get(IndicatorType.MACD_SIGNAL);
        if (macd == null || signal == null || threshold == null) {
            return false;
        }
        BigDecimal gap = macd.subtract(signal).abs();
        return gap.compareTo(threshold) <= 0;
    }

    private boolean hasCrossover(Long stockId, LocalDate latestDate,
                                  IndicatorType type1, IndicatorType type2, boolean above) {
        var today1 = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, type1, latestDate);
        var today2 = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, type2, latestDate);
        if (today1.isEmpty() || today2.isEmpty()) {
            return false;
        }
        LocalDate prevDate = latestDate.minusDays(1);
        var prev1 = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, type1, prevDate);
        var prev2 = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, type2, prevDate);
        if (prev1.isEmpty() || prev2.isEmpty()) {
            return false;
        }
        BigDecimal t1 = today1.get().getValue();
        BigDecimal t2 = today2.get().getValue();
        BigDecimal p1 = prev1.get().getValue();
        BigDecimal p2 = prev2.get().getValue();
        if (above) {
            return t1.compareTo(t2) > 0 && p1.compareTo(p2) <= 0;
        } else {
            return t1.compareTo(t2) < 0 && p1.compareTo(p2) >= 0;
        }
    }

    private boolean evaluateBullishAlignment(BigDecimal closingPrice, Map<IndicatorType, BigDecimal> indicatorMap) {
        BigDecimal sma20 = indicatorMap.get(IndicatorType.SMA_20);
        BigDecimal sma50 = indicatorMap.get(IndicatorType.SMA_50);
        return closingPrice != null && sma20 != null && sma50 != null
                && closingPrice.compareTo(sma20) > 0 && sma20.compareTo(sma50) > 0;
    }

    private boolean evaluateBearishAlignment(BigDecimal closingPrice, Map<IndicatorType, BigDecimal> indicatorMap) {
        BigDecimal sma20 = indicatorMap.get(IndicatorType.SMA_20);
        BigDecimal sma50 = indicatorMap.get(IndicatorType.SMA_50);
        return closingPrice != null && sma20 != null && sma50 != null
                && closingPrice.compareTo(sma20) < 0 && sma20.compareTo(sma50) < 0;
    }

    private boolean evaluateMixedAlignment(BigDecimal closingPrice, Map<IndicatorType, BigDecimal> indicatorMap) {
        BigDecimal sma20 = indicatorMap.get(IndicatorType.SMA_20);
        BigDecimal sma50 = indicatorMap.get(IndicatorType.SMA_50);
        if (closingPrice == null || sma20 == null || sma50 == null) {
            return false;
        }
        BigDecimal min = sma20.min(sma50);
        BigDecimal max = sma20.max(sma50);
        return closingPrice.compareTo(min) >= 0 && closingPrice.compareTo(max) <= 0;
    }

    private boolean hasCrossed(Long stockId, LocalDate latestDate, boolean golden) {
        var todayFast = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, IndicatorType.SMA_20, latestDate);
        var todaySlow = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, IndicatorType.SMA_50, latestDate);
        if (todayFast.isEmpty() || todaySlow.isEmpty()) {
            return false;
        }
        LocalDate prevDate = latestDate.minusDays(1);
        var prevFast = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, IndicatorType.SMA_20, prevDate);
        var prevSlow = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, IndicatorType.SMA_50, prevDate);
        if (prevFast.isEmpty() || prevSlow.isEmpty()) {
            return false;
        }
        BigDecimal tf = todayFast.get().getValue();
        BigDecimal ts = todaySlow.get().getValue();
        BigDecimal pf = prevFast.get().getValue();
        BigDecimal ps = prevSlow.get().getValue();
        if (golden) {
            return tf.compareTo(ts) > 0 && pf.compareTo(ps) <= 0;
        } else {
            return tf.compareTo(ts) < 0 && pf.compareTo(ps) >= 0;
        }
    }

    private boolean evaluateObvTrend(Long stockId, boolean up) {
        var todayOpt = indicatorRepository.findFirstByStockIdAndIndicatorTypeOrderByCalculationDateDesc(stockId, IndicatorType.OBV);
        if (todayOpt.isEmpty()) {
            return false;
        }
        LocalDate todayDate = todayOpt.get().getCalculationDate();
        LocalDate fiveDaysAgo = todayDate.minusDays(5);
        var pastOpt = indicatorRepository.findByStockIdAndIndicatorTypeAndCalculationDate(stockId, IndicatorType.OBV, fiveDaysAgo);
        if (pastOpt.isEmpty()) {
            return false;
        }
        int cmp = todayOpt.get().getValue().compareTo(pastOpt.get().getValue());
        return up ? cmp > 0 : cmp < 0;
    }

    private boolean evaluateCandlestickPattern(Long stockId, LocalDate latestDate, String patternType) {
        List<DailyPrice> prices = dailyPriceRepository.findByStockIdOrderByPriceDateDesc(stockId);
        if (prices == null || prices.size() < 3) {
            return false;
        }

        CandlestickPatternCalculator calculator = new CandlestickPatternCalculator();
        CandlestickPatternCalculator.Result result = calculator.detect(prices);

        String patternName = patternType.toUpperCase().replace('_', ' ');
        
        if ("no_pattern".equals(patternType)) {
            return result.pattern() == CandlestickPatternCalculator.Pattern.NONE;
        }

        return result.pattern().name().replace('_', ' ').equals(patternName);
    }

    /**
     * Returns all cached condition stock counts, grouped by strategy name.
     */
    public Map<String, List<ConditionStatDTO>> getCachedStats() {
        List<StrategyConditionStatsCache> all = statsCacheRepository.findAll();
        Map<String, List<ConditionStatDTO>> result = new HashMap<>();
        for (StrategyConditionStatsCache entry : all) {
            result.computeIfAbsent(entry.getStrategyName(), k -> new ArrayList<>())
                    .add(ConditionStatDTO.builder()
                            .conditionId(entry.getConditionId())
                            .stockCount(entry.getStockCount())
                            .computedAt(entry.getComputedAt())
                            .build());
        }
        return result;
    }

    /**
     * Builds the full strategy DTO list including conditions and cached stock counts.
     */
    public List<StrategyFullDTO> getFullStrategyConfig() {
        var configs = strategyConfigService.getAllConfigs();
        Map<String, List<StrategyConditionGroup>> conditionsByStrategy = getAllConditions();
        Map<String, Map<String, Integer>> statsMap = buildStatsMap();

        List<StrategyFullDTO> result = new ArrayList<>();
        for (var config : configs) {
            List<StrategyConditionGroup> conds = conditionsByStrategy
                    .getOrDefault(config.getStrategyName(), Collections.emptyList());
            Map<String, Integer> conditionStats = statsMap
                    .getOrDefault(config.getStrategyName(), Collections.emptyMap());

            Set<String> tags = conds.stream()
                    .map(StrategyConditionGroup::getFieldLabel)
                    .collect(Collectors.toCollection(HashSet::new));

            int totalStocks = conditionStats.values().stream()
                    .mapToInt(Integer::intValue).sum();

            List<StrategyConditionFullDTO> condDtos = new ArrayList<>();
            for (StrategyConditionGroup c : conds) {
                condDtos.add(StrategyConditionFullDTO.builder()
                        .conditionId(c.getConditionId())
                        .signal(c.getSignalType())
                        .fieldLabel(c.getFieldLabel())
                        .operator(c.getOperator())
                        .thresholdValue(c.getThresholdValue())
                        .thresholdValue2(c.getThresholdValue2())
                        .confidence(c.getConfidence())
                        .displayOrder(c.getDisplayOrder())
                        .stockCount(conditionStats.getOrDefault(c.getConditionId(), 0))
                        .enabled(c.isEnabled())
                        .build());
            }

            result.add(StrategyFullDTO.builder()
                    .strategyName(config.getStrategyName())
                    .displayName(config.getDisplayName())
                    .priority(config.getPriority())
                    .active(config.isActive())
                    .tags(new ArrayList<>(tags))
                    .totalStocksMatched(totalStocks)
                    .conditions(condDtos)
                    .build());
        }
        return result;
    }

    private Map<String, Map<String, Integer>> buildStatsMap() {
        List<StrategyConditionStatsCache> all = statsCacheRepository.findAll();
        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (StrategyConditionStatsCache entry : all) {
            result.computeIfAbsent(entry.getStrategyName(), k -> new HashMap<>())
                    .put(entry.getConditionId(), entry.getStockCount());
        }
        return result;
    }

    /**
     * Toggles a single condition's enabled state.
     */
    @Transactional
    public StrategyConditionGroup toggleCondition(String strategyName, String conditionId, boolean enabled) {
        List<StrategyConditionGroup> conditions = getConditions(strategyName);
        for (StrategyConditionGroup condition : conditions) {
            if (condition.getConditionId().equals(conditionId)) {
                condition.setEnabled(enabled);
                return conditionRepository.save(condition);
            }
        }
        throw new IllegalArgumentException("Condition not found: " + conditionId);
    }

    /**
     * Deletes all conditions and re-seeds defaults.
     */
    @Transactional
    public void resetToDefaults() {
        conditionRepository.deleteAll();
        statsCacheRepository.deleteAll();
        seedDefaultConditions();
    }

    /**
     * Sets a strategy's priority. Validates range 1-10.
     */
    public void updatePriority(String strategyName, int priority) {
        if (priority < 1 || priority > 10) {
            throw new IllegalArgumentException("Priority must be between 1 and 10");
        }
        strategyConfigService.updatePriority(strategyName, priority);
    }
}
