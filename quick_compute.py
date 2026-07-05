#!/usr/bin/env python3
"""Compute expected values from embedded RELIANCE.NS data."""
import json, math
from decimal import Decimal, ROUND_HALF_UP

def D(v): return Decimal(str(v))
def r4(v): return D(v).quantize(D("0.0001"), rounding=ROUND_HALF_UP)
def r6(v): return D(v).quantize(D("0.000001"), rounding=ROUND_HALF_UP)
def r2(v): return D(v).quantize(D("0.01"), rounding=ROUND_HALF_UP)

# Read already-downloaded data
with open('/tmp/reliance.json') as f:
    data = json.load(f)
r = data["chart"]["result"][0]
ts = r["timestamp"]
q = r["indicators"]["quote"][0]
from datetime import datetime
rows = []
for i in range(len(ts)):
    dt = datetime.utcfromtimestamp(ts[i]).strftime("%Y-%m-%d")
    o,h,l,c,v = q["open"][i],q["high"][i],q["low"][i],q["close"][i],q["volume"][i]
    if all(x is not None for x in [o,h,l,c,v]):
        rows.append((dt, float(o), float(h), float(l), float(c), int(v)))

closes = [r[4] for r in rows]
highs = [r[2] for r in rows]
lows = [r[3] for r in rows]
volumes = [r[5] for r in rows]
print(f"Points: {len(rows)}, Last: {rows[-1][0]}, Close: {closes[-1]}")

def sma_calc(cls, p): return r2(sum(D(c) for c in cls[-p:]) / D(p))
def ema_calc(cls, p):
    seed = r4(sum(D(c) for c in cls[:p]) / D(p))
    k = r4(D(2) / D(p+1))
    e = seed
    for c in cls[p:]: e = (D(c) - e) * k + e
    return r2(e)
def rsi_calc(cls, p=14):
    g, l = [], []
    for i in range(1, len(cls)):
        ch = D(cls[i]) - D(cls[i-1])
        g.append(ch if ch > 0 else D(0)); l.append(abs(ch) if ch < 0 else D(0))
    ag = r4(sum(g[:p])/D(p)); al = r4(sum(l[:p])/D(p))
    for i in range(p, len(g)):
        ag = r4((ag*D(p-1)+g[i])/D(p)); al = r4((al*D(p-1)+l[i])/D(p))
    if al == 0: return D(100)
    rs = r4(ag/al); return r2(D(100)-r2(D(100)/(D(1)+rs)))
def macd_calc(cls): return (ema_calc(cls,12)-ema_calc(cls,26)).quantize(D("0.0001"),rounding=ROUND_HALF_UP)
def macd_signal_calc(cls):
    vals = [macd_calc(cls[:i+1]) for i in range(len(cls)-9,len(cls)) if len(cls[:i+1])>=26]
    return r4(sum(vals[-9:])/D(9))
def bb_upper(cls, p=20, m=2.0):
    w=[float(c) for c in cls[-p:]]; s=sum(w)/p; sd=math.sqrt(sum((x-s)**2 for x in w)/p)
    return r2(D(s+m*sd))
def bb_lower(cls, p=20, m=2.0):
    w=[float(c) for c in cls[-p:]]; s=sum(w)/p; sd=math.sqrt(sum((x-s)**2 for x in w)/p)
    return r2(D(s-m*sd))
def stoch_k(hi,lo,cl,p=14):
    hh=max(D(h) for h in hi[-p:]); ll=min(D(l) for l in lo[-p:]); d=hh-ll
    if d==0: return D(0); return r2(r4((D(cl[-1])-ll)/d)*D(100))
def stoch_d(hi,lo,cl,kp=14,dp=3):
    kvals=[stoch_k(hi[i-kp+1:i+1],lo[i-kp+1:i+1],cl[i-kp+1:i+1],kp) for i in range(kp-1,len(cl))]
    return r2(sum(kvals[-dp:])/D(dp))
def williams_r(hi,lo,cl,p=14):
    hh=max(D(h) for h in hi[-p:]); ll=min(D(l) for l in lo[-p:]); d=hh-ll
    if d==0: return D(0); return r2(r4((hh-D(cl[-1]))/d)*D(-100))
def atr_calc(hi,lo,cl,p=14):
    trs=[]
    for i in range(len(cl)):
        h,l,c=D(hi[i]),D(lo[i]),D(cl[i])
        if i==0: trs.append(h-l)
        else: pc=D(cl[i-1]); trs.append(max(h-l,abs(h-pc),abs(l-pc)))
    return r2(sum(trs[-p:])/D(p))
def cci_calc(hi,lo,cl,p=20):
    tps=[r4((D(hi[i])+D(lo[i])+D(cl[i]))/D(3)) for i in range(len(cl)-p,len(cl))]
    sm=r4(sum(tps)/D(p)); md=r4(sum(abs(t-sm) for t in tps)/D(p))
    if md==0: return D(0); return r2((tps[-1]-sm)/(D("0.015")*md))
