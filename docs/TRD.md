# Technical Requirements Document (TRD) — Stock Market Analysis Platform

**Version:** 1.0
**Date:** 2026-09-21
**Status:** Active
**Supersedes:** BRD.md (business requirements) — this TRD defines the *technical* design.
**Related:** `docs/PROJECT_MANIFEST.md` (file inventory), `docs/SIGNAL_ENGINE_SPEC.md`, `docs/API_SPEC.md`

---

## 1. Purpose

Define the technical architecture, components, data model, interfaces, schedulers, and
non-functional requirements for the Stock Market Analysis Platform — a Spring Boot 3.2.5
monolith serving Indian (NSE/BSE) stock technical analysis.

---

## 2. System Context

| Item | Value |
|------|-------|
| Application | `stockmarket` (Maven artifact) |
| Entry point | `org.example.StockTrackerApplication` (`@EnableScheduling`, `@EnableCaching`, `@EnableRetry`, `@Async`) |
| Runtime | Java 17, Spring Boot 3.2.5 |
| Port | 8080 |
| Database | MySQL 8+ (`localhost:3306/stockmarket`, user `root`/`root`) |
| Cache | Spring Caffeine (15-min TTL, 500-entry cap, 10 named caches) |
| Build | Maven (`mvn`), JaCoCo 0.8.11 coverage |
| Tests | JUnit 5 + Mockito (86 files, `src/test`), Playwright e2e (`package.json`) |
| Docs API | springdoc OpenAPI UI 1.6.14 |

**Stack:** Spring Web, Spring Data JPA/Hibernate 6.x, Spring Retry + Aspects, Resilience4j 2.2.0,
Spring WebFlux `WebClient`, Spring Boot Actuator + Micrometer Prometheus, Lombok,
Netty DNS resolver (macOS), Thymeleaf.

---

## 3. Architecture

### 3.1 Style
Layered monolith: **Controller → Service → Repository → Entity**. Universal response
envelope `ApiResponse<T>` = `{status, message, data, timestamp}`. No authentication;
CORS allows all origins on `/api/**`. DDL via `spring.jpa.hibernate.ddl-auto=update`.

### 3.2 Package Layout (`org.example`)

```
StockTrackerApplication.java          # Entry point
CacheConfig.java                       # 10 Caffeine caches (15-min TTL, max 500)
config/  CorsConfig, ClientAbortSilencerFilter, ExecutorConfig
controller/                            # 28 controllers (27 @RestController + 1 @Controller)
dto/                                   # 69 DTOs (incl. nested classes)
entity/                                # 31 entities (28 classes + 3 enums)
exception/                             # 7 custom exceptions + GlobalExceptionHandler
metrics/                               # InstitutionalMetrics (Micrometer)
repository/                            # 27 Spring Data JPA repositories
scheduler/                             # 10 schedulers (11 @Scheduled jobs)
startup/                               # 7 startup tasks
service/                               # 42 services
  calculator/                          # 35 indicator calculators (1 interface + 33 impls + 1 orchestration)
  institutional/                       # 8 institutional services
  smc/                                 # 6 SMC services
strategy/                              # 18 files: base, model, engine, aggregator, config, impl (11)
```

### 3.3 Key Patterns
- **EAV for indicators:** single `technical_indicators` table, 31 `IndicatorType` values.
- **Strategy engine:** 12 strategy beans (11 classes, `CandlestickContext` ×2) aggregated
  by `StrategySignalAggregator` with weighted scoring + confidence + fail-safe.
- **Async:** `ExecutorConfig` defines 4 pools — `syncExecutor` (core 2 / max 4 / queue 20),
  `startupTaskExecutor` (6/8/20), `backfillExecutor` (4/8/100, `@Lazy`), `taskExecutor`
  (2/4/20, `@Primary`).
- **Resilience4j:** circuit breakers on `nseSession` + `nseXbrl` (50% failure threshold,
  30s open), `timelimiter` 30s.
- **View controller:** `HomeController` is the only `@Controller` (Thymeleaf); all others `@RestController`.
---

## 4. Database Schema (MySQL 8)

**Connection:** `jdbc:mysql://localhost:3306/stockmarket?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true`
**Pool:** HikariCP `StockMarketPool`, max 20, min idle 5, 30s timeout.
**DDL:** `spring.jpa.hibernate.ddl-auto=update` (manual migration scripts alongside).

