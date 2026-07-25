# Volume Strategy Fixes — Specification Document

## 1. Objective

Fix **7 identified issues** in the `VolumeStrategy` implementation and its supporting infrastructure to improve calculation accuracy, signal quality, configurability, and test coverage.

The fixes are **backward-compatible by default** — existing behavior is preserved unless explicitly changed in this spec, and all hardcoded constants receive safe defaults so current production behavior remains unchanged.

---

## 2. Requirements

### 2.1 MUST Have (blocking)

| ID | Requirement |
|----|-------------|
| R1 | Align `StrategyConditionService.calculateVolumeRatio()` with `VolumeRatioCalculator` — exclude the latest day from the 20-day moving average. |
| R2 | Make `VOLUME_SPIKE_FACTOR` a configurable Spring Boot property (`strategy.volume.spike-factor`) with a default of `1.5`. |
| R3 | Read `VOLUME_SPIKE_FACTOR` from the property in both `VolumeStrategy.java` and `StrategyConditionService.buildVolumeConditions()`. |
| R4 | Add OBV trend check as an additional signal layer inside `VolumeStrategy.evaluate()`. |
| R5 | Add VOLUME to the ADX regime filter in `StrategySignalAggregator.applyAdxRegimeFilter()`. |
| R6 | Replace the linear confidence formula in `VolumeStrategy` with a logarithmic curve that gives meaningful confidence differentiation for spikes above 2.15x. |
| R7 | Replace the `close > open` accumulation/distribution check with a close-position-in-range check `(close - low) / (high - low)`. |
| R8 | Add `VolumeRatioCalculatorTest` with ≥ 8 test cases covering normal, boundary, and error conditions. |
| R9 | Add/update `VolumeStrategyTest` to cover OBV integration, ADX adjustment, close-position logic, and configurable spike factor. |
| R10 | Add aggregator integration tests for the VOLUME strategy within `StrategySignalAggregatorTest`. |
| R11 | Add condition override tests for VOLUME conditions in the existing strategy condition test suite. |
| R12 | All code compiles (`mvn compile -q`). |
| R13 | All existing tests continue to pass (`mvn test`). |

### 2.2 SHOULD Have (desirable)

| ID | Requirement |
|----|-------------|
| S1 | Add a unit test for `StrategyConditionService.calculateVolumeRatio()` that explicitly verifies the latest day is excluded from the average. |
| S2 | Update `VolumeStrategyTest` confidence assertions to reflect the new logarithmic curve values. |
| S3 | Document the new `strategy.volume.spike-factor` property in `docs/API_SPEC.md` or a properties reference section. |
| S4 | Log the resolved spike factor at startup for observability (`log.info("Volume spike factor: {}", factor)`). |

### 2.3 MUST NOT

| ID | Requirement |
|----|-------------|
| N1 | Do NOT change the DB schema or add new columns. |
| N2 | Do NOT change the `StrategyConditionGroup` entity or DTO structure. |
| N3 | Do NOT introduce breaking changes to the `StrategyResult` record. |
| N4 | Do NOT modify frontend files for this fix (the threshold is backend-configurable via properties; the strategy manager UI reads from the DB seed, which will use the same default). |
| N5 | Do NOT change ADX thresholds for other strategies (MA_CROSSOVER, RSI, BOLLINGER). |

---

## 3. Architecture Notes

### 3.1 Fix 1 — Calculation Inconsistency (`calculateVolumeRatio`)

**Current state:**
- `VolumeRatioCalculator` (lines 39–57) iterates from `prices.size() - LOOKBACK_DAYS - 1` to `prices.size() - 2`, correctly **excluding** the latest day.
- `StrategyConditionService.calculateVolumeRatio()` (lines 449–475) calls `dailyPriceRepository.findLastNDays(stockId, 20)`, sums **all** returned days (including the latest), and divides by `count`. This includes today in the average.

