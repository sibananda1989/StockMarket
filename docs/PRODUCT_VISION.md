# Product Vision — StockTracker

## Purpose
StockTracker is a portfolio tracking and technical analysis platform for the Indian stock market (NSE). It helps investors manage multiple portfolios, generate buy/sell signals from 16+ technical indicators, monitor institutional activity (FII/DII/MF), and backtest trading strategies.

## Target User
- Stock **buyer** (not day-trader) who holds positions for weeks to months
- Manages multiple portfolios (e.g., individual + family accounts)
- Wants data-driven BUY signals with risk assessment, not noise

## Core Capabilities
1. **Multi-Portfolio Management** — Separate portfolios with independent holdings
2. **Technical Signal Engine** — 16-indicator composite scoring with buyer bias
3. **Institutional Activity Tracking** — FII/DII/MF holdings, bulk/block deals
4. **Support & Resistance** — Swing highs/lows, pivot points, major historical levels
5. **Backtesting** — Strategy simulation with trailing stop-loss
6. **CSV Import** — Bulk stock addition with upsert semantics

## Architecture
Spring Boot 3.2.5 monolith (Java 17), MySQL 8, static HTML/JS frontend (no SPA framework), Caffeine caching, scheduled batch processing via cron.

## Guiding Principles
- BUY signals must be actionable (not everything is a HOLD)
- SELL warnings should not dominate the dashboard
- Confidence scores should reflect data quality and historical accuracy
- Institutional data is a confirming filter, not the primary signal
