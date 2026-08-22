// ─── Candlestick Chart (Lightweight Charts) ─────────────────────────────────
// Depends on: modules/state.js, modules/charts/line-charts.js

function computeSMA(ohlc, period) {
    const result = [];
    for (let i = period - 1; i < ohlc.length; i++) {
        let sum = 0;
        for (let j = i - period + 1; j <= i; j++) {
            sum += ohlc[j].close;
        }
        result.push({ time: ohlc[i].time, value: sum / period });
    }
    return result;
}

function fmtBreakdown(val) {
    if (val == null) return '--';
    const n = Number(val);
    return (n >= 0 ? '+' : '') + n.toFixed(1);
}

function renderActiveStrategiesBadge(activeStrategyNames, currentSignal) {
    const container = document.getElementById('activeStrategiesBadge');
    if (!container) return;

    const breakdownMap = {};
    const breakdown = currentSignal?.strategyBreakdown || [];
    if (breakdown && breakdown.length) {
        breakdown.forEach(function(s) {
            breakdownMap[s.strategyName] = s.signal;
        });
    }

    if (!activeStrategyNames || activeStrategyNames.length === 0) {
        container.innerHTML = '<span class="text-xs text-yellow-400 font-medium flex items-center gap-1"><i class="fas fa-triangle-exclamation"></i>No strategies active — all signals will be HOLD</span>';
        return;
    }

    var chips = '<span class="text-xs text-secondary font-semibold mr-1">Strategies:</span>';
    activeStrategyNames.forEach(function(name) {
        var signal = breakdownMap[name] || '?';
        var color, icon;
        if (signal === 'BUY') {
            color = 'bg-green-900/40 text-green-400 border-green-700/50';
            icon = '<i class="fas fa-arrow-up mr-0.5"></i>';
        } else if (signal === 'SELL') {
            color = 'bg-red-900/40 text-red-400 border-red-700/50';
            icon = '<i class="fas fa-arrow-down mr-0.5"></i>';
        } else if (signal === 'HOLD') {
            color = 'bg-gray-700/50 text-gray-400 border-gray-600';
            icon = '<i class="fas fa-minus mr-0.5"></i>';
        } else {
            color = 'bg-gray-700/50 text-gray-400 border-gray-600';
            icon = '';
        }
        chips += '<span class="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-[10px] font-bold border ' + color + '">' + icon + escHtml(name) + '</span>';
    });

    if (currentSignal && currentSignal.recommendation) {
        var rec = currentSignal.recommendation;
        var recColor, recIcon;
        if (rec === 'STRONG BUY' || rec === 'BUY') {
            recColor = 'bg-green-600/30 text-green-300 border-green-500/50';
            recIcon = '<i class="fas fa-flag mr-0.5"></i>';
        } else if (rec === 'STRONG SELL' || rec === 'SELL') {
            recColor = 'bg-red-600/30 text-red-300 border-red-500/50';
            recIcon = '<i class="fas fa-flag mr-0.5"></i>';
        } else {
            recColor = 'bg-gray-600/30 text-gray-300 border-gray-500/50';
            recIcon = '<i class="fas fa-flag mr-0.5"></i>';
        }
        chips += '<span class="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-[10px] font-bold border ' + recColor + '">' + recIcon + rec + '</span>';
    }

    container.innerHTML = chips;
}

// ─── FVG Zone Primitive ──────────────────────────────────────────────────────
class FvgZonePrimitive {
    constructor(fvgEntries) {
        this._fvgEntries = fvgEntries || [];
        this._visible = true;
        this._chart = null;
        this._series = null;
        this._requestUpdate = null;
        this._paneView = {
            renderer: () => ({
                draw: (target) => {
                    if (this._visible === false) return;
                    if (!this._fvgEntries || this._fvgEntries.length === 0) return;
                    if (!this._chart || !this._series) return;

                    const timeScale = this._chart.timeScale();
                    const chartWidth = timeScale.width();

                    target.useMediaCoordinateSpace(scope => {
                        const ctx = scope.context;
                        this._fvgEntries.forEach(fvg => {
                            const topPrice = parseFloat(fvg.topPrice);
                            const bottomPrice = parseFloat(fvg.bottomPrice);
                            const fvgDateStr = fvg.date;

                            const epoch = Math.floor(new Date(fvgDateStr + 'T00:00:00Z').getTime() / 1000);
                            const xStart = timeScale.timeToCoordinate(epoch);
                            const xEnd = chartWidth;

                            const yTop = this._series.priceToCoordinate(topPrice);
                            const yBottom = this._series.priceToCoordinate(bottomPrice);

                            if (xStart == null || yTop == null || yBottom == null) return;
                            if (isNaN(xStart) || isNaN(yTop) || isNaN(yBottom)) return;

                            const isBullish = fvg.direction === 'BULLISH';
                            ctx.fillStyle = isBullish ? 'rgba(34, 197, 94, 0.2)' : 'rgba(239, 68, 68, 0.2)';
                            ctx.fillRect(xStart, yTop, xEnd - xStart, yBottom - yTop);
                        });
                    });
                }
            })
        };
    }

