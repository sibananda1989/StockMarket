# Backtesting Rules

## Overview
The backtester (`BacktestService.runBacktest()`) walks through historical prices sequentially, generating signals at each bar and simulating trades.

## Parameters
| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `stockId` | required | — | Stock to backtest |
| `useStopLoss` | true | true/false | Enable trailing stop-loss |
| `positionSizePct` | 0.02 | 0.01–0.05 | Risk per trade (% of capital) |

## Initial Capital
10,000 (constant, not configurable via API)

## Core Algorithm
For each price bar from index 20 to end:

1. **Compute signal**: Uses `SignalService.computeSignalFromPrices()` (lightweight path)
2. **Event check**: Skip trade if corporate event risk exists for the current bar

### If Holding Position
- **Stop loss** (enabled): Update trailing stop via `TrailingStopService.updateTrailingStop()` using current bar's high and ATR. If low breaches stop, sell all shares.
- **Signal exit**: If recommendation is SELL/STRONG SELL, close position.

### If Not Holding
- **Entry**: If recommendation is BUY/STRONG BUY, compute ATR, set initial stop at `price − 2× ATR`
- **Position size**: `PositionSizingService.suggestPosition(capital, price, stop, riskPct)`
- Deduct capital used

### Tracking
At each step, update:
- Portfolio value (cash + position value)
- Peak value (for drawdown calculation)
- Max drawdown (peak-to-trough)

## Output Metrics
| Field | Description |
|-------|-------------|
| totalTrades | Number of completed trades |
| winningTrades | Trades with positive return |
| losingTrades | Trades with negative return |
| stoppedOutTrades | Exits triggered by stop-loss |
| signalExits | Exits triggered by signal reversal |
| eventsSkipped | Potential trades skipped due to events |
| winRate | winningTrades / completedTrades × 100 |
| totalReturn | (final − initial) / initial × 100 |
| averageReturnPerTrade | Mean return across all trades |
| maxDrawdown | Maximum peak-to-trough decline % |
| finalPortfolioValue | Ending portfolio value |

## Signal Performance Precomputation
- Runs daily at 2:30 AM (`SignalPerformanceScheduler`)
- Samples every 5 bars for stocks with 60+ price points
- Measures forward returns at 5/10/20 trading days
- Aggregates by recommendation type → `signal_historical_performance`

## Signal Accuracy Marking
- Runs daily at 3 AM (`SignalAccuracyScheduler`)
- Marks unmarked signal records older than 10 days
- Computes forward return and sets `was_accurate` flags
- Powers the confidence score's accuracy adjustment

## Limitations
- Single-stock only (no portfolio-level backtest)
- No slippage or transaction cost modeling
- No partial exits or scaling
- Fixed initial capital (10,000) is not configurable via API
