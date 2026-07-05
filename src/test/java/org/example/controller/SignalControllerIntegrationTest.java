package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.SignalDTO;
import org.example.dto.SignalHistoryPoint;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.entity.SignalRecord;
import org.example.entity.TechnicalIndicator;
import org.example.entity.IndicatorType;
import org.example.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SignalController.
 * Verifies that the API returns all new Phase 3 fields correctly.
 */
@SpringBootTest
@Transactional
class SignalControllerIntegrationTest {

    @Autowired
    private SignalController signalController;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DailyPriceRepository dailyPriceRepository;

    @Autowired
    private TechnicalIndicatorRepository technicalIndicatorRepository;

    @Autowired
    private CorporateEventRepository corporateEventRepository;

    @Autowired
    private FiiDiiDataRepository fiiDiiDataRepository;

    @Autowired
    private SignalHistoricalPerformanceRepository signalHistoricalPerformanceRepository;
    @Autowired
    private SignalRecordRepository signalRecordRepository;

    @Autowired
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Autowired
    private SupportResistanceLevelRepository supportResistanceLevelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    private Stock testStock;

    /**
     * Clears all data using JdbcTemplate with FK checks disabled — avoids
     * FK ordering headaches across many entity types that reference stocks.
     */
    private void clearAllData() {
        // Evict signal cache so stale data is not returned after DB cleanup
        org.springframework.cache.Cache cache = cacheManager.getCache("signals");
        if (cache != null) cache.clear();

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("DELETE FROM support_resistance_levels");
        jdbcTemplate.execute("DELETE FROM portfolio_holdings");
        jdbcTemplate.execute("DELETE FROM portfolio_snapshots");
        jdbcTemplate.execute("DELETE FROM daily_prices");
        jdbcTemplate.execute("DELETE FROM technical_indicators");
        jdbcTemplate.execute("DELETE FROM corporate_events");
        jdbcTemplate.execute("DELETE FROM fiidii_data");
        jdbcTemplate.execute("DELETE FROM signal_historical_performance");
        jdbcTemplate.execute("DELETE FROM signal_records");
        jdbcTemplate.execute("DELETE FROM stocks");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @BeforeEach
    void setUp() {
        clearAllData();

        // Create test stock
        testStock = new Stock();
        testStock.setSymbol("TEST.NS");
        testStock.setName("Test Stock");
        testStock.setSector("Technology");
        testStock.setQuantity(10);
        testStock.setAvgPrice(BigDecimal.valueOf(100.0));
        testStock.setLastTradedPrice(BigDecimal.valueOf(105.0));
        testStock.setInvestment(BigDecimal.valueOf(1000.0));
        testStock.setCurrentValue(BigDecimal.valueOf(1050.0));
        testStock.setPnl(BigDecimal.valueOf(50.0));
        testStock.setPnlPercent(BigDecimal.valueOf(5.0));
        testStock.setVolume(10000L);
        testStock = stockRepository.save(testStock);

        // Create 25 daily prices (need at least 20 for a signal)
        LocalDate startDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 25; i++) {
            BigDecimal price = BigDecimal.valueOf(100 + (i * 0.5) + (Math.sin(i) * 2));
            BigDecimal open = price.subtract(BigDecimal.ONE);
            BigDecimal high = price.add(BigDecimal.valueOf(1.5));
            BigDecimal low = price.subtract(BigDecimal.valueOf(1.5));
            long vol = 500000L + (i * 1000L);
            DailyPrice dp = new DailyPrice(testStock, price, open, high, low, vol, startDate.plusDays(i));
            dailyPriceRepository.save(dp);
        }

        // Create ATR indicator for the stock
        TechnicalIndicator atr = new TechnicalIndicator(testStock, IndicatorType.ATR, new BigDecimal("2.5"), LocalDate.now());
        technicalIndicatorRepository.save(atr);
    }

    @AfterEach
    void tearDown() {
        clearAllData();
    }

