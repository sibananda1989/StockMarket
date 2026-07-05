# Price Action Rules

## Trend Detection
- **Trend strength** (`SignalService.calculateTrendStrength`): Ratio of advancing days over 10-bar lookback, expressed as percentage (0–100%)
- **Volatility** (`SignalService.calculateVolatility`): Standard deviation of daily returns across full price history

## Support & Resistance (`SupportResistanceCalculator.java`)

### 20-Day Swing Highs/Lows
- Lookback: ±5 bars around each candle
- Filter: level must deviate > 1% from average price
- Strength: `min(100, touches × 15)` where touches count levels within 2% proximity

### Standard Pivot Points (20-day OHLC)
```
P  = (H20 + L20 + C20) / 3
S1 = 2P − H20    R1 = 2P − L20
S2 = P − (H20−L20)    R2 = P + (H20−L20)
S3 = L20 − 2(H20−P)   R3 = H20 + 2(P−L20)
```

### Major Historical Levels (6-month)
- `HISTORICAL_LOOKBACK_DAYS = 180`
- Cluster tolerance: 2% (merges nearby levels)
- Minimum 2 touches to qualify as "major"
- Top 10 returned by strength score

### Level Types
`SWING_LOW`, `SWING_HIGH`, `PIVOT_P`, `PIVOT_S1`/`S2`/`S3`, `PIVOT_R1`/`R2`/`R3`, `MAJOR_SUPPORT`, `MAJOR_RESISTANCE`

## RSI Divergence (`TechnicalAnalysisUtils.java`)
- **Bullish divergence**: Price lower low, RSI higher low (last 2 troughs)
- **Bearish divergence**: Price higher high, RSI lower high (last 2 peaks)
- Requires ≥ 20 price bars minimum
- Trough/peak detection: price[i] < price[i−1] AND price[i] < price[i+1] (vice versa for peaks)
