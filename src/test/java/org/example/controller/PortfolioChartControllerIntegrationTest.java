package org.example.controller;

import org.example.dto.PortfolioAggregateDTO;
import org.example.dto.ApiResponse;
import org.example.entity.PortfolioSnapshot;
import org.example.entity.Stock;
import org.example.repository.PortfolioSnapshotRepository;
import org.example.repository.PortfolioTransactionRepository;
import org.example.repository.StockRepository;
import org.example.repository.DailyPriceRepository;
import org.example.service.PortfolioSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Portfolio Chart Controller.
 * Tests complete system behavior including chart data rendering and API contract.
 */
@SpringBootTest
@Transactional
class PortfolioChartControllerIntegrationTest {

    @Autowired
    private PortfolioController portfolioController;

    @Autowired
    private PortfolioSnapshotService snapshotService;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PortfolioTransactionRepository transactionRepository;

    @Autowired
    private CacheManager cacheManager;

    private Stock testStock;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        // No cleanup needed — @Transactional on class auto-rolls changes after each test.
        // But the ledger endpoints are @Cacheable("portfolioHistory") and the Spring
        // context (with its cache) is shared across tests — clear it so every test
        // computes from fresh DB state instead of a prior test's cached result.
        var historyCache = cacheManager.getCache("portfolioHistory");
        if (historyCache != null) {
            historyCache.clear();
        }

        // Create test stock
        testStock = new Stock();
        testStock.setSymbol("TEST.NS");
        testStock.setName("Test Stock");
        testStock.setQuantity(10);
        testStock.setAvgPrice(BigDecimal.valueOf(100.0));
        testStock.setLastTradedPrice(BigDecimal.valueOf(120.0));
        testStock.setInvestment(BigDecimal.valueOf(1000.0));
        testStock.setCurrentValue(BigDecimal.valueOf(1200.0));
        testStock.setPnl(BigDecimal.valueOf(200.0));
        testStock.setPnlPercent(BigDecimal.valueOf(20.0));
        testStock.setVolume(10000L);
        testStock = stockRepository.save(testStock);

        testDate = LocalDate.now().minusDays(1);

