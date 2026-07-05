# Target Calculation

## ATR-Based Targets
Target prices are computed dynamically per signal using the 14-period Average True Range.

### Formulas
| Direction | Target Price | Stop Loss |
|-----------|-------------|-----------|
| BUY / STRONG BUY | `currentPrice + 3 × ATR` | `currentPrice − 2 × ATR` |
| SELL / STRONG SELL | `currentPrice − 3 × ATR` | `currentPrice + 2 × ATR` |
| HOLD | null | null |

### ATR Source
- Primary: Fetched from `technical_indicators` table where `indicator_type = 'ATR'` for the stock's latest calculation date
- Fallback: Computed inline as 14-period SMA of True Range if DB value is unavailable

### Validation
- Negative target/stop values are floored to 0
- Target must be above current price for BUY (else null)
- Stop must be below current price for BUY (else null)

## Risk/Reward Ratio
- BUY: R:R = 3:2 (target +3 ATR, stop −2 ATR)
- This is intentional: buyers accept slightly wider stops in exchange for asymmetric upside

## Limitations
- ATR is a trailing measure — it expands during high volatility, widening both targets and stops
- Does not account for fundamental resistance levels (e.g., 52W high, round numbers)
- Single-price targets lack scaling logic (partial exits at intermediate levels)
