#!/usr/bin/env python3
"""
Reference implementation that exactly mirrors each Java calculator.
Downloads RELIANCE.NS OHLCV data from Yahoo Finance and computes expected values
for RealWorldIndicatorValidationTest.java.

Usage:
  pip install requests   (if not installed)
  python3 compute_expected_values.py

Outputs:
  1. RELIANCE_OHLCV data in Java String[][] format
  2. Expected values for each indicator in Java BigDecimal format
"""

import json
import math
import urllib.request
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP, getcontext

getcontext().prec = 50

# ═══════════════════════════════════════════════════════════════════
# DATA DOWNLOAD
# ═══════════════════════════════════════════════════════════════════

def download_reliance_data():
    """Download RELIANCE.NS daily OHLCV from Yahoo Finance."""
    start = int(datetime(2024, 1, 1).timestamp())
    end = int(datetime(2024, 11, 1).timestamp())
    url = (f"https://query1.finance.yahoo.com/v8/finance/chart/RELIANCE.NS"
           f"?period1={start}&period2={end}&interval=1d&events=history")
    headers = {"User-Agent": "Mozilla/5.0"}
    req = urllib.request.Request(url, headers=headers)
    resp = urllib.request.urlopen(req, timeout=15)
    data = json.loads(resp.read())

    result = data["chart"]["result"][0]
    timestamps = result["timestamp"]
    q = result["indicators"]["quote"][0]

    rows = []
    for i in range(len(timestamps)):
        dt = datetime.utcfromtimestamp(timestamps[i]).strftime("%Y-%m-%d")
        o, h, l, c, v = q["open"][i], q["high"][i], q["low"][i], q["close"][i], q["volume"][i]
        if all(x is not None for x in [o, h, l, c, v]):
            rows.append((dt, o, h, l, c, v))
    return rows


# ═══════════════════════════════════════════════════════════════════
# HELPER: Decimal arithmetic with explicit rounding
# ═══════════════════════════════════════════════════════════════════

def D(val):
    """Convert float/int to Decimal."""
    if isinstance(val, Decimal):
        return val
    return Decimal(str(val))


def round4(val):
    return D(val).quantize(D("0.0001"), rounding=ROUND_HALF_UP)


def round6(val):
    return D(val).quantize(D("0.000001"), rounding=ROUND_HALF_UP)


def round2(val):
    return D(val).quantize(D("0.01"), rounding=ROUND_HALF_UP)


# ═══════════════════════════════════════════════════════════════════
# CALCULATOR MIRRORS (exact Java replicas)
# ═══════════════════════════════════════════════════════════════════

def sma(closes, period):
    """Mirror of SmaCalculator."""
    if len(closes) < period:
        raise ValueError("Insufficient data")
    window = closes[-period:]
    total = sum(D(c) for c in window)
    return round2(total / D(period))


def ema(closes, period):
    """Mirror of EmaCalculator."""
    if len(closes) < period:
        raise ValueError("Insufficient data")
    # Seed = SMA of first `period` values
    seed = sum(D(c) for c in closes[:period]) / D(period)
    k = round4(D(2) / D(period + 1))
    ema_val = round4(seed)  # Initial EMA at scale 4
    for c in closes[period:]:
        close = D(c)
        diff = close - ema_val
        ema_val = diff * k + ema_val  # native BigDecimal precision
    return round2(ema_val)


def rsi(closes, period=14):
    """Mirror of RsiCalculator (Wilder's smoothing)."""
    if len(closes) <= period:
        raise ValueError("Insufficient data")

    # Compute gains/losses
    gains = []
    losses = []
    for i in range(1, len(closes)):
        change = D(closes[i]) - D(closes[i - 1])
        if change > 0:
            gains.append(change)
            losses.append(D(0))
        else:
            gains.append(D(0))
            losses.append(abs(change))

    # Initial average = SMA of first `period` gains/losses
    avg_gain = sum(gains[:period]) / D(period)
    avg_gain = round4(avg_gain)
    avg_loss = sum(losses[:period]) / D(period)
    avg_loss = round4(avg_loss)

    # Wilder's smoothing
    for i in range(period, len(gains)):
        avg_gain = round4((avg_gain * D(period - 1) + gains[i]) / D(period))
        avg_loss = round4((avg_loss * D(period - 1) + losses[i]) / D(period))

    if avg_loss == 0:
        return D(100)

    rs = round4(avg_gain / avg_loss)
    rsi_val = D(100) - round2(D(100) / (D(1) + rs))
    return round2(rsi_val)


