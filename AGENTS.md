# AGENTS.md — Stock Market Analysis Platform

Comprehensive operating guide for AI coding agents. Read this file FIRST before any task. It transfers the senior architect's understanding of the codebase so any model (including weak/free models) can work here safely and consistently.

## 1. Project Overview

A self-hosted **Stock Market Analysis Platform** for an Indian stock **buyer** (not intraday trader). It ingests daily OHLCV price data, computes 30+ technical indicators, runs a **multi-strategy signal engine** (9+ strategies), aggregates them into a BUY/SELL/HOLD recommendation per stock, tracks multiple portfolios with P&L, watches institutional activity (FII/DII, bulk/block deals, XBRL shareholding), detects SMC patterns (FVG/Order Blocks), and serves everything through a dark-themed vanilla-JS dashboard.

- **Backend**: Spring Boot 3.2.5, Java 17, Maven
- **Frontend**: Static HTML + vanilla JS (no framework, no build step) served from `src/main/resources/static/`
- **Database**: MySQL 8 (localhost:3306, root/root), JPA/Hibernate with `ddl-auto=update`
- **Build**: `mvn spring-boot:run -Dspring-boot.run.profiles=dev` (port 8080)
- **Main class**: `org.example.StockTrackerApplication` (configured in pom.xml spring-boot-maven-plugin `mainClass`)

**User context matters for signal design**: the user is a buyer, not a trader. Signals should be buyer-oriented (avoid over-selling, focus on accumulation zones). The user holds overlapping positions across multiple portfolios (Dhan, Ammu Groww, Groww Siba). CSV import is the primary stock-addition method.

## 2. Architecture

Layered Spring Boot monolith + static frontend. The backend does NOT render pages (Thymeleaf is present but HomeController serves static views only).

```
src/main/java/org/example/
  entity/       → JPA entities (22+ tables)
  repository/   → Spring Data JPA repositories
  service/      → Business logic (28+ services)
    calculator/ → Technical indicator calculators (22+)
    institutional/ → Institutional analysis services (8)
  strategy/     → Multi-strategy signal engine
    base/       → TradingStrategy abstract base class
    model/      → StrategyResult, AggregatedSignalResult, StrategySignal
    impl/       → 9+ concrete strategies
    engine/     → MultiStrategySignalEngine (entry point)
    aggregator/ → StrategySignalAggregator
    config/     → StrategyConfig Spring beans
  controller/   → REST controllers (15+, return ApiResponse<T>)
  dto/          → Request/response DTOs
  exception/    → GlobalExceptionHandler + 6 custom exceptions
  startup/      → @EventListener(ApplicationReadyEvent.class) seeding
src/main/resources/
  static/       → Frontend (html, js, css) — served as-is
  migrations/   → Manual SQL migrations (column-width changes only)
  application.properties
```

**Why this architecture exists**: 
- Controllers stay thin (delegate to services) so API shape and business rules evolve independently.
- The frontend is static no-build vanilla JS because the platform is a personal tool — no framework toolchain, no CI/CD complexity, instant iteration.
- The strategy engine is a separate package because signal logic is the core product value and is independently testable without HTTP/DB.

## 3. Directory / Module Responsibilities

