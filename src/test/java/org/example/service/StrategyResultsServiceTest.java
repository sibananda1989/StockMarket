package org.example.service;

import org.example.dto.StrategyCountDTO;
import org.example.dto.StrategyStockResultDTO;
import org.example.entity.Stock;
import org.example.entity.StrategyStockResult;
import org.example.repository.StockRepository;
import org.example.repository.StrategyStockResultRepository;
import org.example.strategy.engine.MultiStrategySignalEngine;
import org.example.strategy.model.AggregatedSignalResult;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StrategyResultsService}.
 *
 * The repository is backed by an in-memory fake store so the service's real logic
 * (snapshot writes, same-day overwrite, latest-day aggregation, auto-seed) is
 * exercised deterministically without requiring a live MySQL database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StrategyResultsServiceTest {

    @Mock
    private StrategyStockResultRepository repository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private MultiStrategySignalEngine engine;

    @InjectMocks
    private StrategyResultsService service;

    // In-memory fake store simulating repository persistence.
    // Keyed by stockId|strategyName|snapshotDate (mirrors the unique constraint).
    private Map<String, StrategyStockResult> store = new ConcurrentHashMap<>();

    private List<Stock> stocks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store = new ConcurrentHashMap<>();
        stocks = new ArrayList<>();

        when(repository.count()).thenAnswer(inv -> (long) store.size());
        when(repository.findAllBySnapshotDate(any(LocalDate.class))).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            return store.values().stream()
                    .filter(r -> date.equals(r.getSnapshotDate()))
                    .toList();
        });
        when(repository.findAllByStrategyNameAndSnapshotDate(anyString(), any(LocalDate.class))).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            LocalDate date = inv.getArgument(1);
            return store.values().stream()
                    .filter(r -> name.equals(r.getStrategyName()) && date.equals(r.getSnapshotDate()))
                    .toList();
        });
        when(repository.findMaxSnapshotDate()).thenAnswer(inv -> store.values().stream()
                .map(StrategyStockResult::getSnapshotDate)
                .max(Comparator.naturalOrder())
                .orElse(null));
        doAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            store.entrySet().removeIf(e -> date.equals(e.getValue().getSnapshotDate()));
            return null;
        }).when(repository).deleteBySnapshotDate(any(LocalDate.class));
        when(repository.saveAll(anyList())).thenAnswer(inv -> {
            List<StrategyStockResult> list = inv.getArgument(0);
            for (StrategyStockResult r : list) {
                store.put(key(r), copy(r));
            }
            return list;
        });

        when(stockRepository.findAll()).thenAnswer(inv -> new ArrayList<>(stocks));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Stock stock(long id, String symbol) {
        Stock s = new Stock();
        s.setId(id);
        s.setSymbol(symbol);
        return s;
    }

    private String key(StrategyStockResult r) {
        return r.getStockId() + "|" + r.getStrategyName() + "|" + r.getSnapshotDate();
    }

    private StrategyStockResult row(long stockId, String strategy, String signal, LocalDate date) {
        StrategyStockResult r = new StrategyStockResult();
        r.setStockId(stockId);
        r.setStrategyName(strategy);
        r.setSignalType(signal);
        r.setSnapshotDate(date);
        r.setPriority(5);
        return r;
    }

    private StrategyStockResult copy(StrategyStockResult r) {
        StrategyStockResult c = new StrategyStockResult();
        c.setId(r.getId());
        c.setStockId(r.getStockId());
        c.setStrategyName(r.getStrategyName());
        c.setSignalType(r.getSignalType());
        c.setReason(r.getReason());
        c.setConfidence(r.getConfidence());
        c.setPriority(r.getPriority());
        c.setSnapshotDate(r.getSnapshotDate());
        c.setEventDate(r.getEventDate());
        c.setComputedAt(r.getComputedAt());
        return c;
    }

    private AggregatedSignalResult agg(StrategyResult... breakdown) {
        return new AggregatedSignalResult(StrategySignal.HOLD, 0.0, List.of(breakdown), 20,
                0.0, List.of(), List.of(), Map.of(), Map.of());
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void refreshAll_persistsRowsWithCorrectSignalTypeAndSameDayOverwrite() {
        // Prior-day history must be retained across refreshes.
        LocalDate yesterday = LocalDate.now().minusDays(1);
        store.put("1|RSI|" + yesterday, row(1, "RSI", "BUY", yesterday));

        stocks.add(stock(1, "RELIANCE"));
        when(engine.evaluate(1L)).thenReturn(agg(
                StrategyResult.withoutContributionWithEventDate(StrategySignal.BUY, 0.9, "RSI oversold", "RSI", 7, LocalDate.of(2026, 8, 27)),
                StrategyResult.withoutContribution(StrategySignal.SELL, 0.6, "MACD bearish", "MACD", 5)));

        service.refreshAll();

        // Same-day overwrite: today's rows deleted before re-insert
        verify(repository).deleteBySnapshotDate(LocalDate.now());

        // saveAll received exactly the 2 mapped rows with AS-IS signal names
        verify(repository).saveAll(argThat(rows -> {
            List<StrategyStockResult> list = (List<StrategyStockResult>) rows;
            return list.size() == 2
                    && list.get(0).getSignalType().equals("BUY")
                    && list.get(1).getSignalType().equals("SELL")
                    && list.get(0).getStrategyName().equals("RSI")
                    && list.get(0).getStockId() == 1L
                    && LocalDate.now().equals(list.get(0).getSnapshotDate())
                    && LocalDate.of(2026, 8, 27).equals(list.get(0).getEventDate())
                    && list.get(1).getEventDate() == null; // state-based strategy carries no event date
        }));

        // Prior-day row retained; today's rows present exactly once each (no accumulation)
        assertTrue(store.values().stream().anyMatch(r -> yesterday.equals(r.getSnapshotDate())),
                "prior-day history must be retained");
        assertEquals(2, store.values().stream().filter(r -> LocalDate.now().equals(r.getSnapshotDate())).count());
        assertEquals(3, store.size());

        // Re-refreshing the same day overwrites rather than accumulates
        service.refreshAll();
        assertEquals(2, store.values().stream().filter(r -> LocalDate.now().equals(r.getSnapshotDate())).count());
        assertTrue(store.values().stream().anyMatch(r -> yesterday.equals(r.getSnapshotDate())));
    }

    @Test
    void refreshAll_skipsStocksWhoseEvaluationThrows() {
        stocks.add(stock(1, "BROKEN"));
        stocks.add(stock(2, "HEALTHY"));

        when(engine.evaluate(1L)).thenThrow(new RuntimeException("no price data"));
        when(engine.evaluate(2L)).thenReturn(agg(
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.8, "good", "RSI", 7)));

        var summary = service.refreshAll();

        assertEquals(1, summary.stocksEvaluated());
        assertEquals(1, summary.rowsWritten());
        assertEquals(1, store.size());
        assertEquals(2L, store.values().iterator().next().getStockId());
        assertEquals(LocalDate.now(), summary.snapshotDate());
    }

    @Test
    void getCounts_autoSeedsWhenEmpty() {
        stocks.add(stock(1, "RELIANCE"));
        when(engine.evaluate(1L)).thenReturn(agg(
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.9, "oversold", "RSI", 7),
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.8, "bullish", "MACD", 5),
                StrategyResult.withoutContribution(StrategySignal.SELL, 0.7, "overbought", "VOLUME", 5)));

        List<StrategyCountDTO> counts = service.getCounts();

        verify(repository).saveAll(anyList()); // auto-seed ran a refresh
        assertEquals(3, counts.size());
        assertEquals(1, counts.get(0).buyCount()); // RSI priority 7 sorts first
        assertEquals("RSI", counts.get(0).strategyName());
        assertEquals(7, counts.get(0).priority());
    }

    @Test
    void getCounts_aggregatesOnlyLatestDay() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // Yesterday: RSI had 2 BUYs. Today: RSI has 1 BUY, 1 SELL — only today's should count.
        store.put("1|RSI|" + yesterday, row(1, "RSI", "BUY", yesterday));
        store.put("2|RSI|" + yesterday, row(2, "RSI", "BUY", yesterday));
        store.put("1|RSI|" + LocalDate.now(), row(1, "RSI", "BUY", LocalDate.now()));
        store.put("2|RSI|" + LocalDate.now(), row(2, "RSI", "SELL", LocalDate.now()));

        List<StrategyCountDTO> counts = service.getCounts();

        assertEquals(1, counts.size());
        StrategyCountDTO rsi = counts.get(0);
        assertEquals(1, rsi.buyCount());
        assertEquals(1, rsi.sellCount());
        assertEquals(0, rsi.holdCount());
    }

    @Test
    void getStocksFor_returnsLatestDayRowsWithSymbolsAndFiltersNothing() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        store.put("1|RSI|" + yesterday, row(1, "RSI", "SELL", yesterday));
        store.put("1|RSI|" + LocalDate.now(), row(1, "RSI", "BUY", LocalDate.now()));
        StrategyStockResult withEvent = row(2, "RSI", "HOLD", LocalDate.now());
        withEvent.setEventDate(LocalDate.of(2026, 8, 27));
        store.put("2|RSI|" + LocalDate.now(), withEvent);

        stocks.add(stock(1, "RELIANCE"));
        stocks.add(stock(2, "TCS"));

        List<StrategyStockResultDTO> rows = service.getStocksFor("RSI");

        // Only today's rows; HOLD included (the frontend filters HOLD, not the service)
        assertEquals(2, rows.size());
        StrategyStockResultDTO buy = rows.stream().filter(r -> r.signal().equals("BUY")).findFirst().orElseThrow();
        assertEquals(1L, buy.stockId());
        assertEquals("RELIANCE", buy.symbol());
        StrategyStockResultDTO hold = rows.stream().filter(r -> r.signal().equals("HOLD")).findFirst().orElseThrow();
        assertEquals("TCS", hold.symbol());
        // eventDate round-trips from the snapshot row into the DTO
        assertEquals(LocalDate.of(2026, 8, 27), hold.eventDate());
        assertNull(buy.eventDate()); // rows without an event date map to null
        assertFalse(rows.stream().anyMatch(r -> r.symbol().isEmpty()), "all symbols resolved from stock repo");
    }

    @Test
    void getStocksFor_unknownStrategyReturnsEmpty() {
        store.put("1|RSI|" + LocalDate.now(), row(1, "RSI", "BUY", LocalDate.now()));
        stocks.add(stock(1, "RELIANCE"));

        assertTrue(service.getStocksFor("NOPE").isEmpty());
    }

    @Test
    void getCounts_emptyStoreAfterRefreshReturnsEmpty() {
        // No stocks → refreshAll writes nothing → findMaxSnapshotDate returns null → empty list
        List<StrategyCountDTO> counts = service.getCounts();
        assertTrue(counts.isEmpty());
    }
}