def macd_line(closes):
    """Mirror of MacdLineCalculator: EMA12 - EMA26."""
    if len(closes) < 26:
        raise ValueError("Insufficient data")
    ema12 = ema(closes, 12)
    ema26 = ema(closes, 26)
    return (ema12 - ema26).quantize(D("0.0001"), rounding=ROUND_HALF_UP)


def macd_signal(closes):
    """Mirror of MacdSignalCalculator: SMA of last 9 MACD line values."""
    if len(closes) < 35:
        raise ValueError("Insufficient data")
    n = len(closes)
    macd_values = []
    for i in range(n - 9, n):
        sublist = closes[:i + 1]
        if len(sublist) >= 26:
            macd_values.append(macd_line(sublist))

    if len(macd_values) < 9:
        raise ValueError("Not enough MACD values")

    total = sum(macd_values[-9:])
    return round4(total / D(9))


def bollinger_upper(closes, period=20, multiplier=2.0):
    """Mirror of BollingerUpperCalculator (uses double internally)."""
    if len(closes) < period:
        raise ValueError("Insufficient data")
    window = closes[-period:]
    # Double arithmetic (matches Java)
    double_window = [float(c) for c in window]
    sma_val = sum(double_window) / period
    variance = sum((x - sma_val) ** 2 for x in double_window) / period
    stddev = math.sqrt(variance)
    upper = sma_val + multiplier * stddev
    return round2(D(upper))


def bollinger_lower(closes, period=20, multiplier=2.0):
    """Mirror of BollingerLowerCalculator (uses double internally)."""
    if len(closes) < period:
        raise ValueError("Insufficient data")
    window = closes[-period:]
    double_window = [float(c) for c in window]
    sma_val = sum(double_window) / period
    variance = sum((x - sma_val) ** 2 for x in double_window) / period
    stddev = math.sqrt(variance)
    lower = sma_val - multiplier * stddev
    return round2(D(lower))


def stoch_k(highs, lows, closes, period=14):
    """Mirror of StochKCalculator."""
    if len(closes) < period:
        raise ValueError("Insufficient data")
    hh = max(D(h) for h in highs[-period:])
    ll = min(D(l) for l in lows[-period:])
    close = D(closes[-1])
    denom = hh - ll
    if denom == 0:
        return D(0)
    result = round4((close - ll) / denom) * D(100)
    return round2(result)


def stoch_d(highs, lows, closes, k_period=14, d_period=3):
    """Mirror of StochDCalculator: SMA of last d_period %K values."""
    if len(closes) < k_period + d_period - 1:
        raise ValueError("Insufficient data")
    k_values = []
    for i in range(k_period - 1, len(closes)):
        sub_h = highs[i - k_period + 1: i + 1]
        sub_l = lows[i - k_period + 1: i + 1]
        sub_c = closes[i - k_period + 1: i + 1]
        k_val = stoch_k(sub_h, sub_l, sub_c, k_period)
        k_values.append(k_val)
    # SMA of last d_period K values
    window = k_values[-d_period:]
    total = sum(window)
    return round2(total / D(d_period))


def williams_r(highs, lows, closes, period=14):
    """Mirror of WilliamsRCalculator."""
    if len(closes) < period:
        raise ValueError("Insufficient data")
    hh = max(D(h) for h in highs[-period:])
    ll = min(D(l) for l in lows[-period:])
    close = D(closes[-1])
    denom = hh - ll
    if denom == 0:
        return D(0)
    result = round4((hh - close) / denom) * D(-100)
    return round2(result)


