# Business Requirements Document — Stock Market Analysis Platform

## 1. Project Overview

| Field | Detail |
|-------|--------|
| **Project Name** | Stock Market Analysis Platform |
| **Type** | Full-stack monolithic web application |
| **Framework** | Spring Boot 3.2.5 (Java 17) |
| **Build Tool** | Maven |
| **Database** | MySQL 8+ |
| **Frontend** | Static HTML + Tailwind CSS + Vanilla JS + Chart.js |
| **Purpose** | Personal stock portfolio management and technical analysis for Indian stock market (NSE/BSE) investors |
| **Server Port** | 8080 |

---

## 2. Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Backend | Java | 17 |
| Framework | Spring Boot | 3.2.5 |
| ORM | Spring Data JPA / Hibernate | 6.x (Jakarta) |
| Database | MySQL | 8+ |
| Cache | Spring Cache + Caffeine | 15-min TTL, 500 max |
| Retry | Spring Retry + Spring Aspects | — |
| Async | `@Async` + `ThreadPoolTaskExecutor` | core=2, max=4, queue=20 |
| Circuit Breaker | Resilience4j | 2.2.0 |
| Monitoring | Spring Boot Actuator + Micrometer Prometheus | — |
| API Docs | springdoc OpenAPI UI | 1.6.14 |
| HTTP Client | Spring WebFlux `WebClient` | — |
| Frontend | Tailwind CSS + Chart.js 4.4.0 + Font Awesome 6.4 + Lightweight Charts 5.2.0 | CDN |
| Testing | JUnit 5 + Mockito + Playwright | — |
| Code Coverage | JaCoCo | 0.8.11 |
| Utilities | Lombok, Netty DNS | — |

---

## 3. System Architecture

### 3.1 Package Structure

```
org.example
├── StockTrackerApplication.java          # Entry point (@EnableScheduling, @EnableCaching, @EnableRetry, @EnableAsync)
├── CacheConfig.java                       # 6 Caffeine caches (15-min TTL)
├── config/
│   ├── CorsConfig.java                    # CORS: all origins on /api/**
│   └── ClientAbortSilencerFilter.java     # Broken-pipe exception handler
├── controller/                            # 18 controllers (17 @RestController + 1 @Controller) + 1 .bak
├── dto/                                   # 47 Data Transfer Objects (including nested classes)
├── entity/                                # 22 JPA entities + 2 enums
├── exception/                             # 6 custom exceptions + GlobalExceptionHandler
├── metrics/                               # InstitutionalMetrics (Micrometer counters/timers)
├── repository/                            # 22 Spring Data JPA repositories
├── scheduler/                             # 7 scheduled task classes (12 cron jobs + startup indicators)
├── startup/                               # 6 startup tasks
└── service/                               # 32 services (active) + 3 .bak
    ├── calculator/                        # 29 technical indicator calculators (1 interface + 28 impls)
    └── institutional/                     # 8 institutional activity services
```

### 3.2 Architectural Patterns

- **Layered architecture:** Controller → Service → Repository → Entity
- **Universal response envelope:** `ApiResponse<T>` wraps all responses with `{status, message, data, timestamp}`
- **Exception:** `TechnicalIndicatorController` returns raw DTOs (not wrapped in `ApiResponse`)
- **EAV pattern for indicators:** Single `technical_indicators` table stores all 27 indicator types via `IndicatorType` enum
- **No authentication:** All endpoints are public; CORS allows all origins
- **DDL auto-management:** `spring.jpa.hibernate.ddl-auto=update` with manual SQL migration scripts
- **View controller:** `StockHistoryViewController` is the only `@Controller` (returns Thymeleaf view); all others are `@RestController`

---

## 4. Database Schema

### 4.1 Database Configuration

| Setting | Value |
|---------|-------|
| RDBMS | MySQL 8+ |
| JDBC URL | `jdbc:mysql://localhost:3306/stockmarket?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` |
| Username / Password | `root` / `root` |
| DDL Strategy | `spring.jpa.hibernate.ddl-auto=update` |
| Connection Pool | HikariCP (pool: `StockMarketPool`, max 15, min idle 5) |

**Note:** A 0-byte `database.sqlite` file exists at project root but is **not in use** — no SQLite driver exists in `pom.xml` and no datasource config references it. It is a leftover artifact.

### 4.2 Enums

**`IndicatorType`** (persisted as `VARCHAR(50)` in `technical_indicators.indicator_type`):
`RSI`, `SMA_20`, `SMA_50`, `SMA_200`, `EMA_20`, `MACD_LINE`, `MACD_SIGNAL`, `BOLLINGER_UPPER`, `BOLLINGER_LOWER`, `STOCH_K`, `STOCH_D`, `WILLIAMS_R`, `ATR`, `CCI`, `STOCH_RSI`, `ADX`, `PLUS_DI`, `MINUS_DI`, `ULTIMATE_OSC`, `ROC_12`, `OBV`, `VWAP`, `TENKAN_SEN`, `KIJUN_SEN`, `SENKOU_SPAN_A`, `SENKOU_SPAN_B`, `CHIKOU_SPAN` (27 values total — includes VWAP and 6 Ichimoku Cloud components)

**`LevelType`** (persisted as `VARCHAR(30)` in `support_resistance_levels.level_type`):
`SWING_LOW`, `SWING_HIGH`, `PIVOT_P`, `PIVOT_S1`, `PIVOT_S2`, `PIVOT_S3`, `PIVOT_R1`, `PIVOT_R2`, `PIVOT_R3`, `MAJOR_SUPPORT`, `MAJOR_RESISTANCE`

### 4.3 Entity Definitions

#### 4.3.1 `stocks` — Stock (Hub Entity)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `symbol` | VARCHAR(20) | NOT NULL, UNIQUE | `@NotBlank`, `@Size(max=20)` |
| `name` | VARCHAR(255) | NOT NULL | `@NotBlank` |
| `sector` | VARCHAR(100) | nullable | `@Size(max=100)` |
| `yahoo_symbol` | VARCHAR(20) | nullable | `@Size(max=20)` |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp`, non-updatable |
| `updated_at` | DATETIME | nullable | `@UpdateTimestamp` |
| `quantity` | INT | nullable | **Legacy** — moved to `portfolio_holdings` |
| `avg_price` | DECIMAL(10,2) | nullable | **Legacy** |
| `last_traded_price` | DECIMAL(10,2) | nullable | |
| `investment` | DECIMAL(12,2) | nullable | **Legacy** |
| `current_value` | DECIMAL(12,2) | nullable | **Legacy** |
| `pnl` | DECIMAL(12,2) | nullable | **Legacy** |
| `pnl_percent` | DECIMAL(8,2) | nullable | **Legacy** |
| `volume` | BIGINT | nullable | |

**Note:** Fields `quantity`, `avgPrice`, `investment`, `currentValue`, `pnl`, `pnlPercent` are **legacy** — moved to `portfolio_holdings` but still present in schema for backward compatibility.

**Relationships:**
- `@OneToMany(mappedBy="stock", cascade=ALL, orphanRemoval=true)` → `DailyPrice`
- `@OneToMany(mappedBy="stock", cascade=ALL, orphanRemoval=true)` → `TechnicalIndicator`

---

#### 4.3.2 `portfolios` — Portfolio

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `name` | VARCHAR(255) | NOT NULL | `@NotBlank` |
| `description` | VARCHAR(500) | nullable | |
| `is_default` | BIT | NOT NULL, DEFAULT false | One portfolio marked as default |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |
| `updated_at` | DATETIME | nullable | `@UpdateTimestamp` |

---

#### 4.3.3 `portfolio_holdings` — PortfolioHolding

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `portfolio_id` | BIGINT | FK → `portfolios.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `quantity` | INT | NOT NULL | |
| `avg_price` | DECIMAL(10,2) | nullable | |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp`, non-updatable |
| `updated_at` | DATETIME | nullable | `@UpdateTimestamp` |

**Unique constraint:** `(portfolio_id, stock_id)`
**Transient fields:** `investment`, `currentValue`, `pnl`, `pnlPercent`, `lastTradedPrice` (computed at read time)

---

#### 4.3.4 `portfolio_snapshots` — PortfolioSnapshot

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `portfolio_id` | BIGINT | FK → `portfolios.id`, nullable | `@ManyToOne(LAZY)` |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `snapshot_date` | DATE | NOT NULL | |
| `quantity` | INT | nullable | |
| `avg_price` | DECIMAL(10,2) | nullable | |
| `last_traded_price` | DECIMAL(10,2) | nullable | |
| `investment` | DECIMAL(12,2) | nullable | |
| `current_value` | DECIMAL(12,2) | nullable | |
| `pnl` | DECIMAL(12,2) | nullable | |
| `pnl_percent` | DECIMAL(8,2) | nullable | |
| `volume` | BIGINT | nullable | |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(portfolio_id, stock_id, snapshot_date)`
**Indexes:** `idx_snapshot_stock_date` on `(stock_id, snapshot_date DESC)`, `idx_snapshot_portfolio_date` on `(portfolio_id, snapshot_date)`

---

#### 4.3.5 `daily_prices` — DailyPrice

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)`, `@NotNull` |
| `closing_price` | DECIMAL(10,2) | NOT NULL | `@DecimalMin("0.01")` |
| `opening_price` | DECIMAL(10,2) | nullable | `@DecimalMin("0.01")` |
| `high_price` | DECIMAL(10,2) | nullable | `@DecimalMin("0.01")` |
| `low_price` | DECIMAL(10,2) | nullable | `@DecimalMin("0.01")` |
| `volume` | BIGINT | nullable | `@Min(0)` |
| `price_date` | DATE | NOT NULL | `@NotNull`, `@PastOrPresent` |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(stock_id, price_date)`
**Index:** `idx_stock_date` on `(stock_id, price_date DESC)`

---

#### 4.3.6 `fundamental_data` — FundamentalData

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL, UNIQUE | One-to-one via unique FK |
| `pe_ratio` | DECIMAL(10,2) | nullable | |
| `forward_pe` | DECIMAL(10,2) | nullable | |
| `eps_ttm` | DECIMAL(10,2) | nullable | |
| `eps_forward` | DECIMAL(10,2) | nullable | |
| `book_value` | DECIMAL(10,2) | nullable | |
| `price_to_book` | DECIMAL(10,2) | nullable | |
| `dividend_yield` | DECIMAL(8,4) | nullable | |
| `roe` | DECIMAL(8,4) | nullable | |
| `debt_to_equity` | DECIMAL(10,4) | nullable | |
| `profit_margin` | DECIMAL(8,4) | nullable | |
| `revenue_ttm` | BIGINT | nullable | |
| `sector` | VARCHAR(100) | nullable | |
| `industry` | VARCHAR(100) | nullable | |
| `business_summary` | TEXT | nullable | |
| `shares_outstanding` | BIGINT | nullable | |
| `beta` | DECIMAL(8,4) | nullable | |
| `fifty_two_week_high` | DECIMAL(10,2) | nullable | |
| `fifty_two_week_low` | DECIMAL(10,2) | nullable | |
| `fetched_date` | DATE | NOT NULL | |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |
| `updated_at` | DATETIME | nullable | `@UpdateTimestamp` |

---

#### 4.3.7 `technical_indicators` — TechnicalIndicator (EAV Pattern)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `indicator_type` | VARCHAR(50) | NOT NULL | `@Enumerated(STRING)` — one of 21 IndicatorType values |
| `value` | DECIMAL(18,6) | NOT NULL | Widened by V2 migration |
| `calculation_date` | DATE | NOT NULL | `@PastOrPresent` |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(stock_id, indicator_type, calculation_date)`
**Index:** `idx_stock_indicator_date` on `(stock_id, indicator_type, calculation_date DESC)`

---

#### 4.3.8 `rsi_values` — RsiValue (DEPRECATED)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `rsi14` | DECIMAL(5,2) | NOT NULL | `@DecimalMin("0.00")`, `@DecimalMax("100.00")` |
| `calculation_date` | DATE | NOT NULL | `@PastOrPresent` |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(stock_id, calculation_date)`
**Index:** `idx_stock_rsi_date` on `(stock_id, calculation_date DESC)`

---

