# Project Context — StockTracker

## Overview
StockTracker is a portfolio tracking and technical analysis platform for the Indian stock market (NSE). It helps manage multiple portfolios, compute buy/sell signals from 16+ technical indicators, track institutional activity (FII/DII/MF), and backtest trading strategies.

## Tech Stack
| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2.5, Maven |
| Database | MySQL 8 (database: `stockmarket`) |
| Cache | Caffeine (15-min TTL, 500 entries max) |
| Frontend | Static HTML + JS + Chart.js (no SPA framework) |
| Styling | Tailwind CSS (CDN) |
| Monitoring | Spring Actuator + Micrometer + Prometheus |
| Testing | JUnit, Playwright |
| Docs | Springdoc OpenAPI at `/swagger-ui.html` |

## Directory Structure
```
stockmarket/
├── docs/                          # Project documentation
├── src/
│   ├── main/
│   │   ├── java/org/example/
│   │   │   ├── StockTrackerApplication.java
│   │   │   ├── CacheConfig.java
│   │   │   ├── config/            # CORS, filters
│   │   │   ├── controller/        # 15 REST controllers
│   │   │   ├── dto/               # 36 DTO classes
│   │   │   ├── entity/            # 16 JPA entities
│   │   │   ├── exception/         # 7 exceptions + GlobalExceptionHandler
│   │   │   ├── metrics/           # Micrometer metrics
│   │   │   ├── repository/        # 18 Spring Data JPA repos
│   │   │   ├── scheduler/         # 5 scheduled tasks
│   │   │   ├── service/
│   │   │   │   ├── calculator/    # 20 indicator calculators
│   │   │   │   └── institutional/ # 7 institutional services
│   │   │   └── startup/           # 2 startup tasks
│   │   ├── resources/
│   │   │   ├── static/
│   │   │   │   ├── index.html     # Dashboard
│   │   │   │   ├── stock-detail.html
│   │   │   │   ├── stocks.html, watchlist.html, etc.
│   │   │   │   ├── js/
│   │   │   │   │   ├── api.js           # Shared API client
│   │   │   │   │   ├── portfolio.js     # Dashboard logic
│   │   │   │   │   ├── stock-detail.js  # Stock detail charts
│   │   │   │   │   ├── stock-management.js
│   │   │   │   │   └── ...
│   │   │   │   └── css/
│   │   │   └── application.properties
│   │   └── test/
│   └── test/
├── .opencode/
│   └── opencode.json              # AI agent configuration
└── pom.xml
```

## Key Packages

### `controller/` — 15 REST Controllers
`StockController`, `SignalController`, `PriceController`, `RsiController`, `IndicatorController`, `SupportResistanceController`, `PortfolioController`, `PortfolioHistoryController`, `BacktestController`, `FiiDiiController`, `EventsController`, `FundamentalController`, `InstitutionalController`, `WatchlistController`, `DhanController`

### `service/calculator/` — 20 Indicator Calculators
Each implements `TechnicalIndicatorCalculator` interface. Include: RSI, SMA (20/50/200), EMA, MACD (line + signal), Bollinger (upper + lower), Stochastic (K + D), Williams %R, ATR, CCI, StochRSI, ADX (+DI, −DI), Ultimate Oscillator, ROC, OBV.

### `service/institutional/` — 7 Services
`InstitutionalHoldingService`, `NseXbrlShareholdingService`, `BulkDealService`, `BlockDealService`, `ClientClassifier`, `InstitutionalScoreService`, `InstitutionalScreenerService`, `NseSessionManager`

## Key Flows

### Signal Generation
```
Price data → 16 factor scores → weighted sum → ADX adjustment
→ volume bonus → FII/DII adjustment → recommendation → confidence score
→ target/stop → position size
```

### Daily Data Pipeline
```
Dhan API sync (4:15 PM IST) → Indicator calculation → S/R calculation
→ Snapshot creation (startup) → Accuracy marking (3 AM) → Performance agg (2:30 AM)
```

### Frontend Data Loading
```
Page load → getStockById → getPortfolioHolding → loadStockData →
  getLatestPrice + getLatestRsi + getFiiDiiData + getEventData →
  loadAllCharts → price chart, candlestick, P&L, RSI, S/R, signals,
  institutional, fundamentals, screener, backtest
```

## Key Design Decisions
- **Buyer bias**: Sell-side penalties reduced, buy-side rewards retained, SELL thresholds made stricter
- **Multi-portfolio**: Holdings separated by portfolio, aggregate views available
- **CSV upsert**: Import checks existing holding before insert (preserves `created_at`)
- **Error isolation**: Each chart/section loads independently — one failure doesn't break the page
- **Caching**: Signal results cached 15 min; manual restart clears cache

## Running Locally
- Start: `mvn clean package -DskipTests && java -jar target/stockmarket-1.0-SNAPSHOT.jar`
- Access: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Logs: `/tmp/app.log`
