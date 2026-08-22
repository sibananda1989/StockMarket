# API Specification — 27 controllers, ~153 mappings

Base URL: `http://localhost:8080/api` — Last audited 2026-08-21 — OpenAPI at `/swagger-ui.html`

## Stocks
| Method | Path | Description |
|--------|------|-------------|
| GET | `/stocks` | List all stocks |
| POST | `/stocks` | Create stock |
| GET | `/stocks/{id}` | Get stock details |
| PUT | `/stocks/{id}` | Update stock |
| DELETE | `/stocks/{id}` | Delete stock |
| GET | `/stocks/search?name=&limit=` | Search stocks by name |
| GET | `/stocks/validate-symbol?symbol=` | Validate symbol via Yahoo |
| POST | `/stocks/recalculate-portfolio` | Recalculate all P&L |
| POST | `/stocks/csv-import` | Bulk import from CSV |
| PATCH | `/stocks/{id}/portfolio` | Update portfolio data |
| DELETE | `/stocks/by-prefix/{prefix}` | Delete by symbol prefix |
| GET | `/stocks/{id}/daily-prices?days=` | Daily prices for stock |
| GET | `/stocks/{id}/snapshots?days=` | Snapshots for stock |
| GET | `/stocks/{id}/snapshots/all` | All snapshots for stock |

## Signals
| Method | Path | Description |
|--------|------|-------------|
| GET | `/signals` | All signals (computed on-demand) |
| GET | `/signals/buy` | BUY / STRONG BUY signals (score ≥ 3) |
| GET | `/signals/sell` | SELL / STRONG SELL signals (score ≤ −4) |
| GET | `/signals/{stockId}` | Signal for single stock |
| GET | `/signals/{stockId}/history?days=` | Signal timeline |

## Multi-Strategy Signals
| Method | Path | Description |
|--------|------|-------------|
| GET | `/signals/multi-strategy/{stockId}` | Aggregated 10-strategy signal |
| GET | `/signals/multi-strategy/{stockId}/breakdown` | Per-strategy breakdown |
| GET | `/signals/multi-strategy/compare/{stockId}` | Old vs new engine compare |
| GET | `/signals/multi-strategy/{stockId}/history?days=` | History |

## Strategy Config & Weights
| Method | Path | Description |
|--------|------|-------------|
| GET | `/strategy-config` | List all strategy configs |
| GET | `/strategy-config/full` | Full config with conditions |
| PUT | `/strategy-config/{strategyName}` | Update priority/enabled |
| PUT | `/strategy-config/{strategyName}/priority` | Update priority |
| PUT | `/strategy-config/conditions/{strategyName}` | Replace conditions for strategy |
| PATCH | `/strategy-config/conditions/{strategyName}/{conditionId}` | Update condition |
| POST | `/strategy-config/conditions/reset` | Reset conditions |
| GET | `/strategy-config/stats` | Strategy stats |
| GET | `/strategy-config/volume/spike-factor` | Volume spike factor |
| PUT | `/strategy-config/volume/spike-factor?factor=` | Update spike factor |
| GET | `/strategy-daily-weight/status` | Weight status |
| POST | `/strategy-daily-weight/backfill` | Backfill weights |
| POST | `/strategy-daily-weight/backfill/{stockId}` | Backfill one stock |
| GET | `/strategy-results` | All strategy results |
| GET | `/strategy-results/{strategyName}` | Results for strategy |
| POST | `/strategy-results/refresh` | Refresh results |
| GET | `/score-parameters` | List score toggles |
| PUT | `/score-parameters/{paramKey}` | Toggle factor |
| POST | `/score-parameters/reset` | Reset toggles |
| GET | `/data-availability` | Data coverage health |
| GET | `/indicators/coverage/{stockId}` | Indicator gaps |
| GET | `/smc/{stockId}` | SMC patterns (FVG/OB) |

## Prices
| Method | Path | Description |
|--------|------|-------------|
| POST | `/prices` | Save daily price |
| GET | `/prices/stock/{stockId}?fromDate=&toDate=` | Price history |
| GET | `/prices/stock/{stockId}/latest` | Latest price |

