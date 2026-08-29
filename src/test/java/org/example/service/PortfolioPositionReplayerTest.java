package org.example.service;

import org.example.entity.Portfolio;
import org.example.entity.PortfolioTransaction;
import org.example.entity.Stock;
import org.example.entity.TransactionType;
import org.example.repository.PortfolioTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioPositionReplayerTest {

    @Mock
    private PortfolioTransactionRepository transactionRepository;

    @InjectMocks
    private PortfolioPositionReplayer replayer;

    private Portfolio portfolio;
    private Stock stock;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio("Test", "desc", false);
        portfolio.setId(10L);
        stock = new Stock();
        stock.setId(1L);
        stock.setSymbol("TEST.NS");
        stock.setName("Test Stock");
    }

    // ─── applyBuy: single lot ─────────────────────────────────────────────

    @Test
    void applyBuy_FirstBuy_SetsAvgWithPerShareFee() {
        var result = replayer.applyBuy(
                PortfolioPositionReplayer.PositionState.EMPTY,
                10, new BigDecimal("200.00"), new BigDecimal("20.00"));

        assertEquals(BigDecimal.TEN, result.quantity());
        // avg = 200 + 20/10 = 202.00
        assertEquals(new BigDecimal("202.00"), result.avgCost());
    }

    @Test
    void applyBuy_FirstBuy_NoFees_CorrectAvg() {
        var result = replayer.applyBuy(
                PortfolioPositionReplayer.PositionState.EMPTY,
                10, new BigDecimal("200.00"), BigDecimal.ZERO);

        assertEquals(BigDecimal.TEN, result.quantity());
        assertEquals(new BigDecimal("200.00"), result.avgCost());
    }

    // ─── applyBuy: blending ───────────────────────────────────────────────

    @Test
    void applyBuy_SecondBuy_BlendsAvgCostCorrectly() {
        // First buy: 10 @ 100, no fees → avg = 100
        var state1 = replayer.applyBuy(
                PortfolioPositionReplayer.PositionState.EMPTY,
                10, new BigDecimal("100.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("100.00"), state1.avgCost());

        // Second buy: 10 @ 200, fees 20 → blended avg = (10*100 + 10*200 + 20) / 20 = 3020/20 = 151.00
        var state2 = replayer.applyBuy(
                state1, 10, new BigDecimal("200.00"), new BigDecimal("20.00"));

        assertEquals(new BigDecimal("20"), state2.quantity());
        assertEquals(new BigDecimal("151.00"), state2.avgCost());
    }

    @Test
    void applyBuy_MultiLotBlend_PreciseAvg() {
        // Buy 5 @ 100, no fees
        var state = replayer.applyBuy(
                PortfolioPositionReplayer.PositionState.EMPTY,
                5, new BigDecimal("100.00"), BigDecimal.ZERO);

        // Buy 3 @ 120, fees 6
        state = replayer.applyBuy(state, 3, new BigDecimal("120.00"), new BigDecimal("6.00"));

        // Expected: (5*100 + 3*120 + 6) / 8 = (500 + 360 + 6) / 8 = 866/8 = 108.25
        assertEquals(new BigDecimal("8"), state.quantity());
        assertEquals(new BigDecimal("108.25"), state.avgCost());
    }

    // ─── applySell ────────────────────────────────────────────────────────

    @Test
    void applySell_FullSell_ReturnsEmptyStateAndPnl() {
        var state = replayer.applyBuy(
                PortfolioPositionReplayer.PositionState.EMPTY,
                10, new BigDecimal("100.00"), BigDecimal.ZERO);

        var result = replayer.applySell(state, 10, new BigDecimal("150.00"), BigDecimal.ZERO);

        assertTrue(result.state().quantity().equals(BigDecimal.ZERO));
        assertEquals(new BigDecimal("500.00"), result.realizedPnl());
        assertFalse(result.discrepancyDetected());
    }

    @Test
    void applySell_PartialSell_RetainsAvgCost() {
        var state = replayer.applyBuy(
                PortfolioPositionReplayer.PositionState.EMPTY,
                10, new BigDecimal("100.00"), BigDecimal.ZERO);

        var result = replayer.applySell(state, 4, new BigDecimal("150.00"), new BigDecimal("10.00"));

        assertEquals(new BigDecimal("6"), result.state().quantity());
        assertEquals(new BigDecimal("100.00"), result.state().avgCost());
        // PnL = (150 - 100)*4 - 10 = 190
        assertEquals(new BigDecimal("190.00"), result.realizedPnl());
    }

    @Test
    void applySell_Oversell_ClampsToRemainingQty() {
        var state = replayer.applyBuy(
                PortfolioPositionReplayer.PositionState.EMPTY,
                10, new BigDecimal("100.00"), BigDecimal.ZERO);

        var result = replayer.applySell(state, 15, new BigDecimal("150.00"), BigDecimal.ZERO);

        // Clamped to 10 sold; position fully closed
        assertTrue(result.state().quantity().equals(BigDecimal.ZERO));
        assertEquals(new BigDecimal("500.00"), result.realizedPnl());
        assertTrue(result.discrepancyDetected());
    }

    @Test
    void applySell_SellFromZeroState_ReturnsEmptyStateNoPnl() {
        var result = replayer.applySell(
                PortfolioPositionReplayer.PositionState.EMPTY,
                5, new BigDecimal("150.00"), BigDecimal.ZERO);

        assertTrue(result.state().quantity().equals(BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, result.realizedPnl());
    }

    // ─── replay: full portfolio ───────────────────────────────────────────

    @Test
    void replay_SingleStock_BuyThenSell_RemovesHolding() {
        PortfolioTransaction buy = makeTxn(1L, TransactionType.BUY, 10, new BigDecimal("100.00"), BigDecimal.ZERO);
        PortfolioTransaction sell = makeTxn(2L, TransactionType.SELL, 10, new BigDecimal("150.00"), BigDecimal.ZERO);
        when(transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(10L))
                .thenReturn(List.of(buy, sell));

        Map<Long, PortfolioPositionReplayer.PositionState> result = replayer.replay(10L);

        assertTrue(result.isEmpty(), "Full sell should remove the holding");
    }

    @Test
    void replay_MultiLotBuyThenPartialSell_ComputesBlendedAvg() {
        PortfolioTransaction buy1 = makeTxn(1L, TransactionType.BUY, 10, new BigDecimal("100.00"), BigDecimal.ZERO);
        PortfolioTransaction buy2 = makeTxn(2L, TransactionType.BUY, 10, new BigDecimal("200.00"), new BigDecimal("20.00"));
        PortfolioTransaction sell = makeTxn(3L, TransactionType.SELL, 5, new BigDecimal("150.00"), BigDecimal.ZERO);
        when(transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(10L))
                .thenReturn(List.of(buy1, buy2, sell));

        Map<Long, PortfolioPositionReplayer.PositionState> result = replayer.replay(10L);

        assertEquals(1, result.size());
        PortfolioPositionReplayer.PositionState state = result.get(1L);
        assertEquals(new BigDecimal("15"), state.quantity());
        // Avg = (10*100 + 10*200 + 20) / 20 = 3020/20 = 151.00; partial sell retains avg
        assertEquals(new BigDecimal("151.00"), state.avgCost());
    }

    @Test
    void replay_MultipleStocks_IndependentState() {
        Stock stock2 = new Stock();
        stock2.setId(2L);
        stock2.setSymbol("TEST2.NS");
        stock2.setName("Test Stock 2");

        PortfolioTransaction buy1 = makeTxn(1L, TransactionType.BUY, 10, new BigDecimal("100.00"), BigDecimal.ZERO);
        buy1.setStock(stock);
        PortfolioTransaction buy2 = makeTxn(2L, TransactionType.BUY, 5, new BigDecimal("200.00"), BigDecimal.ZERO);
        buy2.setStock(stock2);
        when(transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(10L))
                .thenReturn(List.of(buy1, buy2));

        Map<Long, PortfolioPositionReplayer.PositionState> result = replayer.replay(10L);

        assertEquals(2, result.size());
        assertEquals(new BigDecimal("10"), result.get(stock.getId()).quantity());
        assertEquals(new BigDecimal("5"), result.get(stock2.getId()).quantity());
    }

    // ─── historicalStateAt ────────────────────────────────────────────────

    @Test
    void historicalStateAt_DeterministicAcrossRuns() {
        PortfolioTransaction buy = makeTxn(1L, TransactionType.BUY, 10, new BigDecimal("100.00"), BigDecimal.ZERO);
        buy.setCreatedAt(java.time.LocalDateTime.of(2024, 1, 1, 10, 0));
        PortfolioTransaction sell = makeTxn(2L, TransactionType.SELL, 4, new BigDecimal("150.00"), BigDecimal.ZERO);
        sell.setCreatedAt(java.time.LocalDateTime.of(2024, 1, 2, 10, 0));
        when(transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(10L, 1L))
                .thenReturn(List.of(buy, sell));

        var state1 = replayer.historicalStateAt(10L, 1L, java.time.LocalDateTime.of(2024, 1, 1, 10, 0));
        var state2 = replayer.historicalStateAt(10L, 1L, java.time.LocalDateTime.of(2024, 1, 1, 10, 0));

        assertNotNull(state1);
        assertNotNull(state2);
        assertEquals(state1, state2);
    }

    @Test
    void historicalStateAt_FilteredByInstant() {
        PortfolioTransaction buy = makeTxn(1L, TransactionType.BUY, 10, new BigDecimal("100.00"), BigDecimal.ZERO);
        buy.setCreatedAt(java.time.LocalDateTime.of(2024, 1, 1, 10, 0));
        PortfolioTransaction sell = makeTxn(2L, TransactionType.SELL, 4, new BigDecimal("150.00"), BigDecimal.ZERO);
        sell.setCreatedAt(java.time.LocalDateTime.of(2024, 1, 2, 10, 0));
        when(transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(10L, 1L))
                .thenReturn(List.of(buy, sell));

        // Before sell: should show 10 qty
        var beforeSell = replayer.historicalStateAt(10L, 1L,
                java.time.LocalDateTime.of(2024, 1, 1, 23, 59, 59));
        assertEquals(new BigDecimal("10"), beforeSell.quantity());

        // After sell: should show 6 qty
        var afterSell = replayer.historicalStateAt(10L, 1L,
                java.time.LocalDateTime.of(2024, 1, 3, 0, 0, 0));
        assertEquals(new BigDecimal("6"), afterSell.quantity());
    }

    @Test
    void historicalStateAt_NoPosition_ReturnsNull() {
        when(transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(10L, 1L))
                .thenReturn(List.of());

        assertNull(replayer.historicalStateAt(10L, 1L, java.time.LocalDateTime.now()));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private PortfolioTransaction makeTxn(long id, TransactionType type, int qty,
                                          BigDecimal price, BigDecimal fees) {
        PortfolioTransaction txn = new PortfolioTransaction();
        txn.setId(id);
        txn.setType(type);
        txn.setQuantity(qty);
        txn.setPrice(price);
        txn.setFees(fees);
        txn.setPortfolio(portfolio);
        txn.setStock(stock);
        txn.setTransactionDate(LocalDate.of(2024, 1, 1));
        return txn;
    }
}
