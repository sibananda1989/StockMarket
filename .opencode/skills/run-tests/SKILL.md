---
name: run-tests
description: Use when running, debugging, or writing tests — backend unit tests, integration tests, or Playwright E2E tests. Front-load keywords: test, run tests, unit test, playwright, e2e, mvn test, test failure, test debug.
---

# Run Tests

## Quick Run

```bash
./run-tests.sh                  # progress-bar runner (recommended for full suite)
mvn test                        # plain Maven
mvn test -Dtest=ClassName       # specific class
mvn test -Dtest=Class#method    # specific method
```

## Backend Unit Tests (JUnit 5 + Mockito)

```bash
mvn test
```

Run a specific test class:
```bash
mvn test -Dtest=SignalServiceTest
```

Run a specific test method:
```bash
mvn test -Dtest=SignalServiceTest#testComputeWeightedScore
```

**Important:** The project uses JaCoCo for code coverage. Coverage reports are generated at `target/site/jacoco/index.html`.

**Test files:** `src/test/java/org/example/` (**63 test files** total)

| Category | Key Tests |
|----------|-----------|
| Core services | StockServiceTest, SignalServiceTest, PortfolioServiceTest, WatchlistServiceTest, BreakoutDetectorTest, StrategyConfigServiceTest, YahooFinanceServiceTest |
| Calculators | RsiCalculatorTest, SmaCalculatorTest, MacdLineCalculatorTest, IchimokuCalculatorTest, VwapCalculatorTest, AdxCalculatorTest, StochRsiCalculatorTest, ReversalDetectorTest, ... (34 calculator tests) |
| Strategy (multi-strategy engine) | RsiStrategyTest, MacdStrategyTest, MovingAverageCrossoverStrategyTest, BollingerBandStrategyTest, VolumeStrategyTest, CandlestickPatternStrategyTest, StrategySignalAggregatorTest, MultiStrategySignalEngineTest, StrategyConditionServiceTest (**9 tests**) |
| Institutional | BlockDealServiceTest, BulkDealServiceTest, ClientClassifierTest, NseXbrlShareholdingServiceTest, InstitutionalScoreServiceTest |
| Integration / Controller | PortfolioManagementControllerIntegrationTest, SignalControllerIntegrationTest, PortfolioChartControllerIntegrationTest, PortfolioChartValidationTest, MultiStrategySignalControllerTest, StrategyConfigControllerTest, StockControllerSearchTest |
| Scheduler | SignalAccuracySchedulerTest |

## Playwright E2E Tests

```bash
npx playwright test
```

Run a specific spec:
```bash
npx playwright test tests/signals-insights.spec.js
```

Run with UI (visual debugger):
```bash
npx playwright test --ui
```

**Important:** The app must be running on `http://localhost:8080` before executing E2E tests. The playwright config auto-starts the app via `mvn spring-boot:run`.

**Test files:** `tests/` directory (5 spec files)

| Spec | What It Tests |
|------|---------------|
| frontend.spec.js | General frontend functionality |
| signals-insights.spec.js | Signal generation and insights UI |
| portfolio-chart-fixes.spec.js | Portfolio chart rendering |
| watchlist-portfolio.spec.js | Watchlist + portfolio operations |
| stock-management-portfolio.spec.js | Stock CRUD and portfolio management |

## Troubleshooting

### Backend test failures
- Check `app.log` and `server.log` for stack traces
- Ensure MySQL is running on `localhost:3306`
- Tests use `application.properties` from `src/test/resources/` if it exists

### E2E test failures
- Check `playwright-report/` for screenshots and videos of failures
- Run with `--trace on` for detailed traces: `npx playwright test --trace on`
- Increase timeouts in `playwright.config.js` if tests are flaky on slow connections
