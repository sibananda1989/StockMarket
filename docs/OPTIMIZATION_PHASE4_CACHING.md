# OPTIMIZATION Phase 4 — Signals Response Caching

Status: implemented and verified (2026-10-03)

## Problem

`GET /api/signals?portfolioId=` re-computed multi-strategy signals on every request:

- Live measurement (fresh instance): **50.6 s cold**, RCA sample **58.4 s**
- All other dashboard endpoints: < 0.2 s — this single call caused the ~58 s dashboard load
- A Caffeine `signals` cache was already registered (`CacheConfig.java`) but never used by the service

## Solution — Phase 1, Option A

Cache the endpoint result (TTL Option A: accept the global Caffeine backstop, all current
writers evict explicitly).

### Cache read

| Method | Annotation |
|---|---|
| `SignalService.getSignalsForPortfolio(portfolioId)` | `@Cacheable(cacheNames = "signals", key = "#portfolioId", sync = true)` |

`sync = true` provides single-flight: concurrent misses compute once.

### Cache invalidation (all writers)

| Site | Annotation |
|---|---|
| `TechnicalIndicatorPersistenceService.saveOrUpdateIndicator` | `@CacheEvict(signals, allEntries)` — **single choke point** for every indicator write |
| `DailyPriceService.saveDailyPrice` (both overloads) | `@CacheEvict(signals, allEntries)` |
| `YahooFinanceSyncService.syncHistory` | `@CacheEvict(signals, allEntries)` |
| `StockSplitDetector.detectAndFixSplits` | `@CacheEvict(signals, allEntries)` |
| `FiiDiiService.fetchAndSave` + `scheduledFetch` | `@CacheEvict(signals, allEntries)` (both annotated: `@Scheduled` self-invocation bypasses the proxy) |
| `CorporateEventService.fetchAndSaveEvents` + `scheduledRefresh` | `@CacheEvict(signals, allEntries)` (same reason) |
| `PortfolioManagementController.recalculate` | `@CacheEvict({signals, signalDto}, allEntries)` |
| `StockController.recalculateAllPortfolios` | `@CacheEvict({signals, signalDto}, allEntries)` |

**Plan deviation (intentional):** the plan listed 5 evict sites inside
`TechnicalAnalysisService`; all indicator writes funnel through
`TechnicalIndicatorPersistenceService.saveOrUpdateIndicator`, so one annotation there
covers the same paths with less duplication.

### TTL / sizing

Global Caffeine manager (`CacheConfig.java`): 15 min, max 500 entries — acts as a
backstop only; normal freshness comes from the explicit evictions above.

## Verification

| Gate | Result |
|---|---|
| `mvn test-compile` | PASS |
| New tests (`SignalControllerIntegrationTest`) | 2/2 pass — cache reuse (`assertSame`, no recompute) + writer eviction |
| Full `mvn test` | 967 tests, 0 failures, 13 errors, 2 skipped — errors are only the pre-existing `PortfolioChartControllerIntegrationTest` baseline |
| Live, fresh instance (:8081) | cold **50.6 s** → warm **8 ms** (≈6,200×) |
| Live eviction round-trip | warm 19 ms → `POST /api/stocks/recalculate-portfolio` → next GET **52.7 s** (real recompute) → **7.4 ms** (re-cached) |

## Testing note

`TechnicalIndicatorPersistenceService` uses `Propagation.REQUIRES_NEW`. Inside
`@Transactional` integration tests, `clearAllData`'s DELETEs hold FK-parent locks on
`stocks`, so any indicator INSERT from a test waits the full
`innodb_lock_wait_timeout` (50 s) — a Java-level stall MySQL does not deadlock out of.
Eviction tests therefore trigger `detectAndFixSplits` (same `@CacheEvict` wiring, no
`REQUIRES_NEW` path) instead of `saveDailyPrice`.

## Future work (not in this phase)

- Stale-while-revalidate for instant warm misses
- Unify `getAllSignals`'s manual volatile cache (`SignalService`, never evicted)
- Frontend: parallelize the 3-wave waterfall with `Promise.all`
