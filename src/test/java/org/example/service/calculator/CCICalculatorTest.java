package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CCICalculatorTest {

    @Test
    void testCCI_CalculatesCorrectly() {
        // Given: Price data for 20 days
        List<DailyPrice> prices = createSamplePriceData();
        
        // When: Calculating CCI with period 20
        CCICalculator calculator = new CCICalculator(20);
        BigDecimal result = calculator.calculate(prices);
        
        // Then: Result should be a reasonable value (not null)
        assertNotNull(result);
    }

    @Test
    void testCCI_InsufficientData_ThrowsException() {
        // Given: Only 10 days of price data
        List<DailyPrice> prices = createSamplePriceData().subList(0, 10);
        
        // When/Then: Should throw exception for insufficient data
        CCICalculator calculator = new CCICalculator(20);
        assertThrows(IllegalArgumentException.class, 
                () -> calculator.calculate(prices));
    }

    private List<DailyPrice> createSamplePriceData() {
         List<DailyPrice> prices = new ArrayList<>();
         for (int i = 0; i < 25; i++) {
             prices.add(new DailyPrice(
                     null, // stock
                     new BigDecimal(String.valueOf(100 + i * 0.5)), // closingPrice: slow increase
                     new BigDecimal("100.00"), // openingPrice
                     new BigDecimal(String.valueOf(110 + Math.sin(i) * 5)), // highPrice
                     new BigDecimal(String.valueOf(90 + Math.cos(i) * 5)),  // lowPrice
                     1000L, // volume
                     LocalDate.of(2023, 1, i+1) // priceDate
             ));
         }
        return prices;
    }
}