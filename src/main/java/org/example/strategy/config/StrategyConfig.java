package org.example.strategy.config;

import org.example.strategy.base.TradingStrategy;
import org.example.strategy.impl.BollingerBandStrategy;
import org.example.strategy.impl.BreakoutStrategy;
import org.example.strategy.impl.CandlestickContextStrategy;
import org.example.strategy.impl.CandlestickPatternStrategy;
import org.example.strategy.impl.LiquidityStrategy;
import org.example.strategy.impl.MacdStrategy;
import org.example.strategy.impl.MovingAverageCrossoverStrategy;
import org.example.strategy.impl.RsiStrategy;
import org.example.strategy.impl.VolumeStrategy;
import org.example.repository.FundamentalDataRepository;
import org.example.service.BreakoutDetector;
import org.example.service.SupportResistanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Registers all active trading strategies as Spring beans.
 * Each strategy's priority is configurable via application.properties.
 */
@Configuration
public class StrategyConfig {

    @Value("${strategy.rsi.priority:5}")
    private int rsiPriority;

    @Value("${strategy.macd.priority:5}")
    private int macdPriority;

    @Value("${strategy.ma-crossover.priority:6}")
    private int maCrossoverPriority;

    @Value("${strategy.bollinger.priority:5}")
    private int bollingerPriority;

    @Value("${strategy.volume.spike-factor:1.5}")
    private double volumeSpikeFactor;

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

    /**
     * Creates the list of active strategies with configurable priorities.
     * Spring will auto-collect beans of type {@link TradingStrategy}
     * for injection into the aggregator.
     *
     * @return immutable list of all active strategies
     */
    @Bean
    public List<TradingStrategy> activeStrategies(SupportResistanceService supportResistanceService,
                                                   BreakoutDetector breakoutDetector,
                                                   FundamentalDataRepository fundamentalDataRepository) {
        return List.of(
                new RsiStrategy(rsiPriority),
                new MacdStrategy(macdPriority),
                new MovingAverageCrossoverStrategy(maCrossoverPriority),
                new BollingerBandStrategy(bollingerPriority),
                new VolumeStrategy(volumePriority, volumeSpikeFactor),
                new LiquidityStrategy(liquidityPriority, fundamentalDataRepository),
                new CandlestickPatternStrategy(candlestickPatternPriority),
                new CandlestickContextStrategy(atSupportPriority, supportResistanceService, true),
                new CandlestickContextStrategy(atResistancePriority, supportResistanceService, false),
                new BreakoutStrategy(breakoutPriority, breakoutDetector)
        );
    }
}
