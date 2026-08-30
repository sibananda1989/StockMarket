package org.example.service;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.StrategyConditionGroup;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StrategyConditionGroupRepository;
import org.example.repository.StrategyConditionStatsCacheRepository;
import org.example.repository.StockRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.calculator.CandlestickPatternCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Inject a known spike factor via reflection (mirrors @Value default)
        setSpikeFactor(1.5);
    }

    private void setSpikeFactor(double value) {
        try {
            var field = StrategyConditionService.class.getDeclaredField("volumeSpikeFactor");
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DailyPrice dailyPrice(long volume, LocalDate date) {
        return new DailyPrice(null, new BigDecimal("100.0"), new BigDecimal("100.0"),
                new BigDecimal("100.0"), new BigDecimal("100.0"), volume, date);
    }

    @Test
    void testCalculateVolumeRatioExcludesLatestDay() throws Exception {
        // Latest day = 3000; prior 20 days all 1000.
        // Average (excluding latest) = 1000; ratio = 3000/1000 = 3.0
        LocalDate today = LocalDate.now();
        var last20 = new java.util.ArrayList<DailyPrice>();
        for (int i = 19; i >= 0; i--) {
            last20.add(dailyPrice(1000L, today.minusDays(i)));
        }
        last20.add(dailyPrice(3000L, today)); // latest day appended (most recent)

        when(dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(1L))
                .thenReturn(java.util.Optional.of(dailyPrice(3000L, today)));
        when(dailyPriceRepository.findLastNDays(1L, 20)).thenReturn(last20);

        Method m = StrategyConditionService.class.getDeclaredMethod("calculateVolumeRatio", Long.class);
        m.setAccessible(true);
        BigDecimal ratio = (BigDecimal) m.invoke(service, 1L);

        assertNotNull(ratio);
        // The latest day (3000) must NOT pollute the average → avg stays 1000
        assertEquals(3.0, ratio.doubleValue(), 0.001);
    }

    @Test
    void testCalculateVolumeRatio_WithLatestDayIncludedWouldDiffer() throws Exception {
        // If latest day were included, avg = (1000*20 + 3000)/21 ≈ 1142.8 → ratio ≈ 2.625.
        // Because it is EXCLUDED, ratio should be exactly 3.0 (proving exclusion).
        LocalDate today = LocalDate.now();
        var last20 = new java.util.ArrayList<DailyPrice>();
        for (int i = 19; i >= 0; i--) {
            last20.add(dailyPrice(1000L, today.minusDays(i)));
        }
        last20.add(dailyPrice(3000L, today));

        when(dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(1L))
                .thenReturn(java.util.Optional.of(dailyPrice(3000L, today)));
        when(dailyPriceRepository.findLastNDays(1L, 20)).thenReturn(last20);

        Method m = StrategyConditionService.class.getDeclaredMethod("calculateVolumeRatio", Long.class);
        m.setAccessible(true);
        BigDecimal ratio = (BigDecimal) m.invoke(service, 1L);

        assertNotEquals(2.625, ratio.doubleValue(), 0.001);
        assertEquals(3.0, ratio.doubleValue(), 0.001);
    }
}