## RSI
| Method | Path | Description |
|--------|------|-------------|
| GET | `/rsi/stock/{stockId}` | Latest RSI |
| GET | `/rsi/stock/{stockId}/history?days=` | RSI history |
| POST | `/rsi/calculate/{stockId}` | Calculate RSI |
| POST | `/rsi/calculate-all` | Batch RSI calculation |
| POST | `/rsi/fill-gaps` | Fill missing RSI gaps |

## Indicators — TechnicalIndicatorController (returns RAW DTOs, not ApiResponse)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/indicators/{stockId}` | All latest indicators (cached) |
| GET | `/indicators/{stockId}/history` | Full history |
| GET | `/indicators/{stockId}/{type}?fromDate=&toDate=` | Indicator type history |
| POST | `/indicators/calculate/{stockId}` | Trigger recalculation |
| POST | `/indicators/fill-gaps` | Fill missing indicator gaps |
| POST | `/indicators/backfill` | Backfill all |
| POST | `/indicators/reset/{stockId}` | Reset indicators |
| POST | `/indicators/reset-all` | Reset all |
| GET | `/rsi/stock/{stockId}` | Latest RSI (deprecated wrapper) |
| GET | `/rsi/stock/{stockId}/history` | RSI history |

## Support/Resistance
| Method | Path | Description |
|--------|------|-------------|
| GET | `/support-resistance/{stockId}` | Latest S/R levels |
| GET | `/support-resistance/{stockId}/as-of?date=` | As-of date |
| GET | `/support-resistance/{stockId}/history` | History |
| POST | `/support-resistance/calculate/{stockId}` | Calculate S/R |
| POST | `/support-resistance/calculate-all` | Batch calculation (async) |

## Stock History & Sync
| Method | Path | Description |
|--------|------|-------------|
| GET | `/stocks/history?symbol=&days=` | Yahoo summary (cached) |
| GET | `/stocks/history/data?symbol=&days=` | Raw Yahoo OHLCV (cached) |
| POST | `/stocks/history/save` | Save Yahoo data to DB |
| POST | `/stocks/sync-history` | Backfill all stocks |
| POST | `/stocks/{id}/sync-history?days=` | Sync single stock |
| POST | `/stocks/portfolio/sync` | Portfolio sync from Yahoo |
| GET | `/stocks/history/summary/all?days=` | All summaries (Yahoo) |
| GET | `/stocks/history/summary/local?days=` | All summaries (local DB) |
| POST | `/stocks/cache/clear` | Clear stockHistory cache |
| GET | `/stocks/test` | Health check |
| GET | `/stocks/sectors` | Distinct sectors |
| GET | `/stocks/health` | DB health |

## Portfolios — PortfolioManagementController (16 endpoints)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/portfolios` | List portfolios |
| POST | `/portfolios` | Create portfolio |
| GET | `/portfolios/{id}` | Portfolio with holdings |
| PUT | `/portfolios/{id}` | Update portfolio |
| DELETE | `/portfolios/{id}` | Delete (not default) |
| GET | `/portfolios/default` | Get/create default |
| GET | `/portfolios/{id}/holdings` | Holdings with P&L |
| POST | `/portfolios/{id}/holdings` | Add holding |
| PATCH | `/portfolios/{pid}/holdings/{hid}` | Update holding |
| DELETE | `/portfolios/{pid}/holdings/{hid}` | Remove holding |
| DELETE | `/portfolios/{pid}/holdings/stock/{sid}` | Remove by stock |
| GET | `/portfolios/{pid}/holdings/stock/{sid}` | Get specific holding |
| POST | `/portfolios/{id}/recalculate` | Recalc P&L |
| GET | `/portfolios/{id}/transactions` | List transactions |
| POST | `/portfolios/{id}/transactions` | Create transaction (BUY/SELL) |
| DELETE | `/portfolios/{id}/transactions/{txId}` | Delete tx |
| GET | `/portfolios/all/holdings/stock/{stockId}` | Cross-portfolio holdings |
| GET | `/portfolios/all/transactions-by-stock` | All tx by stock |