#### 4.3.9 `signal_records` — SignalRecord

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | NOT NULL | Plain FK (no JPA relationship) |
| `recorded_at` | DATE | NOT NULL | |
| `recommendation` | VARCHAR(20) | NOT NULL | `STRONG_BUY`, `BUY`, `HOLD`, `SELL`, `STRONG_SELL` |
| `composite_score` | INT | NOT NULL | |
| `confidence_score` | BIGINT | nullable | |
| `indicator_coverage` | INT | nullable | |
| `price_at_signal` | DECIMAL(14,2) | nullable | |
| `forward_return_5d` | DECIMAL(10,4) | nullable | |
| `forward_return_10d` | DECIMAL(10,4) | nullable | |
| `forward_return_20d` | DECIMAL(10,4) | nullable | |
| `was_accurate_5d` | BIT | nullable | |
| `was_accurate_10d` | BIT | nullable | |
| `was_accurate_20d` | BIT | nullable | |

**Unique constraint:** `(stock_id, recorded_at)`
**Index:** `idx_signal_record_stock_date` on `(stock_id, recorded_at DESC)`

---

#### 4.3.10 `signal_historical_performance` — SignalHistoricalPerformance

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `recommendation` | VARCHAR(20) | NOT NULL | |
| `days_forward` | INT | NOT NULL | e.g. 5, 10, 20 |
| `avg_return` | DECIMAL(10,4) | nullable | |
| `sample_size` | INT | NOT NULL | |
| `last_updated` | DATE | NOT NULL | |

**Index:** `idx_shp_rec_days` on `(recommendation, days_forward)`

---

#### 4.3.11 `support_resistance_levels` — SupportResistanceLevel

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `level_type` | VARCHAR(30) | NOT NULL | `@Enumerated(STRING)` |
| `level_value` | DECIMAL(12,4) | NOT NULL | `@DecimalMin("0.01")` |
| `level_order` | INT | NOT NULL, DEFAULT 0 | Ordering within same type |
| `calculation_date` | DATE | NOT NULL | |
| `strength_score` | DECIMAL(5,2) | nullable | |
| `touch_count` | INT | nullable | |
| `lookback_days` | INT | nullable | |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(stock_id, level_type, calculation_date, level_order)`
**Indexes:** `idx_sr_stock_date` on `(stock_id, calculation_date DESC)`, `idx_sr_stock_type` on `(stock_id, level_type)`

---

#### 4.3.12 `fiidii_data` — FiiDiiData

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `date` | DATE | NOT NULL, UNIQUE | |
| `fii_buy` | DECIMAL(18,4) | nullable | In Crores |
| `fii_sell` | DECIMAL(18,4) | nullable | |
| `fii_net` | DECIMAL(18,4) | nullable | |
| `dii_buy` | DECIMAL(18,4) | nullable | |
| `dii_sell` | DECIMAL(18,4) | nullable | |
| `dii_net` | DECIMAL(18,4) | nullable | |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

---

#### 4.3.13 `corporate_events` — CorporateEvent

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `symbol` | VARCHAR(50) | NOT NULL | Stock symbol string (not FK) |
| `company_name` | VARCHAR(255) | nullable | |
| `purpose` | VARCHAR(255) | NOT NULL | e.g. "Dividend", "AGM" |
| `bm_desc` | TEXT | nullable | Board meeting description |
| `event_date` | DATE | NOT NULL | |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(symbol, event_date, purpose)`
**Index:** `idx_event_symbol_date` on `(symbol, event_date)`

---

#### 4.3.14 `institutional_holdings` — InstitutionalHolding

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `quarter_end_date` | DATE | NOT NULL | |
| `promoter_holding_pct` | DECIMAL(8,4) | nullable | |
| `fii_holding_pct` | DECIMAL(8,4) | nullable | |
| `dii_holding_pct` | DECIMAL(8,4) | nullable | |
| `mutual_fund_holding_pct` | DECIMAL(8,4) | nullable | |
| `insurance_holding_pct` | DECIMAL(8,4) | nullable | |
| `public_holding_pct` | DECIMAL(8,4) | nullable | |
| `total_shares` | BIGINT | nullable | |
| `promoter_change_qoq` | DECIMAL(8,4) | nullable | Computed QoQ delta |
| `fii_change_qoq` | DECIMAL(8,4) | nullable | |
| `dii_change_qoq` | DECIMAL(8,4) | nullable | |
| `mutual_fund_change_qoq` | DECIMAL(8,4) | nullable | |
| `data_source` | VARCHAR(20) | nullable | `NSE_XBRL`, `NSE_API`, `COMPUTED` |
| `filing_date` | DATE | nullable | |
| `fetched_date` | DATE | nullable | |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(stock_id, quarter_end_date)`
**Index:** `idx_inst_hold_stock_date` on `(stock_id, quarter_end_date DESC)`

---

#### 4.3.15 `bulk_deals` — BulkDeal

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `deal_date` | DATE | NOT NULL | |
| `client_name` | VARCHAR(255) | NOT NULL | |
| `buy_sell` | VARCHAR(4) | NOT NULL | `BUY` or `SELL` |
| `quantity` | BIGINT | nullable | |
| `trade_price` | DECIMAL(12,2) | nullable | |
| `remarks` | VARCHAR(255) | nullable | |
| `client_category` | VARCHAR(20) | nullable | `FII`, `DII`, `MF`, `INSURANCE`, `PROMOTER`, `RETAIL`, `UNKNOWN` |
| `is_institutional` | BIT | nullable | |
| `deal_value` | DECIMAL(18,2) | nullable | Computed: `quantity * tradePrice` (`@PrePersist`) |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(stock_id, deal_date, client_name, buy_sell, quantity, trade_price)`
**Index:** `idx_bulk_deal_screener` on `(buy_sell, is_institutional, deal_date)`

---

#### 4.3.16 `block_deals` — BlockDeal

Same schema as `bulk_deals`.

**Unique constraint:** `(stock_id, deal_date, client_name, buy_sell, quantity, trade_price)`
**Index:** `idx_block_deal_screener` on `(buy_sell, is_institutional, deal_date)`

---

#### 4.3.17 `watchlists` — Watchlist

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `name` | VARCHAR(100) | NOT NULL | `@NotBlank`, `@Size(max=100)` |
| `description` | VARCHAR(255) | nullable | `@Size(max=255)` |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |
| `updated_at` | DATETIME | nullable | `@UpdateTimestamp` |

**Relationships:** `@OneToMany(mappedBy="watchlist", cascade=ALL, orphanRemoval=true)` → `WatchlistItem`

---

#### 4.3.18 `watchlist_items` — WatchlistItem

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `watchlist_id` | BIGINT | FK → `watchlists.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `stock_id` | BIGINT | FK → `stocks.id`, NOT NULL | `@ManyToOne(LAZY)` |
| `added_at` | DATETIME | NOT NULL | `@CreationTimestamp` |
| `notes` | VARCHAR(255) | nullable | `@Size(max=255)` |

**Unique constraint:** `(watchlist_id, stock_id)`
**Index:** `idx_wi_watchlist` on `(watchlist_id)`

---

#### 4.3.19 `strategy_config` — StrategyConfig (NEW)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `strategy_name` | VARCHAR(50) | NOT NULL, UNIQUE | e.g. `RSI_OVERSOLD`, `MACD_CROSSOVER` |
| `active` | BIT | NOT NULL, DEFAULT true | Whether strategy participates in signal generation |
| `display_name` | VARCHAR(100) | NOT NULL | Human-readable name for UI |
| `priority` | INT | NOT NULL | Display/processing order |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |
| `updated_at` | DATETIME | nullable | `@UpdateTimestamp` |

**Auto-seed:** On first startup, all 5 strategies are seeded as active (backward compatible). `DataIntegrityViolationException` handled for concurrency safety.

**Used by:** `MultiStrategySignalEngine` reads `active = true` entries as the default set when no `?active=` query param is provided. The `?active=` param still works as a per-request override.

---

#### 4.3.20 `strategy_condition_groups` — StrategyConditionGroup (NEW)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `strategy_name` | VARCHAR(50) | NOT NULL | FK-like reference to `strategy_config.strategy_name` |
| `condition_id` | VARCHAR(80) | NOT NULL | Unique within strategy, e.g. `rsi_strong_buy` |
| `signal_type` | VARCHAR(10) | NOT NULL | Expected signal: `BUY`, `SELL`, `HOLD` |
| `field_label` | VARCHAR(100) | NOT NULL | Human-readable label for the indicator field |
| `operator` | VARCHAR(30) | NOT NULL | Comparison operator (e.g. `<`, `>`, `between`, `crossover_above`) |
| `threshold_value` | DECIMAL(10,4) | nullable | Primary threshold value |
| `threshold_value2` | DECIMAL(10,4) | nullable | Secondary threshold (for `between`, `within` operators) |
| `confidence` | VARCHAR(10) | NOT NULL | `high`, `mid`, `low` |
| `display_order` | INT | NOT NULL | Ordering within the strategy |
| `created_at` | DATETIME | NOT NULL | `@CreationTimestamp` |

**Unique constraint:** `(strategy_name, condition_id)`

**Auto-seed:** 5 default conditions per strategy (25 total) seeded on `ApplicationReadyEvent`.

---

#### 4.3.21 `strategy_condition_stats_cache` — StrategyConditionStatsCache (NEW)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `strategy_name` | VARCHAR(50) | NOT NULL | FK-like reference to strategy |
| `condition_id` | VARCHAR(80) | NOT NULL | Matches `strategy_condition_groups.condition_id` |
| `stock_count` | INT | NOT NULL | Number of stocks matching this condition |
| `computed_at` | DATETIME | NOT NULL | Timestamp of last computation |

**Unique constraint:** `(strategy_name, condition_id)`

**Async computation:** `StrategyConditionService.computeAndCacheStats()` is `@Async("syncExecutor")` — evaluates each condition against real indicator/daily-price data per stock.

---

#### 4.3.22 `shadow_signal_records` — ShadowSignalRecord (NEW)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `stock_id` | BIGINT | NOT NULL | Plain FK (no JPA relationship) |
| `recorded_at` | DATE | NOT NULL | |
| `shadow_version` | VARCHAR(20) | NOT NULL | e.g. `old-logic`, `new-logic` |
| `recommendation` | VARCHAR(20) | NOT NULL | `STRONG_BUY`, `BUY`, `HOLD`, `SELL`, `STRONG_SELL` |
| `composite_score` | INT | NOT NULL | |
| `confidence_score` | BIGINT | nullable | |
| `indicator_coverage` | INT | nullable | |
| `price_at_signal` | DECIMAL(14,2) | nullable | |
| `rsi_14` | DECIMAL(10,4) | nullable | |
| `sma_20` | DECIMAL(14,4) | nullable | |
| `sma_50` | DECIMAL(14,4) | nullable | |
| `macd` | DECIMAL(14,4) | nullable | |
| `macd_signal` | DECIMAL(14,4) | nullable | |
| `macd_histogram` | DECIMAL(14,4) | nullable | |
| `bollinger_upper` | DECIMAL(14,4) | nullable | |
| `bollinger_lower` | DECIMAL(14,4) | nullable | |
| `divergence_score` | INT | NOT NULL | |
| `weekly_confluence_score` | INT | NOT NULL | |
| `monthly_confluence_score` | INT | NOT NULL | |
| `rsi_score` | INT | NOT NULL | |
| `bollinger_score` | INT | NOT NULL | |
| `sma_score` | INT | NOT NULL | |
| `week52_score` | INT | NOT NULL | |
| `macd_score` | INT | NOT NULL | |
| `stoch_score` | INT | NOT NULL | |
| `adx_filter_applied` | INT | NOT NULL | |
| `stoch_rsi_score` | INT | NOT NULL | |
| `ultimate_osc_score` | INT | NOT NULL | |
| `roc_score` | INT | NOT NULL | |
| `williams_r_score` | INT | NOT NULL | |
| `cci_score` | INT | NOT NULL | |
| `sr_proximity_score` | INT | NOT NULL | |
| `vwap_score` | INT | NOT NULL | |
| `ichimoku_score` | INT | NOT NULL | |
| `trend_direction_score` | INT | NOT NULL | |
| `breakout_score` | INT | NOT NULL | |
| `candlestick_score` | INT | NOT NULL | |
| `reversal_score` | INT | NOT NULL | |
| `raw_trend_score` | INT | NOT NULL | |
| `raw_momentum_score` | INT | NOT NULL | |
| `raw_structure_score` | INT | NOT NULL | |
| `adx_multiplier` | DECIMAL(10,4) | nullable | |
| `score_after_adx` | INT | NOT NULL | |
| `score_after_candlestick` | INT | NOT NULL | |
| `score_after_reversal` | INT | NOT NULL | |
| `score_after_discount` | INT | NOT NULL | |
| `gate_blocked` | BOOLEAN | NOT NULL | |
| `is_divergent` | BOOLEAN | nullable | Whether shadow signal diverges from production signal |
| `divergence_reason` | VARCHAR(255) | nullable | |

**Unique constraint:** `(stock_id, recorded_at, shadow_version)`
**Index:** `idx_shadow_signal_stock_date_version` on `(stock_id, recorded_at DESC, shadow_version)`

**Relationships:**
- `@ElementCollection` → `shadow_signal_gate_block_reasons` table (stores gate block reasons as `List<String>`)

**Purpose:** Stores alternative signal computation results for A/B comparison against production signals. Enables signal accuracy comparison between old and new scoring logic.

---

### 4.4 Entity Relationship Diagram (22 entities + 1 `@ElementCollection`)

```
Watchlist  1 ──── * WatchlistItem * ──── 1 Stock
                                      │
