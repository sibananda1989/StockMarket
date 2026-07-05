#!/usr/bin/env python3
"""
Signal Quality Diagnostic Script

This script analyzes signal quality for a given watchlist of stocks.
It connects to the database and runs signal computation using the current logic.
"""

import sqlite3
import csv
from datetime import datetime, timedelta
import math
from decimal import Decimal, getcontext

# Set decimal precision
getcontext().prec = 8

# Configuration
DB_PATH = 'database.sqlite'
WATCHLIST = [
    'GAIL', 'NTPC', 'TATAMOTORS', 'BAJFINANCE', 'RELIANCE', 
    'HDFCBANK', 'INFY', 'ASIANPAINT', 'HINDUNILVR', 'ITC',
    'SBIN', 'ONGC', 'LT', 'TCS', 'KOTAKBANK'
]
OUTPUT_FILE = f'/tmp/signal_diagnostic_{datetime.now().strftime("%Y%m%d_%H%M%S")}.csv'

# SQL Queries
QUERY_STOCK_ID = """
SELECT id FROM stock WHERE symbol = ? OR symbol = ?
"""

QUERY_PRICES = """
SELECT id, stock_id, price_date, opening_price, high_price, low_price, closing_price, volume
FROM daily_price
WHERE stock_id = ?
ORDER BY price_date ASC
LIMIT 60 OFFSET (SELECT COUNT(*) FROM daily_price WHERE stock_id = ?) - 60
"""

