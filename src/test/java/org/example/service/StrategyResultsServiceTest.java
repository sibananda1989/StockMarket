package org.example.service;

import org.example.dto.ConsensusStockDTO;
import org.example.dto.StrategyCountDTO;
import org.example.dto.StrategyStockResultDTO;
import org.example.entity.Stock;
import org.example.entity.StrategyConfig;
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

    @Mock
    private StrategyConfigService strategyConfigService;

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

        // Display-name + active lookup (lenient; only reached by getCounts/refreshAll).
        when(strategyConfigService.getAllConfigs()).thenReturn(List.of(
                new StrategyConfig("RSI", true, "RSI Strategy", 7),
                new StrategyConfig("MACD", true, "MACD Strategy", 5),
                new StrategyConfig("VOLUME", true, "Volume Strategy", 5),
                new StrategyConfig("EMA_CROSSOVER", true, "EMA 20/50 Cross", 7),
                new StrategyConfig("BOLLINGER", false, "Bollinger Band", 6)
        ));
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
        when(engine.evaluate(eq(1L), anySet())).thenReturn(agg(
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

        when(engine.evaluate(eq(1L), anySet())).thenThrow(new RuntimeException("no price data"));
        when(engine.evaluate(eq(2L), anySet())).thenReturn(agg(
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
        when(engine.evaluate(eq(1L), anySet())).thenReturn(agg(
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.9, "oversold", "RSI", 7),
                StrategyResult.withoutContribution(StrategySignal.BUY, 0.8, "bullish", "MACD", 5),
                StrategyResult.withoutContribution(StrategySignal.SELL, 0.7, "overbought", "VOLUME", 5),
                StrategyResult.withoutContribution(StrategySignal.HOLD, 0.0, "disabled", "BOLLINGER", 6)));

        List<StrategyCountDTO> counts = service.getCounts();

        verify(repository).saveAll(anyList()); // auto-seed ran a refresh
        assertEquals(4, counts.size());
        assertEquals(1, counts.get(0).buyCount()); // RSI priority 7 sorts first
        assertEquals("RSI", counts.get(0).strategyName());
        assertEquals("RSI Strategy", counts.get(0).displayName()); // friendly label resolved from config
        assertEquals(7, counts.get(0).priority());
        // active flag resolved from config: RSI enabled, BOLLINGER disabled
        assertTrue(counts.get(0).active()); // RSI active
        StrategyCountDTO boll = counts.stream().filter(c -> c.strategyName().equals("BOLLINGER")).findFirst().orElseThrow();
        assertFalse(boll.active());         // BOLLINGER disabled
        assertEquals(0, boll.buyCount());
        assertEquals(0, boll.sellCount());
        assertEquals(1, boll.holdCount());
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

    // ── getConsensus ─────────────────────────────────────────────────────────

    private void seedConsensusDay() {
        LocalDate d = LocalDate.now();
        // Stock 1: 3 BUY (RSI, MACD, VOLUME) + 1 HOLD (EMA)
        store.put(key(row(1, "RSI", "BUY", d)), row(1, "RSI", "BUY", d));
        store.put(key(row(1, "MACD", "BUY", d)), row(1, "MACD", "BUY", d));
        store.put(key(row(1, "VOLUME", "BUY", d)), row(1, "VOLUME", "BUY", d));
        store.put(key(row(1, "EMA_CROSSOVER", "HOLD", d)), row(1, "EMA_CROSSOVER", "HOLD", d));
        // Stock 2: 1 BUY
        store.put(key(row(2, "RSI", "BUY", d)), row(2, "RSI", "BUY", d));
        // Stock 3: 2 SELL
        store.put(key(row(3, "MACD", "SELL", d)), row(3, "MACD", "SELL", d));
        store.put(key(row(3, "VOLUME", "SELL", d)), row(3, "VOLUME", "SELL", d));

        Stock s1 = stock(1, "AAA");
        s1.setName("Alpha Ltd");
        Stock s2 = stock(2, "BBB");
        s2.setName("Beta Ltd");
        Stock s3 = stock(3, "CCC");
        s3.setName("Gamma Ltd");
        stocks.add(s1);
        stocks.add(s2);
        stocks.add(s3);
    }

    @Test
    void getConsensus_ranksByAgreementCountThenConfidenceThenSymbol() {
        seedConsensusDay();

        List<ConsensusStockDTO> buys = service.getConsensus("BUY", true, 50);

        assertEquals(2, buys.size());
        assertEquals("AAA", buys.get(0).symbol(), "3 agreeing strategies outrank 1");
        assertEquals(3, buys.get(0).agreeCount());
        assertEquals("Alpha Ltd", buys.get(0).name());
        assertEquals("BBB", buys.get(1).symbol());
        assertEquals(1, buys.get(1).agreeCount());
    }

    @Test
    void getConsensus_separatesBuyFromSell() {
        seedConsensusDay();

        List<ConsensusStockDTO> sells = service.getConsensus("SELL", true, 50);

        assertEquals(1, sells.size());
        assertEquals("CCC", sells.get(0).symbol());
        assertEquals(2, sells.get(0).agreeCount());
        assertEquals("SELL", sells.get(0).signal());
    }

    @Test
    void getConsensus_reportsDenominatorOfStrategiesThatProducedAVerdict() {
        seedConsensusDay();

        List<ConsensusStockDTO> buys = service.getConsensus("BUY", true, 50);

        // Stock 1 has 4 rows total (3 BUY + 1 HOLD); only 3 agreed on BUY.
        assertEquals(4, buys.get(0).totalStrategies());
        assertEquals(3, buys.get(0).agreeCount());
        // Stock 2 has 1 row, which agreed.
        assertEquals(1, buys.get(1).totalStrategies());
    }

    @Test
    void getConsensus_listsTheAgreeingStrategiesWithDisplayNames() {
        seedConsensusDay();

        List<ConsensusStockDTO> buys = service.getConsensus("BUY", true, 50);

        List<String> names = buys.get(0).strategyNames();
        assertTrue(names.containsAll(List.of("RSI", "MACD", "VOLUME")));
        assertTrue(buys.get(0).strategyDisplayNames().contains("RSI Strategy"));
        assertFalse(buys.get(0).strategyDisplayNames().contains("EMA 20/50 Cross"),
                "HOLD strategies must not appear as agreeing");
    }

    @Test
    void getConsensus_capsRowsAtRequestedLimit() {
        seedConsensusDay();

        assertEquals(1, service.getConsensus("BUY", true, 1).size());
    }

    @Test
    void getConsensus_clampsLimitToTheConfiguredMaximum() {
        seedConsensusDay();
        // Only 2 BUY rows exist, so seed enough agreeing stocks to exceed the cap.
        for (long id = 10; id <= 10 + StrategyResultsService.CONSENSUS_MAX_ROWS; id++) {
            store.put(key(row(id, "RSI", "BUY", LocalDate.now())), row(id, "RSI", "BUY", LocalDate.now()));
            stocks.add(stock(id, "SYM" + id));
        }

        List<ConsensusStockDTO> buys = service.getConsensus("BUY", true, 999);

        assertEquals(StrategyResultsService.CONSENSUS_MAX_ROWS, buys.size(),
                "an oversized limit must clamp to CONSENSUS_MAX_ROWS, not the full match set");
        assertEquals(10, StrategyResultsService.CONSENSUS_MAX_ROWS,
                "consensus cap is deliberately small — the table is a shortlist, not a full ranking");
    }

    @Test
    void getConsensus_excludesDisabledStrategiesWhenIncludeInactiveIsFalse() {
        LocalDate d = LocalDate.now();
        // BOLLINGER is configured disabled in setUp().
        store.put(key(row(1, "RSI", "BUY", d)), row(1, "RSI", "BUY", d));
        store.put(key(row(1, "BOLLINGER", "BUY", d)), row(1, "BOLLINGER", "BUY", d));
        stocks.add(stock(1, "AAA"));

        assertEquals(2, service.getConsensus("BUY", true, 50).get(0).agreeCount());
        assertEquals(1, service.getConsensus("BUY", false, 50).get(0).agreeCount(),
                "disabled BOLLINGER must be excluded");
    }

    @Test
    void getConsensus_treatsHoldsAsNonAgreement() {
        LocalDate d = LocalDate.now();
        store.put(key(row(1, "RSI", "HOLD", d)), row(1, "RSI", "HOLD", d));
        store.put(key(row(1, "MACD", "HOLD", d)), row(1, "MACD", "HOLD", d));
        stocks.add(stock(1, "AAA"));

        assertTrue(service.getConsensus("BUY", true, 50).isEmpty(),
                "HOLD is non-directional and must never appear as agreement");
        assertTrue(service.getConsensus("SELL", true, 50).isEmpty());
    }

    @Test
    void getConsensus_rejectsNonDirectionalSignal() {
        seedConsensusDay();

        assertThrows(IllegalArgumentException.class, () -> service.getConsensus("HOLD", true, 50));
        assertThrows(IllegalArgumentException.class, () -> service.getConsensus(null, true, 50));
    }

    @Test
    void getConsensus_emptyWhenNoSnapshotExists() {
        assertTrue(service.getConsensus("BUY", true, 50).isEmpty());
    }
}