Portfolio  1 ──── * PortfolioHolding * ── 1 Stock
                                      │
Portfolio  1 ──── * PortfolioSnapshot * ─ 1 Stock
                                      │
                                      ├──── 1 * DailyPrice
                                      ├──── 1 * TechnicalIndicator
                                      ├──── 1   FundamentalData (unique FK)
                                      ├──── 1 * SupportResistanceLevel
                                      ├──── 1 * InstitutionalHolding
                                      ├──── 1 * BulkDeal
                                      └──── 1 * BlockDeal

StrategyConfig            (standalone, no FK — strategy active/inactive state)
StrategyConditionGroup    (standalone, no FK — stores per-strategy signal conditions)
StrategyConditionStatsCache (standalone, no FK — cached stock match counts per condition)
FiiDiiData                (standalone, no FK to Stock)
CorporateEvent            (standalone, uses symbol String, no FK)
SignalRecord              (uses stockId Long, no JPA relationship)
ShadowSignalRecord        (uses stockId Long, no JPA relationship — A/B signal comparison)
SignalHistoricalPerformance  (standalone, no FK)
```

---

## 5. REST API Endpoints

All endpoints return `ApiResponse<T>` with envelope `{status, message, data, timestamp}` unless noted.

### 5.1 StockController (`/api/stocks`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/stocks` | — | `List<StockDTO>` | List all stocks |
| POST | `/api/stocks` | `StockDTO` | `StockDTO` | Add stock |
| PUT | `/api/stocks/{id}` | `StockDTO` | `StockDTO` | Update stock |
| DELETE | `/api/stocks/{id}` | — | void | Delete stock |
| GET | `/api/stocks/{id}` | — | `StockDetailsDTO` | Stock detail (prices + RSI) |
| GET | `/api/stocks/validate-symbol` | `?symbol` | `SymbolValidationResult` | Validate via Yahoo Finance |
| POST | `/api/stocks/recalculate-portfolio` | — | String | Recalculate all portfolios |
| GET | `/api/stocks/search` | `?name, limit=10` | `List<StockSearchResultDTO>` | Search stocks via Yahoo |
| POST | `/api/stocks/csv-import` | `List<CsvImportRequest>` | `List<StockDTO>` | Upsert from CSV |
| GET | `/api/stocks/{id}/daily-prices` | `?days=90` | `List<DailyPriceDTO>` | Price history |
| PATCH | `/api/stocks/{id}/portfolio` | `CsvImportRequest` | `StockDTO` | Update portfolio data |
| GET | `/api/stocks/{id}/snapshots` | `?days=90` | `List<PortfolioSnapshotDTO>` | Recent snapshots |
| GET | `/api/stocks/{id}/snapshots/all` | — | `List<PortfolioSnapshotDTO>` | Full snapshot history |
| DELETE | `/api/stocks/by-prefix/{prefix}` | — | String | Delete by symbol prefix |

### 5.2 StockHistoryController (`/api/stocks`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/stocks/history` | `?symbol, days=30` | `StockSummaryDTO` | Yahoo Finance summary |
| GET | `/api/stocks/history/data` | `?symbol, days=30` | `List<StockHistoryDTO>` | Yahoo Finance history |
| POST | `/api/stocks/cache/clear` | — | String | Clear stockHistory cache |
| GET | `/api/stocks/test` | — | String | Health check |
| POST | `/api/stocks/history/save` | `SaveHistoryRequest` | String | Save price data |
| POST | `/api/stocks/portfolio/sync` | — | `PortfolioSyncResultDTO` | Sync portfolio prices |
| GET | `/api/stocks/history/summary/all` | `?days=30` | `List<BatchStockSummaryDTO>` | Batch summary (all) |
| GET | `/api/stocks/history/summary/local` | `?days=30` | `List<BatchStockSummaryDTO>` | Batch summary (local) |

### 5.3 StockSyncController (`/api/stocks`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| POST | `/api/stocks/sync-history` | — | String | Backfill all stocks |
| POST | `/api/stocks/{id}/sync-history` | `?days=730` | String | Sync single stock |

### 5.4 PortfolioController (`/api/portfolio`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/portfolio/history` | `?days=365, all, portfolioId` | `List<PortfolioAggregateDTO>` | Aggregated history |
| POST | `/api/portfolio/backfill` | `?force=false` | String | Backfill snapshots |
| GET | `/api/portfolio/screener` | `?date` | `List<PortfolioSnapshotDTO>` | Screener data |

### 5.5 PortfolioManagementController (`/api/portfolios`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/portfolios` | — | `List<PortfolioDTO>` | List all portfolios |
| POST | `/api/portfolios` | `CreatePortfolioRequest` | `PortfolioDTO` (201) | Create portfolio |
| GET | `/api/portfolios/{id}` | — | `PortfolioDTO` | Get portfolio |
| PUT | `/api/portfolios/{id}` | `CreatePortfolioRequest` | `PortfolioDTO` | Update portfolio |
| DELETE | `/api/portfolios/{id}` | — | String | Delete portfolio |
| GET | `/api/portfolios/default` | — | `PortfolioDTO` | Get default portfolio |
| GET | `/api/portfolios/{id}/holdings` | — | `List<HoldingDTO>` | List holdings |
| POST | `/api/portfolios/{id}/holdings` | `AddHoldingRequest` | `HoldingDTO` (201) | Add holding |
| PATCH | `/api/portfolios/{portfolioId}/holdings/{holdingId}` | `AddHoldingRequest` | `HoldingDTO` | Update holding |
| DELETE | `/api/portfolios/{portfolioId}/holdings/{holdingId}` | — | String | Remove holding |
| GET | `/api/portfolios/{portfolioId}/holdings/stock/{stockId}` | — | `HoldingDTO` | Get specific holding |
| DELETE | `/api/portfolios/{portfolioId}/holdings/stock/{stockId}` | — | String | Remove by stock ID |
| POST | `/api/portfolios/{id}/recalculate` | — | String | Recalculate portfolio |
| GET | `/api/portfolios/all/holdings/stock/{stockId}` | — | `HoldingDTO` | Aggregate holding across all |

### 5.6 SignalController (`/api/signals`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/signals` | `?portfolioId` | `List<SignalDTO>` | All signals (or portfolio-filtered) |
| GET | `/api/signals/buy` | — | `List<SignalDTO>` | Buy signals only |
| GET | `/api/signals/sell` | — | `List<SignalDTO>` | Sell signals only |
| GET | `/api/signals/{stockId}` | — | `SignalDTO` | Compute signal for stock |
| GET | `/api/signals/{stockId}/history` | `?days=90` | `List<SignalHistoryPoint>` | Signal history |

### 5.6.1 WatchlistOpportunityController (`/api/watchlist`) **(NEW)**

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/watchlist/opportunities` | `?portfolioId` | `WatchlistOpportunityResponseDTO` | Ranked buy/hold/sell opportunities for portfolio holdings |

### 5.7 WatchlistController (`/api/watchlists`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/watchlists` | — | `List<WatchlistDTO>` | List all watchlists |
| POST | `/api/watchlists` | `CreateWatchlistRequest` | `WatchlistDTO` (201) | Create watchlist |
| PUT | `/api/watchlists/{id}` | `CreateWatchlistRequest` | `WatchlistDTO` | Rename watchlist |
| DELETE | `/api/watchlists/{id}` | — | void | Delete watchlist |
| GET | `/api/watchlists/{id}/detail` | — | `WatchlistDetailDTO` | Watchlist with stocks |
| GET | `/api/watchlists/{id}/items` | — | `WatchlistDetailDTO` | Watchlist items |
| POST | `/api/watchlists/{id}/items` | `AddStockRequest` | void (201) | Add stock to watchlist |
| DELETE | `/api/watchlists/{watchlistId}/items/{stockId}` | — | void | Remove stock |
| POST | `/api/watchlists/{id}/items/batch` | `BatchAddRequest` | void (201) | Batch add stocks |
| GET | `/api/watchlists/stock/{stockId}` | — | `List<WatchlistDTO>` | Watchlists containing stock |
| POST | `/api/watchlists/{id}/sync-history` | `SyncWatchlistHistoryRequest` | `Map` | Sync history for watchlist |

### 5.8 PriceController (`/api/prices`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| POST | `/api/prices` | `DailyPriceDTO` | `DailyPriceDTO` | Save price |
| GET | `/api/prices/stock/{stockId}` | `?fromDate, toDate` | `List<DailyPriceDTO>` | Price history |
| GET | `/api/prices/stock/{stockId}/latest` | — | `DailyPriceDTO` | Latest price |

### 5.9 TechnicalIndicatorController (`/api/indicators`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/indicators/{stockId}` | — | `List<IndicatorDto>` | Latest indicators (raw) |
| GET | `/api/indicators/{stockId}/{type}` | `?fromDateStr, toDateStr` | `IndicatorHistoryDto` | Indicator history (raw) |
| POST | `/api/indicators/calculate/{stockId}` | — | void | Trigger calculation |

**Note:** Returns raw POJOs (not wrapped in `ApiResponse`).

### 5.10 FiiDiiController (`/api/fiidii`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/fiidii` | — | `Map` (date, fiiNet, diiNet, combinedNet, sentiment) | Latest FII/DII data |
| POST | `/api/fiidii/refresh` | — | String | Fetch and save |

### 5.11 RsiController (`/api/rsi`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/rsi/stock/{stockId}` | — | `RsiDTO` | Latest RSI |
| GET | `/api/rsi/stock/{stockId}/history` | `?days=30` | `List<RsiDTO>` | RSI history |
| POST | `/api/rsi/calculate/{stockId}` | — | `RsiDTO` | Calculate and save |
| POST | `/api/rsi/calculate-all` | — | String | Calculate for all |

### 5.12 SupportResistanceController (`/api/support-resistance`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/support-resistance/{stockId}` | — | `SupportResistanceDto` | Latest S/R levels |
| GET | `/api/support-resistance/{stockId}/as-of` | `?date` | `SupportResistanceDto` | Levels as of date |
| GET | `/api/support-resistance/{stockId}/history` | `?fromDate, toDate` | `List<SupportResistanceDto>` | S/R history |
| POST | `/api/support-resistance/calculate/{stockId}` | — | `SupportResistanceDto` | Calculate for stock |
| POST | `/api/support-resistance/calculate-all` | — | String | Calculate for all |