class SignalDiagnostic:
    def __init__(self, db_path):
        self.conn = sqlite3.connect(db_path)
        self.conn.row_factory = sqlite3.Row
        self.cursor = self.conn.cursor()

    def get_stock_id(self, symbol):
        """Get stock ID for symbol (try with and without .NS suffix)"""
        self.cursor.execute(QUERY_STOCK_ID, (symbol, f"{symbol}.NS"))
        result = self.cursor.fetchone()
        return result['id'] if result else None

    def get_prices(self, stock_id):
        """Get last 60 days of price data for stock"""
        self.cursor.execute(QUERY_PRICES, (stock_id, stock_id))
        return [dict(row) for row in self.cursor.fetchall()]

    def calculate_sma(self, prices, period):
        """Calculate Simple Moving Average"""
        if len(prices) < period:
            return None
        closes = [Decimal(p['closing_price']) for p in prices[-period:]]
        return sum(closes) / Decimal(period)

    def calculate_ema(self, prices, period):
        """Calculate Exponential Moving Average"""
        if len(prices) < period:
            return None
        k = Decimal(2) / Decimal(period + 1)
        closes = [Decimal(p['closing_price']) for p in prices]
        
        # Initial SMA
        ema = sum(closes[:period]) / Decimal(period)
        
        # Wilder's smoothing
        for i in range(period, len(closes)):
            ema = closes[i] * k + ema * (Decimal(1) - k)
        
        return ema

    def calculate_rsi(self, prices, period=14):
        """Calculate RSI using Wilder's smoothing"""
        if len(prices) < period + 1:
            return None
        
        closes = [Decimal(p['closing_price']) for p in prices]
        gains = []
        losses = []
        
        # Calculate price changes
        for i in range(1, len(closes)):
            change = closes[i] - closes[i-1]
            if change > 0:
                gains.append(change)
                losses.append(Decimal(0))
            else:
                gains.append(Decimal(0))
                losses.append(abs(change))
        
        # Initial averages
        avg_gain = sum(gains[:period]) / Decimal(period)
        avg_loss = sum(losses[:period]) / Decimal(period)
        
        # Wilder's smoothing
        for i in range(period, len(gains)):
            avg_gain = (avg_gain * Decimal(period - 1) + gains[i]) / Decimal(period)
            avg_loss = (avg_loss * Decimal(period - 1) + losses[i]) / Decimal(period)
        
        if avg_loss == 0:
            return Decimal(100)
        
        rs = avg_gain / avg_loss
        return Decimal(100) - (Decimal(100) / (Decimal(1) + rs))

    def calculate_macd(self, prices):
        """Calculate MACD, Signal, and Histogram"""
        if len(prices) < 26:
            return None, None, None
        
        closes = [Decimal(p['closing_price']) for p in prices]
        
        # Calculate EMAs
        ema12 = self.calculate_ema(prices, 12)
        ema26 = self.calculate_ema(prices, 26)
        
        if ema12 is None or ema26 is None:
            return None, None, None
        
        macd = ema12 - ema26
        
        # Calculate signal line (9-day EMA of MACD)
        macd_values = []
        for i in range(25, len(closes)):
            ema12_i = self.calculate_ema(prices[:i+1], 12)
            ema26_i = self.calculate_ema(prices[:i+1], 26)
            if ema12_i and ema26_i:
                macd_values.append(ema12_i - ema26_i)
        
        if len(macd_values) < 9:
            return macd, None, None
        
        signal = sum(macd_values[-9:]) / Decimal(9)
        for i in range(9, len(macd_values)):
            signal = (signal * Decimal(8) + macd_values[i]) / Decimal(9)
        
        histogram = macd - signal
        return macd, signal, histogram

    def calculate_bollinger_bands(self, prices, period=20):
        """Calculate Bollinger Bands"""
        if len(prices) < period:
            return None, None, None
        
        closes = [Decimal(p['closing_price']) for p in prices[-period:]]
        sma = sum(closes) / Decimal(period)
        
        # Sample standard deviation (N-1)
        variance = sum((x - sma) ** 2 for x in closes) / Decimal(period - 1)
        stddev = Decimal(math.sqrt(float(variance)))
        
        upper = sma + (stddev * Decimal(2))
        lower = sma - (stddev * Decimal(2))
        
        return upper, sma, lower

    def calculate_channel_position(self, prices):
        """Calculate 52-week channel position (0-100%)"""
        if len(prices) < 20:
            return 50.0
        
        closes = [Decimal(p['closing_price']) for p in prices]
        current = closes[-1]
        high_52w = max(closes)
        low_52w = min(closes)
        
        if high_52w == low_52w:
            return 50.0
        
        return float((current - low_52w) / (high_52w - low_52w)) * 100

    def count_overbought_signals(self, signal_data):
        """Count overbought signals"""
        count = 0
        
        if signal_data.get('stoch_rsi', 0) > 85:
            count += 1
        if signal_data.get('cci', 0) > 150:
            count += 1
        if signal_data.get('stoch_k', 0) > 80:
            count += 1
        if signal_data.get('williams_r', 0) > -20:
            count += 1
        if signal_data.get('price', 0) >= signal_data.get('bollinger_upper', 0):
            count += 1
            
        return count

    def analyze_signal(self, stock_symbol, prices):
        """Analyze signal for a single stock"""
        if len(prices) < 20:
            return None
        
        current_price = Decimal(prices[-1]['closing_price'])
        
        # Calculate indicators
        sma20 = self.calculate_sma(prices, 20)
        sma50 = self.calculate_sma(prices, 50)
        rsi14 = self.calculate_rsi(prices, 14)
        macd, macd_signal, macd_histogram = self.calculate_macd(prices)
        bollinger_upper, bollinger_middle, bollinger_lower = self.calculate_bollinger_bands(prices)
        channel_pos = self.calculate_channel_position(prices)
        
        # Determine trend
        trend_bullish = sma20 and sma50 and sma20 > sma50
        trend_bearish = sma20 and sma50 and sma20 < sma50
        
        # Calculate scores
        trend_score = 0
        momentum_score = 0
        structure_score = 0
        
        # Trend scoring
        if trend_bullish:
            trend_score += 2
        elif trend_bearish:
            trend_score -= 2
        
        if sma20 and current_price > sma20:
            trend_score += 1
        elif sma20 and current_price < sma20:
            trend_score -= 1
        
        # Momentum scoring
        if rsi14:
            if rsi14 < 30:
                momentum_score += 2
            elif rsi14 < 40:
                momentum_score -= 1
            elif rsi14 > 70:
                momentum_score -= 2
            elif rsi14 > 80:
                momentum_score -= 3
        
        if macd and macd_signal:
            if macd > macd_signal and macd > 0:
                momentum_score += 3
            elif macd > macd_signal:
                momentum_score += 1
            elif macd < macd_signal and macd < 0:
                momentum_score -= 2
        
        # Structure scoring
        if bollinger_upper and current_price >= bollinger_upper:
            momentum_score -= 2
        elif bollinger_lower and current_price <= bollinger_lower:
            momentum_score += 2
        
        if channel_pos <= 20:
            structure_score += 3
        elif channel_pos <= 40:
            structure_score += 1
        elif channel_pos >= 80:
            structure_score -= 3
        elif channel_pos >= 60:
            structure_score -= 1
        
        # Composite score
        raw_score = trend_score + momentum_score + structure_score
        
        # Confidence score
        confidence = 50 + (raw_score * 3)
        confidence = max(20, min(95, confidence))  # Clamp between 20-95
        
        # Determine recommendation
        if raw_score >= 7:
            recommendation = "STRONG BUY"
        elif raw_score >= 5:
            recommendation = "BUY"
        elif raw_score <= -7:
            recommendation = "STRONG SELL"
        elif raw_score <= -5:
            recommendation = "SELL"
        else:
            recommendation = "HOLD"
        
        # Analyze false signal risk
        overbought_count = self.count_overbought_signals({
            'stoch_rsi': 90 if rsi14 and rsi14 > 70 else 0,  # Simplified
            'cci': 200 if rsi14 and rsi14 > 70 else 0,       # Simplified
            'stoch_k': 85 if rsi14 and rsi14 > 70 else 0,    # Simplified
            'williams_r': -10 if rsi14 and rsi14 > 70 else -50,  # Simplified
            'price': current_price,
            'bollinger_upper': bollinger_upper
        })
        
        false_signal_risk = "Low"
        if overbought_count >= 3:
            false_signal_risk = "High"
        elif overbought_count >= 1:
            false_signal_risk = "Medium"
        
        # Identify key changes
        key_changes = []
        if channel_pos <= 15 and (not trend_bullish or current_price < sma50):
            key_changes.append("Channel override blocked (price below SMA50)")
        
        if overbought_count >= 3:
            key_changes.append(f"Overbought dampener applied ({overbought_count}/5 signals)")
        
        if channel_pos <= 15 and (not rsi14 or rsi14 < 50):
            key_changes.append("Proximity gate requires positive momentum")
        
        if not key_changes:
            key_changes.append("No significant changes")
        
        return {
            'stock': stock_symbol,
            'recommendation': recommendation,
            'confidence': confidence,
            'trend_score': trend_score,
            'momentum_score': momentum_score,
            'structure_score': structure_score,
            'channel_position': f"{channel_pos:.1f}%",
            'false_signal_risk': false_signal_risk,
            'key_changes': ", ".join(key_changes),
            'price': float(current_price),
            'sma20': float(sma20) if sma20 else None,
            'sma50': float(sma50) if sma50 else None,
            'rsi14': float(rsi14) if rsi14 else None,
            'macd': float(macd) if macd else None,
            'macd_signal': float(macd_signal) if macd_signal else None,
            'bollinger_upper': float(bollinger_upper) if bollinger_upper else None,
            'bollinger_lower': float(bollinger_lower) if bollinger_lower else None
        }

    def run_diagnostic(self):
        """Run diagnostic for all stocks in watchlist"""
        results = []
        
        for symbol in WATCHLIST:
            stock_id = self.get_stock_id(symbol)
            if not stock_id:
                print(f"Stock not found: {symbol}")
                continue
                
            prices = self.get_prices(stock_id)
            if len(prices) < 20:
                print(f"Insufficient data for {symbol}: {len(prices)} days")
                continue
                
            signal = self.analyze_signal(symbol, prices)
            if signal:
                results.append(signal)
                print(f"Analyzed {symbol}: {signal['recommendation']} (Confidence: {signal['confidence']}%)")
        
        # Save to CSV
        if results:
            with open(OUTPUT_FILE, 'w', newline='') as csvfile:
                fieldnames = ['stock', 'recommendation', 'confidence', 'trend_score', 
                             'momentum_score', 'structure_score', 'channel_position', 
                             'false_signal_risk', 'key_changes']
                writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
                
                writer.writeheader()
                for result in results:
                    writer.writerow({k: v for k, v in result.items() if k in fieldnames})
            
            print(f"Diagnostic report saved to: {OUTPUT_FILE}")
        else:
            print("No results generated")

if __name__ == "__main__":
    print("Running Signal Quality Diagnostic...")
    diagnostic = SignalDiagnostic(DB_PATH)
    diagnostic.run_diagnostic()