## Portfolio History
| Method | Path | Description |
|--------|------|-------------|
| GET | `/portfolio/history?days=&portfolioId=` | Aggregated value history |
| POST | `/portfolio/backfill` | Create missing snapshots |
| POST | `/portfolio/backfill/force` | Force backfill |
| POST | `/portfolio/backfill/date?date=` | Backfill for specific date |
| GET | `/portfolio/screener?date=` | Snapshot screener |

## Backtest
| Method | Path | Description |
|--------|------|-------------|
| GET | `/backtest/stock/{stockId}?stopLoss=&positionSizePct=` | Run backtest |

## Institutional — 20 endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/institutional/holdings/{stockId}` | Latest shareholding |
| GET | `/institutional/holdings/{stockId}/history` | History |
| GET | `/institutional/holdings/all` | All latest holdings |
| POST | `/institutional/holdings/fetch-all` | Fetch NSE for all |
| POST | `/institutional/holdings/fetch/{stockId}` | Fetch NSE for one |
| POST | `/institutional/xbrl/fetch-all` | XBRL for all |
| POST | `/institutional/xbrl/fetch/{stockId}` | XBRL for one |
| GET | `/institutional/bulk-deals/{stockId}` | Bulk deals for stock |
| GET | `/institutional/bulk-deals/range?from=&to=` | Bulk deals by date range |
| POST | `/institutional/bulk-deals/fetch` | Fetch historical bulk deals |
| POST | `/institutional/bulk-deals/fetch-today` | Today's bulk deals |
| GET | `/institutional/bulk-deals/top-institutional` | Top institutional bulk deals |
| GET | `/institutional/block-deals/{stockId}` | Block deals for stock |
| GET | `/institutional/block-deals/range?from=&to=` | Block deals by range |
| POST | `/institutional/block-deals/fetch` | Fetch historical block deals |
| POST | `/institutional/block-deals/fetch-today` | Today's block deals |
| GET | `/institutional/score/{stockId}` | Institutional score (0–100, 8 components) |
| GET | `/institutional/score/all` | Scores for all |
| GET | `/institutional/screeners/*` | 7 screeners (fii/dii/mf accumulation, strong-buy, price-action-buy, recent-bulk/block, all) |

## Watchlists — 11+1 endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/watchlists` | List all |
| POST | `/watchlists` | Create |
| PUT | `/watchlists/{id}` | Update |
| DELETE | `/watchlists/{id}` | Delete |
| GET | `/watchlists/{id}/detail` | Detail with stocks |
| GET | `/watchlists/{id}/items` | Items |
| POST | `/watchlists/{id}/items` | Add stock |
| DELETE | `/watchlists/{wid}/items/{sid}` | Remove stock |
| POST | `/watchlists/{id}/items/batch` | Batch add |
| GET | `/watchlists/stock/{stockId}` | Watchlists containing stock |
| POST | `/watchlists/{id}/sync-history?days=` | Sync history |
| GET | `/watchlists/opportunities/opportunities` | Opportunities (WatchlistOpportunityController — double segment) |

## Other
| Method | Path | Description |
|--------|------|-------------|
| GET | `/fiidii` | Latest FII/DII data |
| POST | `/fiidii/refresh` | Refresh from NSE |
| GET | `/events?symbol=` | Corporate events |
| POST | `/events/refresh` | Refresh from NSE |
| GET | `/fundamentals/{stockId}` | Fundamental data + sectors/screen |
| POST | `/fundamentals/fetch/{stockId}` | Fetch fundamentals |
| POST | `/fundamentals/fetch-all` | Fetch all |
| POST | `/fundamentals/screen` | Screener |
| GET | `/fundamentals/sectors` | Sectors |
| GET | `/startup-tasks` | List startup tasks |
| POST | `/startup-tasks/run` | Run all |
| POST | `/startup-tasks/run-one/{taskId}` | Run one |

## Caching
All signal endpoints are cached with Caffeine (15-min TTL). Cache keys:
- `'allSignals'` — `getAllSignals()`
- `'buySignals'` — `getBuySignals()`
- `'sellSignals'` — `getSellSignals()`
- `'stock_' + stockId` — `computeSignal(stockId)`

## Error Response Format
```json
{
  "status": "error",
  "message": "Description of the error",
  "data": null,
  "timestamp": "2026-01-01T00:00:00Z",
  "success": false
}
```
