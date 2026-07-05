package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.ComparisonDTO;
import org.example.dto.SignalDTO;
import org.example.dto.SignalHistoryPoint;
import org.example.service.SignalService;
import org.example.strategy.engine.MultiStrategySignalEngine;
import org.example.strategy.model.AggregatedSignalResult;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiStrategySignalControllerTest {

    @Mock
    private MultiStrategySignalEngine engine;

    @Mock
    private SignalService signalService;

    @InjectMocks
    private MultiStrategySignalController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetSignal() {
        Long stockId = 1L;
        AggregatedSignalResult mockResult = AggregatedSignalResult.simple(
                StrategySignal.BUY, 5.0, List.of(), 20
        );
        when(engine.evaluate(stockId, null)).thenReturn(mockResult);

        ResponseEntity<ApiResponse<AggregatedSignalResult>> response = controller.getSignal(stockId, null);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("success", response.getBody().getStatus());
        assertEquals(mockResult, response.getBody().getData());
    }

    @Test
    void testGetSignal_WithActiveParam() {
        Long stockId = 1L;
        Set<String> active = Set.of("RSI", "MACD");
        AggregatedSignalResult mockResult = AggregatedSignalResult.simple(
                StrategySignal.BUY, 4.0, List.of(), 14
        );
        when(engine.evaluate(stockId, active)).thenReturn(mockResult);

        ResponseEntity<ApiResponse<AggregatedSignalResult>> response = controller.getSignal(stockId, active);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(mockResult, response.getBody().getData());
        verify(engine).evaluate(stockId, active);
    }

    @Test
    void testGetBreakdown() {
        Long stockId = 1L;
        List<StrategyResult> breakdown = List.of(
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.85, "RSI oversold", "RSI", 7)
        );
        AggregatedSignalResult mockResult = AggregatedSignalResult.simple(
                StrategySignal.BUY, 5.0, breakdown, 20
        );
        when(engine.evaluate(stockId, null)).thenReturn(mockResult);

        ResponseEntity<ApiResponse<List<StrategyResult>>> response = controller.getBreakdown(stockId, null);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(breakdown, response.getBody().getData());
    }

    @Test
    void testCompareSignals() {
        Long stockId = 1L;
        AggregatedSignalResult multiStrategy = AggregatedSignalResult.simple(
                StrategySignal.BUY, 5.0, List.of(), 20
        );
        SignalDTO existing = new SignalDTO();
        existing.setStockId(stockId);
        existing.setRecommendation("BUY");

        when(engine.evaluate(stockId, null)).thenReturn(multiStrategy);
        when(signalService.getComputedSignal(stockId)).thenReturn(existing);

        ResponseEntity<ApiResponse<ComparisonDTO>> response = controller.compareSignals(stockId, null);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(existing, response.getBody().getData().getExistingSignal());
        assertEquals(multiStrategy, response.getBody().getData().getMultiStrategySignal());
    }

    @Test
    void testCompareSignals_WithActiveParam() {
        Long stockId = 1L;
        Set<String> active = Set.of("RSI");
        AggregatedSignalResult multiStrategy = AggregatedSignalResult.simple(
                StrategySignal.HOLD, 1.0, List.of(), 7
        );
        SignalDTO existing = new SignalDTO();
        existing.setStockId(stockId);
        existing.setRecommendation("BUY");

        when(engine.evaluate(stockId, active)).thenReturn(multiStrategy);
        when(signalService.getComputedSignal(stockId)).thenReturn(existing);

        ResponseEntity<ApiResponse<ComparisonDTO>> response = controller.compareSignals(stockId, active);
        assertEquals(200, response.getStatusCodeValue());
        verify(engine).evaluate(stockId, active);
    }

    @Test
    void testGetSignalHistory() {
        Long stockId = 1L;
        int days = 90;
        List<SignalHistoryPoint> mockHistory = List.of(
                new SignalHistoryPoint(LocalDate.now(), "BUY", 5, new BigDecimal("100.00"), null, null, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, null, null, null, null, 0, 0, 0, null, 0, 0, 0, 0, List.of(), 3.0, -3.0, 5.0)
        );
        when(engine.evaluateHistory(stockId, days, null)).thenReturn(mockHistory);

        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response = controller.getSignalHistory(stockId, days, null);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(mockHistory, response.getBody().getData());
        verify(engine).evaluateHistory(stockId, days, null);
    }

    @Test
    void testGetSignalHistory_WithActiveParam() {
        Long stockId = 1L;
        int days = 30;
        Set<String> active = Set.of("RSI", "MACD");
        List<SignalHistoryPoint> mockHistory = List.of(
                new SignalHistoryPoint(LocalDate.now(), "BUY", 5, new BigDecimal("100.00"), null, null, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, null, null, null, null, 0, 0, 0, null, 0, 0, 0, 0, List.of(), 3.0, -3.0, 5.0)
        );
        when(engine.evaluateHistory(stockId, days, active)).thenReturn(mockHistory);

        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response = controller.getSignalHistory(stockId, days, active);
        assertEquals(200, response.getStatusCodeValue());
        verify(engine).evaluateHistory(stockId, days, active);
    }

    @Test
    void testGetSignalHistory_DefaultDays() {
        Long stockId = 1L;
        Set<String> active = Set.of();
        List<SignalHistoryPoint> mockHistory = List.of();
        when(engine.evaluateHistory(stockId, 90, active)).thenReturn(mockHistory);

        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response = controller.getSignalHistory(stockId, 90, active);
        assertEquals(200, response.getStatusCodeValue());
        verify(engine).evaluateHistory(stockId, 90, active);
    }

    @Test
    void testGetBreakdown_Empty() {
        Long stockId = 1L;
        AggregatedSignalResult mockResult = AggregatedSignalResult.simple(
                StrategySignal.HOLD, 0.0, List.of(), 0
        );
        when(engine.evaluate(stockId, null)).thenReturn(mockResult);

        ResponseEntity<ApiResponse<List<StrategyResult>>> response = controller.getBreakdown(stockId, null);
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    void testGetBreakdown_MultipleStrategies() {
        Long stockId = 1L;
        List<StrategyResult> breakdown = List.of(
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.85, "Buy reason 1", "RSI", 7),
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.80, "Buy reason 2", "MACD", 7),
                StrategyResult.withoutContribution(StrategySignal.HOLD, 0.50, "Hold reason", "VOLUME", 5)
        );
        AggregatedSignalResult mockResult = AggregatedSignalResult.simple(
                StrategySignal.BUY, 5.0, breakdown, 19
        );
        when(engine.evaluate(stockId, null)).thenReturn(mockResult);

        ResponseEntity<ApiResponse<List<StrategyResult>>> response = controller.getBreakdown(stockId, null);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(3, response.getBody().getData().size());
        assertEquals("RSI", response.getBody().getData().get(0).strategyName());
        assertEquals("MACD", response.getBody().getData().get(1).strategyName());
    }

    @Test
    void testCompareSignals_NoExistingSignal() {
        Long stockId = 1L;
        AggregatedSignalResult multiStrategy = AggregatedSignalResult.simple(
                StrategySignal.BUY, 5.0, List.of(), 20
        );
        when(engine.evaluate(stockId, null)).thenReturn(multiStrategy);
        when(signalService.getComputedSignal(stockId)).thenReturn(null);

        ResponseEntity<ApiResponse<ComparisonDTO>> response = controller.compareSignals(stockId, null);
        assertEquals(200, response.getStatusCodeValue());
        assertNull(response.getBody().getData().getExistingSignal());
        assertEquals(multiStrategy, response.getBody().getData().getMultiStrategySignal());
    }

    @Test
    void testGetSignal_ErrorHandling() {
        Long stockId = 1L;
        when(engine.evaluate(stockId, null)).thenThrow(new RuntimeException("Engine error"));

        // Controller doesn't have try-catch, so exception propagates
        assertThrows(RuntimeException.class, () -> controller.getSignal(stockId, null));
    }

    @Test
    void testGetBreakdown_ErrorHandling() {
        Long stockId = 1L;
        when(engine.evaluate(stockId, null)).thenThrow(new RuntimeException("Engine error"));

        // Controller doesn't have try-catch, so exception propagates
        assertThrows(RuntimeException.class, () -> controller.getBreakdown(stockId, null));
    }

    @Test
    void testCompareSignals_ErrorHandling() {
        Long stockId = 1L;
        when(engine.evaluate(stockId, null)).thenThrow(new RuntimeException("Engine error"));

        // Controller doesn't have try-catch, so exception propagates
        assertThrows(RuntimeException.class, () -> controller.compareSignals(stockId, null));
    }

    @Test
    void testGetBreakdown_WithActiveParam() {
        Long stockId = 1L;
        Set<String> active = Set.of("RSI");
        List<StrategyResult> breakdown = List.of(
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.85, "RSI oversold", "RSI", 7)
        );
        AggregatedSignalResult mockResult = AggregatedSignalResult.simple(
                StrategySignal.BUY, 5.0, breakdown, 7
        );
        when(engine.evaluate(stockId, active)).thenReturn(mockResult);

        ResponseEntity<ApiResponse<List<StrategyResult>>> response = controller.getBreakdown(stockId, active);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(breakdown, response.getBody().getData());
        verify(engine).evaluate(stockId, active);
    }

    @Test
    void testGetSignalHistory_MergesLegacyScores() {
        Long stockId = 1L;
        int days = 90;
        LocalDate commonDate = LocalDate.now();

        SignalHistoryPoint multiPoint = new SignalHistoryPoint();
        multiPoint.setPriceDate(commonDate);
        multiPoint.setRecommendation("BUY");
        multiPoint.setCompositeScore(5);

        SignalHistoryPoint legacyPoint = new SignalHistoryPoint();
        legacyPoint.setPriceDate(commonDate);
        legacyPoint.setRsiScore(10);
        legacyPoint.setSmaScore(-5);
        legacyPoint.setBollingerScore(3);
        legacyPoint.setMacdScore(7);
        legacyPoint.setTrendDirectionScore(2);
        legacyPoint.setCandlestickScore(4);
        legacyPoint.setCandlestickPattern("ENGULFING");
        legacyPoint.setDivergenceScore(1);
        legacyPoint.setWeeklyConfluenceScore(6);
        legacyPoint.setFiidiiScore(-2);
        legacyPoint.setRsi14(new BigDecimal("65.0"));
        legacyPoint.setAdx(new BigDecimal("25.0"));
        legacyPoint.setSma20(new BigDecimal("150.0"));
        legacyPoint.setSma50(new BigDecimal("145.0"));

        when(engine.evaluateHistory(stockId, days, null)).thenReturn(List.of(multiPoint));
        when(signalService.getSignalHistory(stockId, days)).thenReturn(List.of(legacyPoint));

        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response = controller.getSignalHistory(stockId, days, null);
        assertEquals(200, response.getStatusCodeValue());

        SignalHistoryPoint result = response.getBody().getData().get(0);
        assertEquals(10, result.getRsiScore());
        assertEquals(-5, result.getSmaScore());
        assertEquals(3, result.getBollingerScore());
        assertEquals(7, result.getMacdScore());
        assertEquals(2, result.getTrendDirectionScore());
        assertEquals(4, result.getCandlestickScore());
        assertEquals("ENGULFING", result.getCandlestickPattern());
        assertEquals(1, result.getDivergenceScore());
        assertEquals(6, result.getWeeklyConfluenceScore());
        assertEquals(-2, result.getFiidiiScore());
        assertEquals(new BigDecimal("65.0"), result.getRsi14());
        assertEquals(new BigDecimal("25.0"), result.getAdx());
        assertEquals(new BigDecimal("150.0"), result.getSma20());
        assertEquals(new BigDecimal("145.0"), result.getSma50());
    }

    @Test
    void testGetSignalHistory_NoLegacyMatch() {
        Long stockId = 1L;
        int days = 90;
        LocalDate multiDate = LocalDate.now();
        LocalDate legacyDate = LocalDate.now().minusDays(1);

        SignalHistoryPoint multiPoint = new SignalHistoryPoint();
        multiPoint.setPriceDate(multiDate);
        multiPoint.setRecommendation("BUY");
        multiPoint.setCompositeScore(5);

        SignalHistoryPoint legacyPoint = new SignalHistoryPoint();
        legacyPoint.setPriceDate(legacyDate);
        legacyPoint.setRsiScore(10);

        when(engine.evaluateHistory(stockId, days, null)).thenReturn(List.of(multiPoint));
        when(signalService.getSignalHistory(stockId, days)).thenReturn(List.of(legacyPoint));

        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response = controller.getSignalHistory(stockId, days, null);
        assertEquals(200, response.getStatusCodeValue());

        SignalHistoryPoint result = response.getBody().getData().get(0);
        assertEquals(0, result.getRsiScore());
    }

    @Test
    void testGetSignalHistory_ErrorHandling() {
        Long stockId = 1L;
        int days = 90;
        when(engine.evaluateHistory(stockId, days, null)).thenThrow(new RuntimeException("History error"));

        assertThrows(RuntimeException.class, () -> controller.getSignalHistory(stockId, days, null));
    }

    @Test
    void testGetSignalHistory_WithActiveAndMerge() {
        Long stockId = 1L;
        int days = 30;
        Set<String> active = Set.of("RSI");
        LocalDate commonDate = LocalDate.now();

        SignalHistoryPoint multiPoint = new SignalHistoryPoint();
        multiPoint.setPriceDate(commonDate);
        multiPoint.setRecommendation("HOLD");

        SignalHistoryPoint legacyPoint = new SignalHistoryPoint();
        legacyPoint.setPriceDate(commonDate);
        legacyPoint.setRsiScore(8);

        when(engine.evaluateHistory(stockId, days, active)).thenReturn(List.of(multiPoint));
        when(signalService.getSignalHistory(stockId, days)).thenReturn(List.of(legacyPoint));

        ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> response = controller.getSignalHistory(stockId, days, active);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(8, response.getBody().getData().get(0).getRsiScore());
        verify(engine).evaluateHistory(stockId, days, active);
    }
}