**Fix:**
- Modify `StrategyConditionService.calculateVolumeRatio()` to skip the most recent day when computing the sum, matching `VolumeRatioCalculator`.
- Use `last20.subList(0, last20.size() - 1)` or an equivalent index-aware loop.
- Keep the same guard clauses (null checks, count > 0, avg > 0).
- This is a **localized change** within `StrategyConditionService` only. No interface changes.

**Pattern match:** Follows the same exclusion pattern as `VolumeRatioCalculator` lines 46–50.

### 3.2 Fix 2 — Centralize Spike Threshold

**Current state:**
- `VolumeStrategy.java` line 31: `private static final double VOLUME_SPIKE_FACTOR = 1.5;`
- `StrategyConditionService.java` line 166/168/170: hardcoded `"1.5"` in `buildVolumeConditions()`.

**Fix:**
1. Add `strategy.volume.spike-factor=1.5` to `application.properties` (with a comment).
2. In `VolumeStrategy`, inject the value via `@Value("${strategy.volume.spike-factor:1.5}")` in the constructor or as a field. Because `VolumeStrategy` extends `TradingStrategy` (which uses `@RequiredArgsConstructor`), we add a constructor parameter and pass it to `super()`, or inject it into a package-private field. The cleanest approach is to add it as a constructor parameter and store it in a field, keeping the class immutable.
3. In `StrategyConditionService`, similarly inject the property via `@Value("${strategy.volume.spike-factor:1.5}")` and use it in `buildVolumeConditions()` via `BigDecimal.valueOf(factor)`.
4. Default value `1.5` preserves existing behavior.

**Pattern match:** Follows the same `@Value` injection pattern used by `StrategyConfigService` (e.g., `@Value("${strategy.volume.priority:5}")`).

### 3.3 Fix 3 — Enhance VolumeStrategy with OBV

**Current state:**
- `ObvCalculator` computes OBV and stores it as `IndicatorType.OBV`.
- DB conditions `obv_up` / `obv_down` exist but `VolumeStrategy.evaluate()` never resolves OBV from the indicators list.
- `StrategyConditionService.evaluateOperator()` already handles `obv_up`/`obv_down` for condition stats, but the strategy itself does not use OBV for signal generation.

**Fix:**
1. In `VolumeStrategy.evaluate()`, after resolving `VOLUME_RATIO`, also resolve `OBV` using the existing `resolveIndicator(indicators, IndicatorType.OBV)`.
2. If OBV is present, evaluate the 5-day OBV trend (today vs. 5 days ago). This logic already exists in `StrategyConditionService.evaluateObvTrend()` but is private. Since `VolumeStrategy` only has the `indicators` list (not repository access), we cannot reuse that private method directly.
3. **Approach A (preferred):** Add a package-private or public static helper in `StrategyConditionService` for OBV trend evaluation given an `indicators` list, or add a simple inline check in `VolumeStrategy`:
   - Resolve current OBV.
   - Resolve OBV from 5 days ago using `indicators` stream (filter by `IndicatorType.OBV`, sort by date descending, skip 4, find first).
   - Compare: if current > 5-day-old → OBV trending up.
4. Apply OBV as a **secondary confirmation layer**:
   - If volume ratio ≥ spike factor **and** OBV is trending up → BUY with boosted confidence.
   - If volume ratio ≥ spike factor **and** OBV is trending down → SELL with boosted confidence.
   - If volume ratio ≥ spike factor but OBV is neutral/absent → keep existing logic.
   - If OBV trend contradicts price direction, reduce confidence by 15% (divergence warning).
5. Include OBV status in the `reason` string for transparency.

**Pattern match:** Follows the same indicator-resolution pattern as `RsiStrategy.resolveIndicator()` and `MacdStrategy.resolvePreviousIndicator()`.

### 3.4 Fix 4 — Add ADX Regime Adjustment

**Current state:**
- `StrategySignalAggregator.applyAdxRegimeFilter()` already classifies market regime (TRENDING, RANGING, TRANSITIONAL) and adjusts confidence for MA_CROSSOVER, RSI, and BOLLINGER.
- VOLUME is absent from the switch.

