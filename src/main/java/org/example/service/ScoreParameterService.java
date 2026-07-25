package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.ScoreParameterConfig;
import org.example.repository.ScoreParameterConfigRepository;
import org.example.startup.StartupTask;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreParameterService implements StartupTask {

    private final ScoreParameterConfigRepository repository;

    private volatile Set<String> disabledLegacyFactorsCache = Collections.emptySet();

    // ponytail: auto-execute on startup to seed score parameters
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Score parameter service started - seeding default parameters");
        execute();
    }

    private static final List<ScoreParameterConfig> SEED_LIST = List.of(
            // TREND (8)
            ScoreParameterConfig.builder()
                    .paramKey("TREND_DIVERGENCE").category("TREND").displayName("Divergence")
                    .description("RSI bull/bear divergence").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("TREND_RSI_CONFLUENCE").category("TREND").displayName("RSI Confluence")
                    .description("Weekly/Monthly RSI confluence").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("TREND_MACD_CROSSOVER").category("TREND").displayName("MACD Crossover")
                    .description("MACD line crossover").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("TREND_PRICE_VS_SMA").category("TREND").displayName("Price vs SMA")
                    .description("Price vs SMA20/SMA50").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("TREND_TREND_FOLLOWING_BONUS").category("TREND").displayName("Trend-following Bonus")
                    .description("Trend alignment bonus").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("TREND_52W_PROXIMITY").category("TREND").displayName("52-week Proximity")
                    .description("Near 52-week low").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("TREND_TREND_DIRECTION").category("TREND").displayName("Trend Direction")
                    .description("20d/30d trend direction").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("TREND_ICHIMOKU_CLOUD").category("TREND").displayName("Ichimoku Cloud")
                    .description("Ichimoku cloud signals").enabled(true).scoreSystem("LEGACY").build(),
            // MOMENTUM (10)
            ScoreParameterConfig.builder()
                    .paramKey("MOM_RSI14").category("MOMENTUM").displayName("RSI(14)")
                    .description("Relative Strength Index").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_RSI_REVERSAL").category("MOMENTUM").displayName("RSI Reversal")
                    .description("RSI reversal bonus").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_BOLLINGER_BANDS").category("MOMENTUM").displayName("Bollinger Bands")
                    .description("Bollinger band position").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_STOCHASTIC").category("MOMENTUM").displayName("Stochastic")
                    .description("Stochastic %K/%D").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_STOCHRSI").category("MOMENTUM").displayName("StochRSI")
                    .description("Stochastic RSI").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_ULTIMATE_OSCILLATOR").category("MOMENTUM").displayName("Ultimate Oscillator")
                    .description("Ultimate Oscillator").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_ROC12").category("MOMENTUM").displayName("ROC(12)")
                    .description("Rate of Change 12").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_WILLIAMS_R").category("MOMENTUM").displayName("Williams %R")
                    .description("Williams %R").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_CCI").category("MOMENTUM").displayName("CCI")
                    .description("Commodity Channel Index").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOM_VWAP").category("MOMENTUM").displayName("VWAP")
                    .description("VWAP deviation").enabled(true).scoreSystem("LEGACY").build(),
            // STRUCTURE (4)
            ScoreParameterConfig.builder()
                    .paramKey("STRUCT_OBV").category("STRUCTURE").displayName("OBV")
                    .description("On-Balance Volume").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("STRUCT_SR_PROXIMITY").category("STRUCTURE").displayName("S/R Proximity")
                    .description("Support/Resistance proximity").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("STRUCT_BREAKOUT").category("STRUCTURE").displayName("Breakout")
                    .description("Breakout detection").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("STRUCT_VOLUME_CONFIRMATION").category("STRUCTURE").displayName("Volume Confirmation")
                    .description("Volume confirmation").enabled(true).scoreSystem("LEGACY").build(),
            // MODIFIERS (5)
            ScoreParameterConfig.builder()
                    .paramKey("MOD_ADX_MULTIPLIER").category("MODIFIERS").displayName("ADX Multiplier")
                    .description("ADX trend-strength multiplier").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOD_CANDLESTICK").category("MODIFIERS").displayName("Candlestick")
                    .description("Candlestick pattern score").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOD_STRATEGY_INFLUENCE").category("MODIFIERS").displayName("Strategy Influence")
                    .description("Strategy-influence multiplier").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOD_REVERSAL").category("MODIFIERS").displayName("Reversal Detection")
                    .description("Reversal detection score").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("MOD_OVERBOUGHT_DAMPENER").category("MODIFIERS").displayName("Overbought Dampener")
                    .description("Compound overbought dampener").enabled(true).scoreSystem("LEGACY").build(),
            // POST_SCORE (4)
            ScoreParameterConfig.builder()
                    .paramKey("POST_FII_DII").category("POST_SCORE").displayName("FII/DII Adjustment")
                    .description("FII/DII adjustment").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("POST_BEARISH_DISCOUNT").category("POST_SCORE").displayName("Bearish Discount")
                    .description("Bearish-trend discount").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("POST_ROLLING_ACCURACY").category("POST_SCORE").displayName("Rolling Accuracy")
                    .description("Rolling-accuracy penalty").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("POST_CHANNEL_OVERRIDE").category("POST_SCORE").displayName("Channel Override")
                    .description("52-week channel override").enabled(true).scoreSystem("LEGACY").build(),
            // LIQUIDITY (4)
            ScoreParameterConfig.builder()
                    .paramKey("LIQ_AMIHUD_ILLIQUIDITY").category("LIQUIDITY").displayName("Amihud Illiquidity")
                    .description("Amihud illiquidity ratio impact").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("LIQ_DOLLAR_VOLUME").category("LIQUIDITY").displayName("Dollar Volume")
                    .description("Dollar trading volume impact").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("LIQ_TURNOVER").category("LIQUIDITY").displayName("Turnover")
                    .description("Turnover ratio impact").enabled(true).scoreSystem("LEGACY").build(),
            ScoreParameterConfig.builder()
                    .paramKey("LIQ_VOLUME_STABILITY").category("LIQUIDITY").displayName("Volume Stability")
                    .description("Volume stability impact").enabled(true).scoreSystem("LEGACY").build()
    );

    public void seedIfEmpty() {
        // Key-by-key idempotent seed: backfills only rows missing from a partial seed.
        List<ScoreParameterConfig> missing = SEED_LIST.stream()
                .filter(row -> repository.findByParamKey(row.getParamKey()).isEmpty())
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            return;
        }
        try {
            repository.saveAll(missing);
            log.info("Seeded {} missing score parameter configs", missing.size());
        } catch (DataIntegrityViolationException e) {
            log.warn("Score parameter seed skipped (concurrent initialization): {}", e.getMessage());
        }
        refreshCache();
    }

    private void refreshCache() {
        this.disabledLegacyFactorsCache = Collections.unmodifiableSet(
                repository.findByEnabledFalse().stream()
                        .filter(c -> "LEGACY".equals(c.getScoreSystem()))
                        .map(ScoreParameterConfig::getParamKey)
                        .collect(Collectors.toSet()));
    }

    @Transactional
    @Override
    public void execute() {
        seedIfEmpty();
    }

    @Override
    public String getId() {
        return "score-params";
    }

    @Override
    public String getName() {
        return "Score Parameters";
    }

    @Override
    public String getDescription() {
        return "Initialize default score parameters for the scoring system";
    }

    @Override
    public String getCategory() {
        return "Scoring";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    public Set<String> getDisabledLegacyFactors() {
        return disabledLegacyFactorsCache;
    }

    public void setEnabled(String paramKey, boolean enabled) {
        ScoreParameterConfig config = repository.findByParamKey(paramKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown parameter: " + paramKey));
        config.setEnabled(enabled);
        repository.save(config);
        refreshCache();
    }

    public List<ScoreParameterConfig> getAll() {
        return repository.findAll();
    }

    public void resetToDefaults() {
        List<ScoreParameterConfig> all = repository.findAll();
        all.forEach(c -> c.setEnabled(true));
        repository.saveAll(all);
        refreshCache();
    }
}
