package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WilliamsRCalculatorTest {

    @Test
    void testWilliamsR_CalculatesCorrectly() {
        // Given: Price data for 14 days
        List<DailyPrice> prices = createSamplePriceData();
        
        // When: Calculating Williams %R with period 14
        WilliamsRCalculator calculator = new WilliamsRCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        
        // Then: Result should be approximately -10.00 (based on sample data)
        // High=110, Low=90, Close progresses from 95 to 108
        // %R = (110-108)/(110-90)*-100 = 2/20*-100 = -10.00
        assertEquals(new BigDecimal("-10.00"), result.setScale(2, BigDecimal.ROUND_HALF_UP));
    }

    @Test
    void testWilliamsR_InsufficientData_ThrowsException() {
        // Given: Only 10 days of price data
        List<DailyPrice> prices = createSamplePriceData().subList(0, 10);
        
        // When/Then: Should throw exception for insufficient data
        WilliamsRCalculator calculator = new WilliamsRCalculator(14);
        assertThrows(IllegalArgumentException.class, 
                () -> calculator.calculate(prices));
    }

    @Test
    void testWilliamsR_ConstantHighLow_ReturnsZero() {
        // Given: High and low are the same every day (no range)
          List<DailyPrice> prices = new ArrayList<>();
          for (int i = 0; i < 14; i++) {
              prices.add(new DailyPrice(
                      null, // stock
                      new BigDecimal(String.valueOf(95 + i)), // closingPrice: 95, 96, ..., 108
                      new BigDecimal("100.00"), // openingPrice
                      new BigDecimal("100.00"), // highPrice
                      new BigDecimal("100.00"), // lowPrice
                      1000L, // volume
                      LocalDate.of(2023, 1, i+1) // priceDate
              ));
          }
        
        // When: Calculating Williams %R
        WilliamsRCalculator calculator = new WilliamsRCalculator(14);
        BigDecimal result = calculator.calculate(prices);
        
        // Then: Should return 0 (no range)
        assertEquals(BigDecimal.ZERO, result);
    }

    private List<DailyPrice> createSamplePriceData() {
        List<DailyPrice> prices = new ArrayList<>();
        // Sample data: High=110, Low=90, Close gradually increasing from 95 to 108
        for (int i = 0; i < 14; i++) {
            prices.add(new DailyPrice(
                    null, // stock
                    new BigDecimal(String.valueOf(95 + i)), // closingPrice: 95, 96, ..., 108
                    new BigDecimal("100.00"), // openingPrice
                    new BigDecimal("110.00"), // highPrice
                    new BigDecimal("90.00"),  // lowPrice
                    1000L, // volume
                    LocalDate.of(2023, 1, i+1) // priceDate
            ));
        }
        return prices;
    }
}