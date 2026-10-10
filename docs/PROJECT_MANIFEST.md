# PROJECT MANIFEST — Stock Market Analysis Platform

**Purpose**: Single file for AI agents to locate every file instantly (~2.5K tokens). Stop reading other docs after this.

---

## CONTROLLERS (28) — `src/main/java/org/example/controller/` — ~185 @Mapping annotations

| File | Base Path | Key Endpoints |
|------|-----------|---------------|
| StockController.java | `/api/stocks` | CRUD, search, sectors, CSV import, portfolio recalc, snapshots |
| SignalController.java | `/api/signals` | All/buy/sell/{stockId}/history |
| MultiStrategySignalController.java | `/api/signals/multi-strategy` | Signal, breakdown, compare, history |
| PortfolioController.java | `/api/portfolio` | History, screener, backfill, daily-values |
| PortfolioManagementController.java | `/api/portfolios` | Multi-portfolio CRUD + holdings + transactions + lots (21 endpoints) |
| BacktestController.java | `/api/backtest` | Strategy backtest `/stock/{id}` |
| WatchlistController.java | `/api/watchlists` | CRUD + items + batch + sync (11 endpoints) |
| WatchlistOpportunityController.java | `/api/watchlists` | Opportunities |
| TechnicalIndicatorController.java | `/api/indicators` | Latest, history, calc, backfill, reset — `/{stockId}` + coverage |
| PriceController.java | `/api/prices` | Save, query by date range, latest |
| StockHistoryController.java | `/api/stocks/history` | Yahoo sync + summary (8 endpoints) |
| StockSyncController.java | `/api/stocks` | Backfill, portfolio sync |
| SupportResistanceController.java | `/api/support-resistance` | Levels, calc, history |
| FiiDiiController.java | `/api/fiidii` | FII/DII data + refresh |
| CorporateEventController.java | `/api/events` | Events + refresh |
| InstitutionalHoldingController.java | `/api/institutional` | Holdings, XBRL, deals, scores, 7 screeners (26 endpoints) |
| StartupTaskController.java | `/api/startup-tasks` | List + run tasks |
| RsiController.java | `/api/rsi` | DEPRECATED — use TechnicalIndicatorController |
| HomeController.java | — | Thymeleaf views `GET /` |
| DataAvailabilityController.java | `/api/data-availability` | Data health / coverage |
| IndicatorCoverageController.java | `/api/indicators/coverage` | Coverage gaps `/{stockId}` |
| FundamentalDataController.java | `/api/fundamentals` | Fundamentals fetch/screen/sectors |
| SMCController.java | `/api/smc` | SMC patterns `/{stockId}` |
| ScoreParameterController.java | `/api/score-parameters` | Toggle scoring factors |
| StrategyConfigController.java | `/api/strategy-config` | Strategy priority/enable |
| StrategyDailyWeightController.java | `/api/strategy-daily-weight` | Daily weight adj |
| StrategyResultsController.java | `/api/strategy-results` | Strategy run results + refresh (rows carry `eventDate`) |
| EmaCrossScreenerController.java | `/api/screener/ema-cross` | EMA-20↑50 cross within N days (cached) |

## SERVICES (42) — `src/main/java/org/example/service/`