def atr(highs, lows, closes, period=14):
    """Mirror of ATRCalculator (SMA-based, NOT Wilder's)."""
    if len(closes) < period:
        raise ValueError("Insufficient data")
    trs = []
    for i in range(len(closes)):
        h = D(highs[i])
        l = D(lows[i])
        c = D(closes[i])
        high_low = h - l
        if i == 0:
            trs.append(high_low)
        else:
            prev_c = D(closes[i - 1])
            high_prev = abs(h - prev_c)
            low_prev = abs(l - prev_c)
            tr = max(high_low, high_prev, low_prev)
            trs.append(tr)
    # SMA of last `period` TRs
    window = trs[-period:]
    total = sum(window)
    return round2(total / D(period))


def cci(highs, lows, closes, period=20):
    """Mirror of CCICalculator."""
    if len(closes) < period:
        raise ValueError("Insufficient data")
    # Typical prices for the window
    tps = []
    for i in range(len(closes) - period, len(closes)):
        tp = round4((D(highs[i]) + D(lows[i]) + D(closes[i])) / D(3))
        tps.append(tp)
    # SMA of typical prices
    sma_tp = round4(sum(tps) / D(period))
    # Mean deviation
    deviations = [abs(tp - sma_tp) for tp in tps]
    mean_dev = round4(sum(deviations) / D(period))
    if mean_dev == 0:
        return D(0)
    today_tp = tps[-1]
    cci_val = round2((today_tp - sma_tp) / (D("0.015") * mean_dev))
    return cci_val


def stoch_rsi(closes, rsi_period=14, stoch_lookback=14):
    """Mirror of StochRsiCalculator."""
    if len(closes) < rsi_period + stoch_lookback + 1:
        raise ValueError("Insufficient data")
    # Compute full RSI series
    rsi_values = []
    gains = []
    losses = []
    for i in range(1, len(closes)):
        change = D(closes[i]) - D(closes[i - 1])
        if change > 0:
            gains.append(change)
            losses.append(D(0))
        else:
            gains.append(D(0))
            losses.append(abs(change))

    if len(gains) < rsi_period:
        raise ValueError("Not enough RSI data")

    avg_gain = round4(sum(gains[:rsi_period]) / D(rsi_period))
    avg_loss = round4(sum(losses[:rsi_period]) / D(rsi_period))

    # First RSI value
    if avg_loss == 0:
        rsi_values.append(D(100))
    else:
        rs = round4(avg_gain / avg_loss)
        rsi_values.append(round2(D(100) - round2(D(100) / (D(1) + rs))))

    for i in range(rsi_period, len(gains)):
        avg_gain = round4((avg_gain * D(rsi_period - 1) + gains[i]) / D(rsi_period))
        avg_loss = round4((avg_loss * D(rsi_period - 1) + losses[i]) / D(rsi_period))
        if avg_loss == 0:
            rsi_values.append(D(100))
        else:
            rs = round4(avg_gain / avg_loss)
            rsi_values.append(round2(D(100) - round2(D(100) / (D(1) + rs))))

    # Stochastic of RSI
    window = rsi_values[-stoch_lookback:]
    highest_rsi = max(window)
    lowest_rsi = min(window)
    current_rsi = rsi_values[-1]

    denom = highest_rsi - lowest_rsi
    if denom == 0:
        return D(50)  # neutral

    stoch_rsi_val = round4((current_rsi - lowest_rsi) / denom) * D(100)
    return round2(stoch_rsi_val)


