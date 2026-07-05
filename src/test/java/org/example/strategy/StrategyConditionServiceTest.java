package org.example.strategy;

import org.example.dto.StrategyConditionGroupDTO;
import org.example.entity.IndicatorType;
import org.example.entity.StrategyConditionGroup;
import org.example.entity.StrategyConditionStatsCache;
import org.example.entity.TechnicalIndicator;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StrategyConditionGroupRepository;
import org.example.repository.StrategyConditionStatsCacheRepository;
import org.example.repository.StockRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.StrategyConditionService;
import org.example.service.StrategyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrategyConditionServiceTest {

    @Mock
    private StrategyConditionGroupRepository conditionRepository;
    @Mock
    private StrategyConditionStatsCacheRepository statsCacheRepository;
    @Mock
    private TechnicalIndicatorRepository indicatorRepository;
    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StrategyConfigService strategyConfigService;

    @InjectMocks
    private StrategyConditionService service;

    @Captor
    private ArgumentCaptor<List<StrategyConditionGroup>> conditionCaptor;
    @Captor
    private ArgumentCaptor<List<StrategyConditionStatsCache>> statsCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSeedDefaultConditions_Inserts38Conditions() {
        when(conditionRepository.count()).thenReturn(0L);

        service.seedDefaultConditions();

        verify(conditionRepository).saveAll(conditionCaptor.capture());
        assertEquals(38, conditionCaptor.getValue().size());
    }

    @Test
    void testSeedDefaultConditions_Idempotent() {
        when(conditionRepository.count()).thenReturn(38L);

        service.seedDefaultConditions();

        verify(conditionRepository, never()).saveAll(any());
    }

    @Test
    void testSeedDefaultConditions_DataIntegrityExceptionHandled() {
        when(conditionRepository.count()).thenReturn(0L);
        doThrow(DataIntegrityViolationException.class).when(conditionRepository).saveAll(any());

        assertDoesNotThrow(() -> service.seedDefaultConditions());
    }

    @Test
    void testGetConditions_ReturnsCorrectForRsi() {
        List<StrategyConditionGroup> expected = List.of(
                StrategyConditionGroup.builder().strategyName("RSI").conditionId("rsi_strong_buy").displayOrder(0).build(),
                StrategyConditionGroup.builder().strategyName("RSI").conditionId("rsi_buy").displayOrder(1).build()
        );
        when(conditionRepository.findByStrategyNameOrderByDisplayOrder("RSI")).thenReturn(expected);

        List<StrategyConditionGroup> result = service.getConditions("RSI");

        assertEquals(2, result.size());
        assertEquals("rsi_strong_buy", result.get(0).getConditionId());
        assertEquals("rsi_buy", result.get(1).getConditionId());
    }

    @Test
    void testSaveConditions_DeletesOldAndInsertsNew() {
        String strategyName = "RSI";
        List<StrategyConditionGroupDTO> dtos = List.of(
                StrategyConditionGroupDTO.builder()
                        .conditionId("test_cond_1").signal("BUY").fieldLabel("RSI")
                        .operator("<").thresholdValue(BigDecimal.valueOf(30))
                        .confidence("high").displayOrder(0).build()
        );

        service.saveConditions(strategyName, dtos);

        verify(conditionRepository).deleteByStrategyName(strategyName);
        verify(conditionRepository).saveAll(conditionCaptor.capture());
        assertEquals(1, conditionCaptor.getValue().size());
        verify(statsCacheRepository).deleteByStrategyName(strategyName);
    }

    @Test
    void testSaveConditions_ThrowsOnInvalidSignal() {
        List<StrategyConditionGroupDTO> dtos = List.of(
                StrategyConditionGroupDTO.builder()
                        .conditionId("c1").signal("INVALID").fieldLabel("RSI")
                        .operator("<").confidence("high").build()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.saveConditions("RSI", dtos));
    }

    @Test
    void testSaveConditions_ThrowsOnInvalidOperator() {
        List<StrategyConditionGroupDTO> dtos = List.of(
                StrategyConditionGroupDTO.builder()
                        .conditionId("c1").signal("BUY").fieldLabel("RSI")
                        .operator("invalid_op").confidence("high").build()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.saveConditions("RSI", dtos));
    }

    @Test
    void testSaveConditions_ThrowsOnInvalidConfidence() {
        List<StrategyConditionGroupDTO> dtos = List.of(
                StrategyConditionGroupDTO.builder()
                        .conditionId("c1").signal("BUY").fieldLabel("RSI")
                        .operator("<").confidence("unknown").build()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.saveConditions("RSI", dtos));
    }

    @Test
    void testComputeAndCacheStats_CountsRsiLessThan30() {
        when(stockRepository.findAll()).thenReturn(List.of(
                createStock(1L), createStock(2L)
        ));
        when(conditionRepository.findAll()).thenReturn(List.of(
                StrategyConditionGroup.builder()
                        .strategyName("RSI").conditionId("rsi_strong_buy")
                        .fieldLabel("RSI").operator("<")
                        .thresholdValue(BigDecimal.valueOf(30))
                        .build()
        ));

        when(indicatorRepository.findLatestForStock(1L)).thenReturn(List.of(
                createIndicator(IndicatorType.RSI, BigDecimal.valueOf(25))
        ));
        when(indicatorRepository.findLatestForStock(2L)).thenReturn(List.of(
                createIndicator(IndicatorType.RSI, BigDecimal.valueOf(35))
        ));

        var price1 = new org.example.entity.DailyPrice();
        price1.setClosingPrice(BigDecimal.valueOf(100));
        price1.setPriceDate(LocalDate.now());
        var price2 = new org.example.entity.DailyPrice();
        price2.setClosingPrice(BigDecimal.valueOf(200));
        price2.setPriceDate(LocalDate.now());

        when(dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(1L)).thenReturn(Optional.of(price1));
        when(dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(2L)).thenReturn(Optional.of(price2));

        service.computeAndCacheStats();

        verify(statsCacheRepository).deleteAll();
        verify(statsCacheRepository).saveAll(statsCaptor.capture());
        List<StrategyConditionStatsCache> entries = statsCaptor.getValue();
        assertEquals(1, entries.size());
        assertEquals(1, entries.get(0).getStockCount());
    }

    @Test
    void testComputeAndCacheStats_SkipsStockWhenIndicatorNull() {
        when(stockRepository.findAll()).thenReturn(List.of(
                createStock(1L)
        ));
        when(conditionRepository.findAll()).thenReturn(List.of(
                StrategyConditionGroup.builder()
                        .strategyName("RSI").conditionId("rsi_strong_buy")
                        .fieldLabel("RSI").operator("<")
                        .thresholdValue(BigDecimal.valueOf(30))
                        .build()
        ));

        when(indicatorRepository.findLatestForStock(1L)).thenReturn(Collections.emptyList());

        service.computeAndCacheStats();

        verify(statsCacheRepository).saveAll(statsCaptor.capture());
        assertEquals(1, statsCaptor.getValue().size());
        assertEquals(0, statsCaptor.getValue().get(0).getStockCount());
    }

    @Test
    void testGetCachedStats_ReturnsGroupedMap() {
        when(statsCacheRepository.findAll()).thenReturn(List.of(
                createStats("RSI", "rsi_strong_buy", 5),
                createStats("RSI", "rsi_buy", 3),
                createStats("MACD", "macd_cross_above", 2)
        ));

        Map<String, List<org.example.dto.ConditionStatDTO>> result = service.getCachedStats();

        assertEquals(2, result.size());
        assertEquals(2, result.get("RSI").size());
        assertEquals(1, result.get("MACD").size());
        assertEquals(5, result.get("RSI").get(0).getStockCount());
    }

    @Test
    void testResetToDefaults_ClearsAndReseeds() {
        when(conditionRepository.count()).thenReturn(0L);

        service.resetToDefaults();

        verify(conditionRepository).deleteAll();
        verify(statsCacheRepository).deleteAll();
        verify(conditionRepository).saveAll(any());
    }

    @Test
    void testEvaluateConditionForStock_BetweenOperator() {
        var condition = StrategyConditionGroup.builder()
                .strategyName("RSI").conditionId("rsi_neutral")
                .fieldLabel("RSI").operator("between")
                .thresholdValue(BigDecimal.valueOf(40))
                .thresholdValue2(BigDecimal.valueOf(60))
                .build();

        when(indicatorRepository.findLatestForStock(1L)).thenReturn(List.of(
                createIndicator(IndicatorType.RSI, BigDecimal.valueOf(50))
        ));
        var price = new org.example.entity.DailyPrice();
        price.setClosingPrice(BigDecimal.valueOf(100));
        price.setPriceDate(LocalDate.now());
        when(dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(1L)).thenReturn(Optional.of(price));

        boolean result = service.evaluateConditionForStock(1L, condition);

        assertTrue(result);
    }

    private static org.example.entity.Stock createStock(Long id) {
        var s = new org.example.entity.Stock();
        s.setId(id);
        return s;
    }

    private static TechnicalIndicator createIndicator(IndicatorType type, BigDecimal value) {
        var ti = new TechnicalIndicator();
        ti.setIndicatorType(type);
        ti.setValue(value);
        ti.setCalculationDate(LocalDate.now());
        return ti;
    }

    private static StrategyConditionStatsCache createStats(String strategy, String conditionId, int count) {
        return StrategyConditionStatsCache.builder()
                .strategyName(strategy)
                .conditionId(conditionId)
                .stockCount(count)
                .computedAt(LocalDateTime.now())
                .build();
    }
}
