# Spec: Buy/Sell Transaction-Based Portfolio Management

**Status:** APPROVED (requirements clarified 2026-07-18)
**Phase:** Define
**Author:** Define Agent
**Date:** 2026-07-18

---

## 1. Problem Statement

Today, the platform only supports **bulk setting** a holding's quantity and average price via
`POST /api/portfolios/{id}/holdings` (`addHolding`) and `PATCH .../holdings/{holdingId}`
(`updateHolding`). These endpoints **overwrite** the holding's state rather than record an event.
The user — a stock **buyer** with multiple overlapping portfolios (Dhan, Ammu Groww, Groww Siba) —
cannot express the natural actions of **"buy more of a holding"** or **"sell part / all of a holding"**
as discrete transactions.

Consequences of the current model:
- No audit trail of *when* and *at what price* shares were bought or sold.
- Selling is only possible by manually recomputing the remaining quantity + avg cost and `PATCH`ing
  it — error prone, and avg cost silently distorts on partial sells.
- No realized P&L is ever captured (only unrealized P&L from current price vs. avg cost).
- CSV import is the de-facto way to load a position, but it cannot model incremental trades.

We need a **transaction/ledger** concept so that buys and sells are recorded as immutable events
and holdings (quantity, avg cost) plus realized P&L are derived from them.

---

## 2. Current State Analysis

### What already exists
| Capability | Where | Notes |
|---|---|---|
| Portfolio CRUD (multi-portfolio, default flag) | `PortfolioManagementController`, `PortfolioService` | ✅ Dhan/Ammu Groww/Groww Siba already modeled as separate portfolios |
| Holding set / update / remove | `addHolding`, `updateHolding`, `removeHolding*` | ✅ But **overwrite semantics**, no transaction event |
| Holding P&L (unrealized) | `PortfolioService.computeAndCache` | ✅ `investment`, `currentValue`, `pnl`, `pnlPercent` computed from `avgPrice` × `quantity` vs latest `DailyPrice` |
| Snapshot history | `PortfolioSnapshot` / `PortfolioSnapshotService` | ✅ Daily point-in-time copies of holdings |
| Stock sync back | `syncStockFromHolding` | ✅ Writes holding qty/avgPrice onto `Stock` entity |
| Frontend portfolio UI | `static/js/portfolio.js` (+ `portfolio` HTML page) | ✅ Portfolio selector, holdings table, modals |
| API wrappers | `static/js/api.js` | ✅ `addHolding`, `updateHolding`, `removeHolding` exist; no transaction wrappers |

### The exact gap
- **No `Transaction` entity, repository, service, controller, DTO, or DB table exists.**
  (Verified: no file matches `*Transaction*`; `PortfolioHolding` has only `quantity` + `avgPrice`.)
- Holdings are **state records**, not derived from a ledger. There is no notion of BUY vs SELL,
  no trade date, no fees, no realized P&L field.
- `avgPrice` semantics today: a simple overwrite. A correct "buy more" must **blend** the old and
  new cost; a "sell" must **retain** avg cost (or apply FIFO) and book realized P&L — none of which
  is implemented.

---

## 3. Proposed Feature Scope

### 3.1 New `Transaction` entity (ledger)
A `portfolio_transactions` table, one row per trade:

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, IDENTITY) | |
| `portfolio` | @ManyToOne → Portfolio | not null |
| `stock` | @ManyToOne → Stock | not null |
| `type` | enum BUY / SELL | not null |
| `quantity` | Integer (positive) | not null |
| `price` | BigDecimal(10,2) | execution price per share, not null |
| `fees` | BigDecimal(10,2), optional | default 0; brokerage/charges |
| `transactionDate` | LocalDate | not null, default today |
| `notes` | String(500), optional | |
| `realizedPnl` | BigDecimal(12,2), null for BUY | computed on SELL |
| `createdAt` / `updatedAt` | timestamps | |

Unique constraint / index on `(portfolio_id, stock_id, transaction_date, id)` for ordered ledger reads.

