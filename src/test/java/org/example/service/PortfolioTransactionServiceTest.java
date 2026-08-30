package org.example.service;

import org.example.dto.TransactionDTO;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.PortfolioTransaction;
import org.example.entity.Stock;
import org.example.entity.TransactionType;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioTransactionServiceTest {

    @Mock private PortfolioTransactionRepository transactionRepository;
    @Mock private PortfolioService portfolioService;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private StockRepository stockRepository;
    @Mock private PortfolioHoldingRepository holdingRepository;
    @Mock private PortfolioSnapshotService snapshotService;
    @Mock private PortfolioPositionReplayer replayer;
    @Mock private PortfolioSnapshotWriter snapshotWriter;
    @Mock private PortfolioDailyValueService dailyValueService;

    @InjectMocks
    private PortfolioTransactionService transactionService;

    private Portfolio portfolio;
    private Stock stock;
    private PortfolioHolding holding;

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
    }

    private void stubPortfolioAndStock() {
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(transactionRepository.save(any(PortfolioTransaction.class))).thenAnswer(inv -> {
            PortfolioTransaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(999L);
            return t;
        });
    }

    // ─── avg-cost blend precision ─────────────────────────────────────────

    @Test
    void recordBuy_ExistingHolding_BlendsAvgCost() {
        stubPortfolioAndStock();
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        // After buy: qty=20, avg=151.00
        when(replayer.replay(10L)).thenReturn(Map.of(1L,
                new PortfolioPositionReplayer.PositionState(new BigDecimal("20"), new BigDecimal("151.00"))));

        transactionService.recordBuy(10L, 1L, 10, new BigDecimal("200.00"), new BigDecimal("20.00"), LocalDate.now(), null);

        // newQty = 20; newAvg = (100*10 + 200*10 + 20)/20 = 3020/20 = 151.00
        verify(portfolioService).updateHolding(10L, 100L, 20, new BigDecimal("151.00"));
    }

    @Test
    void recordBuy_NoExistingHolding_AddHoldingWithPerShareFee() {
        stubPortfolioAndStock();
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.empty());
        // After buy: qty=10, avg=202.00
        when(replayer.replay(10L)).thenReturn(Map.of(1L,
                new PortfolioPositionReplayer.PositionState(new BigDecimal("10"), new BigDecimal("202.00"))));

        transactionService.recordBuy(10L, 1L, 10, new BigDecimal("200.00"), new BigDecimal("20.00"), LocalDate.now(), null);

        // effectiveAvg = 200 + 20/10 = 202.00
        verify(portfolioService).addHolding(10L, 1L, 10, new BigDecimal("202.00"));
    }

    // ─── over-sell rejection ──────────────────────────────────────────────

    @Test
    void recordSell_OverSell_ThrowsIllegalArgumentException() {
        stubPortfolioAndStock();
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.recordSell(10L, 1L, 11, new BigDecimal("150.00"), BigDecimal.ZERO, LocalDate.now(), null));
        verify(portfolioService, never()).updateHolding(anyLong(), anyLong(), anyInt(), any());
        verify(portfolioService, never()).removeHolding(anyLong(), anyLong());
    }

    @Test
    void recordSell_NoHolding_ThrowsResourceNotFound() {
        stubPortfolioAndStock();
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> transactionService.recordSell(10L, 1L, 5, new BigDecimal("150.00"), BigDecimal.ZERO, LocalDate.now(), null));
    }

    // ─── full sell deletes holding + snapshot ─────────────────────────────

    @Test
    void recordSell_FullSell_RemovesHolding() {
        stubPortfolioAndStock();
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        // After full sell: replay returns empty (qty=0 for stock 1)
        when(replayer.replay(10L)).thenReturn(Map.of());
        when(holdingRepository.findByPortfolioId(10L)).thenReturn(List.of(holding));

        transactionService.recordSell(10L, 1L, 10, new BigDecimal("150.00"), new BigDecimal("10.00"), LocalDate.now(), null);

        // realizedPnl = (150 - 100)*10 - 10 = 490.00
        verify(portfolioService).removeHolding(10L, 100L);
        verify(portfolioService, never()).updateHolding(anyLong(), anyLong(), anyInt(), any());
    }

    // ─── partial sell retains avg + captures realizedPnl ──────────────────

    @Test
    void recordSell_PartialSell_RetainsAvgAndCapturesRealizedPnl() {
        stubPortfolioAndStock();
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        // After partial sell: qty=6, avg=100.00
        when(replayer.replay(10L)).thenReturn(Map.of(1L,
                new PortfolioPositionReplayer.PositionState(new BigDecimal("6"), new BigDecimal("100.00"))));

        TransactionDTO dto = transactionService.recordSell(10L, 1L, 4, new BigDecimal("150.00"), new BigDecimal("10.00"), LocalDate.now(), null);

        // newQty = 6, avg retained at 100.00
        verify(portfolioService).updateHolding(10L, 100L, 6, new BigDecimal("100.00"));
        assertNotNull(dto.getRealizedPnl());
        assertEquals(0, new BigDecimal("190.00").compareTo(dto.getRealizedPnl()));
        assertEquals(TransactionType.SELL, dto.getType());
    }

    // ─── deleteTransaction recompute from remaining ledger ────────────────

    @Test
    void deleteTransaction_RecomputesHoldingFromRemainingLedger() {
        stubPortfolioAndStock();
        PortfolioTransaction toDelete = new PortfolioTransaction();
        toDelete.setId(5L);
        toDelete.setPortfolio(portfolio);
        toDelete.setStock(stock);
        toDelete.setType(TransactionType.BUY);
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(toDelete));

        // Remaining ledger: BUY 10 @100, SELL 4 @150
        PortfolioTransaction buy = new PortfolioTransaction();
        buy.setId(1L); buy.setType(TransactionType.BUY); buy.setQuantity(10);
        buy.setPrice(new BigDecimal("100.00")); buy.setFees(BigDecimal.ZERO);
        PortfolioTransaction sell = new PortfolioTransaction();
        sell.setId(2L); sell.setType(TransactionType.SELL); sell.setQuantity(4);
        sell.setPrice(new BigDecimal("150.00")); sell.setFees(BigDecimal.ZERO);
        when(transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(10L, 1L))
                .thenReturn(List.of(buy, sell));
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        // After delete: replay returns qty=6, avg=100.00
        when(replayer.replay(10L)).thenReturn(Map.of(1L,
                new PortfolioPositionReplayer.PositionState(new BigDecimal("6"), new BigDecimal("100.00"))));

        transactionService.deleteTransaction(5L);

        // netQty = 6, avg = (10*100)/(10) = 100.00 after proportional reduction
        verify(transactionRepository).delete(toDelete);
        verify(portfolioService).updateHolding(10L, 100L, 6, new BigDecimal("100.00"));
    }

    @Test
    void deleteTransaction_OnlyBuyRemoved_ResetsStockToZero() {
        stubPortfolioAndStock();
        PortfolioTransaction toDelete = new PortfolioTransaction();
        toDelete.setId(5L); toDelete.setPortfolio(portfolio); toDelete.setStock(stock);
        toDelete.setType(TransactionType.BUY);
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(toDelete));
        when(transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(10L, 1L))
                .thenReturn(List.of());
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        when(holdingRepository.findByStockId(1L)).thenReturn(List.of());
        // After delete: replay returns empty (holding removed)
        when(replayer.replay(10L)).thenReturn(Map.of());
        when(holdingRepository.findByPortfolioId(10L)).thenReturn(List.of(holding));
        transactionService.deleteTransaction(5L);
        verify(transactionRepository).delete(toDelete);
        verify(portfolioService).removeHolding(10L, 100L);
        verify(stockRepository).findById(1L);
        verify(stockRepository).save(argThat(st -> st.getQuantity() == 0 && st.getAvgPrice() == null));
    }

    @Test
    void deleteTransaction_DeleteSell_RestoresQtyViaReplay() {
        stubPortfolioAndStock();
        PortfolioTransaction toDelete = new PortfolioTransaction();
        toDelete.setId(3L); toDelete.setPortfolio(portfolio); toDelete.setStock(stock);
        toDelete.setType(TransactionType.SELL);
        when(transactionRepository.findById(3L)).thenReturn(Optional.of(toDelete));
        PortfolioTransaction buy1 = new PortfolioTransaction();
        buy1.setId(1L); buy1.setType(TransactionType.BUY); buy1.setQuantity(10);
        buy1.setPrice(new BigDecimal("100.00")); buy1.setFees(BigDecimal.ZERO);
        PortfolioTransaction buy2 = new PortfolioTransaction();
        buy2.setId(2L); buy2.setType(TransactionType.BUY); buy2.setQuantity(10);
        buy2.setPrice(new BigDecimal("200.00")); buy2.setFees(new BigDecimal("20.00"));
        PortfolioTransaction sell = new PortfolioTransaction();
        sell.setId(4L); sell.setType(TransactionType.SELL); sell.setQuantity(4);
        sell.setPrice(new BigDecimal("150.00")); sell.setFees(BigDecimal.ZERO);
        when(transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(10L, 1L))
                .thenReturn(List.of(buy1, buy2, sell));
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        // After delete sell: replay returns qty=16, avg=151.00
        when(replayer.replay(10L)).thenReturn(Map.of(1L,
                new PortfolioPositionReplayer.PositionState(new BigDecimal("16"), new BigDecimal("151.00"))));
        transactionService.deleteTransaction(3L);
        // Remaining ledger (deleted sell removed): qty 10+10-4 = 16, avg (1000+2000+20)/20 = 151.00
        verify(portfolioService).updateHolding(10L, 100L, 16, new BigDecimal("151.00"));
    }

    @Test
    void recordBuy_CsvLoadedHoldingNullAvgPrice_UsesTxnPriceAsCostBasis() {
        stubPortfolioAndStock();
        PortfolioHolding csvHolding = new PortfolioHolding();
        csvHolding.setId(200L); csvHolding.setPortfolio(portfolio); csvHolding.setStock(stock);
        csvHolding.setQuantity(0); csvHolding.setAvgPrice(null);
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(csvHolding));
        // After buy: replay returns qty=10, avg=202.00
        when(replayer.replay(10L)).thenReturn(Map.of(1L,
                new PortfolioPositionReplayer.PositionState(new BigDecimal("10"), new BigDecimal("202.00"))));
        transactionService.recordBuy(10L, 1L, 10, new BigDecimal("200.00"), new BigDecimal("20.00"), LocalDate.now(), null);
        // New implementation uses replayer to derive state - finds existing csvHolding and updates it
        verify(portfolioService).updateHolding(10L, 200L, 10, new BigDecimal("202.00"));
        verify(portfolioService, never()).addHolding(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void recordBuy_AlreadyHasHolding_BlendsAvgCost() {
        stubPortfolioAndStock();
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        // After buy: qty=15, avg=107.33
        when(replayer.replay(10L)).thenReturn(Map.of(1L,
                new PortfolioPositionReplayer.PositionState(new BigDecimal("15"), new BigDecimal("107.33"))));

        TransactionDTO dto = transactionService.recordBuy(10L, 1L, 5,
                new BigDecimal("120.00"), new BigDecimal("10.00"), LocalDate.now(), null);

        verify(portfolioService).updateHolding(10L, 100L, 15, new BigDecimal("107.33"));
    }
}
