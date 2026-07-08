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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyConfigService {

    private final StrategyConfigRepository repository;

    @Value("${strategy.rsi.priority:7}")
    private int rsiPriority;

    @Value("${strategy.macd.priority:7}")
    private int macdPriority;

    @Value("${strategy.ma-crossover.priority:8}")
    private int maCrossoverPriority;

    @Value("${strategy.bollinger.priority:6}")
    private int bollingerPriority;

    @Value("${strategy.volume.priority:5}")
    private int volumePriority;

    @Value("${strategy.candlestick.priority:4}")
    private int candlestickPriority;

    @Value("${strategy.breakout.priority:7}")
    private int breakoutPriority;

    @PostConstruct
    public void init() {
        seedIfEmpty();
        ensureBreakoutStrategyExists();
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
                new StrategyConfig("CANDLESTICK", true, "Candlestick Pattern", candlestickPriority)
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

    private void ensureBreakoutStrategyExists() {
        if (repository.findByStrategyName("BREAKOUT").isEmpty()) {
            log.info("Seeding BREAKOUT strategy config");
            repository.save(new StrategyConfig("BREAKOUT", true, "Breakout Strategy", breakoutPriority));
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
        return repository.save(config);
    }
}
