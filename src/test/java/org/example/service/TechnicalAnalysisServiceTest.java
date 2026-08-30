package org.example.service;

import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.entity.IndicatorType;
import org.example.repository.DailyPriceRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.TechnicalAnalysisService;
import org.example.service.TechnicalIndicatorPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicalAnalysisServiceTest {

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private StockService stockService;

    @Mock
    private TechnicalIndicatorRepository indicatorRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private TechnicalIndicatorPersistenceService persistenceService;

    @Mock
    private YahooFinanceSyncService yahooFinanceSyncService;

    @InjectMocks
    private TechnicalAnalysisService technicalAnalysisService;

    @Test
    void testCalculateIndicatorsForStock_StochasticK() {
        // Given
        Stock stock = new Stock("RELIANCE", "Reliance Industries", "Energy", null);
        stock.setId(1L);
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            prices.add(new DailyPrice(
                    stock,
                    new BigDecimal(String.valueOf(95 + i)), // closingPrice: 95, 96, ..., 108
                    new BigDecimal("100.00"), // openingPrice
                    new BigDecimal("110.00"), // highPrice
                    new BigDecimal("90.00"),  // lowPrice
                    1000L, // volume
                    LocalDate.of(2023, 1, i+1) // priceDate
            ));
        }
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stock.getId())).thenReturn(prices);
        when(stockService.getStockById(stock.getId())).thenReturn(stock);

        // When
        technicalAnalysisService.calculateIndicatorsForStock(stock.getId());

        // Then
        verify(dailyPriceRepository, times(1)).findAllByStockIdOrderByPriceDateAsc(stock.getId());
        verify(stockService, times(1)).getStockById(stock.getId());
    }

    @Test
    void testGetLatestIndicators() {
        // Given
        Stock stock = new Stock("RELIANCE", "Reliance Industries", "Energy", null);
        stock.setId(1L);
        when(stockService.getStockById(stock.getId())).thenReturn(stock);

        // When
        technicalAnalysisService.getLatestIndicators(stock.getId());

        // Then
        verify(stockService, times(1)).getStockById(stock.getId());
    }

    // --- parseRequiredDays helper tests (Layer B auto-backfill) ---

    @Test
    void testParseRequiredDays_numericMatch_returnsInt() {
        assertEquals(200, TechnicalAnalysisService.parseRequiredDays(
                "Need at least 200 days of price data to calculate SMA-200"));
        assertEquals(52, TechnicalAnalysisService.parseRequiredDays(
                "Need at least 52 days of price data to calculate Senkou Span B"));
        assertEquals(1, TechnicalAnalysisService.parseRequiredDays(
                "Need at least 1 day of price data to calculate VWAP"));
    }

    @Test
    void testParseRequiredDays_noMatch_returnsMinusOne() {
        assertEquals(-1, TechnicalAnalysisService.parseRequiredDays("Some other error"));
        assertEquals(-1, TechnicalAnalysisService.parseRequiredDays(null));
        assertEquals(-1, TechnicalAnalysisService.parseRequiredDays(""));
        // Phrasing variation: not enough %K values - no "Need at least N days"
        assertEquals(-1, TechnicalAnalysisService.parseRequiredDays(
                "Not enough %K values to calculate Stochastic %D"));
    }
}
