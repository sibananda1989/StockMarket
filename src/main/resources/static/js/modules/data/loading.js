// ─── Data Loading & Cached API Wrappers ──────────────────────────────────────
// Depends on: modules/state.js, modules/charts/line-charts.js, modules/charts/candlestick.js

// ─── Cached API Wrappers ────────────────────────────────────────────────────
function getCachedPriceHistory(stockId, from, to, days) {
    const key = days + '-' + stockId;
    if (_cachedPriceHistory[key]) return Promise.resolve(_cachedPriceHistory[key]);
    const p = getPriceHistory(stockId, from, to).then(res => {
        _cachedPriceHistory[key] = res;
        return res;
    }).catch(e => { console.error('Price history fetch failed:', e); return null; });
    _cachedPriceHistory[key] = p;
    return p;
}

function getCachedSmc(stockId, days) {
    const key = days + '-' + stockId;
    if (_cachedSmc[key]) return Promise.resolve(_cachedSmc[key]);
    const p = getSMCPatterns(stockId, days).then(res => {
        _cachedSmc[key] = res;
        return res;
    }).catch(() => null);
    _cachedSmc[key] = p;
    return p;
}

function getCachedSignalHistory(stockId, days) {
    const key = days + '-' + stockId;
    if (_cachedSignalHistory[key]) return Promise.resolve(_cachedSignalHistory[key]);
    const p = getSignalHistory(stockId, days).then(res => {
        _cachedSignalHistory[key] = res;
        return res;
    }).catch(e => { console.error('Signal history fetch failed:', e); return null; });
    _cachedSignalHistory[key] = p;
    return p;
}

function populateDayRange(prices) {
    const select = document.getElementById('dayRange');
    if (!select || !prices || !prices.length) return;
    const current = parseInt(select.value) || 365;
    const last = new Date(prices[0].priceDate + 'T00:00:00');
    const first = new Date(prices[prices.length - 1].priceDate + 'T00:00:00');
    const maxDays = Math.max(30, Math.ceil((last - first) / (1000 * 60 * 60 * 24)));
    const values = [];
    for (const v of [30, 60, 90, 180, 365]) {
        if (v <= maxDays) values.push(v);
    }
    if (!values.includes(maxDays) && maxDays > 0) values.push(maxDays);
    select.innerHTML = '';
    for (const v of values) {
        const opt = document.createElement('option');
        opt.value = v;
        opt.textContent = v >= 365 ? (v === 365 ? '1 year' : `${v/365} years`) : `${v} days`;
        select.appendChild(opt);
    }
    select.value = current <= maxDays ? current : maxDays;
}

// ─── Stock Data Loading ──────────────────────────────────────────────────────
async function loadStockData() {
    const gen = _loadAllGen;
    _cachedPriceHistory = {};
    _cachedSignalHistory = {};
    console.log('[StockDetail] loadStockData start, gen=' + gen + ', symbol=' + _symbol + ', stockId=' + _stockId);
    try {
        const [latestPrice, latestRsi, fiidiiData, eventsData] = await Promise.all([
            getLatestPrice(_stockId).catch(() => null),
            getLatestRsi(_stockId).catch(() => null),
            getFiiDiiData().catch(() => null),
            _symbol ? getEventData(_symbol).catch(() => null) : Promise.resolve(null),
        ]);
        if (gen !== _loadAllGen) return;
        if (latestRsi && latestRsi.status === 'success' && latestRsi.data) {
            _latestRsiValue = latestRsi.data.rsi14;
        }
        updateKpis(latestPrice, latestRsi, _stockData);
        getPriceHistory(_stockId).then(res => {
            if (gen !== _loadAllGen) return;
            const prices = res?.data;
            if (!prices || !prices.length) return;
            const el = document.getElementById('heroKpiRecords');
            if (el) el.textContent = prices.length + ' days';
            populateDayRange(prices);
        }).catch(() => {});
        updateFiidiiBar(fiidiiData);
        updateEventsWarning(eventsData);
        updateHoldingDetails();
        if (gen !== _loadAllGen) return;
        loadAllCharts();
        getRsiHistory(_stockId, parseInt(document.getElementById('dayRange').value) || 365)
            .then(res => {
                if (gen !== _loadAllGen) return;
                _cachedRsiHistory = (res?.data || []).filter(r => r.rsi14 != null);
            })
            .catch(() => {});
        const days = parseInt(document.getElementById('dayRange').value) || 365;
        const toDateStr = new Date().toISOString().slice(0, 10);
        const fromDate = new Date();
        fromDate.setDate(fromDate.getDate() - days);
        const fromDateStr = fromDate.toISOString().slice(0, 10);
        getAllIndicatorHistory(_stockId, fromDateStr, toDateStr).then(res => {
            _cachedIndicatorsByDate = {};
            if (res && res.length) {
                res.forEach(ind => {
                    const date = ind.calculationDate;
                    if (!_cachedIndicatorsByDate[date]) {
                        _cachedIndicatorsByDate[date] = {};
                    }
                    _cachedIndicatorsByDate[date][ind.type] = ind.value;
                });
            }
        }).catch(() => {});
    } catch (error) {
        console.error('Error loading stock data:', error);
        showErrorState();
    }
}

