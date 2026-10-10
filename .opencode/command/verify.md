---
description: Full verify loop — compile gate, full test suite, self-review, evidence report
agent: build
---

Run the full verification loop for the current changes. Do not claim done without pasted evidence.

$ARGUMENTS

## 1. Scope

Run `git status --short` and `git diff --stat` to list what changed in this change-set.

## 2. Compile gate (Java)

`mvn -q test-compile`

- FAIL → fix the compile errors → re-run until PASS. Do not continue to tests with a broken compile.

## 3. Test suite

`mvn test` (takes ~5 min).

Known baseline on a clean tree: 965 tests, 2 skipped, 13 errors — all in `PortfolioChartControllerIntegrationTest` (`Column 'portfolio_id' cannot be null`). These are pre-existing and must not be fixed in this loop.

- Only NEW failures (not in the baseline above) block the change.
- New failures → diagnose → fix the code (not the test, unless the test itself is wrong) → re-run `mvn test`. Max 3 fix rounds.
- Still failing after 3 rounds → stop, and report the blocker with the exact error output instead of claiming done.

## 4. Self-review

Read `git diff` as a skeptical senior reviewer and check:

- Bugs: edge cases, null/empty input, off-by-one, broken control flow, unhandled errors, race conditions
- Security: injection, path traversal, auth/authz gaps, secrets or keys added
- Breaking changes: API contract, response shape, DB schema, config format
- Leftovers: debug prints, commented-out code, TODO/FIXME added by you
- Tests: do new code paths have coverage? do existing tests still match intended behavior?

Findings table: `| Severity | File:line | Issue |`. Write `CLEAN` only if you genuinely found nothing.

## 5. Report

| Check  | Status    | Evidence                                     |
| ------ | --------- | -------------------------------------------- |
| compile | PASS/FAIL | last line of `mvn test-compile` output       |
| tests  | PASS/BLOCKED | counts, e.g. "965 tests, only 13 known baseline errors" |
| review | CLEAN/ISSUES | finding count or `CLEAN`                   |
