# SPEC — Standalone "Strategy Results" Page

Status: Draft (Define phase)
Owner: Define Agent
Date: 2026-08-05
Related: multi-strategy engine (`strategy/engine/MultiStrategySignalEngine.java`), `StrategyResult`, `AggregatedSignalResult`

---

## 1. Objective

Build a standalone **Strategy Results** page that shows, for every strategy in the multi-strategy engine, how many stocks (across **all stocks in the DB**) that strategy rated BUY / SELL / HOLD. Clicking a strategy name expands the row to list the BUY and SELL stocks (HOLD hidden) with each stock's reason text.

**For whom**: the user (stock buyer) who wants a cross-sectional view of what each strategy is saying about the whole universe, not just individual stocks.

**Data model decision (confirmed)**: per-strategy results are **not persisted today** and **must not be computed live on page load**. The page reads a **stored snapshot** from a new entity/table that keeps **one snapshot per day** (rows keyed by `stock_id` + `strategy_name` + `snapshot_date`; history accumulates across days). A manual Refresh **overwrites only the current day's rows**; the page always displays the **latest snapshot** (`max(snapshot_date)`). Auto-computed once on first visit when the table is empty. No scheduled job.

**Done criteria (measurable)**:
- Sidebar link "Strategy Results" navigates to the page.
- Page loads and shows one row per strategy (all 10 current strategies + any new ones like SMA44) with BUY / SELL / HOLD counts matching a known sample of stocks.
- Clicking a strategy name expands to show BUY and SELL stocks with their reason text; HOLD stocks are absent from the expansion.
- Refresh button recomputes all stocks through the engine and persists today's snapshot, overwriting today's earlier rows; page shows updated counts for the latest snapshot date after completion.
- First visit with an empty snapshot table auto-computes and shows data.
- Unit tests added for the new service; existing tests still pass.

---

## 2. Requirements

### MUST have
1. New page reachable from the sidebar (navigation.js registration), served from `src/main/resources/static/strategy-results.html` + `strategy-results.js` (IIFE, no globals, per project convention).
2. Table with columns: **Strategy | BUY count | SELL count | HOLD count**. One row per strategy name present in the snapshot.
3. Row expansion: clicking the strategy **name** toggles a nested list of the BUY and SELL stocks only, each showing symbol + reason text. HOLD stocks are never shown in the expansion.
4. Persistence: new entity `StrategyStockResult` (table `strategy_stock_result`) storing per-stock per-strategy per-day: `stock_id`, `strategy_name`, `snapshot_date` (LocalDate — the day this snapshot belongs to), `signal_type` (column name — `signal` is a MySQL reserved word), `reason`, `confidence`, `priority`, `computed_at`. Unique per `(stock_id, strategy_name, snapshot_date)`.
5. Refresh: POST endpoint that loops all stocks (`StockRepository.findAll()`), calls `MultiStrategySignalEngine.evaluate(stockId)` for each, persists the `breakdown` list (`StrategyResult`) as rows stamped with `snapshot_date = today`, first deleting **only today's existing rows** (same-day overwrite — no cross-day deletion, no accumulation within a day).
6. Read endpoints: GET aggregate counts per strategy; GET rows for one strategy (expansion). Both reflect the **latest snapshot** (`max(snapshot_date)`) only.
7. First-visit seeding: if the snapshot table is empty when the aggregate GET is called, seed today's snapshot (trigger a refresh synchronously) before returning.
8. Snapshot freshness model: manual refresh only. No scheduled job.
9. All endpoints return `ApiResponse<T>` (`status`/`message`/`data`/`timestamp`).
10. Signal bucketing rule: strategies emit `BUY`/`SELL`/`HOLD` today (see `toBreakdownDTO` switch), but the mapping layer must defensively map `STRONG_BUY → BUY` and `STRONG_SELL → SELL` so any future strong signal still lands in a count bucket.

### SHOULD have
1. Loading state on the page while the refresh (or first-visit seed) is in progress; refresh button disabled during refresh to prevent double clicks.
2. Confidence shown alongside reason in the expansion (reference value; engine provides it).
3. Ordering: strategy rows sorted by `priority` (descending) then name, so the most influential strategies appear first.

### MUST NOT
1. No live evaluation on page load — the page reads the stored snapshot only.
2. No scheduled/cron job for refresh.
3. No charts, sector filters, or extra sections on this page ("nothing is required for now").
4. Do **not** reuse `SignalRecord` (legacy composite) or the legacy `SignalService` 15-factor path — this feature is scoped to the multi-strategy engine only.
5. No modification to `docs/PROJECT_MANIFEST.md` (Ship phase owns that).
6. No new libraries/dependencies.

