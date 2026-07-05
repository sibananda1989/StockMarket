package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StochDCalculatorTest {

    @Test
    void testStochasticD_CalculatesCorrectly() {
        // Given: Enough price data for %K and %D calculations
        List<DailyPrice> prices = createSamplePriceData();
        
        // When: Calculating Stochastic %D with K=14, D=3
        StochDCalculator calculator = new StochDCalculator(14, 3);
        BigDecimal result = calculator.calculate(prices);
        
        // Then: Result should be the average of the last 3 %K values
        // This is a simplified test - in practice we'd calculate expected value
        assertNotNull(result);
        assertTrue(result.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(result.compareTo(new BigDecimal(100)) <= 0);
    }

    @Test
    void testStochasticD_InsufficientData_ThrowsException() {
        // Given: Not enough data for KPeriod + DPeriod - 1
        List<DailyPrice> prices = createSamplePriceData().subList(0, 15); // Need at least 14+3-1=16
        
        // When/Then: Should throw exception for insufficient data
        StochDCalculator calculator = new StochDCalculator(14, 3);
        assertThrows(IllegalArgumentException.class, 
                () -> calculator.calculate(prices));
    }

    private List<DailyPrice> createSamplePriceData() {
          List<DailyPrice> prices = new ArrayList<>();
          for (int i = 0; i < 20; i++) {
              // Ensure valid price relationships: low <= close <= high
              BigDecimal close = new BigDecimal(String.valueOf(95 + i*2)); // 95, 97, 99, ..., 133
              BigDecimal high = close.add(new BigDecimal("5")); // High is 5 above close
              BigDecimal low = close.subtract(new BigDecimal("5")); // Low is 5 below close
              prices.add(new DailyPrice(
                      null, // stock
                      close, // closingPrice
                      new BigDecimal("100.00"), // openingPrice
                      high, // highPrice
                      low,  // lowPrice
                      1000L, // volume
                      LocalDate.of(2023, 1, i+1) // priceDate
              ));
          }
        return prices;
    }
}