**Fix:**
- Add a `case` for `"VOLUME"` inside the `TRENDING` block:
  - **TRENDING:** Volume signals are more reliable when volume confirms the trend. Boost confidence by `REGIME_VOLUME_TRENDING_BOOST` (proposed: `1.20`).
  - **RANGING:** Volume spikes in ranging markets are often noise. Reduce confidence by `REGIME_VOLUME_RANGING_REDUCE` (proposed: `0.85`).
  - **TRANSITIONAL:** Slight boost (`1.05`) to give volume the benefit of the doubt.
- Append the regime reason suffix (e.g., `" | ADX trending: volume confirmation boosted"`).
- Define new constants: `REGIME_VOLUME_TRENDING_BOOST = 1.20`, `REGIME_VOLUME_RANGING_REDUCE = 0.85`.

**Pattern match:** Mirrors the existing `TRENDING`/`RANGING`/`TRANSITIONAL` branches for MA_CROSSOVER and RSI.

### 3.5 Fix 5 — Improve Confidence Scaling

**Current state:**
- Formula: `confidence = Math.min(1.0, (volumeRatio / VOLUME_SPIKE_FACTOR) * 0.7)`
- At ratio = 1.5: `(1.5/1.5)*0.7 = 0.7`
- At ratio = 2.15: `(2.15/1.5)*0.7 ≈ 1.0` (hits cap)
- At ratio = 5.0: `(5.0/1.5)*0.7 ≈ 2.33 → min(1.0, 2.33) = 1.0`
- Result: 2.15x and 5.0x spikes produce identical confidence, losing signal differentiation.

**Fix:**
- Replace linear scaling with a logarithmic curve:
  ```
  confidence = Math.min(1.0, 0.7 * (Math.log(volumeRatio) / Math.log(VOLUME_SPIKE_FACTOR)))
  ```
- At ratio = 1.5: `0.7 * (log(1.5) / log(1.5)) = 0.7` (unchanged)
- At ratio = 2.15: `0.7 * (log(2.15) / log(1.5)) ≈ 0.7 * 1.22 ≈ 0.85`
- At ratio = 5.0: `0.7 * (log(5.0) / log(1.5)) ≈ 0.7 * 1.80 ≈ 1.26 → 1.0` (still caps at 1.0, but only at much higher ratios)
- At ratio = 10.0: `0.7 * (log(10) / log(1.5)) ≈ 0.7 * 2.37 ≈ 1.66 → 1.0`
- This preserves the 0.7 baseline at threshold and provides smooth growth up to the 1.0 cap.

**Why log:** Volume spikes are naturally exponential in distribution (few events are huge). A log curve compresses extreme values while preserving relative ordering, giving the aggregator more signal resolution.

**Pattern match:** Similar curvature to `MacdStrategy`'s gap-based confidence (`Math.min(absGap * CONFIDENCE_SCALE, MAX_CONFIDENCE)`), but using log to avoid premature capping.

### 3.6 Fix 6 — Use Close Position in Range

**Current state:**
- Accumulation/distribution is detected by `latest.getClosingPrice().compareTo(latest.getOpeningPrice()) > 0`.
- A close at `open + 0.01` with a huge range counts the same as a close at the high of the day.

**Fix:**
- Replace the binary open/close comparison with a continuous close-position metric:
  ```
  double closePosition = (close - low) / (high - low)
  ```
- Interpretation:
  - `closePosition >= 0.7` → strong accumulation (bought near high)
  - `closePosition >= 0.5` → mild accumulation
  - `closePosition <= 0.3` → strong distribution (sold near low)
  - `closePosition <= 0.5` → mild distribution
  - `0.3 < closePosition < 0.7` → neutral (do not change signal)
- Apply this only when the volume ratio ≥ spike factor.
- If `high.equals(low)` (zero-range day), treat as neutral (no accumulation/distribution signal).
- Use `closePosition` to modulate confidence, not just signal direction:
  - Strong close position (±0.7) → add `+0.05` confidence.
  - Weak close position (0.3–0.7) → no change.
- Update the `reason` string to include the close position percentage.

