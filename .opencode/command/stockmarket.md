---
description: Load full project context for the stockmarket Spring Boot app (entities, APIs, services, frontend, signal engine, institutional analysis)
---

# Stock Tracker Application Context
# Full-stack Java Spring Boot 3.2.5 app for NSE/BSE stock analysis — 21 technical indicators, 16-factor signal scoring, institutional activity tracking, multi-portfolio management, backtesting

## Tech Stack
- Java 17, Spring Boot 3.2.5, Maven
- Spring Data JPA, Validation, Web, WebFlux, Cache, Scheduling, Retry, Actuator
- MySQL (primary), Caffeine Cache, Resilience4j, Lombok
- Frontend: Static HTML, Tailwind CSS, Chart.js 4.4 + Financial plugin, Font Awesome
- Testing: JUnit 5 + Mockito (44 backend), Playwright (5 E2E specs)

## Agents
- `stock-analyzer` — Primary agent for all stock market analysis
- Skills available: `trace-signal`, `add-indicator`, `run-tests`, `add-endpoint`

## Commands
- `/build` — mvn clean compile
- `/test` — mvn test (all backend unit tests)
- `/e2e` — npx playwright test (frontend E2E)
- `/start` — mvn spring-boot:run

## Entities (15 JPA)
- Stock, DailyPrice (OHLCV), TechnicalIndicator, SupportResistanceLevel, PortfolioSnapshot
- Portfolio, PortfolioHolding, Watchlist, WatchlistItem
- InstitutionalHolding, BulkDeal, BlockDeal
- CorporateEvent, FiiDiiData, FundamentalData, SignalRecord, SignalHistoricalPerformance

## Key Services (28+)
- SignalService — 16-factor weighted scoring engine (the core)
- TechnicalAnalysisService — Orchestrates 22 indicator calculators
- BacktestService — Strategy simulation with trailing stop
- YahooFinanceService / DhanApiService — External data ingestion
- InstitutionalHoldingService / InstitutionalScoreService — NSE holdings analysis
- PortfolioService — Multi-portfolio CRUD + P&L
- 8 institutional services in service/institutional/

## REST API Endpoints (~100+)
- /api/stocks — CRUD, search, CSV import
- /api/prices — OHLCV price entry & history
- /api/rsi — RSI-14 calculation & history
- /api/indicators — All 21 technical indicators
- /api/signals — BUY/SELL/HOLD with composite scoring
- /api/portfolios — Multi-portfolio management
- /api/institutional — Holdings, bulk/block deals, scores, 7 screeners
- /api/support-resistance — Pivot levels (S1-S3/R1-R3)
- /api/backtest — Strategy simulation
- /api/dhan, /api/fiidii, /api/events, /api/fundamentals — External data

## Signal Engine (SignalService)
- 16 factors: RSI divergence (±4), MACD (±3), weekly confluence (±3), RSI, Bollinger, Stoch, StochRSI (±2 each), Ultimate Osc, ROC, Williams %R, CCI, OBV, SMA20, 52W, monthly confluence (±1 each), FII/DII (±2)
- ADX filter (0.6x counter-trend penalty), Volume confirmation (0.6x)
- Score → Recommendation: >=6 STRONG BUY, >=3 BUY, <=-6 STRONG SELL, <=-3 SELL, else HOLD
- Confidence 0-100: 50 + score*5, adjusted for volume, events, staleness, accuracy
- Target/Stop: 3x ATR / 2x ATR (1.5:1 R:R)

## 22 Technical Indicators (in service/calculator/)
RSI, SMA_20/50/200, EMA_20, MACD Line/Signal, Bollinger Upper/Lower, Stoch %K/%D, Williams %R, ATR, CCI, StochRSI, ADX, +DI, -DI, Ultimate Osc, ROC-12, OBV, SupportResistance

## Institutional Analysis
- 8 services: Holdings, BulkDeals, BlockDeals, Score, Screener, ClientClassifier, NseSession, NseXbrl
- 8-factor institutional scoring (0-100)
- 7 screeners: FII/DII/MF accumulation, strong buy, price action, bulk/block deals
- Client classification via 135+ regex patterns

## Frontend Pages (src/main/resources/static/)
- index.html — Dashboard with KPIs, signals table, P&L charts
- stock-detail.html — 6 Chart.js charts, candlestick, backtest, AI insights
- stocks.html — Stock CRUD, CSV import
- institutional-dashboard.html — Scores, screeners
- watchlist.html — Watchlist CRUD
- rsi-analysis.html — RSI table
- price-entry.html — OHLCV entry
- fundamentals-screener.html — PE/PB/ROE screener

## External APIs
- Yahoo Finance (1 req/s) — OHLCV, fundamentals, search
- Dhan API v2 (5 req/s) — Holdings, candles
- NSE India (0.5 req/s) — FII/DII, events, shareholding, bulk/block deals

## Schedulers
- Daily 4:15 PM IST — Dhan sync + indicators + S/R
- Daily 2:30 AM — Forward returns computation
- Daily 3:00 AM — Signal accuracy marking
- Sundays 4:30-8:00 AM — Weekly maintenance (holdings, deals, XBRL, fundamentals, backfill)
- Daily 9:00 AM — Safety-net indicator recalculation

## Startup Tasks (3)
- IndicatorStartupTask — Column widening, missing indicator calc
- PortfolioMigrationStartupTask — Legacy→multi-portfolio migration
- PortfolioSnapshotStartupTask — Today's snapshot creation

## Testing
- mvn test — 44 JUnit 5 + Mockito tests (service, calculator, controller integration)
- npx playwright test — 5 E2E spec files (frontend, signals, portfolio, watchlist, stock mgmt)
- Coverage: JaCoCo at target/site/jacoco/index.html