### 3.2 Endpoints (new, under `/api/portfolios/{id}/transactions`)
- `POST /transactions` — record a BUY or SELL.
  - **BUY:** updates the `PortfolioHolding` for (portfolio, stock):
    - `quantity += txnQty`
    - `avgPrice = (oldQty*oldAvg + (txnQty*price + fees)) / (oldQty + txnQty)` (weighted blend; fees added to cost basis)
    - Creates holding if it does not exist.
  - **SELL:** requires `txnQty <= currentQty` (else 400).
    - `realizedPnl = (price - avgPrice) * txnQty - fees`
    - `quantity -= txnQty`
    - `avgPrice` retained (avg-cost method; see open question on FIFO).
    - If `quantity` reaches 0 → holding may be retained at qty 0 / null avgPrice, or removed (open question).
  - After write: re-run `syncStockFromHolding` + `snapshotService.saveOrUpdate` to keep existing consumers consistent.
- `GET /transactions?stockId=&from=&to=` — list ledger for a portfolio (default ordered by date).
- `GET /transactions/{txnId}` — single.
- `DELETE /transactions/{txnId}` — (optional / phase-2) reverse the trade and restore holding state. **Recommended out of scope for v1** unless user wants edit/delete.
- `GET /transactions/realized-pnl?from=&to=` — (optional) sum of `realizedPnl` for reporting.

### 3.3 Frontend
- Add **Buy** / **Sell** buttons per row in the holdings table in `portfolio.js` (reusing the existing
  modal pattern), plus a **Transaction history** view/tab.
- New `api.js` wrappers: `recordTransaction`, `getTransactions`, `getRealizedPnl`.
- IIFE module pattern + `?v=` cache-busting per existing conventions.

### 3.4 Holdings DTO extension
- Add `realizedPnl` (rolling, per holding) and expose cumulative realized P&L per portfolio where useful.

---

## 4. Assumptions
1. **CSV import stays as-is** — remains a bulk position loader; it does *not* create transaction rows (or, if it should, that is a separate decision — see open questions).
2. **Multi-portfolio** is first-class: every transaction is scoped to one of Dhan / Ammu Groww / Groww Siba; the ledger never crosses portfolios.
3. **Default-portfolio / `Stock` sync behavior is preserved** — posting a transaction still updates the `Stock` entity and snapshots as today.
4. **Hibernate `ddl-auto=update`** creates the new table automatically; no manual migration needed.
5. **precision(10,2) / scale(2)** currency fields follow existing `PortfolioHolding` conventions; realized P&L uses (12,2).
6. Transactions are recorded in **one currency** (INR, assumed) — no FX conversion.
7. `transactionDate` defaults to today if omitted; back-dating is allowed (for catching up historical trades).
8. **Avg-cost method** is the default for SELL (retain avg cost, book realized P&L) unless FIFO is requested.

---

## 5. Decisions (user-confirmed)
- Cost-basis method: **Avg-cost** (BUY blends running avg; SELL retains avg, books realized P&L vs avg).
- Transaction ledger: **Yes** — immutable `portfolio_transactions` table persisted.
- Sell-to-zero: **Delete the holding** row when quantity reaches 0.
- Edit/delete trades: **Append-only + delete** (delete allowed, no in-place edit; corrections via new correcting trade).
- Realized P&L reporting: **Capture per sell only** in v1 (no summary report view yet).
- CSV import: stays as-is (no transaction generation) per existing assumption.
- Fees: include optional `fees` field.

---

## 6. Out of Scope (v1)
- Stock **splits / dividends / bonus** corporate-action adjustments to the ledger.
- FIFO lot tracking (unless chosen in Q1).
- Multi-currency / FX.
- Tax computation (e.g., LTCG/STCG India) — realized P&L is raw only.
- Automated import from broker statements (Zerodha/Dhan/Groww CSV).
- Transaction edit/delete + reversal (unless chosen in Q4).
- Alerts/notifications on trade execution.

---

## 7. Success Criteria
- A BUY transaction increments holding quantity and correctly **blends** avg cost.
- A SELL transaction decrements quantity, retains (or FIFO-applies) avg cost, and records **realized P&L**.
- Selling more than held is rejected (400).
- Full ledger is queryable per portfolio/stock/date-range.
- Existing portfolio views, snapshots, and P&L remain consistent after transactions.
- At least unit + integration tests covering buy-blend, partial sell, full sell, over-sell rejection, and realized P&L.

