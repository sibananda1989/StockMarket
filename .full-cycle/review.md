# Code Review — fmtPct ReferenceError Fix

## Scope
Fix for `ReferenceError: Can't find variable: fmtPct` at portfolio-transactions.js:278 in renderTable → applyFiltersAndRender → loadAll chain.

## Changes Reviewed
- `src/main/resources/static/portfolio-transactions.html:279-281` — bump `?v=20260822` → `?v=20260823` on 3 script tags
- `src/main/resources/static/js/portfolio-transactions.js` — no logic change; `fmtPct` at line 660 remains, `node --check` passes, 3 occurrences confirmed
- `target/classes/static/` — synced via `mvn -q compile`

## Verification
- `node --check src/main/resources/static/js/portfolio-transactions.js` — PASSED
- `mvn -q compile -DskipTests` — PASSED (exit 0)
- `grep -c fmtPct` src = 3, target = 3 (definition + 2 usages)
- HTML version bump confirmed via grep
- No new JS logic introduced — avoids regression risk

## Findings
- No CRITICAL/HIGH issues. The fix correctly addresses stale-cache root cause without changing runtime semantics.
- Cache-bust is minimal and isolated to this page; no cross-page impact.
- Alternative mitigation (moving helpers earlier / adding guard) considered but unnecessary — function declarations are hoisted within IIFE.

## Outstanding Risks
- None for this fix. Previous review found 6 unrelated bugs (cross-portfolio blending, CSV column shift etc.) — out of scope for this hotfix, tracked separately.

REVIEW_VERDICT: CLEAN