| Directory | Responsibility |
|-----------|----------------|
| `entity/` | JPA entities; field naming must follow the `signal` reserved-word workaround (see §8) |
| `repository/` | Spring Data interfaces; derived queries; `@Transactional` required on `deleteBy*` callers (see §9) |
| `service/` | All business logic; services are `@Service @RequiredArgsConstructor @Slf4j`; return DTOs |
| `service/calculator/` | Stateless indicator calculators implementing `IndicatorCalculator` (interface: `calculate(List<DailyPrice>): BigDecimal`) |
| `strategy/` | Signal engine; strategies extend `TradingStrategy` and implement `evaluate(stockId, indicators, prices)` |
| `controller/` | REST endpoints; every method returns `ResponseEntity<ApiResponse<T>>` |
| `dto/` | API payloads; `ApiResponse<T>` wrapper + per-feature DTOs |
| `exception/` | `GlobalExceptionHandler` (@RestControllerAdvice) + custom exceptions |
| `startup/` | Post-startup seeding/sync via `@EventListener(ApplicationReadyEvent.class)` |
| `static/` | Frontend pages + JS. Each page = one HTML + one IIFE JS file |
| `migrations/` | Manual SQL for column-width changes only (Hibernate ddl-auto won't widen columns) |

## 4. Important Classes and Responsibilities

### Backend core
- `StockTrackerApplication` — Spring Boot entry point
- `dto/ApiResponse<T>` — universal API wrapper: `{status, message, data, timestamp}` with static `success(msg,data)`, `success(data)`, `error(msg,errorCode)`, `isSuccess()`. ALL controllers return this.
- `exception/GlobalExceptionHandler` — `@RestControllerAdvice` with 12 `@ExceptionHandler` methods. Maps custom exceptions to HTTP status + machine-readable error codes (`STOCK_NOT_FOUND`, `NO_PRICE_DATA`, `PRICE_ALREADY_EXISTS`, `INVALID_PRICE`, `ILLEGAL_ARGUMENT`, `CONSTRAINT_VIOLATION`, `NOT_FOUND`, `INTERNAL_ERROR`). Sanitizes messages (strips `\r\n\t`) before logging/returning to prevent log injection (CWE-117). Silently handles client aborts (`AsyncRequestNotUsableException`, broken pipe).
- Custom exceptions: `ResourceNotFoundException`, `StockNotFoundException`, `NoPriceDataException`, `StockHistoryNotFoundException`, `PriceAlreadyExistsException`, `InvalidPriceException`
- `service/SignalService` — CORE scoring service. 15-factor weighted scoring, BUY/SELL/HOLD thresholds, confidence, targets. See §11 signal thresholds.
- `service/calculator/IndicatorComputationService` — orchestrates all indicator calculations per stock
- `strategy/base/TradingStrategy` — abstract base; subclasses implement `evaluate(Long stockId, List<TechnicalIndicator> indicators, List<DailyPrice> prices)`; helper `resolveIndicator()`/`resolvePreviousIndicator()` extract latest/second-latest indicator values safely
- `strategy/engine/MultiStrategySignalEngine` — public entry: `evaluate(stockId)`, `evaluate(stockId, activeStrategyNames)`, `evaluateWithHistory(days)`; fetches data, runs strategies (parallel via CompletableFuture/ForkJoinPool), delegates to aggregator
- `strategy/aggregator/StrategySignalAggregator` — weighted aggregation + confidence + categorization + fail-safe HOLD
- `strategy/model/` — `StrategyResult` (record: signal, confidence, reason, strategyName, priority, contribution), `AggregatedSignalResult` (finalSignal, score, confidence, breakdown, supporting, opposing), `StrategySignal` enum (STRONG_BUY/BUY/HOLD/SELL/STRONG_SELL)

### Frontend
- `static/js/api.js` — ALL API call wrappers. `API_BASE_URL='/api'`, generic `apiCall(endpoint, options)`, one `async function` per endpoint (~200 wrappers). Frontend pages import these by function name.
- `static/js/navigation.js` — nav injection + theme toggle
- `static/js/*.js` (per page) — IIFE module pattern, no globals; state inside closure

### Key entities
`Stock`, `DailyPrice` (UK: stock_id+date), `TechnicalIndicator` (EAV: stock_id+IndicatorType+value+calculation_date), `SignalRecord`, `Portfolio`, `PortfolioHolding`, `PortfolioTransaction`, `PortfolioSnapshot`, `InstitutionalHolding`, `BulkDeal`, `BlockDeal`, `FiiDiiData`, `CorporateEvent`, `Watchlist/WatchlistItem`, `StrategyConfig`, `StrategyConditionGroup`, `StrategyDailyWeight`, `ScoreParameterConfig`, `StartupTaskLog`, `SignalHistoricalPerformance`, `ShadowSignalRecord`, `FVGPattern`

## 5. Data Flow

1. **Price ingestion**: CSV import or `StockHistoryService` (Yahoo Finance) → `DailyPrice` rows
2. **Indicator computation**: `IndicatorComputationService` reads `DailyPrice` → computes each indicator → persists `TechnicalIndicator` rows (EAV pattern)
3. **Signal generation**: `SignalService.getComputedSignal(stockId)` (legacy 15-factor) OR `MultiStrategySignalEngine.evaluate(stockId)` (multi-strategy) → reads indicators + prices → each `TradingStrategy.evaluate()` returns `StrategyResult` → `StrategySignalAggregator` combines → `AggregatedSignalResult` → `SignalDTO`
4. **API delivery**: Controller → `ApiResponse<T>` JSON → frontend `api.js` wrapper → page JS renders charts/tables
5. **Portfolio P&L**: transactions → avg-cost tracking (`PortfolioTransactionService`) → daily snapshots (`PortfolioSnapshotService`) → dashboard

**Why**: the EAV `TechnicalIndicator` table decouples indicator storage from schema — adding a new indicator is a new enum value + calculator, no table change.

## 6. API Conventions

- Base path: `/api/...` (frontend `API_BASE_URL = '/api'`)
- Every controller method returns `ResponseEntity<ApiResponse<T>>`
- Success: `ApiResponse.success(data)` → status `"success"`, HTTP 200
- Errors: handled by `GlobalExceptionHandler` → status `"error"`, machine-readable `errorCode` in message, appropriate HTTP status (404/400/409/500)
- Controller pattern (see `SignalController`):
  - `@RestController @RequestMapping("/api/signals") @RequiredArgsConstructor`
  - Constructor injection via `private final XxxService xxxService;` (Lombok `@RequiredArgsConstructor`)
  - Query params: `@RequestParam(required = false)` or `@RequestParam(defaultValue = "...")`
  - Path params: `@PathVariable Long stockId`
  - Null-signal case returns HTTP 200 with null data + message (e.g., "Insufficient data for signal") rather than 404
- Frontend pages call only `api.js` wrappers, never raw `fetch`

## 7. Database Conventions

- MySQL 8, `spring.datasource.url=jdbc:mysql://localhost:3306/stockmarket?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true`
- `spring.jpa.hibernate.ddl-auto=update` — Hibernate creates/alters tables automatically. **No manual SQL for new tables.**
- **Manual migrations** (`src/main/resources/migrations/` V1..V5) ONLY for column-width changes — Hibernate ddl-auto cannot widen existing MySQL columns. Naming: `V{n}__description.sql`
- Unique keys: e.g., `DailyPrice` (stock_id + price_date), `FiiDiiData` (date)
- `spring.jpa.open-in-view=false` — no lazy loading outside transactions; fetch what you need in the service
- HikariCP pool: max 20, min idle 5, leak detection 30s
- `@Transactional` on any method calling derived `deleteBy*` queries (e.g., `deleteByStrategyName`) or the call throws 500

## 8. Coding Standards

- Java 17, Spring Boot 3.2.5, Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`)
- Services: `@Service`, constructor injection with `private final`, no field injection
- Controllers: thin — no business logic, delegate to services
- DTOs: Lombok `@Data`/records; `ApiResponse<T>` for all responses
- Entities: JPA annotations; use `@Column(name = "signal_type")` etc. for reserved words
- Frontend JS: IIFE pattern `(function(){ ... })();`, NO global variables, state in closure, API via `api.js` wrappers
- Cache-busting: bump `window._appVer` in HTML + `?v=` query params on JS includes after frontend changes (browser caches aggressively)
- Toggle switches: avoid `pointer-events-none` on parent containers — it blocks clicks on child elements
- Comments: explain WHY (non-obvious decisions), not WHAT

## 9. Error-Handling Rules

- Throw custom exceptions from `exception/` package; let `GlobalExceptionHandler` map them
- Never return raw stack traces or internal error messages to clients — handler sanitizes
- Client aborts (broken pipe / AsyncRequestNotUsableException) are handled silently (WARN/Debug, no stack trace) — do not treat as server errors
- Log exceptions with `log.error("context: {}", sanitizedMessage, ex)` in the handler; services log at DEBUG/INFO
- Frontend: API wrappers throw on `!response.ok` with server message; pages catch per-feature and show user-friendly fallback (e.g., "No data", "Failed to load")

## 10. Logging Rules

- Use SLF4J `@Slf4j`, never `System.out.println`
- `logging.level.org.example=DEBUG`, `SignalService=INFO`
- Log file: `logs/stockmarket.log` (absolute path configured)
- INFO: lifecycle events (app start, strategy evaluation per stock, sync runs)
- DEBUG: detailed data flow
- WARN: recoverable failures (API fallbacks, client aborts)
- ERROR: unhandled exceptions (via GlobalExceptionHandler)
- Never log full price arrays or sensitive data

## 11. Signal Engine (Domain Constants)

- **Scoring thresholds** (`SignalThresholds.java`): STRONG BUY ≥ 7, BUY ≥ 5, HOLD -4..+4, SELL ≤ -5, STRONG SELL ≤ -7
- **ADX multiplier** (`SignalService.adxMultiplier()`): <15 → 0.3; 15–25 → linear 0.5–0.7; 25–35 → linear 0.7–1.0; ≥35 → 1.0; counter-trend → ×0.7
- **Bearish discount**: `SignalThresholds.BEARISH_TREND_DISCOUNT` = 0.85 (with reversal exceptions)
- **Config flags**: `signal.gate.high-confidence.enabled` (default false)
- **Strategy priorities** (application.properties `strategy.*.priority`, 1-10): rsi=7, macd=7, ma-crossover=8, bollinger=6, volume=5, candlestick.at-support=4, candlestick.at-resistance=4, candlestick.pattern=5, liquidity=3, sma44-pullback=8
- **Aggregator thresholds**: `strategy.aggregator.buy-threshold=3.0`, `sell-threshold=-3.0`
- **Cache**: `@Cacheable("signals")` — takes effect only after app restart
- **Caffeine cache** (spring.cache.caffeine.spec): max 2000, expireAfterWrite 10m

## 12. Testing Rules

- JUnit 5 + Mockito + Spring Boot Test. `spring-boot-starter-test` scope test
- Test locations mirror main: `src/test/java/org/example/{controller,service,strategy,entity,scheduler}`
- Run all: `./run-tests.sh` (progress bar) or `mvn test`
- Run one class: `mvn test -Dtest=SignalServiceTest`
- Run one method: `mvn test -Dtest=SignalServiceTest#testComputeWeightedScore`
- Coverage: JaCoCo plugin (report at prepare-package/test phases)
- ~99 total tests (15 integration + 84 unit), ~37 strategy tests
- Every new indicator/strategy/service MUST have unit tests (calculators: edge cases + normal cases). Integration tests for controllers.
- Tests must not depend on live MySQL — mock repositories/services (Mockito) or use test fixtures

## 13. Git / Change-Management Rules

- Work in small, focused commits per feature/fix (thin vertical slices)
- One feature = one PR-style change set; keep frontend + backend changes for the same feature together
- Don't commit `logs/`, IDE files, or local DB artifacts (check `.gitignore`)
- Commit messages: imperative, concise, prefix with area (e.g., "Add RSI divergence detection to SignalService", "Fix S/R refresh button wiring")
- Never commit generated/binary files or secrets (DB credentials are local dev defaults)

## 14. Dependency Rules

- Do NOT add new dependencies without strong justification — the pom is deliberately minimal
- Already present: spring-boot-starter-web/data-jpa/validation/webflux/test/cache/actuator/thymeleaf, mysql-connector-j, Lombok, devtools, Caffeine, spring-retry, netty-resolver-dns-native-macos, springdoc-openapi-ui 1.6.14, resilience4j 2.2.0 (circuitbreaker/timelimiter), micrometer-registry-prometheus
- Prefer reusing existing patterns (Caffeine caching, Resilience4j for external NSE/Yahoo calls) over new infra
- External APIs (Yahoo Finance, NSE) must go through the existing service wrappers with rate limiting / circuit breakers where appropriate

## 15. Common Mistakes to Avoid

1. **Using `signal` as a column name** — it's a MySQL reserved word. Use `@Column(name = "signal_type")` in entities, map to/from DTO `signal` field in service layer.
2. **Calling derived `deleteBy*` queries without `@Transactional`** — throws 500.
3. **Frontend globals** — violating the IIFE pattern breaks the app (script order + closure collisions).
4. **Forgetting cache-busting** (`_appVer`/`?v=`) after frontend edits — users see stale pages.
5. **Seeding in `@PostConstruct`** — runs before Hibernate DDL; use `@EventListener(ApplicationReadyEvent.class)` for anything depending on tables.
6. **Widening columns via Hibernate** — ddl-auto won't do it; add a `migrations/V{n}` SQL script.
7. **Lazy loading outside transactions** — `open-in-view=false`; fetch eagerly in service.
8. **Reading files >5K tokens or scanning unrelated modules** — use `docs/PROJECT_MANIFEST.md` for file mapping, keep context budget small.
9. **Unrequested refactoring/cleanup** — fix only what was asked.
10. **`pointer-events-none` on toggle parents** — breaks child clicks.
11. **Returning internal error details to clients** — always go through GlobalExceptionHandler.

## 16. Rules for Modifying Existing Code

- Read `docs/PROJECT_MANIFEST.md` FIRST — it maps every feature to exact files
- Read only the specific function/method being changed (targeted line ranges; never whole files >5K tokens)
- Make minimal, surgical changes; preserve existing behavior unless the task says otherwise
- Keep the existing style (Lombok, constructor injection, IIFE JS, ApiResponse wrapping)
- If touching frontend, bump `window._appVer` and JS `?v=` params
- Update/extend tests for changed behavior; run `mvn test -Dtest=AffectedClassTest` before committing
- Don't "clean up" surrounding code

## 17. Rules for Creating New Code

- **New endpoint** → Controller + DTO + Service + Repository (if needed) + api.js wrapper
- **New indicator** → `IndicatorType.java` enum value + Calculator class (implements `IndicatorCalculator`) + register in `IndicatorComputationService` + optional SignalService scoring + tests
- **New strategy** → Strategy impl class (extends `TradingStrategy`) + `StrategyConfig.java` bean + `application.properties` priority + `strategy-manager.js` card + tests
- **New frontend page** → new .html + new .js (IIFE) + api.js wrappers + navigation.js registration
- Follow the existing file patterns exactly (see `src/main/java/org/example/...` existing examples)
- No speculative abstractions: no interface with one implementation, no config for values that never change

## 18. Debugging Workflow

1. Reproduce with the running app: `mvn spring-boot:run -Dspring-boot.run.profiles=dev` (MySQL must be up)
2. Check `logs/stockmarket.log` for the failing path (services log DEBUG, errors via GlobalExceptionHandler)
3. Trace the data flow (see §5): is the failure in ingestion, indicators, signal computation, or API rendering?
4. Use the `trace-signal` skill/workflow to walk through why a stock got a particular signal (16-factor scoring walkthrough)
5. Frontend issues: browser console errors; check `api.js` wrapper endpoints against controller paths; verify `_appVer`/cache-busting
6. For API errors, match the `errorCode` in the response to the GlobalExceptionHandler mapping
7. Run targeted tests (`mvn test -Dtest=XxxTest`) to isolate; fix root cause, not symptom

## Verification Checklist (before considering work done)

- [ ] Code compiles: `mvn compile -q` (or full `mvn test`)
- [ ] Targeted tests pass: `mvn test -Dtest=AffectedClassTest`
- [ ] All touched frontend files have cache-busted references
- [ ] New endpoints return `ApiResponse<T>` and are wrapped in api.js
- [ ] No new dependencies added without justification
- [ ] Only requested files changed — no unrelated cleanup
- [ ] MySQL reserved words handled with `@Column` overrides
- [ ] `deleteBy*` callers have `@Transactional`
- [ ] New tables rely on ddl-auto; column width changes use migrations/
- [ ] Logging uses SLF4J; no System.out
- [ ] Tests added/updated for new logic
