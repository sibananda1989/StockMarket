// ─── Chart Helpers & Line Charts (Chart.js) ──────────────────────────────────
// Depends on: modules/state.js

function showChartMsg(canvasId, msg) {
    const el = document.getElementById(canvasId);
    if (!el) return;
    const isCanvas = el.tagName === 'CANVAS';
    if (isCanvas) el.style.display = msg ? 'none' : '';
    let msgEl = el.parentElement.querySelector('.chart-msg');
    if (msg) {
        if (!msgEl) {
            msgEl = document.createElement('p');
            msgEl.className = 'chart-msg text-secondary text-center py-8';
            el.parentElement.appendChild(msgEl);
        }
        msgEl.textContent = msg;
        if (!isCanvas) el.style.display = 'none';
    } else if (msgEl) {
        msgEl.remove();
        if (!isCanvas) el.style.display = '';
    }
}

function destroyChart(key) {
    if (_charts[key]) {
        // Cleanup: unsubscribe from visibleLogicalRangeChange for candlestick chart to prevent memory leaks
        if (key === 'candlestick' && _charts[key].timeScale && typeof _charts[key].timeScale === 'function') {
            try { _charts[key].timeScale().unsubscribeVisibleLogicalRangeChange(); } catch(e) {}
        }
        if (typeof _charts[key].destroy === 'function') _charts[key].destroy();
        else if (typeof _charts[key].remove === 'function') _charts[key].remove();
        delete _charts[key];
    }
}

function setText(id, val) {
    const el = document.getElementById(id);
    if (el) el.textContent = val;
}

// ─── Price Movement (LTP) Chart ──────────────────────────────────────────────
function renderLtpChart(prices) {
    destroyChart('ltp');
    const canvas = document.getElementById('chartLtp');
    if (!canvas || !prices.length) {
        showChartMsg('chartLtp', 'No price data');
        return;
    }
    showChartMsg('chartLtp');
    const sortedPrices = [...prices].sort((a, b) => new Date(a.priceDate) - new Date(b.priceDate));
    const labels = sortedPrices.map(p => p.priceDate);
    const data = sortedPrices.map(p => parseFloat(p.closingPrice));
    _charts.ltp = new Chart(canvas, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'LTP (₹)',
                data,
                borderColor: '#3b82f6',
                backgroundColor: 'rgba(59,130,246,0.08)',
                fill: true,
                tension: 0.3,
                pointRadius: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: { label: ctx => '₹' + Number(ctx.raw).toLocaleString('en-IN', { minimumFractionDigits: 2 }) }
                }
            },
            scales: {
                x: { ticks: { maxTicksLimit: 12 } },
                y: { position: 'right', ticks: { callback: v => '₹' + Number(v).toLocaleString('en-IN') } }
            }
        }
    });
}

// ─── P&L Trend Charts ────────────────────────────────────────────────────────
function renderPnlCharts(dailyData) {
    destroyChart('pnl');
    destroyChart('pnlBar');
    const canvas = document.getElementById('chartPnl');
    const barCanvas = document.getElementById('chartPnlBar');
    if (!canvas || !dailyData.length) {
        showChartMsg('chartPnl', 'No per-day P&L data — add quantity and avg price to compute');
        if (barCanvas) barCanvas.style.display = 'none';
        return;
    }
    showChartMsg('chartPnl');
    if (barCanvas) barCanvas.style.display = '';
    const sortedData = [...dailyData].sort((a, b) => new Date(a.date) - new Date(b.date));
    const labels = sortedData.map(d => d.date);
    const pnlData = sortedData.map(d => d.pnl);

    _charts.pnl = new Chart(canvas, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'P&L (₹)',
                data: pnlData,
                borderColor: '#8b5cf6',
                backgroundColor: 'rgba(139,92,246,0.08)',
                fill: true,
                tension: 0.3,
                pointRadius: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                tooltip: { callbacks: { label: ctx => fmtPrice(ctx.raw) } }
            },
            scales: {
                x: { ticks: { maxTicksLimit: 8 } },
                y: { position: 'right', ticks: { callback: v => fmtPrice(v) } }
            }
        }
    });

    const barColors = pnlData.map(v => v >= 0 ? 'rgba(34,197,94,0.7)' : 'rgba(239,68,68,0.7)');
    _charts.pnlBar = new Chart(barCanvas, {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                label: 'P&L (₹)',
                data: pnlData,
                backgroundColor: barColors,
                borderRadius: 3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { ticks: { maxTicksLimit: 6 } },
                y: { position: 'right', ticks: { callback: v => fmtPrice(v) } }
            }
        }
    });
}

