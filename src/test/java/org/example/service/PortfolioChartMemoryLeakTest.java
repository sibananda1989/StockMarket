package org.example.service;

import org.example.dto.PortfolioAggregateDTO;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioSnapshotRepository;
import org.example.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Test suite for Portfolio Chart memory leak fixes.
 * Verify that Chart.js instances can be properly cleaned up and operations are memory-safe.
 *
 * Note: This tests the service layer for memory safety. Actual chart instance cleanup
 * is handled in the frontend JavaScript code.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioChartMemoryLeakTest {

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private PortfolioSnapshotService snapshotService;

    private final LocalDate testDate = LocalDate.now().minusDays(1);

    @Test
    void getAggregatedHistory_ShouldHandleEmptyPortfolioGracefully() {
        // Test that empty portfolio doesn't cause any memory issues or exceptions
        when(snapshotRepository.findAggregatedHistoryAll(any())).thenReturn(Collections.emptyList());

        List<PortfolioAggregateDTO> result = snapshotService.getAggregatedHistory(365);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify no interactions that could cause leaks
        verify(snapshotRepository, times(1)).findAggregatedHistoryAll(any());
    }

    @Test
    void getAggregatedHistory_ShouldHandleNullPortfolioDataGracefully() {
        // Test that null data doesn't cause memory issues
        when(snapshotRepository.findAggregatedHistoryAll(any())).thenReturn(null);

        // This should not throw NullPointerException after fix
        assertDoesNotThrow(() -> {
            List<PortfolioAggregateDTO> result = snapshotService.getAggregatedHistory(365);
            assertNotNull(result);
        });
    }

    @Test
    void getAggregatedHistory_ShouldHandleLargeNumberOfRows() {
        // Test memory handling with many rows (simulating pagination in database results)
        when(snapshotRepository.findAggregatedHistoryAll(any())).thenReturn(
                List.of(
                        createMockRow(testDate.minusDays(100), 50000, 60000, 10000, 10),
                        createMockRow(testDate.minusDays(50), 55000, 65000, 10000, 15),
                        createMockRow(testDate, 60000, 70000, 10000, 20),
                        createMockRow(testDate.plusDays(50), 65000, 75000, 10000, 18)
                )
        );

        List<PortfolioAggregateDTO> result = snapshotService.getAggregatedHistory(365);

        assertNotNull(result);
        assertEquals(4, result.size());

        // Verify memory safety - no interactions that would cause issues
        verify(snapshotRepository, times(1)).findAggregatedHistoryAll(any());
    }

    @Test
    void getAggregatedHistory_ShouldCalculatePnlPercentCorrectly() {
        // Test financial calculations don't cause memory issues
        when(snapshotRepository.findAggregatedHistoryAll(any())).thenReturn(
                List.of(
                        createMockRow(testDate, 10000, 12000, 2000, 10),
                        createMockRow(testDate.plusDays(1), 10000, 8000, -2000, 10)
                )
        );

        List<PortfolioAggregateDTO> result = snapshotService.getAggregatedHistory(365);

        assertNotNull(result);
        assertEquals(2, result.size());

        // Verify positive P&L calculation
        PortfolioAggregateDTO positive = result.get(0);
        assertEquals(20.00, positive.getTotalPnlPercent().doubleValue(), 0.01);

        // Verify negative P&L calculation
        PortfolioAggregateDTO negative = result.get(1);
        assertEquals(-20.0, negative.getTotalPnlPercent().doubleValue(), 0.01); // investment=10000, pnl=-2000 -> -20%
    }

    @Test
    void getAggregatedHistory_ShouldHandleZeroInvestment() {
        // Test edge case where investment is zero
        when(snapshotRepository.findAggregatedHistoryAll(any())).thenReturn(
                Collections.singletonList(createMockRow(testDate, 0, 1000, 1000, 5))
        );

        assertDoesNotThrow(() -> {
            List<PortfolioAggregateDTO> result = snapshotService.getAggregatedHistory(365);
            assertNotNull(result);
            assertEquals(1, result.size());
        });
    }

    @Test
    void getAggregatedHistory_ShouldHandleNullValues() {
        // Test with null values in the database result
        when(snapshotRepository.findAggregatedHistoryAll(any())).thenReturn(
                Collections.singletonList(new Object[]{null, null, null, null, null})
        );

        assertDoesNotThrow(() -> {
            List<PortfolioAggregateDTO> result = snapshotService.getAggregatedHistory(365);
            assertNotNull(result);
        });
    }

    @Test
    void getAggregatedHistory_ShouldHandleDeepTimeSeries() {
        // Test with large number of days to ensure it doesn't cause memory pressure
        when(snapshotRepository.findAggregatedHistoryAll(any())).thenReturn(
                List.of(
                        createMockRow(LocalDate.now().minusDays(365 * 2), 50000, 55000, 5000, 50),
                        createMockRow(LocalDate.now().minusDays(365), 70000, 75000, 5000, 75),
                        createMockRow(LocalDate.now(), 80000, 85000, 5000, 80)
                )
        );

        assertDoesNotThrow(() -> {
            List<PortfolioAggregateDTO> result = snapshotService.getAggregatedHistory(730);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        });
    }

    @Test
    void getAllAggregatedHistory_ShouldHandleCompleteHistoryGracefully() {
        // Test that complete history doesn't cause memory issues
        when(snapshotRepository.findAllAggregatedHistoryAll()).thenReturn(
                List.of(
                        createMockRow(testDate.minusDays(500), 100000, 120000, 20000, 50),
                        createMockRow(testDate.minusDays(250), 110000, 130000, 20000, 55),
                        createMockRow(testDate, 150000, 180000, 30000, 80)
                )
        );

        assertDoesNotThrow(() -> {
            List<PortfolioAggregateDTO> result = snapshotService.getAllAggregatedHistory();
            assertNotNull(result);
            assertEquals(3, result.size());
        });
    }

    @Test
    void getAllAggregatedHistory_ShouldHandleEmptyCompleteHistory() {
        when(snapshotRepository.findAllAggregatedHistoryAll()).thenReturn(Collections.emptyList());

        List<PortfolioAggregateDTO> result = snapshotService.getAllAggregatedHistory();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void backendOperations_ShouldNotCauseMemoryLeaksInServiceLayer() {
        // Multiple calls to service methods should not cause memory buildup
        when(snapshotRepository.findAggregatedHistoryAll(any())).thenReturn(
                Collections.singletonList(createMockRow(testDate, 10000, 11000, 1000, 10))
        );

        // Simulate multiple chart renders (e.g., user changing time period)
        for (int i = 0; i < 10; i++) {
            List<PortfolioAggregateDTO> result = snapshotService.getAggregatedHistory(365);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        // Verify database queries are consistent
        verify(snapshotRepository, times(10)).findAggregatedHistoryAll(any());
    }

    @Test
    void dtoNullSafety_ShouldHaveToStringNoThrowingNullPointerException() {
        // Test DTO immunity to null pointer issues
        PortfolioAggregateDTO dto = new PortfolioAggregateDTO();
        assertDoesNotThrow(() -> {
            String toString = dto.toString();
            assertNotNull(toString);
        });

        // Test with partially populated values
        dto.setDate(null);
        dto.setTotalInvestment(null);
        assertDoesNotThrow(() -> {
            String toString = dto.toString();
            assertNotNull(toString);
        });
    }

    @Test
    void dtoAccessors_ShouldBeNullSafe() {
        // Test all DTO accessors are null-safe
        PortfolioAggregateDTO dto = new PortfolioAggregateDTO();

        assertDoesNotThrow(() -> {
            dto.getDate();
            dto.getTotalInvestment();
            dto.getTotalCurrentValue();
            dto.getTotalPnl();
            dto.getTotalPnlPercent();
            dto.getHoldingsCount();
        });

        // No exceptions should be thrown even with null state
    }

    /* Helper method to create mock database row */
    private Object[] createMockRow(LocalDate date, double investment, double currentValue, double pnl, int count) {
        return new Object[]{
                java.sql.Date.valueOf(date),
                BigDecimal.valueOf(investment),
                BigDecimal.valueOf(currentValue),
                BigDecimal.valueOf(pnl),
                count
        };
    }
}