---

## 3. Architecture Notes

### New files

| File | Purpose |
|------|---------|
| `src/main/java/org/example/entity/StrategyStockResult.java` | JPA entity (see data model) |
| `src/main/java/org/example/repository/StrategyStockResultRepository.java` | Spring Data repo: `findAllBySnapshotDate(...)`, `findAllByStrategyNameAndSnapshotDate(...)`, `deleteBySnapshotDate(LocalDate)` (derived `deleteBy*` — caller must be `@Transactional`), `findMaxSnapshotDate()` (`@Query("SELECT MAX(s.snapshotDate) ...")`), plus CrudRepository basics (`findAll()`, `saveAll()`, `count()`) |
| `src/main/java/org/example/dto/StrategyCountDTO.java` | Record: `strategyName, buyCount, sellCount, holdCount, priority` |
| `src/main/java/org/example/dto/StrategyStockResultDTO.java` | Record: `stockId, symbol, signal, confidence, reason` (used in expansion) |
| `src/main/java/org/example/service/StrategyResultsService.java` | Core: `getCounts()`, `getStocksFor(strategyName)`, `refreshAll()` |
| `src/main/java/org/example/controller/StrategyResultsController.java` | 3 endpoints (below) |
| `src/main/resources/static/strategy-results.html` | Page shell (table + refresh button) |
| `src/main/resources/static/strategy-results.js` | IIFE module: fetch, render, expand/collapse, refresh |
| `src/main/resources/static/js/api.js` | 3 new wrappers (below) |
| `src/main/resources/static/js/navigation.js` | 1 new nav entry |
| `src/test/java/org/example/service/StrategyResultsServiceTest.java` | Unit tests (mock engine + repo) |

### Data model — `strategy_stock_result`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `stock_id` | BIGINT | FK to `stock` (plain column, no JPA relation needed) |
| `strategy_name` | VARCHAR(50) NOT NULL | e.g. `RSI`, `MACD`, ... `SMA44` |
| `snapshot_date` | DATE NOT NULL | the day this snapshot belongs to (`LocalDate.now()` at refresh); history accumulates one snapshot per day |
| `signal_type` | VARCHAR(20) NOT NULL | `BUY`/`SELL`/`HOLD` (mapped from `StrategySignal`; `STRONG_*` bucketed). Column named `signal_type` because `signal` is a MySQL reserved word |
| `reason` | TEXT | reason text from `StrategyResult.reason()` |
| `confidence` | DOUBLE | reference value from `StrategyResult.confidence()` |
| `priority` | INT | reference value from `StrategyResult.priority()` |
| `computed_at` | DATETIME | snapshot timestamp (`LocalDateTime.now()` at refresh) |

Index (via `@Table(indexes=...)` or left to Hibernate): `(snapshot_date, strategy_name, signal_type)` supports latest-day reads (`MAX(snapshot_date)` + filter) and expansion queries. Unique constraint `(stock_id, strategy_name, snapshot_date)` guards against duplicates within a day's snapshot.

Entity follows `StrategyConfig.java` conventions: `@Entity`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`, timestamps set manually (no `@CreationTimestamp` needed — snapshot time is a business value, not audit).

### Service behavior

**`refreshAll()`** (annotated `@Transactional` — required because it calls the derived `deleteBySnapshotDate` query):
1. `LocalDate today = LocalDate.now()`; `List<Stock> stocks = stockRepository.findAll()` — if empty, delete today's rows (no-op) and return a zero summary.
2. For each stock: `AggregatedSignalResult agg = engine.evaluate(stock.getId())`; for each `StrategyResult sr` in `agg.breakdown()`, map to a `StrategyStockResult` (bucketing `STRONG_BUY→BUY`, `STRONG_SELL→SELL`, else as-is) stamped with `snapshotDate = today`, and collect.
3. `strategyResultsRepository.deleteBySnapshotDate(today)` then `saveAll(collected)` — **same-day overwrite**: only today's rows are replaced; previous days' history is untouched. Re-refreshing the same day produces the identical day-snapshot (idempotent, no accumulation, no unique-violation risk).
4. Return a summary (rows written, stock count, per-strategy counts, `snapshotDate`).
5. `engine.evaluate(stockId)` throws `ResourceNotFoundException` when a stock has no price data — catch per-stock, log a warning, skip that stock (a stock with no prices contributes no rows; see Edge Cases).

**`getCounts()`**:
- If `strategyResultsRepository.count() == 0` → call `refreshAll()` first (first-visit seeding of today).
- `LocalDate latest = strategyResultsRepository.findMaxSnapshotDate()`; load only `latest`'s rows (`findAllBySnapshotDate(latest)`) and aggregate in memory by `(strategy_name, signal_type)` into `List<StrategyCountDTO>` sorted by `priority` desc then name. (N = stocks × strategies per day; in-memory grouping is fine at this scale. 

**`getStocksFor(strategyName)`**:
- Auto-seed first if the table is empty; `LocalDate latest = findMaxSnapshotDate()`; `findAllByStrategyNameAndSnapshotDate(strategyName, latest)`, map to `StrategyStockResultDTO` (join stock symbol via a `StockRepository` lookup or map of id→symbol built once), return all signals — the frontend filters out HOLD rows. One endpoint serves the expansion without needing a second contract if the UI changes.

### Endpoint contracts (all wrap in `ApiResponse<T>`)

**`GET /api/strategy-results`** — aggregate counts for the latest snapshot day (auto-seeds if table empty).
```json
{ "status": "success", "message": "...", "timestamp": "...",
  "data": [
    { "strategyName": "RSI",   "buyCount": 12, "sellCount": 3,  "holdCount": 8,  "priority": 5 },
    { "strategyName": "MACD",  "buyCount": 4,  "sellCount": 11, "holdCount": 8,  "priority": 5 }
  ] }
