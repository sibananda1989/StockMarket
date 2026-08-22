// ─── Backtest Module ─────────────────────────────────────────────────────────
// Depends on: modules/state.js, modules/charts/line-charts.js, api.js

async function runBacktest() {
    const stockId = _stockId;
    if (stockId == null) {
        alert('No stock selected');
        return;
    }
    try {
        showBacktestLoading(true);
        const res = await getBacktestData(stockId, true, 0.02);
        renderBacktestResults(res.data);
        showBacktestLoading(false);
    } catch (e) {
        console.error('Backtest error:', e);
        alert('Backtest failed: ' + (e.message || 'Unknown error'));
        showBacktestLoading(false);
    }
}

async function runBacktestWithParameters() {
    const stockId = _stockId;
    if (stockId == null) {
        alert('No stock selected');
        return;
    }
    const positionSize = parseFloat(document.getElementById('positionSizeInput').value) || 0.02;
    const trailingStop = parseFloat(document.getElementById('trailingStopInput').value) || 2;
    const riskFreeRate = parseFloat(document.getElementById('riskFreeRateInput').value) || 2;
    const days = parseInt(document.getElementById('backtestDaysInput').value) || 365;

    try {
        showBacktestLoading(true);
        const res = await getBacktestData(stockId, true, positionSize, days, riskFreeRate, trailingStop);
        renderBacktestResults(res.data);
        showBacktestLoading(false);
    } catch (e) {
        console.error('Backtest error:', e);
        alert('Backtest failed: ' + (e.message || 'Unknown error'));
        showBacktestLoading(false);
    }
}

