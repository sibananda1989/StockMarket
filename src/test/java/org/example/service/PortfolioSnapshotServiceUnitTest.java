package org.example.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for the fix to PortfolioSnapshotService.saveOrUpdate()
 * Verifies that historical portfolio values are calculated correctly
 * instead of incorrectly using current investment/currentValue fields.
 */
class PortfolioSnapshotServiceUnitTest {

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

    @Nested
    @DisplayName("saveOrUpdate method tests")
    class SaveOrUpdateTests {

        @Test
        @DisplayName("Should calculate historical investment correctly when price data exists")
        void shouldCalculateHistoricalInvestmentCorrectly_whenPriceDataExists() {
            // Arrange
            Stock stock = new Stock();
            stock.setId(1L);
            stock.setSymbol("AAPL");
            stock.setQuantity(100); // 100 shares
            stock.setAvgPrice(new BigDecimal("150.00"));
            stock.setLastTradedPrice(new BigDecimal("170.00"));
            stock.setInvestment(new BigDecimal("15000")); // Current: 100 * 150
            stock.setCurrentValue(new BigDecimal("17000")); // Current: 100 * 170

            LocalDate snapshotDate = LocalDate.of(2024, 4, 15);
            LocalDate priceDate = LocalDate.of(2024, 4, 10); // Historical price date

            PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
            existingSnapshot.setId(1L);
            existingSnapshot.setStock(stock);
            existingSnapshot.setSnapshotDate(snapshotDate);

            when(snapshotRepository.findByStockIdAndSnapshotDate(stock.getId(), snapshotDate))
                    .thenReturn(Optional.of(existingSnapshot));

            DailyPrice historicalPrice = new DailyPrice();
            historicalPrice.setStock(stock);
            historicalPrice.setPriceDate(snapshotDate);
            historicalPrice.setClosingPrice(new BigDecimal("120.00")); // Price on April 15

            // getClosingPriceForDate() queries with the snapshot date
            when(dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), snapshotDate))
                    .thenReturn(Optional.of(historicalPrice));

            // Act
            List<PortfolioSnapshot> results = portfolioSnapshotService.saveOrUpdate(stock, snapshotDate);

            // Assert
            assertFalse(results.isEmpty(), "Result list should not be empty");
            PortfolioSnapshot result = results.get(0);
            assertEquals(stock.getId(), result.getStock().getId());
            assertEquals(snapshotDate, result.getSnapshotDate());
            assertEquals(stock.getQuantity(), result.getQuantity());
            assertEquals(stock.getAvgPrice(), result.getAvgPrice());
            assertEquals(stock.getLastTradedPrice(), result.getLastTradedPrice());

            // Investment = avgPrice * quantity = 150.00 * 100 = 15,000 (cost basis)
            assertEquals(new BigDecimal("15000.00"), result.getInvestment(),
                    "Investment should use avgPrice (cost basis)");

            // Current value = historicalPrice * quantity = 120.00 * 100 = 12,000
            assertEquals(new BigDecimal("12000.00"), result.getCurrentValue(),
                    "Current value should use historical closing price");