### 5.13 InstitutionalHoldingController (`/api/institutional`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/institutional/holdings/{stockId}` | — | `InstitutionalHoldingDTO` | Latest holding |
| GET | `/api/institutional/holdings/{stockId}/history` | — | `List<InstitutionalHoldingDTO>` | Holding history |
| GET | `/api/institutional/holdings/all` | — | `List<InstitutionalHoldingDTO>` | All latest holdings |
| POST | `/api/institutional/holdings/fetch-all` | — | String | Fetch for all stocks |
| POST | `/api/institutional/holdings/fetch/{stockId}` | — | `InstitutionalHoldingDTO` | Fetch for stock |
| POST | `/api/institutional/xbrl/fetch-all` | — | String | XBRL fetch all |
| POST | `/api/institutional/xbrl/fetch/{stockId}` | — | String | XBRL fetch stock |
| GET | `/api/institutional/bulk-deals/{stockId}` | — | `List<BulkDealDTO>` | Bulk deals for stock |
| GET | `/api/institutional/bulk-deals/range` | `?from, to` | `List<BulkDealDTO>` | Bulk deals in range |
| POST | `/api/institutional/bulk-deals/fetch` | `?from, to` | String | Fetch bulk deals |
| POST | `/api/institutional/bulk-deals/fetch-today` | — | String | Fetch today's bulk deals |
| GET | `/api/institutional/bulk-deals/top-institutional` | `?days=30` | `List<BulkDealDTO>` | Top institutional deals |
| GET | `/api/institutional/block-deals/{stockId}` | — | `List<BlockDealDTO>` | Block deals for stock |
| GET | `/api/institutional/block-deals/range` | `?from, to` | `List<BlockDealDTO>` | Block deals in range |
| POST | `/api/institutional/block-deals/fetch` | `?from, to` | String | Fetch block deals |
| POST | `/api/institutional/block-deals/fetch-today` | — | String | Fetch today's block deals |
| GET | `/api/institutional/score/{stockId}` | — | `InstitutionalScoreDTO` | Compute score |
| GET | `/api/institutional/score/all` | — | `List<InstitutionalScoreDTO>` | All scores |
| GET | `/api/institutional/screeners/fii-accumulation` | — | `List<ScreenerResultDTO>` | FII accumulation screener |
| GET | `/api/institutional/screeners/dii-accumulation` | — | `List<ScreenerResultDTO>` | DII accumulation screener |
| GET | `/api/institutional/screeners/mf-accumulation` | — | `List<ScreenerResultDTO>` | MF accumulation screener |
| GET | `/api/institutional/screeners/institutional-strong-buy` | — | `List<ScreenerResultDTO>` | Institutional strong buy |
| GET | `/api/institutional/screeners/institutional-price-action-buy` | — | `List<ScreenerResultDTO>` | Institutional + price action |
| GET | `/api/institutional/screeners/recent-bulk-deals` | — | `List<ScreenerResultDTO>` | Recent bulk deals |
| GET | `/api/institutional/screeners/recent-block-deals` | — | `List<ScreenerResultDTO>` | Recent block deals |
| GET | `/api/institutional/screeners/all` | — | `List<ScreenerResultDTO>` | All screeners |

### 5.14 FundamentalDataController (`/api/fundamentals`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/fundamentals/{stockId}` | — | `FundamentalDataDTO` | Get fundamentals |
| POST | `/api/fundamentals/fetch/{stockId}` | — | `FundamentalDataDTO` | Fetch and save |
| POST | `/api/fundamentals/fetch-all` | — | Integer | Fetch for all stocks |
| POST | `/api/fundamentals/screen` | `FundamentalScreenRequest` | `List<FundamentalDataDTO>` | Screen stocks |
| GET | `/api/fundamentals/sectors` | — | `List<String>` | Distinct sectors |

### 5.15 CorporateEventController (`/api/events`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/events` | `?symbol` | `List<CorporateEvent>` | Events for symbol |
| POST | `/api/events/refresh` | — | String | Fetch and save events |

### 5.16 BacktestController (`/api/backtest`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/backtest/stock/{stockId}` | `?stopLoss=true, positionSizePct=0.02` | `BacktestResultDTO` | Run backtest |

### 5.17 DhanController (`/api/dhan`)

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| POST | `/api/dhan/sync-holdings` | — | String | Sync holdings from Dhan |
| POST | `/api/dhan/sync-prices` | — | String | Sync daily prices + RSI |

### 5.18 StockHistoryViewController (`/stocks`)