def adx_system(highs, lows, closes, period=14):
    """Mirror of AdxCalculator.computeAdxResult. Returns (adx, plusDi, minusDi)."""
    n = len(closes)
    if n < 2 * period + 1:
        raise ValueError("Insufficient data")

    # True Range, +DM, -DM
    tr_list = []
    plus_dm_list = []
    minus_dm_list = []

    for i in range(1, n):
        h = D(highs[i])
        l = D(lows[i])
        prev_h = D(highs[i - 1])
        prev_l = D(lows[i - 1])
        prev_c = D(closes[i - 1])

        tr = max(h - l, abs(h - prev_c), abs(l - prev_c))
        tr_list.append(tr)

        up = h - prev_h
        down = prev_l - l

        if up > down and up > 0:
            plus_dm_list.append(up)
        else:
            plus_dm_list.append(D(0))

        if down > up and down > 0:
            minus_dm_list.append(down)
        else:
            minus_dm_list.append(D(0))

    # Wilder's smoothing for TR
    # Initial SMA from index 0 to period-1
    smoothed_tr = sum(tr_list[:period]) / D(period)
    smoothed_tr = round6(smoothed_tr)

    smoothed_plus_dm = sum(plus_dm_list[:period]) / D(period)
    smoothed_plus_dm = round6(smoothed_plus_dm)

    smoothed_minus_dm = sum(minus_dm_list[:period]) / D(period)
    smoothed_minus_dm = round6(smoothed_minus_dm)

    # Store smoothed values for DI calculation
    di_plus_dm = [D(0)] * (period - 1) + [smoothed_plus_dm]
    di_minus_dm = [D(0)] * (period - 1) + [smoothed_minus_dm]
    di_tr = [D(0)] * (period - 1) + [smoothed_tr]

    for i in range(period, len(tr_list)):
        smoothed_tr = smoothed_tr - round6(smoothed_tr / D(period)) + tr_list[i]
        smoothed_plus_dm = smoothed_plus_dm - round6(smoothed_plus_dm / D(period)) + plus_dm_list[i]
        smoothed_minus_dm = smoothed_minus_dm - round6(smoothed_minus_dm / D(period)) + minus_dm_list[i]
        di_plus_dm.append(smoothed_plus_dm)
        di_minus_dm.append(smoothed_minus_dm)
        di_tr.append(smoothed_tr)

    # Calculate +DI and -DI
    plus_di_values = []
    minus_di_values = []
    dx_values = []

    for i in range(len(di_tr)):
        if di_tr[i] == 0:
            plus_di = D(0)
            minus_di = D(0)
        else:
            plus_di = round4((di_plus_dm[i] * D(100)) / di_tr[i])
            minus_di = round4((di_minus_dm[i] * D(100)) / di_tr[i])
        plus_di_values.append(plus_di)
        minus_di_values.append(minus_di)

        di_sum = plus_di + minus_di
        if di_sum == 0:
            dx = D(0)
        else:
            dx = round4(abs(plus_di - minus_di) * D(100) / di_sum)
        dx_values.append(dx)

    # ADX: Wilder's smoothing of DX values
    # First, get non-zero DX values
    # We need at least `period` DX values after the initial smoothing phase
    dx_for_adx = [d for d in dx_values if d != 0 or True]  # keep all

    if len(dx_for_adx) < period:
        adx_val = round2(sum(dx_for_adx) / D(len(dx_for_adx)))
    else:
        # SMA of first `period` DX values
        adx_smooth = sum(dx_for_adx[:period]) / D(period)
        adx_smooth = round4(adx_smooth)

        for i in range(period, len(dx_for_adx)):
            adx_smooth = round4(adx_smooth * (D(period - 1) / D(period)) + dx_for_adx[i] / D(period))

        adx_val = round2(adx_smooth)

    return adx_val, plus_di_values[-1] if plus_di_values else D(0), minus_di_values[-1] if minus_di_values else D(0)


