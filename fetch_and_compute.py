#!/usr/bin/env python3
"""Fetch RELIANCE.NS data and compute all expected indicator values."""
import json, math, urllib.request
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP, getcontext

getcontext().prec = 50

def D(v): return Decimal(str(v)) if not isinstance(v, Decimal) else v
def r4(v): return D(v).quantize(D("0.0001"), rounding=ROUND_HALF_UP)
def r6(v): return D(v).quantize(D("0.000001"), rounding=ROUND_HALF_UP)
def r2(v): return D(v).quantize(D("0.01"), rounding=ROUND_HALF_UP)

# Download
start = int(datetime(2024, 1, 1).timestamp())
end = int(datetime(2024, 11, 1).timestamp())
url = f"https://query1.finance.yahoo.com/v8/finance/chart/RELIANCE.NS?period1={start}&period2={end}&interval=1d"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
resp = urllib.request.urlopen(req, timeout=15)
data = json.loads(resp.read())
r = data["chart"]["result"][0]
ts = r["timestamp"]
q = r["indicators"]["quote"][0]

rows = []
for i in range(len(ts)):
    dt = datetime.utcfromtimestamp(ts[i]).strftime("%Y-%m-%d")
    o,h,l,c,v = q["open"][i],q["high"][i],q["low"][i],q["close"][i],q["volume"][i]
    if all(x is not None for x in [o,h,l,c,v]):
        rows.append((dt, float(o), float(h), float(l), float(c), int(v)))

dates = [r[0] for r in rows]
opens = [r[1] for r in rows]
highs = [r[2] for r in rows]
lows = [r[3] for r in rows]
closes = [r[4] for r in rows]
volumes = [r[5] for r in rows]

out = []
out.append(f"DATA_POINTS={len(rows)}")
out.append(f"DATE_RANGE={dates[0]} to {dates[-1]}")
out.append(f"LAST_CLOSE={closes[-1]}")
out.append("")

# Java array
out.append("JAVA_ARRAY_START")
for dt,o,h,l,c,v in rows:
    out.append(f'        {{"{dt}", "{o:.2f}", "{h:.2f}", "{l:.2f}", "{c:.2f}", "{v}"}},')
out.append("JAVA_ARRAY_END")
out.append("")

# SMA
def sma_calc(cls, p):
    return r2(sum(D(c) for c in cls[-p:]) / D(p))

# EMA
def ema_calc(cls, p):
    seed = r4(sum(D(c) for c in cls[:p]) / D(p))
    k = r4(D(2) / D(p+1))
    e = seed
    for c in cls[p:]:
        e = (D(c) - e) * k + e
    return r2(e)

# RSI
def rsi_calc(cls, p=14):
    gains, losses = [], []
    for i in range(1, len(cls)):
        ch = D(cls[i]) - D(cls[i-1])
        gains.append(ch if ch > 0 else D(0))
        losses.append(abs(ch) if ch < 0 else D(0))
    ag = r4(sum(gains[:p]) / D(p))
    al = r4(sum(losses[:p]) / D(p))
    for i in range(p, len(gains)):
        ag = r4((ag * D(p-1) + gains[i]) / D(p))
        al = r4((al * D(p-1) + losses[i]) / D(p))
    if al == 0: return D(100)
    rs = r4(ag / al)
    return r2(D(100) - r2(D(100) / (D(1) + rs)))

# MACD
def macd_calc(cls):
    return (ema_calc(cls, 12) - ema_calc(cls, 26)).quantize(D("0.0001"), rounding=ROUND_HALF_UP)

def macd_signal_calc(cls):
    vals = [macd_calc(cls[:i+1]) for i in range(len(cls)-9, len(cls)) if len(cls[:i+1]) >= 26]
    return r4(sum(vals[-9:]) / D(9))

# Bollinger
def bb_upper(cls, p=20, m=2.0):
    w = [float(c) for c in cls[-p:]]
    s = sum(w)/p
    sd = math.sqrt(sum((x-s)**2 for x in w)/p)
    return r2(D(s + m*sd))

def bb_lower(cls, p=20, m=2.0):
    w = [float(c) for c in cls[-p:]]
    s = sum(w)/p
    sd = math.sqrt(sum((x-s)**2 for x in w)/p)
    return r2(D(s - m*sd))

