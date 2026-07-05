---
name: trace-signal
description: Use when debugging or tracing a stock's buy/sell/hold signal — understand why a specific score was generated, which factors fired, and what data might be missing. Front-load keywords: trace signal, debug signal, why buy, why sell, signal score, factor breakdown, composite score.
---

# Trace Signal

## When to use

A signal for a stock looks wrong, or you want to understand why a particular recommendation was generated.

## Step 1: Read the signal endpoint

The signal is computed on-demand in `GET /api/signals/{stockId}` which calls `SignalService.computeSignalFromPrices()`. This is also rendered on the stock detail page.

## Step 2: Read the 16-factor scoring

The core logic is in `SignalService.computeWeightedScore()` (~line 719 in `src/main/java/org/example/service/SignalService.java`). Each factor is evaluated sequentially. Read this method to understand the exact conditions.

## Step 3: Check indicator data availability

Run this thought process:

1. **Does the stock have enough prices?** Minimum 20 daily prices needed.
2. **Are the DB-based indicators computed?** Check `technical_indicators` table for these 7 indicator types: STOCH_K, STOCH_RSI, ULTIMATE_OSC, ROC_12, WILLIAMS_R, CCI, MACD_HISTOGRAM (in `Indicators` enum).
3. **Is RSI divergence detecting correctly?** `TechnicalAnalysisUtils.hasBullishDivergence()` / `hasBearishDivergence()` — scans for 2 troughs/peaks.
4. **Is weekly/monthly RSI data available?** `PriceAggregationService.aggregateWeekly()` / `aggregateMonthly()`.
5. **Is ATR available?** From `TechnicalIndicator` table (ATR type) or computed on-the-fly by `ATRCalculator(14)`.

Use the `/api/indicators/{stockId}` endpoint to check all persisted indicators at once.

## Step 4: Check indicator coverage

`SignalService.computeIndicatorCoverage()` tracks which of the 7 DB-based indicators have data. The max is 16 (9 computed on-the-fly + 7 from DB). Missing indicators reduce confidence by -3 each (max -21 penalty).

## Step 5: Check signal age and data freshness

`SignalService.computeSignalAge()` compares the latest price date to today. A stale signal (>1 day old) loses -5 confidence per day.

## Step 6: Check event risk

`CorporateEventService` checks if a corporate event (dividend, AGM, etc.) falls within 5 days of the signal date. Event risk applies -20 confidence penalty.

## Step 7: Check FII/DII

`FiiDiiService` provides the latest FII/DII net buy/sell data which adjusts the score by ±2.

## Step 8: Check rolling accuracy

`SignalService.computeRollingAccuracy()` looks at the last 90 days of signal records. Accuracy < 50% applies -15 confidence penalty.

## Frontend rendering

The signal breakdown is rendered in `stock-detail.js` in functions:
- `renderSignalContent()` — Main signal card
- `renderScoreBreakdown()` — Per-factor horizontal bars
- `renderPriceAnalysis()` — SMA, Bollinger, 52W proximity
- `renderRiskAssessment()` — Volatility, event risk, divergence, volume
