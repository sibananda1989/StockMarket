package org.example.service;

import org.example.dto.HoldingDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.Stock;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioRepository;
import org.example.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioHoldingRepository holdingRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private PortfolioSnapshotService snapshotService;

    @InjectMocks
    private PortfolioService portfolioService;

    private Stock testStock;
    private Portfolio testPortfolio;
    private DailyPrice testPrice;

    @BeforeEach
    void setUp() {
        testStock = new Stock();
        testStock.setId(1L);
        testStock.setSymbol("TEST.NS");
        testStock.setName("Test Stock");
        testStock.setSector("Technology");

        testPortfolio = new Portfolio("Test Portfolio", "Test", false);
        testPortfolio.setId(10L);

        testPrice = new DailyPrice();
        testPrice.setClosingPrice(new BigDecimal("150.00"));
        testPrice.setPriceDate(LocalDate.now());
    }

    // ─── getHolding() ─────────────────────────────────────────────────────────

    @Test
    void getHolding_ExistingHolding_ReturnsDTO() {
        when(portfolioRepository.existsById(10L)).thenReturn(true);
        PortfolioHolding holding = createHolding(5, new BigDecimal("120.00"));
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.of(holding));
        when(dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(1L)).thenReturn(Optional.of(testPrice));

        HoldingDTO dto = portfolioService.getHolding(10L, 1L);

        assertNotNull(dto);
        assertEquals(1L, dto.getStockId());
        assertEquals("TEST.NS", dto.getSymbol());
        assertEquals(10L, dto.getPortfolioId());
        assertEquals(5, dto.getQuantity());
        assertEquals(0, new BigDecimal("120.00").compareTo(dto.getAvgPrice()));
        assertEquals(0, new BigDecimal("600.00").compareTo(dto.getInvestment())); // 5 * 120
        assertEquals(0, new BigDecimal("750.00").compareTo(dto.getCurrentValue())); // 5 * 150
        assertEquals(0, new BigDecimal("150.00").compareTo(dto.getPnl())); // 750 - 600
    }

    @Test
    void getHolding_StockNotInPortfolio_ThrowsResourceNotFound() {
        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(holdingRepository.findByPortfolioIdAndStockId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> portfolioService.getHolding(10L, 1L));
    }

    @Test
    void getHolding_PortfolioNotFound_ThrowsResourceNotFound() {
        when(portfolioRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> portfolioService.getHolding(99L, 1L));
    }

    // ─── getAggregateHolding() ────────────────────────────────────────────────

    @Test
    void getAggregateHolding_SinglePortfolio_ReturnsCorrectMath() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(testStock));
        when(holdingRepository.findByStockId(1L)).thenReturn(List.of(createHolding(5, new BigDecimal("100.00"))));
        when(dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(1L)).thenReturn(Optional.of(testPrice));

        HoldingDTO dto = portfolioService.getAggregateHolding(1L);

        assertNotNull(dto);
        assertEquals(1L, dto.getStockId());
        assertEquals(5, dto.getQuantity());
        assertEquals(0, new BigDecimal("100.00").compareTo(dto.getAvgPrice()));
        assertEquals(0, new BigDecimal("500.00").compareTo(dto.getInvestment())); // 5 * 100
        assertEquals(0, new BigDecimal("750.00").compareTo(dto.getCurrentValue())); // 5 * 150
        assertEquals(0, new BigDecimal("250.00").compareTo(dto.getPnl())); // 750 - 500
        assertNull(dto.getPortfolioId());
    }

    @Test
    void getAggregateHolding_MultiplePortfolios_AggregatesCorrectly() {
        when(stockRepository.findById(1L)).thenReturn(Optional.of(testStock));
        PortfolioHolding h1 = createHolding(3, new BigDecimal("100.00"));
        PortfolioHolding h2 = createHolding(5, new BigDecimal("120.00"));
        when(holdingRepository.findByStockId(1L)).thenReturn(List.of(h1, h2));
        when(dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(1L)).thenReturn(Optional.of(testPrice));

        HoldingDTO dto = portfolioService.getAggregateHolding(1L);

        assertNotNull(dto);
        assertEquals(8, dto.getQuantity()); // 3 + 5
        BigDecimal expectedAvg = new BigDecimal("112.50"); // (3*100 + 5*120) / 8 = 900/8
        assertEquals(0, expectedAvg.compareTo(dto.getAvgPrice()),
                "Weighted avg price should be 112.50, got: " + dto.getAvgPrice());
        BigDecimal expectedInv = new BigDecimal("900.00"); // 3*100 + 5*120
        assertEquals(0, expectedInv.compareTo(dto.getInvestment()),
                "Investment should sum to 900, got: " + dto.getInvestment());
        BigDecimal expectedVal = new BigDecimal("1200.00"); // 8 * 150
        assertEquals(0, expectedVal.compareTo(dto.getCurrentValue()),
                "Current value should be 1200, got: " + dto.getCurrentValue());
        BigDecimal expectedPnl = new BigDecimal("300.00"); // 1200 - 900
        assertEquals(0, expectedPnl.compareTo(dto.getPnl()),
                "P&L should be 300, got: " + dto.getPnl());
    }

    @Test
    void getAggregateHolding_NoHoldings_ThrowsResourceNotFound() {
        when(holdingRepository.findByStockId(1L)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> portfolioService.getAggregateHolding(1L));
    }

    @Test
    void getAggregateHolding_StockNotFound_ThrowsResourceNotFound() {
        when(holdingRepository.findByStockId(99L)).thenReturn(List.of(createHolding(1, new BigDecimal("50.00"))));
        when(stockRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> portfolioService.getAggregateHolding(99L));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private PortfolioHolding createHolding(int quantity, BigDecimal avgPrice) {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setId((long) (Math.random() * 1000));
        holding.setPortfolio(testPortfolio);
        holding.setStock(testStock);
        holding.setQuantity(quantity);
        holding.setAvgPrice(avgPrice);
        return holding;
    }
}