| File | Purpose |
|------|---------|
| SignalService.java | **CORE**: 19-factor scoring (computeWeightedScore), BUY/SELL/HOLD, ADX, bearish discount, confidence, targets |
| StockService.java | Stock CRUD + portfolio recalc |
| PortfolioService.java | Multi-portfolio CRUD + P&L |
| PortfolioTransactionService.java | Buy/Sell ledger avg-cost tracking |
| PortfolioSnapshotService.java | Daily position snapshots |
| PortfolioSnapshotWriter.java | Snapshot write batch |
| PortfolioPositionReplayer.java | Position replay from transactions |
| PortfolioHistoryService.java | Portfolio history (cached `portfolioHistory`) |
| PortfolioDailyValueService.java | Daily portfolio value computation |
| TechnicalAnalysisService.java | Indicator calc orchestration |
| TechnicalIndicatorPersistenceService.java | Indicator CRUD |
| TechnicalIndicatorCoverageService.java | Coverage gap detection |
| BacktestService.java | Strategy simulation + stop-loss |
| YahooFinanceService.java | Yahoo API client (cached `stockHistory`) |
| YahooFinanceSyncService.java | Persist Yahoo data |
| StockHistoryService.java | Yahoo history fetch |
| StockSyncService.java | Backfill orchestration |
| DailyPriceService.java | Price CRUD + portfolio recalc trigger |
| BreakoutDetector.java | Volume/range/price breakout |
| SupportResistanceService.java | S/R level calc (cached `supportResistanceLevels`) |
| WatchlistService.java | Watchlist CRUD |
| WatchlistOpportunityService.java | Watchlist opportunity scoring |
| FiiDiiService.java | NSE FII/DII data |
| CorporateEventService.java | NSE corporate events |
| ShadowSignalService.java | A/B signal logic comparison |
| ScoreParameterService.java | Toggle scoring factors on/off |
| StrategyConfigService.java | Strategy priority/enable config |
| StrategyConditionService.java | DB condition overrides |
| StrategyInfluenceService.java | Strategy override logic |
| StrategyDailyWeightService.java | Daily strategy weight adj |
| StrategyResultsService.java | Strategy results persistence |
| SignalThresholds.java | All signal constants (BUY_THRESHOLD=3, etc.) |
| DataAvailabilityService.java | Data health check |
| TrailingStopService.java | ATR-based trailing stop |
| PositionSizingService.java | Risk-based position sizing |
| TechnicalAnalysisUtils.java | RSI calc + divergence detection |
| StockSplitDetector.java | Split detection + price adj |
| PriceAggregationService.java | Weekly/monthly aggregation |
| EmaCrossScreenerService.java | EMA-20↑50 cross screener (batched, cached `emaCross`) |
| ApiRateLimiter.java | In-memory rate limiter |
| SMCDetectionService.java | SMC/FVG detection (cached `smcPatterns`) |
| FundamentalDataService.java | Fundamental data fetch/screen |

### Calculators (35) — `src/main/java/org/example/service/calculator/`
- **Interface**: `IndicatorCalculator.java` — `calculate(List<DailyPrice>): BigDecimal`
- **List**: Rsi, Sma (generic), Sma20/50/200/44, Ema, Ema20, EmaSeriesCalculator, MacdLine, MacdSignal, BollingerUpper/Lower, StochK/D, WilliamsR, ATR(14), CCI(20), StochRsi, Adx(+DI/-DI), PlusDi, MinusDi, UltimateOsc(7/14/28), Roc(12), Obv, VolumeRatio, Vwap, DollarVolume, Amihud, Ichimoku(5 components), ReversalDetector, CandlestickPattern(8 patterns), SupportResistance, IndicatorComputationService — **35 files total (33 calculators + interface + orchestration)**

### Institutional (8) — `src/main/java/org/example/service/institutional/`
- InstitutionalHoldingService, NseXbrlShareholdingService, BulkDealService, BlockDealService, ClientClassifier, InstitutionalScoreService, InstitutionalScreenerService, NseSessionManager

### SMC (6) — `src/main/java/org/example/service/smc/`
- SwingDetector, SwingPoint, ZoneDetector, BOSDetector, MarketStructure, ATRProvider

## STRATEGY ENGINE — `src/main/java/org/example/strategy/` — 18 files

| File | Role |
|------|------|
| base/TradingStrategy.java | Abstract base: evaluate(indicator, prices) → StrategyResult |
| model/StrategyResult.java | Record: signal, confidence, reason, strategyName, priority, contribution |
| model/AggregatedSignalResult.java | Record: finalSignal, score, confidence, breakdown, supporting, opposing |
| model/StrategySignal.java | Enum: STRONG_BUY/BUY/HOLD/SELL/STRONG_SELL |
| engine/MultiStrategySignalEngine.java | Entry: evaluate(stockId), evaluateWithHistory(days) |
| aggregator/StrategySignalAggregator.java | Weighted agg + confidence + categorization + fail-safe |
| config/StrategyConfig.java | Spring beans for 12 strategy instances (11 classes) |

| impl/ | 11 strategy classes (12 beans): Rsi, Macd, MovingAverageCrossover, BollingerBand, Volume, Breakout, CandlestickPattern, CandlestickContext(×2 at-support/at-resistance), Sma44PullbackBounce, Liquidity, EmaCrossover |

## ENTITIES (30) — `src/main/java/org/example/entity/`