**Note:** This is a `@Controller` (not `@RestController`). Returns a Thymeleaf view name.

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/stocks/history` | — | String (view: `stock-history`) | Renders stock-history HTML page |

### 5.19 StrategyConfigController (`/api/strategy-config`) **(NEW)**

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| GET | `/api/strategy-config` | — | `ApiResponse<List<StrategyConfigDTO>>` | List all strategy configs |
| PUT | `/api/strategy-config/{strategyName}` | `{ "active": true/false }` | `ApiResponse<StrategyConfigDTO>` | Toggle strategy active/inactive |
| GET | `/api/strategy-config/full` | — | `ApiResponse<List<StrategyFullDTO>>` | Full config with conditions, tags, stock counts |
| GET | `/api/strategy-config/stats` | — | `ApiResponse<Map<String, List<ConditionStatDTO>>>` | Cached stock match counts per condition |
| PUT | `/api/strategy-config/conditions/{strategyName}` | `List<StrategyConditionGroupDTO>` | `ApiResponse<Void>` | Save conditions for strategy; triggers async stats recompute |
| POST | `/api/strategy-config/conditions/reset` | — | `ApiResponse<Void>` | Reset all conditions to defaults; triggers async recompute |
| PUT | `/api/strategy-config/{strategyName}/priority` | `?priority=1-10` | `ApiResponse<StrategyConfigDTO>` | Update strategy priority |

**Note:** On first startup, `StrategyConfigService` auto-seeds all 5 strategies as active. `MultiStrategySignalEngine` reads DB state as the default active set when no `?active=` query param is provided. `StrategyConditionService` seeds 25 default conditions (5 per strategy) on `ApplicationReadyEvent`.

### 5.20 Summary

| HTTP Method | Count |
|-------------|-------|
| GET | 66 |
| POST | 38 |
| PUT | 7 |
| PATCH | 2 |
| DELETE | 7 |
| **Total (REST)** | **119** |
| View (Thymeleaf) | 1 |
| **Grand Total** | **120** |

---

## 6. Business Logic

### 6.1 Signal Engine (`SignalService.java`)

Multi-factor weighted scoring model generating BUY/SELL/HOLD recommendations. Uses 27 scoring factors in `computeWeightedScore()`.

#### Scoring Factors

| # | Factor | BUY Weight | SELL Weight | Condition |
|---|--------|-----------|------------|-----------|
| 1 | Divergence (RSI vs Price) | +4 (bullish) | -2 (bearish) | Price-indicator divergence |
| 2 | Weekly Confluence | +3 (both < 30), +1 (both > 50) | -2 (both > 60 or both < 50) | Weekly RSI + weekly StochRSI agreement |
| 3 | Monthly Confluence | +1 (daily < 30, monthly < 50) | 0 | Daily + monthly timeframe agreement |
| 4 | RSI (14) | +2 (< 30), +1 (55-65 sweet spot) | -2 (< 40), -1 (< 50 or > 75) | Oversold/overbought + momentum sweet spot |
| 5 | Bollinger Bands | +2 (below lower) | -1 (above upper) | Mean reversion |
| 6 | MACD Crossover | +3 (MACD > Signal & > 1), +1 (above signal, both positive) | -2 (below signal, both negative), -1 (below signal, both positive) | Signal line crossover strength |
| 7 | Stochastic %K/%D | +2 (K > D, both < 20), +1 (K > D, K < 30) | -2 (K < D, both > 80), -1 (K > 80) | Momentum crossover |
| 8 | StochRSI | +2 (< 20), +1 (< 30) | -3 (> 90), -1 (> 80) | RSI of RSI |
| 9 | Ultimate Oscillator | +1 (< 30) | 0 | Multi-timeframe oscillator (buyer-oriented) |
| 10 | ROC (12) | +1 (positive) | 0 | Rate of change (buyer-oriented) |
| 11 | Williams %R | +1 (< -80) | -1 (> -20) | Overbought/oversold |
| 12 | CCI | +1 (< -100) | -2 (> 200), -1 (> 100) | Commodity Channel Index |
| 13 | OBV (3-day net) | +1 (positive flow) | -1 (negative flow) | Volume trend |
| 14 | Price vs SMA20/SMA50 | +1 (above SMA20 > 3%), +2 (SMA20 > SMA50, price > SMA20) | -1 (below SMA20 > 3%), -2 (SMA20 < SMA50, price < SMA20) | Trend direction + moving average alignment |
| 15 | 52-Week Proximity | +2 (near low with positive momentum), **0 in bearish trend** | 0, **+2 (near high)** | Zeroed in bearish trend (SMA20 < SMA50) to prevent value trap BUY signals |
| 16 | ADX Trend Filter | Table-based multiplier: see below | Table-based multiplier: see below | ADX strength adjusts score magnitude; counter-trend dampened |
| 17 | Volume Confirmation | +1 (volume >= 1.2x 20-day avg) | 0 (no penalty) | Volume validates move |
| 18 | S/R Proximity | +4 (near support 0-15%), **0 in bearish trend** | -2 (near resistance 85-100%), **+4 neutral** | Support/resistance zone; zeroed in bearish trend to prevent false BUY |
| 19 | VWAP | +2 (below VWAP > 2%), +1 (below VWAP) | -2 (above VWAP > 2%), -1 (above VWAP) | Volume-weighted average price |
| 20 | Ichimoku Cloud | +1 (TK cross bullish), +2 (above cloud), +1 (green cloud) | -1 (TK bearish), -2 (below cloud), -1 (red cloud) | Ichimoku Kinko Hyo |
| 21 | Trend Cap | Cap at +2 if price < SMA50 | — | Prevents large bullish scores in downtrend |
| 22 | Breakout Score | From BreakoutDetector | — | Gap up/down, range breakout |
| 23 | Compound Overbought | score * 0.7 (3+ overbought), score * 0.85 (2 overbought) | — | Dampens score when multiple oscillators overbought |
| 24 | Candlestick Patterns | From CandlestickPatternCalculator | — | Bullish/bearish pattern scores (engulfing, hammer, etc.) |
| 25 | Reversal Detection | From ReversalDetector | — | Multi-flag reversal confirmation (RSI rising, MACD improving, etc.) |

**ADX Multiplier Table:**

| ADX Range | Multiplier | Notes |
|-----------|-----------|-------|
| < 15 | ×0.3 | Very weak trend |
| 15–25 | 0.5 + 0.02×(ADX-15) | Weak to moderate (linear ramp) |
| 25–35 | 0.7 + 0.01×(ADX-25) | Moderate to strong (linear ramp) |
| ≥ 35 | ×1.0 | Strong trend |
| Counter-trend (ADX > 30) | ×0.7 | Additional dampening when signal opposes strong trend |

**Post-Scoring Modifiers (applied after `computeWeightedScore()`):**
- **FII/DII Adjustment:** +1 if FII+DII combined net > 500 Cr, -1 if < -500 Cr, 0 otherwise (from `FiiDiiService.getScoreAdjustment()`)
- **Channel Trading Overrides:** Near 52-week bottom (0-15%) overrides HOLD→BUY if weekly RSI > 40; near top (85-100%) overrides HOLD→SELL
- **Bearish Trend Graduated Discount:** 15% score reduction when SMA20 < SMA50, waived on strong reversal evidence
- **High-Confidence BUY Gate:** When enabled (`signal.gate.high-confidence.enabled=true`), blocks BUY signals if trend (SMA20 > SMA50), momentum (RSI < 75), or risk/reward (price ≤ SMA20) conditions fail

#### Signal Thresholds

| Recommendation | Composite Score |
|---------------|----------------|
| STRONG_BUY | ≥ 7 |
| BUY | ≥ 3 |
| HOLD | Between -3 and +2 |
| SELL | ≤ -4 |
| STRONG_SELL | ≤ -7 |

#### Confidence Score

Computed from (0-100 scale):
- Base score magnitude (compositeScore mapped to 0-100)
- Volume confirmation bonus (+2 if volume >= 1.2x avg)
- Event risk penalty (near corporate events)
- Confluence bonus (multiple agreeing indicators)
- Indicator coverage penalty (fewer of 19 scoring factors with data = lower confidence)
- Stale data penalty (old price data)
- Historical accuracy bonus/penalty (last 30-day signal accuracy)

#### Extended Trade Metrics

- **Stop Loss:** Entry price - (2 × ATR) for BUY, entry price + (2 × ATR) for SELL
- **Target:** Entry price + (3 × ATR) for BUY, entry price - (3 × ATR) for SELL
- **Position Sizing:** 1-5% of portfolio based on volatility (ATR-based), adjusted for confidence
- **Signal Record:** Daily upsert for accuracy tracking (forward returns at 5d, 10d, 20d)

#### Mature Features (formerly Phase 2, now in base scoring)

The following features were developed as Phase 2 additions and are now fully integrated into the base signal pipeline:
- **Candlestick Patterns (`CandlestickPatternCalculator`):** Detects 10 candlestick patterns (engulfing, hammer, shooting star, morning/evening star, harami, etc.)
- **Reversal Detection (`ReversalDetector`):** Multi-flag reversal confirmation using 6 flags: RSI rising, MACD improving, bullish divergence, OBV rising, higher low, reclaiming SMA20
- **High-Confidence BUY Gate:** Config-gated (`signal.gate.high-confidence.enabled`), requires trend (SMA20 > SMA50) + momentum (RSI < 75) + risk/reward (price ≤ SMA20)
- **Channel Trading Overrides:** Config-gated, applies buy/sell overrides at 52W extremes with weekly trend guard
- **Bearish Trend Graduated Discount:** 15% reduction when SMA20 < SMA50, waived on strong reversal evidence

### 6.1.1 Shadow Signal System (`ShadowSignalService`) **(NEW)**

A/B comparison framework for signal quality analysis. Computes signals using "old" or "new" logic to compare accuracy.

| Component | Description |
|-----------|-------------|
| `ShadowSignalService` | Dispatches to old or new logic based on `shadow.version` config |
| `ShadowSignalRecord` (entity) | Stores alternative signal with full scoring breakdown (47 fields) |
| `ShadowSignalConfig` | Spring profile (`application-shadow.properties`) — cron schedule, async pool, logging |
| `ShadowSignalRecordRepository` | Queries by stockId, recordedAt, shadowVersion, divergence |

**Configuration (`application-shadow.properties`):**

| Property | Value | Description |
|----------|-------|-------------|
| `shadow.signal.enabled` | `true` | Enable/disable shadow signal computation |
| `shadow.signal.version` | `old-logic` | Version to compute (`old-logic` or `new-logic`) |
| `shadow.signal.cron` | `0 0 9 * * ?` | Daily at 9:00 AM (configurable) |
| `spring.task.execution.pool.core-size` | `5` | Async pool core size |
| `spring.task.execution.pool.max-size` | `10` | Async pool max size |
| `spring.task.execution.pool.queue-capacity` | `100` | Async pool queue capacity |

**Usage:** Run with `--spring.profiles.active=shadow` to enable shadow computation. Compare production vs shadow signals via divergence analysis.

### 6.1.2 Watchlist Opportunity Ranking (`WatchlistOpportunityService`) **(NEW)**

Ranks portfolio holdings by buy/sell opportunity strength with detailed trade setup information.

| Feature | Description |
|---------|-------------|
| Opportunity Ranking | Sorted by signal strength (strongest BUY first) |
| Entry Zone | `price ± ATR` (low/high boundaries) |
| Risk/Reward Ratio | `(target - entry) / (entry - stop)` |
| Indicator Agreement | % of directional indicators agreeing with composite score |
| Signal Reasons | Human-readable factors (RSI oversold, MACD cross, volume breakout, strong ADX trend) |

**DTOs:**
- `WatchlistOpportunityDTO`: Per-stock opportunity with signal, entryZone, stopLoss, target, riskRewardRatio, indicatorAgreement, confidenceScore, reasons
- `WatchlistOpportunityResponseDTO`: Wrapper with `List<WatchlistOpportunity>` + `WatchlistSummaryDTO`
- `WatchlistSummaryDTO`: Counts of BUY/HOLD/SELL opportunities, strongest/weakest signal
- `EntryZoneDTO`: Entry zone boundaries (low, high)
- `ReasonDTO`: Factor/value/interpretation triplet

**Endpoint:** `GET /api/watchlist/opportunities?portfolioId` → Returns ranked opportunities for portfolio holdings

### 6.2 Portfolio Management

- **Multi-portfolio support:** Users can create named portfolios (e.g., "Dhan", "Ammu Groww", "Groww Siba")
- **Default portfolio:** One portfolio marked as default, auto-loaded on dashboard
- **Holdings:** `PortfolioHolding` links Stock to Portfolio with quantity and avg price
- **P&L Calculation:** `(currentPrice - avgPrice) × quantity`
- **Snapshots:** Daily point-in-time records for performance charting
- **CSV Import:** Upsert behavior — updates quantity/avgPrice if stock exists, preserves `created_at`

### 6.3 Institutional Scoring (`InstitutionalScoreService`)

8-component scoring engine (0–100 scale):

| Component | Max Score | Description |
|-----------|-----------|-------------|
| FII Holding % | 20 | Foreign institutional ownership level |
| DII Holding % | 15 | Domestic institutional ownership |
| MF Holding % | 15 | Mutual fund ownership |
| Bulk Deal Score | 15 | Institutional bulk buy activity |
| Block Deal Score | 10 | Institutional block transactions |
| Delivery Score | 10 | Delivery volume percentage |
| Volume Score | 10 | Trading volume analysis |
| Price Action Score | 5 | Price momentum alignment |

**Grades:** STRONG_BUY (80-100), BUY (60-79), NEUTRAL (40-59), SELL (0-39)

**DTO Fields:** `InstitutionalScoreDTO` includes stockId, symbol, name, sector, all 8 component scores, totalScore, institutionalGrade, latestPrice, holding percentages with QoQ changes, latestQuarterEnd, recentBulkBuys, recentBlockBuys, averageVolume, latestVolume, signalRecommendation

### 6.4 Technical Indicators (29 Calculators)

| Indicator | Calculator | Description |
|-----------|-----------|-------------|
| RSI (14) | `RsiCalculator` | Wilder's smoothing method |
| SMA 20/50/200 | `Sma20Calculator`, `Sma50Calculator`, `Sma200Calculator` | Simple moving averages |
| SMA (Generic) | `SmaCalculator` | Generic SMA with configurable period **(NEW)** |
| EMA 20 | `Ema20Calculator` | Exponential moving average |
| EMA (Generic) | `EmaCalculator` | Generic EMA with configurable period **(NEW)** |
| MACD Line | `MacdLineCalculator` | 12-day EMA - 26-day EMA |
| MACD Signal | `MacdSignalCalculator` | 9-day EMA of MACD line |
| Bollinger Upper/Lower | `BollingerUpperCalculator`, `BollingerLowerCalculator` | 2 std dev bands |
| Stochastic K/D | `StochKCalculator`, `StochDCalculator` | %K and %D |
| Williams %R | `WilliamsRCalculator` | Momentum oscillator |
| ATR | `ATRCalculator` | Average True Range (14-day) |
| CCI | `CCICalculator` | Commodity Channel Index |
| StochRSI | `StochRsiCalculator` | RSI of RSI |
| ADX | `AdxCalculator` | Average Directional Index |
| +DI / -DI | `PlusDiCalculator`, `MinusDiCalculator` | Directional indicators |
| Ultimate Oscillator | `UltimateOscillatorCalculator` | Multi-timeframe oscillator |
| ROC (12) | `RocCalculator` | Rate of Change |
| OBV | `ObvCalculator` | On-Balance Volume |
| VWAP | `VwapCalculator` | Volume-Weighted Average Price |
| Tenkan Sen | `TenkanSenCalculator` | Ichimoku conversion line |
| Kijun Sen | `KijunSenCalculator` | Ichimoku base line |
| Senkou Span A/B | `SenkouSpanACalculator`, `SenkouSpanBCalculator` | Ichimoku cloud boundaries |
| Chikou Span | `ChikouSpanCalculator` | Ichimoku lagging span |
| Ichimoku (Consolidated) | `IchimokuCalculator` | All 5 Ichimoku components in one **(NEW)** |
| Support/Resistance | `SupportResistanceCalculator` | S/R level detection |
| Breakout | `BreakoutDetector` (component) | Gap up/down, range breakout detection |
| Candlestick Patterns | `CandlestickPatternCalculator` | 10 pattern detection (engulfing, hammer, etc.) **(NEW)** |
| Reversal Detection | `ReversalDetector` | Multi-flag reversal confirmation **(NEW)** |

### 6.5 Backtesting (`BacktestService`)

- Entry signals based on composite score threshold
- ATR-based stop-loss (configurable)
- Position sizing (1-5% of portfolio)
- Forward return tracking (5d, 10d, 20d)
- Win rate and profit factor calculation

---

## 7. Scheduled Jobs

### 7.1 Cron Jobs

| Scheduler | Job | Cron (UTC) | IST Equivalent | Description |
|-----------|-----|------------|----------------|-------------|
| `MarketAnalysisScheduler` | `syncDhanAndCalculateIndicators` | `0 45 10 * * MON-FRI` | 4:15 PM IST | Dhan sync + indicators + S/R |
| `MarketAnalysisScheduler` | `weeklyBackfill` | `0 0 7 * * SUN` | 12:30 PM IST | Yahoo Finance history backfill |
| `MarketAnalysisScheduler` | `calculateDailyIndicators` | `0 0 9 * * ?` | 2:30 PM IST | Safety-net indicator recalc |
| `FiiDiiService` | `scheduledFetch` | `0 30 15 * * MON-FRI` | 9:00 PM IST | Daily FII/DII data fetch |
| `SignalPerformanceScheduler` | `computeHistoricalReturns` | `0 30 2 * * *` | 8:00 AM IST | Forward returns precomputation |
| `SignalAccuracyScheduler` | `markForwardAccuracy` | `0 0 3 * * *` | 8:30 AM IST | Mark past signal accuracy |
| `InstitutionalHoldingScheduler` | `weeklyShareholdingSync` | `0 30 4 * * SUN` | 10:00 AM IST | NSE shareholding patterns |
| `InstitutionalHoldingScheduler` | `dailyBulkDealsFetch` | `0 30 12 * * MON-FRI` | 6:00 PM IST | Daily bulk deals |
| `InstitutionalHoldingScheduler` | `dailyBlockDealsFetch` | `0 35 12 * * MON-FRI` | 6:05 PM IST | Daily block deals |
| `InstitutionalHoldingScheduler` | `weeklyDealsBackfill` | `0 30 5 * * SUN` | 11:00 AM IST | 90-day bulk/block backfill |
| `InstitutionalHoldingScheduler` | `weeklyXbrlFetch` | `0 0 6 * * SUN` | 11:30 AM IST | XBRL detailed shareholding |
| `FundamentalDataScheduler` | `weeklyFundamentalSync` | `0 0 8 * * SUN` | 1:30 PM IST | Fundamental data sync |
| `ShadowSignalConfig` | `computeAllShadowSignals` | `0 0 9 * * ?` | 2:30 PM IST | Shadow signal A/B computation **(NEW)** |

### 7.2 Startup Tasks

| Task | Async | Description |
|------|-------|-------------|
| `PortfolioMigrationStartupTask` | No | One-time migration: creates default portfolio, migrates legacy stocks to holdings, backfills null portfolio_id on snapshots |
| `PortfolioSnapshotStartupTask` | No | Create missing daily snapshots for every stock in every portfolio |
| `StartupDataSyncTask` | Yes (`syncExecutor`) | Sync last 7 days of Yahoo Finance history on startup |
| `FundamentalStartupTask` | Yes (`syncExecutor`) | Refresh fundamental data for stocks stale > 7 days |
| `SignalAccuracyStartupTask` | Yes (`syncExecutor`) | Backfill historical signal records and mark forward accuracy |
| `FiiDiiStartupTask` | Yes (`syncExecutor`) | Fetch latest FII/DII data from NSE on startup |
| `IndicatorStartupTask` | Yes (`syncExecutor`) | Calculate indicators for stocks missing today's data; runs V1 DDL to widen indicator_type column |
| `SignalPerformanceScheduler` | Yes (`syncExecutor`) | Also runs `computeHistoricalReturns` on `ApplicationReadyEvent` (in addition to cron) |

---

## 8. Caching Strategy

**Framework:** Spring Cache + Caffeine
**TTL:** 15 minutes (expireAfterWrite)
**Max Size:** 500 entries per cache

| Cache Name | Used By | Key Pattern | Evicted By |
|------------|---------|-------------|------------|
| `latestIndicators` | `TechnicalAnalysisService` | stockId | — |
| `indicatorHistory` | `TechnicalAnalysisService` | stockId:type | — |
| `stockHistory` | `YahooFinanceService` | symbol_days | — |
| `signals` | `SignalService` | `allSignals`, `buySignals`, `sellSignals`, `stock_{id}` | `DhanSyncService` |
| `supportResistanceLevels` | `SupportResistanceService` | stockId | `SupportResistanceService` |
| `institutionalScores` | `InstitutionalScoreService` | stockId or `all` | `InstitutionalHoldingService`, `BulkDealService`, `BlockDealService`, `NseXbrlShareholdingService` |

---

## 9. External Integrations

### 9.1 Yahoo Finance

| Feature | Detail |
|---------|--------|
| Purpose | Stock search, symbol validation, historical OHLCV |
| Auth | None (public API) |
| Rate Limiting | 1 req/s (`ApiRateLimiter`) |
| Client | `WebClient` (Spring WebFlux) |
| Retry | Spring Retry (3 attempts, 5s backoff × 2) |

### 9.2 Dhan Brokerage API

| Feature | Detail |
|---------|--------|
| Purpose | Holdings sync, historical candle data |
| Base URL | `https://api.dhan.co` |
| Auth | Access Token + Client ID (`1101658114`) |
| Client | `WebClient` (Spring WebFlux) |
| Response DTOs | `DhanHoldingDTO` (holdings), `DhanCandleResponseDTO` (column-oriented OHLCV) |

### 9.3 NSE India

