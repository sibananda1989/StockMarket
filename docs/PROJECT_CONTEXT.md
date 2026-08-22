# Project Context — StockTracker

## Overview
StockTracker is a portfolio tracking and technical analysis platform for the Indian stock market (NSE). It helps manage multiple portfolios, compute buy/sell signals from 31 technical indicators (15-factor scoring), track institutional activity (FII/DII/MF), SMC/FVG patterns, and backtest trading strategies.

## Tech Stack
| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2.5, Maven |
| Database | MySQL 8 (database: `stockmarket`) |
| Cache | Caffeine (10-15-min TTL, 500-2000 entries, 7 caches) |
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
│   ├── controller/                # 27 REST controllers (~153 mappings)
│   ├── service/                   # 37 services + calculators
│   ├── service/calculator/        # 34 indicator calculators (32 + interface + orchestration)
│   ├── service/institutional/     # 8 institutional activity services
│   ├── service/smc/               # 6 SMC services (SwingDetector, ZoneDetector, BOS etc.)
│   ├── strategy/                  # Multi-strategy signal engine (10 strategies)
│   ├── entity/                    # 29 JPA entities
│   ├── repository/                # 26 Spring Data repos
│   ├── dto/                       # 59 DTO classes
│   ├── scheduler/                 # 9 scheduled tasks
│   ├── startup/                   # 7 startup tasks
│   └── exception/                 # 7 exceptions + GlobalExceptionHandler
├── src/main/resources/static/     # Frontend (14 HTML + 15 JS incl. 2 vendor + shared)
│   ├── js/api.js                  # All API wrappers (~150+ functions)
│   └── js/*.js                    # One IIFE module per page
├── src/test/java/org/example     # 81 JUnit5/Mockito tests
├── tests/                         # Playwright E2E specs
├── .opencode/                     # AI agent configuration
└── pom.xml                        # Maven build (Java 17, Spring Boot 3.2.5)
```

## Key Flows

### Signal Generation
```
Price data → 15 factor scores (31 indicators) → weighted sum → ADX multiplier → volume bonus
→ FII/DII adjustment → bearish discount (0.85) → recommendation (SBUY≥7/BUY≥3/SELL≤-4/SSELL≤-7)
→ confidence score → target/stop (3×/2× ATR) → position size (1-5%)
```

### Daily Data Pipeline
```
Yahoo sync → Indicator calc (34 calculators) → S/R calc → Snapshot creation (startup + 5:45 AM daily)
→ Accuracy marking (3 AM) → Performance agg (2:30 AM) → Institutional/SMC (Mon-Fri/Sun crons)
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
