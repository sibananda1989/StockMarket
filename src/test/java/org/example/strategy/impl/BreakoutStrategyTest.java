package org.example.strategy.impl;

import org.example.entity.DailyPrice;
import org.example.entity.TechnicalIndicator;
import org.example.service.BreakoutDetector;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BreakoutStrategyTest {

    @Mock
    private BreakoutDetector breakoutDetector;

    private BreakoutStrategy breakoutStrategy;
    private final int priority = 7;

    @BeforeEach
    void setUp() {
        breakoutStrategy = new BreakoutStrategy(priority, breakoutDetector);
    }

    @Test
    void testEvaluate_VolumeBreakout_ReturnsBuy() {
        Long stockId = 1L;
        List<DailyPrice> prices = createDummyPrices(25);
        List<TechnicalIndicator> indicators = new ArrayList<>();

        BreakoutDetector.BreakoutResult result = new BreakoutDetector.BreakoutResult();
        result.volumeBreakout = true;
        result.score = 2;
        result.description = "Volume spike above resistance";

        when(breakoutDetector.detect(prices, stockId)).thenReturn(result);

        StrategyResult strategyResult = breakoutStrategy.evaluate(stockId, indicators, prices);

        assertEquals(StrategySignal.BUY, strategyResult.signal());
        assertEquals(0.70, strategyResult.confidence());
        assertTrue(strategyResult.reason().contains("Volume breakout"));
    }

    @Test
    void testEvaluate_GapUp_ReturnsBuy() {
        Long stockId = 1L;
        List<DailyPrice> prices = createDummyPrices(25);
        List<TechnicalIndicator> indicators = new ArrayList<>();

        BreakoutDetector.BreakoutResult result = new BreakoutDetector.BreakoutResult();
        result.gapUp = true;
        result.score = 2;
        result.description = "Significant gap up";

        when(breakoutDetector.detect(prices, stockId)).thenReturn(result);

        StrategyResult strategyResult = breakoutStrategy.evaluate(stockId, indicators, prices);

        assertEquals(StrategySignal.BUY, strategyResult.signal());
        assertEquals(0.60, strategyResult.confidence());
        assertTrue(strategyResult.reason().contains("Gap up"));
    }

    @Test
    void testEvaluate_GapDown_ReturnsSell() {
        Long stockId = 1L;
        List<DailyPrice> prices = createDummyPrices(25);
        List<TechnicalIndicator> indicators = new ArrayList<>();

        BreakoutDetector.BreakoutResult result = new BreakoutDetector.BreakoutResult();
        result.gapDown = true;
        result.score = -2;
        result.description = "Significant gap down";

        when(breakoutDetector.detect(prices, stockId)).thenReturn(result);

        StrategyResult strategyResult = breakoutStrategy.evaluate(stockId, indicators, prices);

        assertEquals(StrategySignal.SELL, strategyResult.signal());
        assertEquals(0.60, strategyResult.confidence());
        assertTrue(strategyResult.reason().contains("Gap down"));
    }

    @Test
    void testEvaluate_RangeBreakoutUp_ReturnsBuy() {
        Long stockId = 1L;
        List<DailyPrice> prices = createDummyPrices(25);
        List<TechnicalIndicator> indicators = new ArrayList<>();

        BreakoutDetector.BreakoutResult result = new BreakoutDetector.BreakoutResult();
        result.rangeBreakout = true;
        result.score = 1;
        result.description = "Broke out of consolidation zone";

        when(breakoutDetector.detect(prices, stockId)).thenReturn(result);

        StrategyResult strategyResult = breakoutStrategy.evaluate(stockId, indicators, prices);

        assertEquals(StrategySignal.BUY, strategyResult.signal());
        assertEquals(0.55, strategyResult.confidence());
        assertTrue(strategyResult.reason().contains("Range breakout"));
    }

    @Test
    void testEvaluate_RangeBreakoutDown_ReturnsSell() {
        Long stockId = 1L;
        List<DailyPrice> prices = createDummyPrices(25);
        List<TechnicalIndicator> indicators = new ArrayList<>();

        BreakoutDetector.BreakoutResult result = new BreakoutDetector.BreakoutResult();
        result.rangeBreakout = true;
        result.score = -1;
        result.description = "Broke down from consolidation zone";

        when(breakoutDetector.detect(prices, stockId)).thenReturn(result);

        StrategyResult strategyResult = breakoutStrategy.evaluate(stockId, indicators, prices);

        assertEquals(StrategySignal.SELL, strategyResult.signal());
        assertEquals(0.55, strategyResult.confidence());
        assertTrue(strategyResult.reason().contains("Range breakout"));
    }

    @Test
    void testEvaluate_NoBreakout_ReturnsHold() {
        Long stockId = 1L;
        List<DailyPrice> prices = createDummyPrices(25);
        List<TechnicalIndicator> indicators = new ArrayList<>();

        BreakoutDetector.BreakoutResult result = new BreakoutDetector.BreakoutResult();
        result.score = 0;
        result.description = "No breakout detected";

        when(breakoutDetector.detect(prices, stockId)).thenReturn(result);

        StrategyResult strategyResult = breakoutStrategy.evaluate(stockId, indicators, prices);

        assertEquals(StrategySignal.HOLD, strategyResult.signal());
        assertEquals(0.10, strategyResult.confidence());
    }

    @Test
    void testEvaluate_InsufficientData_ReturnsHold() {
        Long stockId = 1L;
        List<DailyPrice> prices = createDummyPrices(10); // Less than 20
        List<TechnicalIndicator> indicators = new ArrayList<>();

        StrategyResult strategyResult = breakoutStrategy.evaluate(stockId, indicators, prices);

        assertEquals(StrategySignal.HOLD, strategyResult.signal());
        assertEquals(0.0, strategyResult.confidence());
        assertEquals("Insufficient data", strategyResult.reason());
    }

    private List<DailyPrice> createDummyPrices(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DailyPrice dp = new DailyPrice();
            dp.setClosingPrice(BigDecimal.valueOf(100 + i));
            dp.setHighPrice(BigDecimal.valueOf(105 + i));
            dp.setLowPrice(BigDecimal.valueOf(95 + i));
            dp.setVolume(1000L);
            prices.add(dp);
        }
        return prices;
    }
}
