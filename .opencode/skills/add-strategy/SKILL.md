---
name: add-strategy
description: Use when adding a new trading strategy to the multi-strategy signal engine — extending TradingStrategy, wiring into the Spring strategy registry, adding condition overrides, frontend config in strategy-manager.js, and writing tests. Front-load keywords: add strategy, new strategy, trading strategy, multi-strategy, signal engine, aggregator.
---

# Add a New Trading Strategy

The multi-strategy signal system lives under `src/main/java/org/example/strategy/`. Each strategy extends the abstract `TradingStrategy` base class, emits a `StrategyResult` (BUY/SELL/HOLD + confidence + reason), and is aggregated by `StrategySignalAggregator` into one weighted score per stock.

## Architecture (read these files first)

| File | Role |
|------|------|
| `strategy/base/TradingStrategy.java` | Abstract base — implements `resolveIndicator()` / `resolvePreviousIndicator()` / `insufficientData()` helpers |
| `strategy/model/StrategyResult.java` | record(result, confidence, reason, strategyName, priority, contribution) |
| `strategy/model/StrategySignal.java` | enum BUY / SELL / HOLD |
| `strategy/model/AggregatedSignalResult.java` | record(finalSignal, score, breakdown, totalPriority, confidence, supporting, opposing, categorySummary, contributions) |
| `strategy/aggregator/StrategySignalAggregator.java` | Runs all strategies, applies DB condition overrides, computes weighted score and confidence |
| `strategy/engine/MultiStrategySignalEngine.java` | Public entry — fetches prices + indicators, delegates to aggregator, also has `evaluateHistory()` |
| `strategy/impl/*.java` | The 6 concrete strategies: RSI, MACD, MovingAverageCrossover, BollingerBand, Volume, CandlestickPattern |
| `service/StrategyConfigService.java` | Reads per-strategy priority and enabled flag from `strategy_config` table |
| `service/StrategyConditionService.java` | Reads DB-stored `StrategyConditionGroup` overrides for each strategy |
| `entity/StrategyConfig.java` | JPA — one row per strategy name (priority, enabled) |

## Step 1: Create the strategy class

Create `src/main/java/org/example/strategy/impl/YourStrategy.java`. Extend `TradingStrategy` and call `super(name, priority)` from the constructor — Spring instantiates each strategy manually (see Step 3), so **do not annotate it `@Component`** unless the strategy should auto-register with default priority.

Follow the pattern in `RsiStrategy.java` (simplest) or `MacdStrategy.java` (crossover detection using `resolvePreviousIndicator()`):

```java
package org.example.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.strategy.base.TradingStrategy;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.util.List;
import java.util.Optional;

@Slf4j
public class YourStrategy extends TradingStrategy {

    public YourStrategy(int priority) {
        super("YOUR_NAME", priority);  // name must be unique and uppercase-friendly
    }

    @Override
    public StrategyResult evaluate(Long stockId,
                                   List<TechnicalIndicator> indicators,
                                   List<DailyPrice> prices) {
        Optional<Double> value = resolveIndicator(indicators, IndicatorType.YOUR_INDICATOR);
        if (value.isEmpty()) {
            return insufficientData();  // HOLD with 0.0 confidence
        }
        double v = value.get();
        if (v < LOW_THRESHOLD) {
            return StrategyResult.withoutContribution(
                StrategySignal.BUY, 0.85,
                String.format("Your reason (%.1f)", v), getName(), getPriority());
        } else if (v > HIGH_THRESHOLD) {
            return StrategyResult.withoutContribution(
                StrategySignal.SELL, 0.80,
                String.format("Your reason (%.1f)", v), getName(), getPriority());
        }
        return StrategyResult.withoutContribution(
            StrategySignal.HOLD, 0.50, "Neutral", getName(), getPriority());
    }
}
```

### Conventions

- **Name** must match the `strategyName` column in `strategy_config` and `strategy_condition_groups` tables. Use uppercase snake-case (e.g., `RSI`, `MA_CROSSOVER`).
- **Confidence** is between 0.0 and 1.0. Pick a value reflecting how strong the pattern is. Existing strategies use 0.40–0.85.
- **Reason** is short and human-readable — it appears in the frontend breakdown. Include the numeric value in `(%.1f)` format so the user can sanity-check.
- For crossover strategies (e.g., MACD, MA_CROSSOVER), use `resolvePreviousIndicator()` to fetch yesterday's value and detect the cross.
- Always return `insufficientData()` (never `null`) when data is missing — the aggregator explicitly checks for and skips null but logs the strategy.

## Step 2: Register the strategy in the Spring config

Strategies are injected as a `List<TradingStrategy>` into `StrategySignalAggregator`'s constructor. Two registration patterns exist:

