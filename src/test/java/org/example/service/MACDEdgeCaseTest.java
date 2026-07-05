package org.example.service;

import org.example.dto.SignalDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.calculator.MacdLineCalculator;
import org.example.service.calculator.MacdSignalCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.example.service.*;
import org.example.service.calculator.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MACDEdgeCaseTest {

    @Mock
    private StockService stockService;
    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private TechnicalIndicatorRepository technicalIndicatorRepository;
    @Mock
    private CorporateEventService corporateEventService;
    @Mock
    private PriceAggregationService aggregationService;
    @Mock
    private FiiDiiService fiiDiiService;
    @Mock
    private SupportResistanceService supportResistanceService;
    @Mock
    private BreakoutDetector breakoutDetector;

    @InjectMocks
    private SignalService signalService;

    private Stock testStock;
    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        testStock = new Stock();
        testStock.setId(1L);
        testStock.setSymbol("TEST");
        testStock.setName("Test Stock");
        testStock.setSector("Technology");
        
        prices = createPriceSeries(100.0, 0.5, 35);
        
        // Mock corporate events to avoid NPE
        when(corporateEventService.getEventsForSymbol("TEST")).thenReturn(Collections.emptyList());
    }

    @Test
    void testMACD_WhenClosesSizeEquals26_ShouldComputeCorrectly() {
        // Given: Exactly 26 closing prices (edge case where loop doesn't run)
        List<DailyPrice> prices26 = prices.subList(0, 26);
        
        // When: Compute signal using reflection to bypass private method
        SignalDTO dto = computeSignalWithMocks(testStock, prices26);
        
        // Then: Verify MACD is computed (not null) and no exception is thrown
        assertNotNull(dto, "SignalDTO should not be null");
        assertNotNull(dto.getMacd(), "MACD should be computed even when closes.size() == 26");
        assertNull(dto.getMacdSignal(), "MACD Signal should be null when insufficient data for signal line");
        assertNull(dto.getMacdHistogram(), "MACD Histogram should be null when MACD Signal is null");
    }

    @Test
    void testMACD_WhenClosesSizeEquals27_ShouldComputeCorrectly() {
        // Given: 27 closing prices (loop runs once)
        List<DailyPrice> prices27 = prices.subList(0, 27);
        
        // When: Compute signal
        SignalDTO dto = computeSignalWithMocks(testStock, prices27);
        
        // Then: Verify MACD is computed
        assertNotNull(dto, "SignalDTO should not be null");
        assertNotNull(dto.getMacd(), "MACD should be computed when closes.size() == 27");
        assertNull(dto.getMacdSignal(), "MACD Signal should be null when insufficient data for signal line");
        assertNull(dto.getMacdHistogram(), "MACD Histogram should be null when MACD Signal is null");
    }

    @Test
    void testMACD_InlineVsStandaloneCalculator_ShouldMatch() {
        // Given: 35 closing prices (enough for MACD Signal)
        List<DailyPrice> prices35 = prices.subList(0, 35);
        
        // When: Compute signal using inline logic
        SignalDTO dto = computeSignalWithMocks(testStock, prices35);
        
        // Compute expected MACD using standalone calculators
        MacdLineCalculator macdLineCalculator = new MacdLineCalculator();
        MacdSignalCalculator macdSignalCalculator = new MacdSignalCalculator();
        BigDecimal expectedMacd = macdLineCalculator.calculate(prices35);
        BigDecimal expectedMacdSignal = macdSignalCalculator.calculate(prices35);
        BigDecimal expectedMacdHistogram = expectedMacd.subtract(expectedMacdSignal);
        
        // Then: Verify inline computation matches standalone calculators
        assertNotNull(dto, "SignalDTO should not be null");
        assertEquals(expectedMacd.setScale(4, RoundingMode.HALF_UP), dto.getMacd().setScale(4, RoundingMode.HALF_UP),
                "Inline MACD should match standalone calculator");
        assertEquals(expectedMacdSignal.setScale(4, RoundingMode.HALF_UP), dto.getMacdSignal().setScale(4, RoundingMode.HALF_UP),
                "Inline MACD Signal should match standalone calculator");
        assertEquals(expectedMacdHistogram.setScale(4, RoundingMode.HALF_UP), dto.getMacdHistogram().setScale(4, RoundingMode.HALF_UP),
                "Inline MACD Histogram should match standalone calculator");
    }

    @Test
    void testMACD_WhenClosesSizeEquals25_ShouldHandleGracefully() {
        // Given: 25 closing prices (insufficient for MACD)
        List<DailyPrice> prices25 = prices.subList(0, 25);
        
        // When: Compute signal
        SignalDTO dto = computeSignalWithMocks(testStock, prices25);
        
        // Then: Verify MACD fields are null (graceful handling)
        assertNotNull(dto, "SignalDTO should not be null");
        assertNull(dto.getMacd(), "MACD should be null when insufficient data");
        assertNull(dto.getMacdSignal(), "MACD Signal should be null when insufficient data");
        assertNull(dto.getMacdHistogram(), "MACD Histogram should be null when insufficient data");
    }

    // Helper method to create a SignalDTO with mocked dependencies
    private SignalDTO computeSignalWithMocks(Stock stock, List<DailyPrice> prices) {
        try {
            // Mock all required dependencies to avoid NPEs
            when(aggregationService.aggregateWeekly(prices)).thenReturn(Collections.emptyList());
            when(aggregationService.aggregateMonthly(prices)).thenReturn(Collections.emptyList());
            when(supportResistanceService.getLatestLevels(stock.getId())).thenReturn(null);
            when(fiiDiiService.getScoreAdjustment()).thenReturn(0);
            BreakoutDetector.BreakoutResult breakoutResult = new BreakoutDetector.BreakoutResult();
            breakoutResult.volumeBreakout = false;
            breakoutResult.gapUp = false;
            breakoutResult.gapDown = false;
            breakoutResult.rangeBreakout = false;
            breakoutResult.score = 0;
            when(breakoutDetector.detect(prices, stock.getId())).thenReturn(breakoutResult);
            
            // Use reflection to access private method
            java.lang.reflect.Method method = SignalService.class.getDeclaredMethod(
                    "computeBaseSignalDto", Stock.class, List.class, boolean.class);
            method.setAccessible(true);
            return (SignalDTO) method.invoke(signalService, stock, prices, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute signal", e);
        }
    }

    private List<DailyPrice> createPriceSeries(double start, double increment, int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = start + i * increment;
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(close)),
                    new BigDecimal(String.valueOf(close - 0.5)),
                    new BigDecimal(String.valueOf(close + 0.5)),
                    new BigDecimal(String.valueOf(close - 1.0)),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }
        return prices;
    }
}