| Entity | Key Fields |
|--------|------------|
| Stock.java | symbol, name, sector, industry, yahooSymbol, portfolio fields (legacy) |
| DailyPrice.java | stock_id FK, OHLCV, volume, price_date (UK: stock_id+date) |
| TechnicalIndicator.java | stock_id FK, IndicatorType enum(31), value, calculation_date (EAV) |
| IndicatorType.java | Enum 31 values + `isDirectional()` |
| SignalRecord.java | stockId, recordedAt, recommendation, compositeScore, confidence, coverage, priceAtSignal, forward returns, accuracy |
| SupportResistanceLevel.java | stock FK, LevelType, value, strength, touchCount, order |
| LevelType.java | Enum 11 values: SWING_LOW/HIGH, PIVOT_P/S1/S2/S3/R1/R2/R3, MAJOR_SUPPORT/RESISTANCE |
| Portfolio.java | name, description, isDefault |
| PortfolioHolding.java | portfolio FK, stock FK, quantity, avgPrice |
| PortfolioTransaction.java | ledger: portfolio FK, stock FK, type(BUY/SELL), qty, price |
| TransactionType.java | Enum: BUY, SELL |
| PortfolioSnapshot.java | stock FK, portfolio FK, snapshot_date, computed P&L fields |
| PortfolioDailyValue.java | portfolio FK, date, totalValue |
| InstitutionalHolding.java | stock FK, quarterEndDate, fii%, dii%, mf%, promoter%, public% |
| BulkDeal.java | stock FK, deal_date, client, B/S, qty, price, clientCategory |
| BlockDeal.java | Same as BulkDeal |
| FiiDiiData.java | UK: date, fiiBuy/Sell, diiBuy/Sell, net |
| CorporateEvent.java | symbol, event_date, purpose |
| FundamentalData.java | stock FK, sharesOutstanding, marketCap, pe, etc. |
| Watchlist/WatchlistItem.java | Named lists + junction |
| StrategyConfig.java | strategyName, priority, enabled |
| StrategyConditionGroup.java | strategy FK, condition rules |
| StrategyConditionStatsCache.java | strategy FK, stats cache |
| StrategyStockResult.java | stock FK, strategy FK, signal, eventDate, confidence |
| StrategyDailyWeight.java | Daily weight adjustments |
| ScoreParameterConfig.java | Scoring factor toggles |
| StartupTaskLog.java | taskId, runDate, status, completedAt |
| SignalHistoricalPerformance.java | Precomputed forward returns |
| ShadowSignalRecord.java | A/B comparison record |
| SMCPatternDTO.FVGEntry.java (DTO) | Fair Value Gap — `src/main/java/org/example/dto/SMCPatternDTO.java:75` (not JPA) |

## FRONTEND (15 HTML + 30 JS) — `src/main/resources/static/`