**Option A — Auto-registered via `@Component`:** If the strategy has a 0-arg or `@Value`-injected constructor, annotating `@Component` will register it. Default priority is the constructor's default. This is the **simplest path** for new strategies with no special lifecycle.

**Option B — Manual bean config (used by existing 5 impl strategies):** Check `StrategyConfig.java` in `strategy/config/` — the existing strategies are declared as `@Bean` methods so their priorities can be tuned via `@Value` properties. Follow the same pattern if you want externalized priority:

```java
@Configuration
public class StrategyConfig {
    @Bean
    public TradingStrategy rsiStrategy(@Value("${strategy.rsi.priority:5}") int priority) {
        return new RsiStrategy(priority);
    }
    // Add your strategy here:
    @Bean
    public TradingStrategy yourStrategy(@Value("${strategy.your-name.priority:5}") int priority) {
        return new YourStrategy(priority);
    }
}
```

> **Check the actual file** — the system went through a refactor. If `@Component` is already on `RsiStrategy`, use `@Component` for consistency. Match whatever pattern the existing 6 strategies use.

## Step 3: Add default config row

Add a default row to the `strategy_config` table seeding. Either:
- Edit the `@EventListener(ApplicationReadyEvent.class)` seeder in `startup/` that seeds default strategy configs, **or**
- Insert via SQL: `INSERT INTO strategy_config (strategy_name, priority, enabled) VALUES ('YOUR_NAME', 5, 1);`

**Gotcha:** Use `@EventListener(ApplicationReadyEvent.class)`, NOT `@PostConstruct` — `@PostConstruct` runs before Hibernate DDL creates the table (see AGENTS.md).

## Step 4: Add `application.properties` defaults (optional)

If you externalized priority via `@Value`:

```properties
strategy.your-name.priority=5
strategy.your-name.enabled=true
```

## Step 5: Write tests

Create `src/test/java/org/example/strategy/impl/YourStrategyTest.java`. Follow `RsiStrategyTest.java` (the simplest existing example). Test cases:

1. BUY signal triggered at the right threshold, with correct confidence and reason
2. SELL signal triggered at the right threshold
3. HOLD signal in the neutral zone
4. `insufficientData()` returned when the indicator is missing
5. Edge cases: boundary values exactly at threshold

Also touch `StrategySignalAggregatorTest.java` if the new strategy changes overall aggregation behavior in non-trivial ways.

## Step 6: Add frontend config UI (optional)

The strategy manager page lets users toggle strategies and adjust priorities. Files:

| File | Role |
|------|------|
| `src/main/resources/static/strategy.html` | The page (lightweight — just hosts `#strategyList`) |
| `src/main/resources/static/strategy-manager.js` | IIFE module — renders strategy cards, wires toggles and sliders |
| `src/main/resources/static/js/api.js` | `getStrategyConfigs()`, `updateStrategyConfig()`, etc. |

In `strategy-manager.js`, find the strategy definition array and add an entry for your strategy:

```js
const STRATEGIES = [
    { name: 'RSI', label: 'Relative Strength Index', description: '...' },
    // add:
    { name: 'YOUR_NAME', label: 'Your Label', description: 'Short description shown in the UI' },
];
```

The UI auto-renders from this array, fetching the priority/enabled state from `getStrategyConfigs()`.

## Verification

1. **Run tests:** `./run-tests.sh` — should include the new `YourStrategyTest`.
2. **Compile & boot:** `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
3. **Hit the API:** `GET http://localhost:8080/api/multi-strategy/signals/{stockId}` — verify the response `breakdown` list includes `YOUR_NAME` with the expected signal and contribution.
4. **Check aggregation:** Open the strategy manager page (`strategy.html`) — your strategy card should appear. Toggle priority and re-check the API — the contribution (priority × confidence × ±1) should change proportionally.
5. **History endpoint:** `GET /api/multi-strategy/signals/{stockId}/history?days=30` — your strategy should appear in each historical point's breakdown.

## Gotchas

- **`signal` is a MySQL reserved word.** The strategy JPA entities map to columns named `signal_type`. Don't add a `@Column(name = "signal")` field anywhere.
- **`@Transactional` on derived delete queries.** Any new method calling `deleteByStrategyName(...)` etc. requires `@Transactional` or it throws 500.
- **`@Cacheable("signals")`** — caches results, only takes effect after app restart. New strategies won't show cached behavior until restart.
- **Forward-only scoring.** The aggregator's contribution formula is `signalNumeric × priority × confidence` (BUY=+1, SELL=-1, HOLD=0). A HOLD strategy contributes 0 — don't expect every active strategy to nudge the score.
- **DB condition overrides can fail silent.** If `StrategyConditionGroup` rows exist for your strategy name, they **override** the in-code result. Make sure you don't have stale seeded conditions with your strategy name.