async function loadAll() {
    if (!_stockId) {
        const urlParams = new URLSearchParams(window.location.search);
        _stockId = urlParams.get('id');
    }
    if (!_stockId) return;
    if (!_symbol) {
        try {
            const res = await getStockById(_stockId);
            if (res.status === 'success' && res.data) {
                _symbol = res.data.stock.symbol || '';
            }
        } catch (_) {}
    }
    _loadAllGen++;
    _cachedSignal = null;
    _cachedBacktest = null;
    _cachedBacktestDays = null;
    _cachedPriceHistory = {};
    _cachedSignalHistory = {};
    _cachedIndicatorsByDate = {};
    _cachedFvgEntries = [];
    _cachedRsiHistory = [];
    _cachedSmc = {};
    await loadStockData();
}

// ─── Chart Loading Orchestration ─────────────────────────────────────────────
async function loadAllCharts() {
    const gen = _loadAllGen;
    const days = parseInt(document.getElementById('dayRange').value) || 365;

    const chartLoaders = [
        { fn: () => gen !== _loadAllGen ? null : loadMovementTab(days), name: 'Price Movement' },
        { fn: () => gen !== _loadAllGen ? null : loadCandlestickTab(days), name: 'Candlestick' },
        { fn: () => gen !== _loadAllGen ? null : loadPnlTab(days), name: 'P&L Trend' },
        { fn: () => gen !== _loadAllGen ? null : loadInvValueTab(days), name: 'Investment vs Value' },
        { fn: () => gen !== _loadAllGen ? null : loadRsiTab(days), name: 'RSI' },
        { fn: () => gen !== _loadAllGen ? null : loadSupportResistanceTab(days), name: 'Support & Resistance' },
        { fn: () => gen !== _loadAllGen ? null : loadInsightsTab(), name: 'Research Insights' },
        { fn: () => gen !== _loadAllGen ? null : loadFundamentalsTab(), name: 'Fundamentals' }
    ];

    const results = await Promise.allSettled(chartLoaders.map(l => l.fn()));
    for (const [i, result] of results.entries()) {
        if (result.status === 'rejected') {
            console.error(`Error loading ${chartLoaders[i].name}:`, result.reason);
        }
    }
}

// ─── Chart Loaders ───────────────────────────────────────────────────────────
async function loadMovementTab(days) {
    const spinner = document.getElementById('ltpSpinner');
    try {
        if (spinner) spinner.classList.remove('hidden');
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - days);
        const res = await getCachedPriceHistory(_stockId, start.toISOString().split('T')[0], end.toISOString().split('T')[0], days);
        const prices = (res?.data || []).filter(p => p.closingPrice != null);
        renderLtpChart(prices);
    } catch (e) {
        console.error('Movement tab error:', e);
        showChartMsg('chartLtp', 'Failed to load price data');
    } finally {
        if (spinner) spinner.classList.add('hidden');
    }
}

