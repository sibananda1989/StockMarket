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

## Quick Reference

**For AI agents**: Read `docs/PROJECT_MANIFEST.md` first — it contains the complete file index, feature mappings, and key constants (~2.5K tokens).

**Directory layout**:
```
stockmarket/
├── docs/                          # Project documentation (start with PROJECT_MANIFEST.md)
├── src/main/java/org/example/     # Backend Java source
│   ├── controller/                # 15+ REST controllers
│   ├── service/calculator/        # 22 indicator calculators
│   ├── service/institutional/     # 8 institutional activity services
│   ├── strategy/                  # Multi-strategy signal engine (9 strategies)
│   ├── entity/                    # 22+ JPA entities
│   ├── repository/                # 18+ Spring Data repos
│   ├── dto/                       # 36+ DTO classes
│   ├── scheduler/                 # 5 scheduled tasks
│   ├── startup/                   # 2 startup tasks
│   └── exception/                 # 7 exceptions + GlobalExceptionHandler
├── src/main/resources/static/     # Frontend (9 HTML + 9 JS + shared)
│   ├── js/api.js                  # All API wrappers (~200 functions)
│   └── js/*.js                    # One IIFE module per page
├── tests/                         # Playwright E2E specs
├── .opencode/                     # AI agent configuration
└── pom.xml                        # Maven build
```

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