### 4.1 Enums
- **`IndicatorType`** (VARCHAR(50)) — 31 values: RSI, SMA_20/50/200/44, EMA_20, MACD_LINE, MACD_SIGNAL, BOLLINGER_UPPER/LOWER, STOCH_K/D, WILLIAMS_R, ATR, CCI, STOCH_RSI, ADX, PLUS_DI, MINUS_DI, ULTIMATE_OSC, ROC_12, OBV, VWAP, TENKAN_SEN, KIJUN_SEN, SENKOU_SPAN_A/B, CHIKOU_SPAN, VOLUME_RATIO, DOLLAR_VOLUME, AMIHUD_ILLIQUIDITY. `isDirectional()`.
- **`LevelType`** (VARCHAR(30)) — 11 values: SWING_LOW/HIGH, PIVOT_P/S1/S2/S3/R1/R2/R3, MAJOR_SUPPORT/RESISTANCE.
- **`TransactionType`** — BUY, SELL.

### 4.2 Entities (21 + 1 `@ElementCollection`)

| Entity | Key fields / constraints |
|--------|--------------------------|
| `stocks` | `symbol` UNIQUE; **legacy** portfolio fields (quantity, avg_price, pnl, etc.) kept for backward compat |
| `daily_prices` | UK `(stock_id, price_date)`; CHECK `volume >= 0`; ~91K rows |
| `technical_indicators` | UK `(stock_id, indicator_type, calculation_date)`; EAV; **744K rows, 60MB data** |
| `signal_records` | UK `(stock_id, recorded_at)`; ~86K rows |
| `strategy_daily_weight` | UK `(stock_id, strategy_name, signal_date)`; ~82K rows |
| `portfolio_snapshots` | UK `(portfolio_id, stock_id, snapshot_date)`; ~34K rows |
| `shadow_signal_records` | UK `(stock_id, recorded_at, shadow_version)`; 47 scoring fields; A/B comparison |
| `shadow_signal_gate_block_reasons` | `@ElementCollection List<String>` of gate block reasons |
| `fundamental_data` | unique `stock_id`; `business_summary` TEXT |
| `support_resistance_levels` | UK `(stock_id, level_type, calculation_date, level_order)` |
| `institutional_holdings` | UK `(stock_id, quarter_end_date)`; QoQ deltas |
| `bulk_deals` / `block_deals` | identical schema; screener index `(buy_sell, is_institutional, deal_date)` |
| `corporate_events` | UK `(symbol, event_date, purpose)`; symbol is String (no FK) |
| `fiidii_data` | unique `date` |
| `watchlists` / `watchlist_items` | UK `(watchlist_id, stock_id)` |
| `portfolios` / `portfolio_holdings` / `portfolio_transactions` | avg-cost lot tracking; `linked_buy_id` self-ref |
| `portfolio_daily_values` | UK `(portfolio_id, value_date)` |
| `strategy_config` | unique `strategy_name`; 12 auto-seeded active |
| `strategy_condition_groups` | UK `(strategy_name, condition_id)`; ~60 default conditions |
| `strategy_condition_stats_cache` | UK `(strategy_name, condition_id)`; async-computed match counts |
| `strategy_stock_result` | UK `(stock_id, strategy_name, snapshot_date)` |
| `signal_historical_performance` | index `(recommendation, days_forward)` |
| `score_parameter_config` | unique `param_key`; toggleable scoring factors |
| `startup_task_log` | task run history |

**Relationships:** `Stock` 1→* `DailyPrice`, `TechnicalIndicator`, `PortfolioHolding`,
`PortfolioSnapshot`, `FundamentalData` (unique FK), `SupportResistanceLevel`,
`InstitutionalHolding`, `BulkDeal`, `BlockDeal`. `Watchlist` 1→* `WatchlistItem`.
`Portfolio` 1→* `PortfolioHolding`, `PortfolioSnapshot`, `PortfolioTransaction`,
`PortfolioDailyValue`. `SignalRecord`/`ShadowSignalRecord` use plain `stock_id` (no JPA relationship).

### 4.3 Indexing
All FK columns are indexed (MySQL auto-indexes FKs). Hot composite indexes:
`(stock_id, price_date)`, `(stock_id, indicator_type, calculation_date)`,
`(stock_id, recorded_at)`, `(stock_id, strategy_name, signal_date)`,
`(portfolio_id, stock_id, snapshot_date)`. EXPLAIN confirms index usage on representative queries.

