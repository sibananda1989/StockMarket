package org.example.service;

import org.example.dto.EmaCrossResponseDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmaCrossScreenerServiceTest {

    private DailyPriceRepository dailyPriceRepository;
    private StockRepository stockRepository;
    private EmaCrossScreenerService service;

    // 5 consecutive trading dates. D4 is the latest (anchor) date.
    private final LocalDate D0 = LocalDate.of(2026, 7, 1);
    private final List<LocalDate> WINDOW = List.of(D0, D0.plusDays(1), D0.plusDays(2), D0.plusDays(3), D0.plusDays(4));
    private final LocalDate ANCHOR = D0.plusDays(4);

    @BeforeEach
    void setUp() {
        dailyPriceRepository = mock(DailyPriceRepository.class);
        stockRepository = mock(StockRepository.class);
        service = new EmaCrossScreenerService(dailyPriceRepository, stockRepository);

        when(stockRepository.findAll()).thenReturn(List.of(stock(1L, "XYZ", "XYZ Ltd"), stock(2L, "SHORT", "Shallow Co")));

        // Trading calendar = WINDOW. Coverage: D1..D4 complete (2 stocks), D0 partial (1).
        when(dailyPriceRepository.findDistinctTradingDatesAfter(any(LocalDate.class))).thenReturn(WINDOW);
        when(dailyPriceRepository.countByPriceDate(eq(D0))).thenReturn(1L);
        when(dailyPriceRepository.countByPriceDate(eq(D0.plusDays(1)))).thenReturn(2L);
        when(dailyPriceRepository.countByPriceDate(eq(D0.plusDays(2)))).thenReturn(2L);
        when(dailyPriceRepository.countByPriceDate(eq(D0.plusDays(3)))).thenReturn(2L);
        when(dailyPriceRepository.countByPriceDate(eq(D0.plusDays(4)))).thenReturn(2L);
    }

    private Stock stock(Long id, String symbol, String name) {
        Stock s = new Stock();
        s.setId(id);
        s.setSymbol(symbol);
        s.setName(name);
        return s;
    }

    /**
     * Stub the single batched price fetch. For each stockId, build closes.length bars
     * ending on ANCHOR and put them into the returned list (the real query returns
     * every stock's rows in one result set).
     */
    private void stubPrices(Map<Long, List<Double>> closesByStock) {
        List<DailyPrice> all = new ArrayList<>();
        for (Map.Entry<Long, List<Double>> e : closesByStock.entrySet()) {
            List<Double> closes = e.getValue();
            LocalDate first = ANCHOR.minusDays(closes.size() - 1L);
            Stock ref = stock(e.getKey(), "S" + e.getKey(), "N" + e.getKey());
            for (int i = 0; i < closes.size(); i++) {
                all.add(new DailyPrice(ref, BigDecimal.valueOf(closes.get(i)), first.plusDays(i)));
            }
        }
        when(dailyPriceRepository.findPricesForStockIdsSince(eq(List.of(1L, 2L)), any(LocalDate.class))).thenReturn(all);
    }

    /**
     * A >=50-bar close series that makes EMA-20 cross above EMA-50 exactly on the
     * LAST bar: flat at 100 for all bars except the final one, which jumps +20%
     * (below the 30% split threshold). On the second-to-last bar EMA-20 == EMA-50
     * (~100); on the last bar EMA-20 rises faster than EMA-50 => up-cross at anchor.
     */
    private List<Double> rampClosesCrossingAtEnd() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 69; i++) closes.add(100.0); // flat base
        closes.add(120.0);                              // last: +20% -> cross
        return closes;
    }

    private List<Double> flatCloses(int n) {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < n; i++) closes.add(100.0);
        return closes;
    }

    @Test
    void findsUpCrossInWindow() {
        stubPrices(new HashMap<>(Map.of(1L, rampClosesCrossingAtEnd(), 2L, List.of(100.0, 101.0, 102.0))));

        EmaCrossResponseDTO result = service.findEmaCrosses(5);

        assertTrue(result.getSignals().stream().anyMatch(s -> s.getStockId().equals(1L)),
                "Expected an up-cross signal for stock 1");
        assertEquals(ANCHOR, result.getAnchorDate());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("SHORT")),
                "Shallow stock 2 should warn about insufficient history");
        assertEquals(1, result.getUniverseScanned());
    }

    @Test
    void splitJumpRejected() {
        List<Double> closes = rampClosesCrossingAtEnd();
        closes.set(closes.size() - 1, closes.get(closes.size() - 2) * 1.4); // last: +40% jump
        stubPrices(new HashMap<>(Map.of(1L, closes)));

        EmaCrossResponseDTO result = service.findEmaCrosses(5);

        assertFalse(result.getSignals().stream().anyMatch(s -> s.getStockId().equals(1L)),
                "Split-artifact cross must be rejected");
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("split jump")),
                "Rejection should surface a warning");
    }

    @Test
    void shallowHistoryProducedNoSignalAndWarning() {
        stubPrices(new HashMap<>(Map.of(1L, List.of(100.0, 101.0, 102.0))));
        EmaCrossResponseDTO result = service.findEmaCrosses(5);
        assertTrue(result.getSignals().isEmpty());
        assertEquals(0, result.getUniverseScanned());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("XYZ")),
                "Shallow stock should warn about insufficient history");
    }

    @Test
    void flatPricesProduceNoCross() {
        stubPrices(new HashMap<>(Map.of(1L, flatCloses(70))));
        EmaCrossResponseDTO result = service.findEmaCrosses(5);
        assertTrue(result.getSignals().isEmpty());
        assertEquals(1, result.getUniverseScanned());
    }
}