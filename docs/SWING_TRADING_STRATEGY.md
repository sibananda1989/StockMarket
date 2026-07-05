# Swing Trading Strategy

## Philosophy
The signal engine is biased toward **buyers** who hold for weeks to months (swing trading). It identifies stocks where technical factors align to suggest upward price movement, while filtering out weak or conflicting setups.

## Entry Triggers
- **Oversold bounce**: RSI(14) < 30 combined with weekly RSI confluence
- **Bullish divergence**: Price makes lower low, RSI makes higher low
- **MACD crossover**: MACD line crosses above signal line with positive histogram
- **Bollinger Band squeeze**: Price near lower band with volume confirmation
- **Multi-indicator alignment**: 3+ factors pointing in the same direction

## Exit Triggers
- **Target hit**: Price reaches 3× ATR from entry
- **Stop loss**: Price falls below 2× ATR from entry
- **Signal reversal**: Recommendation changes to SELL/STRONG SELL
- **Event risk**: Upcoming corporate events (results, dividends, splits)

## Position Sizing
- Risk per trade: 1–5% of portfolio capital (default 2%)
- Position size: `20 / volatility %`, clamped to [1.0%, 5.0%]
- ATR-based stop ensures risk is quantified before entry

## Why This Works for Buyers
- Oversold conditions in strong trends offer the best risk/reward
- Multi-factor scoring avoids false signals from any single indicator
- FII/DII sentiment adjustment is mild (±1) to avoid market-wide noise overwhelming stock-level analysis