```

**`GET /api/strategy-results/{strategyName}`** — rows for one strategy from the latest snapshot day (expansion; frontend hides HOLD).
```json
{ "status": "success", "message": "...", "timestamp": "...",
  "data": {
    "strategyName": "RSI",
    "stocks": [
      { "stockId": 1, "symbol": "RELIANCE", "signal": "BUY",  "confidence": 0.8, "reason": "RSI below 30, oversold" },
      { "stockId": 5, "symbol": "TCS",      "signal": "SELL", "confidence": 0.7, "reason": "RSI above 70, overbought" }
    ] } }
```

**`POST /api/strategy-results/refresh`** — recompute + persist today's snapshot (overwrites only today's rows).
```json
{ "status": "success", "message": "...", "timestamp": "...",
  "data": { "snapshotDate": "2026-08-05", "rowsWritten": 230, "stocksEvaluated": 23, "computedAt": "2026-08-05T10:30:00Z" } }
```
The page re-fetches `GET /api/strategy-results` after refresh completes to redraw counts.

### Frontend

- **`strategy-results.html`**: page shell reusing the existing theme (`css/theme.css`, `css/styles.css`); a refresh button (top-right), a loading indicator, and a `<table id="strategy-results-table">` with thead `Strategy | BUY | SELL | HOLD`.
- **`strategy-results.js`** (IIFE):
  - `load()` → `getStrategyResults()` → render rows; first row count == 0 shows an empty-state message.
  - Each strategy name is a `<a>`/button toggling a hidden expanded row (`<tr class="strategy-expand">` with a nested table or list) → lazy-fetch `getStrategyResultsFor(name)` on first expansion, cache in closure.
  - Expansion renders only `signal === 'BUY' || signal === 'SELL'` rows: `symbol` + `signal` badge + `reason` (+ confidence).
  - Refresh button → disabled + spinner → `refreshStrategyResults()` → re-`load()`.
- **`js/api.js`** wrappers (following existing multi-strategy wrapper style, lines ~633-648 of api.js):
  ```js
  function getStrategyResults() { return apiCall('/strategy-results'); }
  function getStrategyResultsFor(strategyName) { return apiCall(`/strategy-results/${encodeURIComponent(strategyName)}`); }
  function refreshStrategyResults() { return apiCall('/strategy-results/refresh', { method: 'POST' }); }
  ```
- **`js/navigation.js`**: add entry alongside the existing `strategy.html` entry (line 6):
  ```js
  { href: 'strategy-results.html', label: 'Strategy Results', icon: 'fas fa-chart-bar' },
  ```

---

## 4. Edge Cases

| Case | Handling |
|------|----------|
| **Empty DB (no stocks)** | `refreshAll()` writes 0 rows; page shows an empty state ("No stocks in database"). |
| **Strategy with no stocks rated** | Row still appears with `0/0/0` counts (strategy names come from the snapshot's distinct names — if a strategy never fired for any stock it won't appear at all; acceptable, and a zero row appears once any stock produces that strategy with HOLD). |
| **Duplicate refresh / idempotency (same-day overwrite)** | `deleteBySnapshotDate(today)` + `saveAll` inside one `@Transactional` — re-refreshing the same day replaces only that day's rows; prior days' history untouched; no accumulation, no unique-violation errors. |
| **History growth over days** | Table grows by (stocks × strategies) rows per day — small at current scale (e.g., ~230 rows/day for ~23 stocks × 10 strategies). Index on `snapshot_date` keeps latest-day reads fast; future trend/date-range features can read older days, but no date picker on this page for now. |
| **Stock with missing price data** | `engine.evaluate(stockId)` throws `ResourceNotFoundException` → catch per-stock, log warning, skip. Stock simply contributes no rows this run. |
| **Missing indicators** | Engine already handles this: computes indicators on the fly (`indicatorComputationService.computeIndicators`) — no extra work in the new service. Cost: slower refresh for stocks without persisted indicators. |
| **Performance of N evaluations** | Refresh is O(N) engine evaluations; each may compute indicators on the fly for indicator-less stocks. First refresh (esp. with auto-seed) may take seconds to tens of seconds. Mitigations: (a) synchronous is acceptable for this scale; (b) loading spinner + disabled button; (c) note the existing `SIGNAL_HISTORY_POOL` pattern as the upgrade path if refresh becomes too slow — parallelize the stock loop with a bounded pool. |
| **`signal` reserved word** | Column is `signal_type`; entity field `signalType` maps via `@Column(name = "signal_type")`. |
| **Refresh in progress / double-click** | Button disabled + spinner while the POST is in flight; `@Transactional` on the service method prevents interleaved same-day deletes. |
| **STRONG_BUY / STRONG_SELL from future strategies** | Bucketed into BUY/SELL at mapping time so counts stay 3-bucket. |
| **Strategy names with spaces/special chars** | `encodeURIComponent` on the frontend; `@PathVariable` on the backend. |

---

## 5. Dependencies

- **`MultiStrategySignalEngine`** (`strategy/engine/`) — `evaluate(stockId)` per stock; no engine changes required (no batch method needed — the loop lives in the new service).
- **`AggregatedSignalResult.breakdown()`** — source of `StrategyResult` rows.
- **`StockRepository.findAll()`** — stock universe for refresh.
- **`StrategyConfigService`** (optional, for display names/priorities) — can be used to sort rows by priority; fallback to the snapshot's own `priority` column if not needed.
- **`StrategyStockResultRepository`** — needs derived `deleteBySnapshotDate(LocalDate)` (project gotcha: callers of derived `deleteBy*` queries must be `@Transactional` — `refreshAll()` already is), `findAllBySnapshotDate(...)` / `findAllByStrategyNameAndSnapshotDate(...)`, and `findMaxSnapshotDate()` (`@Query("SELECT MAX(s.snapshotDate) FROM StrategyStockResult s")`).
- **No new libraries.** Hibernate `ddl-auto=update` creates the table automatically; no manual migration.

---

## 6. Open Questions

1. **Async vs synchronous refresh**: spec defaults to synchronous (simple, adequate at current scale). If the stock universe grows large enough that a refresh takes > ~30s, switch the POST to kick off an async job and poll for completion. Deferred — not blocking.
2. **Auto-seed concurrency**: two simultaneous first-visit GETs could both trigger `refreshAll()`. The same-day overwrite design makes this benign (last writer wins, no unique violations), and `@Transactional` + a simple in-process guard (`AtomicBoolean refreshing`) removes the redundancy. Deferred — not blocking.

---

## 7. Success Criteria

1. `docs/STRATEGY_RESULTS_SPEC.md` exists (this file); sidebar shows **Strategy Results** and it loads `strategy-results.html`.
2. Table lists all current strategies (RSI, MACD, MA_CROSSOVER, BOLLINGER, VOLUME, CANDLESTICK, BREAKOUT, CANDLESTICK_AT_SUPPORT, CANDLESTICK_AT_RESISTANCE, LIQUIDITY) — and any newly added ones (e.g., SMA44) automatically, since rows derive from the latest snapshot.
3. Counts are correct: verified against a known sample of stocks (e.g., pick 2–3 stocks, run `GET /api/signals/multi-strategy/{id}/breakdown` manually, and confirm the page's counts match the sum of those breakdowns).
4. Clicking a strategy name expands to BUY + SELL stocks with symbols and reason text; no HOLD rows appear.
5. Clicking Refresh returns updated `snapshotDate`/`rowsWritten`/`stocksEvaluated`; page re-renders with fresh counts for the latest day; refresh button is disabled during the operation; re-refreshing the same day overwrites today's rows (count stays flat — no duplicates).
6. First visit with an empty `strategy_stock_result` table: `GET /api/strategy-results` auto-seeds today's snapshot and returns counts.
7. `mvn test` passes — existing ~99 tests plus new `StrategyResultsServiceTest` (mock engine: verify rows persisted with correct bucketing, same-day overwrite on re-refresh while prior-day rows are retained, skip-on-no-price-data; counts aggregation reflects only the latest `snapshot_date`).
