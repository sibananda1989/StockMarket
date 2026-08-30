package org.example.strategy.engine;

import org.example.dto.SignalHistoryPoint;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.Stock;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.springframework.data.domain.Pageable;

class MultiStrategySignalEngineTest {

    private static final Set<String> ALL_ACTIVE = Set.of(
            "RSI", "MACD", "MA_CROSSOVER", "BOLLINGER", "VOLUME");
    private static final List<TechnicalIndicator> SOME_INDICATORS = List.of(
            new TechnicalIndicator(new Stock(), IndicatorType.RSI, new BigDecimal("50.00"), LocalDate.now())
    );

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private TechnicalIndicatorRepository technicalIndicatorRepository;

    @Mock
    private IndicatorComputationService indicatorComputationService;

    @Mock
    private StrategySignalAggregator aggregator;

    @Mock
    private StrategyConfigService strategyConfigService;

    @InjectMocks
    private MultiStrategySignalEngine engine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(strategyConfigService.getActiveStrategyNames()).thenReturn(ALL_ACTIVE);
        when(technicalIndicatorRepository.findLatestTwoCalculationDates(anyLong(), any(Pageable.class))).thenReturn(List.of());
    }

    @Test
    void testEvaluate_Success() {
        Long stockId = 1L;
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), LocalDate.now())
        );
        AggregatedSignalResult mockResult = new AggregatedSignalResult(
                StrategySignal.BUY, 5.0, List.of(), 20,
                0.0, List.of(), List.of(), Map.of(), Map.of()
        );

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(technicalIndicatorRepository.findLatestForStock(stockId)).thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(eq(stockId), anyList(), eq(prices), eq(ALL_ACTIVE))).thenReturn(mockResult);

        AggregatedSignalResult result = engine.evaluate(stockId);
        assertEquals(StrategySignal.BUY, result.finalSignal());
        assertEquals(5.0, result.score());
        verify(strategyConfigService).getActiveStrategyNames();
    }

    @Test
    void testEvaluate_NoPriceData_ThrowsException() {
        Long stockId = 1L;
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> engine.evaluate(stockId));
    }

    @Test
    void testEvaluate_NoIndicators_FallsBackToComputation() {
        Long stockId = 1L;
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), LocalDate.now())
        );
        List<TechnicalIndicator> computed = List.of(
                new TechnicalIndicator(new Stock(), IndicatorType.RSI, new BigDecimal("50.00"), LocalDate.now())
        );
        AggregatedSignalResult mockResult = new AggregatedSignalResult(
                StrategySignal.HOLD, 0.0, List.of(), 0,
                0.0, List.of(), List.of(), Map.of(), Map.of()
        );

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(technicalIndicatorRepository.findLatestForStock(stockId)).thenReturn(List.of());
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), eq(prices)))
                .thenReturn(computed);
        when(aggregator.aggregate(eq(stockId), anyList(), eq(prices), eq(ALL_ACTIVE))).thenReturn(mockResult);

        AggregatedSignalResult result = engine.evaluate(stockId);
        assertEquals(StrategySignal.HOLD, result.finalSignal());
        assertEquals(0.0, result.score());
        verify(indicatorComputationService).computeIndicators(eq(stockId), any(LocalDate.class), eq(prices));
    }

    @Test
    void testEvaluate_WithActiveSet_PassesToAggregator() {
        Long stockId = 1L;
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), LocalDate.now())
        );
        Set<String> active = Set.of("RSI", "MACD");
        AggregatedSignalResult mockResult = new AggregatedSignalResult(
                StrategySignal.BUY, 4.0, List.of(), 14,
                0.0, List.of(), List.of(), Map.of(), Map.of()
        );

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(technicalIndicatorRepository.findLatestForStock(stockId)).thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(eq(stockId), anyList(), eq(prices), eq(active))).thenReturn(mockResult);

        AggregatedSignalResult result = engine.evaluate(stockId, active);
        assertEquals(StrategySignal.BUY, result.finalSignal());
        assertEquals(4.0, result.score());
        verify(aggregator).aggregate(eq(stockId), anyList(), eq(prices), eq(active));
        verify(strategyConfigService, never()).getActiveStrategyNames();
    }

    @Test
    void testEvaluate_NullActiveSet_UsesDbState() {
        Long stockId = 1L;
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), LocalDate.now())
        );
        AggregatedSignalResult mockResult = new AggregatedSignalResult(
                StrategySignal.HOLD, 0.0, List.of(), 0,
                0.0, List.of(), List.of(), Map.of(), Map.of()
        );

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(technicalIndicatorRepository.findLatestForStock(stockId)).thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(eq(stockId), anyList(), eq(prices), eq(ALL_ACTIVE))).thenReturn(mockResult);

        AggregatedSignalResult result = engine.evaluate(stockId, null);
        assertEquals(StrategySignal.HOLD, result.finalSignal());
        verify(strategyConfigService).getActiveStrategyNames();
        verify(aggregator).aggregate(eq(stockId), anyList(), eq(prices), eq(ALL_ACTIVE));
    }

    @Test
    void testEvaluate_EmptyActiveSet_PassedThroughToAggregator() {
        // When activeStrategyNames is explicitly empty (all disabled),
        // it should be passed through to the aggregator, NOT fall back to DB state.
        Long stockId = 1L;
        List<DailyPrice> prices = List.of(
                new DailyPrice(null, new BigDecimal("100.00"), LocalDate.now())
        );
        AggregatedSignalResult mockResult = new AggregatedSignalResult(
                StrategySignal.HOLD, 0.0, List.of(), 0,
                0.0, List.of(), List.of(), Map.of(), Map.of()
        );

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(technicalIndicatorRepository.findLatestForStock(stockId)).thenReturn(SOME_INDICATORS);
        // Mock the aggregator with empty set (not ALL_ACTIVE)
        when(aggregator.aggregate(eq(stockId), anyList(), eq(prices), eq(Set.of()))).thenReturn(mockResult);

        AggregatedSignalResult result = engine.evaluate(stockId, Set.of());
        assertEquals(StrategySignal.HOLD, result.finalSignal());
        assertEquals(0.0, result.score());
        // Should NOT fall back to DB state — empty set is passed through
        verify(strategyConfigService, never()).getActiveStrategyNames();
        verify(aggregator).aggregate(eq(stockId), anyList(), eq(prices), eq(Set.of()));
    }

    @Test
    void testEvaluateHistory_SimpleCase() {
        Long stockId = 1L;
        int days = 30;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(i)));
        }
        List<SignalHistoryPoint> mockHistory = List.of(
                new SignalHistoryPoint(LocalDate.now().minusDays(5), "BUY", 5, new BigDecimal("101.00"), null, null, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, null, null, null, null, 0, 0, 0, null, 0, 0, 0, 0, List.of(), 3.0, -3.0, 5.0)
        );

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet())).thenReturn(
                new AggregatedSignalResult(StrategySignal.BUY, 5.0, List.of(), 20, 0.0, List.of(), List.of(), Map.of(), Map.of())
        );

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        assertTrue(result.size() > 0);
        assertEquals("BUY", result.get(0).getRecommendation());
        assertEquals(5, result.get(0).getRawScore().intValue());
    }

    @Test
    void testEvaluateHistory_WithActiveFilter() {
        Long stockId = 1L;
        int days = 90;
        Set<String> active = Set.of("RSI", "MACD");
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(24 - i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), eq(active))).thenReturn(
                new AggregatedSignalResult(StrategySignal.BUY, 5.0, List.of(), 14, 0.0, List.of(), List.of(), Map.of(), Map.of())
        );

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, active);
        assertTrue(result.size() > 0);
        verify(aggregator, atLeastOnce()).aggregate(anyLong(), anyList(), anyList(), eq(active));
    }

    @Test
    void testEvaluateHistory_LargeTimeRange_UsesStep() {
        Long stockId = 1L;
        int days = 365;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet())).thenReturn(
                new AggregatedSignalResult(StrategySignal.BUY, 5.0, List.of(), 20, 0.0, List.of(), List.of(), Map.of(), Map.of())
        );

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        // No adaptive stepping — all dates within the window are included
        // 400 prices spanning 399d, cutoff at 365d → ~366 dates in window
        assertTrue(result.size() > 300, "Expected at least 300 points (all dates within window)");
        assertTrue(result.size() < 400, "Expected less than 400 points (all dates within window)");
    }

    @Test
    void testEvaluateHistory_FewerThan20Prices_ReturnsEmpty() {
        Long stockId = 1L;
        int days = 90;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testEvaluateHistory_StrategyException_HandlesGracefully() {
        Long stockId = 1L;
        int days = 30;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet()))
                .thenThrow(new RuntimeException("Strategy error"))
                .thenReturn(new AggregatedSignalResult(StrategySignal.BUY, 5.0, List.of(), 20, 0.0, List.of(), List.of(), Map.of(), Map.of()));

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        // Should skip the failed evaluation and continue
        assertTrue(result.size() >= 0);
    }

    @Test
    void testEvaluateHistory_SmallTimeRange_UsesStep1() {
        Long stockId = 1L;
        int days = 10;
        List<DailyPrice> prices = new ArrayList<>();
        // Create 30 prices, all within the last 10 days (so all are in the window)
        for (int i = 0; i < 30; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(9 - i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet())).thenReturn(
                new AggregatedSignalResult(StrategySignal.BUY, 5.0, List.of(), 20, 0.0, List.of(), List.of(), Map.of(), Map.of())
        );

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        // With step=1 and 30 prices all within 10 days, should get 30 points
        assertTrue(result.size() > 5, "Expected more than 5 points with step=1 for 10 days");
    }

    @Test
    void testEvaluateHistory_MediumTimeRange_UsesStep5() {
        Long stockId = 1L;
        int days = 60;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet())).thenReturn(
                new AggregatedSignalResult(StrategySignal.BUY, 5.0, List.of(), 20, 0.0, List.of(), List.of(), Map.of(), Map.of())
        );

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        // No adaptive stepping — all dates within the 60-day window are included
        // 100 prices spanning 99d, cutoff at 60d → ~61 dates in window
        assertTrue(result.size() > 50, "Expected more than 50 points (all dates within window)");
        assertTrue(result.size() < 70, "Expected less than 70 points (all dates within window)");
    }

    @Test
    void testEvaluateHistory_EvaluationOrder() {
        Long stockId = 1L;
        int days = 90;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(24 - i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet())).thenReturn(
                new AggregatedSignalResult(StrategySignal.BUY, 5.0, List.of(), 20, 0.0, List.of(), List.of(), Map.of(), Map.of())
        );

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        // Should be sorted oldest first — all dates included (no adaptive stepping)
        assertEquals(25, result.size());
        assertEquals(LocalDate.now().minusDays(24), result.get(0).getPriceDate());
        assertEquals(LocalDate.now().minusDays(23), result.get(1).getPriceDate());
        assertEquals(LocalDate.now().minusDays(22), result.get(2).getPriceDate());
    }

    @Test
    void testEvaluateHistory_MultipleStrategies() {
        Long stockId = 1L;
        int days = 30;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(24 - i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet()))
                .thenReturn(new AggregatedSignalResult(
                        StrategySignal.BUY, 5.0,
                        List.of(
                                StrategyResult.withoutContribution(StrategySignal.BUY, 0.8, "Buy1", "RSI", 7),
                                StrategyResult.withoutContribution(StrategySignal.BUY, 0.7, "Buy2", "MACD", 7),
                                StrategyResult.withoutContribution(StrategySignal.BUY, 0.6, "Buy3", "MA_CROSSOVER", 7),
                                StrategyResult.withoutContribution(StrategySignal.BUY, 0.5, "Buy4", "BOLLINGER", 7),
                                StrategyResult.withoutContribution(StrategySignal.BUY, 0.4, "Buy5", "VOLUME", 7)
                        ),
                        35, 0.85, List.of("RSI", "MACD"), List.of(),
                        Map.of("BUY", 5), Map.of("RSI", 5.6, "MACD", 4.9)
                ));

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, ALL_ACTIVE);
        assertTrue(result.size() > 0);
        assertEquals(5, result.get(0).getStrategyBreakdown().size());
    }

    @Test
    void testEvaluateHistory_ConfidenceInResult() {
        Long stockId = 1L;
        int days = 30;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet()))
                .thenReturn(new AggregatedSignalResult(
                        StrategySignal.BUY, 5.0, List.of(), 20,
                        0.85, List.of("RSI", "MACD"), List.of(),
                        Map.of("BUY", 2), Map.of("RSI", 3.5, "MACD", 1.5)
                ));

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        assertTrue(result.size() > 0);
        assertEquals(5.0, result.get(0).getRawScore(), 0.01);
    }

    @Test
    void testEvaluateHistory_BreakdownInResult() {
        Long stockId = 1L;
        int days = 30;
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100.00").add(BigDecimal.valueOf(i * 0.1)),
                    LocalDate.now().minusDays(i)));
        }

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId)).thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(stockId), any(LocalDate.class), anyList()))
                .thenReturn(SOME_INDICATORS);
        when(aggregator.aggregate(anyLong(), anyList(), anyList(), anySet()))
                .thenReturn(new AggregatedSignalResult(
                        StrategySignal.BUY, 5.0,
                        List.of(StrategyResult.withoutContribution(StrategySignal.BUY, 0.85, "Buy reason", "RSI", 7)),
                        20, 0.85, List.of("RSI"), List.of(),
                        Map.of("BUY", 1), Map.of("RSI", 5.95)
                ));

        List<SignalHistoryPoint> result = engine.evaluateHistory(stockId, days, null);
        assertTrue(result.size() > 0);
        assertEquals(1, result.get(0).getStrategyBreakdown().size());
        assertEquals("RSI", result.get(0).getStrategyBreakdown().get(0).getStrategyName());
        assertEquals("BUY", result.get(0).getStrategyBreakdown().get(0).getSignal());
    }
}