// ─── Investment vs Current Value Chart ───────────────────────────────────────
function renderInvValueChart(snapshots) {
    destroyChart('invValue');
    const canvas = document.getElementById('chartInvValue');
    if (!canvas || !snapshots.length) {
        showChartMsg('chartInvValue', 'No investment data');
        return;
    }
    showChartMsg('chartInvValue');
    const sortedData = [...snapshots].sort((a, b) => new Date(a.date) - new Date(b.date));
    const labels = sortedData.map(d => d.date);
    const invData = sortedData.map(d => parseFloat(d.investment));
    const valData = sortedData.map(d => parseFloat(d.currentValue));

    _charts.invValue = new Chart(canvas, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'Investment (₹)',
                data: invData,
                borderColor: '#f59e0b',
                backgroundColor: 'rgba(245,158,11,0.08)',
                fill: true,
                borderDash: [5, 5],
                tension: 0.3,
                pointRadius: 2
            }, {
                label: 'Current Value (₹)',
                data: valData,
                borderColor: '#22c55e',
                backgroundColor: 'rgba(34,197,94,0.1)',
                fill: true,
                tension: 0.3,
                pointRadius: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            resizeDelay: 20,
            animation: { duration: 400, easing: 'easeInOutQuart' },
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: { callbacks: { label: ctx => ctx.dataset.label + ': ' + fmtPrice(ctx.raw) } }
            },
            scales: {
                x: { ticks: { maxTicksLimit: 8 }, grid: { display: true } },
                y: { position: 'right', ticks: { callback: v => fmtPrice(v) }, grid: { display: true } }
            },
            onClick: null
        }
    });

    setTimeout(() => {
        if (_charts.invValue) _charts.invValue.resize();
    }, 100);
}

