package org.example.service;

import org.example.dto.SignalHistoryPoint;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.entity.StrategyDailyWeight;
import org.example.entity.TechnicalIndicator;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StockRepository;
import org.example.repository.StrategyDailyWeightRepository;
import org.example.service.calculator.IndicatorComputationService;
import org.example.strategy.aggregator.StrategySignalAggregator;
import org.example.strategy.model.AggregatedSignalResult;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyDailyWeightServiceTest {

    @Mock
    private StrategyDailyWeightRepository weightRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private IndicatorComputationService indicatorComputationService;
    @Mock
    private StrategySignalAggregator aggregator;
    @Mock
    private StrategyConfigService strategyConfigService;
    @Mock
    private Executor backfillExecutor;

    @InjectMocks
    private StrategyDailyWeightService service;

    private Stock stock;
    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        stock = new Stock("RELIANCE", "Reliance Industries", "Energy");
        stock.setId(1L);

        prices = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 100; i++) {
            DailyPrice dp = new DailyPrice();
            dp.setStock(stock);
            dp.setPriceDate(start.plusDays(i));
            dp.setClosingPrice(new BigDecimal("2500.00"));
            dp.setVolume(1000000L);
            prices.add(dp);
        }
    }

    @Test
    void testComputeForStock_InsufficientPrices() {
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(List.of());

        int result = service.computeForStock(stock, LocalDate.now());

        assertEquals(0, result);
        verifyNoInteractions(aggregator);
    }

    @Test
    void testComputeForStock_Success() {
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);
        when(indicatorComputationService.computeIndicators(eq(1L), any(LocalDate.class), any()))
                .thenReturn(List.of());
        when(strategyConfigService.getActiveStrategyNames())
                .thenReturn(Set.of("VOLUME", "RSI"));
        when(strategyConfigService.getConfigVersion()).thenReturn(1L);

        StrategyResult volumeResult = new StrategyResult(
                StrategySignal.BUY, 0.85, "Volume spike", "VOLUME", 5, 4.25,
                1500000L, 800000.0, 1200000.0, null);
        StrategyResult rsiResult = new StrategyResult(
                StrategySignal.HOLD, 0.5, "RSI neutral", "RSI", 7, 0.0,
                null, null, null, null);

        AggregatedSignalResult aggResult = new AggregatedSignalResult(
                StrategySignal.BUY, 4.25, List.of(volumeResult, rsiResult),
                12, 0.75, List.of(), List.of(), java.util.Map.of(), java.util.Map.of());

        when(aggregator.aggregate(eq(1L), any(), any(), any())).thenReturn(aggResult);
        when(weightRepository.saveAll(any())).thenReturn(List.of());

        int result = service.computeForStock(stock, LocalDate.now());

        assertEquals(2, result);
        verify(weightRepository).saveAll(any());
    }

    @Test
    void testGetWeightedHistory_NoData() {
        when(weightRepository.findByStockIdAndSignalDateBetweenOrderBySignalDateAsc(
                eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        List<SignalHistoryPoint> result = service.getWeightedHistory(
                1L, LocalDate.now().minusDays(30), LocalDate.now());

        assertNull(result);
    }

    @Test
    void testGetWeightedHistory_WithData() {
        Stock s = new Stock("TCS", "TCS", "IT");
        s.setId(2L);

        StrategyDailyWeight w1 = StrategyDailyWeight.builder()
                .stock(s).strategyName("VOLUME").signalDate(LocalDate.of(2026, 7, 10))
                .signalType("BUY").confidence(0.85).contribution(4.25)
                .priority(5).configVersion(1).computedAt(LocalDateTime.now())
                .build();

        StrategyDailyWeight w2 = StrategyDailyWeight.builder()
                .stock(s).strategyName("RSI").signalDate(LocalDate.of(2026, 7, 10))
                .signalType("HOLD").confidence(0.5).contribution(0.0)
                .priority(7).configVersion(1).computedAt(LocalDateTime.now())
                .build();

        when(weightRepository.findByStockIdAndSignalDateBetweenOrderBySignalDateAsc(
                eq(2L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(w1, w2));

        DailyPrice dp = new DailyPrice();
        dp.setPriceDate(LocalDate.of(2026, 7, 10));
        dp.setClosingPrice(new BigDecimal("3500.00"));
        when(dailyPriceRepository.findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(
                eq(2L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(dp));

        List<SignalHistoryPoint> result = service.getWeightedHistory(
                2L, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BUY", result.get(0).getRecommendation());
        assertEquals(4, result.get(0).getCompositeScore());
        assertEquals(new BigDecimal("3500.00"), result.get(0).getLatestPrice());
        assertNotNull(result.get(0).getStrategyBreakdown());
        assertEquals(2, result.get(0).getStrategyBreakdown().size());
    }

    @Test
    void testInvalidateByConfigVersion_NoChange() {
        when(strategyConfigService.getConfigVersion()).thenReturn(1L);

        service.invalidateByConfigVersion(1L);

        verifyNoInteractions(weightRepository);
    }

    @Test
    void testGetProcessedStockCount() {
        assertEquals(0, service.getProcessedStockCount());
    }

    @Test
    void testGetLastComputedTime() {
        assertNull(service.getLastComputedTime());
    }
}
