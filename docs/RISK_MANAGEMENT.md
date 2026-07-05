# Risk Management

## Stop Loss
Calculated per-signal using ATR (Average True Range, 14-period).

| Recommendation | Stop Loss |
|---------------|-----------|
| BUY / STRONG BUY | `entryPrice − 2 × ATR` |
| SELL / STRONG SELL | `entryPrice + 2 × ATR` |
| HOLD | None |

### Trailing Stop (`TrailingStopService.java`)
- **Initial**: `entryPrice − 2 × ATR`
- **Update**: `max(currentStop, currentHigh − 3 × ATR)`
- **Trigger**: `lowPrice ≤ stopPrice`

## Position Sizing (`PositionSizingService.java`)
```
riskAmount = capital × riskPercentage
riskPerShare = entryPrice − stopPrice
shares = floor(riskAmount / riskPerShare)
capitalUsed = shares × entryPrice
```

### Signal-Level Position Size (`SignalService.computePositionSize()`)
```
size = 20.0 / volatility
clamped = max(1.0%, min(5.0%, size))
```

## Risk Modifiers in Confidence Score
| Risk Factor | Penalty |
|-------------|---------|
| Corporate event (results, dividends) | −20 confidence |
| Signal stale (>1 day since last price) | −5 per day |
| Missing indicators (< 16 of 16) | −3 per missing |
| Counter-trend with strong trend (ADX > 30) | score × 0.8 |
| Low historical accuracy (< 40% 30-day) | −20 confidence |
| Moderate historical accuracy (40–50%) | −15 confidence |

## Event Risk Detection
- Corporate events fetched from NSE corporate announcements
- Stocks with events in the next 5 days are flagged
- Confidence penalty is applied but signal is not suppressed (buyer may still want awareness)
