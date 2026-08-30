# Database Design

## Schema Overview
29 entities / 26 repositories — tables across portfolio management, price/indicator storage, institutional tracking, strategy engine, SMC, and signal history. `ddl-auto=update` (Hibernate). MySQL `stockmarket` (PostgreSQL alt via `schema.sql.postgresql`). Last audited 2026-08-21.

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
| Table | Purpose | Notes |
|-------|---------|-------|
| `fundamental_data` | P/E, EPS, ROE, D/E, market cap per stock | FK → stocks |
| `fiidii_data` | Daily FII/DII net flows (date-unique) | standalone |
| `institutional_holdings` | Quarterly FII/DII/MF shareholding % | FK → stocks, UK (stock_id, quarter_end_date) |
| `bulk_deals` | NSE bulk deal transactions | FK → stocks, UK (stock_id, deal_date, client_name, buy_sell, quantity, trade_price) |
| `block_deals` | NSE block deal transactions | FK → stocks, UK (stock_id, deal_date, client_name, buy_sell, quantity, trade_price) |
| `corporate_events` | Upcoming corporate actions (results, dividends) | UK (symbol, event_date, purpose) |
| `support_resistance_levels` | Computed S/R levels per stock | FK → stocks, 11 LevelTypes |
| `watchlists` | Named watchlist groups | UK (name) |
| `watchlist_items` | Stocks within watchlists | UK (watchlist_id, stock_id) |
| `signal_records` | Historical signal + forward returns + accuracy | UK (stock_id, recorded_at) |
| `signal_historical_performance` | Aggregated returns by recommendation type | no explicit unique |
| `portfolios` | Named portfolios | is_default |
| `portfolio_holdings` | Stock ↔ Portfolio qty/avgPrice | UK (portfolio_id, stock_id) |
| `portfolio_transactions` | BUY/SELL ledger (avg-cost) | FK → portfolio, stock |
| `strategy_config` | Strategy priority/enabled | UK (strategyName) |
| `strategy_condition_groups` | DB condition overrides | FK → strategy |
| `strategy_daily_weight` | Daily weight adj per stock/strategy/date | UK (stock_id, strategy_name, signal_date), FK → stocks, signal_type/confidence/contribution/priority/config_version |
| `strategy_stock_result` | Per-stock strategy run results (daily snapshot) | UK (stock_id, strategy_name, snapshot_date), signal_type/confidence/priority/reason/snapshot_date/computed_at |
| `strategy_condition_stats_cache` | Condition stats cache | |
| `score_parameter_config` | Scoring factor toggles | |
| `startup_task_log` | Startup task run status | task_id, run_date, status, completed_at |
| `shadow_signal_records` | A/B signal comparison | UK (stock_id, recorded_at, shadow_version), extended scoring + divergence/gate fields |

## Indexing Notes
- All FK columns are indexed (JPA/Hibernate default)
- Composite UNIQUE keys serve as the primary access path for batch queries
- `portfolio_snapshots` has a composite index on `(portfolio_id, snapshot_date)` for portfolio history queries
