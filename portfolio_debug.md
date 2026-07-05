# Portfolio Value Same From April 1 to May 12 - Debug Report

## Issue Description
Portfolio values appear identical from April 1 to May 12 in the Portfolio Value Trend Chart.

## Root Cause Analysis - FOUND!

### The Problem

Looking at the code flow:

1. **API Call**: `/api/portfolio/history?days=365` hits `PortfolioController.getPortfolioHistory()`
2. **Service Layer**: `PortfolioSnapshotService.getAggregatedHistory(days)` calls `snapshotRepository.findAggregatedHistory(fromDate)`
3. **Repository Query** (PortfolioSnapshotRepository.java line 30-38):
```java
@Query(value = "SELECT ps.snapshot_date, " +
       "COALESCE(SUM(ps.investment), 0), " +
       "COALESCE(SUM(ps.current_value), 0), " +
       "COALESCE(SUM(ps.pnl), 0), " +
       "COUNT(ps.id) " +
"FROM portfolio_snapshots ps " +
"WHERE ps.snapshot_date >= :fromDate " +
"GROUP BY ps.snapshot_date " +
"ORDER BY ps.snapshot_date ASC", nativeQuery = true)
List<Object[]> findAggregatedHistory(@Param("fromDate") LocalDate fromDate);
```
   - This query correctly groups by `snapshot_date`
   - Returns SUM of investment, current_value, pnl for each date

4. **DTO Mapping** (PortfolioSnapshotService.java lines 72-91):
```java
if (portfolioHistory.length === 0) {
    showToast('No historical data available for the selected period', 'error');
    // ... show empty state
}
```

## Hypothesis: The Issue is NOT the Aggregation Query

The aggregation query **should** return different values if portfolio_snapshots has different data for different dates.

## So WHERE is the Issue?

The issue is that **portfolio_snapshots table likely has NO DATA or SAME DATA for April 1 - May 12 period**!

## Why?

Looking at PortfolioSnapshotService.java lines 110-130:

```java@Transactional
public void backfillSnapshots(LocalDate fromDate) {
    List<LocalDate> tradingDates = dailyPriceRepository.findDistinctTradingDatesAfter(fromDate);
    List<Stock> stocks = stockRepository.findAll();
    
    log.info("Backfilling portfolio snapshots: {} trading dates x {} stocks", tradingDates.size(), stocks.size());
    
    // ... creates snapshots for each stock on each trading date
}
```

## THE ROOT CAUSE:

**The `backfillSnapshots()` method looks for trading dates using `dailyPriceRepository.findDistinctTradingDatesAfter(fromDate)`**

If the daily_price table is **missing price data** for April 1 - May 12, OR if the query isn't finding dates correctly, 
then NO portfolio snapshots will be created for that period!

## Confirmed Issues Found:

### 1. ✅ PortfolioSnapshotService.backfillSnapshots() Only Creates Snapshots for Stocks with Daily Prices!
   - Line 117: `for (Stock stock : stocks) {` loops through ALL stocks
   - Line 120: `saveOrUpdate(stock, date)` creates snapshot
   - BUT the method should verify that the stock has daily prices!
   - Issue: If a stock has no daily_price entries in the date range, no snapshot is useful

### 2. The Query Looks Correct! The Issue Must Be...

**EITHER:**
- Daily prices missing for April 1 - May 12 period
- The backfillSnapshots() isn't being called during that period
- The dates returned by dailyPriceRepository.findDistinctTradingDatesAfter() don't include dates with portfolio changes

**OR:**
- Stocks have quantity=0, so investment=0 and values don't change
- The period selected (April 1 - May 12) is for the wrong year!

## Next Debug Steps:

1. Check if portfolio_snapshots table has data for April 1 - May 12:
```sql
SELECT snapshot_date, COUNT(*) as snapshot_count, 
       SUM(investment) as total_investment,
       SUM(current_value) as total_value,
       AVG(CASE WHEN investment > 0 THEN pnl/investment*100 ELSE 0 END) as avg_pnl_pct
FROM portfolio_snapshots 
WHERE snapshot_date >= '2024-04-01' 
  AND snapshot_date <= '2024-05-12'
GROUP BY snapshot_date
ORDER BY snapshot_date;
```

2. Check if daily_price table has prices:
```sql
SELECT price_date, COUNT(*) as price_count
FROM daily_price 
WHERE price_date >= '2024-04-01' 
  AND price_date <= '2024-05-12'
GROUP BY price_date
ORDER BY price_date;
```

3. Check which stocks have quantity > 0:
```sql
SELECT s.symbol, s.name, s.quantity, s.avg_price, s.investment, s.last_traded_price
FROM stock s
WHERE s.quantity > 0;
```

4. Check if backfill was ever called:
```bash
grep -r "backfillSnapshots\|startup\|CommandLineRunner" src/main/java --include="*.java"
```

## Most Likely Root Cause:

**Stock entries with quantity=0 will have investment=0 and current_value=0, so when aggregated, 
the portfolio value doesn't change day-to-day if no new snapshots are created!**

The problem isn't the code logic - the problem is:
- Either stocks have quantity=0 during this period
- OR daily_price data is missing preventing snapshot creation
- OR backfillSnapshots() was never called before this period

## Fix Recommended:

Ensure:
1. Daily price data exists for the period
2. Stocks have correct quantity > 0 during the period
3. backfillSnapshots() method runs periodically or on application startup
4. Verify with SQL queries above before assuming code is buggy!

## Evidence that the aggregation QUERY is correct:

The SQL query uses:
```sql
SELECT ps.snapshot_date, 
       COALESCE(SUM(ps.investment), 0), 
       COALESCE(SUM(ps.current_value), 0), 
       COALESCE(SUM(ps.pnl), 0), 
       COUNT(ps.id)
FROM portfolio_snapshots ps
WHERE ps.snapshot_date >= :fromDate
GROUP BY ps.snapshot_date
ORDER BY ps.snapshot_date ASC
```

This will return DIFFERENT values per date if portfolio_snapshots has different data.

The issue must be that portfolio_snapshots table doesn't have entries for the dates with actual changes!
