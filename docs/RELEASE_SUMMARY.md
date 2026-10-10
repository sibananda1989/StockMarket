# Release Summary — Buy/Sell Transaction Feature

**Date:** 2026-07-18
**Status:** Implemented, verified, and code-reviewed (APPROVED). Not yet deployed.

## What Changed
A new append-only transaction ledger lets you record real BUY/SELL trades against any
portfolio+stock. The backend maintains a running average-cost model: BUYs blend the
running average (fees added to cost basis); SELLs retain the average and book realized
P&L = `(price - avg) * qty - fees`. Over-selling is rejected (HTTP 400). A full sell
removes the holding and its snapshot; the stock's aggregate qty/avgPrice is recomputed
from remaining portfolios. Deleting a transaction replays the remaining ledger
chronologically to keep holdings consistent. Multi-portfolio aware. Realized P&L is
captured per sell (no summary report yet in v1). CSV import is unchanged.

## How To Use
- **Frontend:** Dashboard holdings table now has **Buy** / **Sell** buttons that open a
  transaction modal; a **Transaction History** panel shows past trades.
- **API:**
  - `POST /api/portfolios/{id}/transactions` — body `{stockId, type, quantity, price, fees}`
  - `GET /api/portfolios/{id}/transactions` — optional `?stockId=` filter
  - `DELETE /api/portfolios/{id}/transactions/{txnId}`

## Migration Notes
- New `portfolio_transactions` table is auto-created on startup (`ddl-auto=update`).
  **No manual migration required.**
- Static assets are cache-busted (`index.html` now loads with `?v=20260718txn`).

## Known Issues
- Pre-existing, **unrelated** failure in `StrategyConditionServiceTest`
  (seed-count 31 vs 36 expectation) — not caused by this feature; separate ticket.

## Tests
28 feature tests pass (10 service + 11 integration + 7 portfolio).
