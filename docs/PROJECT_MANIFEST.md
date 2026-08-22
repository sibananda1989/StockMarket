# PROJECT MANIFEST — Stock Market Analysis Platform

**Purpose**: Single file for AI agents to locate every file instantly (~2.5K tokens). Stop reading other docs after this.

---

## CONTROLLERS (27) — `src/main/java/org/example/controller/` — ~153 @Mapping annotations

| File | Base Path | Key Endpoints |
|------|-----------|---------------|
| StockController.java | `/api/stocks` | CRUD, search, sectors, CSV import, portfolio recalc, snapshots |
| SignalController.java | `/api/signals` | All/buy/sell/{stockId}/history |
| MultiStrategySignalController.java | `/api/signals/multi-strategy/{id}` | Signal, breakdown, compare, history |
| PortfolioController.java | `/api/portfolio` | History, screener, backfill |
| PortfolioManagementController.java | `/api/portfolios` | Multi-portfolio CRUD + holdings + transactions (16 endpoints) |
| BacktestController.java | `/api/backtest/stock/{id}` | Strategy backtest |
| WatchlistController.java | `/api/watchlists` | CRUD + items + batch + sync (11 endpoints) |
| WatchlistOpportunityController.java | `/api/watchlists` | Opportunities |
| TechnicalIndicatorController.java | `/api/indicators/{stockId}` | Latest, history, calc, backfill, reset (returns RAW DTOs) |
| PriceController.java | `/api/prices` | Save, query by date range |
| StockHistoryController.java | `/api/stocks/history` | Yahoo sync + summary (8 endpoints) |
| StockSyncController.java | `/api/stocks` | Backfill, portfolio sync |
| SupportResistanceController.java | `/api/support-resistance` | Levels, calc, history |
| FiiDiiController.java | `/api/fiidii` | FII/DII data + refresh |
| CorporateEventController.java | `/api/events` | Events + refresh |
| InstitutionalHoldingController.java | `/api/institutional` | Holdings, XBRL, deals, scores, 7 screeners (20 endpoints) |
| StartupTaskController.java | `/api/startup-tasks` | List + run tasks |
| RsiController.java | `/api/rsi` | DEPRECATED — use TechnicalIndicatorController |
| HomeController.java | — | Thymeleaf views |
| DataAvailabilityController.java | `/api/data-availability` | Data health / coverage |
| IndicatorCoverageController.java | `/api/indicator-coverage` | Coverage gaps |
| FundamentalDataController.java | `/api/fundamentals` | Fundamentals fetch/screen/sectors |
| SMCController.java | `/api/smc` | SMC patterns |
| ScoreParameterController.java | `/api/score-parameters` | Toggle scoring factors |
| StrategyConfigController.java | `/api/strategy-config` | Strategy priority/enable |
| StrategyDailyWeightController.java | `/api/strategy-daily-weights` | Daily weight adj |
| StrategyResultsController.java | `/api/strategy-results` | Strategy run results + refresh |

## SERVICES (37) — `src/main/java/org/example/service/`

| File | Size | Purpose |
|------|------|---------|
| SignalService.java | 34.6K | **CORE**: 15-factor scoring, BUY/SELL/HOLD, confidence, targets |
| StockService.java | 9.1K | Stock CRUD + portfolio recalc |
| PortfolioService.java | 5.2K | Multi-portfolio CRUD + P&L |
| PortfolioTransactionService.java | - | Buy/Sell ledger avg-cost tracking |
| PortfolioSnapshotService.java | 8.9K | Daily position snapshots |
| TechnicalAnalysisService.java | 6.7K | Indicator calc orchestration |
| BacktestService.java | 8K | Strategy simulation + stop-loss |
| YahooFinanceService.java | 13.7K | Yahoo API client |
| YahooFinanceSyncService.java | - | Persist Yahoo data |
| StockHistoryService.java | - | Yahoo history fetch |
| StockSyncService.java | - | Backfill orchestration |
| DailyPriceService.java | 4.7K | Price CRUD + portfolio recalc trigger |
| BreakoutDetector.java | - | Volume/range/price breakout |
| SupportResistanceService.java | - | S/R level calc |
| WatchlistService.java | - | Watchlist CRUD |
| FiiDiiService.java | - | NSE FII/DII data |
| CorporateEventService.java | - | NSE corporate events |
| ShadowSignalService.java | - | A/B signal logic comparison |
| ScoreParameterService.java | - | Toggle scoring factors on/off |
| StrategyConfigService.java | - | Strategy priority/enable config |
| StrategyConditionService.java | - | DB condition overrides |
| StrategyInfluenceService.java | - | Strategy override logic |
| StrategyDailyWeightService.java | - | Daily strategy weight adj |
| SignalThresholds.java | - | All signal constants (BUY_THRESHOLD=5, etc.) |
| TechnicalIndicatorPersistenceService.java | - | Indicator CRUD |
| DataAvailabilityService.java | - | Data health check |
| TrailingStopService.java | - | ATR-based trailing stop |
| PositionSizingService.java | - | Risk-based position sizing |
| TechnicalAnalysisUtils.java | - | RSI calc + divergence detection |
| StockSplitDetector.java | - | Split detection + price adj |
| PriceAggregationService.java | - | Weekly/monthly aggregation |
| ApiRateLimiter.java | - | In-memory rate limiter |