**Pattern match:** Matches the band-position calculation style used in `StrategyConditionService.calculateBandPosition()` (also a 0–100 normalized position).

### 3.7 Fix 7 — Add Missing Tests

**Test additions:**

| Test File | Test Cases |
|-----------|-----------|
| `VolumeRatioCalculatorTest` (new) | 1. Exact 21-day minimum, 2. Ratio = 1.0 (normal), 3. Ratio = 1.5 (spike), 4. Ratio > 2.0 (strong spike), 5. Zero volume returns 0, 6. Null volumes treated as 0, 7. Latest day excluded from average, 8. `getSupportedType()` returns OBV |
| `VolumeStrategyTest` (update) | 1. OBV up + spike → BUY, 2. OBV down + spike → SELL, 3. OBV divergence reduces confidence, 4. ADX trending boosts confidence, 5. ADX ranging reduces confidence, 6. Configurable spike factor injected correctly, 7. Close position in strong accumulation, 8. Close position in strong distribution, 9. Zero-range day (high==low) → neutral, 10. Logarithmic confidence at ratio 1.5, 2.15, 5.0 |
| `StrategySignalAggregatorTest` (add volume tests) | 1. Volume strategy in TRENDING regime gets confidence boost, 2. Volume strategy in RANGING regime gets confidence reduction, 3. Volume strategy with OBV confirmation in breakdown, 4. Volume strategy contributes correctly to score |
| `StrategyConditionServiceTest` (new or extend) | 1. `calculateVolumeRatio` excludes latest day, 2. Spike factor property is read correctly in `buildVolumeConditions` |

---

## 4. Edge Cases

### 4.1 Error States

| Edge Case | Handling |
|-----------|----------|
| **Zero or null latest volume** | `VolumeRatioCalculator` returns `BigDecimal.ZERO`. `VolumeStrategy.evaluate()` should treat ratio < spike factor as HOLD. |
| **Zero or null average volume** | `VolumeRatioCalculator` returns `BigDecimal.ZERO`. Same HOLD fallback. |
| **Insufficient price data (< 21 days)** | `VolumeRatioCalculator` throws `IllegalArgumentException`. `VolumeStrategy.evaluate()` returns `insufficientData()` before invoking the calculator. |
| **Null OBV indicator** | If OBV is absent from the indicators list, skip OBV logic and fall back to existing volume-only logic. Do not fail. |
| **Null high/low/open/close** | `DailyPrice` getters return `BigDecimal`; use `compareTo` safely with null checks. If any of high/low is null, return HOLD with reason "Incomplete price data". |
| **High == Low (zero range)** | `closePosition` denominator is zero. Detect with `high.compareTo(low) == 0` and treat as neutral (0.5). |
| **ADX absent** | `resolveAdxFromIndicators()` returns `Optional.empty()`. `applyAdxRegimeFilter()` returns result unchanged (existing behavior). |
| **Volume spike factor = 0 or negative** | `@Value` default is `1.5`. If a user sets `strategy.volume.spike-factor=0`, the property is technically invalid. Add a `@PostConstruct` validation in `StrategyConditionService` (or the strategy) that throws `IllegalArgumentException` on startup if factor <= 0. |
| **Volume spike factor > 10** | Log a warning but allow it (user-configurable). |

### 4.2 Boundary Conditions

| Boundary | Expected Behavior |
|----------|-------------------|
| Volume ratio = exactly spike factor | Triggers spike logic; confidence = 0.7 * log(1.5)/log(1.5) = 0.7 |
| Volume ratio = 2.15 (old cap point) | Old confidence = 1.0; new confidence ≈ 0.85 |
| Volume ratio = 1.49 (just below threshold) | HOLD, confidence = 0.3 |
| Close position = 0.7 exactly | Treated as strong accumulation (`>= 0.7`) |
| Close position = 0.3 exactly | Treated as strong distribution (`<= 0.3`) |
| ADX = 20.0 (transitional lower bound) | TRANSITIONAL regime; volume gets slight 1.05 boost |
| ADX = 25.0 (trending lower bound) | TRENDING regime; volume gets 1.20 boost |
| ADX = 19.9 (ranging upper bound) | RANGING regime; volume gets 0.85 reduction |