// ─── RSI(14) Chart ───────────────────────────────────────────────────────────
function renderRsiChart(history) {
    destroyChart('rsi');
    const canvas = document.getElementById('chartRsi');
    if (!canvas || !history.length) {
        showChartMsg('chartRsi', 'No RSI data');
        return;
    }
    showChartMsg('chartRsi');

    function fmtDate(raw) {
        if (!raw || typeof raw !== 'string') return raw || '';
        var parts = raw.split('-');
        if (parts.length !== 3) return raw;
        var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
        return parseInt(parts[2], 10) + ' ' + months[parseInt(parts[1], 10) - 1] + ' ' + parts[0];
    }

    function shortDate(raw) {
        if (!raw || typeof raw !== 'string') return raw || '';
        var parts = raw.split('-');
        if (parts.length !== 3) return raw;
        var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
        return parseInt(parts[2], 10) + ' ' + months[parseInt(parts[1], 10) - 1];
    }

    var labels = history.map(function(h) { return h.calculationDate || h.date; });
    var data = history.map(function(h) { return parseFloat(h.rsi14); });
    var pointColors = data.map(function(v) {
        return v > 70 ? '#ef4444' : v < 30 ? '#22c55e' : '#8b5cf6';
    });
    var pointStyles = data.map(function(v) {
        return v > 70 ? 'triangle' : v < 30 ? 'triangleRotated' : 'circle';
    });
    if (canvas && data.length > 0) {
        var latest = data[data.length - 1];
        canvas.setAttribute('aria-label', 'RSI(14) chart. Latest value: ' + latest.toFixed(2) + '. Overbought >70, Oversold <30.');
    }

    _charts.rsi = new Chart(canvas, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'RSI 14',
                data: data,
                borderColor: '#8b5cf6',
                backgroundColor: 'rgba(139,92,246,0.08)',
                fill: true,
                tension: 0.3,
                pointRadius: 2,
                pointHoverRadius: 4,
                pointStyle: pointStyles,
                pointBackgroundColor: pointColors
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        title: function(items) {
                            if (!items || !items.length) return '';
                            var idx = items[0].dataIndex;
                            return fmtDate(history[idx].calculationDate || history[idx].date);
                        },
                        label: function(ctx) {
                            var v = ctx.parsed.y;
                            var tag = '';
                            if (v > 70) tag = ' (Overbought)';
                            else if (v < 30) tag = ' (Oversold)';
                            return 'RSI 14: ' + v.toFixed(2) + tag;
                        }
                    }
                }
            },
            scales: {
                x: {
                    ticks: {
                        maxTicksLimit: 10,
                        callback: function(val) {
                            if (typeof val !== 'string') return val;
                            return shortDate(val);
                        }
                    }
                },
                y: {
                    min: 0,
                    max: 100,
                    ticks: { stepSize: 10 },
                    grid: {
                        color: function(ctx) {
                            var v = ctx.tick.value;
                            if (v === 70) return 'rgba(239,68,68,0.4)';
                            if (v === 50) return 'rgba(107,114,128,0.4)';
                            if (v === 30) return 'rgba(34,197,94,0.4)';
                            return 'rgba(255,255,255,0.06)';
                        }
                    }
                }
            }
        },
        plugins: [
            {
                id: 'rsiBackgroundZones',
                beforeDraw: function(chart) {
                    var ctx = chart.ctx;
                    var yScale = chart.scales.y;
                    var chartArea = chart.chartArea;
                    if (!yScale || !chartArea) return;
                    var y70 = yScale.getPixelForValue(70);
                    var y30 = yScale.getPixelForValue(30);
                    ctx.save();
                    ctx.fillStyle = 'rgba(239,68,68,0.1)';
                    ctx.fillRect(chartArea.left, chartArea.top, chartArea.right - chartArea.left, y70 - chartArea.top);
                    ctx.fillStyle = 'rgba(34,197,94,0.1)';
                    ctx.fillRect(chartArea.left, y30, chartArea.right - chartArea.left, chartArea.bottom - y30);
                    ctx.restore();
                }
            },
            {
                id: 'rsiCrosshair',
                afterDraw: function(chart) {
                    var activeElements = chart.getActiveElements();
                    if (!activeElements || activeElements.length === 0) return;
                    var ctx = chart.ctx;
                    var point = activeElements[0].element;
                    var x = point.x;
                    var left = chart.chartArea.left, right = chart.chartArea.right, top = chart.chartArea.top, bottom = chart.chartArea.bottom;
                    if (x < left || x > right) return;
                    ctx.save();
                    ctx.beginPath();
                    ctx.strokeStyle = 'rgba(255,255,255,0.5)';
                    ctx.lineWidth = 1;
                    ctx.setLineDash([5, 5]);
                    ctx.moveTo(x, top);
                    ctx.lineTo(x, bottom);
                    ctx.stroke();
                    ctx.restore();
                }
            },
            {
                id: 'rsiLatestLabel',
                afterDraw: function(chart) {
                    var dataset = chart.data.datasets[0];
                    var data = dataset.data;
                    if (!data || data.length === 0) return;
                    var latest = data[data.length - 1];
                    var ctx = chart.ctx;
                    var xScale = chart.scales.x;
                    var yScale = chart.scales.y;
                    if (!xScale || !yScale) return;
                    var index = data.length - 1;
                    var x = xScale.getPixelForTick(index);
                    var y = yScale.getPixelForValue(latest);
                    var left = chart.chartArea.left, right = chart.chartArea.right, top = chart.chartArea.top, bottom = chart.chartArea.bottom;
                    if (x < left || x > right) return;
                    ctx.save();
                    ctx.font = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif';
                    ctx.fillStyle = latest > 70 ? '#ef4444' : latest < 30 ? '#22c55e' : '#8b5cf6';
                    ctx.textAlign = 'left';
                    ctx.textBaseline = 'middle';
                    ctx.fillText(latest.toFixed(2), x + 6, y);
                    ctx.restore();
                }
            },
            {
                id: 'rsiRefLines',
                afterDraw: function(chart) {
                    var ctx = chart.ctx;
                    var area = chart.chartArea;
                    if (!area) return;
                    var top = area.top, bottom = area.bottom, left = area.left, right = area.right;
                    var yScale = chart.scales.y;
                    if (!yScale) return;
                    var refs = [
                        { value: 70, color: '#ef4444', dash: [6, 6], label: 'Overbought 70' },
                        { value: 50, color: '#6b7280', dash: [3, 3], label: '' },
                        { value: 30, color: '#22c55e', dash: [6, 6], label: 'Oversold 30' }
                    ];
                    ctx.save();
                    for (var i = 0; i < refs.length; i++) {
                        var ref = refs[i];
                        var y = yScale.getPixelForValue(ref.value);
                        if (y < top - 5 || y > bottom + 5) continue;
                        ctx.beginPath();
                        ctx.setLineDash(ref.dash);
                        ctx.strokeStyle = ref.color;
                        ctx.lineWidth = 1.5;
                        ctx.moveTo(left, y);
                        ctx.lineTo(right, y);
                        ctx.stroke();
                        if (ref.label) {
                            ctx.setLineDash([]);
                            ctx.fillStyle = ref.color;
                            ctx.font = '11px -apple-system, BlinkMacSystemFont, sans-serif';
                            ctx.textAlign = 'right';
                            ctx.textBaseline = 'bottom';
                            ctx.fillText(ref.label, right - 6, y - 3);
                        }
                    }
                }
            }
        ]
    });
}

