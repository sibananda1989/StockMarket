package org.example.scheduler;

import org.example.entity.DailyPrice;
import org.example.entity.SignalRecord;
import org.example.repository.DailyPriceRepository;
import org.example.repository.SignalRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignalAccuracySchedulerTest {

    @Mock
    private SignalRecordRepository signalRecordRepository;
    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private SignalAccuracyScheduler scheduler;

    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        prices = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 30; i++) {
            DailyPrice dp = new DailyPrice();
            dp.setPriceDate(start.plusDays(i));
            dp.setClosingPrice(new BigDecimal("100.00"));
            prices.add(dp);
        }
    }

    @Test
    void testMarkRecord_NullPriceAtSignal() {
        SignalRecord record = new SignalRecord();
        record.setPriceAtSignal(null);

        boolean result = scheduler.markRecord(record);
        assertFalse(result);
        verifyNoInteractions(dailyPriceRepository);
    }

    @Test
    void testMarkRecord_ZeroPriceAtSignal() {
        SignalRecord record = new SignalRecord();
        record.setPriceAtSignal(BigDecimal.ZERO);

        boolean result = scheduler.markRecord(record);
        assertFalse(result);
        verifyNoInteractions(dailyPriceRepository);
    }

    @Test
    void testMarkRecord_NoPrices() {
        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("BUY");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(List.of());

        boolean result = scheduler.markRecord(record);
        assertFalse(result);
    }

    @Test
    void testMarkRecord_DateNotFoundInPrices() {
        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2025, 12, 31));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("BUY");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        assertFalse(result);
    }

    @Test
    void testMarkRecord_BuyAccurate() {
        prices.get(5).setClosingPrice(new BigDecimal("110.00"));
        prices.get(10).setClosingPrice(new BigDecimal("120.00"));
        prices.get(20).setClosingPrice(new BigDecimal("130.00"));

        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("BUY");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        assertTrue(result);

        // 5d: (110 - 100) / 100 * 100 = +10.00%
        assertNotNull(record.getForwardReturn5d());
        assertEquals(10.00, record.getForwardReturn5d().doubleValue(), 0.01);
        assertTrue(record.getWasAccurate5d());

        // 10d: (120 - 100) / 100 * 100 = +20.00%
        assertNotNull(record.getForwardReturn10d());
        assertEquals(20.00, record.getForwardReturn10d().doubleValue(), 0.01);
        assertTrue(record.getWasAccurate10d());

        // 20d: (130 - 100) / 100 * 100 = +30.00%
        assertNotNull(record.getForwardReturn20d());
        assertEquals(30.00, record.getForwardReturn20d().doubleValue(), 0.01);
        assertTrue(record.getWasAccurate20d());
    }

    @Test
    void testMarkRecord_SellAccurate() {
        prices.get(5).setClosingPrice(new BigDecimal("90.00"));
        prices.get(10).setClosingPrice(new BigDecimal("80.00"));
        prices.get(20).setClosingPrice(new BigDecimal("70.00"));

        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("SELL");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        assertTrue(result);

        // 5d: (90 - 100) / 100 * 100 = -10.00%
        assertNotNull(record.getForwardReturn5d());
        assertEquals(-10.00, record.getForwardReturn5d().doubleValue(), 0.01);
        assertTrue(record.getWasAccurate5d());
    }

    @Test
    void testMarkRecord_StrongBuy() {
        prices.get(5).setClosingPrice(new BigDecimal("105.00"));

        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("STRONG BUY");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        assertTrue(result);
        assertTrue(record.getWasAccurate5d());
    }

    @Test
    void testMarkRecord_BuyInaccurate() {
        prices.get(5).setClosingPrice(new BigDecimal("95.00"));

        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("BUY");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        assertTrue(result);
        assertFalse(record.getWasAccurate5d());
    }

    @Test
    void testMarkRecord_SellInaccurate() {
        prices.get(5).setClosingPrice(new BigDecimal("105.00"));

        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("SELL");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        assertTrue(result);
        assertFalse(record.getWasAccurate5d());
    }

    @Test
    void testMarkRecord_HoldMarksNullAccuracy() {
        prices.get(5).setClosingPrice(new BigDecimal("110.00"));

        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("HOLD");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        assertTrue(result);
        assertNull(record.getWasAccurate5d());
        assertNotNull(record.getForwardReturn5d());
    }

    @Test
    void testMarkRecord_NotEnoughForwardPrices() {
        prices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            DailyPrice dp = new DailyPrice();
            dp.setPriceDate(LocalDate.of(2026, 1, 1).plusDays(i));
            dp.setClosingPrice(new BigDecimal("100.00"));
            prices.add(dp);
        }

        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("BUY");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        // signal is at index 0, need 5 forward prices → only have indices 0,1,2 → fails
        assertFalse(result);
    }

    @Test
    void testMarkForwardAccuracy_NoUnmarked() {
        when(signalRecordRepository.findUnmarkedRecordsOlderThan(any()))
                .thenReturn(List.of());

        scheduler.markForwardAccuracy();

        verify(signalRecordRepository).findUnmarkedRecordsOlderThan(any());
        verifyNoMoreInteractions(signalRecordRepository);
    }

    @Test
    void testMarkForwardAccuracy_ProcessesUnmarked() {
        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("BUY");

        when(signalRecordRepository.findUnmarkedRecordsOlderThan(any()))
                .thenReturn(List.of(record));
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);
        when(signalRecordRepository.save(record)).thenReturn(record);

        scheduler.markForwardAccuracy();

        verify(signalRecordRepository).save(record);
        assertNotNull(record.getForwardReturn5d());
    }

    @Test
    void testMarkForwardAccuracy_HandlesNullPriceGracefully() {
        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setRecommendation("BUY");
        // priceAtSignal is null → markRecord returns false, caught by try-catch

        when(signalRecordRepository.findUnmarkedRecordsOlderThan(any()))
                .thenReturn(List.of(record));

        scheduler.markForwardAccuracy();

        // Should not throw; record not saved
        verify(signalRecordRepository, never()).save(record);
    }

    @Test
    void testMarkRecord_NullClosingPriceForward() {
        prices.get(5).setClosingPrice(null);

        SignalRecord record = new SignalRecord();
        record.setStockId(1L);
        record.setRecordedAt(LocalDate.of(2026, 1, 1));
        record.setPriceAtSignal(new BigDecimal("100.00"));
        record.setRecommendation("BUY");

        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);

        boolean result = scheduler.markRecord(record);
        // Should still try 10d and 20d
        assertTrue(result);
        // 5d was skipped (null closing price), 10d+20d should work
        assertNull(record.getForwardReturn5d());
        assertNotNull(record.getForwardReturn10d());
        assertNotNull(record.getForwardReturn20d());
    }
}