async function loadCandlestickTab(days) {
    const spinner = document.getElementById('candlestickSpinner');
    if (spinner) spinner.classList.remove('hidden');
    try {
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - days);
        const fmt = d => d.toISOString().split('T')[0];

        let activeStrategyNames = null;
        try {
            const strategyRes = await getStrategyConfigs();
            if (strategyRes && strategyRes.status === 'success' && strategyRes.data) {
                activeStrategyNames = strategyRes.data
                    .filter(s => s.active)
                    .map(s => s.strategyName);
                console.log('[Candlestick] Active strategies:', activeStrategyNames);
            }
        } catch (e) {
            console.warn('[Candlestick] Failed to fetch active strategies:', e);
        }

        const shouldUseMulti = activeStrategyNames && activeStrategyNames.length > 0;

        console.log('[Candlestick] Date range: ' + fmt(start) + ' to ' + fmt(end) + ' (' + days + ' days)');

        const [priceRes, signalHistoryRes, currentSignalRes, srRes, smcRes] = await Promise.all([
            getPriceHistory(_stockId, fmt(start), fmt(end)).catch(e => { console.error('[Candlestick] Price fetch failed:', e); return null; }),
            shouldUseMulti
                ? getMultiStrategySignalHistory(_stockId, days, activeStrategyNames).catch(e => {
                    console.warn('[Candlestick] Multi-strategy history failed, falling back to legacy:', e);
                    return getSignalHistory(_stockId, days).catch(e2 => { console.error('[Candlestick] Signal history fallback failed:', e2); return null; });
                  })
                : getSignalHistory(_stockId, days).catch(e => { console.error('[Candlestick] Signal history fetch failed:', e); return null; }),
            shouldUseMulti
                ? getMultiStrategySignal(_stockId, activeStrategyNames).catch(e => { console.error('[Candlestick] Multi-strategy signal failed:', e); return null; })
                : getStockSignal(_stockId).catch(e => { console.error('[Candlestick] Current signal fetch failed:', e); return null; }),
            getSupportResistance(_stockId, days).catch(() => null),
            getSMCPatterns(_stockId, days).catch(() => null)
        ]);
        const prices = (priceRes?.data || []).filter(p => p.closingPrice != null);
        const signalHistory = signalHistoryRes?.status === 'success' ? signalHistoryRes.data : null;
        console.log('[Candlestick] Prices returned: ' + prices.length + ' records, range: ' +
            (prices.length > 0 ? prices[0].priceDate + ' to ' + prices[prices.length - 1].priceDate : 'empty'));

        let currentSignal = null;
        if (currentSignalRes?.status === 'success' && currentSignalRes.data) {
            const data = currentSignalRes.data;
            if (data.finalSignal) {
                currentSignal = {
                    recommendation: data.finalSignal,
                    compositeScore: Math.round(data.score),
                    strategyBreakdown: data.breakdown || [],
                    buyThreshold: data.buyThreshold || 5,
                    sellThreshold: data.sellThreshold || -3
                };
            } else {
                currentSignal = data;
            }
        }

        let srData = srRes && srRes.status === 'success' ? srRes.data : null;
        if (!srData) {
            await calculateSupportResistance(_stockId).catch(() => {});
            const retryRes = await getSupportResistance(_stockId, days).catch(() => null);
            srData = retryRes && retryRes.status === 'success' ? retryRes.data : null;
        }
        console.log('[Candlestick] prices:', prices.length, 'signalHistory:', signalHistory?.length || 0, 'currentSignal:', currentSignal?.recommendation, 'activeStrategies:', activeStrategyNames?.length || 0, 'srData:', !!srData);
        if (smcRes && smcRes.status === 'success' && smcRes.data && smcRes.data.fairValueGaps) {
            _cachedFvgEntries = smcRes.data.fairValueGaps;
        } else {
            _cachedFvgEntries = [];
        }
        renderActiveStrategiesBadge(activeStrategyNames, currentSignal);

        let txRecords = null;
        if (_portfolioId) {
            try {
                const txRes = await getTransactions(_portfolioId, _stockId);
                txRecords = txRes?.status === 'success' ? txRes.data : null;
                console.log('[Candlestick] Portfolio tx fetch result:', txRecords?.length || 0, 'transactions for portfolio', _portfolioId);
            } catch (e) {
                console.warn('[Candlestick] Portfolio tx fetch failed:', e);
            }
        }
        if (!txRecords || txRecords.length === 0) {
            try {
                const txRes = await getAllTransactionsByStock(_stockId);
                txRecords = txRes?.status === 'success' ? txRes.data : null;
                console.log('[Candlestick] Aggregate tx fetch result:', txRecords?.length || 0, 'transactions (fallback)');
            } catch (e) {
                console.warn('[Candlestick] Aggregate tx fetch failed:', e);
            }
        }
        renderCandlestickChart(prices, signalHistory, currentSignal, srData, txRecords);
    } catch (e) {
        console.error('Candlestick tab error:', e);
        showChartMsg('chartCandlestick', 'Failed to load candlestick data');
    } finally {
        if (spinner) spinner.classList.add('hidden');
    }
}