    name() { return 'FvgZonePrimitive'; }

    setVisible(visible) {
        this._visible = visible;
        if (this._requestUpdate) this._requestUpdate();
    }

    attached(param) {
        this._chart = param.chart;
        this._series = param.series;
        this._requestUpdate = param.requestUpdate;
    }

    detached() {
        this._chart = null;
        this._series = null;
        this._requestUpdate = null;
    }

    paneViews() {
        return [this._paneView];
    }
}

// ─── Candlestick Chart Render ────────────────────────────────────────────────
function renderCandlestickChart(prices, signalHistory, currentSignal, srData, txRecords = null) {
    const container = document.getElementById('chartCandlestick');
    if (!container) return;
    if (_charts.candlestick) {
        try { _charts.candlestick.remove(); } catch (e) {}
        delete _charts.candlestick;
    }
    for (const key in _chartOverlays) {
        if (_chartOverlays[key] && _chartOverlays[key].forEach) {
            _chartOverlays[key].forEach(series => {
                try { chart.removeSeries(series); } catch (e) {}
            });
         } else if (_chartOverlays[key]) {
             _chartOverlays[key].detach();
         }
        delete _chartOverlays[key];
    }

    if (!prices.length) {
        showChartMsg('chartCandlestick', 'No price data for candlestick');
        return;
    }
    if (typeof LightweightCharts === 'undefined') {
        showChartMsg('chartCandlestick', 'Chart library not loaded');
        return;
    }

    const sortedPrices = [...prices].sort((a, b) => new Date(a.priceDate) - new Date(b.priceDate));
    const rawOhlc = sortedPrices.map(p => ({
        time: p.priceDate,
        open: parseFloat(p.openingPrice || p.closingPrice),
        high: parseFloat(p.highPrice || p.closingPrice),
        low: parseFloat(p.lowPrice || p.closingPrice),
        close: parseFloat(p.closingPrice),
    })).filter(d => d.time && isFinite(d.open) && isFinite(d.high) && isFinite(d.low) && isFinite(d.close));

    const ohlc = rawOhlc.map(d => ({
        ...d,
        time: Math.floor(new Date(d.time + 'T00:00:00Z').getTime() / 1000),
    }));

    if (!ohlc.length) {
        showChartMsg('chartCandlestick', 'Insufficient OHLC data');
        return;
    }

    const volByEpoch = {};
    const volume = sortedPrices.map(p => {
        const t = Math.floor(new Date(p.priceDate + 'T00:00:00Z').getTime() / 1000);
        volByEpoch[t] = { time: t, value: parseFloat(p.volume) || 0 };
        return { time: t, value: parseFloat(p.volume) || 0 };
    }).filter(v => v.value > 0);

    const sigByDate = {};
    if (signalHistory) signalHistory.forEach(s => {
        const t = Math.floor(new Date(s.priceDate + 'T00:00:00Z').getTime() / 1000);
        sigByDate[t] = s;
    });
    if (currentSignal && currentSignal.recommendation
        && currentSignal.recommendation !== 'HOLD' && currentSignal.recommendation !== 'NEUTRAL'
        && ohlc.length) {
        const latestTime = ohlc[ohlc.length - 1].time;
        if (sigByDate[latestTime]) {
            sigByDate[latestTime].recommendation = currentSignal.recommendation;
            sigByDate[latestTime].compositeScore = currentSignal.compositeScore;
            if (currentSignal.strategyBreakdown) {
                sigByDate[latestTime].strategyBreakdown = currentSignal.strategyBreakdown;
                sigByDate[latestTime].buyThreshold = currentSignal.buyThreshold;
                sigByDate[latestTime].sellThreshold = currentSignal.sellThreshold;
            }
        } else {
            sigByDate[latestTime] = currentSignal;
        }
    }

    const Lw = window.LightweightCharts;
    const chart = Lw.createChart(container, {
        layout: { background: { type: 'solid', color: 'transparent' }, textColor: '#9ca3af' },
        grid: { vertLines: { color: '#1f2937' }, horzLines: { color: '#1f2937' } },
        crosshair: { mode: Lw.CrosshairMode.Normal },
        timeScale: { borderColor: '#374151' },
        rightPriceScale: { borderColor: '#374151' },
    });

    const cs = chart.addCandlestickSeries({
        upColor: '#22c55e', downColor: '#ef4444',
        borderDownColor: '#ef4444', borderUpColor: '#22c55e',
        wickDownColor: '#ef4444', wickUpColor: '#22c55e',
    });
    cs.setData(ohlc);
    _csCandlestick = cs;

    // Volume histogram colored by price direction
    const cleanVolume = volume.filter(v => v.value != null && v.value > 0 && !isNaN(v.value));
    if (cleanVolume.length > 0) {
        try {
            const volSeries = chart.addHistogramSeries({
                priceFormat: { type: 'volume' },
                priceScaleId: 'volume',
                color: '#26a69a',
                priceLineVisible: false,
                lastValueVisible: false,
            });
            chart.priceScale('volume').applyOptions({
                scaleMargins: { top: 0.8, bottom: 0 },
            });
            const upByTime = new Set(ohlc.filter(o => o.close >= o.open).map(o => o.time));
            const coloredVolume = cleanVolume.map(v => ({
                ...v,
                color: upByTime.has(v.time) ? 'rgba(34, 197, 94, 0.5)' : 'rgba(239, 68, 68, 0.5)',
            }));
            volSeries.setData(coloredVolume);
        } catch (e) {
            console.warn('[Candlestick] Volume histogram not available:', e);
        }
    }

    // SMA overlays
    const sma20Data = computeSMA(ohlc, 20);
    const sma44Data = ohlc.length >= 44 ? computeSMA(ohlc, 44) : [];
    const sma50Data = ohlc.length >= 50 ? computeSMA(ohlc, 50) : [];
    const sma200Data = ohlc.length >= 200 ? computeSMA(ohlc, 200) : [];

    const sma20Series = chart.addLineSeries({
        color: '#3b82f6', lineWidth: 1.5, lastValueVisible: false, priceLineVisible: false,
    });
    sma20Series.setData(sma20Data);
    _chartOverlays.sma20 = sma20Series;

    const sma44Series = chart.addLineSeries({
        color: '#eab308', lineWidth: 1.5, lastValueVisible: false, priceLineVisible: false,
    });
    sma44Series.setData(sma44Data);
    _chartOverlays.sma44 = sma44Series;

    const sma50Series = chart.addLineSeries({
        color: '#f97316', lineWidth: 1.5, lastValueVisible: false, priceLineVisible: false,
    });
    sma50Series.setData(sma50Data);
    _chartOverlays.sma50 = sma50Series;

    const sma200Series = chart.addLineSeries({
        color: '#a855f7', lineWidth: 1.5, lastValueVisible: false, priceLineVisible: false,
    });
    sma200Series.setData(sma200Data);
    _chartOverlays.sma200 = sma200Series;

    // S/R price lines
    const srPriceLines = [];
    const srColorMap = [];
    if (srData) {
        const usedPrices = new Set();
        const addLevel = (price, color, label, width) => {
            if (price == null || usedPrices.has(price)) return;
            usedPrices.add(price);
            const pl = cs.createPriceLine({
                price: parseFloat(price),
                color: color,
                lineWidth: width || 1,
                lineStyle: Lw.LineStyle.Dashed,
                axisLabelVisible: true,
                title: label,
            });
            srPriceLines.push(pl);
            srColorMap.push(color);
        };
        if (srData.pivots) {
            const p = srData.pivots;
            addLevel(p.pivot, 'rgba(59,130,246,0.6)', 'P', 2);
            addLevel(p.r1, 'rgba(239,68,68,0.5)', 'R1', 1.5);
            addLevel(p.r2, 'rgba(239,68,68,0.35)', 'R2', 1);
            addLevel(p.r3, 'rgba(239,68,68,0.25)', 'R3', 1);
            addLevel(p.s1, 'rgba(34,197,94,0.5)', 'S1', 1.5);
            addLevel(p.s2, 'rgba(34,197,94,0.35)', 'S2', 1);
            addLevel(p.s3, 'rgba(34,197,94,0.25)', 'S3', 1);
        }
        if (srData.majorLevels) {
            srData.majorLevels.forEach(ml => {
                addLevel(ml.price,
                    ml.type === 'support' ? 'rgba(34,197,94,0.5)' : 'rgba(239,68,68,0.5)',
                    ml.type === 'support' ? 'S (' + ml.touches + '×)' : 'R (' + ml.touches + '×)', 2);
            });
        }
    }
    _chartOverlays.sr = srPriceLines;
    _chartOverlays.srColors = srColorMap;

    // Avg Cost price line
    if (_stockData?.quantity > 0 && _stockData?.avgPrice != null) {
        cs.createPriceLine({
            price: parseFloat(_stockData.avgPrice),
            color: 'rgba(59,130,246,0.9)',
            lineWidth: 2,
            lineStyle: Lw.LineStyle.Dashed,
            axisLabelVisible: true,
            title: 'Avg Cost',
        });
    }

    // FVG zones
    if (_cachedFvgEntries && _cachedFvgEntries.length) {
        const fvgPrimitive = new FvgZonePrimitive(_cachedFvgEntries);
        cs.attachPrimitive(fvgPrimitive);
        _chartOverlays.fvg = fvgPrimitive;
    }

    // ── Markers ──
    const markers = [];
    const validDates = new Set(ohlc.map(d => new Date(d.time * 1000).toISOString().slice(0, 10)));
    console.log('[Markers] Valid OHLC dates:', Array.from(validDates).slice(0, 10), '... total:', validDates.size);

    // Transaction markers
    if (txRecords && txRecords.length) {
        console.log('[Markers] Processing', txRecords.length, 'transaction records');
        const txMap = {};
        txRecords.forEach(tx => {
            const txDateStr = new Date(tx.transactionDate + 'T00:00:00Z').toISOString().slice(0, 10);
            const epoch = Math.floor(new Date(tx.transactionDate + 'T00:00:00Z').getTime() / 1000);
            if (!validDates.has(txDateStr)) {
                console.log('[Markers] Skipping tx - no matching OHLC date:', tx.transactionDate, '→', txDateStr);
                return;
            }
            const type = tx.type;
            const qty = tx.quantity;
            const key = `${epoch}_${type}`;
            if (!txMap[key]) {
                txMap[key] = { time: epoch, type, qty: 0 };
            }
            txMap[key].qty += qty;
        });
        Object.values(txMap).forEach(item => {
            const isBuy = item.type === 'BUY';
            markers.push({
                time: item.time,
                position: isBuy ? 'belowBar' : 'aboveBar',
                color: isBuy ? '#2962FF' : '#FF9800',
                shape: isBuy ? 'arrowUp' : 'arrowDown',
                size: 1.5,
                text: String(item.qty),
                isTransaction: true,
            });
        });
    }

    // Signal markers
    if (signalHistory && signalHistory.length) {
        signalHistory.forEach(s => {
            const epoch = Math.floor(new Date(s.priceDate + 'T00:00:00Z').getTime() / 1000);
            const sigDateStr = new Date(s.priceDate + 'T00:00:00Z').toISOString().slice(0, 10);
            if (s.priceDate && s.recommendation
                && s.recommendation !== 'NEUTRAL'
                && s.recommendation !== 'HOLD'
                && validDates.has(sigDateStr)) {
                const isBuy = s.recommendation === 'STRONG BUY' || s.recommendation === 'BUY';
                const isSell = s.recommendation === 'STRONG SELL' || s.recommendation === 'SELL';
                const isStrong = s.compositeScore >= 7 || s.compositeScore <= -7;
                if (isStrong || (isBuy && _showBuyArrows) || (isSell && _showSellArrows)) {
                    markers.push({
                        time: epoch,
                        position: isBuy ? 'belowBar' : isSell ? 'aboveBar' : 'inBar',
                        color: isStrong && isBuy ? '#16a34a' : isBuy ? '#22c55e' :
                               isStrong && isSell ? '#dc2626' : isSell ? '#ef4444' : '#6b7280',
                        shape: isBuy ? 'arrowUp' : 'arrowDown',
                        size: isStrong ? 2 : 1,
                        text: isBuy ? (s.compositeScore >= 7 ? 'SB' : 'B') :
                              isSell ? (s.compositeScore <= -7 ? 'SS' : 'S') : '',
                    });
                }
            }
        });
    }

    // Candlestick pattern markers
    if (signalHistory && signalHistory.length) {
        signalHistory.forEach(s => {
            if (!s.priceDate || !s.candlestickPattern || s.candlestickPattern === 'NONE') return;
            const epoch = Math.floor(new Date(s.priceDate + 'T00:00:00Z').getTime() / 1000);
            const patDateStr = new Date(s.priceDate + 'T00:00:00Z').toISOString().slice(0, 10);
            if (!validDates.has(patDateStr)) return;
            const hasSignal = markers.some(m => m.time === epoch);
            if (hasSignal) return;
            const isBullish = s.candlestickScore >= 2;
            markers.push({
                time: epoch,
                position: 'belowBar',
                color: isBullish ? 'rgba(168,85,247,0.5)' : 'rgba(168,85,247,0.35)',
                shape: 'circle',
                size: 8,
                text: '',
            });
        });
    }

    // Current signal marker
    if (currentSignal && currentSignal.recommendation
        && currentSignal.recommendation !== 'HOLD' && currentSignal.recommendation !== 'NEUTRAL'
        && ohlc.length) {
        const lastTime = ohlc[ohlc.length - 1].time;
        const existingIdx = markers.findIndex(m => m.time === lastTime);
        if (existingIdx >= 0) {
            markers.splice(existingIdx, 1);
        }
        const isBuy = currentSignal.recommendation === 'STRONG BUY' || currentSignal.recommendation === 'BUY';
        const isStrongCur = currentSignal.compositeScore >= 7 || currentSignal.compositeScore <= -7;
        if (isStrongCur || (isBuy && _showBuyArrows) || (!isBuy && _showSellArrows)) {
            markers.push({
                time: lastTime,
                position: isBuy ? 'belowBar' : 'aboveBar',
                color: isStrongCur && isBuy ? '#16a34a' : isBuy ? '#22c55e' :
                       isStrongCur && !isBuy ? '#dc2626' : '#ef4444',
                shape: isBuy ? 'arrowUp' : 'arrowDown',
                size: isStrongCur ? 2 : 1,
                text: isStrongCur && isBuy ? 'SB' :
                      currentSignal.compositeScore <= -7 && !isBuy ? 'SS' :
                      isBuy ? 'B' : 'S',
            });
        }
    }

    _lastMarkersFull = [...markers];
    markers.sort((a, b) => a.time - b.time);
    if (markers.length > 40) {
        markers.splice(0, markers.length - 40);
    }

    // ── Tooltip ──
    container.querySelectorAll('.lw-tooltip').forEach(el => el.remove());
    const tooltip = document.createElement('div');
    tooltip.className = 'lw-tooltip';
    tooltip.style.cssText = [
        'position:absolute;display:none;z-index:100;min-width:280px;max-width:500px',
        'background:rgba(30,41,59,0.96);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px)',
        'border:1px solid rgba(255,255,255,0.08);border-radius:14px',
        'padding:12px 14px;font-size:14px;',
        'box-shadow:0 12px 40px rgba(0,0,0,0.55),0 0 0 1px rgba(255,255,255,0.06) inset',
        'color:#e2e8f0;line-height:1.5',
        'transition:opacity 0.15s ease',
    ].join(';');
    container.appendChild(tooltip);

    function buildCompactIndicators(indicatorsForDate) {
        const keys = [
            { label: 'RSI(14)', key: 'RSI', pr: 1, col: v => v > 70 ? '#ef4444' : v < 30 ? '#22c55e' : null },
            { label: 'MACD', key: 'MACD_LINE', pr: 2, col: v => v >= 0 ? '#22c55e' : '#ef4444' },
            { label: 'ADX', key: 'ADX', pr: 1, col: v => v > 25 ? '#22c55e' : null },
            { label: 'SMA20', key: 'SMA_20', pr: 2, col: null },
            { label: 'SMA50', key: 'SMA_50', pr: 2, col: null },
            { label: 'Bollinger U', key: 'BOLLINGER_UPPER', pr: 2, col: null },
            { label: 'Bollinger L', key: 'BOLLINGER_LOWER', pr: 2, col: null },
        ];
        const items = keys
            .map(k => ({ label: k.label, pr: k.pr, col: k.col, val: indicatorsForDate[k.key] }))
            .filter(x => x.val != null && !isNaN(x.val));
        if (!items.length) return '';
        const rows = items.map(x => {
            const f = Number(x.val).toFixed(x.pr);
            const color = x.col ? x.col(parseFloat(x.val)) : null;
            return `<span style="color:#8892a0;font-size:10px;">${x.label}</span><span style="font-weight:600;text-align:right;color:${color || '#e2e8f0'};font-size:10px;font-variant-numeric:tabular-nums;">${f}</span>`;
        }).join('');
        return `<div style="display:grid;grid-template-columns:1fr auto;gap:2px 10px;">${rows}</div>`;
    }

    chart.subscribeCrosshairMove(param => {
        if (!param.point || !param.time) { tooltip.style.display = 'none'; return; }
        const data = param.seriesData.get(cs);
        if (!data) { tooltip.style.display = 'none'; return; }
        const isUp = data.close >= data.open;
        const chg = data.close - data.open;
        const chgPct = data.open ? (chg / data.open * 100) : 0;
        const epoch = data.time || '';
        const sig = sigByDate[epoch];
        const volItem = volume.find(v => v.time === epoch);
        const dateStr = epoch ? new Date(epoch * 1000).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';

        let recLabel = 'No Signal';
        let recColor = '#9ca3af';

        const volDisplay = volItem
            ? (volItem.value >= 1e7 ? (volItem.value / 1e7).toFixed(2) + 'Cr' :
               volItem.value >= 1e5 ? (volItem.value / 1e5).toFixed(2) + 'L' :
               Number(volItem.value).toLocaleString('en-IN'))
            : '--';
        const c = isUp ? '#22c55e' : '#ef4444';
        const bgColor = isUp ? 'rgba(34,197,94,0.06)' : 'rgba(239,68,68,0.06)';
        const p = v => '₹' + Number(v).toLocaleString('en-IN', { minimumFractionDigits: 2 });

        const indicatorLookupDate = epoch ? new Date(epoch * 1000).toISOString().split('T')[0] : '';
        const indicatorsForDate = _cachedIndicatorsByDate[indicatorLookupDate] || {};
        const indicatorHtml = buildCompactIndicators(indicatorsForDate);

        // FVG proximity check
        var fvgTooltip = '';
        if (_cachedFvgEntries && _cachedFvgEntries.length > 0 && data.close) {
            var hoverPrice = data.close;
            for (var fi = 0; fi < _cachedFvgEntries.length; fi++) {
                var f = _cachedFvgEntries[fi];
                var topP = parseFloat(f.topPrice);
                var botP = parseFloat(f.bottomPrice);
                if (topP && botP && hoverPrice >= botP * 0.995 && hoverPrice <= topP * 1.005) {
                    var dir = f.direction === 'BULLISH' ? '↑' : '↓';
                    var gapStr = f.gapSize ? ' ₹' + Number(f.gapSize).toFixed(1) : '';
                    var stateStr = f.state === 'OPEN' ? 'Open' : f.state === 'PARTIALLY_FILLED' ? 'Partial' : 'Filled';
                    fvgTooltip = '<div style="margin-top:4px;padding:4px 8px;border-left:3px solid rgba(251,191,36,0.4);background:rgba(251,191,36,0.04);border-radius:4px;display:flex;justify-content:space-between;align-items:center;font-size:10px;">' +
                        '<span style="color:#d1d5db;">⚠ FVG</span>' +
                        '<span style="font-weight:600;color:#e2e8f0;">' + dir + gapStr + ' <span style="color:#9ca3af;font-size:9px;">' + stateStr + '</span></span></div>';
                    break;
                }
            }
        }

        let scoreSection = '';
        let strategyHtml = '';
        const miniFactors = [];

        if (sig) {
            if (sig.compositeScore >= 5) { recColor = '#22c55e'; }
            else if (sig.compositeScore <= -5) { recColor = '#ef4444'; }

            recLabel = sig.compositeScore >= 7 ? 'STRONG BUY' :
                sig.compositeScore >= 5 ? 'BUY' :
                sig.compositeScore <= -7 ? 'STRONG SELL' :
                sig.compositeScore <= -5 ? 'SELL' : (sig.recommendation || 'HOLD');

            const scores = [
                { label: 'RSI', score: sig.rsiScore },
                { label: 'SMA', score: sig.smaScore },
                { label: 'Bollinger', score: sig.bollingerScore },
                { label: 'MACD', score: sig.macdScore },
                { label: 'Trend', score: sig.trendDirectionScore },
                { label: 'Divergence', score: sig.divergenceScore },
                { label: 'Weekly', score: sig.weeklyConfluenceScore },
                { label: 'FII/DII', score: sig.fiidiiScore },
            ];
            const activeScores = scores.filter(f => f.score != null && f.score !== 0);
            if (activeScores.length) {
                const scoreChips = activeScores.map(f => {
                    const col = f.score > 0 ? '#22c55e' : '#ef4444';
                    const bg = f.score > 0 ? 'rgba(34,197,94,0.12)' : 'rgba(239,68,68,0.12)';
                    return `<span style="display:inline-flex;align-items:center;gap:2px;padding:1px 5px;border-radius:4px;font-size:9px;font-weight:600;background:${bg};color:${col};">${f.label} ${f.score >= 0 ? '+' : ''}${f.score}</span>`;
                }).join('');
                scoreSection = `<div style="display:flex;flex-wrap:wrap;gap:2px;margin-top:4px;padding-top:4px;border-top:1px solid rgba(255,255,255,0.06);">${scoreChips}</div>`;
            }

            if (sig.candlestickPattern && sig.candlestickScore !== 0) {
                const col = sig.candlestickScore > 0 ? '#22c55e' : '#ef4444';
                miniFactors.push(`<span style="color:#9ca3af;font-size:9px;">Candle</span><span style="font-weight:600;color:${col};font-size:9px;">${sig.candlestickPattern}</span>`);
            }

            if (sig.strategyBreakdown && sig.strategyBreakdown.length && sig.buyThreshold != null) {
                const rows = sig.strategyBreakdown.map(s => {
                    const sc = s.signal === 'BUY' ? '#22c55e' : s.signal === 'SELL' ? '#ef4444' : '#6b7280';
                    const sbg = s.signal === 'BUY' ? 'rgba(34,197,94,0.1)' : s.signal === 'SELL' ? 'rgba(239,68,68,0.1)' : 'transparent';
                    return `
                         <div style="display:flex;flex-direction:column;gap:1px;padding:3px 5px;border-radius:5px;background:${sbg};">
                            <div style="display:flex;justify-content:space-between;align-items:center;">
                                <span style="font-weight:600;color:#e2e8f0;font-size:9px;">${s.strategyName}</span>
                                <span style="color:${sc};font-weight:700;font-size:9px;">${s.signal}</span>
                            </div>
                            <div style="display:flex;justify-content:space-between;color:#9ca3af;font-size:8px;">
                                <span>${(s.confidence * 100).toFixed(0)}% confidence</span>
                                <span>p${s.priority} ×${(s.confidence * 100).toFixed(0)}% = <strong style="color:#e2e8f0;">${(s.weightedScore ?? s.contribution ?? 0).toFixed(2)}</strong></span>
                            </div>
                            ${s.reason ? `<div style="color:#9ca3af;font-size:8px;margin-top:1px;line-height:1.3;">${s.reason}</div>` : ''}
                        </div>`;
                }).join('');
                const thresholdColor = sig.rawScore != null
                    ? (sig.rawScore >= sig.buyThreshold ? '#22c55e' :
                       sig.rawScore <= sig.sellThreshold ? '#ef4444' : '#6b7280')
                    : '#6b7280';
                const thresholdLabel = sig.rawScore != null
                    ? (sig.rawScore >= sig.buyThreshold ? '✓ BUY' :
                       sig.rawScore <= sig.sellThreshold ? '✓ SELL' : '→ HOLD')
                    : '—';
                const thresholdDetail = sig.rawScore >= sig.buyThreshold ?
                    `${sig.rawScore.toFixed(2)} ≥ ${sig.buyThreshold}` :
                    sig.rawScore <= sig.sellThreshold ?
                    `${sig.rawScore.toFixed(2)} ≤ ${sig.sellThreshold}` :
                    `between thresholds`;
                strategyHtml = `
                    <div style="margin-top:5px;padding-top:4px;border-top:1px solid rgba(255,255,255,0.06);">
                         <div class="breakdown-header" style="display:flex;align-items:center;gap:4px;padding:2px 0;">
                             <span style="font-weight:600;color:#9ca3af;font-size:9px;text-transform:uppercase;letter-spacing:0.3px;">Strategy Breakdown</span>
                             <span style="margin-left:auto;font-size:8px;color:${thresholdColor};font-weight:600;">${thresholdLabel}</span>
                             <div style="color:#9ca3af;font-size:8px;text-align:center;margin-top:1px;">${thresholdDetail}</div>
                         </div>
                         <div class="breakdown-body" style="display:flex;flex-direction:column;gap:3px;margin-top:3px;">
                             ${rows}
                             <div style="display:flex;justify-content:space-between;padding:3px 5px;border-top:1px solid rgba(255,255,255,0.04);margin-top:1px;">
                                 <span style="color:#9ca3af;font-size:9px;font-weight:600;">Total</span>
                                 <span style="color:#e2e8f0;font-size:9px;font-weight:700;">${sig.rawScore != null ? sig.rawScore.toFixed(2) : sig.compositeScore}</span>
                             </div>
                         </div>
                     </div>`;
            }
        }

        const signalScoreText = sig?.compositeScore != null ? (sig.compositeScore >= 0 ? '+' : '') + sig.compositeScore : '--';
        const accIcon = sig?.wasAccurate === true ? '✓' : sig?.wasAccurate === false ? '✗' : '';
        const accColor = sig?.wasAccurate === true ? '#22c55e' : sig?.wasAccurate === false ? '#9ca3af' : '#9ca3af';
        const accFwd = sig?.forwardReturn != null ? (sig.forwardReturn >= 0 ? '+' : '') + Number(sig.forwardReturn).toFixed(1) + '%' : '';
        const accuracyTag = sig?.wasAccurate != null
            ? `<span style="color:${accColor};font-size:10px;font-weight:600;margin-left:4px;">${accIcon} ${accFwd}</span>`
            : '';

        tooltip.innerHTML = `
            <div style="margin:-12px -14px 8px;padding:8px 14px;border-radius:14px 14px 0 0;background:${bgColor};border-bottom:1px solid rgba(255,255,255,0.08);">
                <div style="display:flex;align-items:center;justify-content:space-between;">
                    <div style="display:flex;align-items:center;gap:6px;">
                        <span style="display:inline-flex;align-items:center;gap:5px;">
                            <span style="display:inline-flex;align-items:center;justify-content:center;width:6px;height:6px;border-radius:50%;background:${recColor};"></span>
                            <span style="font-weight:800;color:${recColor};font-size:14px;letter-spacing:-0.02em;">${recLabel}</span>
                            <span style="font-weight:700;color:${recColor};font-size:12px;background:rgba(255,255,255,0.08);padding:1px 5px;border-radius:4px;box-shadow:0 0 6px rgba(255,255,255,0.06);">${signalScoreText}</span>
                        </span>
                        ${accuracyTag}
                    </div>
                    <span style="color:#9ca3af;font-size:9px;font-weight:500;">${dateStr}</span>
                </div>
                <div style="font-size:9px;color:#9ca3af;margin-top:2px;font-weight:500;">${_symbol || 'Stock'}</div>
            </div>
            <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:2px 6px;margin-bottom:5px;">
                <div style="background:rgba(255,255,255,0.02);border-radius:5px;padding:3px 5px;text-align:center;">
                    <div style="color:#9ca3af;font-size:8px;font-weight:600;text-transform:uppercase;letter-spacing:0.4px;">O</div>
                    <div style="font-weight:700;color:#e2e8f0;font-size:10px;font-variant-numeric:tabular-nums;">${p(data.open)}</div>
                </div>
                <div style="background:rgba(255,255,255,0.04);border-radius:5px;padding:3px 5px;text-align:center;">
                    <div style="color:#9ca3af;font-size:8px;font-weight:600;text-transform:uppercase;letter-spacing:0.4px;">H</div>
                    <div style="font-weight:700;color:#e2e8f0;font-size:10px;font-variant-numeric:tabular-nums;">${p(data.high)}</div>
                </div>
                <div style="background:rgba(255,255,255,0.02);border-radius:5px;padding:3px 5px;text-align:center;">
                    <div style="color:#9ca3af;font-size:8px;font-weight:600;text-transform:uppercase;letter-spacing:0.4px;">L</div>
                    <div style="font-weight:700;color:#e2e8f0;font-size:10px;font-variant-numeric:tabular-nums;">${p(data.low)}</div>
                </div>
                <div style="background:rgba(255,255,255,0.04);border-radius:5px;padding:3px 5px;text-align:center;">
                    <div style="color:#9ca3af;font-size:8px;font-weight:600;text-transform:uppercase;letter-spacing:0.4px;">C</div>
                    <div style="font-weight:700;color:${c};font-size:10px;font-variant-numeric:tabular-nums;">${p(data.close)}</div>
                </div>
                <div style="background:rgba(255,255,255,0.02);border-radius:5px;padding:3px 5px;text-align:center;">
                    <div style="color:#9ca3af;font-size:8px;font-weight:600;text-transform:uppercase;letter-spacing:0.4px;">Chg</div>
                    <div style="font-weight:700;color:${c};font-size:10px;">${chg >= 0 ? '+' : ''}${chgPct.toFixed(2)}%</div>
                </div>
                <div style="background:rgba(255,255,255,0.04);border-radius:5px;padding:3px 5px;text-align:center;">
                    <div style="color:#9ca3af;font-size:8px;font-weight:600;text-transform:uppercase;letter-spacing:0.4px;">Vol</div>
                    <div style="font-weight:700;color:#e2e8f0;font-size:10px;">${volDisplay}</div>
                </div>
            </div>
            ${scoreSection}
            ${miniFactors.length ? `<div style="display:flex;flex-wrap:wrap;gap:4px;margin-top:3px;padding-top:3px;border-top:1px solid rgba(255,255,255,0.06);">${miniFactors.map(f => `<span style="padding:2px 6px;border-radius:10px;background:rgba(255,255,255,0.04);font-size:9px;">${f}</span>`).join('')}</div>` : ''}
            ${indicatorHtml ? `<div style="margin-top:4px;padding-top:3px;border-top:1px solid rgba(255,255,255,0.06);">${indicatorHtml}</div>` : ''}
            ${fvgTooltip}
            ${strategyHtml}
        `;

        const rect = container.getBoundingClientRect();
        let left = param.point.x + 16, top = param.point.y - 10;
        const tipW = tooltip.offsetWidth || 280;
        const tipH = tooltip.offsetHeight || 350;
        if (left + tipW > rect.width - 8) left = param.point.x - tipW - 16;
        if (top + tipH > rect.height - 8) top = rect.height - tipH - 10;
        tooltip.style.display = 'block';
        tooltip.style.left = Math.max(4, left) + 'px';
        tooltip.style.top = Math.max(4, top) + 'px';
    });

    chart.timeScale().fitContent();
    console.log('[Markers] Total markers:', markers.length, 'Markers:', markers);
    if (markers.length && typeof cs.setMarkers === 'function') {
        try {
            cs.setMarkers(markers);
            console.log('[Markers] setMarkers succeeded');
        } catch (e) {
            console.warn('[Candlestick] setMarkers failed:', e);
        }
    } else if (markers.length) {
        console.warn('[Candlestick] setMarkers not available on series (lightweight-charts v4.1.0 compatibility issue)');
    } else {
        console.log('[Markers] No markers to display');
    }
    _charts.candlestick = chart;

    chart.timeScale().subscribeVisibleLogicalRangeChange(() => {
        if (_csCandlestick && _lastMarkersFull) {
            try { _csCandlestick.setMarkers(getVisibleMarkers()); } catch(e) {}
        }
    });

    toggleChartOverlays();
}

