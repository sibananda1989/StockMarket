package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.HoldingDTO;
import org.example.entity.*;
import org.example.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.example.exception.ResourceNotFoundException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PortfolioManagementControllerIntegrationTest {

    @Autowired
    private PortfolioManagementController controller;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioHoldingRepository holdingRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DailyPriceRepository dailyPriceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Stock testStock;
    private Portfolio testPortfolio;

    private void clearAllData() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("DELETE FROM portfolio_holdings");
        jdbcTemplate.execute("DELETE FROM portfolio_snapshots");
        jdbcTemplate.execute("DELETE FROM portfolios");
        jdbcTemplate.execute("DELETE FROM daily_prices");
        jdbcTemplate.execute("DELETE FROM stocks");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @BeforeEach
    void setUp() {
        clearAllData();

        testPortfolio = portfolioRepository.save(new Portfolio("Test Portfolio", "Test", true));

        testStock = new Stock();
        testStock.setSymbol("TEST.NS");
        testStock.setName("Test Stock");
        testStock.setSector("Technology");
        testStock.setQuantity(0);
        testStock = stockRepository.save(testStock);

        // Need a daily price for the holding P&L computation
        dailyPriceRepository.save(new DailyPrice(testStock, new BigDecimal("150.00"),
                new BigDecimal("148.00"), new BigDecimal("152.00"),
                new BigDecimal("147.00"), 100000L, LocalDate.now()));
    }

    @AfterEach
    void tearDown() {
        clearAllData();
    }

    // ─── getHolding() ─────────────────────────────────────────────────────────

    @Test
    void getHolding_ExistingHolding_Returns200WithData() {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setPortfolio(testPortfolio);
        holding.setStock(testStock);
        holding.setQuantity(9);
        holding.setAvgPrice(new BigDecimal("627.00"));
        holding = holdingRepository.save(holding);

        ResponseEntity<ApiResponse<HoldingDTO>> response = controller.getHolding(testPortfolio.getId(), testStock.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<HoldingDTO> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());

        HoldingDTO dto = body.getData();
        assertNotNull(dto);
        assertEquals(9, dto.getQuantity());
        assertEquals(0, new BigDecimal("627.00").compareTo(dto.getAvgPrice()));
        assertEquals(testStock.getId(), dto.getStockId());
        assertEquals(testPortfolio.getId(), dto.getPortfolioId());
        assertEquals("TEST.NS", dto.getSymbol());
        assertNotNull(dto.getInvestment());
        assertTrue(dto.getInvestment().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void getHolding_StockNotInPortfolio_ThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> controller.getHolding(testPortfolio.getId(), 9999L));
    }

    @Test
    void getHolding_PortfolioNotFound_ThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> controller.getHolding(9999L, testStock.getId()));
    }

    // ─── getAggregateHolding() ────────────────────────────────────────────────

    @Test
    void getAggregateHolding_SinglePortfolio_Returns200() {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setPortfolio(testPortfolio);
        holding.setStock(testStock);
        holding.setQuantity(9);
        holding.setAvgPrice(new BigDecimal("627.00"));
        holdingRepository.save(holding);

        ResponseEntity<ApiResponse<HoldingDTO>> response = controller.getAggregateHolding(testStock.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<HoldingDTO> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());

        HoldingDTO dto = body.getData();
        assertNotNull(dto);
        assertEquals(9, dto.getQuantity());
        assertNull(dto.getPortfolioId(), "Aggregate should not have portfolioId");
        assertNotNull(dto.getInvestment());
        assertTrue(dto.getInvestment().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void getAggregateHolding_MultiplePortfolios_Returns200WithAggregate() {
        Portfolio portfolio2 = portfolioRepository.save(new Portfolio("Portfolio 2", "Second", false));

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setPortfolio(testPortfolio);
        h1.setStock(testStock);
        h1.setQuantity(5);
        h1.setAvgPrice(new BigDecimal("100.00"));
        holdingRepository.save(h1);

        PortfolioHolding h2 = new PortfolioHolding();
        h2.setPortfolio(portfolio2);
        h2.setStock(testStock);
        h2.setQuantity(3);
        h2.setAvgPrice(new BigDecimal("120.00"));
        holdingRepository.save(h2);

        ResponseEntity<ApiResponse<HoldingDTO>> response = controller.getAggregateHolding(testStock.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        HoldingDTO dto = response.getBody().getData();
        assertNotNull(dto);
        assertEquals(8, dto.getQuantity(), "Total qty should be 5+3=8");
    }

    @Test
    void getAggregateHolding_NoHoldings_ThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> controller.getAggregateHolding(testStock.getId()));
    }
}