            // P&L = 12,000 - 15,000 = -3,000
            assertEquals(new BigDecimal("-3000.00"), result.getPnl(),
                    "P&L should be currentValue - investment");
            // P&L percent = (-3000 / 15000) * 100 = -20%
            assertEquals(new BigDecimal("-20.00"), result.getPnlPercent(),
                    "P&L percent should be (pnl / investment) * 100");
        }

        @Test
        @DisplayName("Should fall back to current investment when no historical price data")
        void shouldFallBackToCurrentValues_whenNoHistoricalPriceData() {
            // Arrange
            Stock stock = new Stock();
            stock.setId(2L);
            stock.setSymbol("GOOGL");
            stock.setQuantity(50);
            stock.setAvgPrice(new BigDecimal("2800.00"));
            stock.setLastTradedPrice(new BigDecimal("2900.00"));
            stock.setInvestment(new BigDecimal("140000")); // 50 * 2800
            stock.setCurrentValue(new BigDecimal("145000")); // 50 * 2900

            LocalDate snapshotDate = LocalDate.of(2024, 4, 15);

            PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
            existingSnapshot.setId(2L);
            existingSnapshot.setStock(stock);
            existingSnapshot.setSnapshotDate(snapshotDate);

            when(snapshotRepository.findByStockIdAndSnapshotDate(stock.getId(), snapshotDate))
                    .thenReturn(Optional.of(existingSnapshot));

            // No historical price data available
            when(dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), snapshotDate))
                    .thenReturn(Optional.empty());
            when(dailyPriceRepository.findByStockIdAndPriceDateBetweenOrderByPriceDateDesc(
                    eq(stock.getId()), any(LocalDate.class), eq(snapshotDate)))
                    .thenReturn(Collections.emptyList());

            // Act
            List<PortfolioSnapshot> results = portfolioSnapshotService.saveOrUpdate(stock, snapshotDate);

            // Assert - Should use current investment/currentValue as fallback
            assertFalse(results.isEmpty(), "Result list should not be empty");
            PortfolioSnapshot result = results.get(0);
            assertEquals(0, stock.getInvestment().compareTo(result.getInvestment()),
                    "Should use current investment when no historical data");
            assertEquals(0, stock.getCurrentValue().compareTo(result.getCurrentValue()),
                    "Should use current currentValue when no historical data");
        }

        @Test
        @DisplayName("Should handle zero quantity correctly")
        void shouldHandleZeroQuantityCorrectly() {
            // Arrange
            Stock stock = new Stock();
            stock.setId(3L);
            stock.setSymbol("MSFT");
            stock.setQuantity(0); // Zero shares
            stock.setAvgPrice(new BigDecimal("300.00"));
            stock.setLastTradedPrice(new BigDecimal("320.00"));
            stock.setInvestment(BigDecimal.ZERO); // 0 * 300
            stock.setCurrentValue(BigDecimal.ZERO); // 0 * 320

            LocalDate snapshotDate = LocalDate.of(2024, 4, 15);

            PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
            existingSnapshot.setId(3L);
            existingSnapshot.setStock(stock);
            existingSnapshot.setSnapshotDate(snapshotDate);

            when(snapshotRepository.findByStockIdAndSnapshotDate(stock.getId(), snapshotDate))
                    .thenReturn(Optional.of(existingSnapshot));

            DailyPrice historicalPrice = new DailyPrice();
            historicalPrice.setStock(stock);
            historicalPrice.setPriceDate(snapshotDate);
            historicalPrice.setClosingPrice(new BigDecimal("280.00"));

            // getClosingPriceForDate() queries with the snapshot date
            when(dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), snapshotDate))
                    .thenReturn(Optional.of(historicalPrice));

            // Act
            List<PortfolioSnapshot> results = portfolioSnapshotService.saveOrUpdate(stock, snapshotDate);

            // Assert - Should be zero since quantity is zero
            assertFalse(results.isEmpty(), "Result list should not be empty");
            PortfolioSnapshot result = results.get(0);
            assertEquals(BigDecimal.ZERO, result.getInvestment(), "Investment should be zero");
            assertEquals(BigDecimal.ZERO, result.getCurrentValue(), "Current value should be zero");
            assertEquals(BigDecimal.ZERO, result.getPnl(), "P&L should be zero");
            assertEquals(BigDecimal.ZERO, result.getPnlPercent(), "P&L percent should be zero");
        }

        @Test
        @DisplayName("Should use most recent price when exact date price not available")
        void shouldUseMostRecentPrice_whenExactDatePriceNotAvailable() {
            // Arrange
            Stock stock = new Stock();
            stock.setId(4L);
            stock.setSymbol("TSLA");
            stock.setQuantity(25);
            stock.setAvgPrice(new BigDecimal("800.00"));
            stock.setLastTradedPrice(new BigDecimal("850.00"));
            stock.setInvestment(new BigDecimal("20000")); // 25 * 800
            stock.setCurrentValue(new BigDecimal("21250")); // 25 * 850

            LocalDate snapshotDate = LocalDate.of(2024, 4, 15); // Target date (weekend/market closed)
            LocalDate priceDate = LocalDate.of(2024, 4, 12);   // Most recent trading day before target

            PortfolioSnapshot existingSnapshot = new PortfolioSnapshot();
            existingSnapshot.setId(4L);
            existingSnapshot.setStock(stock);
            existingSnapshot.setSnapshotDate(snapshotDate);

            when(snapshotRepository.findByStockIdAndSnapshotDate(stock.getId(), snapshotDate))
                    .thenReturn(Optional.of(existingSnapshot));

            // No price for exact date (weekend)
            when(dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), snapshotDate))
                    .thenReturn(Optional.empty());

            // Most recent price before the date
            List<DailyPrice> recentPrices = Collections.singletonList(new DailyPrice());
            recentPrices.get(0).setStock(stock);
            recentPrices.get(0).setPriceDate(priceDate);
            recentPrices.get(0).setClosingPrice(new BigDecimal("750.00"));

            when(dailyPriceRepository.findByStockIdAndPriceDateBetweenOrderByPriceDateDesc(
                    eq(stock.getId()), any(LocalDate.class), eq(snapshotDate)))
                    .thenReturn(recentPrices);

            // Act
            List<PortfolioSnapshot> results = portfolioSnapshotService.saveOrUpdate(stock, snapshotDate);

            // Assert - Should use April 12 price (most recent before April 15)
            assertFalse(results.isEmpty(), "Result list should not be empty");
            PortfolioSnapshot result = results.get(0);
            // Investment = avgPrice * quantity = 800.00 * 25 = 20,000 (cost basis)
            assertEquals(new BigDecimal("20000.00"), result.getInvestment(),
                    "Investment should use avgPrice (cost basis)");
            // Current value = historicalPrice * quantity = 750.00 * 25 = 18,750
            assertEquals(new BigDecimal("18750.00"), result.getCurrentValue(),
                    "Current value should use most recent historical closing price");
        }
    }
}