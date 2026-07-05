# Database Design

## Schema Overview
16 tables across portfolio management, price/indicator storage, institutional tracking, and signal history.

## Tables

### `stocks`
Primary registry for all tracked stocks. Core identity columns plus legacy portfolio fields (migrated to `portfolio_holdings`).
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK, auto-increment |
| symbol | VARCHAR(20) | UNIQUE, NOT NULL |
| name | VARCHAR(255) | NOT NULL |
| sector | VARCHAR(100) | |
| yahoo_symbol | VARCHAR(20) | |
| last_traded_price | DECIMAL(10,2) | |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### `portfolios`
Named collections of holdings. One portfolio is marked `is_default`.
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| name | VARCHAR(255) | NOT NULL |
| is_default | BOOLEAN | NOT NULL |
| description | VARCHAR(500) | |

### `portfolio_holdings`
Links stocks to portfolios with quantity and average price.
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| portfolio_id | BIGINT | FK → portfolios |
| stock_id | BIGINT | FK → stocks |
| quantity | INT | NOT NULL |
| avg_price | DECIMAL(10,2) | |
| UNIQUE | (portfolio_id, stock_id) | |

### `daily_prices`
OHLCV data per stock per day.
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| stock_id | BIGINT | FK → stocks |
| closing_price | DECIMAL(10,2) | NOT NULL |
| opening_price | DECIMAL(10,2) | |
| high_price | DECIMAL(10,2) | |
| low_price | DECIMAL(10,2) | |
| volume | BIGINT | |
| price_date | DATE | NOT NULL |
| UNIQUE | (stock_id, price_date) | |

### `technical_indicators`
All computed indicator values. `indicator_type` stores the enum name as a string.
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| stock_id | BIGINT | FK → stocks |
| indicator_type | VARCHAR(50) | NOT NULL |
| value | DECIMAL(18,6) | NOT NULL |
| calculation_date | DATE | NOT NULL |
| UNIQUE | (stock_id, indicator_type, calculation_date) | |

### `portfolio_snapshots`
Daily P&L snapshots per holding per portfolio.
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| portfolio_id | BIGINT | FK → portfolios |
| stock_id | BIGINT | FK → stocks |
| snapshot_date | DATE | NOT NULL |
| quantity, avg_price, last_traded_price | DECIMAL | |
| investment, current_value, pnl, pnl_percent | DECIMAL | |
| UNIQUE | (portfolio_id, stock_id, snapshot_date) | |

### `signal_records`
Historical signal recommendations with forward return tracking.
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK |
| stock_id | BIGINT | NOT NULL |
| recorded_at | DATE | NOT NULL |
| recommendation | VARCHAR(20) | NOT NULL |
| composite_score | INT | NOT NULL |
| confidence_score | BIGINT | |
| forward_return_5d/10d/20d | DECIMAL(10,4) | |
| was_accurate_5d/10d/20d | BOOLEAN | |
| UNIQUE | (stock_id, recorded_at) | |

### Other Tables
| Table | Purpose |
|-------|---------|
| `fundamental_data` | P/E, EPS, ROE, D/E, market cap per stock |
| `fiidii_data` | Daily FII/DII net flows (date-unique) |
| `institutional_holdings` | Quarterly FII/DII/MF shareholding percentages |
| `bulk_deals` | NSE bulk deal transactions |
| `block_deals` | NSE block deal transactions |
| `corporate_events` | Upcoming corporate actions (results, dividends) |
| `support_resistance_levels` | Computed S/R levels per stock |
| `watchlists` | Named watchlist groups |
| `watchlist_items` | Stocks within watchlists |
| `rsi_values` | Deprecated — legacy RSI storage |
| `signal_historical_performance` | Aggregated returns by recommendation type |

## Indexing Notes
- All FK columns are indexed (JPA/Hibernate default)
- Composite UNIQUE keys serve as the primary access path for batch queries
- `portfolio_snapshots` has a composite index on `(portfolio_id, snapshot_date)` for portfolio history queries