async function loadPnlTab(days) {
    const gen = _loadAllGen;
    try {
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - days);
        const fmt = d => d.toISOString().split('T')[0];
        const priceRes = await getCachedPriceHistory(_stockId, fmt(start), fmt(end), days);
        const prices = (priceRes?.data || []).filter(p => p.closingPrice != null);

        const qty = _stockData?.quantity;
        const avgPrice = _stockData?.avgPrice;
        if (qty && avgPrice && prices.length) {
            const dailyPnl = prices.map(p => ({
                date: p.priceDate,
                closingPrice: parseFloat(p.closingPrice),
                pnl: (parseFloat(p.closingPrice) - parseFloat(avgPrice)) * qty
            }));

            const latestDate = prices[prices.length - 1].priceDate;
            const daysSince = Math.floor((Date.now() - new Date(latestDate + 'T00:00:00')) / 86400000);
            const pnlDescEl = document.querySelector('#chartPnl').closest('.card')?.querySelector('.text-secondary.mb-4');
            if (pnlDescEl && daysSince > 3) {
                pnlDescEl.innerHTML = 'P&amp;L based on last close: <strong>' + latestDate + '</strong> <span class="text-yellow-400">(stale — ' + daysSince + 'd old)</span>';
            }

            renderPnlCharts(dailyPnl);
        } else {
            renderPnlCharts([]);
        }
        if (gen !== _loadAllGen) return;
    } catch (e) {
        console.error('P&L tab error:', e);
        showChartMsg('chartPnl', 'No P&L data');
        showChartMsg('chartPnlBar', 'No P&L data');
    }
}

async function loadInvValueTab(days) {
    const gen = _loadAllGen;
    try {
        const qty = _stockData?.quantity;
        const avgPrice = _stockData?.avgPrice;
        if (!qty || !avgPrice) {
            showChartMsg('chartInvValue', 'Add quantity and avg price to compute investment vs current value');
            return;
        }
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - days);
        const fmt = d => d.toISOString().split('T')[0];
        const res = await getCachedPriceHistory(_stockId, fmt(start), fmt(end), days);
        const prices = (res?.data || []).filter(p => p.closingPrice != null);
        const inv = parseFloat(avgPrice) * qty;
        const dailyData = prices.map(p => ({
            date: p.priceDate,
            investment: inv,
            currentValue: parseFloat(p.closingPrice) * qty
        }));
        renderInvValueChart(dailyData);
        if (gen !== _loadAllGen) return;
    } catch (e) {
        console.error('Inv vs Value tab error:', e);
        showChartMsg('chartInvValue', 'No investment data');
    }
}

async function loadRsiTab(days) {
    const gen = _loadAllGen;
    try {
        const res = await getRsiHistory(_stockId, days);
        const history = (res.data || [])
            .filter(r => r.rsi14 != null)
            .reverse();
        renderRsiChart(history);
        if (gen !== _loadAllGen) return;
    } catch (e) {
        console.error('RSI tab error:', e);
        showChartMsg('chartRsi', 'No RSI data');
    }
}

async function loadSupportResistanceTab(days) {
    const gen = _loadAllGen;
    const spinner = document.getElementById('srSpinner');
    if (spinner) spinner.classList.remove('hidden');
    try {
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - days);
        const fmt = d => d.toISOString().split('T')[0];
        const [priceRes, srRes] = await Promise.all([
            getCachedPriceHistory(_stockId, fmt(start), fmt(end), days),
            getSupportResistance(_stockId, days).catch(() => null)
        ]);
        const prices = (priceRes?.data || []).filter(p => p.closingPrice != null);
        const srData = srRes && srRes.status === 'success' ? srRes.data : null;

        if (!srData) {
            await calculateSupportResistance(_stockId).catch(() => {});
            const retryRes = await getSupportResistance(_stockId, days).catch(() => null);
            const retryData = retryRes && retryRes.status === 'success' ? retryRes.data : null;
            renderSupportResistanceChart(prices, retryData);
            renderSrLevelTable(retryData);
        } else {
            renderSupportResistanceChart(prices, srData);
            renderSrLevelTable(srData);
        }
        if (gen !== _loadAllGen) return;
    } catch (e) {
        console.error('S/R tab error:', e);
        showChartMsg('chartSupportResistance', 'No support/resistance data — click Refresh to calculate');
        const content = document.getElementById('srLevelContent');
        if (content) content.innerHTML = '<p class="text-secondary text-center py-4">No S/R data available</p>';
    } finally {
        if (spinner) spinner.classList.add('hidden');
    }
}

async function refreshSupportResistance() {
    const btn = document.getElementById('srRefreshBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-1"></i>Calc...'; }
    try {
        await calculateSupportResistance(_stockId);
        await loadSupportResistanceTab(parseInt(document.getElementById('dayRange').value) || 365);
    } catch (e) {
        console.error('S/R refresh error:', e);
        const content = document.getElementById('srLevelContent');
        if (content) content.innerHTML = '<p class="text-danger text-center py-4">⚠ Failed to calculate S/R levels</p>';
    } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-sync-alt mr-1"></i>Refresh'; }
    }
}

function showErrorState() {
    document.getElementById('stockTitle').textContent = '⚠ Error loading data';
}
