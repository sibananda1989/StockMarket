---
name: add-indicator
description: Use when adding a new technical indicator to the system — creating the calculator class, adding to the enum, wiring into the analysis service, adding signal scoring, and writing tests. Front-load keywords: add indicator, new indicator, technical indicator, calculator, indicator type.
---

# Add a New Technical Indicator

## Step 1: Create the calculator class

Create a new file in `src/main/java/org/example/service/calculator/YourIndicatorCalculator.java`:

```java
package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class YourIndicatorCalculator implements IndicatorCalculator {
    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        // Implementation here
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.YOUR_NEW_INDICATOR;  // optional override
    }
}
```

The `IndicatorCalculator` interface (at `src/main/java/org/example/service/calculator/IndicatorCalculator.java`) has two methods: `BigDecimal calculate(List<DailyPrice> prices)` (required) and `default IndicatorType getSupportedType()` returning `null` (optional override for typed calculators).

Follow the pattern of existing calculators (e.g., `RsiCalculator`, `MacdLineCalculator`, `IchimokuCalculator`, `VwapCalculator`).

## Step 2: Add to IndicatorType enum

Open `src/main/java/org/example/entity/IndicatorType.java` and add your new indicator name. The enum has two categories — directional (true, indicates buy/sell direction) and non-directional (false, measures magnitude like volatility):

```java
public enum IndicatorType {
    // Directional indicators (indicate buy/sell direction)
    RSI(true), SMA_20(true), SMA_50(true), SMA_200(true), EMA_20(true),
    MACD_LINE(true), MACD_SIGNAL(true), STOCH_K(true), STOCH_D(true),
    WILLIAMS_R(true), CCI(true), STOCH_RSI(true), PLUS_DI(true), MINUS_DI(true),
    ULTIMATE_OSC(true), ROC_12(true), OBV(true), VWAP(true),
    TENKAN_SEN(true), KIJUN_SEN(true), SENKOU_SPAN_A(true), SENKOU_SPAN_B(true), CHIKOU_SPAN(true),

    // Non-directional indicators (measure magnitude, not direction)
    BOLLINGER_UPPER(false), BOLLINGER_LOWER(false), ATR(false), ADX(false);

    private final boolean directional;

    IndicatorType(boolean directional) { this.directional = directional; }
    public boolean isDirectional() { return directional; }
}
```

Pick the right category — adding a volatility-only indicator (like a new ATR variant) as directional will cause it to wrongly contribute to buy/sell scoring.

## Step 3: Wire in TechnicalAnalysisService

Open `src/main/java/org/example/service/TechnicalAnalysisService.java` and:

1. Inject your calculator:
```java
private final YourIndicatorCalculator yourIndicatorCalculator;
```

2. Add to `calculateAllIndicatorsForStock()` method — follow the existing pattern of calling the calculator and persisting via `technicalIndicatorPersistenceService`.

## Step 4: Add signal scoring

If the indicator should contribute to buy/sell signals, add scoring logic in `SignalService.computeWeightedScore()` (~line 719 in `src/main/java/org/example/service/SignalService.java`):

- Fetch the indicator value from the DTO (it's already collected in `computeBaseSignalDto()`)
- Add your scoring condition: `if (condition) score += weight; else if (condition) score -= weight;`
- Common weights: ±1 for moderate indicators, ±2 for strong indicators

## Step 5: Write tests

Create test file at `src/test/java/org/example/service/calculator/YourIndicatorCalculatorTest.java` following the pattern of existing calculator tests (e.g., `RsiCalculatorTest`).

Test cases should cover:
- Normal calculation with sufficient data
- Edge cases (insufficient data, zero values, constant prices)
- Boundary conditions

## Step 6: Add frontend display (optional)

If the indicator should appear in the UI:
1. `stock-detail.js` — Add to the indicator panels
2. `signal-detail.html` — Add visualization if needed

## Verification

- Run `mvn test` to verify calculator tests pass
- Hit `POST /api/indicators/calculate/{stockId}` to test recalculation
- Hit `GET /api/indicators/{stockId}` to see the new indicator value
- Check the stock detail page to verify frontend rendering