---

## 8. Implementation Plan (Plan Phase)

### 8.1 Database / Entity
- New entity `src/main/java/org/example/entity/PortfolioTransaction.java`, `@Table(name = "portfolio_transactions")`:
  - `Long id` (IDENTITY)
  - `@ManyToOne Portfolio portfolio` (nullable=false) — FK directly, NOT to holding (survives holding deletion)
  - `@ManyToOne Stock stock` (nullable=false)
  - `@Enumerated(STRING) TransactionType type` (BUY/SELL)
  - `Integer quantity` (positive)
  - `BigDecimal price` (precision 12, scale 2)
  - `BigDecimal fees` (10,2, default 0)
  - `BigDecimal realizedPnl` (14,2, nullable — SELL only)
  - `LocalDate transactionDate` (default today, back-dating allowed)
  - `String notes` (length 500, optional)
  - `@CreationTimestamp createdAt`, `@UpdateTimestamp updatedAt`
- Enum `TransactionType { BUY, SELL }` (inner or separate file `entity/TransactionType.java`).
- ddl-auto=update auto-creates the table. No migration file needed. `transaction` is not a MySQL reserved word — safe.

### 8.2 Repository
- New `src/main/java/org/example/repository/PortfolioTransactionRepository.java` (`JpaRepository<PortfolioTransaction, Long>`):
  - `List<PortfolioTransaction> findByPortfolioIdOrderByTransactionDateAscIdAsc(Long portfolioId)`
  - `List<PortfolioTransaction> findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(Long, Long)`
  - `long countByPortfolioId(Long)`
  - `void deleteByPortfolioId(Long)` — MUST be inside @Transactional (derived deleteBy* rule)
  - `void deleteByStockId(Long)` — @Transactional

### 8.3 DTOs (org.example.dto)
- `CreateTransactionRequest`: `Long stockId; TransactionType type; Integer quantity; BigDecimal price; BigDecimal fees; LocalDate transactionDate; String notes`. (Validation: quantity>0, price>=0, type not null, stockId not null.)
- `TransactionDTO`: all entity fields + `String stockSymbol; String stockName; Long portfolioId`.

### 8.4 Service
- New `src/main/java/org/example/service/PortfolioTransactionService.java` (`@Service @Transactional`).
- Inject `PortfolioTransactionRepository`, `PortfolioService` (reuse `addHolding`/`updateHolding`/`removeHolding` + `syncStockFromHolding` via PortfolioService), `PortfolioRepository`, `StockRepository`, `PortfolioHoldingRepository`, `PortfolioSnapshotService`.
- `recordBuy(portfolioId, stockId, qty, price, fees, date, notes)`:
  - Load Portfolio (throw ResourceNotFound if missing), Stock (throw if missing).
  - If holding exists: `newQty = qty0 + qty`; `newAvg = (avg0*qty0 + (price*qty + fees)) / newQty` using BigDecimal scale 4 math, RoundingMode.HALF_UP, stored to scale 2. Call `portfolioService.updateHolding(...)`.
  - Else: `portfolioService.addHolding(portfolioId, stockId, qty, price + fees/qty)`.
  - Persist `PortfolioTransaction(BUY, realizedPnl=null)`.
- `recordSell(portfolioId, stockId, qty, price, fees, date, notes)`:
  - Load holding (throw if missing). If `qty > holding.quantity` → throw IllegalArgumentException (over-sell, 400).
  - `realizedPnl = (price - avgPrice)*qty - fees` (scale 2, HALF_UP).
  - `newQty = holding.quantity - qty`.
  - If `newQty == 0` → `portfolioService.removeHolding(portfolioId, holdingId)` (deletes holding + snapshot). Else `portfolioService.updateHolding(...)` with newQty (avgPrice unchanged).
  - Persist `PortfolioTransaction(SELL, realizedPnl)`.