def stoch_rsi_calc(cls,rp=14,sl=14):
    g,l=[],[]
    for i in range(1,len(cls)):
        ch=D(cls[i])-D(cls[i-1]); g.append(ch if ch>0 else D(0)); l.append(abs(ch) if ch<0 else D(0))
    ag=r4(sum(g[:rp])/D(rp)); al=r4(sum(l[:rp])/D(rp))
    rv=[]
    for i in range(rp,len(g)):
        ag=r4((ag*D(rp-1)+g[i])/D(rp)); al=r4((al*D(rp-1)+l[i])/D(rp))
        if al==0: rv.append(D(100))
        else: rs=r4(ag/al); rv.append(r2(D(100)-r2(D(100)/(D(1)+rs))))
    w=rv[-sl:]; hi_r,lo_r=max(w),min(w); d=hi_r-lo_r
    if d==0: return D(50); return r2(r4((rv[-1]-lo_r)/d)*D(100))
def adx_calc(hi,lo,cl,p=14):
    n=len(cl); trl,pdm,mdm=[],[],[]
    for i in range(1,n):
        h,l=D(hi[i]),D(lo[i]); ph,pl,pc=D(hi[i-1]),D(lo[i-1]),D(cl[i-1])
        trl.append(max(h-l,abs(h-pc),abs(l-pc)))
        up,dn=h-ph,pl-l
        pdm.append(up if up>dn and up>0 else D(0))
        mdm.append(dn if dn>up and dn>0 else D(0))
    st=r6(sum(trl[:p])/D(p)); spdm=r6(sum(pdm[:p])/D(p)); smdm=r6(sum(mdm[:p])/D(p))
    pdi_v,mdi_v,dx_v=[],[],[]
    for i in range(p,len(trl)):
        st=st-r6(st/D(p))+trl[i]; spdm=spdm-r6(spdm/D(p))+pdm[i]; smdm=smdm-r6(smdm/D(p))+mdm[i]
        pdi=r4(spdm*D(100)/st) if st!=0 else D(0); mdi=r4(smdm*D(100)/st) if st!=0 else D(0)
        pdi_v.append(pdi); mdi_v.append(mdi)
        s=pdi+mdi; dx_v.append(r4(abs(pdi-mdi)*D(100)/s) if s!=0 else D(0))
    if len(dx_v)<p: adx=r2(sum(dx_v)/D(len(dx_v)))
    else:
        a=r4(sum(dx_v[:p])/D(p))
        for i in range(p,len(dx_v)): a=r4(a*(D(p-1)/D(p))+dx_v[i]/D(p))
        adx=r2(a)
    return adx,pdi_v[-1],mdi_v[-1]
def uo_calc(hi,lo,cl,p1=7,p2=14,p3=28):
    bps,trs=[],[]
    for i in range(1,len(cl)):
        h,l,pc,c=D(hi[i]),D(lo[i]),D(cl[i-1]),D(cl[i]); mn,mx=min(l,pc),max(h,pc)
        bps.append(c-mn); trs.append(mx-mn)
    s7,t7=sum(bps[-p1:]),sum(trs[-p1:]); s14,t14=sum(bps[-p2:]),sum(trs[-p2:]); s28,t28=sum(bps[-p3:]),sum(trs[-p3:])
    if t7==0 or t14==0 or t28==0: return D(50)
    return r2(((D(4)*r6(s7/t7)+D(2)*r6(s14/t14)+r6(s28/t28))*D(100))/D(7))
def roc_calc(cls,p=12):
    pc=D(cls[-(p+1)]);
    if pc==0: return D(0)
    return r2(r6((D(cls[-1])-pc)/pc)*D(100))
def obv_calc(cls,vols):
    o=D(0)
    for i in range(1,len(cls)):
        v=vols[i] if vols[i] is not None else 0
        if D(cls[i])>D(cls[i-1]): o+=D(v)
        elif D(cls[i])<D(cls[i-1]): o-=D(v)
    return o.quantize(D("1"),rounding=ROUND_HALF_UP)

print(f"SMA_20={sma_calc(closes,20)}")
print(f"SMA_50={sma_calc(closes,50)}")
print(f"SMA_200={sma_calc(closes,200)}")
print(f"EMA_20={ema_calc(closes,20)}")
print(f"RSI_14={rsi_calc(closes,14)}")
print(f"STOCH_K={stoch_k(highs,lows,closes,14)}")
print(f"STOCH_D={stoch_d(highs,lows,closes,14,3)}")
print(f"WILLIAMS_R={williams_r(highs,lows,closes,14)}")
print(f"ATR_14={atr_calc(highs,lows,closes,14)}")
print(f"CCI_20={cci_calc(highs,lows,closes,20)}")
print(f"STOCH_RSI={stoch_rsi_calc(closes,14,14)}")
adx_v,pdi,mdi=adx_calc(highs,lows,closes,14)
print(f"ADX={adx_v}")
print(f"PLUS_DI={pdi}")
print(f"MINUS_DI={mdi}")
print(f"UO={uo_calc(highs,lows,closes)}")
print(f"ROC_12={roc_calc(closes,12)}")
print(f"OBV={int(obv_calc(closes,volumes))}")
print(f"MACD_LINE={macd_calc(closes)}")
print(f"MACD_SIGNAL={macd_signal_calc(closes)}")
print(f"BB_UPPER={bb_upper(closes)}")
print(f"BB_LOWER={bb_lower(closes)}")