### 4.4 Known Risks
- **Unbounded growth** — `technical_indicators` (744K, ~3.5K rows/day), `strategy_daily_weight`,
  `signal_records`, `daily_prices`. No partitioning/archival yet.
- **Buffer pool** (128MB) < total data (~130MB); increase as data grows.
- **Slow query log OFF** — enable `slow_query_log`, `long_query_time=2`, `log_output=TABLE`.
- `mysql.slow_log` is a CSV table (no DELETE/lock); rotate via MySQL restart or cron.

---

## 5. REST API

**Base:** `/api` (from `js/api.js:1`). **Envelope:** `ApiResponse<T>` on all endpoints
(except `TechnicalIndicatorController`, which returns raw POJOs).

### 5.1 Endpoint Inventory — 28 controllers, 161 `@Mapping` (162 incl. 1 Thymeleaf view)

| Controller | Base path | Highlights |
|------------|-----------|------------|
| `StockController` | `/api/stocks` | CRUD, search, sectors, CSV import, portfolio recalc, snapshots, delete-by-prefix |
| `StockHistoryController` | `/api/stocks` | Yahoo sync + summary (8 endpoints) |
| `StockSyncController` | `/api/stocks` | Backfill, portfolio sync |
| `PortfolioController` | `/api/portfolio` | History, screener, backfill, daily-values |
| `PortfolioManagementController` | `/api/portfolios` | Multi-portfolio CRUD + holdings + transactions + lots |
| `SignalController` | `/api/signals` | All/buy/sell/{stockId}/history |
| `MultiStrategySignalController` | `/api/signals/multi-strategy` | Signal, breakdown, compare, history |
| `WatchlistController` | `/api/watchlists` | CRUD + items + batch + sync |
| `WatchlistOpportunityController` | `/api/watchlist` | Ranked opportunities for portfolio holdings |
| `TechnicalIndicatorController` | `/api/indicators` | Latest, history, calc, backfill, reset (raw DTOs) |
| `PriceController` | `/api/prices` | Save, query by date range, latest |
| `SupportResistanceController` | `/api/support-resistance` | Levels, calc, history |
| `FiiDiiController` | `/api/fiidii` | FII/DII data + refresh |
| `CorporateEventController` | `/api/events` | Events + refresh |
| `InstitutionalHoldingController` | `/api/institutional` | Holdings, XBRL, deals, scores, 7 screeners |
| `FundamentalDataController` | `/api/fundamentals` | Fetch/screen/sectors |
| `SMCController` | `/api/smc` | SMC patterns (FVG) |
| `BacktestController` | `/api/backtest` | Strategy backtest `/stock/{id}` |
| `RsiController` | `/api/rsi` | DEPRECATED — use TechnicalIndicatorController |
| `StartupTaskController` | `/api/startup-tasks` | List + run tasks |
| `DataAvailabilityController` | `/api/data-availability` | Data health / coverage |
| `IndicatorCoverageController` | `/api/indicators/coverage` | Coverage gaps `/{stockId}` |
| `ScoreParameterController` | `/api/score-parameters` | Toggle scoring factors |
| `StrategyConfigController` | `/api/strategy-config` | Strategy priority/enable + conditions + stats |
| `StrategyDailyWeightController` | `/api/strategy-daily-weight` | Daily weight adj |
| `StrategyResultsController` | `/api/strategy-results` | Strategy run results + refresh |
| `EmaCrossScreenerController` | `/api/screener/ema-cross` | EMA-20↑50 cross within N days (cached) |
| `HomeController` | — | Thymeleaf view `GET /` |

**Method breakdown:** GET 89, POST 53, PUT 8, PATCH 3, DELETE 8.

### 5.2 Frontend (15 HTML + 28 JS)
Pages: dashboard, stock-detail, stock-management, watchlist, institutional-dashboard,
strategy, rsi-analysis, price-entry, stock-history, portfolio-transactions,
fundamentals-screener, history-summary, strategy-results, watchlist-opportunities,
ema-cross-screener. Shared: `api.js` (~150 wrappers), `navigation.js`, `js/modules/` (7 dirs),
`css/theme.css`, `css/styles.css`, vendor bundles (chartjs-adapter, chartjs-plugin-annotation).
Charts: Chart.js 4.4.0, Lightweight Charts 4.2.0 (candlestick), Font Awesome 6.4.

---

## 6. Signal Engine (CORE)

`SignalService.java` (2,990 lines) — 19-factor weighted scoring → composite score →
recommendation. Cached via Caffeine (10–15 min TTL).