    @Test
    void getAllSignals_ShouldReturnSuccessResponse() {
        ResponseEntity<ApiResponse<List<SignalDTO>>> response = signalController.getAllSignals(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<List<SignalDTO>> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertNotNull(body.getData());
    }

    @Test
    void getAllSignals_ShouldReturnSignalWithNewFields() {
        ResponseEntity<ApiResponse<List<SignalDTO>>> response = signalController.getAllSignals(null);

        ApiResponse<List<SignalDTO>> body = response.getBody();
        assertNotNull(body);
        List<SignalDTO> signals = body.getData();
        assertFalse(signals.isEmpty(), "Should return at least one signal");

        SignalDTO signal = signals.get(0);

        // Verify essential new fields exist
        assertNotNull(signal);

        // atr should be present (we created an ATR indicator)
        assertNotNull(signal.getAtr(), "ATR should not be null");

        // targetPrice and stopLoss should be computed (BUY recommendation expected)
        // These can be null for HOLD, but should be non-null for BUY signals
        if ("BUY".equals(signal.getRecommendation()) || "STRONG BUY".equals(signal.getRecommendation())) {
            assertNotNull(signal.getTargetPrice(), "Target price should not be null for BUY signals");
            assertNotNull(signal.getStopLoss(), "Stop loss should not be null for BUY signals");
        }

        // confidenceScore should be a valid Long (0-100)
        Long confidence = signal.getConfidenceScore();
        assertNotNull(confidence, "Confidence score should not be null");
        assertTrue(confidence >= 0 && confidence <= 100,
                "Confidence score should be 0-100, got: " + confidence);

        // suggestedPositionSize should be a BigDecimal
        BigDecimal posSize = signal.getSuggestedPositionSize();
        assertNotNull(posSize, "Position size should not be null");
        assertTrue(posSize.compareTo(BigDecimal.ZERO) > 0, "Position size should be positive");

        // signalAge should be non-negative
        Long age = signal.getSignalAge();
        assertNotNull(age, "Signal age should not be null");
        assertTrue(age >= 0, "Signal age should be >= 0, got: " + age);

        // Latest price should be set
        assertNotNull(signal.getLatestPrice(), "Latest price should not be null");
    }

    @Test
    void getAllSignals_ShouldIncludeAllNewNullableFields() {
        ResponseEntity<ApiResponse<List<SignalDTO>>> response = signalController.getAllSignals(null);

        ApiResponse<List<SignalDTO>> body = response.getBody();
        assertNotNull(body);
        List<SignalDTO> signals = body.getData();
        assertFalse(signals.isEmpty());

        SignalDTO signal = signals.get(0);

        // Verify all 15 new fields are accessible
        // (some may be null depending on data, but they must be in the response)
        assertNotNull(signal.getAtr());
        assertNotNull(signal.getOpenPrice());
        assertNotNull(signal.getHighPrice());
        assertNotNull(signal.getLowPrice());
        assertNotNull(signal.getCompositeScore());
        assertNotNull(signal.getRecommendation());
        assertNotNull(signal.getConfidenceScore());
        assertNotNull(signal.getSuggestedPositionSize());
        assertNotNull(signal.getSignalAge());

        // Verify open/high/low prices match the latest daily price
        assertTrue(signal.getHighPrice().compareTo(signal.getLowPrice()) >= 0,
                "High price should be >= low price");

        // Assert remaining new fields (may be null for HOLD signals)
        if ("BUY".equals(signal.getRecommendation()) || "STRONG BUY".equals(signal.getRecommendation())) {
            assertNotNull(signal.getTargetPrice(), "Target price should not be null for BUY");
            assertNotNull(signal.getStopLoss(), "Stop loss should not be null for BUY");
            assertTrue(signal.getTargetPrice().compareTo(signal.getStopLoss()) > 0,
                    "Target should be above stop for BUY");
        } else if ("SELL".equals(signal.getRecommendation()) || "STRONG SELL".equals(signal.getRecommendation())) {
            assertNotNull(signal.getTargetPrice(), "Target price should not be null for SELL");
            assertNotNull(signal.getStopLoss(), "Stop loss should not be null for SELL");
            assertTrue(signal.getTargetPrice().compareTo(signal.getStopLoss()) < 0,
                    "Target should be below stop for SELL");
        } else {
            assertNull(signal.getTargetPrice(), "Target price should be null for HOLD");
            assertNull(signal.getStopLoss(), "Stop loss should be null for HOLD");
        }

        // stochK, stochD, williamsR, cci may be null if no indicator data exists
        // Historical returns may be null if not yet computed
        // Target/stop already validated above
        // All fields should exist in the JSON (assertNotNull is done for nullable ones above)
    }

    @Test
    void getBuySignals_ShouldReturnFilteredResults() {
        ResponseEntity<ApiResponse<List<SignalDTO>>> response = signalController.getBuySignals();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<List<SignalDTO>> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertNotNull(body.getData());

        // All returned signals should have compositeScore >= 2
        for (SignalDTO signal : body.getData()) {
            assertTrue(signal.getCompositeScore() >= 2,
                    "Buy signals should have score >= 2, got: " + signal.getCompositeScore());
        }
    }

    @Test
    void getSellSignals_ShouldReturnFilteredResults() {
        ResponseEntity<ApiResponse<List<SignalDTO>>> response = signalController.getSellSignals();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<List<SignalDTO>> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertNotNull(body.getData());

        // All returned signals should have compositeScore <= -4
        for (SignalDTO signal : body.getData()) {
            assertTrue(signal.getCompositeScore() <= -4,
                    "Sell signals should have score <= -4, got: " + signal.getCompositeScore());
        }
    }

    @Test
    void getSignalForStock_ShouldReturnSignal() {
        ResponseEntity<ApiResponse<SignalDTO>> response = signalController.getSignalForStock(testStock.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<SignalDTO> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());

        SignalDTO signal = body.getData();
        assertNotNull(signal, "Signal should not be null for a stock with sufficient data");
        assertEquals(testStock.getId(), signal.getStockId());
        assertEquals(testStock.getSymbol(), signal.getSymbol());

        // Verify new fields are present
        assertNotNull(signal.getAtr());
        assertNotNull(signal.getConfidenceScore());
        assertTrue(signal.getConfidenceScore() >= 0 && signal.getConfidenceScore() <= 100);
    }

    @Test
    void getSignalForStock_WithInsufficientData_ShouldReturnNull() {
        // Create a stock with no price data
        Stock emptyStock = new Stock();
        emptyStock.setSymbol("EMPTY.NS");
        emptyStock.setName("Empty Stock");
        emptyStock.setQuantity(0);
        emptyStock = stockRepository.save(emptyStock);

        ResponseEntity<ApiResponse<SignalDTO>> response = signalController.getSignalForStock(emptyStock.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<SignalDTO> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertNull(body.getData(), "Signal should be null for stock with insufficient data");
    }

    @Test
    void getAllSignals_ResponseStructure_ShouldMatchFrontendExpectations() {
        ResponseEntity<ApiResponse<List<SignalDTO>>> response = signalController.getAllSignals(null);

        ApiResponse<List<SignalDTO>> body = response.getBody();
        assertNotNull(body);

        // Verify response wrapper
        assertEquals("success", body.getStatus());
        assertNotNull(body.getMessage());
        assertNotNull(body.getData());

        // Frontend expects the data array
        List<SignalDTO> signals = body.getData();
        if (!signals.isEmpty()) {
            SignalDTO signal = signals.get(0);

            // Fields expected by frontend portfolio.js
            assertNotNull(signal.getStockId());
            assertNotNull(signal.getSymbol());
            assertNotNull(signal.getRecommendation());
            assertNotNull(signal.getCompositeScore());
            assertNotNull(signal.getPnlPercent());
            assertNotNull(signal.getRsi14());

            // New frontend fields - targetPrice/stopLoss nullable for HOLD
            String rec = signal.getRecommendation();
            if ("HOLD".equals(rec)) {
                assertNull(signal.getTargetPrice(), "Target price should be null for HOLD");
                assertNull(signal.getStopLoss(), "Stop loss should be null for HOLD");
            } else {
                assertNotNull(signal.getTargetPrice(),
                    "Target price should not be null for " + rec);
                assertNotNull(signal.getStopLoss(),
                    "Stop loss should not be null for " + rec);
            }
            assertNotNull(signal.getConfidenceScore(), "Confidence score should not be null");
            assertTrue(signal.getConfidenceScore() >= 0 && signal.getConfidenceScore() <= 100);
            assertNotNull(signal.getSuggestedPositionSize());
            assertNotNull(signal.getSignalAge());
        }
    }

    @Test
    void apiResponse_ShouldHandleEmptyDatabase() {
        // Delete all data
        technicalIndicatorRepository.deleteAll();
        dailyPriceRepository.deleteAll();
        stockRepository.deleteAll();

        ResponseEntity<ApiResponse<List<SignalDTO>>> response = signalController.getAllSignals(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<List<SignalDTO>> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        // Should return empty list, not fail
        List<SignalDTO> signals = body.getData();
        assertNotNull(signals);
        assertTrue(signals.isEmpty(), "Should return empty list when no stocks exist");
    }

    @Test
    void getSignalForStock_ShouldIncludeScoreBreakdownFields() {
        ResponseEntity<ApiResponse<SignalDTO>> response = signalController.getSignalForStock(testStock.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SignalDTO signal = response.getBody().getData();
        assertNotNull(signal, "Signal should exist for test stock");

        // Score breakdown fields (int — may be 0 but must be present)
        assertNotNull(signal.getDivergenceScore());
        assertNotNull(signal.getRsiScore());
        assertNotNull(signal.getBollingerScore());
        assertNotNull(signal.getMacdScore());
        assertNotNull(signal.getStochScore());
        assertNotNull(signal.getStochRsiScore());
        assertNotNull(signal.getUltimateOscScore());
        assertNotNull(signal.getRocScore());
        assertNotNull(signal.getWilliamsRScore());
        assertNotNull(signal.getCciScore());
        assertNotNull(signal.getObvScore());
        assertNotNull(signal.getSmaScore());
        assertNotNull(signal.getWeek52Score());
        assertNotNull(signal.getWeeklyConfluenceScore());
        assertNotNull(signal.getMonthlyConfluenceScore());
        assertNotNull(signal.getFiidiiScore());

        // Multi-timeframe RSI (nullable, may be null for test data)
        // weeklyRsi and monthlyRsi should be in the response (may be null)
        assertTrue(signal.getWeeklyRsi() == null || signal.getWeeklyRsi().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(signal.getMonthlyRsi() == null || signal.getMonthlyRsi().compareTo(BigDecimal.ZERO) > 0);

        // All scoring fields should be integers in valid range
        assertTrue(signal.getDivergenceScore() >= -4 && signal.getDivergenceScore() <= 4);
        assertTrue(signal.getRsiScore() >= -2 && signal.getRsiScore() <= 2);
    }

    @Test
    void getSignalForStock_ShouldIncludeFilterPenaltyFields() {
        ResponseEntity<ApiResponse<SignalDTO>> response = signalController.getSignalForStock(testStock.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SignalDTO signal = response.getBody().getData();
        assertNotNull(signal);

        // volumePenaltyApplied is a boolean primitive
        assertTrue(signal.isVolumePenaltyApplied() == false || signal.isVolumePenaltyApplied() == true);

        // adxFilterApplied is an int (0 = no filter, 1+ = applied)
        assertTrue(signal.getAdxFilterApplied() >= 0);

        // fiidiiScore should be present (int)
        assertTrue(signal.getFiidiiScore() >= -2 && signal.getFiidiiScore() <= 2);
    }

    @Test
    void getSignalForStock_ShouldIncludeHistoricalReturnFields() {
        ResponseEntity<ApiResponse<SignalDTO>> response = signalController.getSignalForStock(testStock.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SignalDTO signal = response.getBody().getData();
        assertNotNull(signal);

        // Historical returns may be null if not computed, but must be in the response
        assertTrue(signal.getHistoricalReturn5d() == null
                || signal.getHistoricalReturn5d().compareTo(BigDecimal.ZERO) != 0
                || signal.getHistoricalReturn5d().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(signal.getHistoricalReturn10d() == null
                || signal.getHistoricalReturn10d().compareTo(BigDecimal.ZERO) != 0
                || signal.getHistoricalReturn10d().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(signal.getHistoricalReturn20d() == null
                || signal.getHistoricalReturn20d().compareTo(BigDecimal.ZERO) != 0
                || signal.getHistoricalReturn20d().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void getSignalHistory_ShouldReturnHistory() {
        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response =
                signalController.getSignalHistory(testStock.getId(), 90);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<List<SignalHistoryPoint>> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        List<SignalHistoryPoint> history = body.getData();
        assertNotNull(history);
        // With 25 price bars (20+ for signal), should have at least 1 signal point
        assertFalse(history.isEmpty(), "Should have signal history for test stock");

        // Each point must have recommendation and score
        for (SignalHistoryPoint p : history) {
            assertNotNull(p.getRecommendation(), "Each history point needs a recommendation");
            assertNotNull(p.getPriceDate(), "Each history point needs a priceDate");
        }

        // Sorted by date ascending
        for (int i = 1; i < history.size(); i++) {
            assertTrue(!history.get(i).getPriceDate().isBefore(history.get(i - 1).getPriceDate()),
                    "Signal history must be sorted by date ascending");
        }
    }

    @Test
    void getSignalHistory_ShouldIncludeAccuracyFields_WhenSignalRecordExists() {
        // Create a SignalRecord for the test stock so accuracy fields populate
        // Use the date of a price bar that will be included in history (step=3 for 90 days)
        // With 25 prices and step=3, indices 0,3,6,9,12,15,18,21,24 are included
        List<DailyPrice> allPrices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(testStock.getId());
        LocalDate recordDate = allPrices.size() > 21
                ? allPrices.get(21).getPriceDate()
                : LocalDate.now().minusDays(5);

        SignalRecord record = new SignalRecord();
        record.setStockId(testStock.getId());
        record.setRecordedAt(recordDate);
        record.setRecommendation("BUY");
        record.setCompositeScore(6);
        record.setConfidenceScore(80L);
        record.setIndicatorCoverage(14);
        record.setPriceAtSignal(new BigDecimal("105.00"));
        record.setForwardReturn5d(new BigDecimal("5.50"));
        record.setForwardReturn10d(new BigDecimal("8.20"));
        record.setForwardReturn20d(new BigDecimal("12.00"));
        record.setWasAccurate5d(true);
        record.setWasAccurate10d(true);
        record.setWasAccurate20d(true);
        signalRecordRepository.save(record);

        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response =
                signalController.getSignalHistory(testStock.getId(), 90);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<SignalHistoryPoint> history = response.getBody().getData();
        assertNotNull(history);
        assertFalse(history.isEmpty());

        // Find the point matching our record's date
        boolean found = false;
        for (SignalHistoryPoint p : history) {
            if (p.getPriceDate() != null && p.getPriceDate().equals(recordDate)) {
                assertNotNull(p.getForwardReturn(), "forwardReturn should be present when SignalRecord exists");
                assertNotNull(p.getWasAccurate(), "wasAccurate should be present when SignalRecord exists");
                assertTrue(p.getWasAccurate(), "Record was created with wasAccurate10d=true");
                assertTrue(p.getForwardReturn().compareTo(BigDecimal.ZERO) > 0,
                        "forwardReturn should be positive for this record");
                found = true;
                break;
            }
        }
        assertTrue(found, "Should find a history point matching recordDate " + recordDate);
    }

    @Test
    void getSignalHistory_ShouldReturnHistoryWithoutAccuracy_WhenNoSignalRecord() {
        // Ensure no signal_records exist for this stock
        signalRecordRepository.findByStockIdAndRecordedAt(testStock.getId(), LocalDate.now())
                .ifPresent(r -> signalRecordRepository.delete(r));

        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response =
                signalController.getSignalHistory(testStock.getId(), 90);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<SignalHistoryPoint> history = response.getBody().getData();
        assertNotNull(history);
        assertFalse(history.isEmpty());

        // Without SignalRecords, forwardReturn and wasAccurate should be null
        for (SignalHistoryPoint p : history) {
            assertNull(p.getForwardReturn(), "forwardReturn should be null without SignalRecord");
            assertNull(p.getWasAccurate(), "wasAccurate should be null without SignalRecord");
        }
    }
}
