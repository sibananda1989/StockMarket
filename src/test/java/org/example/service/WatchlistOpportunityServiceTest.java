package org.example.service;

import org.example.dto.*;
import org.example.entity.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistOpportunityServiceTest {

    @Mock
    private SignalService signalService;

    @Mock
    private PortfolioService portfolioService;

    @InjectMocks
    private WatchlistOpportunityService watchlistOpportunityService;

    private Portfolio defaultPortfolio;
    private HoldingDTO holding1;
    private HoldingDTO holding2;
    private SignalDTO signal1;
    private SignalDTO signal2;

    @BeforeEach
    void setUp() {
        defaultPortfolio = new Portfolio("Default Portfolio", "Test", true);
        defaultPortfolio.setId(1L);

        holding1 = HoldingDTO.builder()
                .id(1L)
                .portfolioId(1L)
                .stockId(100L)
                .symbol("RELIANCE")
                .name("Reliance Industries Ltd")
                .sector("Energy")
                .quantity(10)
                .avgPrice(new BigDecimal("2500.00"))
                .build();

        holding2 = HoldingDTO.builder()
                .id(2L)
                .portfolioId(1L)
                .stockId(101L)
                .symbol("TCS")
                .name("Tata Consultancy Services")
                .sector("IT")
                .quantity(5)
                .avgPrice(new BigDecimal("3500.00"))
                .build();

        signal1 = new SignalDTO();
        signal1.setStockId(100L);
        signal1.setSymbol("RELIANCE");
        signal1.setLatestPrice(new BigDecimal("2800.00"));
        signal1.setCompositeScore(6);
        signal1.setRecommendation("BUY");
        signal1.setAtr(new BigDecimal("50.00"));
        signal1.setTargetPrice(new BigDecimal("2950.00"));
        signal1.setStopLoss(new BigDecimal("2700.00"));
        signal1.setConfidenceScore(75L);
        signal1.setRsi14(new BigDecimal("45.00"));
        signal1.setVolumeConfirmed(true);

        signal2 = new SignalDTO();
        signal2.setStockId(101L);
        signal2.setSymbol("TCS");
        signal2.setLatestPrice(new BigDecimal("3800.00"));
        signal2.setCompositeScore(-2);
        signal2.setRecommendation("SELL");
        signal2.setAtr(new BigDecimal("60.00"));
        signal2.setTargetPrice(new BigDecimal("3620.00"));
        signal2.setStopLoss(new BigDecimal("3920.00"));
        signal2.setConfidenceScore(60L);
        signal2.setRsi14(new BigDecimal("72.00"));
        signal2.setVolumeConfirmed(false);
    }

    @Test
    void getOpportunities_emptyPortfolio_returnsEmptyResponse() {
        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(Collections.emptyList());

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        assertNotNull(response);
        assertNotNull(response.getStocks());
        assertTrue(response.getStocks().isEmpty());
        assertEquals(0, response.getSummary().getTotalStocks());
    }

    @Test
    void getOpportunities_singleStock_returnsCorrectDTO() {
        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1));
        when(signalService.computeSignal(100L)).thenReturn(signal1);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        assertNotNull(response);
        assertEquals(1, response.getStocks().size());

        WatchlistOpportunityDTO opp = response.getStocks().get(0);
        assertEquals(100L, opp.getStockId());
        assertEquals("RELIANCE", opp.getSymbol());
        assertEquals(new BigDecimal("2800.00"), opp.getCurrentPrice());
        assertEquals(1, opp.getRank());
        assertEquals("BUY", opp.getSignal());
        assertNotNull(opp.getEntryZone());
        assertEquals(new BigDecimal("2750.00"), opp.getEntryZone().getLow());
        assertEquals(new BigDecimal("2850.00"), opp.getEntryZone().getHigh());
        assertEquals(new BigDecimal("2700.00"), opp.getStopLoss());
        assertEquals(new BigDecimal("2950.00"), opp.getTarget());
    }

    @Test
    void getOpportunities_multipleStocks_sortedBySignalStrength() {
        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1, holding2));
        when(signalService.computeSignal(100L)).thenReturn(signal1);
        when(signalService.computeSignal(101L)).thenReturn(signal2);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        assertNotNull(response);
        assertEquals(2, response.getStocks().size());

        // BUY should come before SELL
        assertEquals("BUY", response.getStocks().get(0).getSignal());
        assertEquals("SELL", response.getStocks().get(1).getSignal());

        // Rank 1 should be BUY, Rank 2 should be SELL
        assertEquals(1, response.getStocks().get(0).getRank());
        assertEquals(2, response.getStocks().get(1).getRank());
    }

    @Test
    void getOpportunities_signalComputationFails_addsWarning() {
        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1));
        when(signalService.computeSignal(100L)).thenThrow(new RuntimeException("API error"));

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        assertNotNull(response);
        assertTrue(response.getStocks().isEmpty());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.getWarnings().get(0).contains("RELIANCE"));
    }

    @Test
    void getOpportunities_withNullPortfolio_usesDefault() {
        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(Collections.emptyList());

        watchlistOpportunityService.getOpportunities(null);

        verify(portfolioService).getDefaultPortfolio();
        verify(portfolioService).getHoldings(1L);
    }

    @Test
    void getOpportunities_withPortfolioId_usesProvided() {
        Portfolio customPortfolio = new Portfolio("Custom", "Test", false);
        customPortfolio.setId(2L);

        when(portfolioService.getHoldings(2L)).thenReturn(Collections.emptyList());

        watchlistOpportunityService.getOpportunities(2L);

        verify(portfolioService, never()).getDefaultPortfolio();
        verify(portfolioService).getHoldings(2L);
    }

    @Test
    void getOpportunities_riskReward_computedCorrectly() {
        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1));
        when(signalService.computeSignal(100L)).thenReturn(signal1);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        WatchlistOpportunityDTO opp = response.getStocks().get(0);
        // Risk = 2800 - 2700 = 100
        // Reward = 2950 - 2800 = 150
        // Risk/Reward = 150 / 100 = 1.50
        assertEquals(new BigDecimal("1.50"), opp.getRiskRewardRatio());
    }

    @Test
    void getOpportunities_riskReward_nullWhenStopLossHigherThanPrice() {
        SignalDTO badSignal = new SignalDTO();
        badSignal.setStockId(100L);
        badSignal.setSymbol("TEST");
        badSignal.setLatestPrice(new BigDecimal("100.00"));
        badSignal.setCompositeScore(0);
        badSignal.setRecommendation("HOLD");
        badSignal.setStopLoss(new BigDecimal("110.00")); // Stop higher than price
        badSignal.setTargetPrice(new BigDecimal("120.00"));

        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1));
        when(signalService.computeSignal(100L)).thenReturn(badSignal);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        WatchlistOpportunityDTO opp = response.getStocks().get(0);
        assertNull(opp.getRiskRewardRatio());
    }

    @Test
    void getOpportunities_summary_computedCorrectly() {
        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1, holding2));
        when(signalService.computeSignal(100L)).thenReturn(signal1);
        when(signalService.computeSignal(101L)).thenReturn(signal2);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        WatchlistSummaryDTO summary = response.getSummary();
        assertEquals(2, summary.getTotalStocks());
        assertEquals(1, summary.getBuyOpportunities());
        assertEquals(0, summary.getHoldOpportunities());
        assertEquals(1, summary.getSellOpportunities());
        assertEquals("RELIANCE", summary.getStrongestSignal());
        assertEquals("TCS", summary.getWeakestSignal());
    }

    @Test
    void getOpportunities_reasons_rsiOversold_addsReason() {
        SignalDTO oversoldSignal = new SignalDTO();
        oversoldSignal.setStockId(100L);
        oversoldSignal.setSymbol("TEST");
        oversoldSignal.setLatestPrice(new BigDecimal("100.00"));
        oversoldSignal.setCompositeScore(5);
        oversoldSignal.setRecommendation("BUY");
        oversoldSignal.setRsi14(new BigDecimal("25.00")); // Oversold

        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1));
        when(signalService.computeSignal(100L)).thenReturn(oversoldSignal);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        WatchlistOpportunityDTO opp = response.getStocks().get(0);
        assertFalse(opp.getReasons().isEmpty());
        assertTrue(opp.getReasons().stream().anyMatch(r -> r.getFactor().equals("RSI")));
    }

    @Test
    void getOpportunities_reasons_rsiOverbought_addsReason() {
        SignalDTO overboughtSignal = new SignalDTO();
        overboughtSignal.setStockId(100L);
        overboughtSignal.setSymbol("TEST");
        overboughtSignal.setLatestPrice(new BigDecimal("100.00"));
        overboughtSignal.setCompositeScore(-3);
        overboughtSignal.setRecommendation("SELL");
        overboughtSignal.setRsi14(new BigDecimal("75.00")); // Overbought

        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1));
        when(signalService.computeSignal(100L)).thenReturn(overboughtSignal);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        WatchlistOpportunityDTO opp = response.getStocks().get(0);
        assertFalse(opp.getReasons().isEmpty());
        assertTrue(opp.getReasons().stream().anyMatch(r -> r.getFactor().equals("RSI")));
    }

    @Test
    void getOpportunities_indicatorAgreement_computedCorrectly() {
        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1));
        when(signalService.computeSignal(100L)).thenReturn(signal1);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        WatchlistOpportunityDTO opp = response.getStocks().get(0);
        assertNotNull(opp.getIndicatorAgreement());
        assertTrue(opp.getIndicatorAgreement() >= 0 && opp.getIndicatorAgreement() <= 100);
    }

    @Test
    void getOpportunities_noSignalData_skipsStock() {
        SignalDTO emptySignal = new SignalDTO();
        emptySignal.setStockId(100L);
        emptySignal.setCompositeScore(0);
        emptySignal.setRecommendation(null);

        when(portfolioService.getDefaultPortfolio()).thenReturn(defaultPortfolio);
        when(portfolioService.getHoldings(1L)).thenReturn(List.of(holding1));
        when(signalService.computeSignal(100L)).thenReturn(emptySignal);

        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(null);

        assertTrue(response.getStocks().isEmpty());
        assertFalse(response.getWarnings().isEmpty());
    }
}