### Calculators (34) — `src/main/java/org/example/service/calculator/`
- **Interface**: `IndicatorCalculator.java` — `calculate(List<DailyPrice>): BigDecimal`
- **List**: Rsi, Sma (generics), Sma20/50/200/44, Ema, Ema20, MacdLine, MacdSignal, BollingerUpper/Lower, StochK/D, WilliamsR, ATR(14), CCI(20), StochRsi, Adx(+DI/-DI), PlusDi, MinusDi, UltimateOsc(7/14/28), Roc(12), Obv, VolumeRatio, Vwap, DollarVolume, Amihud, Ichimoku(5 components), ReversalDetector, CandlestickPattern(8 patterns), SupportResistance, IndicatorComputationService — **34 files total (32 calculators + interface + orchestration service)**

### Institutional (8) — `src/main/java/org/example/service/institutional/`
- InstitutionalHoldingService(338L), NseXbrlShareholdingService(625L), BulkDealService(292L), BlockDealService(271L), ClientClassifier(135L), InstitutionalScoreService(499L), InstitutionalScreenerService(439L), NseSessionManager(134L)

## STRATEGY ENGINE — `src/main/java/org/example/strategy/`

| File | Role |
|------|------|
| base/TradingStrategy.java | Abstract base: evaluate(indicator, prices) → StrategyResult |
| model/StrategyResult.java | Record: signal, confidence, reason, strategyName, priority, contribution |
| model/AggregatedSignalResult.java | Record: finalSignal, score, confidence, breakdown, supporting, opposing |
| model/StrategySignal.java | Enum: STRONG_BUY/BUY/HOLD/SELL/STRONG_SELL |
| engine/MultiStrategySignalEngine.java | Entry: evaluate(stockId), evaluateWithHistory(days) |
| aggregator/StrategySignalAggregator.java | Weighted agg + confidence + categorization + fail-safe |
| config/StrategyConfig.java | Spring beans for 10 strategies |
| impl/ | 10 strategies: Rsi, Macd, MovingAverageCrossover, BollingerBand, Volume, Breakout, CandlestickPattern, CandlestickContext, Sma44PullbackBounce, Liquidity |

## ENTITIES (29) — `src/main/java/org/example/entity/`

| Entity | Key Fields |
|--------|------------|
| Stock.java | symbol, name, sector, industry, yahooSymbol, portfolio fields (legacy) |
| DailyPrice.java | stock_id FK, OHLCV, volume, price_date (UK: stock_id+date) |
| TechnicalIndicator.java | stock_id FK, IndicatorType enum(31), value, calculation_date (EAV pattern) |
| IndicatorType.java | Enum with 31 values + `isDirectional()` |
| SignalRecord.java | stockId, recordedAt, recommendation, compositeScore, forward returns, accuracy |
| SupportResistanceLevel.java | stock FK, LevelType, value, strength, touchCount, order |
| LevelType.java | Enum: 11 values (SWING_LOW/HIGH, PIVOT_*x3, MAJOR_*) |
| Portfolio.java | name, description, isDefault |
| PortfolioHolding.java | portfolio FK, stock FK, quantity, avgPrice |
| PortfolioTransaction.java | ledger: portfolio FK, stock FK, type(BUY/SELL), qty, price |
| TransactionType.java | Enum: BUY, SELL |
| PortfolioSnapshot.java | stock FK, portfolio FK, snapshot_date, computed P&L fields |
| InstitutionalHolding.java | stock FK, quarterEndDate, fii%, dii%, mf%, promoter%, public% |
| BulkDeal.java | stock FK, deal_date, client, B/S, qty, price, clientCategory |
| BlockDeal.java | Same as BulkDeal |
| FiiDiiData.java | UK: date, fiiBuy/Sell, diiBuy/Sell, net |
| CorporateEvent.java | symbol, event_date, purpose |
| Watchlist/WatchlistItem.java | Named lists + junction |
| StrategyConfig.java | strategyName, priority, enabled |
| StrategyConditionGroup.java | strategy FK, condition rules |
| StrategyDailyWeight.java | Daily weight adjustments |
| ScoreParameterConfig.java | Scoring factor toggles |
| StartupTaskLog.java | taskId, runDate, status, completedAt |
| SignalHistoricalPerformance.java | Precomputed forward returns |
| ShadowSignalRecord.java | A/B comparison record |
| FVGPattern.java | Fair Value Gap / Order Block |

