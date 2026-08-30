# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project uses date-based "Unreleased" grouping pending versioned releases.

## [Unreleased]

### Added
- **Buy/Sell Transaction ledger** — Append-only `portfolio_transactions` table recording
  BUY/SELL against a portfolio+stock, with avg-cost tracking and realized P&L on sells.
  - `POST /api/portfolios/{id}/transactions` — record a BUY/SELL (over-sell rejected with 400).
  - `GET /api/portfolios/{id}/transactions` and `?stockId=` — list transaction history.
  - `DELETE /api/portfolios/{id}/transactions/{txnId}` — delete a transaction; holding is
    recomputed from the remaining ledger via exact chronological replay.
  - New entities `TransactionType`, `PortfolioTransaction`; repository
    `PortfolioTransactionRepository`; DTOs `CreateTransactionRequest`, `TransactionDTO`;
    service `PortfolioTransactionService` (full multi-portfolio avg-cost model).
  - Frontend: Buy/Sell buttons + transaction modal and Transaction History panel on the
    Dashboard holdings table (`js/portfolio.js`, `js/api.js`); `index.html` cache-busted
    with `?v=20260718txn`.
- **Spec & plan** — `docs/spec-buy-sell-transactions.md` (approved).
- **FVG zone visualization** — Fair Value Gaps now render as two-price-line boundaries (top+bottom) instead of a single midpoint line on the candlestick chart.
- **FVG state-based styling** — OPEN zones use solid lines (0.8 opacity), PARTIALLY_FILLED use dashed (0.5 opacity), FILLED use dotted (0.2 opacity).
- **FVG gap size in y-axis labels** — Labels now show gap size: `FVG↑ ₹12.5 O`.
- **FVG crosshair tooltip** — Hovering near a price inside an FVG zone shows a `⚠ FVG` badge with direction, gap size, and fill state.
- **Frontend SMC cache** — New `_cachedSmc` key-based cache (same pattern as `_cachedPriceHistory`) avoids redundant API calls when switching date ranges.

### Changed
- `CacheConfig.java` — Registered `"smcPatterns"` cache for SMC detection results.
- `PortfolioManagementController` — new transaction endpoints (all `ApiResponse`-wrapped).
- CSV import behavior unchanged.

### Notes
- New table auto-created on startup via `ddl-auto=update`; no manual migration required.
- Realized P&L is captured per sell only (no summary report in v1).
- Pre-existing unrelated test failure in `StrategyConditionServiceTest`
  (seed-count 31 vs 36 expectation) is NOT caused by this feature; tracked separately.

### Tests
- 28 new feature tests pass: `PortfolioTransactionServiceTest` (10),
  `PortfolioManagementControllerIntegrationTest` (11), `PortfolioServiceTest` (7).
- Pre-existing failures unchanged: `SignalControllerIntegrationTest` (nullable fields), `StrategyConditionServiceTest` (seed count 31→36), `PortfolioChartControllerIntegrationTest` (FK cleanup). None related to FVG changes.