def ultimate_oscillator(highs, lows, closes, period1=7, period2=14, period3=28):
    """Mirror of UltimateOscillatorCalculator."""
    n = len(closes)
    if n < period3 + 1:
        raise ValueError("Insufficient data")

    bps = []
    trs = []
    for i in range(1, n):
        h = D(highs[i])
        l = D(lows[i])
        prev_c = D(closes[i - 1])
        close = D(closes[i])

        min_val = min(l, prev_c)
        max_val = max(h, prev_c)

        bp = close - min_val
        tr = max_val - min_val
        bps.append(bp)
        trs.append(tr)

    # Sums for each period
    sum_bp7 = sum(bps[-period1:])
    sum_tr7 = sum(trs[-period1:])
    sum_bp14 = sum(bps[-period2:])
    sum_tr14 = sum(trs[-period2:])
    sum_bp28 = sum(bps[-period3:])
    sum_tr28 = sum(trs[-period3:])

    if sum_tr7 == 0 or sum_tr14 == 0 or sum_tr28 == 0:
        return D(50)

    avg7 = round6(sum_bp7 / sum_tr7)
    avg14 = round6(sum_bp14 / sum_tr14)
    avg28 = round6(sum_bp28 / sum_tr28)

    uo = round2(((D(4) * avg7 + D(2) * avg14 + avg28) * D(100)) / D(7))
    return uo


def roc(closes, period=12):
    """Mirror of RocCalculator."""
    if len(closes) < period + 1:
        raise ValueError("Insufficient data")
    past_close = D(closes[-(period + 1)])
    current_close = D(closes[-1])
    if past_close == 0:
        return D(0)
    result = round2(round6((current_close - past_close) / past_close) * D(100))
    return result


def obv(closes, volumes):
    """Mirror of ObvCalculator."""
    if len(closes) < 2:
        raise ValueError("Insufficient data")
    obv_val = D(0)
    for i in range(1, len(closes)):
        vol = volumes[i] if volumes[i] is not None else 0
        if D(closes[i]) > D(closes[i - 1]):
            obv_val += D(vol)
        elif D(closes[i]) < D(closes[i - 1]):
            obv_val -= D(vol)
    return obv_val.quantize(D("1"), rounding=ROUND_HALF_UP)


# ═══════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════

