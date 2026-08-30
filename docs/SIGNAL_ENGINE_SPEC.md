# Signal Engine Specification

## Overview
The signal engine (`SignalService.java` — 2,986 lines) computes a composite score from 15 technical factors (31 IndicatorTypes), applies modifier adjustments (ADX, volume, FII/DII, bearish discount), and maps via `SignalThresholds.java` to recommendation. Scores cached via Caffeine (10-15 min TTL, 7 caches). Last audited 2026-08-21.

## Scoring Factors

| # | Factor | Bullish | Bearish |
|---|--------|---------|---------|
| 1 | RSI Divergence | +4 (bullish divergence) | −2 (bearish divergence) |
| 2 | Weekly Confluence | +3 (daily+weekly oversold) | −2 (daily+weekly overbought) |
| 3 | Monthly Confluence | +1 | — |
| 4 | RSI(14) | +2 (< 30) | −1 (> 75) |
| 5 | Bollinger Bands | +2 (below lower band) | — |
| 6 | MACD | +3 (bullish cross) | −2 (bearish cross) |
| 7 | Stochastic | +2 (oversold cross), +1 (rising) | −1 (overbought cross, falling) |
| 8 | StochRSI | +2 (< 20), +1 (< 30) | −1 (> 85) |
| 9 | Ultimate Oscillator | +1 (< 30) | — |
| 10 | ROC | +1 (> 0) | — |
| 11 | Williams %R | +1 (< −80) | — |
| 12 | CCI | +1 (< −100) | — |
| 13 | OBV | +1 (3 up closes) | — |
| 14 | SMA20 | +1 (price >5% below SMA20) | — |
| 15 | 52-Week | +1 (within 5% of 52W low) | — |
| 16 | ADX Filter | score × 0.8 (ADX > 30 + counter-trend) | — |
| 17 | Volume Confirmation | +2 (volume ≥ 1.2× 20d avg) | — |
| — | FII/DII Sentiment | +1 (combined > 500 Cr) | −1 (combined < −500 Cr) |

## Recommendation Thresholds — `SignalThresholds.java`

| Recommendation | Score Range |
|---------------|-----------|
| STRONG BUY | ≥ 7 (`STRONG_BUY_THRESHOLD`) |
| BUY | ≥ 3 (`BUY_THRESHOLD`) |
| HOLD | −3 to 2 |
| SELL | ≤ −4 (`SELL_THRESHOLD`) |
| STRONG SELL | ≤ −7 (`STRONG_SELL_THRESHOLD`) |
| **Bearish dampener** | When SMA20 < SMA50: dampener 0.5× + subtract 2, discount 0.85 (reversal exceptions 0.9-1.0) |
| **ADX multiplier** | <15→0.3, 15-25→0.5-0.7, 25-35→0.7-1.0, ≥35→1.0 (counter-trend 0.7) |
| **Overbought dampener** | 0.7× when ≥3 (or ≥4 in bullish ADX) of StochRSI>85/CCI>150/StochK>80/W%R>-20 |

## Confidence Score (0–100)
```
base = 50 + compositeScore × 5  (clamped 0–100)
+10  if volume confirmed
−20  if event risk detected
+abs(weeklyConfluence)   (max +3)
+abs(monthlyConfluence)   (max +1)
−5 per day of signal age
−3 per missing indicator out of 16
+10 if 30d accuracy ≥ 70%
+5  if 30d accuracy ≥ 60%
−20 if 30d accuracy < 40%
−15 if 30d accuracy < 50%
```

## Target & Stop (ATR-based)
| Type | BUY | SELL |
|------|-----|------|
| Target | price + 3× ATR | price − 3× ATR |
| Stop | price − 2× ATR | price + 2× ATR |

## Position Size
```
size = 20.0 / volatility
size = clamp(1.0%, 5.0%)
```
