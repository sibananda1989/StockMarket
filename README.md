# Stock Market Analysis Platform

A full-stack Java Spring Boot web application for capturing, storing, and analyzing stock market data. It provides daily OHLCV price tracking, 21 technical indicators, RSI-14 calculation, multi-indicator signal generation, strategy backtesting, portfolio tracking, and integration with live market data providers (Dhan Brokerage API, Yahoo Finance, NSE India).

---

## Table of Contents

- [Executive Summary](#executive-summary)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Database Analysis](#database-analysis)
- [API Analysis](#api-analysis)
- [Frontend Analysis](#frontend-analysis)
- [Business Features](#business-features)
- [Institutional Activity Engine](#institutional-activity-engine)
- [Multi-Portfolio Management](#multi-portfolio-management)
- [Stock Lifecycle](#stock-lifecycle)
- [Signal Engine](#signal-engine)
- [Backtesting](#backtesting)
- [Technical Indicators](#technical-indicators)
- [Stock Split Detection](#stock-split-detection)
- [Scheduled Tasks](#scheduled-tasks)
- [External Data Integrations](#external-data-integrations)
- [Caching](#caching)
- [Error Handling](#error-handling)
- [Code Quality Assessment](#code-quality-assessment)
- [Extension Points](#extension-points)
- [Prerequisites](#prerequisites)
- [Database Setup](#database-setup)
- [Installation](#installation)
- [Running the Application](#running-the-application)
- [Configuration](#configuration)
- [Project Structure](#project-structure)
- [Testing](#testing)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## Executive Summary

### What Is This Application?

This is a **personal stock portfolio management and technical analysis platform** for Indian stock market (NSE/BSE) investors. It enables users to:

- Track stock holdings with real-time P&L
- Run 21 technical indicators on daily OHLCV data
- Generate algorithmic BUY/SELL/HOLD signals with a weighted composite scoring system
- Backtest trading strategies with trailing stop-loss and position sizing
- Manage watchlists for stocks of interest
- Track institutional investor sentiment (FII/DII flows)
- Monitor corporate events (dividends, AGMs, board meetings)

### Most Important Modules

| Module | Size | Purpose |
|--------|------|---------|
| `SignalService` | 34.6k | Core signal generation engine with 15-component weighted scoring |
| `TechnicalAnalysisService` + 22 calculators | 6.7k + 22 files | Indicator computation pipeline |
| `BacktestService` | 8k | Strategy simulation with risk management |
| `YahooFinanceService` + `DhanSyncService` | 13.7k + 6.5k | Data ingestion from external APIs |
| `StockService` + `DailyPriceService` | 9.1k + 4.7k | Core CRUD and portfolio calculations |
| `Institutional Activity Engine` (8 services) | 2.7k total | FII/DII/MF shareholding tracking, bulk/block deals, institutional scoring (0-100), 7 screeners |
| `PortfolioService` + `PortfolioSnapshotService` | 4.9k + 8.9k | Multi-portfolio management with named portfolios and holdings |
| `portfolio.js` + `stock-detail.js` | 34k + 86k | Primary frontend visualization |

### Primary User Workflows

```
1. Add stocks      → Manual entry, CSV import, or Dhan sync
2. Sync prices     → Yahoo Finance or Dhan API → Daily OHLCV data stored
3. Calculate       → RSI, SMA, MACD, etc. computed and persisted
4. Generate signals → Multi-indicator weighted score → BUY/SELL/HOLD recommendation
5. View dashboard  → Portfolio P&L, signal table, charts, sector allocation
6. Analyze stock   → Detail view with 6 charts, AI insights, backtest
7. Manage lists    → Create watchlists, add stocks, sync history
```

### How to Safely Contribute

1. Read this README and the `.opencode/commands/stockmarket.md` project context
2. Run `mvn spring-boot:run` (requires MySQL on localhost:3306)
3. Follow the existing layered architecture: **Controller → Service → Repository → Entity**
4. Use `ApiResponse<T>` envelope for all new endpoints
5. Add tests alongside new features (follow existing patterns in `src/test/`)
6. Use `@Valid` for input validation and custom exceptions for error handling
7. Frontend: static HTML + Tailwind CSS + vanilla JS, API functions in `api.js`
8. Check existing code for similar patterns before implementing new features

---

## Technology Stack

### Backend

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.5 |
| Build Tool | Maven | with JaCoCo coverage |
| Data Access | Spring Data JPA (Hibernate) | 6.4.4 |
| Database | MySQL (primary), PostgreSQL (alternative) | 8+ |
| Template Engine | Thymeleaf | (minimal use) |
| Validation | Jakarta Validation (Hibernate Validator) | — |
| HTTP Client | Spring WebFlux `WebClient` + `java.net.http.HttpClient` | — |
| Caching | Spring Cache + Caffeine | — |
| Scheduling | `@Scheduled` + Spring Retry | — |
| Async | `@Async` + `ThreadPoolTaskExecutor` (core=2, max=4, queue=20) | — |
| Monitoring | Spring Boot Actuator (health, metrics, Prometheus) | — |
| Circuit Breaker | Resilience4j (spring-boot3, circuitbreaker, timelimiter) | 2.2.0 |
| Documentation | springdoc OpenAPI UI | 1.6.14 |
| Utilities | Lombok, Netty DNS (macOS) | — |
| Timezone | IST | — |

### Frontend

| Component | Technology |
|-----------|-----------|
| Pages | HTML5 (static files served by Spring Boot) |
| Styling | Tailwind CSS (CDN), custom theme CSS |
| Scripting | Vanilla JavaScript (ES6, Fetch API) |
| Charting | Chart.js 4.4.0 + Financial plugin (candlestick) + Annotations plugin |
| Icons | Font Awesome 6.4 (CDN) |
| Testing | Playwright (E2E) |

### External Integrations

| Provider | Purpose | Auth | Rate Limit |
|----------|---------|------|-----------|
| Yahoo Finance | Stock search, symbol validation, historical OHLCV | None (public API) | 1 req/s |
| Dhan Brokerage API | Holdings sync, historical candle data | Access Token + Client ID | 5 req/s |
| NSE India | FII/DII data, corporate events calendar, shareholding patterns | Cookie-based (session) | 0.5 req/s |
| NSE India (Bulk/Block) | Bulk deals, block deals data via historical API + CSV archives | Cookie-based (session) | 0.5 req/s |
| NSE XBRL | Detailed quarterly FII/DII/MF shareholding filings (XBRL XML parser) | Public (nsearchives) | No rate limit |

### Testing

| Layer | Framework | Files |
|-------|----------|-------|
| Backend Unit | JUnit 5 + Mockito | 24 test files |
| Backend Integration | SpringBootTest | 3 test files |
| Frontend E2E | Playwright | 2 spec files |

---

## Architecture

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    BROWSER (Frontend)                     │
│  Static HTML + Tailwind CSS + Vanilla JS + Chart.js      │
│  Pages: Dashboard, Stock Detail, Watchlist, etc.          │
│  api.js → fetch() → REST API calls                       │
└──────────────────────┬───────────────────────────────────┘
                       │ HTTP (port 8080)
┌──────────────────────▼───────────────────────────────────┐
│               SPRING BOOT APPLICATION                     │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  CONTROLLERS (15 REST controllers, 68 endpoints)    │  │
│  └──────────────────┬──────────────────────────────────┘  │
│                     │                                      │
│  ┌──────────────────▼──────────────────────────────────┐  │
│  │  SERVICES (24 service classes)                       │  │
│  │  ├── SignalService (34.6k) ← CORE BUSINESS LOGIC    │  │
│  │  ├── BacktestService (8k)                            │  │
│  │  ├── TechnicalAnalysisService (6.7k)                 │  │
│  │  ├── YahooFinanceService (13.7k)                     │  │
│  │  ├── DhanSyncService (6.5k)                          │  │
│  │  ├── PortfolioSnapshotService (8.9k)                 │  │
│  │  └── 18 other services                               │  │
│  └──────────────────┬──────────────────────────────────┘  │
│                     │                                      │
│  ┌──────────────────▼──────────────────────────────────┐  │
│  │  CALCULATORS (22 indicator calculators)               │  │
│  │  RSI, SMA(20/50/200), EMA(20), MACD, Bollinger,     │  │
│  │  Stochastic K/D, Williams %R, ATR, CCI, ADX,         │  │
│  │  +DI/-DI, OBV, ROC, StochRSI, Ultimate Osc, S/R     │  │
│  └──────────────────┬──────────────────────────────────┘  │
│                     │                                      │
│  ┌──────────────────▼──────────────────────────────────┐  │
│  │  REPOSITORIES (11 Spring Data JPA repos)              │  │
│  └──────────────────┬──────────────────────────────────┘  │
│                     │                                      │
│  ┌──────────────────▼──────────────────────────────────┐  │
│  │  CACHE (Caffeine: 5 caches, 15min TTL, 200 entries) │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  SCHEDULERS (3 cron jobs + 2 startup tasks)          │  │
│  │  ├── Daily 4:15PM IST: Dhan sync + indicators       │  │
│  │  ├── Daily 9AM IST: Safety-net indicator recalc      │  │
│  │  ├── Weekly Sun 7AM UTC: Yahoo backfill              │  │
│  │  ├── Daily 2:30AM: Signal performance precompute     │  │
│  │  └── Startup: Indicator calc + portfolio snapshot    │  │
│  └─────────────────────────────────────────────────────┘  │
└──────────────────────┬───────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────┐
│                    MySQL (stockmarket)                     │
│  11 tables, ddl-auto=update                               │
└──────────────────────────────────────────────────────────┘
```

### Module Map

```
org.example
├── StockTrackerApplication.java          # Entry point (@SpringBootApplication)
├── CacheConfig.java                      # Caffeine cache configuration
├── config/
│   ├── CorsConfig.java                   # CORS (all origins on /api/**)
│   └── ClientAbortSilencerFilter.java    # Broken-pipe exception handler
├── controller/                           # 17 REST controllers
│   ├── StockController.java              # Stock CRUD + portfolio + search (13 endpoints)
│   ├── StockHistoryController.java       # Yahoo Finance history (8 endpoints)
│   ├── StockSyncController.java          # Data sync (2 endpoints)
│   ├── PriceController.java              # OHLCV CRUD (3 endpoints)
│   ├── RsiController.java                # RSI endpoints (4 endpoints)
│   ├── TechnicalIndicatorController.java # Indicator endpoints (3 endpoints)
│   ├── SignalController.java             # Signal endpoints (4 endpoints)
│   ├── PortfolioController.java          # Portfolio history + screener (3 endpoints)
│   ├── PortfolioManagementController.java# Multi-portfolio CRUD + holdings (9 endpoints)
│   ├── BacktestController.java           # Backtesting (1 endpoint)
│   ├── WatchlistController.java          # Watchlist CRUD + items (11 endpoints)
│   ├── SupportResistanceController.java  # S/R levels (5 endpoints)
│   ├── DhanController.java               # Dhan brokerage sync (2 endpoints)
│   ├── FiiDiiController.java             # FII/DII data (2 endpoints)
│   ├── CorporateEventController.java     # Corporate events (2 endpoints)
│   ├── InstitutionalHoldingController.java # Shareholding, deals, scores, screeners (20 endpoints)
│   └── StockHistoryViewController.java   # Thymeleaf view (1 endpoint)
├── dto/                                  # 32 Data Transfer Objects
│   ├── ApiResponse.java                  # Universal envelope {status, message, data, timestamp}
│   ├── StockDTO.java
│   ├── StockDetailsDTO.java
│   ├── SignalDTO.java                    # 30+ fields including composite score
│   ├── BacktestResultDTO.java
│   ├── PortfolioSnapshotDTO.java
│   ├── PortfolioAggregateDTO.java
│   ├── PortfolioDTO.java                 # Multi-portfolio DTO with holdings
│   ├── HoldingDTO.java                   # Portfolio-stock link with computed P&L
│   ├── AddHoldingRequest.java
│   ├── CreatePortfolioRequest.java
│   ├── InstitutionalHoldingDTO.java      # Quarterly FII/DII/MF holding data
│   ├── InstitutionalScoreDTO.java        # 0-100 composite score with 8 components
│   ├── ScreenerResultDTO.java            # Combined screener results
│   ├── BulkDealDTO.java / BlockDealDTO.java
│   ├── SymbolValidationResult.java
│   └── 20 other DTOs
├── entity/                               # 15 JPA entities + 2 enums
│   ├── Stock.java                        # Hub entity (still has legacy portfolio fields)
│   ├── DailyPrice.java                   # OHLCV (UK: stock_id + price_date)
│   ├── RsiValue.java                     # DEPRECATED — superseded by TechnicalIndicator
│   ├── TechnicalIndicator.java           # Generic EAV for 21 indicator types
│   ├── IndicatorType.java                # Enum (21 values)
│   ├── SupportResistanceLevel.java       # 11 level types (UK: stock_id + type + date + order)
│   ├── LevelType.java                    # Enum (11 values)
│   ├── PortfolioSnapshot.java            # Daily position snapshots
│   ├── Portfolio.java                    # Named portfolio (Retirement, Trading, etc.)
│   ├── PortfolioHolding.java             # Links Stock → Portfolio with qty/avg price
│   ├── InstitutionalHolding.java         # Quarterly FII/DII/MF shareholding %
│   ├── BulkDeal.java                     # Bulk deals with client classification
│   ├── BlockDeal.java                    # Block deals with client classification
│   ├── Watchlist.java / WatchlistItem.java
│   ├── CorporateEvent.java
│   ├── FiiDiiData.java
│   └── SignalHistoricalPerformance.java
├── exception/                            # 6 custom exceptions + GlobalExceptionHandler
│   ├── StockNotFoundException.java       # 404
│   ├── NoPriceDataException.java         # 404
│   ├── StockHistoryNotFoundException.java # 404
│   ├── ResourceNotFoundException.java    # 404 (generic)
│   ├── PriceAlreadyExistsException.java  # 409
│   ├── InvalidPriceException.java        # 400
│   └── GlobalExceptionHandler.java       # @RestControllerAdvice (includes ConstraintViolation, sanitization)
├── metrics/
│   └── InstitutionalMetrics.java         # Custom Micrometer counters/timers for institutional engine
├── repository/                           # 15 Spring Data JPA repositories
│   ├── StockRepository.java
│   ├── DailyPriceRepository.java
│   ├── RsiValueRepository.java
│   ├── TechnicalIndicatorRepository.java
│   ├── SupportResistanceLevelRepository.java
│   ├── PortfolioSnapshotRepository.java
│   ├── PortfolioRepository.java          # Multi-portfolio repository
│   ├── PortfolioHoldingRepository.java   # Portfolio-stock link repository
│   ├── InstitutionalHoldingRepository.java
│   ├── BulkDealRepository.java
│   ├── BlockDealRepository.java
│   ├── WatchlistRepository.java / WatchlistItemRepository.java
│   ├── CorporateEventRepository.java
│   ├── FiiDiiDataRepository.java
│   └── SignalHistoricalPerformanceRepository.java
├── scheduler/                            # 4 scheduled task classes
│   ├── MarketAnalysisScheduler.java      # Dhan sync, indicators, backfill
│   ├── SignalPerformanceScheduler.java   # Forward returns precomputation
│   ├── InstitutionalHoldingScheduler.java # Shareholding + deals + XBRL (5 cron jobs)
│   └── IndicatorStartupTask.java         # On startup: calc indicators if missing
├── startup/
│   ├── PortfolioMigrationStartupTask.java # One-time migration: stock→portfolio_holdings
│   └── PortfolioSnapshotStartupTask.java  # On startup: ensure today's snapshots
├── service/                              # 28+ service classes
│   ├── SignalService.java                # CORE: Signal generation engine (34.6k)
│   ├── BacktestService.java              # Strategy backtesting
│   ├── TechnicalAnalysisService.java     # Indicator calculation orchestration
│   ├── TechnicalAnalysisUtils.java       # RSI calc + divergence detection
│   ├── YahooFinanceService.java          # Yahoo Finance API client
│   ├── DhanApiService.java               # Dhan API v2 client (WebClient)
│   ├── DhanSyncService.java              # Dhan data synchronization
│   ├── StockService.java                 # Stock CRUD + portfolio logic
│   ├── PortfolioService.java             # Multi-portfolio CRUD + P&L computation
│   ├── PortfolioSnapshotService.java     # Portfolio snapshot management
│   ├── WatchlistService.java             # Watchlist business logic
│   ├── SupportResistanceService.java     # S/R level management
│   ├── FiiDiiService.java                # FII/DII data from NSE
│   ├── CorporateEventService.java        # Corporate events from NSE
│   ├── DailyPriceService.java            # Price CRUD + triggers
│   ├── PriceAggregationService.java      # Weekly/monthly price aggregation
│   ├── ApiRateLimiter.java               # In-memory rate limiting for external APIs
│   ├── PositionSizingService.java        # Risk-based position sizing
│   ├── TrailingStopService.java          # ATR-based trailing stop-loss
│   ├── StockHistoryService.java          # Yahoo Finance history wrapper
│   ├── StockSyncService.java             # Backfill orchestration
│   ├── YahooFinanceSyncService.java      # Persist Yahoo data
│   ├── TechnicalIndicatorPersistenceService.java # Indicator CRUD
│   ├── StockSplitDetector.java           # Detects & adjusts stock splits in price data
│   ├── RsiCalculatorService.java         # Thin facade: delegates RSI math to RsiCalculator (deprecated, removal in v2)
│   └── institutional/                    # 8 institutional activity services
│       ├── InstitutionalHoldingService.java  # NSE shareholding fetch + QoQ change
│       ├── InstitutionalScoreService.java    # 8-component 0-100 scoring engine
│       ├── InstitutionalScreenerService.java # 7 stock screeners (FII/DII/MF/Deals)
│       ├── BulkDealService.java              # NSE bulk deals fetch + classification
│       ├── BlockDealService.java             # NSE block deals fetch + classification
│       ├── ClientClassifier.java             # Regex-based FII/MF/DII/Promoter/Retail
│       ├── NseSessionManager.java            # NSE cookie session mgmt + circuit breaker
│       └── NseXbrlShareholdingService.java   # XBRL XML parser for detailed FII/DII/MF
└── service/calculator/                   # 22 technical indicator calculators
    ├── IndicatorCalculator.java          # Interface: calculate(List<DailyPrice>): BigDecimal
    ├── RsiCalculator.java                # RSI-14 (Wilder's smoothing)
    ├── SmaCalculator.java                # Generic SMA
    ├── Sma20/50/200Calculator.java       # Period-specific SMA wrappers
    ├── EmaCalculator.java                # Generic EMA
    ├── Ema20Calculator.java              # EMA-20 wrapper
    ├── MacdLineCalculator.java           # MACD = EMA12 - EMA26
    ├── MacdSignalCalculator.java         # Signal = EMA9 of MACD
    ├── BollingerUpper/LowerCalculator.java
    ├── StochK/DCalculator.java           # Stochastic Oscillator
    ├── WilliamsRCalculator.java          # Williams %R
    ├── ATRCalculator.java                # Average True Range (14)
    ├── CCICalculator.java                # Commodity Channel Index (20)
    ├── StochRsiCalculator.java           # Stochastic RSI
    ├── AdxCalculator.java                # ADX with +DI/-DI (14)
    ├── PlusDi/MinusDiCalculator.java     # Directional indicators
    ├── UltimateOscillatorCalculator.java # 7/14/28 period
    ├── RocCalculator.java                # Rate of Change (12)
    ├── ObvCalculator.java                # On-Balance Volume
    └── SupportResistanceCalculator.java  # Swing H/L, Pivots, Major levels
```

### Layer Responsibilities

| Layer | Responsibility | Count |
|-------|---------------|-------|
| **Controller** | HTTP request/response handling, input validation, DTO marshaling | 17 |
| **Service** | Business logic, orchestration, external API calls, caching | 28+ |
| **Calculator** | Pure mathematical indicator computation (no I/O) | 22 |
| **Repository** | Database access via Spring Data JPA + custom native queries | 15 |
| **Entity** | JPA domain model with annotations | 15 |
| **DTO** | API request/response data contracts | 32 |
| **Scheduler** | Cron-triggered batch operations | 4 |
| **Exception** | Error classification and response formatting | 7 |
| **Metrics** | Custom Micrometer counters/timers for monitoring | 1 |

### Dependency Flow

```
Controller → Service → Repository → Database
              ↓
         Calculator (pure functions)
              ↓
         External API (Yahoo, Dhan, NSE)
```

---

## Database Analysis

### ORM & Configuration

- **ORM Framework**: Spring Data JPA (Hibernate 6.x with Jakarta Persistence)
- **DDL Strategy**: `spring.jpa.hibernate.ddl-auto=update` (Hibernate auto-creates/updates tables)
- **Migration Tool**: Manual SQL scripts in `src/main/resources/migrations/` (not Flyway/Liquibase)
- **Primary Database**: MySQL 8+ (`jdbc:mysql://localhost:3306/stockmarket`)
- **Alternative**: PostgreSQL via `schema.sql.postgresql`

### Entity Relationship Diagram

```
stocks
  |
  +--< daily_prices (stock_id FK, ON DELETE CASCADE)
  |     Unique: (stock_id, price_date)
  |     Index: idx_stock_date (stock_id, price_date DESC)
  |
  +--< rsi_values (DEPRECATED, stock_id FK, ON DELETE CASCADE)
  |     Unique: (stock_id, calculation_date)
  |     Index: idx_stock_rsi_date (stock_id, calculation_date DESC)
  |
  +--< technical_indicators (stock_id FK, ON DELETE CASCADE)
  |     Unique: (stock_id, indicator_type, calculation_date)
  |     Index: idx_stock_indicator_date (stock_id, indicator_type, calculation_date DESC)
  |
  +--< support_resistance_levels (stock_id FK, ON DELETE CASCADE)
  |     Unique: (stock_id, level_type, calculation_date, level_order)
  |     Indexes: idx_sr_stock_date, idx_sr_stock_type
  |
  +--< portfolio_snapshots (stock_id FK)
        Unique: (stock_id, snapshot_date)
        Index: idx_snapshot_stock_date (stock_id, snapshot_date DESC)

watchlists
  |
  +--< watchlist_items (watchlist_id FK)
        Unique: (watchlist_id, stock_id)
        Index: idx_wi_watchlist (watchlist_id)
        +--> stocks (stock_id FK)

Standalone tables (no FK relationships):
  - fiidii_data             Unique: (date)
  - corporate_events        Unique: (symbol, event_date, purpose)
                            Index: idx_event_symbol_date (symbol, event_date)
  - signal_historical_performance  No explicit index (missing!)
```

### Table Details

#### `stocks` — Stock Master Table (Hub Entity)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, auto-increment | |
| `symbol` | VARCHAR(20) | UNIQUE, NOT NULL, indexed | Trading symbol |
| `name` | VARCHAR(255) | NOT NULL | Company name |
| `sector` | VARCHAR(100) | Nullable | Industry sector |
| `yahoo_symbol` | VARCHAR(20) | Nullable | Yahoo Finance ticker (e.g. `RELIANCE.NS`) |
| `quantity` | INT | Nullable | Holdings quantity |
| `avg_price` | DECIMAL(10,2) | Nullable | Average buy price |
| `last_traded_price` | DECIMAL(10,2) | Nullable | Latest market price |
| `investment` | DECIMAL(12,2) | Nullable | quantity × avg_price |
| `current_value` | DECIMAL(12,2) | Nullable | quantity × last_traded_price |
| `pnl` | DECIMAL(12,2) | Nullable | current_value − investment |
| `pnl_percent` | DECIMAL(8,2) | Nullable | (pnl / investment) × 100 |
| `volume` | BIGINT | Nullable | Latest trading volume |
| `created_at` | TIMESTAMP | Auto-set | |
| `updated_at` | TIMESTAMP | Auto-updated | |

**Relationships**: Parent of daily_prices, rsi_values, technical_indicators, support_resistance_levels, portfolio_snapshots. Referenced by watchlist_items.

**Business Rules**: Symbol must be unique. Portfolio fields are computed (not user-entered). `getOrCreateStock()` is idempotent.

#### `daily_prices` — Daily OHLCV Data

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK |
| `stock_id` | BIGINT | FK → stocks, ON DELETE CASCADE |
| `closing_price` | DECIMAL(10,2) | NOT NULL |
| `opening_price` | DECIMAL(10,2) | Nullable |
| `high_price` | DECIMAL(10,2) | Nullable |
| `low_price` | DECIMAL(10,2) | Nullable |
| `volume` | BIGINT | Nullable |
| `price_date` | DATE | NOT NULL |
| `created_at` | TIMESTAMP | Auto-set |

**Unique**: (stock_id, price_date) — one record per stock per trading day
**Business Rules**: Foundation for all calculations. Triggers RSI calculation and portfolio recalc on save.

#### `technical_indicators` — Generic Indicator Storage (EAV Pattern)

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK |
| `stock_id` | BIGINT | FK → stocks, ON DELETE CASCADE |
| `indicator_type` | VARCHAR(50) | NOT NULL (enum string) |
| `value` | DECIMAL(10,4) | NOT NULL |
| `calculation_date` | DATE | NOT NULL |
| `created_at` | TIMESTAMP | Auto-set |

**Unique**: (stock_id, indicator_type, calculation_date)
**IndicatorType enum** (21 values): RSI, SMA_20, SMA_50, SMA_200, EMA_20, MACD_LINE, MACD_SIGNAL, BOLLINGER_UPPER, BOLLINGER_LOWER, STOCH_K, STOCH_D, WILLIAMS_R, ATR, CCI, STOCH_RSI, ADX, PLUS_DI, MINUS_DI, ULTIMATE_OSC, ROC_12, OBV

**Migration Note**: Column widened via `V1__widen_indicator_type_column.sql` — Hibernate's `ddl-auto=update` does NOT widen columns in MySQL.

#### `support_resistance_levels`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK |
| `stock_id` | BIGINT | FK → stocks, ON DELETE CASCADE |
| `level_type` | VARCHAR(30) | NOT NULL (enum string) |
| `level_value` | DECIMAL(12,4) | NOT NULL |
| `level_order` | INT | NOT NULL, default 0 |
| `calculation_date` | DATE | NOT NULL |
| `strength_score` | DECIMAL(5,2) | Nullable |
| `touch_count` | INT | Nullable |
| `lookback_days` | INT | Nullable |
| `created_at` | TIMESTAMP | Auto-set |

**LevelType enum** (11 values): SWING_LOW, SWING_HIGH, PIVOT_P, PIVOT_S1/S2/S3, PIVOT_R1/R2/R3, MAJOR_SUPPORT, MAJOR_RESISTANCE

#### `portfolio_snapshots` — Daily Position Snapshots

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK |
| `stock_id` | BIGINT | FK → stocks |
| `snapshot_date` | DATE | NOT NULL |
| `quantity` | INT | Nullable |
| `avg_price` | DECIMAL(10,2) | Nullable |
| `last_traded_price` | DECIMAL(10,2) | Nullable |
| `investment` | DECIMAL(12,2) | Nullable |
| `current_value` | DECIMAL(12,2) | Nullable |
| `pnl` | DECIMAL(12,2) | Nullable |
| `pnl_percent` | DECIMAL(8,2) | Nullable |
| `volume` | BIGINT | Nullable |
| `created_at` | TIMESTAMP | Auto-set |

**Unique**: (stock_id, snapshot_date)
**Business Rules**: Created by startup task and daily scheduler. Used for portfolio charts and P&L trends. **Note**: NOT cascade-deleted when stock is deleted.

#### Other Tables

| Table | Purpose | Unique Constraint |
|-------|---------|-------------------|
| `rsi_values` | **DEPRECATED** — RSI storage (superseded by technical_indicators) | (stock_id, calculation_date) |
| `watchlists` | Named stock lists | (name) |
| `watchlist_items` | Junction table (watchlist ↔ stock) | (watchlist_id, stock_id) |
| `corporate_events` | NSE corporate calendar (dividends, AGMs, meetings) | (symbol, event_date, purpose) |
| `fiidii_data` | Daily institutional investor flows | (date) |
| `signal_historical_performance` | Precomputed forward returns by recommendation type | No explicit unique |
| `portfolios` | Named portfolios (Retirement, Trading, etc.) | — |
| `portfolio_holdings` | Junction: stock → portfolio with qty/price | (portfolio_id, stock_id) |
| `institutional_holdings` | Quarterly FII/DII/MF/Promoter shareholding % | (stock_id, quarter_end_date) |
| `bulk_deals` | NSE bulk deals with client classification | (stock_id, deal_date, client_name, buy_sell, quantity, trade_price) |
| `block_deals` | NSE block deals with client classification | (stock_id, deal_date, client_name, buy_sell, quantity, trade_price) |

### Table Summary

| Table | Est. Rows | Key Relationships | Notes |
|-------|----------|-------------------|-------|
| `stocks` | 10–100 | Parent of 5 tables | Hub entity (has legacy portfolio fields) |
| `daily_prices` | 1K–50K | FK → stocks | Largest table, ON DELETE CASCADE |
| `technical_indicators` | 20K–1M | FK → stocks | 21 types × stocks × days |
| `rsi_values` | 1K–50K | FK → stocks | **DEPRECATED** |
| `support_resistance_levels` | 1K–10K | FK → stocks | 11 level types |
| `portfolio_snapshots` | 1K–10K | FK → stocks | Daily position tracking |
| `portfolios` | 1–5 | Parent of portfolio_holdings | Named multi-portfolio support |
| `portfolio_holdings` | 5–50 | FK → portfolios, stocks | Links stocks to portfolios |
| `institutional_holdings` | 100–1K | FK → stocks | Quarterly shareholding patterns |
| `bulk_deals` | 500–5K | FK → stocks | Client-classified bulk transactions |
| `block_deals` | 200–2K | FK → stocks | Client-classified block transactions |
| `watchlists` | 1–5 | Parent of watchlist_items | User-created lists |
| `watchlist_items` | 5–50 | FK → watchlists, stocks | Junction table |
| `corporate_events` | 100–1K | Standalone | NSE calendar data |
| `fiidii_data` | 100–1K | Standalone | Daily institutional flows |
| `signal_historical_performance` | 50–500 | Standalone | **Missing index** |

---

## API Analysis

### Response Envelope

All REST endpoints (except `TechnicalIndicatorController`) wrap responses in:

```json
{
  "status": "success" | "error",
  "message": "Human-readable message",
  "data": { ... },
  "timestamp": "2026-06-09T12:00:00Z"
}
```

### Authentication

**None.** No Spring Security, no JWT, no API key enforcement. All ~97 endpoints are fully public. CORS is configured to allow all origins on `/api/**`. The only filter is `ClientAbortSilencerFilter` for broken-pipe handling.

### Complete Endpoint Inventory (~97 endpoints)

#### Stock Management (13 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/stocks` | List all stocks with latest price, RSI, P&L |
| POST | `/api/stocks` | Create stock (symbol unique, not blank, max 20 chars) |
| PUT | `/api/stocks/{id}` | Update stock (prevents symbol collision) |
| DELETE | `/api/stocks/{id}` | Delete stock (cascade: prices, indicators, S/R, RSI) |
| GET | `/api/stocks/{id}` | Stock detail (90-day prices + RSI history) |
| GET | `/api/stocks/validate-symbol` | Validate symbol via Yahoo Finance |
| GET | `/api/stocks/search` | Search stocks by name (min 3 chars, Yahoo Finance) |
| POST | `/api/stocks/csv-import` | Bulk import from CSV data |
| GET | `/api/stocks/{id}/daily-prices?days=90` | Price history (last N days) |
| PATCH | `/api/stocks/{id}/portfolio` | Update portfolio data |
| GET | `/api/stocks/{id}/snapshots?days=90` | Portfolio snapshots |
| GET | `/api/stocks/{id}/snapshots/all` | All portfolio snapshots |
| POST | `/api/stocks/recalculate-portfolio` | Recalculate all portfolios |

#### Data Sync & History (10 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/stocks/history?symbol={s}&days={d}` | Yahoo Finance summary (cached) |
| GET | `/api/stocks/history/data?symbol={s}&days={d}` | Raw Yahoo OHLCV (cached) |
| POST | `/api/stocks/history/save` | Save Yahoo data to DB |
| POST | `/api/stocks/sync-history` | Backfill all stocks (skip if ≥365 days) |
| POST | `/api/stocks/{id}/sync-history?days=730` | Sync single stock |
| POST | `/api/stocks/portfolio/sync` | Sync portfolio from Yahoo |
| GET | `/api/stocks/history/summary/all?days=30` | All summaries (Yahoo) |
| GET | `/api/stocks/history/summary/local?days=30` | All summaries (local DB) |
| POST | `/api/stocks/cache/clear` | Clear stockHistory cache |
| GET | `/api/stocks/test` | Health check |

#### Price Data (3 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/prices` | Save daily price (validates: price > 0, date not future) |
| GET | `/api/prices/stock/{stockId}?fromDate=&toDate=` | Price history with date range |
| GET | `/api/prices/stock/{stockId}/latest` | Latest price (404 if none) |

#### Technical Indicators (7 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/rsi/stock/{stockId}` | Latest RSI-14 |
| GET | `/api/rsi/stock/{stockId}/history?days=30` | RSI history |
| POST | `/api/rsi/calculate/{stockId}` | Calculate RSI for one stock |
| POST | `/api/rsi/calculate-all` | Calculate RSI for all stocks |
| GET | `/api/indicators/{stockId}` | All latest indicators (cached) |
| GET | `/api/indicators/{stockId}/{type}?fromDate=&toDate=` | Indicator history by type |
| POST | `/api/indicators/calculate/{stockId}` | Trigger indicator recalculation |

**Note**: `TechnicalIndicatorController` returns raw DTOs (not wrapped in `ApiResponse`).

#### Signals (4 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/signals` | All signals (computed on-demand) |
| GET | `/api/signals/buy` | Buy signals (compositeScore ≥ 3) |
| GET | `/api/signals/sell` | Sell signals (compositeScore ≤ -3) |
| GET | `/api/signals/{stockId}` | Signal for specific stock |

#### Portfolio History (3 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/portfolio/history?days=365&portfolioId=` | Aggregated portfolio history (supports multi-portfolio) |
| POST | `/api/portfolio/backfill?force=false` | Backfill snapshots (60 days) |
| GET | `/api/portfolio/screener?date=` | Portfolio screener by date |

#### Multi-Portfolio Management (9 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/portfolios` | List all portfolios |
| POST | `/api/portfolios` | Create portfolio (name unique, max 100 chars) |
| GET | `/api/portfolios/{id}` | Portfolio detail with holdings + P&L |
| DELETE | `/api/portfolios/{id}` | Delete portfolio (cannot delete default) |
| GET | `/api/portfolios/default` | Get or create default portfolio |
| GET | `/api/portfolios/{id}/holdings` | List holdings with computed P&L |
| POST | `/api/portfolios/{id}/holdings` | Add holding (stock, quantity, avg price) |
| PATCH | `/api/portfolios/{portfolioId}/holdings/{holdingId}` | Update holding quantity/price |
| DELETE | `/api/portfolios/{portfolioId}/holdings/{holdingId}` | Remove holding from portfolio |
| POST | `/api/portfolios/{id}/recalculate` | Recalculate P&L for all holdings |

#### Backtesting (1 endpoint)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/backtest/stock/{stockId}?stopLoss=true&positionSizePct=0.02` | Run backtest |

#### Watchlist (11 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/watchlists` | List all watchlists |
| POST | `/api/watchlists` | Create watchlist (name unique) |
| PUT | `/api/watchlists/{id}` | Update watchlist |
| DELETE | `/api/watchlists/{id}` | Delete watchlist (cascade items) |
| GET | `/api/watchlists/{id}/detail` | Watchlist detail with stocks |
| GET | `/api/watchlists/{id}/items` | Watchlist items |
| POST | `/api/watchlists/{id}/items` | Add stock to watchlist |
| DELETE | `/api/watchlists/{wid}/items/{sid}` | Remove stock from watchlist |
| POST | `/api/watchlists/{id}/items/batch` | Batch add stocks |
| GET | `/api/watchlists/stock/{stockId}` | Watchlists containing stock |
| POST | `/api/watchlists/{id}/sync-history?days=730` | Sync watchlist history |

#### Support/Resistance (5 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/support-resistance/{stockId}` | Latest S/R levels |
| GET | `/api/support-resistance/{stockId}/as-of?date=` | S/R as of specific date |
| GET | `/api/support-resistance/{stockId}/history` | S/R history |
| POST | `/api/support-resistance/calculate/{stockId}` | Calculate S/R for one stock |
| POST | `/api/support-resistance/calculate-all` | Calculate all S/R (async) |

#### Institutional Activity (20 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/institutional/holdings/{stockId}` | Latest quarterly shareholding |
| GET | `/api/institutional/holdings/{stockId}/history` | Shareholding history |
| GET | `/api/institutional/holdings/all` | Latest holdings for all stocks |
| POST | `/api/institutional/holdings/fetch-all` | Fetch NSE shareholding for all stocks |
| POST | `/api/institutional/holdings/fetch/{stockId}` | Fetch NSE shareholding for one stock |
| POST | `/api/institutional/xbrl/fetch-all` | Fetch XBRL detailed filings for all stocks |
| POST | `/api/institutional/xbrl/fetch/{stockId}` | Fetch XBRL detailed filings for one stock |
| GET | `/api/institutional/bulk-deals/{stockId}` | Bulk deals for a stock |
| GET | `/api/institutional/bulk-deals/range` | Bulk deals by date range |
| POST | `/api/institutional/bulk-deals/fetch` | Fetch historical bulk deals |
| POST | `/api/institutional/bulk-deals/fetch-today` | Fetch today's bulk deals |
| GET | `/api/institutional/bulk-deals/top-institutional` | Top institutional bulk deals (by value) |
| GET | `/api/institutional/block-deals/{stockId}` | Block deals for a stock |
| GET | `/api/institutional/block-deals/range` | Block deals by date range |
| POST | `/api/institutional/block-deals/fetch` | Fetch historical block deals |
| POST | `/api/institutional/block-deals/fetch-today` | Fetch today's block deals |
| GET | `/api/institutional/score/{stockId}` | Institutional score (0-100) for a stock |
| GET | `/api/institutional/score/all` | Scores for all stocks |
| GET | `/api/institutional/screeners/...` | 7 screener endpoints (fii-accumulation, dii-accumulation, mf-accumulation, institutional-strong-buy, institutional-price-action-buy, recent-bulk-deals, recent-block-deals, all) |

#### External Data (6 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/dhan/sync-holdings` | Sync Dhan holdings (upsert stocks) |
| POST | `/api/dhan/sync-prices` | Sync Dhan prices + RSI (60 days) |
| GET | `/api/fiidii` | Latest FII/DII data with sentiment |
| POST | `/api/fiidii/refresh` | Refresh FII/DII from NSE |
| GET | `/api/events?symbol=` | Corporate events for symbol |
| POST | `/api/events/refresh` | Refresh events from NSE |

#### Monitoring (4 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/actuator/health` | Health check |
| GET | `/actuator/info` | Application info |
| GET | `/actuator/metrics` | Metrics |
| GET | `/actuator/prometheus` | Prometheus metrics |

### Grouped by Business Capability

```
Stock Lifecycle:    13 (CRUD) + 10 (Sync) + 3 (Prices) = 26 endpoints
Analysis:           7 (Indicators) + 4 (Signals) + 5 (S/R) = 16 endpoints
Portfolio History:  3 endpoints
Multi-Portfolio:    10 endpoints (CRUD + holdings)
Institutional:      20 endpoints (holdings, XBRL, bulk/block deals, scores, screeners)
Watchlists:         11 endpoints
External Data:      6 endpoints (Dhan, FII/DII, Events)
Backtesting:        1 endpoint
Monitoring:         4 endpoints
────────────────────────────────────
Total:              ~97 endpoints
```

---

## Frontend Analysis

### Pages (9 static HTML files)

| Page | File | Size | URL | Purpose |
|------|------|------|-----|---------|
| Dashboard | `index.html` | 19.5k | `/index.html` | Main dashboard: holdings, signals, charts, P&L |
| Stock Detail | `stock-detail.html` | 26.4k | `/stock-detail.html?id={id}` | Per-stock analysis with 6 charts, AI insights, backtest, **institutional tab** |
| Stock Management | `stocks.html` | 30k | `/stocks.html` | CRUD, CSV import, client-side alerts |
| Institutional Dashboard | `institutional-dashboard.html` | 18k | `/institutional-dashboard.html` | FII/DII/MF shareholding, deal monitoring, scores, 7 screeners |
| Watchlist | `watchlist.html` | 19.9k | `/watchlist.html` | Watchlist CRUD, sector chart |
| Price Entry | `price-entry.html` | 7.8k | `/price-entry.html` | Manual OHLCV entry |
| RSI Analysis | `rsi-analysis.html` | 6.6k | `/rsi-analysis.html` | RSI overview table |
| Stock History | `stock-history.html` | 15.3k | `/stock-history.html` | Yahoo Finance history viewer |
| History Summary | `history-summary.html` | 9k | `/history-summary.html` | Batch summary view |

### JavaScript Files (9 files)

| File | Size | Responsibility |
|------|------|---------------|
| `api.js` | ~2k | Shared fetch wrapper with all API functions |
| `portfolio.js` | 34k | Dashboard: signals table, charts (line/doughnut/bar), P&L, risk cards, market regime |
| `stock-detail.js` | 86k | **Largest file** — 6 Chart.js charts, AI insights, backtest UI, candlestick |
| `stock-management.js` | 27.7k | Stock CRUD, CSV import, client-side alert rules (localStorage) |
| `watchlist.js` | 33.6k | Watchlist CRUD, stock search, sector allocation doughnut chart |
| `stock-history.js` | — | Yahoo Finance history viewer with pagination |
| `rsi-analysis.js` | — | RSI table with color coding |
| `price-entry.js` | — | Price entry form |
| `history-summary.js` | — | Batch summary view |

### Navigation Structure

Every page includes a **duplicated** navigation bar (no shared component):

```
[Dashboard] [Stock Management] [Institutional] [Watchlists] [Stock Detail] [Price Entry] [RSI Analysis] [Stock History] [History Summary]
```

### Shared UI Patterns

| Pattern | Implementation |
|---------|---------------|
| **Cards** | `.card` with Tailwind `rounded-xl p-4 shadow-lg`, `var(--bg-secondary)` |
| **Stat Cards** | Color-bordered left edge (green/red/yellow/purple/cyan/gray) |
| **Tables** | Sticky headers, sortable columns, color-coded P&L rows (10 tiers) |
| **Badges** | `.badge-success`, `.badge-warning`, `.badge-danger`, `.badge-info` |
| **Modals** | Fixed overlay with `openModal(id)` / `closeModal(id)` helpers |
| **Toasts** | Fixed-position bottom-right notifications |
| **Theme Toggle** | Dark/light mode via body class toggle |
| **Charts** | Chart.js 4.4.0 (line, doughnut, bar, candlestick with line fallback) |

### State Management

- **No framework** — simple module-level variables per page
- **Global arrays/objects**: `allStocks`, `portfolioHistory`, `allWatchlists`, `allSignals`, `charts`
- **LocalStorage**: Alert rules (`stockAlerts`), recent searches (`recentStockSearches`)
- **Chart instances**: Stored in `charts`/`_charts` objects, destroyed before re-creation to prevent memory leaks

### Institutional Dashboard (`institutional-dashboard.html`) Components

- **KPI Cards**: Total Stocks Tracked, Last Fetch Time, Average Institutional Score, High-Confidence Signals
- **Tabs**: 8 tab views — Score Overview, FII Accumulation, DII Accumulation, MF Accumulation, Bulk Deals, Block Deals, Institutional + Price Action, All Screeners Combined
- **Score Distribution**: Visual bar chart showing score distribution across stocks (0-20, 20-40, 40-60, 60-80, 80-100 buckets)
- **Score Table**: Columns for symbol, score value, 8 component breakdowns, FII/DII/MF percentages and QoQ changes, signal recommendation
- **Bulk/Block Deal Tables**: Client name, deal date, buy/sell, quantity, trade price, client category, institutional badge
- **Screener Results**: Combined view with all 7 screener criteria as togglable columns
- **Fetch Controls**: Buttons for fetching holdings, XBRL data, bulk deals, block deals

### Dashboard (`portfolio.js`) Components

- **KPI Cards**: Total Investment, Current Value, Total P&L, Total P&L%, Holdings Count, Win Rate, Portfolio Risk, Market Regime
- **Charts**: Portfolio Value Trend (line), Sector Allocation (doughnut), P&L Distribution (horizontal bar)
- **Signals & Insights**: Filtered table with symbol, RSI, target/stop-loss, confidence, position sizing, MACD, 52-week metrics, composite score
- **Signals Filter**: Symbol search, RSI range, confidence, composite score, recommendation checkboxes
- **Top/Bottom Performers**: 5 best/worst P&L% stocks with links
- **Watched Stocks**: Watchlist stocks not yet in portfolio
- **Holdings Table**: Full sortable table with all portfolio fields

### Stock Detail (`stock-detail.js`) Components

- **KPIs**: LTP, P&L, P&L%, Investment, Current Value, RSI 14
- **FII/DII Sentiment Bar**
- **Corporate Events Warning** (next 5 days)
- **6 Charts** (stacked vertically):
  1. Price Movement (line)
  2. Candlestick Chart (with line fallback)
  3. P&L Trend (line + bar side by side)
  4. Investment vs Current Value (dual line)
  5. RSI(14) (line with overbought/oversold zones, crosshair)
  6. Support & Resistance (line with S/R annotations)
- **AI Insights**: Signal Analysis, Technical Indicators (grouped by category), Price Analysis, Risk Assessment
- **Screener**: Signal summary + snapshot history
- **Backtest**: Trailing stop-loss toggle, risk per trade, results (win rate, return, drawdown, trade list)
- **Institutional Tab**: FII/DII/MF shareholding trends, institutional score, bulk/block deal history
- **Watchlist Integration**: Add/remove from watchlists

---

## Business Features

### Feature 1: Portfolio Dashboard

- **Purpose**: At-a-glance view of all holdings with P&L, signals, charts
- **User Flow**: Load page → Fetch all stocks + signals → Render KPI cards, signals table, charts
- **Backend**: `StockController.getAllStocks()` → `StockService.getAllStockDTOs()` → `SignalService.getAllSignals()`
- **Frontend**: `index.html` + `portfolio.js`
- **DB Dependencies**: `stocks`, `daily_prices`, `technical_indicators`, `signal_historical_performance`

### Feature 2: Stock Detail View

- **Purpose**: Deep analysis of individual stocks with 6 charts and AI insights
- **User Flow**: Click stock → Load detail → Display KPIs, price chart, candlestick, RSI, S/R, P&L trend, AI insights, backtest
- **Backend**: `StockController` + `SignalController` + `TechnicalIndicatorController` + `BacktestController` + `SupportResistanceController`
- **Frontend**: `stock-detail.html` + `stock-detail.js` (86k)
- **DB Dependencies**: `stocks`, `daily_prices`, `technical_indicators`, `rsi_values`, `support_resistance_levels`, `portfolio_snapshots`

### Feature 3: Signal Generation Engine

- **Purpose**: Algorithmic BUY/SELL/HOLD recommendations using 15+ weighted indicators
- **User Flow**: Triggered on-demand or via API → Computes composite score → Returns recommendation
- **Backend**: `SignalService.computeSignal()` → 15 sub-scores → ADX penalty → Volume penalty → FII/DII adjustment → Confidence → Position sizing
- **Scoring**: Divergence (±4), MACD (±3), Weekly Confluence (±3), RSI (±2), Bollinger (±2), Stochastic (±2), StochRSI (±2), plus 7 more
- **Thresholds**: ≥6 STRONG BUY, ≥3 BUY, ≤-3 SELL, ≤-6 STRONG SELL
- **DB Dependencies**: `stocks`, `daily_prices`, `technical_indicators`, `support_resistance_levels`, `fiidii_data`, `corporate_events`, `signal_historical_performance`

### Feature 4: Backtesting

- **Purpose**: Simulate trading strategy on historical data with stop-loss and position sizing
- **User Flow**: Select stock → Configure (stop-loss on/off, risk %) → Run → View win rate, return, drawdown, trade list
- **Backend**: `BacktestService.runBacktest()` → Walk through prices → Signal at each step → Simulate trades
- **Config**: Initial capital: ₹10,000, ATR period: 14, Position sizing: risk-based (1–5%)
- **DB Dependencies**: `stocks`, `daily_prices`, `corporate_events`

### Feature 5: Technical Analysis

- **Purpose**: Calculate 21 indicator types for all stocks
- **User Flow**: Triggered by scheduler or API → Calculate all indicators → Store in DB → Available for signals and display
- **Backend**: `TechnicalAnalysisService.calculateIndicatorsForStock()` → 22 calculators → `TechnicalIndicatorPersistenceService`
- **DB Dependencies**: `technical_indicators` table

### Feature 6: Watchlist Management

- **Purpose**: Organize stocks into named lists for monitoring
- **User Flow**: Create watchlist → Add stocks → View detail with signal badges → Sync history
- **Backend**: `WatchlistService` → Full CRUD + batch operations + background sync
- **Frontend**: `watchlist.html` + `watchlist.js`
- **DB Dependencies**: `watchlists`, `watchlist_items`, `stocks`

### Feature 7: Data Synchronization

- **Purpose**: Keep local database updated with latest market data
- **Sources**: Dhan API (holdings + OHLCV), Yahoo Finance (OHLCV + search), NSE (FII/DII + events)
- **Scheduled**: Daily post-market (Dhan), weekly backfill (Yahoo), daily FII/DII, daily events
- **Backend**: `DhanSyncService`, `YahooFinanceSyncService`, `StockSyncService`

### Feature 8: Support/Resistance Analysis

- **Purpose**: Identify key price levels for trading decisions
- **Methods**: Swing highs/lows (20-day), Pivot points (P/S1-S3/R1-R3), Major historical levels (180-day clustering)
- **Backend**: `SupportResistanceService` → `SupportResistanceCalculator` → DB storage + caching

### Feature 9: Institutional Activity Engine

The Institutional Activity Engine is a comprehensive system for tracking, scoring, and screening stocks based on institutional investor behavior. It pulls data from NSE India and BSE XBRL filings.

**Components:**

| Module | Lines | Purpose |
|--------|-------|---------|
| `InstitutionalHoldingService` | 338 | Fetches quarterly shareholding patterns (promoter, public %) from NSE API |
| `NseXbrlShareholdingService` | 625 | Downloads & parses XBRL XML filings for detailed FII/DII/MF/Insurance % |
| `ClientClassifier` | 135 | Regex-based client name categorization (FII / MF / DII / Promoter / Retail / Unknown) |
| `BulkDealService` | 292 | Fetches and classifies NSE bulk deals (BUY/SELL, institutional flag, client category) |
| `BlockDealService` | 271 | Fetches and classifies NSE block deals (same structure as bulk deals) |
| `InstitutionalScoreService` | 499 | Computes 0-100 composite score from 8 weighted components |
| `InstitutionalScreenerService` | 439 | 7 stock screeners based on institutional activity |
| `NseSessionManager` | 134 | Cookie-based NSE session management with Resilience4j circuit breaker |

**Institutional Score Components (0-100):**

| Component | Max Points | Condition |
|-----------|-----------|-----------|
| FII Holding QoQ Increase | 20 | Δ ≥ 2% → 20pts, Δ ≥ 1% → 15pts, Δ ≥ 0.5% → 10pts, Δ ≥ 0.1% → 5pts |
| DII Holding QoQ Increase | 15 | Δ ≥ 2% → 15pts, Δ ≥ 1% → 10pts, Δ ≥ 0.5% → 7pts, Δ ≥ 0.1% → 3pts |
| Mutual Fund Holding QoQ Increase | 15 | Same thresholds as DII |
| Institutional Bulk Purchase | 15 | Last 7d → 15pts, 30d → 10pts, 90d → 5pts |
| Institutional Block Purchase | 10 | Last 7d → 10pts, 30d → 7pts, 90d → 3pts |
| Delivery % (Volume Proxy) | 10 | Deal/Total Volume > 60% → 10pts, > 40% → 7pts, > 20% → 3pts |
| Volume Spike | 10 | Latest Vol > 2× avg → 10pts, > 1.5× → 7pts, > 1.2× → 3pts |
| Price Action Signal | 5 | STRONG BUY signal → 5pts, BUY → 4pts, RSI < 30 → 3pts |

**Grade Thresholds:** ≥ 80 = STRONG_BUY, ≥ 60 = BUY, ≥ 40 = NEUTRAL_POSITIVE, ≥ 20 = NEUTRAL_NEGATIVE, < 20 = SELL

**Screeners (7 types):**
1. **FII Accumulation** — FII holding ↑ ≥ 0.5% QoQ (or promoter ↓ > 1% as proxy)
2. **DII Accumulation** — DII holding ↑ ≥ 0.5% QoQ
3. **Mutual Fund Accumulation** — MF holding ↑ ≥ 0.5% QoQ
4. **Institutional Strong Buy** — Institutional score ≥ 60
5. **Institutional + Price Action Buy** — Score ≥ 40 AND technical signal is BUY/STRONG BUY
6. **Recent Bulk Deals** — Institutional bulk BUY in last 30 days
7. **Recent Block Deals** — Institutional block BUY in last 30 days

**Data Sources:**
- **NSE Corporate Shareholding API**: Returns promoter%, public%, employeeTrusts% per quarter for all stocks
- **NSE XBRL Filings (BSE taxonomy)**: Parses XML from nsearchives for granular FII/DII/MF/Insurance breakdowns
- **NSE Bulk/Block Deals API**: Historical and current-day bulk + block transaction data
- **NSE CSV Archives**: `archives.nseindia.com/content/equities/bulk.csv` / `block.csv`

**Scheduled Sync (see [Scheduled Tasks](#scheduled-tasks)):**
- Weekly shareholding sync (Sunday 10 AM IST)
- Daily bulk/block deals fetch (weekdays 6 PM IST, post-market)
- Weekly XBRL detailed filing fetch (Sunday 11:30 AM IST)
- Weekly deals backfill (Sunday 11 AM IST, 90-day lookback)

### Feature 10: Multi-Portfolio Management

- **Purpose**: Organize stocks into named portfolios (e.g., "Retirement", "Trading", "Demo") instead of a single flat list
- **Entities**: `Portfolio` (name, description, isDefault) + `PortfolioHolding` (stock, quantity, avgPrice)
- **Backend**: `PortfolioManagementController` (9 endpoints) + `PortfolioService` with full CRUD
- **Migration**: `PortfolioMigrationStartupTask` auto-migrates existing stocks with quantity > 0 to a "Default Portfolio" on first startup
- **Backward Compatibility**: `PortfolioController.getHistory()` accepts optional `portfolioId` parameter; defaults to all portfolios if omitted
- **Computed Fields**: Investment, Current Value, P&L, P&L% are computed on read (not persisted on PortfolioHolding)
- **Snapshot Support**: `portfolio_snapshots` table now has a `portfolio_id` column; the migration task sets it to the default portfolio for all existing records
- **Constraints**: Cannot delete the default portfolio; portfolio names must be unique

### Feature 11: Stock Split Detection

- **Purpose**: Detect stock splits in historical price data and adjust pre-split records to maintain indicator accuracy
- **Backend**: `StockSplitDetector` service
- **Detection**: Flags single-day price changes > 40% (split) or > 100% (reverse split)
- **Adjustment**: Divides all pre-split OHLCV values by the computed split ratio
- **Prevents**: Distorted RSI, moving averages, and other technical indicators across split boundaries

---

## Stock Lifecycle

### Flow 1: Stock Enters the System

```
┌──────────────┐     ┌───────────────┐     ┌──────────────┐
│ Manual Entry │────>│               │     │              │
│ (stocks.html)│     │  StockController│     │ StockService │
├──────────────┤     │  POST /api/   │     │ .createStock │
│ CSV Import   │────>│  stocks       │────>│ .getOrCreate │
│ (stocks.html)│     │               │     │  Stock()     │
├──────────────┤     └───────────────┘     └──────┬───────┘
│ Yahoo Search │                                  │
│ (stocks.html)│                                  ▼
├──────────────┤                          ┌──────────────┐
│ Dhan Holdings│──── DhanController ────>│ stocks table  │
│ (Dhan API)   │     POST /api/dhan/     │ (INSERT)      │
└──────────────┘     sync-holdings       └──────────────┘
```

### Flow 2: Stock Price Data Stored

```
┌────────────────┐     ┌──────────────────┐     ┌────────────────┐
│ Yahoo Finance  │────>│ YahooFinanceSync │────>│ daily_prices   │
│ /v8/finance/   │     │ Service          │     │ table (INSERT) │
│ chart/{symbol} │     │ .saveHistoryData │     └───────┬────────┘
└────────────────┘     └──────────────────┘             │
                                                        │ triggers
┌────────────────┐     ┌──────────────────┐             ▼
│ Dhan API       │────>│ DhanSyncService  │     ┌────────────────┐
│ /v2/charts/    │     │ .syncDailyPrices │────>│ RSI calculated │
│ historical     │     │                  │     │ Portfolio recalc│
└────────────────┘     └──────────────────┘     └────────────────┘
```

### Flow 3: Stock Analysis Pipeline

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ Scheduler    │────>│ TechnicalAnalysis │────>│ 22 Calculators   │
│ (daily cron) │     │ Service          │     │ compute indicators│
└──────────────┘     │ .calculateFor    │     └────────┬─────────┘
                     │  AllStocks()     │              │
                     └──────────────────┘              ▼
                                            ┌──────────────────┐
                                            │ technical_       │
                                            │ indicators table │
                                            │ (UPSERT)         │
                                            └──────────────────┘
                                                     │
                                                     ▼
                                            ┌──────────────────┐
                                            │ SignalService    │
                                            │ .computeSignal() │
                                            │ reads indicators │
                                            │ + prices + FII   │
                                            │ + events → score │
                                            └────────┬─────────┘
                                                     │
                                                     ▼
                                            ┌──────────────────┐
                                            │ SignalDTO        │
                                            │ recommendation,  │
                                            │ compositeScore,  │
                                            │ target, stopLoss │
                                            └──────────────────┘
```

### Flow 4: Stock Displayed on Dashboard

```
┌────────────────┐     ┌──────────────────┐
│ GET /api/stocks│────>│ StockController  │
└────────────────┘     │ .getAllStocks()   │
                       └────────┬─────────┘
                                │
                       ┌────────▼─────────┐
                       │ StockService     │
                       │ .getAllStockDTOs()│
                       │ for each stock:  │
                       │  query latest    │
                       │  daily_price     │
                       │  query latest    │
                       │  rsi_value       │
                       └────────┬─────────┘
                                │
     ┌──────────────────────────┼──────────────────────────┐
     ▼                          ▼                          ▼
┌──────────┐           ┌──────────────┐           ┌──────────────┐
│ KPI Cards│           │ Holdings     │           │ Signal Table │
│ Total P&L│           │ Table        │           │ composite    │
│ Win Rate │           │ sorted by    │           │ Score, RSI,  │
│ Risk     │           │ P&L%         │           │ Confidence   │
└──────────┘           └──────────────┘           └──────────────┘
```

### Flow 5: Stock Removed

```
┌────────────────┐     ┌──────────────────┐     ┌────────────────┐
│ DELETE /api/   │────>│ StockController  │────>│ StockService   │
│ stocks/{id}    │     │ .deleteStock()   │     │ .deleteStock() │
└────────────────┘     └──────────────────┘     └───────┬────────┘
                                                        │ CASCADE
                                                        ▼
                                               ┌────────────────┐
                                               │ stocks table   │
                                               │ daily_prices   │
                                               │ rsi_values     │
                                               │ technical_     │
                                               │  indicators    │
                                               │ support_       │
                                               │  resistance    │
                                               │ (all deleted)  │
                                               └────────────────┘
                                               NOTE: portfolio_snapshots
                                               and watchlist_items NOT
                                               cascade-deleted
```

---

## Signal Engine

The signal engine (`SignalService.computeSignalFromPrices`) performs a comprehensive multi-indicator weighted analysis across 30+ metrics.

### Computed Metrics

- **Basic Price Indicators**: SMA20, SMA50, EMA12, EMA26, MACD, MACD Signal, MACD Histogram
- **RSI & Divergence**: RSI-14, bullish divergence (price lower low + RSI higher low), bearish divergence (price higher high + RSI lower high)
- **Multi-Timeframe Confluence**: Weekly RSI and Monthly RSI (via `PriceAggregationService`)
- **Volume Confirmation**: Current volume vs. 20-day average (confirmed if ≥ 1.2× average)
- **Bollinger Bands**: Upper, middle (SMA20), lower bands
- **52-Week Proximity**: 52-week high, 52-week low, percentage from each
- **Support/Resistance**: Based on S/R service levels or bottom/top decile of closing prices
- **Trend Strength**: Percentage of up-days in last 10 periods (0–100%)
- **Volatility**: Standard deviation of daily log returns × 100
- **Corporate Event Risk**: Flags stocks with upcoming dividends, AGMs, or board meetings in next 5 days
- **FII/DII Sentiment**: Integrated score from institutional flow data

### Weighted Scoring System

**Maximum raw score: ±22** (before penalties and FII/DII adjustment)

| # | Component | Conditions | Score |
|---|-----------|-----------|-------|
| 1 | **Divergence** | Bullish divergence (price lower low + RSI higher low) | +4 |
|   |           | Bearish divergence (price higher high + RSI lower high) | −4 |
| 2 | **Weekly Confluence** | Daily RSI < 30 AND Weekly RSI < 40 | +3 |
|   |           | Daily RSI > 70 AND Weekly RSI > 60 | −3 |
| 3 | **MACD** | Histogram > 0 AND MACD > Signal line | +3 |
|   |           | Histogram < 0 AND MACD < Signal line | −3 |
| 4 | **RSI** | RSI < 30 | +2 |
|   |           | RSI > 70 | −2 |
| 5 | **Bollinger Bands** | Price < Lower Band | +2 |
|   |           | Price > Upper Band | −2 |
| 6 | **Stochastic %K/%D** | K > D AND both < 20 (oversold bullish) | +2 |
|   |           | K < D AND both > 80 (overbought bearish) | −2 |
|   |           | K > D AND K < 30 (near oversold) | +1 |
|   |           | K < D AND K > 70 (near overbought) | −1 |
| 7 | **StochRSI** | StochRSI < 20 | +2 |
|   |           | StochRSI > 80 | −2 |
|   |           | StochRSI < 30 | +1 |
|   |           | StochRSI > 70 | −1 |
| 8 | **Monthly Confluence** | Daily RSI < 30 AND Monthly RSI < 50 | +1 |
|   |           | Daily RSI > 70 AND Monthly RSI > 50 | −1 |
| 9 | **Ultimate Oscillator** | UO < 30 | +1 |
|   |           | UO > 70 | −1 |
| 10 | **ROC** | ROC > 0 (positive momentum) | +1 |
|    |           | ROC < 0 (negative momentum) | −1 |
| 11 | **Williams %R** | Williams %R < −80 | +1 |
|    |           | Williams %R > −20 | −1 |
| 12 | **CCI** | CCI < −100 | +1 |
|    |           | CCI > 100 | −1 |
| 13 | **OBV (price proxy)** | 2 consecutive up closes (buying pressure) | +1 |
|    |           | 2 consecutive down closes (selling pressure) | −1 |
| 14 | **Price vs SMA20** | Price > SMA20 by 5%+ | −1 |
|    |           | Price < SMA20 by 5%+ | +1 |
| 15 | **52-Week Proximity** | Within 5% of 52-week low | +1 |
|    |           | Within 5% of 52-week high | −1 |

### Post-Scoring Modifiers

| Modifier | Condition | Effect |
|----------|-----------|--------|
| **ADX Trend Filter** | ADX > 25 AND signal counter-trend | Score × 0.6 (40% penalty) |
| **Volume Confirmation** | `volumeConfirmed = false` AND `|score| ≥ 3` | Score × 0.6 (40% penalty) |
| **FII/DII Adjustment** | Based on institutional flow sentiment | ±0, ±1, or ±2 |

### Recommendation Thresholds

| Composite Score | Recommendation |
|-----------------|---------------|
| ≥ 6 | **STRONG BUY** |
| 3 – 5 | **BUY** |
| −2 – 2 | **HOLD** |
| −5 – −3 | **SELL** |
| ≤ −6 | **STRONG SELL** |

### Target Price and Stop Loss

| Recommendation | Stop Loss | Target Price |
|---------------|-----------|-------------|
| BUY / STRONG BUY | `price − (ATR × 2)` (floored at 0) | `price + (ATR × 3)` |
| SELL / STRONG SELL | `price + (ATR × 2)` | `price − (ATR × 3)` (floored at 0) |
| HOLD | null | null |

### Confidence Score

- Base: `50 + (compositeScore × 5)`, clamped to [0, 100]
- If `volumeConfirmed`: +10
- If `eventRisk`: −20
- `|weeklyConfluenceScore|` added (max +3)
- `|monthlyConfluenceScore|` added (max +1)
- Final clamped to [0, 100]

### Position Size

- Formula: `20.0 / volatility`
- If volatility is null or zero: 5.0%
- Clamped to [1.0, 5.0]%

### Signal Age

- `DAYS.between(latestPriceDate, LocalDate.now())`

---

## Backtesting

The backtesting engine (`BacktestService`) runs an event-driven simulation.

### Parameters

| Parameter | Default | Range |
|-----------|---------|-------|
| Initial Capital | ₹10,000 | Fixed |
| ATR Period | 14 days | Fixed |
| Stop-Loss | Enabled | On/Off |
| Position Size | 2% risk per trade | 1%–5% |
| Min Data Points | 50 prices | Fixed |
| Min Bars for Signal | 20 | Fixed |

### Algorithm

```
for each price record from index 20 to end:
  1. Compute signal on historical data up to current point
  2. If HOLDING:
     a. Update trailing stop: newStop = high - 3×ATR
     b. If stopped out (low ≤ stop): sell, record loss, update drawdown
     c. If SELL/STRONG SELL signal: sell, record win/loss
  3. If NOT HOLDING:
     a. If corporate event risk today: skip (increment eventsSkipped)
     b. If BUY/STRONG BUY signal:
        - Calculate ATR, initial stop = entry - 2×ATR
        - Position size via PositionSizingService (risk-based)
        - Enter trade, record entry price
  4. Track portfolio value and max drawdown
```

### Tracked Metrics

| Metric | Formula |
|--------|---------|
| Total Trades | Count of entries |
| Completed Trades | totalTrades / 2 (round-trips) |
| Win Rate | (winningTrades / completedTrades) × 100 |
| Total Return | ((finalValue − 10000) / 10000) × 100 |
| Avg Return Per Trade | totalReturn / completedTrades |
| Max Drawdown | Maximum peak-to-trough decline |
| Final Portfolio Value | shares × lastPrice + capital |

### Example Request

```bash
curl "http://localhost:8080/api/backtest/stock/1?stopLoss=true&positionSizePct=0.02"
```

---

## Technical Indicators

The platform calculates and persists 21 indicator types using a strategy-pattern calculator registry (`IndicatorCalculator` interface):

| Indicator | Calculator | Description | Min Data |
|-----------|-----------|-------------|----------|
| **RSI** (14) | `RsiCalculator` | Wilder's smoothing RSI | 15 prices |
| **SMA 20** | `Sma20Calculator` | 20-period Simple Moving Average | 20 |
| **SMA 50** | `Sma50Calculator` | 50-period Simple Moving Average | 50 |
| **SMA 200** | `Sma200Calculator` | 200-period Simple Moving Average | 200 |
| **EMA 20** | `Ema20Calculator` | 20-period Exponential Moving Average | 20 |
| **MACD Line** | `MacdLineCalculator` | EMA12 − EMA26 | 26 |
| **MACD Signal** | `MacdSignalCalculator` | 9-period EMA of MACD Line | 35 |
| **Bollinger Upper** | `BollingerUpperCalculator` | SMA20 + (2 × StdDev) | 20 |
| **Bollinger Lower** | `BollingerLowerCalculator` | SMA20 − (2 × StdDev) | 20 |
| **Stochastic %K** | `StochKCalculator` | (Close−Low)/(High−Low)×100 | 14 |
| **Stochastic %D** | `StochDCalculator` | 3-period SMA of %K | 17 |
| **Williams %R** | `WilliamsRCalculator` | (High−Close)/(High−Low)×−100 | 14 |
| **ATR** (14) | `ATRCalculator` | Average True Range | 15 |
| **CCI** (20) | `CCICalculator` | Commodity Channel Index | 20 |
| **StochRSI** | `StochRsiCalculator` | Stochastic formula on RSI values | 28 |
| **ADX** (14) | `AdxCalculator` | Average Directional Index | 28 |
| **+DI** | `PlusDiCalculator` | Positive Directional Indicator | 28 |
| **−DI** | `MinusDiCalculator` | Negative Directional Indicator | 28 |
| **Ultimate Oscillator** | `UltimateOscillatorCalculator` | 7/14/28 period buying pressure | 28 |
| **ROC** (12) | `RocCalculator` | Rate of Change | 13 |
| **OBV** | `ObvCalculator` | On-Balance Volume (cumulative) | 2 |

**Persistence**: Results stored in `technical_indicators` table with unique constraint on `(stock_id, indicator_type, calculation_date)`. Indicators cached with 15-minute TTL.

---

## Stock Split Detection

The `StockSplitDetector` service detects and corrects stock splits in historical OHLCV data to prevent distorted technical indicators.

### Detection Logic

- Looks for single-day close-to-close price changes exceeding **40%** (forward split) or **100%** (reverse split)
- Compares each day's closing price against the previous day's close
- Skips invalid (zero/negative) prices

### Adjustment

When a split is detected at index `i` (the first post-split trading day):

1. Computes the split ratio: `splitRatio = prevClose / currClose`
2. Divides all pre-split OHLCV values (open, high, low, close) by the split ratio
3. Saves the adjusted records back to the `daily_prices` table

### Impact

- Prevents RSI from showing false oversold/overbought signals across split boundaries
- Ensures moving averages (SMA/EMA) remain continuous
- Allows accurate MACD and Bollinger Band calculations on split-adjusted data

### Usage

```java
// In a service or scheduler:
int adjustedRecords = stockSplitDetector.detectAndFixSplits(stockId);
```

```java
// Check for recent splits (last 90 days):
boolean hasSplit = stockSplitDetector.hasRecentSplit(stockId, 90);
```

---

## Scheduled Tasks

| Job | Cron (UTC) | Cron (IST) | Description | Retry |
|-----|-----------|-----------|-------------|-------|
| `syncDhanAndCalculateIndicators` | `0 45 10 * * MON-FRI` | 4:15 PM IST (Mon–Fri) | Sync Dhan prices + RSI + all indicators + S/R levels | Yes (3 attempts, 5s backoff × 2) |
| `weeklyBackfill` | `0 0 7 * * SUN` | 12:30 PM IST (Sunday) | Yahoo Finance backfill for stocks with < 365 days | No |
| `calculateDailyIndicators` | `0 0 9 * * ?` | 2:30 PM IST (daily) | Safety-net recalculation of all indicators | Yes (3 attempts, 5s backoff × 2) |
| Signal Performance | `0 30 2 * * ?` | 8:00 AM IST (daily) | Precompute 5d/10d/20d forward returns per recommendation | No |
| FII/DII Refresh | `0 30 15 * * MON-FRI` | 9:00 PM IST (Mon–Fri) | Fetch FII/DII data from NSE | No |
| Corporate Events | `0 0 9 * * MON-FRI` | 2:30 PM IST (Mon–Fri) | Refresh corporate events from NSE | No |
| Weekly Shareholding Sync | `0 30 4 * * SUN` | 10:00 AM IST (Sunday) | Fetch quarterly shareholding data for all stocks from NSE | No |
| Daily Bulk Deals | `0 30 12 * * MON-FRI` | 6:00 PM IST (Mon–Fri) | Fetch today's bulk deals post-market | No |
| Daily Block Deals | `0 35 12 * * MON-FRI` | 6:05 PM IST (Mon–Fri) | Fetch today's block deals post-market | No |
| Weekly Deals Backfill | `0 30 5 * * SUN` | 11:00 AM IST (Sunday) | Backfill 90 days of bulk + block deals | No |
| Weekly XBRL Fetch | `0 0 6 * * SUN` | 11:30 AM IST (Sunday) | Download & parse detailed FII/DII/MF XBRL filings | No |

### Startup Tasks

| Task | Trigger | Action |
|------|---------|--------|
| `IndicatorStartupTask` | `ApplicationReadyEvent` | Calculate indicators for today if not done; widen `indicator_type` column |
| `PortfolioMigrationStartupTask` | `ApplicationReadyEvent` | One-time migration: create default portfolio, migrate Stock→PortfolioHolding, update snapshot portfolio_id |
| `PortfolioSnapshotStartupTask` | `ApplicationReadyEvent` | Ensure today's portfolio snapshot exists for each stock |

---

## External Data Integrations

### Yahoo Finance
- **Endpoints Used**: `/v7/finance/quote`, `/v1/finance/search`, `/v8/finance/chart/{symbol}`
- **Features**: `.NS` suffix fallback for Indian stocks, 3-retry with 2s backoff, 15-min Caffeine cache
- **Rate Limit**: 1 request/second
- **Backfill Target**: 730 days (2 years) per stock

### Dhan Brokerage API
- **Endpoints Used**: `/v2/holdings`, `/v2/charts/historical`
- **Sync Scope**: 60-day candle history + RSI-14 recalculation per holding
- **Security**: SSRF prevention via allowlisted base URL (`https://api.dhan.co`), exchange segments (`NSE_EQ`, `BSE_EQ`), security ID validation (alphanumeric, max 20)
- **Rate Limit**: 5 requests/second

### NSE India
- **FII/DII Data**: `https://www.nseindia.com/api/fiidiiTradeReact`
  - Cookie-acquisition pattern (hit homepage first, then API)
  - Sentiment: > 500 = Bullish, > 200 = Mildly Bullish, < −500 = Bearish, < −200 = Mildly Bearish, else Neutral
- **Corporate Events**: `https://www.nseindia.com/api/event-calendar`
  - Same cookie-acquisition pattern
  - 5-day forward window for event risk detection
- **Shareholding Data**: `https://www.nseindia.com/api/corporate-share-holdings-master`
  - Returns promoter%, public%, employeeTrusts% per stock per quarter
  - Cookie-based session managed by `NseSessionManager`
  - Protected by Resilience4j circuit breaker (10-sliding-window, 50% failure threshold, 30s open state)
  - Rate Limit: 0.5 requests/second (2s between calls)
- **XBRL Detailed Filings**: `nsearchives.nseindia.com` XML filing documents
  - BSE taxonomy (`in-bse-shp`), parses CategoryOfShareholdersAxis dimension
  - Extracts: ForeignPortfolioInvestorMember → FII, MutualFundsMember → MF, InsuranceCompaniesMember → Insurance, etc.
  - No rate limiting (static files on nsearchives), but slow (download + XML parse per stock)

### NSE Bulk & Block Deals
- **Historical API**: `https://www.nseindia.com/api/historicalOR/bulk-block-short-deals`
  - JSON endpoint for date-range queries
  - Same NSE session management as FII/DII
- **Current Day CSV**: `https://archives.nseindia.com/content/equities/bulk.csv` / `block.csv`
  - Direct CSV download for current day's data
- **Client Classification**: `ClientClassifier` uses 70+ regex patterns to categorize client names into FII, MF, DII, Promoter, Retail, or Unknown
- **Rate Limit**: 0.5 requests/second (shared NSE rate limit)

---

## Caching

Caffeine cache with **15-minute TTL** and **200-entry maximum** per cache:

| Cache Name | Key | Used In |
|-----------|-----|---------|
| `stockHistory` | `symbol_days` | `YahooFinanceService.fetchHistory` |
| `latestIndicators` | `stockId` | `TechnicalAnalysisService.getLatestIndicators` |
| `indicatorHistory` | `stockId:type` | `TechnicalAnalysisService.getIndicatorHistory` |
| `signals` | `stockId` | `SignalService` |
| `supportResistanceLevels` | `stockId` | `SupportResistanceService` |
| `institutionalScores` | `stockId` or `'all'` | `InstitutionalScoreService` |

Cache eviction: When `calculateIndicatorsForStock` runs, it evicts `latestIndicators` and `indicatorHistory` for that stock. When new shareholding data or deals are fetched, `institutionalScores` is fully evicted via `@CacheEvict(allEntries = true)`.

```bash
# Clear stockHistory cache
curl -X POST http://localhost:8080/api/stocks/cache/clear
```

---

## Error Handling

All errors handled by `GlobalExceptionHandler` (`@RestControllerAdvice`):

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `StockNotFoundException` | 404 | `STOCK_NOT_FOUND` |
| `NoPriceDataException` | 404 | `NO_PRICE_DATA` |
| `StockHistoryNotFoundException` | 404 | `HISTORY_NOT_FOUND` |
| `ResourceNotFoundException` | 404 | `NOT_FOUND` |
| `PriceAlreadyExistsException` | 409 | `PRICE_ALREADY_EXISTS` |
| `InvalidPriceException` | 400 | `INVALID_PRICE` |
| `MethodArgumentNotValidException` | 400 | Field-level errors |
| `ConstraintViolationException` | 400 | `CONSTRAINT_VIOLATION` |
| `IllegalArgumentException` | 400 | `ILLEGAL_ARGUMENT` |
| Generic `Exception` | 500 | `INTERNAL_ERROR` |

All error messages are sanitized (newlines/carriage returns stripped) before logging to prevent log injection (CWE-117/CWE-93).

### Validation Rules

| Field | Rules |
|-------|-------|
| Stock Symbol | `@NotBlank`, unique, max 20 chars |
| Stock Name | `@NotBlank` |
| Closing Price | `@NotNull`, > 0 |
| Price Date | `@NotNull`, not in future |
| History Symbol | `^[A-Z0-9.\-^=]+$`, days 1–365 |
| Position Size | 0.01–0.05 (1%–5%) |

---

## Code Quality Assessment

### Critical Issues

| Issue | Severity | Location |
|-------|----------|----------|
| **No authentication** on any endpoint | CRITICAL | All controllers |
| **XSS risk** in stock search onclick handler | HIGH | `stock-management.js:589` |
| **N+1 queries in getLatestIndicators** (17 queries per stock) | HIGH | `TechnicalAnalysisService:137` |
| **N+1 queries in getAllStockDTOs** (2N queries for N stocks) | HIGH | `StockService:177` |
| **N+1 queries in screeners** (N stocks × N queries per screener) | HIGH | `InstitutionalScreenerService` |
| **Loading all stocks to find one DTO** | HIGH | `StockController:41-44` |

### Medium Issues

| Issue | Location |
|-------|----------|
| Duplicate RSI implementation (TechnicalAnalysisUtils vs RsiCalculator) | 2 files, ~50 lines |
| Duplicate P&L percent formula (5 occurrences) | StockService, PortfolioSnapshotService, PortfolioService |
| Duplicate NSE fetch pattern (FiiDii + CorporateEvent) | 2 services |
| Actuator endpoints exposed without protection | `application.properties:33` |
| Error messages leak internal details (IllegalArgumentException) | `GlobalExceptionHandler:77` |
| Unbounded request payloads (CSV import, history save) | `StockHistoryController:86` |
| TOCTOU race in ApiRateLimiter | `ApiRateLimiter:17` |
| Hardcoded DB credentials (root/root) | `application.properties:7-8` |
| Deprecated RsiValue entity still in codebase | entity/, repository/, service/ |
| Manual migration process (no Flyway/Liquibase) | `migrations/V1__widen_indicator_type_column.sql` |
| N+1 stock repository loop in screeners (screenerAll calls computeScore per stock) | `InstitutionalScreenerService:363-416` |
| Stock entity still has legacy portfolio fields (quantity, avgPrice, etc.) | `Stock.java:54-72` |
| Missing index on `bulk_deals(buy_sell, is_institutional, deal_date)` | BulkDealRepository queries |

### Test Coverage Gaps

| Untested Component | Risk |
|-------------------|------|
| BacktestService | HIGH — complex trading logic |
| TechnicalAnalysisUtils (divergence detection) | HIGH — affects signal generation |
| PositionSizingService | MEDIUM |
| TrailingStopService | MEDIUM |
| PriceAggregationService | MEDIUM |
| SupportResistanceService | MEDIUM |
| FiiDiiService, CorporateEventService | MEDIUM |
| DhanApiService, DhanSyncService | MEDIUM |
| 10 of 17 controllers | MEDIUM |
| StockSplitDetector | MEDIUM — data integrity risk |
| PortfolioService (integration) | MEDIUM |
| NseXbrlShareholdingService (production parsing) | MEDIUM |
| All JavaScript code | MEDIUM |
| Calculator edge cases (empty/null data) | LOW |

### Covered by Tests

| Component | Test File | Coverage |
|-----------|-----------|----------|
| `ClientClassifier` | `ClientClassifierTest.java` | All categories + edge cases + institutional flag |
| `InstitutionalScoreService` | `InstitutionalScoreServiceTest.java` | All 8 scoring components + totals + grading |
| `BulkDealService` | `BulkDealServiceTest.java` | Fetch, classification, dedup |
| `BlockDealService` | `BlockDealServiceTest.java` | Fetch, classification, dedup |
| `NseXbrlShareholdingService` | `NseXbrlShareholdingServiceTest.java` + IntegrationTest | Parsing + NSE connectivity |

### Missing Indexes

| Table | Missing Index | Impact |
|-------|--------------|--------|
| `signal_historical_performance` | (recommendation, days_forward) | Slow signal queries |
| `corporate_events` | (symbol, event_date) — partially indexed | Slow event risk checks |
| `bulk_deals` | (buy_sell, is_institutional, deal_date) | Slow screener/institutional score queries |
| `block_deals` | (buy_sell, is_institutional, deal_date) | Same as bulk_deals |
| `institutional_holdings` | (stock_id, quarter_end_date DESC) | Slow latest-holding queries |
| `portfolio_snapshots` | (portfolio_id, snapshot_date) | Slow multi-portfolio aggregation queries |

---

## Extension Points

### Adding a New Feature (e.g., Price Action Alerts)

**Modules to create/modify:**

| Step | Module | File Pattern |
|------|--------|-------------|
| 1 | Entity | `src/main/java/org/example/entity/NewEntity.java` |
| 2 | Repository | `src/main/java/org/example/repository/NewEntityRepository.java` |
| 3 | Service | `src/main/java/org/example/service/NewFeatureService.java` |
| 4 | Controller | `src/main/java/org/example/controller/NewFeatureController.java` |
| 5 | DTO | `src/main/java/org/example/dto/NewFeatureDTO.java` |
| 6 | Frontend Page | `src/main/resources/static/new-page.html` |
| 7 | API Client | `src/main/resources/static/js/new-page.js` + `api.js` |
| 8 | Navigation | Add `<a>` to nav bar in **all 9 HTML files** |
| 9 | Metrics | Add Micrometer counters/timers in `metrics/` for monitoring |
| 10 | Cache | Add cache to `CacheConfig.java` if needed |
| 11 | Config | Add properties to `application.properties` if needed |

**Patterns to follow:**

- **Controller**: `@RestController`, return `ApiResponse<T>`, validate with `@Valid`
- **Service**: Inject repos + services, `@Transactional` where needed
- **Repository**: Extend `JpaRepository<Entity, Long>`, custom `@Query` for complex queries
- **DTO**: Simple POJOs with getters/setters
- **Frontend**: Static HTML + Tailwind CSS + vanilla JS, API functions in `api.js`
- **Error handling**: Throw custom exceptions, caught by `GlobalExceptionHandler`
- **Caching**: Add to `CacheConfig` with appropriate TTL
- **Scheduling**: Add `@Scheduled` method to scheduler class

---

## Prerequisites

- **Java**: JDK 17 or higher
- **Maven**: 3.6 or higher
- **MySQL**: 8.0 or higher (or compatible fork)
- **Modern web browser**: Chrome, Firefox, Edge, or Safari

### Optional (for live integrations)
- **Dhan Brokerage API** access token and client ID
- **Internet access** for Yahoo Finance and NSE data fetching

---

## Database Setup

### MySQL (Default)

1. Create a MySQL database:
   ```sql
   CREATE DATABASE IF NOT EXISTS stockmarket;
   ```

2. Ensure a MySQL user exists with privileges:
   ```sql
   CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY 'root';
   GRANT ALL PRIVILEGES ON stockmarket.* TO 'root'@'localhost';
   FLUSH PRIVILEGES;
   ```

3. Update database credentials in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/stockmarket?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
   spring.datasource.username=root
   spring.datasource.password=root
   ```

4. The application uses `spring.jpa.hibernate.ddl-auto=update`, so Hibernate will auto-create tables on first run.

### PostgreSQL (Alternative)

A PostgreSQL schema script is available at `src/main/resources/schema.sql.postgresql`. To use PostgreSQL instead:

1. Create the database:
   ```sql
   CREATE DATABASE stockmarket;
   ```

2. Run the schema script:
   ```bash
   psql -U postgres -d stockmarket -f src/main/resources/schema.sql.postgresql
   ```

3. Update `application.properties` to point to PostgreSQL:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/stockmarket
   spring.datasource.driver-class-name=org.postgresql.Driver
   spring.datasource.username=postgres
   spring.datasource.password=your_password
   ```

4. Add the PostgreSQL driver dependency:
   ```xml
   <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```

---

## Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd stockmarket
   ```

2. Ensure MySQL is running and the database is configured (see [Database Setup](#database-setup)).

3. Build the project:
   ```bash
   mvn clean install
   ```

---

## Running the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

### Access Points
- **Application**: [http://localhost:8080](http://localhost:8080)
- **API Documentation (Swagger UI)**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) or [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Actuator Metrics**: [http://localhost:8080/actuator/metrics](http://localhost:8080/actuator/metrics)

---

## Configuration

All configuration is managed through `src/main/resources/application.properties`:

```properties
# Server
server.port=8080

# MySQL Database
spring.datasource.url=jdbc:mysql://localhost:3306/stockmarket?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver
spring.datasource.username=root
spring.datasource.password=root

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false

# HikariCP Connection Pool
spring.datasource.hikari.maximum-pool-size=15
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=60000
spring.datasource.hikari.pool-name=StockMarketPool

# Dhan API
dhan.api.base-url=https://api.dhan.co
dhan.api.access-token=YOUR_DHAN_ACCESS_TOKEN
dhan.api.client-id=YOUR_DHAN_CLIENT_ID

# Timezone
spring.jackson.time-zone=IST

# Logging
logging.level.org.springframework.web=INFO
logging.level.org.springframework.web.context.request.async=WARN
logging.level.org.apache.tomcat=WARN
logging.level.org.apache.coyote=WARN
logging.level.org.apache.catalina=WARN
logging.level.org.hibernate=ERROR
logging.level.com.stocks=DEBUG

# Actuator
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.prometheus.enabled=true
management.endpoint.health.show-details=always

# DevTools
spring.devtools.livereload.enabled=false
spring.devtools.add-properties=false

# Static Resource Caching (disable in dev)
spring.web.resources.cache.cachecontrol.max-age=0
spring.web.resources.cache.cachecontrol.no-cache=true
spring.web.resources.cache.cachecontrol.must-revalidate=true

# Resilience4j Circuit Breaker
resilience4j.circuitbreaker.configs.default.sliding-window-size=10
resilience4j.circuitbreaker.configs.default.failure-rate-threshold=50
resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.nseSession.base-config=default
resilience4j.circuitbreaker.instances.nseXbrl.base-config=default
resilience4j.timelimiter.configs.default.timeout-duration=30s
```

### Environment Variables (Production)
```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://your-host:3306/stockmarket
export SPRING_DATASOURCE_USERNAME=your_username
export SPRING_DATASOURCE_PASSWORD=your_password
export DHAN_API_ACCESS_TOKEN=your_dhan_token
export DHAN_API_CLIENT_ID=your_dhan_client_id
export SERVER_PORT=8080
```

---

## Testing

### Running Backend Tests
```bash
mvn test
```

### Running JaCoCo Coverage Report
```bash
mvn clean test jacoco:report
# Report: target/site/jacoco/index.html
```

### Running Playwright E2E Tests
```bash
# Start the application first
mvn spring-boot:run

# In another terminal
npx playwright test
```

### Test Files

| File | Type | Tests |
|------|------|-------|
| `SignalServiceTest.java` | Unit | 19 test methods — confidence, position sizing, signal age |
| `StockServiceTest.java` | Unit | CRUD, portfolio recalculation, DTO mapping |
| `TechnicalAnalysisServiceTest.java` | Unit | Indicator calculation |
| `WatchlistServiceTest.java` | Unit | 11 test methods |
| `PortfolioChartControllerIntegrationTest.java` | Integration | 17 Spring integration tests |
| `PortfolioChartValidationTest.java` | Integration | 13 validation tests |
| `PortfolioChartMemoryLeakTest.java` | Unit | 12 memory safety tests |
| `PortfolioSnapshotServiceUnitTest.java` | Unit | Portfolio snapshot logic |
| `PortfolioSnapshotServiceFixTest.java` | Unit | Snapshot fix validation |
| `SignalControllerIntegrationTest.java` | Integration | Signal endpoint tests |
| `StockControllerSearchTest.java` | Unit | Search endpoint tests |
| 10 calculator tests | Unit | ATR, ADX, CCI, OBV, ROC, StochK, StochD, StochRSI, UltimateOsc, WilliamsR |
| `ClientClassifierTest.java` | Unit | All client categories + institutional flag + edge cases |
| `InstitutionalScoreServiceTest.java` | Unit | All 8 scoring components + totals + grade mapping |
| `BulkDealServiceTest.java` | Unit | Bulk deal fetching + client classification + dedup |
| `BlockDealServiceTest.java` | Unit | Block deal fetching + client classification + dedup |
| `NseXbrlShareholdingServiceTest.java` | Unit | XBRL XML parsing + fields extraction |
| `NseXbrlShareholdingServiceIntegrationTest.java` | Integration | NSE connectivity + live filing download |
| `tests/frontend.spec.js` | E2E | Page loading, navigation, theme, search, sorting |
| `tests/portfolio-chart-fixes.spec.js` | E2E | 32 portfolio chart tests |

---

## Deployment

### Production JAR Build
```bash
mvn clean package -DskipTests
java -jar target/stockmarket-1.0-SNAPSHOT.jar
```

### Docker Deployment

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY target/stockmarket-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build and Run:**
```bash
docker build -t stock-market-analysis .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://db-host:3306/stockmarket \
  -e SPRING_DATASOURCE_USERNAME=prod_user \
  -e SPRING_DATASOURCE_PASSWORD=prod_password \
  -e DHAN_API_ACCESS_TOKEN=your_token \
  -e DHAN_API_CLIENT_ID=your_client_id \
  stock-market-analysis
```

### Docker Compose (with MySQL)
```yaml
version: "3.8"
services:
  db:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: stockmarket
      MYSQL_ROOT_PASSWORD: root
    volumes:
      - mysql-data:/var/lib/mysql
    ports:
      - "3306:3306"

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/stockmarket
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: root
    depends_on:
      - db

volumes:
  mysql-data:
```

```bash
docker compose up --build
```

---

## Troubleshooting

### Database Connection Issues
- Verify MySQL is running: `mysqladmin -u root -p ping`
- Check database credentials in `application.properties` or environment variables
- Ensure the database exists and the user has proper privileges
- Review `spring.jpa.hibernate.ddl-auto=update` behavior (tables created automatically)
- Check MySQL max connection limits if you see `Too many connections`

### RSI / Indicator Calculation Errors
- At least 14 daily prices are required for RSI-14; 15+ for initial Wilder's smoothing
- Ensure price dates are in chronological order without gaps
- Verify closing prices are valid (> 0)

### Frontend Issues
- Clear browser cache
- Check browser console for JavaScript errors
- Verify backend is running and accessible at `http://localhost:8080`
- For Swagger UI, ensure springdoc-openapi dependency is resolved

### Scheduling Issues
- Verify server timezone is IST (Indian Standard Time) or adjust cron expressions
- Check `@Retryable` logs in application logs for repeated failures
- For Dhan sync, ensure the access token is valid and not expired

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m "Add my feature"`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

Please ensure all tests pass (`mvn test`) and code follows the existing Lombok + Jakarta Validation conventions before submitting.

---

## License

This project is licensed under the [MIT License](LICENSE).