---

## 5. Dependencies

### 5.1 Files to Modify Together

| File | Why It Must Change With Others |
|------|-------------------------------|
| `VolumeStrategy.java` | Needs injected spike factor, OBV resolution, close-position logic, log confidence curve. |
| `VolumeRatioCalculator.java` | **No change required** for Fix 1 (it is already correct). It serves as the canonical reference. |
| `StrategyConditionService.java` | Needs injected spike factor, fixed average calculation in `calculateVolumeRatio`, and updated `buildVolumeConditions()`. |
| `StrategySignalAggregator.java` | Needs VOLUME case added to `applyAdxRegimeFilter()`. |
| `application.properties` | New property `strategy.volume.spike-factor`. |
| `VolumeRatioCalculatorTest.java` (new) | Validates canonical calculator behavior. |
| `VolumeStrategyTest.java` (update) | Validates all new behaviors. |
| `StrategySignalAggregatorTest.java` (add) | Validates ADX regime adjustment for volume. |
| `StrategyConditionServiceTest` (add/extend) | Validates `calculateVolumeRatio` exclusion and property injection. |

### 5.2 Dependency Graph

```
application.properties (new property)
    ├── VolumeStrategy (inject @Value)
    └── StrategyConditionService (inject @Value)

VolumeRatioCalculator (unchanged — reference implementation)
    └── StrategyConditionService.calculateVolumeRatio() (align with it)

ObvCalculator (unchanged)
    └── VolumeStrategy.evaluate() (resolve OBV indicator)

StrategySignalAggregator.applyAdxRegimeFilter() (extend with VOLUME)
    └── Depends on MarketRegime enum (unchanged)
    └── Depends on resolveAdxFromIndicators() (unchanged)
```

### 5.3 Backward Compatibility

- Default `strategy.volume.spike-factor=1.5` ensures `buildVolumeConditions()` seeds the same `1.5` threshold currently in the DB.
- Existing DB rows seeded with `1.5` remain valid; new deployments will overwrite them only if `resetToDefaults()` is called.
- The logarithmic confidence curve produces values **≤** the old linear values at the same ratio (except at the cap), so existing high-confidence signals are not artificially inflated.
- Close-position logic only affects the **BUY/SELL vs HOLD** decision when volume ratio is already above the spike factor. Below the spike factor, the result remains HOLD regardless.

---

## 6. Open Questions

| ID | Question | Decision Needed |
|----|----------|----------------|
| O1 | Should `strategy.volume.spike-factor` be a **double** or a **BigDecimal** property? | Proposed: `double` via `@Value`, matching other numeric strategy properties. |
| O2 | For OBV trend detection in `VolumeStrategy`, should we reuse `StrategyConditionService.evaluateObvTrend()` logic (5-day window) or make it configurable? | Proposed: Hardcode 5-day window to match existing DB condition behavior, with a comment that it mirrors `evaluateObvTrend()`. |
| O3 | When volume ratio is above spike factor but OBV is **absent**, should we still emit BUY/SELL, or downgrade to HOLD? | Proposed: Still emit BUY/SELL at reduced confidence (0.6 instead of 0.7 base) to avoid losing volume-only signals. |
| O4 | Should the close-position thresholds (0.3, 0.5, 0.7) be configurable properties? | Proposed: No — keep them as constants in `VolumeStrategy` for simplicity. They can be extracted later if tuning is needed. |
| O5 | Should the ADX volume boost factors (`1.20`, `0.85`, `1.05`) be centralized constants or inline literals? | Proposed: Add private constants in `StrategySignalAggregator` (`REGIME_VOLUME_TRENDING_BOOST`, etc.) for consistency with existing regime constants. |
| O6 | Should we add a migration note for existing databases where `buildVolumeConditions()` seeded `1.5`? | Proposed: Yes — add a startup log message in `StrategyConditionService.init()` noting the resolved spike factor, so operators can verify. |

---

## 7. Testing Strategy

### 7.1 Unit Tests