function getVisibleMarkers() {
    const showBuy = document.getElementById('toggleBuyArrows')?.checked;
    const showSell = document.getElementById('toggleSellArrows')?.checked;
    return _lastMarkersFull.filter(m => {
        if (!m.text) return true;
        if (m.isTransaction) return true;
        if (m.text === 'SB' || m.text === 'SS') return true;
        const firstChar = m.text.charAt(0);
        if (firstChar === 'B' && !showBuy) return false;
        if (firstChar === 'S' && !showSell) return false;
        return true;
    });
}

function toggleChartOverlays() {
    if (_chartOverlays.sma20) _chartOverlays.sma20.applyOptions({ visible: document.getElementById('toggleSma20')?.checked !== false });
    if (_chartOverlays.sma44) _chartOverlays.sma44.applyOptions({ visible: document.getElementById('toggleSma44')?.checked !== false });
    if (_chartOverlays.sma50) _chartOverlays.sma50.applyOptions({ visible: document.getElementById('toggleSma50')?.checked !== false });
    if (_chartOverlays.sma200) _chartOverlays.sma200.applyOptions({ visible: document.getElementById('toggleSma200')?.checked !== false });

    if (_chartOverlays.sr && _chartOverlays.srColors) {
        const srVisible = document.getElementById('toggleSR')?.checked !== false;
        _chartOverlays.sr.forEach((pl, i) => {
            if (pl && typeof pl.applyOptions === 'function') {
                pl.applyOptions({
                    color: srVisible ? (_chartOverlays.srColors[i] || '#6b7280') : 'transparent',
                    axisLabelVisible: srVisible,
                });
            }
        });
    }

    const showFvg = document.getElementById('toggleFvg')?.checked;
    if (_chartOverlays.fvg) {
        _chartOverlays.fvg.setVisible(showFvg !== false);
    }

    _showBuyArrows = document.getElementById('toggleBuyArrows')?.checked !== false;
    _showSellArrows = document.getElementById('toggleSellArrows')?.checked !== false;

    if (_csCandlestick && _lastMarkersFull) {
        try { _csCandlestick.setMarkers(getVisibleMarkers()); } catch(e) {}
    }
}