function renderBacktestResults(r) {
    setText('btWinRate', r.winRate != null ? r.winRate.toFixed(1) + '%' : '--');
    setText('btTotalReturn', r.totalReturn != null ? (r.totalReturn >= 0 ? '+' : '') + r.totalReturn.toFixed(2) + '%' : '--');
    setText('btMaxDrawdown', r.maxDrawdown != null ? r.maxDrawdown.toFixed(2) + '%' : '--');
    setText('btTradeCount', r.totalTrades != null ? Math.floor(r.totalTrades / 2) : '0');
    setText('btWinningTrades', r.winningTrades != null ? r.winningTrades : '--');
    setText('btLosingTrades', r.losingTrades != null ? r.losingTrades : '--');
    setText('btStoppedOut', r.stoppedOutTrades != null ? r.stoppedOutTrades : '--');
    setText('btSignalExits', r.signalExits != null ? r.signalExits : '--');
    setText('btEventsSkipped', r.eventsSkipped != null ? r.eventsSkipped : '--');
    setText('btFinalValue', r.finalPortfolioValue != null ? fmtPrice(r.finalPortfolioValue) : '--');
    setText('btLongestDrawdownDays', r.longestDrawdownDays != null ? r.longestDrawdownDays : '0');

    setText('btSharpeRatio', r.sharpeRatio != null ? r.sharpeRatio.toFixed(2) : '--');
    setText('btCalmarRatio', r.calmarRatio != null ? r.calmarRatio.toFixed(2) : '--');
    setText('btSortinoRatio', r.sortinoRatio != null ? r.sortinoRatio.toFixed(2) : '--');
    setText('btProfitFactor', r.profitFactor != null ? r.profitFactor.toFixed(2) : '--');
    setText('btAvgWinningTrade', r.avgWinningTrade != null ? fmtPrice(r.avgWinningTrade) : '--');
    setText('btAvgLosingTrade', r.avgLosingTrade != null ? fmtPrice(r.avgLosingTrade) : '--');
    setText('btLargestWinnerTrade', r.largestWinnerCount != null ? r.largestWinnerCount : '0');
    setText('btLargestLoserTrade', r.largestLoserCount != null ? r.largestLoserCount : '0');

    const retEl = document.getElementById('btTotalReturn');
    if (retEl && r.totalReturn != null) {
        const v = parseFloat(r.totalReturn);
        retEl.className = 'text-2xl font-bold ' + (v >= 0 ? 'text-green-400' : 'text-red-400');
    }

    destroyChart('backtestEquity');
    const eqCanvas = document.getElementById('backtestEquityCurve');
    if (eqCanvas && r.equityCurve && r.equityCurve.length > 0) {
        const labels = r.equityCurve.map((_, i) => i + 1);
        _charts.backtestEquity = new Chart(eqCanvas, {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: 'Portfolio Value (₹)',
                    data: r.equityCurve,
                    borderColor: '#22c55e',
                    backgroundColor: 'rgba(34,197,94,0.08)',
                    fill: true,
                    tension: 0.3,
                    pointRadius: 1
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
    }

    renderTradeHistory(r.tradeHistory);
}

function renderTradeHistory(trades) {
    const tbody = document.getElementById('tradeHistoryBody');
    if (!tbody) return;

    if (!trades || trades.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="px-4 py-8 text-center text-secondary"><i class="fas fa-info-circle mr-2"></i>No trades executed during backtest period.</td></tr>';
        return;
    }

    tbody.innerHTML = trades.map(t => {
        const isBuy = t.action === 'BUY';
        const displayAction = isBuy ? 'BUY' : 'EXIT';
        const pnlClass = t.pnl != null ? (t.pnl >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
        const pnlText = t.pnl != null ? (t.pnl >= 0 ? '+' : '') + fmtPrice(t.pnl) : '--';
        const stopLossBadge = t.stopLossHit
            ? '<span class="px-1.5 py-0.5 bg-red-500/20 text-red-400 text-xs rounded">SL</span>'
            : '<span class="text-secondary">—</span>';

        return `<tr class="border-b border-gray-700 hover:bg-gray-700/30 transition-colors">
            <td class="px-4 py-3 text-secondary">${t.exitDate || t.entryDate || '--'}</td>
            <td class="px-4 py-3">
                <span class="px-2 py-0.5 rounded-full text-xs font-semibold ${isBuy ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}">
                    ${displayAction}
                </span>
            </td>
            <td class="px-4 py-3 text-right font-mono">${t.entryPrice ? fmtPrice(t.entryPrice) : '--'}</td>
            <td class="px-4 py-3 text-right font-mono">${t.exitPrice ? fmtPrice(t.exitPrice) : '--'}</td>
            <td class="px-4 py-3 text-right font-mono">${t.quantity ? Number(t.quantity).toLocaleString('en-IN') : '--'}</td>
            <td class="px-4 py-3 text-right font-mono font-semibold ${pnlClass}">${pnlText}</td>
            <td class="px-4 py-3 text-secondary text-xs">${t.exitReason || '--'}</td>
            <td class="px-4 py-3 text-center">${stopLossBadge}</td>
        </tr>`;
    }).join('');
}

function exportEquityData() {
    const equity = window._lastBacktestEquityCurve || [];
    if (equity.length === 0) {
        alert('No equity curve data to export. Run a backtest first.');
        return;
    }

    let csv = 'Day,Portfolio Value\n';
    equity.forEach((v, i) => {
        csv += `${i + 1},${v != null ? Number(v).toFixed(2) : ''}\n`;
    });

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `backtest-${_stockId || 'data'}-equity.csv`;
    a.click();
    URL.revokeObjectURL(url);
}

// Store trades globally for export
const _origRenderBacktestResults = renderBacktestResults;
renderBacktestResults = function(r) {
    window._lastBacktestTrades = r.tradeHistory || [];
    window._lastBacktestEquityCurve = r.equityCurve || [];
    _origRenderBacktestResults(r);
};

function showBacktestLoading(show) {
    const section = document.getElementById('backtestSection');
    const loadingSection = document.getElementById('backtestLoadingSection');
    if (show) {
        if (section) section.style.display = 'block';
        if (loadingSection) {
            loadingSection.classList.remove('hidden');
        }
    } else {
        if (loadingSection) {
            loadingSection.classList.add('hidden');
        }
    }
}
