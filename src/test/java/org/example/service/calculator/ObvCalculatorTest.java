package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObvCalculatorTest {

    @Test
    void testOBV_CalculatesCorrectly_AllUpDays() {
        // Given: 5 days, each day close is higher than previous
        // Day 1: 100, Day 2: 105, Day 3: 110, Day 4: 115, Day 5: 120
        // Volumes: 1000, 2000, 1500, 3000, 2500
        List<DailyPrice> prices = createUpDownPrices(
                new double[]{100, 105, 110, 115, 120},
                new long[]{1000, 2000, 1500, 3000, 2500}
        );

        // When: Calculating OBV
        ObvCalculator calculator = new ObvCalculator();
        BigDecimal result = calculator.calculate(prices);

        // Then: OBV = 0 + 2000 + 1500 + 3000 + 2500 = 9000
        assertEquals(new BigDecimal("9000"), result);
    }

    @Test
    void testOBV_CalculatesCorrectly_AllDownDays() {
        // Given: 5 days, each day close is lower than previous
        List<DailyPrice> prices = createUpDownPrices(
                new double[]{120, 115, 110, 105, 100},
                new long[]{1000, 2000, 1500, 3000, 2500}
        );

        // When: Calculating OBV
        ObvCalculator calculator = new ObvCalculator();
        BigDecimal result = calculator.calculate(prices);

        // Then: OBV = 0 - 2000 - 1500 - 3000 - 2500 = -9000
        assertEquals(new BigDecimal("-9000"), result);
    }

    @Test
    void testOBV_CalculatesCorrectly_MixedUpAndDown() {
        // Given: Mixed up/down days
        // Close: 100, 110 (up), 105 (down), 115 (up), 110 (down)
        // Volume: 1000, 2000, 1500, 3000, 2500
        List<DailyPrice> prices = createUpDownPrices(
                new double[]{100, 110, 105, 115, 110},
                new long[]{1000, 2000, 1500, 3000, 2500}
        );

        // When: Calculating OBV
        ObvCalculator calculator = new ObvCalculator();
        BigDecimal result = calculator.calculate(prices);

        // Then: OBV = 0 + 2000 - 1500 + 3000 - 2500 = 1000
        assertEquals(new BigDecimal("1000"), result);
    }

    @Test
    void testOBV_ConstantPrice_VolumeIgnored() {
        // Given: All closes are the same (100)
        List<DailyPrice> prices = createUpDownPrices(
                new double[]{100, 100, 100, 100, 100},
                new long[]{1000, 2000, 1500, 3000, 2500}
        );

        // When: Calculating OBV
        ObvCalculator calculator = new ObvCalculator();
        BigDecimal result = calculator.calculate(prices);

        // Then: OBV = 0 (no changes)
        assertEquals(new BigDecimal("0"), result);
    }

    @Test
    void testOBV_InsufficientData_ThrowsException() {
        // Given: Only 1 day of data
        List<DailyPrice> prices = new ArrayList<>();
        prices.add(new DailyPrice(
                null,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                new BigDecimal("90.00"),
                1000L,
                LocalDate.of(2023, 1, 1)
        ));

        // When/Then: Should throw exception
        ObvCalculator calculator = new ObvCalculator();
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(prices));
    }

    @Test
    void testOBV_NullVolume_TreatedAsZero() {
        // Given: Price data with null volumes
        List<DailyPrice> prices = new ArrayList<>();
        prices.add(new DailyPrice(null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                new BigDecimal("110.00"), new BigDecimal("90.00"), null, LocalDate.of(2023, 1, 1)));
        prices.add(new DailyPrice(null, new BigDecimal("110.00"), new BigDecimal("100.00"),
                new BigDecimal("115.00"), new BigDecimal("95.00"), null, LocalDate.of(2023, 1, 2)));

        // When: Calculating OBV
        ObvCalculator calculator = new ObvCalculator();
        BigDecimal result = calculator.calculate(prices);

        // Then: OBV = 0 + 0 = 0 (null volume treated as 0)
        assertEquals(new BigDecimal("0"), result);
    }

    @Test
    void testOBV_GetSupportedType_ReturnsOBV() {
        ObvCalculator calculator = new ObvCalculator();
        assertEquals(org.example.entity.IndicatorType.OBV, calculator.getSupportedType());
    }

    private List<DailyPrice> createUpDownPrices(double[] closes, long[] volumes) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            prices.add(new DailyPrice(
                    null,
                    new BigDecimal(String.valueOf(closes[i])),
                    new BigDecimal("100.00"),
                    new BigDecimal("110.00"),
                    new BigDecimal("90.00"),
                    volumes[i],
                    LocalDate.of(2023, 1, 1).plusDays(i)
            ));
        }
        return prices;
    }
}