## FRONTEND (14 HTML + 15 JS) — `src/main/resources/static/`

| Page | JS File | Size | Purpose |
|------|---------|------|---------|
| index.html | portfolio.js | 34K | Dashboard: P&L, signals table, 3 charts, transactions |
| stock-detail.html | stock-detail.js | **86K** | 6 charts, AI insights, backtest, institutional tab |
| stocks.html | stock-management.js | 27.7K | CRUD, CSV import, alerts |
| watchlist.html | watchlist.js | 33.6K | CRUD, sector chart |
| institutional-dashboard.html | (inline) | - | Scores, holdings, deals, 7 screeners |
| strategy.html | strategy-manager.js | - | Strategy config + conditions |
| rsi-analysis.html | rsi-analysis.js | - | RSI overview table |
| price-entry.html | price-entry.js | - | Manual OHLCV entry |
| stock-history.html | stock-history.js | - | Yahoo history viewer |
| portfolio-transactions.html | portfolio-transactions.js | 26K | Transactions ledger, filters, CSV export |
| fundamentals-screener.html | fundamentals-screener.js | - | Fundamental screening |
| history-summary.html | history-summary.js | - | Batch Yahoo history summary |
| strategy-results.html | (inline/api) | - | Strategy run results |
| watchlist-opportunities.html | watchlist-opportunities.js | - | Watchlist opportunities |
| **Shared**: js/api.js (~150+ funcs), js/navigation.js, css/theme.css, css/styles.css + vendor: chartjs-adapter, chartjs-plugin-annotation |

### Notable API Wrappers in js/api.js
| Wrapper | Endpoint | Usage |
|---------|----------|-------|
| `getAllStocks()` | GET /api/stocks | List all stocks |
| `getSectors()` | GET /api/stocks/sectors | List distinct sectors (for filter dropdowns) |
| `getPortfolios()` | GET /api/portfolios | List portfolios |
| `getStartupTasks()` | GET /api/startup-tasks | List startup tasks |

## FEATURE → FILE MAPPING

| Task | Files to Touch |
|------|----------------|
| **Add endpoint** | Controller, DTO, Service, Repository (if needed), api.js |
| **Add indicator** | IndicatorType.java (enum), Calculator class, IndicatorComputationService.java, SignalService.java (scoring), tests |
| **Add strategy** | Strategy impl class, StrategyConfig.java (bean), application.properties (priority), strategy-manager.js (card), tests |
| **Add frontend page** | new .html, new .js (IIFE), api.js (wrappers), navigation.js (register) |
| **Modify signal logic** | SignalService.java (computeWeightedScore), SignalThresholds.java (constants), SignalDTO.java (fields), ScoreParameterService |
| **Modify dashboard chart** | index.html, portfolio.js (owns all 3 charts + signals table) |
| **Modify stock detail** | stock-detail.html, stock-detail.js (owns 6 charts + backtest + insights) |
| **Portfolio CRUD** | PortfolioManagementController, PortfolioService, Portfolio entity |
| **Transactions** | PortfolioManagementController, PortfolioTransactionService, PortfolioTransaction entity |
| **Institutional** | InstitutionalHoldingController, 8 institutional services, institutional-dashboard.html |
| **Watchlist** | WatchlistController, WatchlistService, watchlist.html, watchlist.js |
| **Backtest** | BacktestController, BacktestService |
| **FII/DII** | FiiDiiController, FiiDiiService, FiiDiiData entity |
| **Corp events** | CorporateEventController, CorporateEventService, CorporateEvent entity |
| **SMC/FVG** | SMCController, SMCDetectionService, smc/ (SwingDetector, ZoneDetector etc.) |
| **Strategy config** | StrategyConfigController, StrategyConfigService, strategy-manager.js |
| **Score params** | ScoreParameterController, ScoreParameterService, ScoreParameterConfig entity |

## KEY CONSTANTS

| Constant | Location | Value |
|----------|----------|-------|
| Signal thresholds | SignalThresholds.java | SBUY≥7, BUY≥3, HOLD=-3~2, SELL≤-4, SSELL≤-7 |
| ADX multiplier | SignalService.adxMultiplier() | <15→0.3, 15-25→0.5-0.7, 25-35→0.7-1.0, ≥35→1.0 |
| Bearish discount | SignalThresholds.BEARISH_TREND_DISCOUNT | 0.85 (with reversal exceptions) |
| Strategy priorities | application.properties | strategy.*.priority (1-10) — 10 strategies configured |
| Cache names | CacheConfig.java | latestIndicators, indicatorHistory, stockHistory, signals, supportResistanceLevels, institutionalScores, smcPatterns (app props: signals, signalDto, latestIndicators, indicatorHistory, supportResistanceLevels) |
| API base | js/api.js | `API_BASE_URL = '/api'` |
| Test count | — | 81 test files (unit + integration) |
