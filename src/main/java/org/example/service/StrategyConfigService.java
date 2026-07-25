package org.example.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.StrategyConfig;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.StrategyConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyConfigService {

    private final StrategyConfigRepository repository;

    /**
     * Monotonically increasing config version counter.
     * Incremented whenever strategy config changes (toggle, priority, conditions).
     * Used by StrategyDailyWeightService to detect stale pre-calculated weights.
     */
    private final AtomicLong configVersion = new AtomicLong(1);

    @Value("${strategy.rsi.priority:5}")
    private int rsiPriority;

    @Value("${strategy.macd.priority:5}")
    private int macdPriority;

    @Value("${strategy.ma-crossover.priority:6}")
    private int maCrossoverPriority;

    @Value("${strategy.bollinger.priority:5}")
    private int bollingerPriority;

    @Value("${strategy.volume.priority:4}")
    private int volumePriority;

    @Value("${strategy.candlestick.at-support.priority:4}")
    private int atSupportPriority;

    @Value("${strategy.candlestick.at-resistance.priority:4}")
    private int atResistancePriority;

    @Value("${strategy.breakout.priority:5}")
    private int breakoutPriority;

    @Value("${strategy.candlestick.pattern.priority:4}")
    private int candlestickPatternPriority;

    @Value("${strategy.liquidity.priority:4}")
    private int liquidityPriority;

    @Value("${strategy.volume.spike-factor:1.5}")
    private double volumeSpikeFactor;

    @PostConstruct
    public void init() {
        seedIfEmpty();
        ensureCandlestickStrategiesExist();
    }

    private void seedIfEmpty() {
        if (repository.count() > 0) {
            return;
        }
        List<StrategyConfig> seeds = List.of(
                new StrategyConfig("RSI", true, "RSI Strategy", rsiPriority),
                new StrategyConfig("MACD", true, "MACD Strategy", macdPriority),
                new StrategyConfig("MA_CROSSOVER", true, "MA Crossover", maCrossoverPriority),
                new StrategyConfig("BOLLINGER", true, "Bollinger Band", bollingerPriority),
                new StrategyConfig("VOLUME", true, "Volume Strategy", volumePriority),
                new StrategyConfig("CANDLESTICK_AT_SUPPORT", true, "Candlestick at Support", atSupportPriority),
                new StrategyConfig("CANDLESTICK_AT_RESISTANCE", true, "Candlestick at Resistance", atResistancePriority),
                new StrategyConfig("BREAKOUT", true, "Breakout Strategy", breakoutPriority),
                new StrategyConfig("CANDLESTICK_PATTERN", true, "Candlestick Patterns", candlestickPatternPriority),
                new StrategyConfig("LIQUIDITY", true, "Liquidity Assessment", liquidityPriority)
        );
        try {
            repository.saveAll(seeds);
            log.info("Seeded {} strategy configs", seeds.size());
        } catch (DataIntegrityViolationException e) {
            log.warn("Strategy config seed skipped (concurrent initialization): {}", e.getMessage());
        }
    }

    public Set<String> getActiveStrategyNames() {
        List<StrategyConfig> configs = repository.findAll();
        if (configs.isEmpty()) {
            seedIfEmpty();
            configs = repository.findAll();
        }
        return configs.stream()
                .filter(StrategyConfig::isActive)
                .map(StrategyConfig::getStrategyName)
                .collect(Collectors.toSet());
    }

    @CacheEvict(value = "signals", allEntries = true)
    public StrategyConfig toggleStrategy(String strategyName, boolean active) {
        StrategyConfig config = repository.findByStrategyName(strategyName)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + strategyName));
        config.setActive(active);
        bumpConfigVersion();
        return repository.save(config);
    }

    public List<StrategyConfig> getAllConfigs() {
        return repository.findAllByOrderByStrategyNameAsc();
    }

    public int getPriority(String strategyName) {
        return repository.findByStrategyName(strategyName)
                .map(StrategyConfig::getPriority)
                .orElse(5);
    }

    /**
     * Returns the current config version. Incremented on every toggle/priority/condition change.
     */
    public long getConfigVersion() {
        return configVersion.get();
    }

    /**
     * Bumps the config version counter. Called internally when config changes.
     */
    public long bumpConfigVersion() {
        long newVersion = configVersion.incrementAndGet();
        log.info("Strategy config version bumped to {}", newVersion);
        return newVersion;
    }

    @Transactional
    private void ensureCandlestickStrategiesExist() {
        repository.findByStrategyName("CANDLESTICK").ifPresent(config -> {
            log.info("Removing legacy CANDLESTICK strategy config");
            repository.delete(config);
        });
        if (repository.findByStrategyName("CANDLESTICK_AT_SUPPORT").isEmpty()) {
            log.info("Seeding CANDLESTICK_AT_SUPPORT strategy config");
            repository.save(new StrategyConfig("CANDLESTICK_AT_SUPPORT", true, "Candlestick at Support", atSupportPriority));
        }
        if (repository.findByStrategyName("CANDLESTICK_AT_RESISTANCE").isEmpty()) {
            log.info("Seeding CANDLESTICK_AT_RESISTANCE strategy config");
            repository.save(new StrategyConfig("CANDLESTICK_AT_RESISTANCE", true, "Candlestick at Resistance", atResistancePriority));
        }
    }

    @CacheEvict(value = "signals", allEntries = true)
    public StrategyConfig updatePriority(String strategyName, int priority) {
        if (priority < 1 || priority > 10) {
            throw new IllegalArgumentException("Priority must be between 1 and 10");
        }
        StrategyConfig config = repository.findByStrategyName(strategyName)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + strategyName));
        config.setPriority(priority);
        bumpConfigVersion();
        return repository.save(config);
    }

    public double getVolumeSpikeFactor() {
        return volumeSpikeFactor;
    }

    public void updateVolumeSpikeFactor(double factor) {
        this.volumeSpikeFactor = factor;
        bumpConfigVersion();
        log.info("Volume spike factor updated to {}", factor);
    }
}
