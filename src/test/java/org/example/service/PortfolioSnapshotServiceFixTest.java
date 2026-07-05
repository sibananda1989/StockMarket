package org.example.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.entity.DailyPrice;
import org.example.entity.PortfolioSnapshot;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioSnapshotRepository;
import org.example.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test to verify the fix for identical portfolio values from April 1 - May 12
 * Tests that saveOrUpdate calculates historical values correctly instead of using current values
 */
class PortfolioSnapshotServiceFixTest {

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private PortfolioHoldingRepository holdingRepository;

    @InjectMocks
    private PortfolioSnapshotService portfolioSnapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(holdingRepository.findByStockId(anyLong())).thenReturn(List.of());
    }

    @Test
    void testSaveOrUpdate_CalculatesHistoricalValuesCorrectly() {
        // Arrange
        Stock stock = new Stock();
        stock.setId(1L);
        stock.setSymbol("TEST");
        stock.setQuantity(100); // 100 shares
        stock.setAvgPrice(new BigDecimal("50.00"));
        stock.setLastTradedPrice(new BigDecimal("60.00"));
        stock.setInvestment(new BigDecimal("5000")); // Current investment: 100 * 50
        stock.setCurrentValue(new BigDecimal("6000")); // Current value: 100 * 60

        LocalDate historicalDate = LocalDate.of(2024, 4, 15);
        LocalDate priceDate = LocalDate.of(2024, 4, 10);

        // Existing snapshot (to test update path)
        PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
        existingSnapshot.setId(1L);
        existingSnapshot.setStock(stock);
        existingSnapshot.setSnapshotDate(historicalDate);

        when(snapshotRepository.findByStockIdAndSnapshotDate(stock.getId(), historicalDate))
            .thenReturn(Optional.of(existingSnapshot));

        // Mock daily price repository to return historical price
        // getClosingPriceForDate() first queries with the SNAPSHOT date (April 15)
        DailyPrice historicalPrice = new DailyPrice();
        historicalPrice.setStock(stock);
        historicalPrice.setPriceDate(historicalDate);
        historicalPrice.setClosingPrice(new BigDecimal("70.00")); // Price on April 15

        when(dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), historicalDate))
            .thenReturn(Optional.of(historicalPrice));

        // Act
        List<PortfolioSnapshot> results = portfolioSnapshotService.saveOrUpdate(stock, historicalDate);

        // Assert
        assertFalse(results.isEmpty(), "Result list should not be empty");
        PortfolioSnapshot result = results.get(0);
        assertEquals(stock.getId(), result.getStock().getId());
        assertEquals(historicalDate, result.getSnapshotDate());
        assertEquals(stock.getQuantity(), result.getQuantity()); // 100 shares
        assertEquals(stock.getAvgPrice(), result.getAvgPrice()); // 50.00 avg price
        assertEquals(stock.getLastTradedPrice(), result.getLastTradedPrice()); // 60.00 current price

        // THE KEY ASSERTIONS - Verify historical values are calculated correctly:
        // Investment = avgPrice * quantity = 50.00 * 100 = 5000 (cost basis)
        assertEquals(new BigDecimal("5000.00"), result.getInvestment(),
                "Investment should use avgPrice (cost basis)");

        // Current value = historicalPrice * quantity = 70.00 * 100 = 7000
        assertEquals(new BigDecimal("7000.00"), result.getCurrentValue(),
                "Current value should use historical closing price");

        // P&L = currentValue - investment = 7000 - 5000 = 2000
        assertEquals(new BigDecimal("2000.00"), result.getPnl(),
                "P&L should be currentValue - investment");

        // P&L percent = (2000 / 5000) * 100 = 40%
        assertEquals(new BigDecimal("40.00"), result.getPnlPercent(),
                "P&L percent should be (pnl / investment) * 100");
    }

    @Test
    void testSaveOrUpdate_UsesCurrentValues_WhenNoHistoricalPrice() {
        // Arrange
        Stock stock = new Stock();
        stock.setId(1L);
        stock.setSymbol("TEST");
        stock.setQuantity(50);
        stock.setAvgPrice(new BigDecimal("40.00"));
        stock.setLastTradedPrice(new BigDecimal("45.00"));
        stock.setInvestment(new BigDecimal("2000")); // 50 * 40
        stock.setCurrentValue(new BigDecimal("2250")); // 50 * 45

        LocalDate historicalDate = LocalDate.of(2024, 4, 15);

        // Existing snapshot
        PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
        existingSnapshot.setId(1L);
        existingSnapshot.setStock(stock);
        existingSnapshot.setSnapshotDate(historicalDate);

        when(snapshotRepository.findByStockIdAndSnapshotDate(stock.getId(), historicalDate))
            .thenReturn(Optional.of(existingSnapshot));

        // Mock daily price repository to return NO historical prices
        when(dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), historicalDate))
            .thenReturn(Optional.empty());

        when(dailyPriceRepository.findByStockIdAndPriceDateBetweenOrderByPriceDateDesc(
                eq(stock.getId()), any(LocalDate.class), eq(historicalDate)))
            .thenReturn(java.util.Collections.emptyList());

        // Act
        List<PortfolioSnapshot> results = portfolioSnapshotService.saveOrUpdate(stock, historicalDate);

        // Assert - Should fall back to current investment/currentValue
        assertFalse(results.isEmpty(), "Result list should not be empty");
        PortfolioSnapshot result = results.get(0);
        assertEquals(0, stock.getInvestment().compareTo(result.getInvestment()),
                "Should use current investment when no historical data");
        assertEquals(0, stock.getCurrentValue().compareTo(result.getCurrentValue()),
                "Should use current currentValue when no historical data");
    }

    @Test
    void testSaveOrUpdate_HandlesZeroQuantity() {
        // Arrange
        Stock stock = new Stock();
        stock.setId(1L);
        stock.setSymbol("TEST");
        stock.setQuantity(0); // Zero quantity
        stock.setAvgPrice(new BigDecimal("100.00"));
        stock.setLastTradedPrice(new BigDecimal("110.00"));
        stock.setInvestment(BigDecimal.ZERO); // 0 * 100 = 0
        stock.setCurrentValue(BigDecimal.ZERO); // 0 * 110 = 0

        LocalDate historicalDate = LocalDate.of(2024, 4, 15);
        LocalDate priceDate = LocalDate.of(2024, 4, 10);

        // Existing snapshot
        PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
        existingSnapshot.setId(1L);
        existingSnapshot.setStock(stock);
        existingSnapshot.setSnapshotDate(historicalDate);

        when(snapshotRepository.findByStockIdAndSnapshotDate(stock.getId(), historicalDate))
            .thenReturn(Optional.of(existingSnapshot));

        // Mock daily price
        DailyPrice historicalPrice = new DailyPrice();
        historicalPrice.setStock(stock);
        historicalPrice.setPriceDate(priceDate);
        historicalPrice.setClosingPrice(new BigDecimal("120.00"));

        when(dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), priceDate))
            .thenReturn(Optional.of(historicalPrice));

        // Act
        List<PortfolioSnapshot> results = portfolioSnapshotService.saveOrUpdate(stock, historicalDate);

        // Assert - Should be zero since quantity is zero
        assertFalse(results.isEmpty(), "Result list should not be empty");
        PortfolioSnapshot result = results.get(0);
        assertEquals(BigDecimal.ZERO, result.getInvestment());
        assertEquals(BigDecimal.ZERO, result.getCurrentValue());
        assertEquals(BigDecimal.ZERO, result.getPnl());
        assertEquals(BigDecimal.ZERO, result.getPnlPercent());
    }
}