# Stoch
def stoch_k(hi, lo, cl, p=14):
    hh = max(D(h) for h in hi[-p:])
    ll = min(D(l) for l in lo[-p:])
    d = hh - ll
    if d == 0: return D(0)
    return r2(r4((D(cl[-1]) - ll) / d) * D(100))

def stoch_d(hi, lo, cl, kp=14, dp=3):
    kvals = [stoch_k(hi[i-kp+1:i+1], lo[i-kp+1:i+1], cl[i-kp+1:i+1], kp) for i in range(kp-1, len(cl))]
    return r2(sum(kvals[-dp:]) / D(dp))

# Williams %R
def williams_r(hi, lo, cl, p=14):
    hh = max(D(h) for h in hi[-p:])
    ll = min(D(l) for l in lo[-p:])
    d = hh - ll
    if d == 0: return D(0)
    return r2(r4((hh - D(cl[-1])) / d) * D(-100))

# ATR
def atr_calc(hi, lo, cl, p=14):
    trs = []
    for i in range(len(cl)):
        h, l, c = D(hi[i]), D(lo[i]), D(cl[i])
        if i == 0:
            trs.append(h - l)
        else:
            pc = D(cl[i-1])
            trs.append(max(h-l, abs(h-pc), abs(l-pc)))
    return r2(sum(trs[-p:]) / D(p))

# CCI
def cci_calc(hi, lo, cl, p=20):
    tps = [r4((D(hi[i])+D(lo[i])+D(cl[i]))/D(3)) for i in range(len(cl)-p, len(cl))]
    sm = r4(sum(tps)/D(p))
    md = r4(sum(abs(t-sm) for t in tps)/D(p))
    if md == 0: return D(0)
    return r2((tps[-1] - sm) / (D("0.015") * md))

# StochRSI
def stoch_rsi_calc(cls, rp=14, sl=14):
    gains, losses = [], []
    for i in range(1, len(cls)):
        ch = D(cls[i]) - D(cls[i-1])
        gains.append(ch if ch > 0 else D(0))
        losses.append(abs(ch) if ch < 0 else D(0))
    ag = r4(sum(gains[:rp])/D(rp))
    al = r4(sum(losses[:rp])/D(rp))
    rsi_vals = []
    for i in range(rp, len(gains)):
        ag = r4((ag*D(rp-1)+gains[i])/D(rp))
        al = r4((al*D(rp-1)+losses[i])/D(rp))
        if al == 0: rsi_vals.append(D(100))
        else:
            rs = r4(ag/al)
            rsi_vals.append(r2(D(100)-r2(D(100)/(D(1)+rs))))
    w = rsi_vals[-sl:]
    hi_r, lo_r = max(w), min(w)
    d = hi_r - lo_r
    if d == 0: return D(50)
    return r2(r4((rsi_vals[-1]-lo_r)/d)*D(100))

# ADX system
def adx_calc(hi, lo, cl, p=14):
    n = len(cl)
    trl, pdm, mdm = [], [], []
    for i in range(1, n):
        h, l = D(hi[i]), D(lo[i])
        ph, pl, pc = D(hi[i-1]), D(lo[i-1]), D(cl[i-1])
        trl.append(max(h-l, abs(h-pc), abs(l-pc)))
        up, dn = h-ph, pl-l
        pdm.append(up if up>dn and up>0 else D(0))
        mdm.append(dn if dn>up and dn>0 else D(0))
    st = r6(sum(trl[:p])/D(p))
    spdm = r6(sum(pdm[:p])/D(p))
    smdm = r6(sum(mdm[:p])/D(p))
    pdi_v, mdi_v, dx_v = [], [], []
    for i in range(p, len(trl)):
        st = st - r6(st/D(p)) + trl[i]
        spdm = spdm - r6(spdm/D(p)) + pdm[i]
        smdm = smdm - r6(smdm/D(p)) + mdm[i]
        pdi = r4(spdm*D(100)/st) if st!=0 else D(0)
        mdi = r4(smdm*D(100)/st) if st!=0 else D(0)
        pdi_v.append(pdi)
        mdi_v.append(mdi)
        s = pdi+mdi
        dx_v.append(r4(abs(pdi-mdi)*D(100)/s) if s!=0 else D(0))
    if len(dx_v) < p:
        adx = r2(sum(dx_v)/D(len(dx_v)))
    else:
        a = r4(sum(dx_v[:p])/D(p))
        for i in range(p, len(dx_v)):
            a = r4(a*(D(p-1)/D(p)) + dx_v[i]/D(p))
        adx = r2(a)
    return adx, pdi_v[-1], mdi_v[-1]