### 6.1 Scoring Factors (in `computeWeightedScore()`)
Divergence, Weekly Confluence, Monthly Confluence, RSI(14), Bollinger Bands, MACD Crossover,
Stochastic %K/%D, StochRSI, Ultimate Oscillator, ROC(12), Williams %R, CCI, OBV,
Price vs SMA20/SMA50, 52-Week Proximity, ADX Trend Filter, Volume Confirmation, S/R Proximity,
VWAP, Ichimoku Cloud, Trend Cap, Breakout Score, Compound Overbought, Candlestick Patterns,
Reversal Detection, plus FII/DII adjustment and post-scoring modifiers.

### 6.2 Thresholds (`SignalThresholds.java`)
| Recommendation | Score |
|---------------|-------|
| STRONG_BUY | ≥ 7 |
| BUY | ≥ 3 |
| HOLD | −3 to +2 |
| SELL | ≤ −4 |
| STRONG_SELL | ≤ −7 |

**ADX multiplier:** <15→×0.3; 15–25→0.5+0.02×(ADX−15); 25–35→0.7+0.01×(ADX−25); ≥35→×1.0;
counter-trend ×0.7. **Bearish discount:** 0.85 default; 0.9 minimal / 1.0 full reversal; floor 0.9.

### 6.3 Confidence (0–100)
`base = 50 + compositeScore×5` (clamped) + volume bonus − event risk − confluence −
stale-data penalty − 3/missing indicator + historical accuracy bonus/penalty.

### 6.4 Trade Metrics
Stop = entry ∓ 2×ATR; Target = entry ± 3×ATR; Position size = 20/volatility, clamped 1–5%.

### 6.5 Shadow Signal System
A/B comparison (`ShadowSignalService`, `ShadowSignalRecord`, `application-shadow.properties`).
Run with `--spring.profiles.active=shadow` to compare old vs new logic accuracy.

---

## 7. Strategy Engine

`org.example.strategy/` — 18 files. `TradingStrategy.evaluate(indicator, prices) → StrategyResult`.
12 beans (11 impl classes; `CandlestickContextStrategy` ×2 at-support/at-resistance):
Rsi, Macd, MovingAverageCrossover, BollingerBand, Volume, Breakout, CandlestickPattern,
CandlestickContext(×2), Sma44PullbackBounce, Liquidity, EmaCrossover.

**Priorities (`application.properties`):** rsi 7, macd 7, ma-crossover 8, bollinger 6,
volume 5, candlestick.at-support 4 / at-resistance 4 / pattern 5, liquidity 3, sma44 8,
ema-crossover 7. Aggregator buy threshold 3.0 / sell −3.0. `StrategyConfigService` auto-seeds
all 12 as active on first startup.

---

## 8. Schedulers & Startup (17 total)

**Schedulers (10):** `MarketAnalysisScheduler` (weekly Yahoo backfill + daily indicator calc),
`InstitutionalHoldingScheduler` (SUN 4:30 + MON–FRI 12:30), `SignalAccuracyScheduler` (daily 3:00),
`DailySnapshotScheduler` (MON–FRI 5:45 UTC), `FundamentalDataScheduler` (SUN 8:00 UTC),
`StrategyDailyWeightScheduler` (MON–FRI 10:00), `IndicatorStartupTask`,
`SignalStartupTask`, `SellLotBackfillStartupTask`, `SignalPerformanceScheduler`.

**Startup tasks (7):** `StartupDataSyncTask`, `FiiDiiStartupTask`, `FundamentalStartupTask`,
`PortfolioMigrationStartupTask`, `SignalAccuracyStartupTask`, `SignalCacheInitializer`,
`StartupTask` (base). 11 `@Scheduled` jobs total.

---

## 9. Frontend Architecture

15 HTML pages (Tailwind CSS, dark/light theme) + 28 JS files (16 top-level + 7 modules +
5 others). Shared `navigation.js` injects nav into every page via `#navigation` placeholder.
`api.js` wraps all 161 endpoints. Modules: `backtest`, `charts` (candlestick, line-charts),
`components` (events, fiidii, holding-details, kpi), `data` (loading), `insights` (fundamentals, index),
`state`, `watchlist`. `index.html` owns dashboard (P&L, signals table, 3 charts);
`stock-detail.html` owns 6 charts + AI insights + backtest + institutional tab.

---

## 10. Non-Functional Requirements

