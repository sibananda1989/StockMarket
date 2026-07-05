# Breakout Detection

## Current Approach
StockTracker does not have an explicit breakout detection module. Breakout-like setups are captured indirectly through the signal engine's factor scoring:

### Implicit Breakout Signals
| Scenario | Detection | Score Impact |
|----------|-----------|-------------|
| Price breaks above resistance | MACD bullish cross | +3 (MACD score) |
| Price explodes from oversold | RSI < 30 + volume confirmation | +2 (RSI) + 2 (Volume) |
| Strong uptrend continuation | ADX > 30 with +DI > −DI | No penalty (trend-following) |
| Bollinger Band expansion | Price at upper band | No score (neutral for buyer) |

### What's Missing
- Volume breakout: price + volume spike above resistance level
- Trendline breakout: price closing above/below a multi-touch trendline
- Range breakout: price exiting a well-defined consolidation zone
- Gap detection: opening gaps with follow-through

## Future Direction
A dedicated breakout detector could integrate:
- Consolidation zone identification (narrowing Bollinger Bands + low ADX)
- Volume-weighted breakout confirmation (2× average volume)
- Pullback entry after successful retest of broken level
- Multi-timeframe confluence (daily + weekly breakout alignment)