| Class | Focus |
|-------|-------|
| `VolumeRatioCalculatorTest` | Calculator correctness, latest-day exclusion, zero/null handling, supported type. |
| `VolumeStrategyTest` | OBV integration, ADX adjustment (via injected mock regime), close-position logic, logarithmic confidence, spike factor injection, edge cases (null volume, zero range, insufficient data). |
| `StrategyConditionServiceTest` | `calculateVolumeRatio()` excludes latest day; `buildVolumeConditions()` reads property. |

### 7.2 Integration / Aggregator Tests

| Class | Focus |
|-------|-------|
| `StrategySignalAggregatorTest` | Volume strategy contribution under TRENDING/RANGING/TRANSITIONAL regimes; score calculation with volume confidence adjustments. |

### 7.3 Regression Tests

- Run full `mvn test` after changes.
- Verify that `VolumeStrategyTest` cases that previously asserted `0.7` confidence at ratio `1.5` still pass (the log curve gives `0.7` at threshold).
- Verify that cases asserting `1.0` confidence at ratio `3.0` are updated to the new log-based value (~`0.98`).

### 7.4 Test Coverage Targets

| Metric | Target |
|--------|--------|
| VolumeRatioCalculator | 100% |
| VolumeStrategy | ≥ 90% |
| StrategySignalAggregator (volume paths) | ≥ 85% |
| StrategyConditionService (volume methods) | ≥ 80% |

---

## 8. Implementation Checklist

- [ ] **Issue 1:** Fix `StrategyConditionService.calculateVolumeRatio()` to exclude latest day.
- [ ] **Issue 2:** Add `strategy.volume.spike-factor` to `application.properties`.
- [ ] **Issue 2:** Inject property into `VolumeStrategy` and `StrategyConditionService`.
- [ ] **Issue 3:** Add OBV resolution and 5-day trend check in `VolumeStrategy.evaluate()`.
- [ ] **Issue 4:** Add VOLUME case to `StrategySignalAggregator.applyAdxRegimeFilter()`.
- [ ] **Issue 5:** Replace linear confidence with logarithmic formula in `VolumeStrategy`.
- [ ] **Issue 6:** Replace `close > open` with close-position-in-range in `VolumeStrategy`.
- [ ] **Issue 7:** Create `VolumeRatioCalculatorTest` (8 cases).
- [ ] **Issue 7:** Update `VolumeStrategyTest` (10 cases).
- [ ] **Issue 7:** Add volume tests to `StrategySignalAggregatorTest` (4 cases).
- [ ] **Issue 7:** Add/extend `StrategyConditionServiceTest` (2 cases).
- [ ] **Verification:** `mvn compile -q` succeeds.
- [ ] **Verification:** `mvn test` passes with no regressions.
- [ ] **Optional (S3):** Document property in docs.
- [ ] **Optional (S4):** Add startup log for spike factor.

---

## 9. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Log confidence curve produces unexpected values at very high ratios | Low | Low | Cap is still `1.0`; curve is mathematically bounded. |
| `calculateVolumeRatio` fix changes stats cache counts | Medium | Medium | Stats are recomputed asynchronously on next `computeAndCacheStats()` run. Log the change. |
| OBV data missing for many stocks | Medium | Low | Strategy gracefully falls back to volume-only logic. |
| ADX boost changes aggregator scores | Low | Medium | Run regression tests on aggregator thresholds; existing BUY/SELL signals should be stable. |
| Property injection failure in tests | Low | Low | Use `@TestPropertySource` or constructor injection in test setup. |

---

## 10. References

- `VolumeRatioCalculator.java` — canonical 20-day average calculation
- `StrategySignalAggregator.java` — ADX regime filter and confidence model
- `RsiStrategy.java` — simple indicator-resolution pattern
- `MacdStrategy.java` — gap-based confidence scaling pattern
- `StrategyConditionService.java` — DB condition seeding and evaluation
- `application.properties` — strategy configuration conventions

---

*Document version: 1.0*
*Created: 2026-07-16*
*Author: Implementation Planning*