| Area | Requirement | Implementation |
|------|-------------|----------------|
| **Availability** | Single instance, dev tool | `spring-boot-maven-plugin` repackaging |
| **Performance** | Cache hot paths | Caffeine 10 caches, 15-min TTL, max 500 |
| **Resilience** | NSE API failures | Resilience4j circuit breakers (nseSession, nseXbrl), retry |
| **Observability** | Metrics + logs | Actuator (health/info/metrics/prometheus), Micrometer counters/timers, `logs/stockmarket.log` |
| **Security** | No auth (dev) | CORS all origins; exception messages sanitized (CWE-117 log injection) |
| **Async** | Parallel backfill | 4 `ThreadPoolTaskExecutor` pools |
| **CORS** | `/api/**` | `CorsConfig` |
| **Broken pipe** | Client aborts | `ClientAbortSilencerFilter` |

---

## 11. Build, Test & CI

- **Build:** `mvn -o compile` / `mvn -o package` (spring-boot-maven-plugin, excludes Lombok).
- **Unit tests:** JUnit 5 + Mockito, 86 files under `src/test`, JaCoCo 0.8.11 coverage.
- **E2E tests:** Playwright (`npm test`), `@playwright/test ^1.40.0`.
- **Coverage gate:** JaCoCo report on `mvn test`.
- **Lint/Typecheck:** none configured (JS is vanilla; no ESLint/Prettier in repo).
- **Pre-commit hook:** `scripts/hooks/pre-commit`.

---

## 12. Known Issues & Technical Debt

1. **Unbounded table growth** — `technical_indicators` (744K, ~3.5K rows/day), `strategy_daily_weight`,
   `signal_records`, `daily_prices`. No partitioning/archival. Primary long-term risk.
2. **Buffer pool** (128MB) < total data (~130MB) — increase as data grows.
3. **Slow query log OFF** — enable to diagnose regressions.
4. **Stale cardinality stats** — run `ANALYZE TABLE` on large tables periodically.
5. **`stocks` legacy portfolio fields** — duplicate of `portfolio_holdings`; two sources of truth.
6. **`strategy_condition_groups`** — no index on `strategy_name` (0 rows now, add before load).
7. **`mysql.slow_log`** — CSV table; cannot `DELETE`/`TRUNCATE` via locks; rotate on restart.
8. **No JS lint/format** — 28 JS files, 8.7K+ lines, unformatted.
9. **`SignalComparisonController.java.bak`** — stale backup file tracked in git.
10. **`database.sqlite`** — 0-byte leftover, no SQLite driver; dead artifact.

---

## 13. Configuration Reference

| Key | Value | Where |
|-----|-------|-------|
| Server port | 8080 | `application.properties:1` |
| JDBC URL | `localhost:3306/stockmarket` | `application.properties:3` |
| DB creds | `root`/`root` (env-overridable) | `application.properties:5-6` |
| Hikari max | 20 | `application.properties:35` |
| Cache spec | max 2000, expireAfterWrite 10m | `application.properties:93` |
| Cache names | signals, signalDto, latestIndicators, indicatorHistory, supportResistanceLevels, emaCross | `application.properties:80` |
| Strategy priorities | 12 values | `application.properties:49-62` |
| Signal thresholds | SBUY 7, BUY 3, SELL −4, SSELL −7 | `SignalThresholds.java` |
| Log file | `/Users/sibanandasahoo/Documents/projects/stockmarket/logs/stockmarket.log` | `application.properties:76` |
| Actuator | health, info, metrics, prometheus | `application.properties:23` |
| Async pools | sync 2/4/20, startup 6/8/20, backfill 4/8/100, task 2/4/20 | `ExecutorConfig.java` |
| Shadow profile | `application-shadow.properties` | `ShadowSignalConfig` |

---

## 14. Document Map

| Doc | Scope |
|-----|-------|
| `BRD.md` | Business requirements |
| `docs/PROJECT_MANIFEST.md` | File inventory (controllers, services, entities) |
| `docs/SIGNAL_ENGINE_SPEC.md` | Signal scoring detail |
| `docs/API_SPEC.md` | Endpoint contracts |
| `docs/DATABASE_DESIGN.md` | Schema design |
| `docs/BACKTESTING_RULES.md` | Backtest rules |
| `docs/PRODUCT_VISION.md` | Product scope |
| `docs/AI_TEAM_GUIDELINES.md` | Agent workflow rules |
| `TRD.md` | **This document** — full technical design |
