#!/usr/bin/env python3
"""Fetch fundamental data for multiple stock symbols using yfinance.

Respects Yahoo Finance rate limits (1 req/sec internally via yfinance).
Outputs JSON array of results to stdout, one object per symbol.

Usage:
  python3 fetch_fundamentals_batch.py SYMBOL1 SYMBOL2 ...
  echo "SYMBOL1,SYMBOL2,..." | python3 fetch_fundamentals_batch.py

Each result object includes the input symbol key so the caller can map results.
"""
import json
import sys
import time
import yfinance as yf
import urllib.error
import logging
import os
import io

# Suppress ALL yfinance and urllib3 output to stderr only
logging.getLogger('yfinance').setLevel(logging.CRITICAL)
logging.getLogger('urllib3').setLevel(logging.CRITICAL)
logging.getLogger('requests').setLevel(logging.CRITICAL)

# Redirect stderr to /dev/null for yfinance HTTP errors
class SuppressStderr:
    def __enter__(self):
        self._original_stderr = sys.stderr
        sys.stderr = io.StringIO()
        return self
    def __exit__(self, *args):
        sys.stderr = self._original_stderr


def fetch(symbol: str) -> dict:
    try:
        with SuppressStderr():
            ticker = yf.Ticker(symbol)
            info = ticker.info
        # Check for yfinance error indicators
        if not info:
            return None
        # yfinance returns error info when symbol not found
        if info.get('quoteType') == 'INVALID':
            return None
        # Check if we have minimal valid data
        if (info.get('regularMarketPrice') is None and 
            info.get('currentPrice') is None and
            info.get('previousClose') is None):
            return None
        return {
            '_inputSymbol': symbol,
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
    except urllib.error.HTTPError:
        # HTTP errors should not be printed to stdout
        return None
    except Exception:
        # Catch-all but don't print errors to stdout
        return None


def fetch_with_fallback(symbol: str) -> dict:
    result = fetch(symbol)
    if result is None and '.' not in symbol:
        result = fetch(symbol + '.NS')
    return result


def main():
    if len(sys.argv) > 1:
        symbols = sys.argv[1:]
    elif not sys.stdin.isatty():
        input_data = sys.stdin.read().strip()
        symbols = [s.strip() for s in input_data.replace(',', '\n').split('\n') if s.strip()]
    else:
        print(json.dumps({'error': 'No symbols provided'}))
        sys.exit(1)

    results = []
    for i, symbol in enumerate(symbols):
        if i > 0:
            time.sleep(1.0)
        result = fetch_with_fallback(symbol)
        results.append(result if result else {'_inputSymbol': symbol, 'error': f'No data found for {symbol}'})

    print(json.dumps(results))


if __name__ == '__main__':
    main()
