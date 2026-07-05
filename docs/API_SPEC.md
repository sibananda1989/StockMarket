# API Specification

Base URL: `http://localhost:8080/api`

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

## Signals
| Method | Path | Description |
|--------|------|-------------|
| GET | `/signals?portfolioId=` | All signals (optionally filtered) |
| GET | `/signals/buy` | BUY / STRONG BUY signals (score ≥ 2) |
| GET | `/signals/sell` | SELL / STRONG SELL signals (score ≤ −4) |
| GET | `/signals/{stockId}` | Signal for single stock |
| GET | `/signals/{stockId}/history?days=` | Signal timeline |

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

## Indicators
| Method | Path | Description |
|--------|------|-------------|
| GET | `/indicators/{stockId}` | All latest indicators |
| GET | `/indicators/{stockId}/{type}` | Indicator type history |
| POST | `/indicators/calculate/{stockId}` | Trigger recalculation |

## Support/Resistance
| Method | Path | Description |
|--------|------|-------------|
| GET | `/support-resistance/{stockId}` | Latest S/R levels |
| POST | `/support-resistance/calculate/{stockId}` | Calculate S/R |
| POST | `/support-resistance/calculate-all` | Batch calculation |

## Portfolios
| Method | Path | Description |
|--------|------|-------------|
| GET | `/portfolios` | List portfolios |
| POST | `/portfolios` | Create portfolio |
| GET | `/portfolios/{id}` | Portfolio with holdings |
| GET | `/portfolios/default` | Default portfolio |
| GET | `/portfolios/{id}/holdings` | Holdings list |
| POST | `/portfolios/{id}/holdings` | Add holding |
| PATCH | `/portfolios/{pid}/holdings/{hid}` | Update holding |
| DELETE | `/portfolios/{pid}/holdings/{hid}` | Remove holding |
| GET | `/portfolios/{pid}/holdings/stock/{sid}` | Get specific holding |

## Portfolio History
| Method | Path | Description |
|--------|------|-------------|
| GET | `/portfolio/history?days=&portfolioId=` | Aggregated value history |
| POST | `/portfolio/backfill` | Create missing snapshots |
| GET | `/portfolio/screener?date=` | Snapshot screener |

## Backtest
| Method | Path | Description |
|--------|------|-------------|
| GET | `/backtest/stock/{stockId}?stopLoss=&positionSizePct=` | Run backtest |

## Institutional
| Method | Path | Description |
|--------|------|-------------|
| GET | `/institutional/holdings/{stockId}` | Latest shareholding |
| GET | `/institutional/holdings/{stockId}/history` | Holding history |
| GET | `/institutional/bulk-deals/{stockId}` | Bulk deals for stock |
| GET | `/institutional/block-deals/{stockId}` | Block deals for stock |
| GET | `/institutional/score/{stockId}` | Institutional score (0–100) |
| GET | `/institutional/screeners/*` | Pre-built screeners |

## Other
| Method | Path | Description |
|--------|------|-------------|
| GET | `/fiidii` | Latest FII/DII data |
| GET | `/events?symbol=` | Corporate events |
| GET | `/fundamentals/{stockId}` | Fundamental data |
| POST | `/dhan/sync-holdings` | Sync from Dhan |
| POST | `/dhan/sync-prices` | Sync prices from Dhan |
| GET | `/watchlists` | List watchlists |
| POST | `/watchlists` | Create watchlist |

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
