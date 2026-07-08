# RSI Data Integrity Investigation — Anchor Summary 2

## Date: 2026-07-06 (Monday)

### What We've Learned

#### The Bug: Systematic RSI Staleness

After exhaustive tracing across the codebase, here's the confirmed root cause:

**Root Cause: Dhan Sync Corrupts Prices at 4:15 PM Weekdays**

1. **9 AM daily task** (`calculateDailyIndicators()`) computes correct RSI (~44.86 for INOXWIND) using Yahoo prices and saves it with `calculation_date = today`
2. **4:15 PM Dhan sync** (`syncDhanAndCalculateIndicators()`) calls `DhanSyncService.syncDailyPricesAndRsi()` which:
   - Calls `DhanApiService.getDailyCandles()` to fetch 60 days of candles
   - `saveCandlesToDb()` unconditionally overwrites ALL existing prices in that 60-day window with Dhan's data
   - Then calls `calculateIndicatorsForStock()` which computes RSI using the **now-Dhan-modified prices** → produces a different RSI (28.37)
   - Saves this RSI with `calculation_date = today`, **overwriting** the correct 9 AM value
3. On weekends, only the 9 AM task runs (Dhan sync is `MON-FRI`), so correct values survive

#### Why Weekend Values Are Sometimes Correct

- If the server restarts on a weekend, `StartupDataSyncTask` syncs 7 days from Yahoo, **restoring correct prices** before recalculating indicators
- But if the server runs continuously through the weekend, the Dhan-corrupted prices from Friday persist, producing wrong values even on weekends

#### Why Stale RSI Value (28.37) Is Actually a "Correct" Computation

The stale 28.37 is the genuine RSI computed from Dhan's potentially unadjusted or differently-sourced price data. Dhan may return:
- Prices unadjusted for corporate actions (bonus issues, stock splits)
- A different data source (NSE/BSE direct vs Yahoo's adjusted close)
- Intraday/incomplete data for the current trading day

#### Evidence Summary

| Pattern | Value | Source |
|---------|-------|--------|
| Correct RSI (when freshly computed with Yahoo prices) | 44.86 | Yahoo Finance |
| Stale RSI (after Dhan sync overwrites) | 28.37 | Dhan API prices |
| Weekday stored values | 28.37 | Dhan sync overwrites at 4:15 PM |
| Weekend stored values | 44.86 | Only 9 AM task runs |

#### Fix Needed

1. **Primary Fix**: `DhanSyncService.saveCandlesToDb()` should NOT unconditionally overwrite existing prices. It should only INSERT new records (dates not yet in DB). The comment on line 185 `orElse(new DailyPrice(...))` creates a new record for missing dates, but then lines 187-191 always update ALL fields, and line 193 always saves — even for existing records. Change to skip `save()` when the record already existed.

2. **Secondary Fix**: `TechnicalIndicatorPersistenceService.saveOrUpdateIndicator()` should add a staleness guard — only overwrite if the new value is "fresher" (e.g., computed at a later time) or the existing value is null.

3. **Tertiary Fix**: `IndicatorStartupTask` should verify correctness, not just completeness. Currently it only counts records per date; if count >= 27 it skips even if values are stale.

#### Key Files

| File | Function | Role in Bug |
|------|----------|-------------|
| `DhanSyncService.java` | `saveCandlesToDb()` lines 172-197 | Unconditionally overwrites 60 days of prices with Dhan data |
| `DhanSyncService.java` | `syncPricesForHolding()` lines 136-169 | Calls `calculateIndicatorsForStock` after Dhan overwrite |
| `TechnicalAnalysisService.java` | `calculateIndicatorsForStock()` lines 101-147 | Computes RSI using whatever prices are in DB (could be Dhan-corrupted) |
| `TechnicalIndicatorPersistenceService.java` | `saveOrUpdateIndicator()` lines 23-50 | Unconditional overwrite — no staleness check |
| `MarketAnalysisScheduler.java` | `syncDhanAndCalculateIndicators()` line 35-43 | Weekday 4:15 PM task that triggers the corruption cycle |
| `IndicatorStartupTask.java` | `execute()` lines 69-101 | Completeness-only check (count >= 27), no correctness verification |

#### All Affected Indicators

This bug affects ALL 27+ technical indicators, not just RSI. Any indicator whose value changes when computed with Dhan prices vs Yahoo prices is affected. RSI is the most visible due to its sensitivity to recent price changes.

#### Next Action Items

1. Fix `saveCandlesToDb()` to only INSERT new records, not UPDATE existing ones
2. Fix `saveOrUpdateIndicator()` to add staleness guard
3. Fix `IndicatorStartupTask` to verify correctness
4. Backfill correct values for all dates/stocks after fix