- `deleteTransaction(id)`: load tx; recompute the holding from the REMAINING ledger (authoritative) — i.e. replay remaining BUY/SELL rows to derive qty + blended avg, then upsert holding (or delete if net 0). Retain append-only semantics (delete row, recompute state). Document this.
- `getTransactions(portfolioId)`, `getTransactions(portfolioId, stockId)` → `List<TransactionDTO>` via mapper.
- Keep existing `PortfolioService.addHolding/updateHolding/removeHolding` intact (backward compat; CSV import uses addHolding).

### 8.5 Controller
- Extend `src/main/java/org/example/controller/PortfolioManagementController.java` (no new controller):
  - `POST /api/portfolios/{id}/transactions` → `ApiResponse<TransactionDTO>` (body CreateTransactionRequest)
  - `GET /api/portfolios/{id}/transactions` → `ApiResponse<List<TransactionDTO>>`
  - `GET /api/portfolios/{id}/transactions?stockId=` → filtered (use @RequestParam(required=false))
  - `DELETE /api/portfolios/{id}/transactions/{txId}` → `ApiResponse<Void>`
- All responses wrapped in `ApiResponse.success(...)`.

### 8.6 Frontend
- `src/main/resources/static/js/api.js` (~lines 239-315 area, after existing portfolio wrappers): add
  - `recordTransaction(portfolioId, payload)` → POST `/portfolios/{id}/transactions`
  - `getTransactions(portfolioId, stockId?)` → GET
  - `deleteTransaction(portfolioId, txId)` → DELETE
  (IIFE-compatible function declarations; reuse `apiCall` helper.)
- Portfolio UI lives in `index.html` (Dashboard, holdings table `#holdingsTableBody`) and `stocks.html`. Add **Buy** / **Sell** buttons per holding row + a **Transaction History** panel. Reuse existing `portfolio.js` modal flow. NOTE: `portfolio.js` currently uses global functions (not IIFE) — follow its existing pattern for v1; do not rewrite into IIFE unless explicitly requested. Add `?v=` cache-bust query param to the script tag when editing.
- No new HTML page / nav registration needed (portfolio is reachable via Dashboard & Stock Management). If a dedicated page is later desired, register in `src/main/resources/static/js/navigation.js` NAV_ITEMS/DROPDOWN_ITEMS.

### 8.7 Validation & Errors
- Reject over-sell (> current qty) → IllegalArgumentException (controller maps to 400).
- Reject missing portfolio/stock, negative qty/price, null type.
- Fees optional, default 0.

### 8.8 Backward Compatibility
- Existing holding CRUD, aggregate holding, snapshots, Stock sync, and CSV import remain unchanged.
- Transactions layer on top; first BUY via transaction creates-or-updates the holding.

### 8.9 Tests
- New `PortfolioTransactionServiceTest` (unit): avg-cost blend precision, over-sell rejection (400 path), full-sell deletes holding + snapshot, partial sell retains avg + captures realizedPnl, deleteTransaction recomputes holding from remaining ledger.
- `PortfolioManagementController` integration test: POST/GET/DELETE endpoints, ApiResponse shape.
- Verify existing `PortfolioServiceTest` still passes (no signature changes).
- Optional Playwright E2E: Buy/Sell modal + history render on Dashboard.

### 8.10 Risks & Assumptions
- Avg-cost precision: use scale 4 math, HALF_UP; store scale 2 (matches existing avgPrice column). Consider widening avgPrice to (12,4) via migrations file if truncation observed — out of scope unless needed.
- Over-sell: reject (never allow negative holdings).
- Transaction references portfolio+stock (not holding) → safe across holding deletion.
- Concurrency: default isolation adequate for single-user buyer app; no pessimistic lock in v1.
- `deleteTransaction` recompute assumes ledger is authoritative.

### 8.11 Implementation Order (vertical slices)
1. `PortfolioTransaction` entity + `TransactionType` enum.
2. `PortfolioTransactionRepository`.
3. DTOs (`CreateTransactionRequest`, `TransactionDTO`).
4. `PortfolioTransactionService` (recordBuy, recordSell, getTransactions; deleteTransaction recompute).
5. Controller endpoints + ApiResponse.
6. `api.js` wrappers.
7. `portfolio.js` Buy/Sell buttons + Transaction History panel (extend index.html holdings table).
8. Tests.
9. Compile + `mvn test` (run `./run-tests.sh` or `mvn test`).
