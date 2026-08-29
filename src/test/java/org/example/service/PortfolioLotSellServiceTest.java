package org.example.service;

import org.example.dto.BuyLotDTO;
import org.example.dto.TransactionDTO;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.PortfolioTransaction;
import org.example.entity.Stock;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioRepository;
import org.example.repository.PortfolioTransactionRepository;
import org.example.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for lot-based sell matching: each SELL maps to exactly one BUY lot,
 * sell quantity must not exceed the lot's open quantity, and lot status is
 * derived from sold vs remaining quantity.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioLotSellServiceTest {

    @Mock private PortfolioTransactionRepository transactionRepository;
    @Mock private PortfolioService portfolioService;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private StockRepository stockRepository;
    @Mock private PortfolioHoldingRepository holdingRepository;
    @Mock private PortfolioSnapshotService snapshotService;
    @Mock private PortfolioPositionReplayer replayer;

    @InjectMocks
    private PortfolioTransactionService transactionService;

    private Portfolio portfolio;
    private Stock stock;
    private PortfolioHolding holding;
    private PortfolioTransaction buyLot;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio("Test", "desc", false);
        portfolio.setId(10L);
        stock = new Stock();
        stock.setId(1L);
        stock.setSymbol("TEST.NS");
        stock.setName("Test Stock");
        holding = new PortfolioHolding();
        holding.setId(100L);
        holding.setPortfolio(portfolio);
        holding.setStock(stock);
        holding.setQuantity(10);
        holding.setAvgPrice(new BigDecimal("100.00"));

        buyLot = new PortfolioTransaction();
        buyLot.setId(500L);
        buyLot.setPortfolio(portfolio);
        buyLot.setStock(stock);
        buyLot.setType(org.example.entity.TransactionType.BUY);
        buyLot.setQuantity(10);
        buyLot.setPrice(new BigDecimal("100.00"));
        buyLot.setTransactionDate(LocalDate.now().minusDays(5));
    }

    private void stubLotSell() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(buyLot));
        when(transactionRepository.save(any(PortfolioTransaction.class))).thenAnswer(inv -> {
            PortfolioTransaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(999L);
            return t;
        });
    }

    // ─── sell from lot: happy paths ──────────────────────────────────────

    @Test
    void recordSellAgainstLot_PartialSell_ComputesLotPnlAndReducesHolding() {
        stubLotSell();
        when(transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(500L))
                .thenReturn(List.of()); // nothing sold yet
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        when(replayer.replay(10L)).thenReturn(Map.of(1L,
                new org.example.service.PortfolioPositionReplayer.PositionState(new BigDecimal("6"), new BigDecimal("100.00"))));

        TransactionDTO dto = transactionService.recordSellAgainstLot(
                10L, 500L, 4, new BigDecimal("150.00"), new BigDecimal("10.00"), LocalDate.now(), "partial");

        assertEquals(org.example.entity.TransactionType.SELL, dto.getType());
        assertEquals(500L, dto.getLinkedBuyId());
        // Lot P/L = (150 - 100) * 4 - 10 = 190.00
        assertEquals(new BigDecimal("190.00"), dto.getRealizedPnl());
        // Holding qty 10 -> 6, avg retained
        verify(portfolioService).updateHolding(10L, 100L, 6, new BigDecimal("100.00"));
    }

    @Test
    void recordSellAgainstLot_FullSell_RemovesHolding() {
        stubLotSell();
        when(transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(500L))
                .thenReturn(List.of());
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        when(replayer.replay(10L)).thenReturn(Map.of());
        when(holdingRepository.findByPortfolioId(10L)).thenReturn(List.of(holding));

        transactionService.recordSellAgainstLot(
                10L, 500L, 10, new BigDecimal("120.00"), BigDecimal.ZERO, LocalDate.now(), null);

        verify(portfolioService).removeHolding(10L, 100L);
    }

    // ─── over-sell rejection ─────────────────────────────────────────────

    @Test
    void recordSellAgainstLot_ExceedsOpenQuantity_Throws() {
        stubLotSell();
        // 6 already sold from the lot -> only 4 open
        PortfolioTransaction priorSell = new PortfolioTransaction();
        priorSell.setId(600L);
        priorSell.setType(org.example.entity.TransactionType.SELL);
        priorSell.setQuantity(6);
        when(transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(500L))
                .thenReturn(List.of(priorSell));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.recordSellAgainstLot(
                        10L, 500L, 5, new BigDecimal("150.00"), BigDecimal.ZERO, LocalDate.now(), null));
        assertTrue(ex.getMessage().contains("only 4"));
        verify(transactionRepository, never()).save(any(PortfolioTransaction.class));
    }

    @Test
    void recordSellAgainstLot_NonBuyTarget_Throws() {
        stubLotSell();
        buyLot.setType(org.example.entity.TransactionType.SELL);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.recordSellAgainstLot(
                        10L, 500L, 1, new BigDecimal("150.00"), BigDecimal.ZERO, LocalDate.now(), null));
    }

    @Test
    void recordSellAgainstLot_BuyFromOtherPortfolio_Throws() {
        stubLotSell();
        Portfolio other = new Portfolio("Other", "d", false);
        other.setId(20L);
        buyLot.setPortfolio(other);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.recordSellAgainstLot(
                        10L, 500L, 1, new BigDecimal("150.00"), BigDecimal.ZERO, LocalDate.now(), null));
    }

    @Test
    void recordSellAgainstLot_MissingBuy_ThrowsNotFound() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findById(500L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> transactionService.recordSellAgainstLot(
                        10L, 500L, 1, new BigDecimal("150.00"), BigDecimal.ZERO, LocalDate.now(), null));
    }

    // ─── lots listing & status ───────────────────────────────────────────

    @Test
    void getLots_StatusDerivedFromSoldQuantity() {
        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(transactionRepository.findByPortfolioIdAndTypeOrderByTransactionDateAscIdAsc(10L,
                org.example.entity.TransactionType.BUY)).thenReturn(List.of(buyLot));

        PortfolioTransaction partialSell = new PortfolioTransaction();
        partialSell.setId(600L);
        partialSell.setPortfolio(portfolio);
        partialSell.setStock(stock);
        partialSell.setType(org.example.entity.TransactionType.SELL);
        partialSell.setQuantity(3);
        partialSell.setPrice(new BigDecimal("140.00"));
        partialSell.setRealizedPnl(new BigDecimal("120.00"));
        when(transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(500L))
                .thenReturn(List.of(partialSell));

        List<BuyLotDTO> lots = transactionService.getLots(10L, null);

        assertEquals(1, lots.size());
        BuyLotDTO lot = lots.get(0);
        assertEquals(3, lot.getSoldQuantity());
        assertEquals(7, lot.getRemainingQuantity());
        assertEquals("PARTIALLY_SOLD", lot.getStatus());
        assertEquals(new BigDecimal("120.00"), lot.getRealizedPnl());
        assertEquals(1, lot.getSells().size());
    }

    @Test
    void getLots_FullySoldLot_StatusSold() {
        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(transactionRepository.findByPortfolioIdAndTypeOrderByTransactionDateAscIdAsc(10L,
                org.example.entity.TransactionType.BUY)).thenReturn(List.of(buyLot));

        PortfolioTransaction fullSell = new PortfolioTransaction();
        fullSell.setId(600L);
        fullSell.setPortfolio(portfolio);
        fullSell.setStock(stock);
        fullSell.setType(org.example.entity.TransactionType.SELL);
        fullSell.setQuantity(10);
        fullSell.setPrice(new BigDecimal("130.00"));
        fullSell.setRealizedPnl(new BigDecimal("300.00"));
        when(transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(500L))
                .thenReturn(List.of(fullSell));

        BuyLotDTO lot = transactionService.getLots(10L, null).get(0);
        assertEquals(0, lot.getRemainingQuantity());
        assertEquals("SOLD", lot.getStatus());
    }

    @Test
    void getLots_UnsoldLot_StatusOpen() {
        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(transactionRepository.findByPortfolioIdAndTypeOrderByTransactionDateAscIdAsc(10L,
                org.example.entity.TransactionType.BUY)).thenReturn(List.of(buyLot));
        when(transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(500L))
                .thenReturn(List.of());

        BuyLotDTO lot = transactionService.getLots(10L, null).get(0);
        assertEquals(10, lot.getRemainingQuantity());
        assertEquals("OPEN", lot.getStatus());
    }

    // ─── delete guard ────────────────────────────────────────────────────

    @Test
    void deleteTransaction_BuyWithLinkedSells_Throws() {
        PortfolioTransaction linkedSell = new PortfolioTransaction();
        linkedSell.setId(600L);
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(buyLot));
        when(transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(500L))
                .thenReturn(List.of(linkedSell));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.deleteTransaction(500L));
        assertTrue(ex.getMessage().contains("linked sell"));
        verify(transactionRepository, never()).delete(any(PortfolioTransaction.class));
    }

    @Test
    void deleteTransaction_BuyWithoutLinkedSells_Deletes() {
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(buyLot));
        when(transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(500L))
                .thenReturn(List.of());
        when(transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(10L, 1L))
                .thenReturn(List.of());
        when(replayer.replay(10L)).thenReturn(Map.of());

        transactionService.deleteTransaction(500L);

        verify(transactionRepository).delete(buyLot);
    }
}