| Feature | Detail |
|---------|--------|
| Purpose | FII/DII data, corporate events, shareholding patterns, bulk/block deals |
| Auth | Cookie-based (session) |
| Rate Limiting | 0.5 req/s |
| Circuit Breaker | Resilience4j (`nseSession`, `nseXbrl` instances) |
| Session Management | `NseSessionManager` with automatic cookie refresh |
| Scheduled Jobs | Daily FII/DII fetch (3:30 PM UTC), daily bulk/block deals (6:00-6:05 PM UTC), weekly shareholding (10 AM UTC), weekly XBRL (11:30 AM UTC), weekly deals backfill (11 AM UTC) |

**Circuit Breaker Config:**
- Sliding window: 10
- Failure threshold: 50%
- Wait in open state: 30s
- Half-open calls: 3
- Time limiter: 30s timeout

---

## 10. Frontend Pages

| Page | File | JS Module | Description |
|------|------|-----------|-------------|
| Dashboard | `index.html` (369 lines) | `portfolio.js` (1,000 lines) | Portfolio overview, signals, charts |
| Stock Management | `stocks.html` (564 lines) | `stock-management.js` (1,056 lines) | CRUD, CSV import, portfolio tabs, alerts |
| Stock Detail | `stock-detail.html` (483 lines) | `stock-detail.js` (3,242 lines) | Individual stock analysis, 10 chart sections, candlestick, backtest |
| Institutional Dashboard | `institutional-dashboard.html` (967 lines) | *(inline JS)* | FII/DII data, screeners, 8 tabs, CSV export |
| Fundamentals Screener | `fundamentals-screener.html` (173 lines) | `fundamentals-screener.js` (226 lines) | Multi-criteria fundamental screening |
| Watchlist | `watchlist.html` (423 lines) | `watchlist.js` (896 lines) | Watchlist management, signal badges |
| Watchlist Opportunities | `watchlist-opportunities.html` (198 lines) | `watchlist-opportunities.js` (284 lines) | Ranked buy/sell opportunities with entry zones, stops, targets **(NEW)** |
| Price Entry | `price-entry.html` (139 lines) | `price-entry.js` (91 lines) | Manual price entry |
| RSI Analysis | `rsi-analysis.html` (136 lines) | `rsi-analysis.js` (162 lines) | RSI overview, oversold/overbought |
| Stock History | `stock-history.html` (300 lines) | `stock-history.js` (466 lines) | Yahoo Finance history viewer |
| History Summary | `history-summary.html` (190 lines) | `history-summary.js` (202 lines) | Batch Yahoo summary |
| Shadow Signal Comparison | `shadow_signal_comparison.html` (382 lines) | *(Thymeleaf template)* | A/B signal comparison view |
| Strategy Manager | `strategy.html` | `strategy-manager.js` (528 lines) | Full strategy manager with editable conditions, priority controls, stock count pills, inline editing **(NEW)** |

**Shared:** `api.js` (523 lines, ~65 named API functions), `styles.css` (181 lines), `theme.css` (205 lines)

**Libraries (CDN):** Tailwind CSS, Chart.js 4.4.0 + Financial plugin + Annotations, Font Awesome 6.4, Lightweight Charts 5.2.0 (candlestick), chartjs-adapter-date-fns

**Total custom frontend code:** ~8,162 lines JS + 386 lines CSS + 4,324 lines HTML = ~12,872 lines across 33 source files (excluding minified CDN bundles)

---

## 11. Testing

### 11.1 Backend Tests

| Category | Test Files | Test Methods (approx.) | Framework |
|----------|-----------|----------------------|-----------|
| Service Unit Tests | 14 | ~465 | JUnit 5 + Mockito |
| Calculator Unit Tests | 26 | ~100 | JUnit 5 |
| Institutional Service Tests | 6 | ~50 | JUnit 5 + Mockito |
| Controller Integration Tests | 5 | ~25 | SpringBootTest + @Transactional |
| Scheduler Tests | 1 | ~5 | JUnit 5 + Mockito |
| Strategy Tests | 3 | ~37 | JUnit 5 + Mockito |
| **Total Backend** | **55 files** | **~682** | |

### 11.2 Frontend E2E Tests

| Spec File | Lines | Description |
|-----------|-------|-------------|
| `frontend.spec.js` | 501 | Dashboard, navigation, charts, theme — all pages load |
| `portfolio-chart-fixes.spec.js` | 597 | Chart periods, memory leak prevention, empty data handling |
| `signals-insights.spec.js` | 272 | Signal table, filtering, sorting, opportunity cards |
| `watchlist-portfolio.spec.js` | 309 | Watchlist CRUD, add-to-portfolio integration |
| `stock-management-portfolio.spec.js` | 406 | CSV import, portfolio context, modals |

**Framework:** Playwright (base URL: `localhost:8080`, auto-starts `mvn spring-boot:run`)
**Total E2E lines:** 2,085 across 5 spec files (56 total test files across backend + E2E)

### 11.3 Code Coverage

**JaCoCo** configured via Maven plugin — report at `prepare-package` and `test` phases.

---

## 12. Configuration Reference

### 12.1 application.properties

| Category | Property | Value |
|----------|----------|-------|
| Server | `server.port` | `8080` |
| Database | `spring.datasource.url` | `jdbc:mysql://localhost:3306/stockmarket?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` |
| Database | `spring.datasource.driverClassName` | `com.mysql.cj.jdbc.Driver` |
| Database | `spring.datasource.username` | `root` |
| Database | `spring.datasource.password` | `root` |
| JPA | `spring.jpa.hibernate.ddl-auto` | `update` |
| JPA | `spring.jpa.show-sql` | `false` |
| Dhan API | `dhan.api.base-url` | `https://api.dhan.co` |
| Dhan API | `dhan.api.client-id` | `1101658114` |
| Timezone | `spring.jackson.time-zone` | `IST` |
| Actuator | `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` |
| Static Cache | `spring.web.resources.cache.cachecontrol.max-age` | `0` |
| Static Cache | `spring.web.resources.cache.cachecontrol.no-cache` | `true` |

### 12.2 HikariCP

| Property | Value |
|----------|-------|
| Pool Name | `StockMarketPool` |
| Max Pool Size | 15 |
| Min Idle | 5 |
| Connection Timeout | 30s |
| Idle Timeout | 600s |
| Max Lifetime | 1800s |
| Leak Detection | 60s |

### 12.3 Resilience4j

| Property | Value |
|----------|-------|
| Sliding Window Size | 10 |
| Failure Rate Threshold | 50% |
| Wait Duration (Open) | 30s |
| Half-Open Calls | 3 |
| Time Limiter Timeout | 30s |

### 12.4 Logging

| Logger | Level |
|--------|-------|
| `org.springframework.web` | INFO |
| `org.apache.tomcat` | WARN |
| `org.hibernate` | ERROR |
| `com.stocks` | DEBUG |

### 12.5 Async Thread Pool

| Bean | Core | Max | Queue | Prefix |
|------|------|-----|-------|--------|
| `syncExecutor` | 2 | 4 | 20 | `sync-` |

### 12.6 Resilience4j Circuit Breaker Instances

| Instance | Purpose |
|----------|---------|
| `nseSession` | NSE session API calls (shareholding, deals) |
| `nseXbrl` | NSE XBRL filing API calls |

---

## 13. Error Handling

