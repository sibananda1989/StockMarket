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
import org.example.startup.StartupTask;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
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
public class StrategyConditionService implements StartupTask {

    private final StrategyConditionGroupRepository conditionRepository;
    private final StrategyConditionStatsCacheRepository statsCacheRepository;
    private final TechnicalIndicatorRepository indicatorRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final StockRepository stockRepository;
    private final StrategyConfigService strategyConfigService;

    @Value("${strategy.volume.spike-factor:1.5}")
    private double volumeSpikeFactor;

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

    // ponytail: auto-execute on startup to seed default conditions
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("Strategy condition service started - running startup tasks");
        execute();
    }

    @Override
    public String getId() {
        return "strategy-conditions";
    }

    @Override
    public String getName() {
        return "Strategy Conditions";
    }

    @Override
    public String getDescription() {
        return "Initialize default strategy conditions for all trading strategies";
    }

    @Override
    public String getCategory() {
        return "Signals";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Transactional
    @Override
    public void execute() {
        log.info("Executing StrategyConditionStartupTask");
        validateSpikeFactor();
        log.info("Volume spike factor: {}", volumeSpikeFactor);
        seedDefaultConditions();
        ensureCandlestickConditionsRemoved();
    }

    /**
     * Validates that the configured volume spike factor is positive. A non-positive
     * factor would make every volume ratio trigger a spike and break the confidence curve.
     */
    private void validateSpikeFactor() {
        if (volumeSpikeFactor <= 0) {
            throw new IllegalArgumentException(
                    "strategy.volume.spike-factor must be > 0, but was: " + volumeSpikeFactor);
        }
        if (volumeSpikeFactor > 10) {
            log.warn("Volume spike factor is unusually high: {}", volumeSpikeFactor);
        }
    }

    /**
     * Removes legacy CANDLESTICK strategy conditions from existing databases where
     * seedDefaultConditions() was skipped because the table already had data.
     */
    @Transactional
    private void ensureCandlestickConditionsRemoved() {
        conditionRepository.deleteByStrategyName("CANDLESTICK");
        log.info("Ensured legacy CANDLESTICK strategy conditions are removed");
    }

    /**
     * Seeds default conditions if the table is empty. Currently no default conditions.
     */
    public void seedDefaultConditions() {
        if (conditionRepository.count() > 0) {
            return;
        }
        List<StrategyConditionGroup> seeds = new ArrayList<>();
        // no more default strategy conditions to seed
        try {
            conditionRepository.saveAll(seeds);
            log.info("Seeded {} strategy conditions", seeds.size());
        } catch (DataIntegrityViolationException e) {
            log.warn("Strategy condition seed skipped (concurrent): {}", e.getMessage());
        }
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
            case "Amihud Score" -> calculateAmihudScore(indicatorMap);
            default -> null;
        };
    }

    private BigDecimal calculateAmihudScore(Map<IndicatorType, BigDecimal> indicatorMap) {
        BigDecimal amihud = indicatorMap.get(IndicatorType.AMIHUD_ILLIQUIDITY);
        if (amihud == null) return null;
        // Invert Amihud: lower raw = more liquid. Compute score 0-100.
        // Typical Amihud values range from 0.000001 (ultra-liquid) to ~0.001 (illiquid).
        double raw = amihud.doubleValue();
        double score = Math.max(0, Math.min(100, 100 - (raw * 100000)));
        return BigDecimal.valueOf(Math.round(score));
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
        // Exclude the latest day (last element) from the average, matching VolumeRatioCalculator
        for (int i = 0; i < last20.size() - 1; i++) {
            var dp = last20.get(i);
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
