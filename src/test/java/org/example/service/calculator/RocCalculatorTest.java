package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RocCalculatorTest {

    @Test
    void testROC_CalculatesCorrectly_PositiveMomentum() {
        // Given: 13 days of data, close goes from 100 to 110 over 12 periods
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(100 + i)), // closingPrice: 100, 101, ..., 112
                    new BigDecimal("100.00"), // openingPrice
                    new BigDecimal(String.valueOf(102 + i)), // highPrice
                    new BigDecimal(String.valueOf(98 + i)),  // lowPrice
                    1000L, // volume
                    LocalDate.of(2023, 1, 1).plusDays(i) // priceDate
            ));
        }

        // When: Calculating ROC with period 12
        RocCalculator calculator = new RocCalculator(12);
        BigDecimal result = calculator.calculate(prices);

        // Then: ROC = (112 - 100) / 100 * 100 = 12.00
        assertEquals(new BigDecimal("12.00"), result);
    }

    @Test
    void testROC_CalculatesCorrectly_NegativeMomentum() {
        // Given: 13 days of data, close drops from 100 to 88 over 12 periods
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(100 - i)), // closingPrice: 100, 99, ..., 88
                    new BigDecimal("100.00"), // openingPrice
                    new BigDecimal(String.valueOf(102 - i)), // highPrice
                    new BigDecimal(String.valueOf(98 - i)),  // lowPrice
                    1000L, // volume
                    LocalDate.of(2023, 1, 1).plusDays(i) // priceDate
            ));
        }

        // When: Calculating ROC with period 12
        RocCalculator calculator = new RocCalculator(12);
        BigDecimal result = calculator.calculate(prices);

        // Then: ROC = (88 - 100) / 100 * 100 = -12.00
        assertEquals(new BigDecimal("-12.00"), result);
    }

    @Test
    void testROC_InsufficientData_ThrowsException() {
        // Given: Only 10 days of price data (need 13 for period 12)
        List<DailyPrice> prices = createSamplePrices(10);

        // When/Then: Should throw exception
        RocCalculator calculator = new RocCalculator(12);
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(prices));
    }

    @Test
    void testROC_ConstantPrices_ReturnsZero() {
        // Given: All prices are the same (100)
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal("100.00"), // closingPrice
                    new BigDecimal("100.00"), // openingPrice
                    new BigDecimal("100.00"), // highPrice
                    new BigDecimal("100.00"), // lowPrice
                    1000L, // volume
                    LocalDate.of(2023, 1, 1).plusDays(i) // priceDate
            ));
        }

        // When: Calculating ROC
        RocCalculator calculator = new RocCalculator(12);
        BigDecimal result = calculator.calculate(prices);

        // Then: ROC = (100 - 100) / 100 * 100 = 0.00
        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void testROC_SmallPeriod_WorksCorrectly() {
        // Given: 5 days of data, close goes from 100 to 110 over 4 periods
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(100 + i * 2.5)), // 100, 102.5, 105, 107.5, 110
                    new BigDecimal("100.00"),
                    new BigDecimal("110.00"),
                    new BigDecimal("90.00"),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }

        // When: Calculating ROC with period 4
        RocCalculator calculator = new RocCalculator(4);
        BigDecimal result = calculator.calculate(prices);

        // Then: ROC = (110 - 100) / 100 * 100 = 10.00
        assertEquals(new BigDecimal("10.00"), result);
    }

    @Test
    void testROC_GetSupportedType_ReturnsROC_12() {
        RocCalculator calculator = new RocCalculator(12);
        assertEquals(org.example.entity.IndicatorType.ROC_12, calculator.getSupportedType());
    }

    private List<DailyPrice> createSamplePrices(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(100 + i)),
                    new BigDecimal("100.00"),
                    new BigDecimal("110.00"),
                    new BigDecimal("90.00"),
                    1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }
        return prices;
    }
}