// ─── Support & Resistance Chart ──────────────────────────────────────────────
function renderSupportResistanceChart(prices, srData) {
    destroyChart('supportResistance');
    const canvas = document.getElementById('chartSupportResistance');
    if (!canvas) return;
    if (!prices.length) {
        showChartMsg('chartSupportResistance', 'No price data for S/R chart');
        return;
    }
    showChartMsg('chartSupportResistance');

    const sortedPrices = [...prices].sort((a, b) => new Date(a.priceDate) - new Date(b.priceDate));
    const labels = sortedPrices.map(p => p.priceDate);
    const data = sortedPrices.map(p => parseFloat(p.closingPrice));

    const annotations = {};
    if (srData) {
        let lineIndex = 0;
        if (srData.majorLevels) {
            srData.majorLevels.forEach(ml => {
                const id = 'major_' + lineIndex++;
                const color = ml.type === 'support' ? 'rgba(34,197,94,0.5)' : 'rgba(239,68,68,0.5)';
                annotations[id] = {
                    type: 'line',
                    yMin: parseFloat(ml.price),
                    yMax: parseFloat(ml.price),
                    borderColor: color,
                    borderWidth: 2.5,
                    borderDash: [],
                    label: {
                        display: true,
                        content: (ml.type === 'support' ? 'S' : 'R') + ' (' + ml.touches + '×)',
                        position: 'end',
                        backgroundColor: color,
                        color: '#fff',
                        font: { size: 10, weight: 'bold' },
                        padding: { top: 2, bottom: 2, left: 4, right: 4 }
                    }
                };
            });
        }
        if (srData.pivots) {
            const pivotEntries = [
                ['pivot', 'P', 'rgba(59,130,246,0.8)'],
                ['s1', 'S1', 'rgba(34,197,94,0.7)'],
                ['s2', 'S2', 'rgba(34,197,94,0.5)'],
                ['s3', 'S3', 'rgba(34,197,94,0.35)'],
                ['r1', 'R1', 'rgba(239,68,68,0.7)'],
                ['r2', 'R2', 'rgba(239,68,68,0.5)'],
                ['r3', 'R3', 'rgba(239,68,68,0.35)']
            ];
            pivotEntries.forEach(([key, label, color]) => {
                const val = srData.pivots[key];
                if (val != null) {
                    const id = 'pivot_' + key;
                    annotations[id] = {
                        type: 'line',
                        yMin: parseFloat(val),
                        yMax: parseFloat(val),
                        borderColor: color,
                        borderWidth: 2,
                        borderDash: [4, 3],
                        label: {
                            display: true,
                            content: label,
                            position: 'start',
                            backgroundColor: color,
                            color: '#fff',
                            font: { size: 10, weight: 'bold' },
                            padding: { top: 1, bottom: 1, left: 3, right: 3 }
                        }
                    };
                }
            });
        }
        if (srData.swingHighs) {
            srData.swingHighs.forEach((sh, i) => {
                annotations['sh_' + i] = {
                    type: 'line',
                    yMin: parseFloat(sh.price),
                    yMax: parseFloat(sh.price),
                    borderColor: 'rgba(239,68,68,0.3)',
                    borderWidth: 1,
                    borderDash: [3, 3],
                    label: {
                        display: i < 3,
                        content: 'SH ' + fmtPrice(sh.price),
                        position: 'start',
                        color: 'rgba(239,68,68,0.7)',
                        font: { size: 8 },
                        padding: { top: 1, bottom: 1, left: 2, right: 2 }
                    }
                };
            });
        }
        if (srData.swingLows) {
            srData.swingLows.forEach((sl, i) => {
                annotations['sl_' + i] = {
                    type: 'line',
                    yMin: parseFloat(sl.price),
                    yMax: parseFloat(sl.price),
                    borderColor: 'rgba(34,197,94,0.3)',
                    borderWidth: 1,
                    borderDash: [3, 3],
                    label: {
                        display: i < 3,
                        content: 'SL ' + fmtPrice(sl.price),
                        position: 'start',
                        color: 'rgba(34,197,94,0.7)',
                        font: { size: 8 },
                        padding: { top: 1, bottom: 1, left: 2, right: 2 }
                    }
                };
            });
        }
    }

    _charts.supportResistance = new Chart(canvas, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'Close (₹)',
                data,
                borderColor: '#3b82f6',
                backgroundColor: 'rgba(59,130,246,0.08)',
                fill: true,
                tension: 0.3,
                pointRadius: 2,
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: { label: ctx => '₹' + Number(ctx.raw).toLocaleString('en-IN', { minimumFractionDigits: 2 }) }
                },
                annotation: { annotations }
            },
            scales: {
                x: { ticks: { maxTicksLimit: 12 } },
                y: { position: 'right', ticks: { callback: v => '₹' + Number(v).toLocaleString('en-IN') } }
            }
        }
    });
}

