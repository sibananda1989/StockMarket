#!/usr/bin/env python3
"""Fetch fundamental data for a stock symbol using yfinance.

Usage: python3 fetch_fundamentals.py <SYMBOL>

Returns JSON to stdout. Exits with non-zero code on error.
"""
import json
import sys
import yfinance as yf

def fetch(symbol: str) -> dict:
    ticker = yf.Ticker(symbol)
    info = ticker.info
    if not info or info.get('regularMarketPrice') is None and info.get('currentPrice') is None:
        return None
    return {
        'peRatio': info.get('trailingPE'),
        'forwardPe': info.get('forwardPE'),
        'epsTtm': info.get('trailingEps'),
        'epsForward': info.get('forwardEps'),
        'bookValue': info.get('bookValue'),
        'priceToBook': info.get('priceToBook'),
        'dividendYield': info.get('dividendYield'),
        'roe': info.get('returnOnEquity'),
        'debtToEquity': info.get('debtToEquity'),
        'profitMargin': info.get('profitMargins'),
        'marketCap': info.get('marketCap'),
        'revenueTtm': info.get('totalRevenue'),
        'sector': info.get('sector'),
        'industry': info.get('industry'),
        'businessSummary': info.get('longBusinessSummary'),
        'sharesOutstanding': info.get('sharesOutstanding'),
        'beta': info.get('beta'),
        'fiftyTwoWeekHigh': info.get('fiftyTwoWeekHigh'),
        'fiftyTwoWeekLow': info.get('fiftyTwoWeekLow'),
        'name': info.get('longName') or info.get('shortName'),
        'symbol': info.get('symbol')
    }

def main():
    if len(sys.argv) < 2:
        print(json.dumps({'error': 'No symbol provided'}))
        sys.exit(1)

    symbol = sys.argv[1].strip()
    result = fetch(symbol)

    if result is None and '.' not in symbol:
        result = fetch(symbol + '.NS')

    if result is None:
        print(json.dumps({'error': f'No data found for {symbol}'}))
        sys.exit(1)

    print(json.dumps(result))

if __name__ == '__main__':
    main()