def main():
    print("Downloading RELIANCE.NS data...")
    rows = download_reliance_data()
    print(f"Downloaded {len(rows)} data points")

    dates = [r[0] for r in rows]
    opens = [r[1] for r in rows]
    highs = [r[2] for r in rows]
    lows = [r[3] for r in rows]
    closes = [r[4] for r in rows]
    volumes = [int(r[5]) for r in rows]

    print(f"Date range: {dates[0]} to {dates[-1]}")
    print(f"Last close: {closes[-1]}")
    print()

    # Print Java data array
    print("=" * 70)
    print("JAVA DATA ARRAY (paste into test class):")
    print("=" * 70)
    print('    private static final String[][] RAW_OHLCV = {')
    for r in rows:
        print(f'        {{"{r[0]}", "{r[1]:.2f}", "{r[2]:.2f}", "{r[3]:.2f}", "{r[4]:.2f}", "{int(r[5])}"}},')
    print('    };')
    print()

    # Compute expected values
    print("=" * 70)
    print("EXPECTED VALUES:")
    print("=" * 70)

    expected = {}

    try:
        expected["SMA_20"] = sma(closes, 20)
        print(f"    SMA-20:     {expected['SMA_20']}")
    except Exception as e:
        print(f"    SMA-20:     ERROR: {e}")

    try:
        expected["SMA_50"] = sma(closes, 50)
        print(f"    SMA-50:     {expected['SMA_50']}")
    except Exception as e:
        print(f"    SMA-50:     ERROR: {e}")

    try:
        expected["SMA_200"] = sma(closes, 200)
        print(f"    SMA-200:    {expected['SMA_200']}")
    except Exception as e:
        print(f"    SMA-200:    ERROR: {e}")

    try:
        expected["EMA_20"] = ema(closes, 20)
        print(f"    EMA-20:     {expected['EMA_20']}")
    except Exception as e:
        print(f"    EMA-20:     ERROR: {e}")

    try:
        expected["RSI_14"] = rsi(closes, 14)
        print(f"    RSI-14:     {expected['RSI_14']}")
    except Exception as e:
        print(f"    RSI-14:     ERROR: {e}")

    try:
        expected["STOCH_K"] = stoch_k(highs, lows, closes, 14)
        print(f"    Stoch %K:   {expected['STOCH_K']}")
    except Exception as e:
        print(f"    Stoch %K:   ERROR: {e}")

    try:
        expected["STOCH_D"] = stoch_d(highs, lows, closes, 14, 3)
        print(f"    Stoch %D:   {expected['STOCH_D']}")
    except Exception as e:
        print(f"    Stoch %D:   ERROR: {e}")

    try:
        expected["WILLIAMS_R"] = williams_r(highs, lows, closes, 14)
        print(f"    Williams %R: {expected['WILLIAMS_R']}")
    except Exception as e:
        print(f"    Williams %R: ERROR: {e}")

    try:
        expected["ATR_14"] = atr(highs, lows, closes, 14)
        print(f"    ATR-14:     {expected['ATR_14']}")
    except Exception as e:
        print(f"    ATR-14:     ERROR: {e}")

    try:
        expected["CCI_20"] = cci(highs, lows, closes, 20)
        print(f"    CCI-20:     {expected['CCI_20']}")
    except Exception as e:
        print(f"    CCI-20:     ERROR: {e}")

    try:
        expected["STOCH_RSI"] = stoch_rsi(closes, 14, 14)
        print(f"    StochRSI:   {expected['STOCH_RSI']}")
    except Exception as e:
        print(f"    StochRSI:   ERROR: {e}")

    try:
        adx_val, plus_di, minus_di = adx_system(highs, lows, closes, 14)
        expected["ADX"] = adx_val
        expected["PLUS_DI"] = plus_di
        expected["MINUS_DI"] = minus_di
        print(f"    ADX-14:     {expected['ADX']}")
        print(f"    +DI:        {expected['PLUS_DI']}")
        print(f"    -DI:        {expected['MINUS_DI']}")
    except Exception as e:
        print(f"    ADX system: ERROR: {e}")

    try:
        expected["ULTIMATE_OSC"] = ultimate_oscillator(highs, lows, closes)
        print(f"    UO:         {expected['ULTIMATE_OSC']}")
    except Exception as e:
        print(f"    UO:         ERROR: {e}")

    try:
        expected["ROC_12"] = roc(closes, 12)
        print(f"    ROC-12:     {expected['ROC_12']}")
    except Exception as e:
        print(f"    ROC-12:     ERROR: {e}")

    try:
        expected["OBV"] = obv(closes, volumes)
        print(f"    OBV:        {expected['OBV']}")
    except Exception as e:
        print(f"    OBV:        ERROR: {e}")

    try:
        expected["MACD_LINE"] = macd_line(closes)
        print(f"    MACD Line:  {expected['MACD_LINE']}")
    except Exception as e:
        print(f"    MACD Line:  ERROR: {e}")

    try:
        expected["MACD_SIGNAL"] = macd_signal(closes)
        print(f"    MACD Signal:{expected['MACD_SIGNAL']}")
    except Exception as e:
        print(f"    MACD Signal: ERROR: {e}")

    try:
        expected["BOLLINGER_UPPER"] = bollinger_upper(closes, 20, 2.0)
        expected["BOLLINGER_LOWER"] = bollinger_lower(closes, 20, 2.0)
        print(f"    BB Upper:   {expected['BOLLINGER_UPPER']}")
        print(f"    BB Lower:   {expected['BOLLINGER_LOWER']}")
    except Exception as e:
        print(f"    Bollinger:  ERROR: {e}")

    # Print Java assertions
    print()
    print("=" * 70)
    print("JAVA EXPECTED VALUE CONSTANTS (paste into test class):")
    print("=" * 70)
    for key, val in expected.items():
        print(f'    private static final String EXPECTED_{key} = "{val}";')

    # Print OBV separately (scale 0)
    if "OBV" in expected:
        print(f'    // OBV uses scale 0 (integer)')
        print(f'    private static final String EXPECTED_OBV = "{int(expected["OBV"])}";')


if __name__ == "__main__":
    main()