# UO
def uo_calc(hi, lo, cl, p1=7, p2=14, p3=28):
    bps, trs = [], []
    for i in range(1, len(cl)):
        h, l, pc, c = D(hi[i]), D(lo[i]), D(cl[i-1]), D(cl[i])
        mn, mx = min(l,pc), max(h,pc)
        bps.append(c-mn)
        trs.append(mx-mn)
    s7, t7 = sum(bps[-p1:]), sum(trs[-p1:])
    s14, t14 = sum(bps[-p2:]), sum(trs[-p2:])
    s28, t28 = sum(bps[-p3:]), sum(trs[-p3:])
    if t7==0 or t14==0 or t28==0: return D(50)
    return r2(((D(4)*r6(s7/t7)+D(2)*r6(s14/t14)+r6(s28/t28))*D(100))/D(7))

# ROC
def roc_calc(cls, p=12):
    pc = D(cls[-(p+1)])
    if pc == 0: return D(0)
    return r2(r6((D(cls[-1])-pc)/pc)*D(100))

# OBV
def obv_calc(cls, vols):
    o = D(0)
    for i in range(1, len(cls)):
        v = vols[i] if vols[i] is not None else 0
        if D(cls[i]) > D(cls[i-1]): o += D(v)
        elif D(cls[i]) < D(cls[i-1]): o -= D(v)
    return o.quantize(D("1"), rounding=ROUND_HALF_UP)

# Compute all
results = {}
results["SMA_20"] = str(sma_calc(closes, 20))
results["SMA_50"] = str(sma_calc(closes, 50))
results["SMA_200"] = str(sma_calc(closes, 200))
results["EMA_20"] = str(ema_calc(closes, 20))
results["RSI_14"] = str(rsi_calc(closes, 14))
results["STOCH_K"] = str(stoch_k(highs, lows, closes, 14))
results["STOCH_D"] = str(stoch_d(highs, lows, closes, 14, 3))
results["WILLIAMS_R"] = str(williams_r(highs, lows, closes, 14))
results["ATR_14"] = str(atr_calc(highs, lows, closes, 14))
results["CCI_20"] = str(cci_calc(highs, lows, closes, 20))
results["STOCH_RSI"] = str(stoch_rsi_calc(closes, 14, 14))
adx_v, pdi_v, mdi_v = adx_calc(highs, lows, closes, 14)
results["ADX"] = str(adx_v)
results["PLUS_DI"] = str(pdi_v)
results["MINUS_DI"] = str(mdi_v)
results["UO"] = str(uo_calc(highs, lows, closes))
results["ROC_12"] = str(roc_calc(closes, 12))
results["OBV"] = str(int(obv_calc(closes, volumes)))
results["MACD_LINE"] = str(macd_calc(closes))
results["MACD_SIGNAL"] = str(macd_signal_calc(closes))
results["BB_UPPER"] = str(bb_upper(closes, 20, 2.0))
results["BB_LOWER"] = str(bb_lower(closes, 20, 2.0))

# Write output
with open("/tmp/expected_values.txt", "w") as f:
    f.write(f"DATA_POINTS={len(rows)}\n")
    f.write(f"DATE_RANGE={dates[0]} to {dates[-1]}\n")
    f.write(f"LAST_CLOSE={closes[-1]}\n\n")
    f.write("JAVA_ARRAY_START\n")
    for dt,o,h,l,c,v in rows:
        f.write(f'        {{"{dt}", "{o:.2f}", "{h:.2f}", "{l:.2f}", "{c:.2f}", "{v}"}},\n')
    f.write("JAVA_ARRAY_END\n\n")
    for k,v in results.items():
        f.write(f"EXPECTED_{k}={v}\n")

print("Done! Output written to /tmp/expected_values.txt")
for k,v in results.items():
    print(f"  {k}: {v}")
