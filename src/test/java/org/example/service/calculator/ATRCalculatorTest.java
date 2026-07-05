package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ATRCalculatorTest {

    @Test
    void testATR_CalculatesCorrectly() {
        // Given: Price data with known true ranges
        List<DailyPrice> prices = createSamplePriceData();
        
        // When: Calculating ATR with period 14
        ATRCalculator calculator = new ATRCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        
        // Then: Result should be positive and reasonable
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testATR_InsufficientData_ThrowsException() {
        // Given: Only 10 days of price data
        List<DailyPrice> prices = createSamplePriceData().subList(0, 10);
        
        // When/Then: Should throw exception for insufficient data
        ATRCalculator calculator = new ATRCalculator(14);
        assertThrows(IllegalArgumentException.class, 
                () -> calculator.calculate(prices));
    }

    @Test
    void testATR_NoMovement_ReturnsZero() {
        // Given: No price movement (high=low=close, and no gaps)
         List<DailyPrice> prices = new ArrayList<>();
         for (int i = 0; i < 14; i++) {
             prices.add(new DailyPrice(
                     null, // stock
                     new BigDecimal("100.00"), // closingPrice
                     new BigDecimal("100.00"), // openingPrice
                     new BigDecimal("100.00"), // highPrice
                     new BigDecimal("100.00"), // lowPrice
                     1000L, // volume
                     LocalDate.of(2023, 1, i+1) // priceDate
             ));
         }
        
        // When: Calculating ATR
        ATRCalculator calculator = new ATRCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        
         // Then: Should return 0 (no true range)
         assertEquals(BigDecimal.ZERO.setScale(2), result);
    }

    private List<DailyPrice> createSamplePriceData() {
        List<DailyPrice> prices = new ArrayList<>();
        // Sample data with known true ranges
        for (int i = 0; i < 14; i++) {
            prices.add(new DailyPrice(
                    null, // stock
                    new BigDecimal(String.valueOf(100 + i*2)), // closingPrice: increases faster
                    new BigDecimal("100.00"), // openingPrice
                    new BigDecimal(String.valueOf(105 + i)), // highPrice: increases
                    new BigDecimal(String.valueOf(95 + i)),  // lowPrice: increases
                    1000L, // volume
                    LocalDate.of(2023, 1, i+1) // priceDate
            ));
        }
        return prices;
    }
}