function renderSrLevelTable(srData) {
    const el = document.getElementById('srLevelContent');
    if (!el) return;
    if (!srData) {
        el.innerHTML = '<p class="text-secondary text-center py-4">No S/R data. Click Refresh to calculate levels for this stock.</p>';
        return;
    }

    const fmtP = v => v != null ? fmtPrice(v) : '--';
    const calcDate = srData.calculationDate || '--';

    const pivots = srData.pivots || {};
    const pivotHtml = `
        <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
            <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                <i class="fas fa-crosshairs text-blue-400"></i>Pivot Points (20-day)
            </h4>
            <div class="space-y-2">
                <div class="flex justify-between items-center">
                    <span class="text-xs text-secondary">Pivot (P)</span>
                    <span class="text-sm font-bold text-blue-400">${fmtP(pivots.pivot)}</span>
                </div>
                <div class="flex justify-between items-center">
                    <span class="text-xs text-green-400">S1 / S2 / S3</span>
                    <span class="text-sm font-semibold text-green-400">${fmtP(pivots.s1)} / ${fmtP(pivots.s2)} / ${fmtP(pivots.s3)}</span>
                </div>
                <div class="flex justify-between items-center">
                    <span class="text-xs text-red-400">R1 / R2 / R3</span>
                    <span class="text-sm font-semibold text-red-400">${fmtP(pivots.r1)} / ${fmtP(pivots.r2)} / ${fmtP(pivots.r3)}</span>
                </div>
            </div>
        </div>`;

    const swingHighs = (srData.swingHighs || []);
    const swingLows = (srData.swingLows || []);
    const swingHtml = `
        <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
            <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                <i class="fas fa-arrow-trend-up text-orange-400"></i>20-Day Swings
            </h4>
            <div class="space-y-2">
                <div class="flex justify-between items-center">
                    <span class="text-xs text-secondary">Swing Highs</span>
                    <span class="text-sm font-semibold text-red-400">${swingHighs.length > 0 ? swingHighs.map(s => fmtP(s.price)).join(', ') : '--'}</span>
                </div>
                <div class="flex justify-between items-center">
                    <span class="text-xs text-secondary">Swing Lows</span>
                    <span class="text-sm font-semibold text-green-400">${swingLows.length > 0 ? swingLows.map(s => fmtP(s.price)).join(', ') : '--'}</span>
                </div>
            </div>
        </div>`;

    const majorLevels = (srData.majorLevels || []);
    let majorHtml = `
        <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4 sm:col-span-2">
            <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                <i class="fas fa-layer-group text-purple-400"></i>Major Historical Levels
            </h4>`;
    if (majorLevels.length > 0) {
        majorHtml += `<div class="space-y-2">`;
        majorLevels.forEach(ml => {
            const color = ml.type === 'support' ? 'text-green-400' : 'text-red-400';
            const icon = ml.type === 'support' ? 'fa-arrow-up' : 'fa-arrow-down';
            majorHtml += `
                <div class="flex justify-between items-center">
                    <span class="text-xs flex items-center gap-1">
                        <i class="fas ${icon} ${color}"></i>
                        <span class="text-secondary">${ml.type === 'support' ? 'Support' : 'Resistance'} (${ml.touches}×)</span>
                    </span>
                    <span class="text-sm font-bold ${color}">${fmtP(ml.price)}</span>
                </div>`;
        });
        majorHtml += `</div>`;
    } else {
        majorHtml += `<p class="text-xs text-secondary">No major levels detected (need 2+ touches)</p>`;
    }
    majorHtml += `</div>`;

    el.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            ${pivotHtml}
            ${swingHtml}
            ${majorLevels.length > 0 ? majorHtml : ''}
        </div>
        <div class="text-xs text-secondary text-right pt-3 mt-3 border-t border-gray-700">
            Calculated: ${calcDate}
        </div>`;
}