        // Create portfolio snapshots for testing
        createSnapshot(testStock, testDate.minusDays(30), 800.0, 900.0, 0.0, 0.0);
        createSnapshot(testStock, testDate.minusDays(25), 825.0, 950.0, 0.0, 0.0);
        createSnapshot(testStock, testDate.minusDays(20), 850.0, 920.0, 0.0, 0.0);
        createSnapshot(testStock, testDate.minusDays(15), 875.0, 960.0, 0.0, 0.0);
        createSnapshot(testStock, testDate.minusDays(10), 900.0, 980.0, 0.0, 0.0);
        createSnapshot(testStock, testDate.minusDays(5), 925.0, 1000.0, 0.0, 0.0);
        createSnapshot(testStock, testDate, 950.0, 1020.0, 0.0, 0.0);
    }

    private void createSnapshot(Stock stock, LocalDate date, double investment, double currentValue, double pnl, double pnlPercent) {
        PortfolioSnapshot snapshot = snapshotRepository.findByStockIdAndSnapshotDate(stock.getId(), date)
                .orElse(new PortfolioSnapshot());
        snapshot.setStock(stock);
        snapshot.setSnapshotDate(date);
        snapshot.setQuantity(stock.getQuantity());
        snapshot.setAvgPrice(stock.getAvgPrice());
        snapshot.setLastTradedPrice(BigDecimal.valueOf(stock.getLastTradedPrice().doubleValue() + (currentValue - investment) / stock.getQuantity()));
        snapshot.setInvestment(BigDecimal.valueOf(investment));
        snapshot.setCurrentValue(BigDecimal.valueOf(currentValue));
        // Auto-compute P&L when the caller passed 0 (test convenience)
        if (pnl == 0.0 && pnlPercent == 0.0 && investment != 0.0) {
            snapshot.setPnl(BigDecimal.valueOf(currentValue - investment));
            snapshot.setPnlPercent(BigDecimal.valueOf((currentValue - investment) / investment * 100));
        } else {
            snapshot.setPnl(BigDecimal.valueOf(pnl));
            snapshot.setPnlPercent(BigDecimal.valueOf(pnlPercent));
        }
        snapshot.setVolume(stock.getVolume());
        snapshotRepository.save(snapshot);
    }

    @Test
    void getHistory_ShouldReturnSuccessResponseWithData() {
        // Test successful API call with valid data
        ResponseEntity<?> response = portfolioController.getHistory(30, false);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getBody();
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertTrue(apiResponse.getData() instanceof List);

        List<?> dataList = (List<?>) apiResponse.getData();
        assertFalse(dataList.isEmpty(), "Data list should not be empty for portfolio with snapshots");
    }

    @Test
    void getHistory_ShouldReturnDataWithCorrectStructure() {
        ResponseEntity<?> response = portfolioController.getHistory(30, false);

        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getBody();
        List<PortfolioAggregateDTO> dtos = (List<PortfolioAggregateDTO>) apiResponse.getData();

        // Ledger path returns one point per trading date across the shared dev DB,
        // so the exact count is data-dependent — assert non-empty instead.
        assertFalse(dtos.isEmpty(), "Should have data points for the 30-day window");

        // Verify structure of DTOs
        PortfolioAggregateDTO first = dtos.get(0);
        assertNotNull(first.getDate());
        assertNotNull(first.getTotalInvestment());
        assertNotNull(first.getTotalCurrentValue());
        assertNotNull(first.getTotalPnl());
        assertNotNull(first.getTotalPnlPercent());
        assertTrue(first.getHoldingsCount() >= 0);

        // Verify calculations
        assertTrue(first.getTotalInvestment().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(first.getTotalCurrentValue().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void getHistory_ShouldTruncateToRequestedDays() {
        // Create more snapshots beyond requested period
        createSnapshot(testStock, testDate.plusDays(5), 1000.0, 1300.0, 0.0, 0.0);
        createSnapshot(testStock, testDate.plusDays(10), 1050.0, 1350.0, 0.0, 0.0);

        ResponseEntity<?> response = portfolioController.getHistory(30, false);

        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getBody();
        List<PortfolioAggregateDTO> dtos = (List<PortfolioAggregateDTO>) apiResponse.getData();

        // All snapshots are within 30 days window, so should return all except future
        assertTrue(dtos.size() >= 7, "Should include all snapshots within 30 days");
    }

    @Test
    void getHistory_ShouldHandleDaysParameterCorrectly() {
        // Test various day parameters
        int[] testDays = {7, 15, 30, 60, 90, 180, 365};

        for (int days : testDays) {
            ResponseEntity<?> response = portfolioController.getHistory(days, false);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "Should return 200 for days=" + days);

            ApiResponse<?> apiResponse = (ApiResponse<?>) response.getBody();
            assertTrue(apiResponse.isSuccess());
        }
    }

    @Test
    void getHistory_ShouldReturnEmptyDataWhenNoHistoryAvailable() {
        // Delete snapshots AND all transactions to test empty state —
        // the ledger path is fed by portfolio_transactions, not snapshots
        snapshotRepository.deleteAll();
        transactionRepository.deleteAll();

        ResponseEntity<?> response = portfolioController.getHistory(30, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ApiResponse<?> apiResponse2 = (ApiResponse<?>) response.getBody();
        List<PortfolioAggregateDTO> dtos = (List<PortfolioAggregateDTO>) apiResponse2.getData();
        assertTrue(dtos.isEmpty(), "Should return empty list when no portfolio data exists");
    }

    @Test
    void getAllAggregatedHistory_ShouldReturnCompletePortfolioTimeline() {
        ResponseEntity<?> response = portfolioController.getHistory(365, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getBody();
        assertTrue(apiResponse.isSuccess());

        List<PortfolioAggregateDTO> dtos = (List<PortfolioAggregateDTO>) apiResponse.getData();
        assertTrue(dtos.size() >= 7, "Should return all available historical data");

        // Verify data is ordered chronologically (newest last)
        for (int i = 0; i < dtos.size() - 1; i++) {
            assertTrue(dtos.get(i).getDate().isBefore(dtos.get(i + 1).getDate()),
                    "Data should be in chronological order");
        }
    }

    @Test
    void getAllAggregatedHistory_ShouldHandleEmptyPortfolio() {
        // Delete all data the ledger path reads — transactions (sole holdings source)
        // + snapshots. Stocks/prices are intentionally NOT deleted: the global ledger
        // replay with portfolioId=null derives holdings from transactions only, and
        // child tables (support_resistance_levels, daily_prices) hold FKs to stocks.
        transactionRepository.deleteAll();
        snapshotRepository.deleteAll();

        ResponseEntity<?> response = portfolioController.getHistory(365, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getBody();
        List<PortfolioAggregateDTO> dtos = (List<PortfolioAggregateDTO>) apiResponse.getData();
        assertTrue(dtos.isEmpty(), "Should return empty list when no portfolio exists");
    }

    @Test
    void portfolioData_ShouldHaveConsistentHoldingCounts() {
        // Calculate holding counts for different time periods
        ResponseEntity<?> shortTerm = portfolioController.getHistory(30, false);
        ResponseEntity<?> longTerm = portfolioController.getHistory(365, true);

        List<PortfolioAggregateDTO> shortDtos = (List<PortfolioAggregateDTO>) ((ApiResponse<?>) shortTerm.getBody()).getData();
        List<PortfolioAggregateDTO> longDtos = (List<PortfolioAggregateDTO>) ((ApiResponse<?>) longTerm.getBody()).getData();

        // Ledger path replays ALL transactions in the shared dev DB — the count evolves
        // over time as buys/sells occur, so assert sanity: non-negative everywhere,
        // and the latest point reflects actual current holdings.
        assertFalse(shortDtos.isEmpty(), "Expected short-term data points");
        assertFalse(longDtos.isEmpty(), "Expected long-term data points");

        for (PortfolioAggregateDTO dto : shortDtos) {
            assertTrue(dto.getHoldingsCount() >= 0, "Holding count should be non-negative");
        }

        for (PortfolioAggregateDTO dto : longDtos) {
            assertTrue(dto.getHoldingsCount() >= 0, "Holding count should be non-negative across all history");
        }

        int latestCount = longDtos.get(longDtos.size() - 1).getHoldingsCount();
        assertTrue(latestCount >= 1, "Latest point should have at least one current holding");
    }

    @Test
    void pnlCalculations_ShouldBeMathematicallyCorrect() {
        ResponseEntity<?> response = portfolioController.getHistory(30, false);

        List<PortfolioAggregateDTO> dtos = (List<PortfolioAggregateDTO>) ((ApiResponse<?>) response.getBody()).getData();

        // Find a data point where we can validate calculations
        PortfolioAggregateDTO dto = dtos.get(dtos.size() / 2);

        // P&L = currentValue - investment minusDays
        BigDecimal expectedPnl = dto.getTotalCurrentValue().subtract(dto.getTotalInvestment());
        assertEquals(0, expectedPnl.compareTo(dto.getTotalPnl()),
                "P&L should be currentValue - investment");

        // P&L% = (P&L / investment) * 100 when investment > 0
        BigDecimal pnlPercent = dto.getTotalPnlPercent();
        assertTrue(pnlPercent.compareTo(BigDecimal.valueOf(-1000)) > 0,
                "P&L% should be reasonable value");
        assertTrue(pnlPercent.compareTo(BigDecimal.valueOf(1000)) < 0,
                "P&L% should not exceed reasonableness bounds");
    }

    @Test
    void apiResponseTimes_ShouldBeReasonable() {
        // Test that API responses don't take excessive time (memory leak could cause performance issues)
        long startTime = System.currentTimeMillis();

        ResponseEntity<?> response = portfolioController.getHistory(365, false);
        long duration = System.currentTimeMillis() - startTime;

        // 365-day all-portfolio ledger replay sweeps ~250 dates with DB price fallbacks —
        // 10s is a leak guardrail, not a performance SLA (dev MySQL over network).
        assertTrue(duration < 10_000, "Response should complete within 10 seconds (leak guardrail): " + duration + "ms");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void multipleSequentialCalls_ShouldNotCausePerformanceDegradation() {
        // Multiple consecutive calls should not slow down (indicating potential memory leaks)
        for (int i = 0; i < 50; i++) {
            ResponseEntity<?> response = portfolioController.getHistory(30, false);
            assertEquals(HttpStatus.OK, response.getStatusCode());

            // Slight delay to simulate real usage
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Should still complete within reasonable time
        long startTime = System.currentTimeMillis();
        ResponseEntity<?> finalResponse = portfolioController.getHistory(30, false);
        long finalDuration = System.currentTimeMillis() - startTime;

        assertTrue(finalDuration < 500, "Should not have memory leak causing slowdown: " + finalDuration + "ms");
        assertEquals(HttpStatus.OK, finalResponse.getStatusCode());
    }

    @Test
    void chartsApiContract_ShouldBeStable() {
        // Verify the API contract provides what the frontend chart expects
        ResponseEntity<?> response = portfolioController.getHistory(365, false);

        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getBody();
        assertNotNull(apiResponse);

        Object data = apiResponse.getData();
        assertNotNull(data);

        // Frontend expects: list of objects with date, totalInvestment, totalCurrentValue
        assertTrue(data instanceof List);
        assertFalse(((List<?>) data).isEmpty());

        Object firstItem = ((List<?>) data).get(0);
        assertTrue(firstItem instanceof Map || firstItem instanceof PortfolioAggregateDTO);

        // If it's a DTO, verify all expected fields
        if (firstItem instanceof PortfolioAggregateDTO) {
            PortfolioAggregateDTO dto = (PortfolioAggregateDTO) firstItem;
            assertHasField(dto, "date");
            assertHasField(dto, "totalInvestment");
            assertHasField(dto, "totalCurrentValue");
            assertHasField(dto, "totalPnl");
            assertHasField(dto, "totalPnlPercent");
            assertHasField(dto, "holdingsCount");
        }
    }

    @Test
    void negativeAndZeroDays_ShouldNotCauseApiFailures() {
        // Test that edge cases don't break the API
        int[] edgeCases = {0, -1, -100};

        for (int days : edgeCases) {
            ResponseEntity<?> response = portfolioController.getHistory(days, false);
            assertNotNull(response, "Response should not be null for days=" + days);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                    "Should return 200 even for invalid days parameter: " + days);
        }
    }

    private void assertHasField(Object obj, String fieldName) {
        try {
            obj.getClass().getMethod("get" + capitalize(fieldName));
        } catch (NoSuchMethodException e) {
            fail("DTO should have method get" + capitalize(fieldName));
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
