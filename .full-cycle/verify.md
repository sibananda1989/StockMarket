# Verification — fmtPct Fix

## Build
- `mvn -q compile -DskipTests` — PASSED (exit 0)
- `node --check src/main/resources/static/js/portfolio-transactions.js` — PASSED
- `node --check target/classes/static/js/portfolio-transactions.js` — PASSED

## Artifact Checks
- `src/main/resources/static/portfolio-transactions.html` — 3 script tags now `?v=20260823` (verified via grep)
- `target/classes/static/portfolio-transactions.html` — synced, same `?v=20260823`
- `src/main/resources/static/js/portfolio-transactions.js` — `fmtPct` defined at line 660: `function fmtPct(val) { if (val == null || isNaN(val)) return '--'; ... }`
- `grep -c fmtPct` src=3 target=3 (definition + 2 call sites at lines 280, 285)
- JS file size: 26KB, HTML 15KB — present

## Functional Verification (static)
- renderTable uses `fmtPct(l.openPlPct)` and `fmtPct(l.realizedPlPct)` within same IIFE closure — lexically valid
- Helpers `fmtPct`, `fmtPrice`, `fmtPriceWithSign`, `escHtml` all defined before return statement — accessible at runtime
- `loadAll` try/catch preserves error handling; no regression

## Not Automated
- Live browser hard-reload not performed in CI (requires running Spring Boot + DB). Manual step: `curl -s http://localhost:8080/js/portfolio-transactions.js?v=20260823 | grep fmtPct` should return 3; hard-reload page should show no console errors and P&L % cells populated.

## Result
Implementation complete, no regressions in build.

VERIFY_RESULT: PASS