### 13.1 Global Exception Handler (`@RestControllerAdvice`)

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `StockNotFoundException` | 404 | `STOCK_NOT_FOUND` |
| `NoPriceDataException` | 404 | `NO_PRICE_DATA` |
| `StockHistoryNotFoundException` | 404 | `HISTORY_NOT_FOUND` |
| `ResourceNotFoundException` | 404 | `NOT_FOUND` |
| `PriceAlreadyExistsException` | 409 | `PRICE_ALREADY_EXISTS` |
| `InvalidPriceException` | 400 | `INVALID_PRICE` |
| `MethodArgumentNotValidException` | 400 | Validation field errors |
| `IllegalArgumentException` | 400 | `ILLEGAL_ARGUMENT` |
| `ConstraintViolationException` | 400 | `CONSTRAINT_VIOLATION` |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` |

All exception messages are sanitized (strips `\r\n\t`) to prevent log injection (CWE-117). The `GlobalExceptionHandler` also handles `MethodArgumentNotValidException` (field-level validation errors), `IllegalArgumentException`, and `ConstraintViolationException` (from `@Validated` path variables).

---

## 14. Security

| Aspect | Status |
|--------|--------|
| Authentication | **None** — all endpoints public |
| Authorization | **None** |
| CORS | Wide open — all origins (`*`) on `/api/**` |
| API Keys | Not enforced |
| HTTPS | Not configured |
| Input Validation | Jakarta Validation (`@NotBlank`, `@DecimalMin`, etc.) |
| Log Injection | Protected via `sanitize()` method |

---

## 15. Monitoring

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Application health with details (always show-details) |
| `/actuator/info` | Application info |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/prometheus` | Prometheus-format metrics |

**Custom Metrics (`InstitutionalMetrics` class):**

| Meter Name | Type | Description |
|------------|------|-------------|
| `institutional.score.computations` | Counter | Score computation count |
| `institutional.score.computation.time` | Timer | Score computation duration |
| `institutional.nse.api.calls` | Counter | NSE API call volume |
| `institutional.nse.api.errors` | Counter | Failed NSE API calls |
| `institutional.cache.evictions` | Counter | Institutional score cache evictions |

---

## 16. SQL Migrations

No Flyway/Liquibase — manual migration scripts:

| File | Purpose |
|------|---------|
| `V1__widen_indicator_type_column.sql` | `ALTER TABLE technical_indicators MODIFY COLUMN indicator_type VARCHAR(50) NOT NULL` |
| `V2__widen_indicator_value_column.sql` | `ALTER TABLE technical_indicators MODIFY COLUMN value DECIMAL(18,0) NOT NULL` |

---

## 17. Table Summary (23 tables)

| # | Table | Entity | Unique Constraints | Indexes |
|---|-------|--------|-------------------|---------|
| 1 | `stocks` | Stock | `symbol` | — |
| 2 | `portfolios` | Portfolio | — | — |
| 3 | `portfolio_holdings` | PortfolioHolding | `(portfolio_id, stock_id)` | — |
| 4 | `portfolio_snapshots` | PortfolioSnapshot | `(portfolio_id, stock_id, snapshot_date)` | `idx_snapshot_stock_date`, `idx_snapshot_portfolio_date` |
| 5 | `daily_prices` | DailyPrice | `(stock_id, price_date)` | `idx_stock_date` |
| 6 | `fundamental_data` | FundamentalData | unique FK `stock_id` | — |
| 7 | `technical_indicators` | TechnicalIndicator | `(stock_id, indicator_type, calculation_date)` | `idx_stock_indicator_date` |
| 8 | `rsi_values` | RsiValue (deprecated) | `(stock_id, calculation_date)` | `idx_stock_rsi_date` |
| 9 | `signal_records` | SignalRecord | `(stock_id, recorded_at)` | `idx_signal_record_stock_date` |
| 10 | `signal_historical_performance` | SignalHistoricalPerformance | — | `idx_shp_rec_days` |
| 11 | `support_resistance_levels` | SupportResistanceLevel | `(stock_id, level_type, calculation_date, level_order)` | `idx_sr_stock_date`, `idx_sr_stock_type` |
| 12 | `fiidii_data` | FiiDiiData | `date` | — |
| 13 | `corporate_events` | CorporateEvent | `(symbol, event_date, purpose)` | `idx_event_symbol_date` |
| 14 | `institutional_holdings` | InstitutionalHolding | `(stock_id, quarter_end_date)` | `idx_inst_hold_stock_date` |
| 15 | `bulk_deals` | BulkDeal | `(stock_id, deal_date, client_name, buy_sell, quantity, trade_price)` | `idx_bulk_deal_screener` |
| 16 | `block_deals` | BlockDeal | `(stock_id, deal_date, client_name, buy_sell, quantity, trade_price)` | `idx_block_deal_screener` |
| 17 | `watchlists` | Watchlist | — | — |
| 18 | `watchlist_items` | WatchlistItem | `(watchlist_id, stock_id)` | `idx_wi_watchlist` |
| 19 | `strategy_config` | StrategyConfig | `strategy_name` | — |
| 20 | `strategy_condition_groups` | StrategyConditionGroup | `(strategy_name, condition_id)` | — |
| 21 | `strategy_condition_stats_cache` | StrategyConditionStatsCache | `(strategy_name, condition_id)` | — |
| 22 | `shadow_signal_records` | ShadowSignalRecord | `(stock_id, recorded_at, shadow_version)` | `idx_shadow_signal_stock_date_version` |
| 23 | `shadow_signal_gate_block_reasons` | *(ElementCollection)* | — | — |

**Note:** 22 JPA entity tables + 1 `@ElementCollection` join table = 23 total database tables.

---

## 18. Documentation Directory

The `docs/` directory contains project documentation and strategy specifications:

| File | Size | Description |
|------|------|-------------|
| `PROJECT_CONTEXT.md` | 5,089 | Overall project context and architecture |
| `PRODUCT_VISION.md` | 1,478 | Product vision and goals |
| `AI_TEAM_GUIDELINES.md` | 4,056 | AI team collaboration guidelines |
| `API_SPEC.md` | 4,578 | API specification details |
| `DATABASE_DESIGN.md` | 3,993 | Database design documentation |
| `SIGNAL_ENGINE_SPEC.md` | 2,195 | Signal engine specification |
| `BACKTESTING_RULES.md` | 2,776 | Backtesting rules and methodology |
| `RISK_MANAGEMENT.md` | 1,500 | Risk management strategies |
| `PRICE_ACTION_RULES.md` | 1,448 | Price action trading rules |
| `BREAKOUT_DETECTION.md` | 1,239 | Breakout detection methodology |
| `TARGET_CALCULATION.md` | 1,255 | Target price calculation logic |
| `SWING_TRADING_STRATEGY.md` | 1,392 | Swing trading strategy documentation |

**Total:** 12 documentation files (~28 KB)

---

## 19. Change Log

### BRD v2.3 — Updated 2026-06-29 (Calculator Bugfixes, Pipeline Optimization, UX Fixes)

**Bug Fixes — Backend:**

1. **Bollinger Band stddev formula corrected** (`BollingerUpperCalculator.java`, `BollingerLowerCalculator.java`)
   - **Before:** Population stddev — `Math.sqrt(variance / period)`
   - **After:** Sample stddev — `Math.sqrt(variance / (period - 1))`
   - **Impact:** Bands now match TradingView behavior (slightly wider)

2. **Null safety in Ichimoku & Ultimate Oscillator calculators** (`IchimokuCalculator.java`, `UltimateOscillatorCalculator.java`)
   - Added null checks for `getHighPrice()`/`getLowPrice()` in Ichimoku `midpoint()`
   - Null fallbacks for UltimateOscillator when high/low prices are absent
   - **Impact:** No more NPE on first bar or sparse data

3. **Channel Override Value Trap fix** (`SignalService.java`)
   - **Problem:** Channel override forced BUY on bottom-15% 52W stocks regardless of trend
   - **Fix:** Added `weeklyTrendOk` guard — requires weekly RSI > 40 before overriding HOLD→BUY
   - **Impact:** Prevents buying into fundamentally bearish stocks just because they're cheap

4. **Proximity Score Bias fix** (`SignalService.java`)
   - **Problem:** 52-week proximity (+2) and S/R channel position (+4) scores gave +6 total near 52W low regardless of trend
   - **Fix:** Both scores zeroed when trend is bearish (SMA20 < SMA50). In downtrend, only SELL signals amplified at channel top
   - **Impact:** Downtrending stocks (e.g., GAIL) no longer get false BUY signals throughout decline

5. **Inline Computation Removal** (`SignalService.java`)
   - **Problem:** SMA, MACD, RSI, BB computed inline AND read from DB — wasted work + formula mismatch risk
   - **Fix:** Restructured `computeBaseSignalDto()` to fetch from DB first, compute inline only as fallback
   - Removed old override block (lines 296-315) that patched inline values with DB values
   - **Impact:** Eliminated redundant computation; indicators always consistent with DB

6. **ADX Caching** (`AdxCalculator.java`, `PlusDiCalculator.java`, `MinusDiCalculator.java`)
   - **Problem:** PlusDi and MinusDi calculators each called `computeAdxResult()` independently — 3x redundant O(N) work per stock
   - **Fix:** `AdxCalculator` caches `AdxResult` in static field; DI calculators read from cache
   - **Impact:** ~66% reduction in ADX computation per stock pipeline

7. **SignalService reads precomputed indicators** (`SignalService.java`)
   - `fetchAdditionalIndicators()` now queries RSI, SMA_20, SMA_50, MACD_LINE, MACD_SIGNAL, BOLLINGER_UPPER, BOLLINGER_LOWER from DB
   - Fetches first, computes inline only as fallback

**Bug Fixes — Frontend:**

8. **Stock Detail Page fixes** (`stock-detail.js`)
   - **TDZ ReferenceError:** Moved `factorFields`/`totalFactors` before `showAdxFilterDetail` (line 1583)
   - **Race condition:** Replaced AbortController with generation counter (`_loadAllGen`)
   - **Backtest days fallback:** `runBacktest()` reads `days` from DOM when called without argument
   - **Bollinger zone detection:** Added `latestPrice` fallback when `lastTradedPrice` is null
   - **Percentage display:** Removed `* 100` from `trendStrength`/`volatility` (backend returns %)

9. **Candlestick HOLD markers added** (`stock-detail.js`)
   - **Before:** HOLD signals filtered out, only BUY/SELL arrows visible
   - **After:** HOLD signals shown as gray dots (circle shape, inBar position)
   - **Impact:** All 3 signal types visible: green arrows (BUY), red arrows (SELL), gray dots (HOLD)

**Features:**

10. **3-Dimension High-Confidence BUY Gate** (`SignalService.java`)
    - New config flag: `signal.gate.high-confidence.enabled` (default: `false` for safe rollout)
    - Three gate dimensions: trend (SMA20 > SMA50) + momentum (RSI < 75) + risk:reward (price ≤ SMA20)
    - When blocked: recommendation downgraded to HOLD, `gateBlocked=true`, `gateBlockReasons` lists failures
    - DTO additions: `SignalDTO.gateBlocked`, `SignalDTO.gateBlockReasons`

11. **Default Portfolio Isolation** (`stock-management.js`)
    - `loadStocks()` now awaits `loadPortfoliosMgmt()` so `currentPortfolioId` is set
    - Auto-selects default portfolio in `loadPortfoliosMgmt()`

12. **CSV Import Upsert** (`stock-management.js:693-714`)
    - Checks `getPortfolioHoldingByStock()` first → calls `updateHolding()` if exists, `addHolding()` if new

**Tests Added (6 new):**

| Test | File | What It Validates |
|------|------|-------------------|
| `testBollingerUpper_UsesSampleStddev_NotPopulation` | `BollingerUpperCalculatorTest.java` | Verifies N-1 stddev formula |
| `testComputeWeightedScore_NullIndicators` | `SignalServiceTest.java` | Null indicators don't throw NPE |
| `testComputeWeightedScore_BullishSetup` | `SignalServiceTest.java` | Bullish setup produces positive score |
| `testComputeWeightedScore_BearishSetup` | `SignalServiceTest.java` | Bearish setup produces negative score |
| `testComputeWeightedScore_LowAdxDampensScore` | `SignalServiceTest.java` | Low ADX dampens score via multiplier |
| `testGetBuyAndSellSignals_FilterByRecommendation` | `SignalServiceTest.java` | Buy/sell filters match recommendation string |

**Updated Counts:**

| Category | Before (v2.2) | After (v2.3) | Change |
|----------|--------------|-------------|--------|
| Backend Test Files | 50 | 51 | +1 (BollingerUpperCalculatorTest) |
| SignalServiceTest methods | 100 | 104 | +4 |
| stock-detail.js lines | 2,923 | 3,242 | +319 |
| Total frontend JS lines | 7,909 | 8,162 | +253 |
| Total frontend HTML lines | 4,200 | 4,324 | +124 |
| Total frontend source lines | 12,495 | 12,872 | +377 |

---

### BRD v2.4 — Updated 2026-06-30 (Strategy Config Persistence)

**New Feature: Strategy Config Persistence**

Strategy active/inactive state now persists to the database so toggles survive page refresh and reflect consistently across the site.

**Components Added:**

| Component | File | Description |
|-----------|------|-------------|
| Entity | `StrategyConfig.java` | `strategy_config` table with `strategy_name`, `active`, `display_name`, `priority` |
| Repository | `StrategyConfigRepository.java` | `findByStrategyName()`, `findAllByOrderByStrategyNameAsc()` |
| Service | `StrategyConfigService.java` | Auto-seed on first startup via `@PostConstruct`; `getActiveStrategyNames()`, `toggleStrategy()`, `getAllConfigs()` |
| Controller | `StrategyConfigController.java` | `GET /api/strategy-config`, `PUT /api/strategy-config/{strategyName}` |
| API Module | `api.js` | `getStrategyConfigs()`, `toggleStrategyConfig()` |

**Modified:**

| File | Change |
|------|--------|
| `MultiStrategySignalEngine.java` | Reads `StrategyConfigService.getActiveStrategyNames()` as default instead of returning all strategies |
| `strategy.html` | `loadStrategyConfig()` called from `init()`; toggle persists to DB with optimistic UI + rollback on failure |

**Key Design Decisions:**
- **Global state** (not per-user) — simpler, no auth needed
- **`?active=` query param still works** as per-request override; DB state used only when no param provided
- **Auto-seed on first startup** — all 5 strategies seeded as active for backward compatibility
- **`DataIntegrityViolationException` caught** on seed for concurrency safety

**Tests Added (19 new):**

| Test File | Tests | Description |
|-----------|-------|-------------|
| `StrategyConfigServiceTest.java` | 9 | Auto-seed, getActiveStrategyNames, toggleStrategy, getAllConfigs, toggle on non-existent, set active/inactive, find by name |
| `StrategyConfigControllerTest.java` | 4 | GET list, PUT toggle, 404 on non-existent strategy |
| `MultiStrategySignalEngineTest.java` | 6 | Updated with `@Mock StrategyConfigService`, verifies DB state used as default |

**Updated Counts:**

| Category | Before (v2.3) | After (v2.4) | Change |
|----------|--------------|-------------|--------|
| JPA Entities | 19 | 20 | +1 |
| Repositories | 19 | 20 | +1 |
| Controllers | 17 | 18 | +1 |
| Services (top-level) | 30 | 31 | +1 |
| Backend Test Files | 51 | 53 | +2 |
| Total Backend Tests | ~630 | ~649 | +19 |
| REST Endpoints | 113 | 114 | +1 |
| Database Tables | 20 | 21 | +1 |

---

### BRD v2.5 — Updated 2026-06-30 (Strategy Manager with Condition Editor)

**New Feature: Strategy Manager with Editable Conditions**

Extends the strategy toggle module into a full strategy management page with per-strategy signal conditions, priority controls, and live stock match counts.

**Components Added (7 new files):**

| Component | File | Description |
|-----------|------|-------------|
| Entity | `StrategyConditionGroup.java` | `strategy_condition_groups` table with `strategy_name`, `condition_id`, `signal_type`, `field_label`, `operator`, thresholds, `confidence`, `display_order` |
| Entity | `StrategyConditionStatsCache.java` | `strategy_condition_stats_cache` table — cached stock match counts per condition |
| Repository | `StrategyConditionGroupRepository.java` | `findByStrategyNameOrderByDisplayOrder()`, `deleteByStrategyName()` |
| Repository | `StrategyConditionStatsCacheRepository.java` | `findByStrategyNameAndConditionId()`, `findByStrategyName()`, `deleteByStrategyName()` |
| DTO | `StrategyConditionGroupDTO.java` | Request/response for condition create/update |
| DTO | `StrategyConditionFullDTO.java` | Full condition with `stockCount` |
| DTO | `StrategyFullDTO.java` | Full strategy with tags, conditions, stock counts |
| DTO | `ConditionStatDTO.java` | `conditionId` + `stockCount` + `computedAt` |
| Service | `StrategyConditionService.java` | Auto-seeds 25 default conditions (5 per strategy); validates/saves conditions; async compute-and-cache stats; reset to defaults |
| Frontend | `strategy-manager.js` | Full IIFE module — renders strategy rows, rules panels, inline edit forms, save/reset with dirty tracking |
| Frontend | `strategy.html` (replaced) | Full strategy manager page with sidebar nav, expandable rules panel, Save button, unsaved changes guard |

**Modified:**
| File | Change |
|------|--------|
| `StrategyConfigService.java` | Added `updatePriority()` method |
| `StrategyConfigController.java` | 5 new endpoints: full config, stats, save conditions, reset, update priority |
| `api.js` | Added `getStrategyFull()`, `getStrategyStats()`, `saveStrategyConditions()`, `updateStrategyPriority()`, `resetStrategyConditions()` |

**Default Conditions Seeded (25 total):**
| Strategy | Conditions |
|----------|------------|
| RSI | rsi_strong_buy (RSI<30, BUY), rsi_buy (RSI<40, BUY), rsi_sell (RSI>70, SELL), rsi_strong_sell (RSI>80, SELL), rsi_neutral (RSI 30-70, HOLD) |
| MACD | macd_buy_cross (crossover_above, BUY), macd_buy_positive (both>0, BUY), macd_sell_cross (crossover_below, SELL), macd_sell_negative (both<0, SELL), macd_neutral (HOLD) |
| MA_CROSSOVER | ma_buy_golden (golden_cross, BUY), ma_buy_aligned (alignment_bullish, BUY), ma_sell_death (death_cross, SELL), ma_sell_aligned (alignment_bearish, SELL), ma_neutral (HOLD) |
| BOLLINGER | bb_buy_lower (price<lower, BUY), bb_buy_mid (within lower-mid, BUY), bb_sell_upper (price>upper, SELL), bb_sell_mid (within mid-upper, SELL), bb_neutral (HOLD) |
| VOLUME | vol_buy_spike (volume>=1.5x avg, BUY), vol_buy_obv (obv_up, BUY), vol_sell_spike (volume>=1.5x avg, SELL), vol_sell_obv (obv_down, SELL), vol_neutral (HOLD) |

**Condition Operators Supported:**
`<`, `>`, `<=`, `>=`, `==`, `between`, `within`, `crossover_above`, `crossover_below`, `both_above`, `both_below`, `alignment_bullish`, `alignment_bearish`, `golden_cross`, `death_cross`, `mixed`, `obv_up`, `obv_down`

**Key Design Decisions:**
- `@EventListener(ApplicationReadyEvent.class)` for seed (not `@PostConstruct`) — ensures Hibernate DDL creates tables before seed
- `StrategyConditionGroup` uses `strategyName` as plain String (no JPA FK) — avoids coupling to `StrategyConfig`
- Stats cache uses `deleteAll` + `saveAll` — acceptable volume (25 rows max)
- Condition evaluation operators interpreted in Java `switch` — no dynamic scripting
- Frontend uses IIFE pattern (consistent with existing codebase)
- Frontend toggle is optimistic (update DOM first, API call second, rollback on error)
- Save button persists per-strategy changes individually

**Tests Added (18 new):**
| Test File | Tests | Description |
|-----------|-------|-------------|
| `StrategyConditionServiceTest.java` | 13 | Seed count, idempotency, DataIntegrityException, getConditions, save/delete, invalid signal/operator/confidence validation, computeAndCacheStats counting & skip-null, getCachedStats grouping, resetToDefaults, between operator evaluation |
| `StrategyConfigControllerTest.java` | 5 | Full config, stats, save conditions, reset, update priority |

**Bug Fix:**
- **Reserved word column name fixed** (`StrategyConditionGroup.java`): Renamed column `signal` → `signal_type` to avoid MySQL reserved word conflict with Hibernate unquoted SQL generation

**Updated Counts:**

| Category | Before (v2.4) | After (v2.5) | Change |
|----------|--------------|-------------|--------|
| JPA Entities | 20 | 22 | +2 |
| Repositories | 20 | 22 | +2 |
| DTOs | 43 | 47 | +4 |
| Services (top-level) | 31 | 32 | +1 |
| Controllers | 18 | 18 | 0 (extended existing) |
| Backend Test Files | 53 | 55 | +2 |
| Total Backend Tests | ~649 | ~686 | +37 |
| REST Endpoints | 114 | 119 | +5 |
| Database Tables | 21 | 23 | +2 |
| Frontend JS Files | 11 | 12 | +1 |
| Total Frontend JS lines | 8,162 | ~8,690 | +528 |

---

### BRD v2.2 — Updated 2026-06-28 (Score/Recommendation Desync + Frontend Fixes)

**Bug Fixes:**

1. **BUY gate and channel override no longer desync score from recommendation** (`SignalService.java:534-595`)
   - **Before:** `dto.setRecommendation("HOLD")` and `dto.setCompositeScore(score)` called independently after `mapRecommendation()`, causing score to not match recommendation
   - **After:** All adjustments modify `score` variable directly; `dto.setRecommendation(mapRecommendation(score))` and `dto.setCompositeScore(score)` called exactly once at the end
   - **Impact:** `compositeScore` now always matches `recommendation`

2. **`getBuySignals()` and `getSellSignals()` filter by recommendation string** (`SignalService.java:121-133`)
   - **Before:** Filtered by `compositeScore >= 5` / `compositeScore <= -5`, inconsistent with `mapRecommendation()` thresholds (BUY ≥ 3, SELL ≤ −4)
   - **After:** Filters by `"BUY"/"STRONG BUY"` / `"SELL"/"STRONG SELL"` recommendation strings
   - **Impact:** Stocks with score 3–4 (BUY) now appear in `getBuySignals()`

3. **Threshold constants extracted** (`SignalService.java:52-56`)
   - New `static final` constants: `STRONG_BUY_THRESHOLD=7`, `BUY_THRESHOLD=3`, `SELL_THRESHOLD=-4`, `STRONG_SELL_THRESHOLD=-7`
   - `mapRecommendation()` uses constants instead of magic numbers
   - `computeOldLogicSignal()` (ShadowSignalService) annotated with comment explaining intentional threshold divergence (A/B baseline)

4. **Shadow signal new-logic computation fixed** (`ShadowSignalService.java:177-185`, `SignalService.java:610-619`)
   - **Before:** `computeNewLogicSignal()` constructed `new SignalService(...)` with null dependencies → NPE swallowed silently, shadow signals never computed
   - **After:** Injected real `SignalService` bean via `@Lazy` setter; new `computeShadowDto()` method runs base computation without persisting
   - **Impact:** New-logic shadow signals now compute correctly for A/B comparison

5. **Frontend `priceVsSma20` and `totalReturn` inflation fixed** (`stock-detail.js:2332,2787`)
   - **Before:** `(signal.priceVsSma20 * 100).toFixed(2)` and `(r.totalReturn * 100).toFixed(2)` — doubled already-scaled percentage values
   - **After:** `signal.priceVsSma20.toFixed(2)` and `r.totalReturn.toFixed(2)`
   - **Impact:** Values now display correctly (e.g., 3.61% instead of 361%)

**Tests Added (9 validation tests in `SignalServiceTest`):**

| Test | Bug | What It Validates |
|------|-----|-------------------|
| `testComputeShadowDto_ScoreMatchesRecommendation` | Bug 2 | After full pipeline, `recommendation == mapRecommendation(compositeScore)` |
| `testComputeShadowDto_MultipleStocks_AllConsistent` | Bug 2 | Consistency across 5 different price patterns |
| `testGetBuyAndSellSignals_FilterByRecommendation` | Bug 3 | Filter predicates include score=3 BUY and score=-4 SELL |
| `testGetBuySignals_Score3PreviouslyExcluded` | Bug 3 | Old filter excluded score=3, new filter includes it |
| `testGetSellSignals_ScoreN4PreviouslyExcluded` | Bug 3 | Old filter excluded score=-4, new filter includes it |
| `testComputeShadowDto_NullPrices_ReturnsNull` | Bug 1 | computeShadowDto handles null input |
| `testComputeShadowDto_InsufficientPrices_ReturnsNull` | Bug 1 | computeShadowDto handles prices < 20 |
| `testComputeShadowDto_SufficientPrices_ReturnsDto` | Bug 1 | computeShadowDto produces valid DTO with consistent recommendation |

**Updated Test Counts:**

| Category | Before | After | Change |
|----------|--------|-------|--------|
| SignalServiceTest | 91 | 100 | +9 |
| Total Backend | ~582 | ~556 | (547 unit + 9 new = 556) |

---

### BRD v2.1 — Updated 2026-06-28 (Signal Engine Bug Fixes)

**Bug Fixes:**

1. **BUY threshold corrected** (`SignalService.java`)
   - **Before:** `score >= 5` mapped to BUY
   - **After:** `score >= 3` maps to BUY (matches spec)
   - **Impact:** Scores of 3-4 were incorrectly classified as HOLD instead of BUY

2. **SELL threshold corrected** (`SignalService.java`)
   - **Before:** `score <= -5` mapped to SELL
   - **After:** `score <= -4` maps to SELL (matches spec)
   - **Impact:** Score of -4 was incorrectly classified as HOLD instead of SELL

3. **Overbought dampener integer truncation fixed** (`SignalService.java`)
   - **Before:** `(int)(score * 0.7)` — truncated towards zero
   - **After:** `(int) Math.round(score * 0.7)` — rounds to nearest integer
   - **Impact:** Minor 1-point discrepancy for non-multiple-of-10 scores (e.g., score=5 → 3 instead of 4)

4. **Extracted `mapRecommendation()` method** for testability
   - Package-private method replaces inline if/else chain
   - Enables direct unit testing of threshold boundaries

**Tests Added (19 new tests in `SignalServiceTest`):**

| Test Category | Count | Description |
|---------------|-------|-------------|
| `mapRecommendation` boundary tests | 14 | Every boundary value (7, 3, 2, 0, -3, -4, -7) and neighbors |
| ADX counter-trend dampening | 3 | Counter-trend reduces score, with-trend doesn't, adxFilterApplied flag |
| Overbought dampener rounding | 1 | Verifies Math.round behavior |
| Downtrend bearish dampener | 1 | Downtrend score < uptrend score |

**Updated Test Counts:**

| Category | Before | After | Change |
|----------|--------|-------|--------|
| SignalServiceTest | 72 | 91 | +19 |
| Total Backend | ~580 | ~582 | +19 (net, accounting for new tests) |

---

### BRD v2.0 — Updated 2026-06-28

**New Features Added:**

1. **Shadow Signal System** — A/B comparison framework for signal accuracy analysis
   - New entity: `ShadowSignalRecord` (19th JPA entity)
   - New repository: `ShadowSignalRecordRepository`
   - New service: `ShadowSignalService`
   - New config: `ShadowSignalConfig` + `application-shadow.properties`
   - New DTOs: `SignalComparisonDTO`, `SignalComparisonReportDTO`
   - New page: `shadow_signal_comparison.html` (Thymeleaf)

2. **Watchlist Opportunity Ranking** — Ranked buy/sell opportunities for portfolio holdings
   - New controller: `WatchlistOpportunityController` (`GET /api/watchlist/opportunities`)
   - New service: `WatchlistOpportunityService`
   - New DTOs: `WatchlistOpportunityDTO`, `WatchlistOpportunityResponseDTO`, `WatchlistSummaryDTO`, `EntryZoneDTO`, `ReasonDTO`
   - New page: `watchlist-opportunities.html` + `watchlist-opportunities.js`

3. **Candlestick Pattern Detection** — 10-pattern candlestick analysis
   - New calculator: `CandlestickPatternCalculator`

4. **Reversal Detection** — Multi-flag reversal confirmation system
   - New calculator: `ReversalDetector`

5. **Generic Calculator Helpers** — Configurable-period SMA/EMA calculators
   - New calculators: `SmaCalculator`, `EmaCalculator`

6. **Consolidated Ichimoku** — All 5 Ichimoku components in one calculator
   - New calculator: `IchimokuCalculator`

7. **Stock Split Detection** — Automatic detection and adjustment for stock splits
   - New service: `StockSplitDetector`

8. **Price Aggregation** — Daily to weekly/monthly OHLC aggregation
   - New service: `PriceAggregationService`

9. **Client Classification** — Bulk/block deal client categorization
   - New institutional service: `ClientClassifier`

10. **PostgreSQL Schema Support** — Alternative database backend DDL
    - New file: `schema.sql.postgresql`

**Updated Counts:**

| Category | Before | After | Change |
|----------|--------|-------|--------|
| JPA Entities | 18 | 19 | +1 |
| Enums | 2 | 2 | — |
| DTOs | 36 | 43 | +7 |
| Controllers | 18 | 18 | — (WatchlistOpportunityController replaces inactive SignalComparisonController) |
| Services (top-level) | 28 | 30 | +2 |
| Institutional Services | 7 | 8 | +1 |
| Calculators | 26 | 29 | +3 |
| Repositories | 18 | 19 | +1 |
| Cron Jobs | 11 | 12 | +1 |
| Config Classes | 2 | 3 | +1 |
| Frontend Pages | 10 | 12 | +2 |
| Frontend JS Files | 10 | 11 | +1 |
| Backend Tests | 41 | 50 | +9 |
| REST Endpoints | 112 | 113 | +1 |
| Tables | 18 | 20 | +2 |
