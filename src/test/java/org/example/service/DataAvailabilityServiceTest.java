package org.example.service;

import org.example.dto.DataAvailabilitySummaryDTO;
import org.example.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataAvailabilityServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private SignalRecordRepository signalRecordRepository;
    @Mock
    private TechnicalIndicatorRepository technicalIndicatorRepository;
    @Mock
    private SupportResistanceLevelRepository supportResistanceLevelRepository;
    @Mock
    private FiiDiiDataRepository fiiDiiDataRepository;
    @Mock
    private FundamentalDataRepository fundamentalDataRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;

    @InjectMocks
    private DataAvailabilityService dataAvailabilityService;

    @Test
    void testGetSummary_withData() {
        when(stockRepository.count()).thenReturn(100L);
        when(dailyPriceRepository.countDistinctStocks()).thenReturn(80L);
        when(signalRecordRepository.countDistinctStocks()).thenReturn(60L);
        when(technicalIndicatorRepository.countDistinctStocksWithIndicatorOrSr()).thenReturn(70L);
        when(fiiDiiDataRepository.count()).thenReturn(250L);
        when(fundamentalDataRepository.countDistinctStocks()).thenReturn(50L);
        when(portfolioRepository.count()).thenReturn(3L);
        when(portfolioHoldingRepository.count()).thenReturn(120L);
        when(portfolioHoldingRepository.countDistinctStocks()).thenReturn(90L);

        DataAvailabilitySummaryDTO result = dataAvailabilityService.getSummary();

        assertEquals(100L, result.getTotalStocks());
        assertEquals(80L, result.getStocksWithPriceHistory());
        assertEquals(60L, result.getStocksWithSignals());
        assertEquals(70L, result.getStocksWithIndicatorsAndSr());
        assertEquals(250L, result.getFiidiiRecords());
        assertTrue(result.isFiidiiAvailable());
        assertEquals(50L, result.getStocksWithFundamentals());
        assertEquals(3L, result.getPortfolioCount());
        assertEquals(120L, result.getHoldingsCount());
        assertEquals(90L, result.getStocksWithHoldings());
    }

    @Test
    void testGetSummary_noFiiDii() {
        when(stockRepository.count()).thenReturn(50L);
        when(dailyPriceRepository.countDistinctStocks()).thenReturn(0L);
        when(signalRecordRepository.countDistinctStocks()).thenReturn(0L);
        when(technicalIndicatorRepository.countDistinctStocksWithIndicatorOrSr()).thenReturn(0L);
        when(fiiDiiDataRepository.count()).thenReturn(0L);
        when(fundamentalDataRepository.countDistinctStocks()).thenReturn(0L);
        when(portfolioRepository.count()).thenReturn(0L);
        when(portfolioHoldingRepository.count()).thenReturn(0L);
        when(portfolioHoldingRepository.countDistinctStocks()).thenReturn(0L);

        DataAvailabilitySummaryDTO result = dataAvailabilityService.getSummary();

        assertEquals(50L, result.getTotalStocks());
        assertEquals(0L, result.getFiidiiRecords());
        assertFalse(result.isFiidiiAvailable());
    }
}