| Page | JS Entry | Purpose |
|------|----------|---------|
| index.html | js/portfolio.js (34K) | Dashboard: P&L, signals table, 3 charts, transactions |
| stock-detail.html | js/stock-detail.js (86K) | 6 charts, AI insights, backtest, institutional tab |
| stocks.html | js/stock-management.js (27.7K) | CRUD, CSV import, alerts |
| watchlist.html | js/watchlist.js (33.6K) | CRUD, sector chart |
| institutional-dashboard.html | (inline) | Scores, holdings, deals, 7 screeners |
| strategy.html | strategy-manager.js | Strategy config + conditions |
| rsi-analysis.html | js/rsi-analysis.js | RSI overview table |
| price-entry.html | js/price-entry.js | Manual OHLCV entry |
| stock-history.html | js/stock-history.js | Yahoo history viewer |
| portfolio-transactions.html | js/portfolio-transactions.js (26K) | Transactions ledger, filters, CSV export |
| fundamentals-screener.html | js/fundamentals-screener.js | Fundamental screening |
| history-summary.html | js/history-summary.js | Batch Yahoo history summary |
| strategy-results.html | strategy-results.js | Strategy run results |
| watchlist-opportunities.html | js/watchlist-opportunities.js | Watchlist opportunities |
| ema-cross-screener.html | js/ema-cross-screener.js | EMA-20↑50 cross screener |
| **Shared**: js/api.js (~150+ funcs), js/navigation.js, js/modules/**, css/theme.css, css/styles.css + vendor: chartjs-adapter, chartjs-plugin-annotation |

### Notable API Wrappers in js/api.js
| Wrapper | Endpoint | Usage |
|---------|----------|-------|
| `getAllStocks()` | GET /api/stocks | List all stocks |
| `getSectors()` | GET /api/stocks/sectors | List distinct sectors |
| `getPortfolios()` | GET /api/portfolios | List portfolios |
| `getStartupTasks()` | GET /api/startup-tasks | List startup tasks |

## SCHEDULERS (10) + STARTUP (7) — `src/main/java/org/example/scheduler/` & `startup/`

| Scheduler | Cron / Trigger | Purpose |
|-----------|---------------|---------|
| MarketAnalysisScheduler | `0 0 7 * * SUN` + `0 0 9 * * ?` | Weekly Yahoo backfill + daily indicator calc |
| InstitutionalHoldingScheduler | `0 30 4 * * SUN` + `0 30 12 * * MON-FRI` | Institutional fetch |
| SignalAccuracyScheduler | `0 0 3 * * *` | Signal accuracy rollup |
| DailySnapshotScheduler | `0 45 5 * * MON-FRI` UTC | Daily portfolio snapshot |
| FundamentalDataScheduler | `0 0 8 * * SUN` UTC | Weekly fundamental sync |
| StrategyDailyWeightScheduler | `0 0 10 * * MON-FRI` | Daily weight rebalance |
| IndicatorStartupTask | Startup | Backfill indicators on boot |
| SignalStartupTask | Startup | Warm signal cache on boot |
| SellLotBackfillStartupTask | Startup | Backfill sell lots on boot |
| SignalPerformanceScheduler | Startup | Precompute SignalHistoricalPerformance |

| StartupTask impls (7) | `src/main/java/org/example/startup/` — StartupDataSyncTask, FiiDiiStartupTask, FundamentalStartupTask, PortfolioMigrationStartupTask, SignalAccuracyStartupTask + scheduler startup tasks above |

## FEATURE → FILE MAPPING

| Task | Files to Touch |
|------|----------------|
| **Add endpoint** | Controller, DTO, Service, Repository (if needed), api.js |
| **Add indicator** | IndicatorType.java (enum), Calculator class, IndicatorComputationService.java, SignalService.java (scoring), tests |
| **Add strategy** | Strategy impl class, StrategyConfig.java (bean), application.properties (priority), strategy-manager.js (card), tests |
| **Add frontend page** | new .html, new .js (IIFE), api.js (wrappers), navigation.js (register) |
| **Modify signal logic** | SignalService.java (computeWeightedScore:1667), SignalThresholds.java (constants), SignalDTO.java, ScoreParameterService |
| **Modify dashboard chart** | index.html, portfolio.js (owns all 3 charts + signals table) |
| **Modify stock detail** | stock-detail.html, stock-detail.js (owns 6 charts + backtest + insights) |
| **Portfolio CRUD** | PortfolioManagementController, PortfolioService, Portfolio entity |
| **Transactions** | PortfolioManagementController, PortfolioTransactionService, PortfolioTransaction entity |
| **Institutional** | InstitutionalHoldingController, 8 institutional services, institutional-dashboard.html |
| **Watchlist** | WatchlistController, WatchlistService, watchlist.html, watchlist.js |
| **Backtest** | BacktestController, BacktestService |
| **FII/DII** | FiiDiiController, FiiDiiService, FiiDiiData entity |
| **Corp events** | CorporateEventController, CorporateEventService, CorporateEvent entity |
| **SMC/FVG** | SMCController, SMCDetectionService, service/smc/ (SwingDetector, ZoneDetector etc.) |
| **Strategy config** | StrategyConfigController, StrategyConfigService, strategy-manager.js |
| **Score params** | ScoreParameterController, ScoreParameterService, ScoreParameterConfig entity |

## KEY CONSTANTS

| Constant | Location | Value |
|----------|----------|-------|
| Signal thresholds | SignalThresholds.java:16 | SBUY≥7, BUY≥3, HOLD -3~2, SELL≤-4, SSELL≤-7 |
| ADX multiplier | SignalThresholds.java:62 | <15→0.3, 15-25→0.5+0.02*(adx-15), 25-35→0.7+0.01*(adx-25), ≥35→1.0; counter-trend ×0.7 |
| Bearish discount | SignalThresholds.java:23 | 0.85 default; 0.9 minimal / 1.0 full reversal exception; floor 0.9 |
| Strategy priorities | application.properties:48 | 12 beans: rsi 7, macd 7, ma-crossover 8, bollinger 6, volume 5, candlestick.at-support 4/at-resistance 4/pattern 5, liquidity 3, sma44 8, ema-crossover 7; aggregator buy 3.0/sell -3.0 |
| Cache names | `src/main/java/org/example/CacheConfig.java:25` | 10 caches: latestIndicators, indicatorHistory, stockHistory, signals, signalDto, portfolioHistory, supportResistanceLevels, institutionalScores, smcPatterns, emaCross (props has 6) |
| API base | js/api.js | `API_BASE_URL = '/api'` |
| Test count | — | 86 test files (unit + integration) — `src/test/**/*.java` |
| Factor count | SignalService.java:1247 | 19 scoring factors (indicatorCoverage) |

