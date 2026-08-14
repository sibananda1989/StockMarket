// ─── State ──────────────────────────────────────────────────────────────────
const _charts = {};
const _chartOverlays = {};
let _stockId = null;
let _portfolioId = null;
let _symbol = '';
let _stockData = null;
let _latestRsiValue = null;
let _cachedSignal = null;
let _cachedBacktest = null;
let _cachedBacktestDays = null;
let _cachedPriceHistory = {};
let _cachedSignalHistory = {};
let _cachedRsiHistory = [];
let _cachedIndicatorsByDate = {};
let _cachedFvgEntries = [];
let _showBuyArrows = true;
let _showSellArrows = true;
let _csCandlestick = null;
let _lastMarkersFull = [];
let _cachedSmc = {};
let _loadAllGen = 0;

// ─── Formatters (module scope) ────────────────────────────────────────────────
const _inrFormatter = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const _pctFormatter = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

function fmtPrice(val) {
    if (val == null || isNaN(val)) return '--';
    return '₹' + _inrFormatter.format(val);
}

function fmtPct(val) {
    if (val == null || isNaN(val)) return '--';
    const sign = val >= 0 ? '+' : '';
    return sign + _pctFormatter.format(val) + '%';
}

function pnlColor(val) {
    return val != null && val >= 0 ? 'text-green-400' : 'text-red-400';
}

// ─── DOM Ready ──────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function() {
    const urlParams = new URLSearchParams(window.location.search);
    _stockId = urlParams.get('id');
    _portfolioId = urlParams.get('portfolioId') ? parseInt(urlParams.get('portfolioId')) : null;

    if (!_stockId) {
        document.getElementById('stockTitle').textContent = 'Stock not found';
        document.getElementById('stockSubtitle').textContent = '';
        return;
    }

    // Fix history button link
    const historyBtn = document.getElementById('historyBtn');
    if (historyBtn) historyBtn.href = 'stock-history.html?stockId=' + _stockId;

    // Wire the S/R Refresh button (was previously an inline onclick that failed
    // when the function wasn't in global scope)
    const srRefreshBtn = document.getElementById('srRefreshBtn');
    if (srRefreshBtn) srRefreshBtn.addEventListener('click', refreshSupportResistance);

    // Wire the Fundamentals Refresh button
    const fundRefreshBtn = document.getElementById('fundRefreshBtn');
    if (fundRefreshBtn) fundRefreshBtn.addEventListener('click', () => loadFundamentalsTab(true));

    getStockById(_stockId)
        .then(response => {
            if (response.status === 'success' && response.data) {
                const stock = response.data.stock;
                _symbol = stock.symbol || '';
                _stockData = stock;
                document.getElementById('stockTitle').textContent = stock.name || 'Unknown';
                document.getElementById('stockSubtitle').textContent =
                    (_symbol ? _symbol + ' - ' : '') + (stock.sector || '');

                // If a portfolio context is provided, merge that portfolio's holding P&L data
                // Otherwise, try aggregate across all portfolios
                const holdingPromise = _portfolioId
                    ? getPortfolioHoldingByStock(_portfolioId, parseInt(_stockId))
                    : getAggregateHolding(parseInt(_stockId)).catch(() => null);
                holdingPromise
                    .then(holdingRes => {
                        if (holdingRes && holdingRes.status === 'success' && holdingRes.data) {
                            const h = holdingRes.data;
                            Object.assign(_stockData, {
                                quantity: h.quantity,
                                avgPrice: h.avgPrice,
                                investment: h.investment,
                                currentValue: h.currentValue,
                                pnl: h.pnl,
                                pnlPercent: h.pnlPercent
                            });
                        }
                        return loadStockData();
                    })
                    .catch(() => loadStockData());
            } else {
                document.getElementById('stockTitle').textContent = 'Error loading stock';
                document.getElementById('stockSubtitle').textContent = '';
            }
        })
        .catch(error => {
            console.error('Error fetching stock data:', error);
            document.getElementById('stockTitle').textContent = 'Error loading stock';
            document.getElementById('stockSubtitle').textContent = '';
        });
});

// ─── Data Loading ───────────────────────────────────────────────────────────
async function loadStockData() {
    const gen = _loadAllGen;
    // Clear cached API responses to ensure fresh data on every load
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
        // Store RSI value for consistent use across components
        if (latestRsi && latestRsi.status === 'success' && latestRsi.data) {
            _latestRsiValue = latestRsi.data.rsi14;
        }
        updateKpis(latestPrice, latestRsi, _stockData);
        // Fetch price records for Records KPI + populate day range dropdown
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
        // Load all charts (no tabs - stacked vertically)
        if (gen !== _loadAllGen) return;
        loadAllCharts();
        getRsiHistory(_stockId, parseInt(document.getElementById('dayRange').value) || 365)
            .then(res => {
                if (gen !== _loadAllGen) return;
                _cachedRsiHistory = (res?.data || []).filter(r => r.rsi14 != null);
            })
            .catch(() => {});
        // Pre-fetch all technical indicators for candlestick tooltip (per-date data)
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

// ─── Cached API Wrappers ─────────────────────────────────────────────────
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

// Cached SMC patterns (FVG, BOS, CHoCH etc.)
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
    // Calculate max calendar days from first to last price date
    const last = new Date(prices[0].priceDate + 'T00:00:00');
    const first = new Date(prices[prices.length - 1].priceDate + 'T00:00:00');
    const maxDays = Math.max(30, Math.ceil((last - first) / (1000 * 60 * 60 * 24)));
    // Generate option values at common intervals
    const values = [];
    for (const v of [30, 60, 90, 180, 365]) {
        if (v <= maxDays) values.push(v);
    }
    if (!values.includes(maxDays) && maxDays > 0) values.push(maxDays);
    // Rebuild options preserving current selection
    select.innerHTML = '';
    for (const v of values) {
        const opt = document.createElement('option');
        opt.value = v;
        opt.textContent = v >= 365 ? (v === 365 ? '1 year' : `${v/365} years`) : `${v} days`;
        select.appendChild(opt);
    }
    select.value = current <= maxDays ? current : maxDays;
}

async function loadAllCharts() {
    const gen = _loadAllGen;
    const days = parseInt(document.getElementById('dayRange').value) || 365;

    // Load charts in parallel with error isolation
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

    // Backtest disabled — see BacktestController
}

// ─── KPI Updates ────────────────────────────────────────────────────────────
function updateRsiKpi(value) {
    const rsiEl = document.getElementById('kpiRsi');
    if (rsiEl && value != null) {
        const rv = parseFloat(value);
        rsiEl.textContent = rv.toFixed(2);
        const card = rsiEl.closest('.stat-card');
        if (card) {
            card.classList.remove('green', 'red', 'yellow', 'gray');
            card.classList.add(rv > 70 ? 'red' : rv < 30 ? 'green' : 'gray');
        }
    } else if (rsiEl) {
        rsiEl.textContent = '--';
        const card = rsiEl.closest('.stat-card');
        if (card) card.classList.remove('green', 'red', 'yellow', 'gray');
    }

    const heroRsi = document.getElementById('heroKpiRsi');
    if (heroRsi) {
        if (value != null) {
            const rv = parseFloat(value);
            heroRsi.textContent = rv.toFixed(2);
            heroRsi.className = 'text-2xl font-bold ' + (rv > 70 ? 'text-red-400' : rv < 30 ? 'text-green-400' : 'text-orange-400');
        } else {
            heroRsi.textContent = '--';
            heroRsi.className = 'text-2xl font-bold text-orange-400';
        }
    }
}

function updateHeroSignal(signal) {
    const badge = document.getElementById('heroSignalBadge');
    const text = document.getElementById('heroSignalText');
    const score = document.getElementById('heroCompositeScore');
    if (!badge || !text || !score) return;

    if (!signal) {
        badge.className = 'px-5 py-3 rounded-xl font-bold text-white shadow-lg flex items-center gap-3 min-w-[140px] justify-center transition-all duration-300 bg-gradient-to-r from-gray-500 to-gray-600';
        text.textContent = 'NO SIGNAL';
        score.textContent = '--';
        return;
    }

    const rec = signal.recommendation || 'HOLD';
    const compositeScore = signal.compositeScore != null ? signal.compositeScore : 0;
    score.textContent = (compositeScore >= 0 ? '+' : '') + compositeScore;

    const isBuy = rec === 'STRONG BUY' || rec === 'BUY';
    const isSell = rec === 'STRONG SELL' || rec === 'SELL';

    if (isBuy) {
        badge.className = 'px-5 py-3 rounded-xl font-bold text-white shadow-lg flex items-center gap-3 min-w-[140px] justify-center transition-all duration-300 bg-gradient-to-r from-green-500 to-emerald-600';
        text.innerHTML = '<i class="fas fa-thumbs-up mr-2"></i>' + rec;
    } else if (isSell) {
        badge.className = 'px-5 py-3 rounded-xl font-bold text-white shadow-lg flex items-center gap-3 min-w-[140px] justify-center transition-all duration-300 bg-gradient-to-r from-red-500 to-rose-600';
        text.innerHTML = '<i class="fas fa-thumbs-down mr-2"></i>' + rec;
    } else {
        badge.className = 'px-5 py-3 rounded-xl font-bold text-white shadow-lg flex items-center gap-3 min-w-[140px] justify-center transition-all duration-300 bg-gradient-to-r from-gray-500 to-gray-600';
        text.innerHTML = '<i class="fas fa-minus mr-2"></i>' + rec;
    }
}

function updateKpis(priceData, rsiData, stockData) {
    const getVal = (obj, key) => obj && obj.status === 'success' && obj.data ? obj.data[key] : null;

    // LTP
    const cp = getVal(priceData, 'closingPrice');
    const heroLtp = document.getElementById('heroKpiLtp');
    if (heroLtp) heroLtp.textContent = cp != null ? '₹' + parseFloat(cp).toFixed(2) : '--';

    // P&L
    const heroPnl = document.getElementById('heroKpiPnl');
    if (heroPnl && stockData && stockData.pnl != null) {
        const pv = parseFloat(stockData.pnl);
        heroPnl.textContent = '₹' + Number(pv).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        heroPnl.className = 'text-2xl font-bold ' + (pv >= 0 ? 'text-green-400' : 'text-red-400');
    } else if (heroPnl) { heroPnl.textContent = '--'; heroPnl.className = 'text-2xl font-bold text-secondary'; }

    // P&L%
    const heroPnlPct = document.getElementById('heroKpiPnlPct');
    if (heroPnlPct && stockData && stockData.pnlPercent != null) {
        const ppv = parseFloat(stockData.pnlPercent);
        heroPnlPct.textContent = (ppv >= 0 ? '+' : '') + Number(ppv).toFixed(2) + '%';
        heroPnlPct.className = 'text-2xl font-bold ' + (ppv >= 0 ? 'text-green-400' : 'text-red-400');
    } else if (heroPnlPct) { heroPnlPct.textContent = '--'; heroPnlPct.className = 'text-2xl font-bold text-secondary'; }

    // Investment
    const heroInv = document.getElementById('heroKpiInv');
    if (heroInv) {
        if (stockData && stockData.investment != null) {
            heroInv.textContent = '₹' + Number(stockData.investment).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        } else { heroInv.textContent = '--'; }
    }

    // Current Value
    const heroCurr = document.getElementById('heroKpiCurr');
    if (heroCurr) {
        if (stockData && stockData.currentValue != null) {
            heroCurr.textContent = '₹' + Number(stockData.currentValue).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        } else { heroCurr.textContent = '--'; }
    }

    // RSI - use stored value for consistency
    updateRsiKpi(_latestRsiValue);
}

function setPnlKpi(elId, stock, field, fmt) {
    const el = document.getElementById(elId);
    if (!el) return;
    if (stock && stock[field] != null) {
        const v = parseFloat(stock[field]);
        el.textContent = fmt(v);
        const card = el.closest('.stat-card');
        if (card) {
            card.classList.remove('green', 'red');
            card.classList.add(v >= 0 ? 'green' : 'red');
        }
    } else {
        el.textContent = '--';
        const card = el.closest('.stat-card');
        if (card) card.classList.remove('green', 'red');
    }
}

// ─── Charts ────────────────────────────────────────────────────────────────
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
        
        // Fetch active strategies from strategy config
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
            // Use multi-strategy history when active strategies are configured
            shouldUseMulti
                ? getMultiStrategySignalHistory(_stockId, days, activeStrategyNames).catch(e => {
                    console.warn('[Candlestick] Multi-strategy history failed, falling back to legacy:', e);
                    return getSignalHistory(_stockId, days).catch(e2 => { console.error('[Candlestick] Signal history fallback failed:', e2); return null; });
                  })
                : getSignalHistory(_stockId, days).catch(e => { console.error('[Candlestick] Signal history fetch failed:', e); return null; }),
            // Use multi-strategy signal with active strategies for current signal
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

        // Transform multi-strategy result to signal DTO format for chart
        let currentSignal = null;
        if (currentSignalRes?.status === 'success' && currentSignalRes.data) {
            const data = currentSignalRes.data;
            // Check if it's multi-strategy response (has finalSignal property)
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
        // Extract FVG entries from SMC patterns
        if (smcRes && smcRes.status === 'success' && smcRes.data && smcRes.data.fairValueGaps) {
            _cachedFvgEntries = smcRes.data.fairValueGaps;
        } else {
            _cachedFvgEntries = [];
        }
    // Render active strategies badge with populated signal data
    renderActiveStrategiesBadge(activeStrategyNames, currentSignal);
    
    // Fetch transaction markers overlay (portfolio context preferred)
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
    // Fallback to all-portfolio aggregate if no transactions found (regardless of portfolioId)
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

        // Compute per-day P&L from daily close price × quantity − investment
        const qty = _stockData?.quantity;
        const avgPrice = _stockData?.avgPrice;
        if (qty && avgPrice && prices.length) {
            const dailyPnl = prices.map(p => ({
                date: p.priceDate,
                closingPrice: parseFloat(p.closingPrice),
                pnl: (parseFloat(p.closingPrice) - parseFloat(avgPrice)) * qty
            }));

        // Show latest price freshness
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
            .reverse(); // clock starts at the left: oldest → newest
        renderRsiChart(history);
        if (gen !== _loadAllGen) return;
    } catch (e) {
        console.error('RSI tab error:', e);
        showChartMsg('chartRsi', 'No RSI data');
    }
}

// ─── Support & Resistance ──────────────────────────────────────────────────
async function loadSupportResistanceTab(days) {
    const gen = _loadAllGen;
    const spinner = document.getElementById('srSpinner');
    if (spinner) spinner.classList.remove('hidden');
    try {
        // Load price history for the chart + S/R levels in parallel
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

        // If no S/R data exists, auto-calculate once
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
        // Reload after calculation
        await loadSupportResistanceTab(parseInt(document.getElementById('dayRange').value) || 365);
    } catch (e) {
        console.error('S/R refresh error:', e);
        const content = document.getElementById('srLevelContent');
        if (content) content.innerHTML = '<p class="text-danger text-center py-4">⚠ Failed to calculate S/R levels</p>';
    } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-sync-alt mr-1"></i>Refresh'; }
    }
}

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

    // Build annotation lines for S/R levels
    const annotations = {};
    if (srData) {
        let lineIndex = 0;
        // Major historical levels (thickest)
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
        // Pivot levels (dashed)
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
        // Swing highs (dotted red)
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
                        display: i < 3, // Only show first 3 to avoid clutter
                        content: 'SH ' + fmtPrice(sh.price),
                        position: 'start',
                        color: 'rgba(239,68,68,0.7)',
                        font: { size: 8 },
                        padding: { top: 1, bottom: 1, left: 2, right: 2 }
                    }
                };
            });
        }
        // Swing lows (dotted green)
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

    // Pivot levels
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

    // Swing levels
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

    // Major historical levels
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

async function loadInsightsTab() {
    try {
        const days = parseInt(document.getElementById('dayRange').value) || 365;
        const signal = await getStockSignal(_stockId).catch(() => null);
        const signalData = signal && signal.status === 'success' ? signal.data : null;
        _cachedSignal = signalData;
        // Override stale RSI KPI with signal's RSI from TechnicalIndicator table
        if (signalData && signalData.rsi14 != null) {
            _latestRsiValue = signalData.rsi14;
            updateRsiKpi(signalData.rsi14);
        }
        renderSignalContent(signalData);
        updateHeroSignal(signalData);
        renderScoreBreakdown(signalData);
        renderPriceAnalysis(signalData);

        // Fire-and-forget: signal history, indicators, risk assessment
        const gen = _loadAllGen;
        getCachedSignalHistory(_stockId, days).then(res => {
            if (gen !== _loadAllGen) return;
            const historyData = res && res.status === 'success' ? res.data : null;
            renderSignalTimeline(historyData, signalData);
        }).catch(() => {});

        getLatestIndicators(_stockId).then(res => {
            if (gen !== _loadAllGen) return;
            var needsRecalc = !res || !res.length;
            if (!needsRecalc) {
                var today = new Date();
                var todayStr = today.getFullYear() + '-' + String(today.getMonth()+1).padStart(2,'0') + '-' + String(today.getDate()).padStart(2,'0');
                needsRecalc = res.every(function(ind) { return ind.calculationDate !== todayStr; });
            }
            if (needsRecalc) {
                calculateIndicators(_stockId).then(function() {
                    getLatestIndicators(_stockId).then(function(retry) { 
                        if (gen !== _loadAllGen) return;
                        renderIndicatorsContent(retry); 
                    }).catch(function() {});
                }).catch(function() { 
                    renderIndicatorsContent(res); 
                });
            } else {
                renderIndicatorsContent(res);
            }
        }).catch(function() {
            calculateIndicators(_stockId).then(function() {
                getLatestIndicators(_stockId).then(function(retry) { 
                    if (gen !== _loadAllGen) return;
                    renderIndicatorsContent(retry); 
                }).catch(function() {});
            }).catch(function() {});
        });

        loadRiskAssessment(signalData);
    } catch (e) {
        console.error('Insights tab error:', e);
        const fallback = document.getElementById('recommendationContent');
        if (fallback) fallback.innerHTML = '<p class="text-danger text-center py-8">⚠ Failed to load AI Insights. Ensure indicators are calculated for this stock.</p>';
    }
}

async function loadRiskAssessment(signalData) {
    try {
        renderRiskAssessment(signalData, null);
    } catch (e) {
        console.error('Risk assessment error:', e);
    }
}

// ─── Chart Rendering ────────────────────────────────────────────────────────
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

function renderLtpChart(prices) {
    destroyChart('ltp');
    const canvas = document.getElementById('chartLtp');
    if (!canvas || !prices.length) {
        showChartMsg('chartLtp', 'No price data');
        return;
    }
    showChartMsg('chartLtp');
    // Sort prices by date ascending (oldest first) to ensure older dates on left
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

    // Build a lookup: strategy name → signal from the breakdown
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

    // Add current overall signal as a summary chip
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

// ─── Chart Primitives ────────────────────────────────────────────────────────
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

                    // ponytail: lightweight-charts v4 passes a CanvasRenderingTarget2D, not a 2D
                    // context. useMediaCoordinateSpace gives the real ctx in CSS-pixel coords.
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
        // Merge currentSignal into existing signal history point to preserve RSI/ADX data
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
            // Color each volume bar by its corresponding candle direction
            // Build an O(1) lookup for up/down direction by time
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

    // ── SMA overlays ──
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

    // ── S/R price lines on candlestick series ──
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
    // Avg Cost price line — holding average buy price (shown only when stock is held)
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

    // ── FVG zones as colored fill blocks starting from formation date ──
    if (_cachedFvgEntries && _cachedFvgEntries.length) {
        const fvgPrimitive = new FvgZonePrimitive(_cachedFvgEntries);
        cs.attachPrimitive(fvgPrimitive);
        _chartOverlays.fvg = fvgPrimitive;
    }

    // ── Shared initialization for marker processing ──
    const markers = [];
    // Use date strings (YYYY-MM-DD) for matching instead of exact epoch seconds
    // to handle timezone differences between transaction dates and OHLC data
    const validDates = new Set(ohlc.map(d => new Date(d.time * 1000).toISOString().slice(0, 10)));
    console.log('[Markers] Valid OHLC dates:', Array.from(validDates).slice(0, 10), '... total:', validDates.size);

    // ── Transaction markers — buy/sell arrows with quantities ──
    if (txRecords && txRecords.length) {
        console.log('[Markers] Processing', txRecords.length, 'transaction records');
        const txMap = {};
        txRecords.forEach(tx => {
            const txDateStr = new Date(tx.transactionDate + 'T00:00:00Z').toISOString().slice(0, 10);
            const epoch = Math.floor(new Date(tx.transactionDate + 'T00:00:00Z').getTime() / 1000);
            if (!validDates.has(txDateStr)) {
                console.log('[Markers] Skipping tx - no matching OHLC date:', tx.transactionDate, '→', txDateStr);
                return; // skip weekends/holidays (no candle)
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
            const marker = {
                time: item.time,
                position: isBuy ? 'belowBar' : 'aboveBar',
                color: isBuy ? '#2962FF' : '#FF9800',
                shape: isBuy ? 'arrowUp' : 'arrowDown',
                size: 1.5,
                text: String(item.qty),
                isTransaction: true,
            };
            markers.push(marker);
            console.log('[Markers] Added transaction marker:', marker);
        });
    } else {
        console.log('[Markers] No transaction records to process');
    }

    // ── Buy/Sell markers — arrows only for strong signals, small dots for regular ──
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
                        // All buy/sell signals use arrows, just at smaller sizes
                        shape: isBuy ? 'arrowUp' : 'arrowDown',
                        size: isStrong ? 2 : 1,
                        text: isBuy ? (s.compositeScore >= 7 ? 'SB' : 'B') :
                              isSell ? (s.compositeScore <= -7 ? 'SS' : 'S') : '',
                    });
                }
            }
        });
    }

    // Add candlestick pattern annotations as special markers
    if (signalHistory && signalHistory.length) {
        signalHistory.forEach(s => {
            if (!s.priceDate || !s.candlestickPattern || s.candlestickPattern === 'NONE') return;
            const epoch = Math.floor(new Date(s.priceDate + 'T00:00:00Z').getTime() / 1000);
            const patDateStr = new Date(s.priceDate + 'T00:00:00Z').toISOString().slice(0, 10);
            if (!validDates.has(patDateStr)) return;
            // Don't add pattern markers where we already have a BUY/SELL marker
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

    // Current signal marker — always an arrow, but smaller
    if (currentSignal && currentSignal.recommendation
        && currentSignal.recommendation !== 'HOLD' && currentSignal.recommendation !== 'NEUTRAL'
        && ohlc.length) {
        const lastTime = ohlc[ohlc.length - 1].time;
        // Remove existing marker for latest time (from signal history) and replace with currentSignal
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
    // Limit markers to prevent chart clutter — keep only the most recent 40
    if (markers.length > 40) {
        markers.splice(0, markers.length - 40);
    }

    // ── Enhanced tooltip with modern design ──
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

    // Helper: format key indicators in a compact 2-column grid without group headers
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

        // Get all technical indicators for this date from cached data
        const indicatorLookupDate = epoch ? new Date(epoch * 1000).toISOString().split('T')[0] : '';
        const indicatorsForDate = _cachedIndicatorsByDate[indicatorLookupDate] || {};
        const indicatorHtml = buildCompactIndicators(indicatorsForDate);
        
        // FVG proximity check for crosshair
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

            // Strategy scores row
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

            // Candlestick pattern mini row (ADX shown in indicators below)
            if (sig.candlestickPattern && sig.candlestickScore !== 0) {
                const col = sig.candlestickScore > 0 ? '#22c55e' : '#ef4444';
                miniFactors.push(`<span style="color:#9ca3af;font-size:9px;">Candle</span><span style="font-weight:600;color:${col};font-size:9px;">${sig.candlestickPattern}</span>`);
            }

            // Build strategy breakdown HTML
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

        // Signal badge + accuracy
        const signalScoreText = sig?.compositeScore != null ? (sig.compositeScore >= 0 ? '+' : '') + sig.compositeScore : '--';
        const accIcon = sig?.wasAccurate === true ? '✓' : sig?.wasAccurate === false ? '✗' : '';
        const accColor = sig?.wasAccurate === true ? '#22c55e' : sig?.wasAccurate === false ? '#9ca3af' : '#9ca3af';
        const accFwd = sig?.forwardReturn != null ? (sig.forwardReturn >= 0 ? '+' : '') + Number(sig.forwardReturn).toFixed(1) + '%' : '';
        const accuracyTag = sig?.wasAccurate != null
            ? `<span style="color:${accColor};font-size:10px;font-weight:600;margin-left:4px;">${accIcon} ${accFwd}</span>`
            : '';

        // Build the tooltip HTML
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

    // Re-apply markers on zoom/pan to prevent them from disappearing (lightweight-charts v4.2.0)
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
    // Toggle SMA overlays
    if (_chartOverlays.sma20) _chartOverlays.sma20.applyOptions({ visible: document.getElementById('toggleSma20')?.checked !== false });
    if (_chartOverlays.sma44) _chartOverlays.sma44.applyOptions({ visible: document.getElementById('toggleSma44')?.checked !== false });
    if (_chartOverlays.sma50) _chartOverlays.sma50.applyOptions({ visible: document.getElementById('toggleSma50')?.checked !== false });
    if (_chartOverlays.sma200) _chartOverlays.sma200.applyOptions({ visible: document.getElementById('toggleSma200')?.checked !== false });
    
    // Toggle S/R lines
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
    
    // Toggle FVG primitive
    const showFvg = document.getElementById('toggleFvg')?.checked;
    if (_chartOverlays.fvg) {
        _chartOverlays.fvg.setVisible(showFvg !== false);
    }
    
    // Update arrow toggle state
    _showBuyArrows = document.getElementById('toggleBuyArrows')?.checked !== false;
    _showSellArrows = document.getElementById('toggleSellArrows')?.checked !== false;

    if (_csCandlestick && _lastMarkersFull) {
        try { _csCandlestick.setMarkers(getVisibleMarkers()); } catch(e) {}
    }
}

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
    // Sort dailyData by date ascending (oldest first)
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

function renderInvValueChart(snapshots) {
    destroyChart('invValue');
    const canvas = document.getElementById('chartInvValue');
    if (!canvas || !snapshots.length) {
        showChartMsg('chartInvValue', 'No investment data');
        return;
    }
    showChartMsg('chartInvValue');
    // Sort snapshots by date ascending
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
            // Ensure chart respects parent dimensions
            resizeDelay: 20,
            animation: {
                duration: 400,  // Slightly faster loading
                easing: 'easeInOutQuart'
            },
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: {
                    display: false // Hide to save space
                },
                tooltip: {
                    callbacks: { label: ctx => ctx.dataset.label + ': ' + fmtPrice(ctx.raw) }
                }
            },
            scales: {
                x: {
                    ticks: {
                        maxTicksLimit: 8
                    },
                    grid: { display: true }
                },
                y: {
                    position: 'right',
                    ticks: {
                        callback: v => fmtPrice(v)
                    },
                    grid: { display: true }
                }
            },
            onClick: null
        }
    });

    // Force Chart.js to properly clear and establish size on next tick
    setTimeout(() => {
        if (_charts.invValue) {
            _charts.invValue.resize();
        }
    }, 100);
}

function renderRsiChart(history) {
    destroyChart('rsi');
    const canvas = document.getElementById('chartRsi');
    if (!canvas || !history.length) {
        showChartMsg('chartRsi', 'No RSI data');
        return;
    }
    showChartMsg('chartRsi');

    // Format ISO date (yyyy-MM-dd) for display
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
    // Shape indicators for accessibility
    var pointStyles = data.map(function(v) {
        return v > 70 ? 'triangle' : v < 30 ? 'triangleRotated' : 'circle';
    });
    // Set ARIA label with latest value
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
                    // Light red above 70
                    ctx.fillStyle = 'rgba(239,68,68,0.1)';
                    ctx.fillRect(chartArea.left, chartArea.top, chartArea.right - chartArea.left, y70 - chartArea.top);
                    // Light green below 30
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
            },
        ]
    });
}

// ─── Fundamentals ─────────────────────────────────────────────────────────
async function loadFundamentalsTab(forceRefresh) {
    const container = document.getElementById('fundamentalsContent');
    if (!container) return;
    container.innerHTML = '<p class="text-secondary text-center py-4"><i class="fas fa-spinner fa-spin mr-2"></i>Loading fundamentals...</p>';

    try {
        const promise = forceRefresh ? fetchFundamentals(_stockId) : getFundamentals(_stockId);
        const res = await promise;
        const data = res && res.status === 'success' ? res.data : null;
        renderFundamentals(container, data);
    } catch (e) {
        console.error('Error loading fundamentals:', e);
        container.innerHTML = '<p class="text-secondary text-center py-4">No fundamental data available. <button onclick="loadFundamentalsTab(true)" class="text-blue-400 hover:underline">Fetch from Yahoo</button></p>';
    }
}

function renderFundamentals(container, data) {
    if (!data) {
        container.innerHTML = '<p class="text-secondary text-center py-4">No fundamental data available. <button onclick="loadFundamentalsTab(true)" class="text-blue-400 hover:underline">Fetch from Yahoo</button></p>';
        return;
    }

    const fetched = data.fetchedDate ? new Date(data.fetchedDate + 'T00:00:00') : null;
    const daysSince = fetched ? Math.floor((Date.now() - fetched) / 86400000) : 999;
    let staleClass = 'text-green-400';
    let staleLabel = 'Fresh';
    if (daysSince > 30) { staleClass = 'text-red-400'; staleLabel = 'Stale'; }
    else if (daysSince > 7) { staleClass = 'text-yellow-400'; staleLabel = 'Aging'; }

    const fetchedEl = document.getElementById('fundFetchedDate');
    if (fetchedEl) {
        fetchedEl.innerHTML = '<span class="' + staleClass + '">●</span> ' + staleLabel + ' — ' + (data.fetchedDate || 'Never');
    }

    function v(val, prefix, suffix) {
        if (val == null || val === '') return '—';
        return (prefix || '') + val + (suffix || '');
    }

    function formatCr(num) {
        if (num == null) return '—';
        const cr = num / 10000000;
        if (cr >= 100) return '₹' + (cr / 100).toFixed(2) + 'K Cr';
        return '₹' + cr.toFixed(2) + ' Cr';
    }

    function fmt(num, decimals) {
        if (num == null) return '—';
        if (typeof num === 'string') num = parseFloat(num);
        return num.toFixed(decimals != null ? decimals : 2);
    }

    function pct(val) {
        if (val == null) return '—';
        return fmt(val, 2) + '%';
    }

    function colorVal(val, goodDir) {
        if (val == null) return '';
        if (typeof val === 'string') val = parseFloat(val);
        if (goodDir === 'low') return val < 0 ? '' : val <= 15 ? 'text-green-400' : val <= 25 ? 'text-yellow-400' : 'text-red-400';
        if (goodDir === 'high') return val >= 20 ? 'text-green-400' : val >= 10 ? 'text-yellow-400' : 'text-red-400';
        if (goodDir === 'neg') return val <= 0.5 ? 'text-green-400' : val <= 1.5 ? 'text-yellow-400' : 'text-red-400';
        return '';
    }

    let html = '<div class="grid grid-cols-2 lg:grid-cols-4 gap-4">';

    // Market Cap (live, computed)
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Market Cap <span class="text-green-400 text-xs">Live</span></div>';
    html += '<div class="text-lg font-bold">' + formatCr(data.marketCap) + '</div></div>';

    // P/E
    const peClass = colorVal(data.peRatio, 'low');
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">P/E Ratio</div>';
    html += '<div class="text-lg font-bold ' + peClass + '">' + v(fmt(data.peRatio)) + '</div></div>';

    // Forward P/E
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Forward P/E</div>';
    html += '<div class="text-lg font-bold">' + v(fmt(data.forwardPe)) + '</div></div>';

    // EPS TTM
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">EPS (TTM)</div>';
    html += '<div class="text-lg font-bold">' + v(data.epsTtm, '₹') + '</div></div>';

    // Book Value
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Book Value</div>';
    html += '<div class="text-lg font-bold">' + v(data.bookValue, '₹') + '</div></div>';

    // P/B
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">P/B Ratio</div>';
    html += '<div class="text-lg font-bold">' + v(fmt(data.priceToBook)) + '</div></div>';

    // Dividend Yield
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Div Yield</div>';
    html += '<div class="text-lg font-bold">' + v(data.dividendYield ? (data.dividendYield * 100).toFixed(2) + '%' : null) + '</div></div>';

    // ROE
    const roeClass = colorVal(data.roe, 'high');
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">ROE</div>';
    html += '<div class="text-lg font-bold ' + roeClass + '">' + pct(data.roe) + '</div></div>';

    // D/E
    const deClass = colorVal(data.debtToEquity, 'neg');
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Debt/Equity</div>';
    html += '<div class="text-lg font-bold ' + deClass + '">' + v(fmt(data.debtToEquity)) + '</div></div>';

    // Profit Margin
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Profit Margin</div>';
    html += '<div class="text-lg font-bold">' + pct(data.profitMargin) + '</div></div>';

    // Beta
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Beta</div>';
    html += '<div class="text-lg font-bold">' + v(fmt(data.beta)) + '</div></div>';

    // 52W Range
    html += '<div class="card rounded-xl p-3 shadow-lg lg:col-span-1"><div class="text-xs text-secondary mb-1">52W Range</div>';
    html += '<div class="text-lg font-bold">' + (data.fiftyTwoWeekLow ? '₹' + fmt(data.fiftyTwoWeekLow) : '—') + ' – ' + (data.fiftyTwoWeekHigh ? '₹' + fmt(data.fiftyTwoWeekHigh) : '—') + '</div></div>';

    // Revenue
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Revenue (TTM)</div>';
    html += '<div class="text-lg font-bold">' + formatCr(data.revenueTtm) + '</div></div>';

    html += '</div>';

    // Business Summary
    if (data.businessSummary) {
        html += '<div class="mt-4 pt-4 border-t border-gray-700">';
        html += '<h4 class="text-sm font-semibold mb-2 text-secondary">About</h4>';
        html += '<p class="text-sm text-secondary leading-relaxed">' + escHtml(data.businessSummary) + '</p>';
        html += '</div>';
    }

    container.innerHTML = html;
}

function escHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ─── Insights Sub-Renderers ─────────────────────────────────────────────────
function renderSignalContent(signal) {
    const el = document.getElementById('recommendationContent');
    if (!el) return;
    if (!signal) {
        el.innerHTML = '<p class="text-secondary text-center py-4">Insufficient data for signal</p>';
        return;
    }
    const rec = signal.recommendation || 'NEUTRAL';
    const rawScore = signal.compositeScore;
    const scoreDisplay = rawScore != null ? (rawScore >= 0 ? '+' : '') + rawScore : '--';
    const isBuy = rec === 'STRONG BUY' || rec === 'BUY';
    const isSell = rec === 'STRONG SELL' || rec === 'SELL';

    // High Conviction Criteria
    const indicatorCoverageOk = signal.indicatorCoverage === 19;
    const trendOk = signal.sma20 > signal.sma50;
    const volumeOk = signal.volumeConfirmed === true;
    const highConviction = isBuy && indicatorCoverageOk && trendOk && volumeOk;

    // Adjust display if Buy signal does not meet High Conviction
    let displayRec = rec;
    let displayScore = rawScore;
    let highConvictionWarning = '';

    if (isBuy && !highConviction) {
        displayRec = 'HOLD';
        displayScore = Math.min(rawScore, 4);
        highConvictionWarning = '<p class=\"text-yellow-400 text-xs mt-2\">Note: Signal not high-conviction (Missing: ' + 
            (!indicatorCoverageOk ? 'Indicators, ' : '') + 
            (!trendOk ? 'Trend, ' : '') + 
            (!volumeOk ? 'Volume' : '') + ')</p>';
    }

    const badgeCls = (isBuy && highConviction) ? 'bg-gradient-to-r from-green-600 to-emerald-500' :
                     isSell ? 'bg-gradient-to-r from-red-600 to-rose-500' : 'bg-gradient-to-r from-gray-500 to-gray-400';
    const badgeIcon = (isBuy && highConviction) ? 'fa-thumbs-up' : isSell ? 'fa-thumbs-down' : 'fa-minus';
    const recText = (isBuy && highConviction) ? 'Bullish outlook — high conviction entry' :
                    isSell ? 'Bearish outlook — consider reducing exposure' :
                    (isBuy && !highConviction) ? 'Bullish signal detected, but conviction criteria not met' :
                    'Neutral outlook — wait for clearer signals';
    const scoreColor = displayScore != null ? (displayScore >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const scoreBarColor = displayScore != null ? (displayScore >= 0 ? 'bg-gradient-to-r from-green-500 to-emerald-400' : 'bg-gradient-to-r from-red-500 to-rose-400') : 'bg-gray-500';
    const scoreMag = displayScore != null ? Math.abs(displayScore) : 0;
    const scoreBarWidth = displayScore != null
        ? (displayScore >= 7 ? 100 : displayScore >= 3 ? 66 : displayScore <= -7 ? 100 : displayScore <= -4 ? 66 : 33)
        : 0;
    // Multi-timeframe RSI
    const tfRsi = (src, label) => {
        const v = src != null ? parseFloat(src) : null;
        const disp = v != null ? v.toFixed(2) : '--';
        const color = v != null ? (v > 70 ? 'text-red-400' : v < 30 ? 'text-green-400' : 'text-yellow-400') : 'text-secondary';
        const bg = v != null ? (v > 70 ? 'bg-red-900/20 border-red-800/30' : v < 30 ? 'bg-green-900/20 border-green-800/30' : 'bg-yellow-900/20 border-yellow-800/30') : 'bg-gray-800/30 border-gray-700';
        const icon = v != null ? (v > 70 ? 'fa-circle-up' : v < 30 ? 'fa-circle-down' : 'fa-circle-minus') : 'fa-circle';
        const status = v != null ? (v > 70 ? 'Overbought' : v < 30 ? 'Oversold' : 'Neutral') : '--';
        return { disp, color, bg, icon, status };
    };
    const dailyRsi = tfRsi(_latestRsiValue != null ? _latestRsiValue : signal.rsi14, 'Daily');
    const weeklyRsi = tfRsi(signal.weeklyRsi, 'Weekly');
    const monthlyRsi = tfRsi(signal.monthlyRsi, 'Monthly');
    const macdVal = signal.macdHistogram;
    const macdDisplay = macdVal != null ? (macdVal >= 0 ? 'Bullish' : 'Bearish') : '--';
    const macdValDisplay = macdVal != null ? (macdVal >= 0 ? '+' : '') + macdVal.toFixed(2) : '';
    const macdColor = macdVal != null ? (macdVal >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const macdBg = macdVal != null ? (macdVal >= 0 ? 'bg-green-900/20 border-green-800/30' : 'bg-red-900/20 border-red-800/30') : 'bg-gray-800/30 border-gray-700';
    const macdIcon = macdVal != null ? (macdVal >= 0 ? 'fa-arrow-trend-up' : 'fa-arrow-trend-down') : 'fa-chart-line';
    const bbUpper = signal.bollingerUpper;
    const bbLower = signal.bollingerLower;
    const bbLtp = signal.lastTradedPrice || signal.closePrice || signal.latestPrice;
    const bbActive = bbUpper != null && bbLower != null;
    let bbZone = '--', bbZoneColor = 'text-secondary';
    if (bbActive && bbLtp != null) {
        if (bbLtp >= bbUpper * 0.98) { bbZone = 'Upper'; bbZoneColor = 'text-red-400'; }
        else if (bbLtp <= bbLower * 1.02) { bbZone = 'Lower'; bbZoneColor = 'text-green-400'; }
        else { bbZone = 'Middle'; bbZoneColor = 'text-yellow-400'; }
    }
    const bbDisplay = bbActive ? bbZone : '--';
    const bbColor = bbActive ? bbZoneColor : 'text-secondary';
    const bbBg = bbActive ? 'bg-blue-900/20 border-blue-800/30' : 'bg-gray-800/30 border-gray-700';

    // Trade parameters
    const targetDisplay = signal.targetPrice != null ? fmtPrice(signal.targetPrice) : '--';
    const stopDisplay = signal.stopLoss != null ? fmtPrice(signal.stopLoss) : '--';
    const targetVal = signal.targetPrice;
    const stopVal = signal.stopLoss;
    const ltpVal = signal.lastTradedPrice || signal.closePrice || signal.latestPrice;
    let rrRatio = null;
    if (targetVal != null && stopVal != null && ltpVal != null && targetVal !== stopVal) {
        const upside = Math.abs(targetVal - ltpVal);
        const downside = Math.abs(ltpVal - stopVal);
        if (downside > 0) rrRatio = (upside / downside).toFixed(2);
    }
    const rrDisplay = rrRatio != null ? '1:' + rrRatio : '--';
    const rrColor = rrRatio != null ? (parseFloat(rrRatio) >= 2 ? 'text-green-400' : parseFloat(rrRatio) >= 1 ? 'text-yellow-400' : 'text-red-400') : 'text-secondary';
    const conf = signal.confidenceScore;
    const confDisplay = conf != null ? conf + '/100' : '--';
    const confColor = conf != null ? (conf >= 70 ? 'text-green-400' : conf >= 40 ? 'text-yellow-400' : 'text-red-400') : 'text-secondary';
    const age = signal.signalAge;
    const ageDisplay = age != null ? age + 'd' : '--';
    const stale = age != null && age > 3;
    const factorFields = ['divergenceScore','weeklyConfluenceScore','monthlyConfluenceScore','rsiScore','macdScore','bollingerScore','stochScore','stochRsiScore','ultimateOscScore','rocScore','williamsRScore','cciScore','obvScore','smaScore','week52Score','srProximityScore','vwapScore','ichimokuScore','breakoutScore'];
    const totalFactors = factorFields.length;
    const coverage = signal.indicatorCoverage;
    const coverageDisplay = coverage != null ? coverage + '/' + totalFactors : '--';
    const accPct = signal.signalAccuracy30d;
    const accTotal = signal.signalAccuracyTotal30d;
    const accCorrect = signal.signalAccuracyCorrect30d;
    const hasAccuracy = accPct != null && accTotal >= 5;

    const factorValues = factorFields.map(f => signal[f] != null ? signal[f] : 0).filter(v => v !== 0);
    const bullishCount = factorValues.filter(v => v > 0).length;
    const bearishCount = factorValues.filter(v => v < 0).length;

    const breakoutBadges = [];
    if (signal.volumeBreakout) breakoutBadges.push('<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-yellow-900/30 border border-yellow-700/50 text-yellow-300 text-[10px] font-bold"><i class="fas fa-bolt"></i>VOLUME BREAKOUT</span>');
    if (signal.gapUp) breakoutBadges.push('<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-green-900/30 border border-green-700/50 text-green-300 text-[10px] font-bold"><i class="fas fa-arrow-up"></i>GAP UP</span>');
    if (signal.gapDown) breakoutBadges.push('<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-red-900/30 border border-red-700/50 text-red-300 text-[10px] font-bold"><i class="fas fa-arrow-down"></i>GAP DOWN</span>');
    if (signal.rangeBreakout) breakoutBadges.push('<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-blue-900/30 border border-blue-700/50 text-blue-300 text-[10px] font-bold"><i class="fas fa-expand-arrows-alt"></i>RANGE BREAKOUT</span>');
    const breakoutHtml = breakoutBadges.length ? breakoutBadges.join('') : '';

    el.innerHTML = `
        <div class="rounded-xl overflow-hidden border border-gray-700 bg-gray-800/40">
            <div class="px-5 py-4 border-b border-gray-700 flex items-center gap-3 flex-wrap">
                <span class="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-white font-bold shadow-lg text-base sm:text-lg ${badgeCls}">
                    <i class="fas ${badgeIcon}"></i>${displayRec}
                </span>
                ${breakoutHtml}
            </div>
            <div class="p-5 space-y-5">
                <div>
                    <div class="flex items-center justify-between mb-1.5">
                        <span class="text-xs font-semibold text-secondary uppercase tracking-wider">Composite Score</span>
                        <span class="text-2xl font-extrabold ${scoreColor}">${scoreDisplay}</span>
                    </div>
                    <div class="w-full h-3 bg-gray-700 rounded-full overflow-hidden">
                        <div class="h-full rounded-full transition-all duration-500 ease-out ${scoreBarColor}" style="width: ${scoreBarWidth}%"></div>
                    </div>
                    <div class="flex items-center justify-between mt-1.5">
                        <div class="flex gap-3 text-xs">
                            <span class="text-green-400 font-semibold"><i class="fas fa-arrow-up mr-0.5"></i>${bullishCount}</span>
                            <span class="text-red-400 font-semibold"><i class="fas fa-arrow-down mr-0.5"></i>${bearishCount}</span>
                        </div>
                        <span class="text-xs text-secondary">${factorValues.length}/${factorFields.length} indicators active</span>
                    </div>
                </div>
                ${coverage != null && coverage < 16 ? `
                <div class="flex items-center gap-2">
                    <span class="text-xs text-secondary">Indicators Available:</span>
                    <span class="text-xs font-semibold px-2 py-0.5 rounded-full border ${coverage >= 14 ? 'bg-green-900/20 border-green-800/30 text-green-400' : coverage >= 10 ? 'bg-yellow-900/20 border-yellow-800/30 text-yellow-400' : 'bg-red-900/20 border-red-800/30 text-red-400'}">${coverageDisplay}</span>
                </div>` : ''}
                ${hasAccuracy ? `
                <div class="flex items-center gap-2">
                    <span class="text-xs text-secondary">Signal Accuracy (30d):</span>
                    <span class="text-xs font-semibold px-2 py-0.5 rounded-full border ${accPct >= 70 ? 'bg-green-900/20 border-green-800/30 text-green-400' : accPct >= 50 ? 'bg-yellow-900/20 border-yellow-800/30 text-yellow-400' : 'bg-red-900/20 border-red-800/30 text-red-400'}">${accPct.toFixed(1)}% (${accCorrect}/${accTotal})</span>
                </div>` : ''}
                <div class="grid grid-cols-2 sm:grid-cols-5 gap-3">
                    <div class="rounded-lg bg-gray-800/40 border border-gray-700 p-3 text-center">
                        <span class="text-xs text-secondary block">Target</span>
                        <span class="text-sm font-bold text-green-400">${targetDisplay}</span>
                    </div>
                    <div class="rounded-lg bg-gray-800/40 border border-gray-700 p-3 text-center">
                        <span class="text-xs text-secondary block">Stop Loss</span>
                        <span class="text-sm font-bold text-red-400">${stopDisplay}</span>
                    </div>
                    <div class="rounded-lg bg-gray-800/40 border border-gray-700 p-3 text-center">
                        <span class="text-xs text-secondary block">R/R Ratio</span>
                        <span class="text-sm font-bold ${rrColor}">${rrDisplay}</span>
                    </div>
                    <div class="rounded-lg bg-gray-800/40 border border-gray-700 p-3 text-center">
                        <span class="text-xs text-secondary block">Confidence</span>
                        <span class="text-sm font-bold ${confColor}">${confDisplay}</span>
                    </div>
                    <div class="rounded-lg bg-gray-800/40 border ${stale ? 'border-yellow-800/30' : 'border-gray-700'} p-3 text-center">
                        <span class="text-xs text-secondary block">${stale ? '<i class="fas fa-clock text-yellow-400 mr-1"></i>' : ''}Signal Age</span>
                        <span class="text-sm font-bold ${stale ? 'text-yellow-400' : ''}">${ageDisplay}</span>
                    </div>
                </div>
                <div class="grid grid-cols-3 gap-3">
                    <div class="rounded-lg border ${dailyRsi.bg} p-3 text-center">
                        <div class="flex items-center justify-center gap-1.5 mb-1.5">
                            <i class="fas ${dailyRsi.icon} ${dailyRsi.color}"></i>
                            <span class="text-xs uppercase tracking-wider text-secondary font-medium">RSI</span>
                        </div>
                        <div class="space-y-0.5 text-left px-2">
                            <div class="flex justify-between text-xs">
                                <span class="text-secondary">Daily</span>
                                <span class="font-bold ${dailyRsi.color}">${dailyRsi.disp}</span>
                            </div>
                            <div class="flex justify-between text-xs">
                                <span class="text-secondary">Weekly</span>
                                <span class="font-bold ${weeklyRsi.color}">${weeklyRsi.disp}</span>
                            </div>
                            <div class="flex justify-between text-xs">
                                <span class="text-secondary">Monthly</span>
                                <span class="font-bold ${monthlyRsi.color}">${monthlyRsi.disp}</span>
                            </div>
                        </div>
                    </div>
                    <div class="rounded-lg border ${macdBg} p-3 text-center">
                        <div class="flex items-center justify-center gap-1.5 mb-1.5">
                            <i class="fas ${macdIcon} ${macdColor}"></i>
                            <span class="text-xs uppercase tracking-wider text-secondary font-medium">MACD</span>
                        </div>
                        <div class="text-lg font-bold ${macdColor}">${macdDisplay}</div>
                        <p class="text-xs ${macdColor} mt-0.5">${macdValDisplay}</p>
                    </div>
                    <div class="rounded-lg border ${bbBg} p-3 text-center">
                        <div class="flex items-center justify-center gap-1.5 mb-1.5">
                            <i class="fas fa-chart-simple ${bbColor}"></i>
                            <span class="text-xs uppercase tracking-wider text-secondary font-medium">Bollinger</span>
                        </div>
                        <div class="text-lg font-bold ${bbColor}">${bbDisplay}</div>
                        <p class="text-xs text-secondary mt-0.5">&nbsp;</p>
                    </div>
                </div>
                <div class="text-sm text-secondary border-t border-gray-700 pt-3 flex items-center gap-2">
                    <i class="fas ${badgeIcon} ${scoreColor}"></i>
                    <span>${recText}</span>
                </div>
            </div>
        </div>`;
}

// ─── Score Breakdown ─────────────────────────────────────────────────────────
function renderScoreBreakdown(signal) {
    const el = document.getElementById('scoreBreakdownContent');
    if (!el) return;
    if (!signal) {
        el.classList.add('hidden');
        return;
    }

    const factors = [
        { label: 'RSI Divergence',     field: 'divergenceScore' },
        { label: 'Weekly Confluence',  field: 'weeklyConfluenceScore' },
        { label: 'Monthly Confluence', field: 'monthlyConfluenceScore' },
        { label: 'RSI 14',             field: 'rsiScore' },
        { label: 'MACD',               field: 'macdScore' },
        { label: 'Bollinger Bands',    field: 'bollingerScore' },
        { label: 'Stochastic',         field: 'stochScore' },
        { label: 'StochRSI',           field: 'stochRsiScore' },
        { label: 'Ultimate Oscillator', field: 'ultimateOscScore' },
        { label: 'ROC',               field: 'rocScore' },
        { label: 'Williams %R',        field: 'williamsRScore' },
        { label: 'CCI',                field: 'cciScore' },
        { label: 'OBV',                field: 'obvScore' },
        { label: 'SMA 20',             field: 'smaScore' },
        { label: '52W Proximity',      field: 'week52Score' },
        { label: 'S/R Proximity',      field: 'srProximityScore' },
        { label: 'VWAP',               field: 'vwapScore' },
        { label: 'Ichimoku Cloud',     field: 'ichimokuScore' },
        { label: 'Breakout',           field: 'breakoutScore' }
    ];

    const entries = factors
        .map(f => ({ label: f.label, score: signal[f.field] != null ? signal[f.field] : 0 }))
        .filter(e => e.score !== 0);

    if (!entries.length) {
        el.classList.add('hidden');
        return;
    }

    el.classList.remove('hidden');

    const total = entries.reduce((s, e) => s + e.score, 0);
    const bullish = entries.filter(e => e.score > 0);
    const bearish = entries.filter(e => e.score < 0);
    const maxMag = Math.max(...entries.map(e => Math.abs(e.score)));

    const sorted = [...entries].sort((a, b) => Math.abs(b.score) - Math.abs(a.score));

    const rows = sorted.map((e, i) => {
        const pos = e.score > 0;
        const color = pos ? 'text-green-400' : 'text-red-400';
        const barColor = pos ? 'bg-green-500' : 'bg-red-500';
        const width = Math.max(Math.abs(e.score) / maxMag * 100, 8);
        const pctOfTotal = total !== 0 ? (e.score / total * 100) : 0;
        const isTop = i === 0;
        return `
            <div class="flex items-center gap-2 py-1.5 ${isTop ? 'bg-gray-700/20 -mx-2 px-2 rounded' : ''}">
                <span class="text-xs ${isTop ? 'text-white font-semibold' : 'text-secondary'} w-36 shrink-0 truncate" title="${e.label}">${isTop && pos ? '<i class="fas fa-star text-yellow-400 mr-1 text-[10px]"></i>' : ''}${e.label}</span>
                <span class="text-xs font-bold ${color} w-8 text-right shrink-0">${pos ? '+' : ''}${e.score}</span>
                <div class="flex-1 h-2.5 bg-gray-700 rounded-full overflow-hidden">
                    <div class="h-full rounded-full ${barColor} transition-all" style="width: ${width}%"></div>
                </div>
                <span class="text-[10px] text-secondary w-10 text-right shrink-0">${pctOfTotal > 0 ? '+' : ''}${pctOfTotal.toFixed(0)}%</span>
            </div>`;
    }).join('');

    const bullSum = bullish.reduce((s, e) => s + e.score, 0);
    const bearSum = bearish.reduce((s, e) => s + e.score, 0);

    el.innerHTML = `
        <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
            <div class="flex items-center justify-between mb-3">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider flex items-center gap-2">
                    <i class="fas fa-chart-pie text-purple-400"></i>Score Breakdown
                </h4>
                <div class="flex gap-3 text-xs">
                    <span class="text-green-400 font-semibold"><i class="fas fa-arrow-up mr-0.5"></i>${bullSum > 0 ? '+' + bullSum : bullSum}</span>
                    <span class="text-gray-500">|</span>
                    <span class="text-red-400 font-semibold">${bearSum < 0 ? '' : '+'}${bearSum} <i class="fas fa-arrow-down ml-0.5"></i></span>
                </div>
            </div>
            ${rows}
            <div class="flex items-center justify-between mt-2 pt-2 border-t border-gray-700">
                <span class="text-xs text-secondary">${bullish.length} bullish · ${bearish.length} bearish</span>
                <span class="text-xs font-bold ${total >= 0 ? 'text-green-400' : 'text-red-400'}">Net: ${total >= 0 ? '+' : ''}${total}</span>
            </div>
        </div>`;
}

function renderSignalTimeline(history, currentSignal) {
    const el = document.getElementById('signalTimeline');
    if (!el) return;
    if (!history || !history.length) {
        el.innerHTML = '';
        return;
    }

    const colors = {
        'STRONG BUY': '#16a34a',
        'BUY': '#4ade80',
        'HOLD': '#6b7280',
        'NEUTRAL': '#6b7280',
        'SELL': '#f87171',
        'STRONG SELL': '#dc2626'
    };

    const sorted = [...history].sort((a, b) => new Date(a.priceDate) - new Date(b.priceDate));
    const first = new Date(sorted[0].priceDate).getTime();
    const last = new Date(sorted[sorted.length - 1].priceDate).getTime();
    const range = last - first || 1;

    // Count signals by type
    let buyCount = 0, sellCount = 0, holdCount = 0;
    sorted.forEach(s => {
        const r = s.recommendation || 'NEUTRAL';
        if (r === 'STRONG BUY' || r === 'BUY') buyCount++;
        else if (r === 'STRONG SELL' || r === 'SELL') sellCount++;
        else holdCount++;
    });

    // Accuracy stats from signals that have been evaluated
    const evaluated = sorted.filter(s => s.wasAccurate != null);
    const accurateCount = evaluated.filter(s => s.wasAccurate === true).length;
    const wrongCount = evaluated.filter(s => s.wasAccurate === false).length;
    const evalPct = evaluated.length > 0 ? (accurateCount / evaluated.length * 100).toFixed(0) : null;

    // Recent trend: last 5 signals
    const recent = sorted.slice(-5);
    const recentBuy = recent.filter(s => s.recommendation === 'STRONG BUY' || s.recommendation === 'BUY').length;
    const recentSell = recent.filter(s => s.recommendation === 'STRONG SELL' || s.recommendation === 'SELL').length;
    let trendIcon = '', trendText = '', trendColor = '';
    if (recentBuy > recentSell) {
        trendIcon = 'fa-arrow-trend-up';
        trendText = 'Bullish bias';
        trendColor = 'text-green-400';
    } else if (recentSell > recentBuy) {
        trendIcon = 'fa-arrow-trend-down';
        trendText = 'Bearish bias';
        trendColor = 'text-red-400';
    } else {
        trendIcon = 'fa-minus';
        trendText = 'Neutral bias';
        trendColor = 'text-secondary';
    }

    // Trend change: how many signal flips in last 10 entries
    let flips = 0;
    for (let i = 1; i < Math.min(sorted.length, 10); i++) {
        const prev = sorted[sorted.length - 1 - i];
        const curr = sorted[sorted.length - i];
        const pCat = prev.recommendation === 'STRONG BUY' || prev.recommendation === 'BUY' ? 'buy' :
                     prev.recommendation === 'STRONG SELL' || prev.recommendation === 'SELL' ? 'sell' : 'hold';
        const cCat = curr.recommendation === 'STRONG BUY' || curr.recommendation === 'BUY' ? 'buy' :
                     curr.recommendation === 'STRONG SELL' || curr.recommendation === 'SELL' ? 'sell' : 'hold';
        if (pCat !== cCat) flips++;
    }

    let html = '<div class="mt-4 pt-3 border-t border-gray-700">';
    html += '<div class="flex items-center justify-between mb-2">';
    const dateRange = sorted.length >= 2 ? Math.round((last - first) / 86400000) + 'd' : '';
    html += `<span class="text-xs font-semibold text-secondary uppercase tracking-wider">Signal History (${dateRange})</span>`;
    const total = sorted.length;
    if (total > 0) {
        html += `<span class="text-xs text-secondary">${buyCount} BUY · ${holdCount} HOLD · ${sellCount} SELL</span>`;
    }
    html += '</div>';

    // Compact timeline bar
    html += '<div class="flex h-5 rounded overflow-hidden">';
    sorted.forEach((s, i) => {
        const x = new Date(s.priceDate).getTime();
        let left = (x - first) / range * 100;
        let width;
        if (i < sorted.length - 1) {
            const nextX = new Date(sorted[i + 1].priceDate).getTime();
            width = (nextX - x) / range * 100;
        } else {
            width = 100 - left;
        }
        if (width < 1.5) width = 1.5;
        const color = colors[s.recommendation] || '#6b7280';
        const label = s.recommendation || '--';
        const score = s.compositeScore != null ? (s.compositeScore >= 0 ? '+' : '') + s.compositeScore : '';
        const accTag = s.wasAccurate != null ? (s.wasAccurate ? ' ✓' : ' ✗') : '';
        const fwdStr = s.forwardReturn != null ? (s.forwardReturn >= 0 ? ' +' : ' ') + Number(s.forwardReturn).toFixed(1) + '%' : '';
        html += `<div class="h-full" style="width:${width}%;background:${color};min-width:2px" title="${s.priceDate} | ${label} (${score})${accTag}${fwdStr}"></div>`;
    });
    html += '</div>';

    // Trend summary row
    html += `<div class="flex items-center justify-between mt-2 text-xs">`;
    html += `<span class="flex items-center gap-1 ${trendColor}"><i class="fas ${trendIcon}"></i>${trendText} (last 5)</span>`;
    if (evalPct != null) {
        const evalColor = accurateCount >= wrongCount ? 'text-green-400' : 'text-red-400';
        html += `<span class="${evalColor}"><i class="fas ${accurateCount >= wrongCount ? 'fa-check-circle' : 'fa-times-circle'}"></i> ${accurateCount}/${evaluated.length} correct (${evalPct}%)</span>`;
    }
    html += `<span class="text-secondary">${flips} signal ${flips === 1 ? 'flip' : 'flips'} in last 10</span>`;
    if (currentSignal && currentSignal.recommendation) {
        const cur = currentSignal.recommendation;
        const prev = sorted.length >= 2 ? sorted[sorted.length - 2].recommendation : null;
        let change = '';
        if (prev && cur !== prev) {
            const dir = (cur === 'STRONG BUY' || cur === 'BUY') ? '↑' : (cur === 'STRONG SELL' || cur === 'SELL') ? '↓' : '→';
            const dColor = (cur === 'STRONG BUY' || cur === 'BUY') ? 'text-green-400' : (cur === 'STRONG SELL' || cur === 'SELL') ? 'text-red-400' : 'text-secondary';
            change = `<span class="${dColor}">${dir} from ${prev}</span>`;
        }
        if (change) html += `<span>${change}</span>`;
    }
    html += '</div>';

    // Accuracy row — shows ✓/✗ for each evaluated signal
    if (evaluated.length > 0) {
        html += '<div class="flex h-3 rounded overflow-hidden mt-1">';
        sorted.forEach((s) => {
            if (s.wasAccurate == null) return;
            const x = new Date(s.priceDate).getTime();
            let left = (x - first) / range * 100;
            let width;
            const nextIdx = sorted.findIndex(t => t.wasAccurate != null && new Date(t.priceDate).getTime() > x);
            if (nextIdx !== -1) {
                width = (new Date(sorted[nextIdx].priceDate).getTime() - x) / range * 100;
            } else {
                width = 100 - left;
            }
            if (width < 2) width = 2;
            const bg = s.wasAccurate ? '#22c55e' : '#ef4444';
            const icon = s.wasAccurate ? '✓' : '✗';
            const fwd = s.forwardReturn != null ? (s.forwardReturn >= 0 ? '+' : '') + Number(s.forwardReturn).toFixed(1) + '%' : '';
            html += `<div class="h-full flex items-center justify-center text-xs font-bold text-white" style="width:${width}%;background:${bg};min-width:14px" title="${s.priceDate}: ${s.wasAccurate ? 'Correct' : 'Wrong'} (${fwd})">${icon}</div>`;
        });
        html += '</div>';
    }

    // Legend row
    html += '<div class="flex items-center gap-3 mt-1.5">';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#16a34a"></span>Strong Buy</span>';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#4ade80"></span>Buy</span>';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#6b7280"></span>Hold</span>';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#f87171"></span>Sell</span>';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#dc2626"></span>Strong Sell</span>';
    html += '</div>';

    html += '</div>';
    el.innerHTML = html;
}

function renderIndicatorsContent(indicators) {
    const el = document.getElementById('indicatorsContent');
    if (!el) return;
    if (!indicators || !indicators.length) {
        el.innerHTML = '<p class="text-secondary text-center py-4">No indicator data. Click Refresh to calculate.</p>';
        return;
    }

    function getIndicatorInfo(type, value) {
        const t = (type || '').toLowerCase();
        const hasValue = value != null;
        let group = 'Other', color = 'text-blue-400', bg = 'bg-blue-900/20 border-blue-800/30', icon = 'fa-chart-line';
        if (t.includes('stoch_rsi') || t.includes('stochrsi')) {
            group = 'Momentum'; icon = 'fa-gauge-high';
            if (hasValue) {
                if (value > 80) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
                else if (value < 20) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else { color = 'text-yellow-400'; bg = 'bg-yellow-900/20 border-yellow-800/30'; }
            }
        } else if (t.includes('rsi')) {
            group = 'Momentum'; icon = 'fa-gauge-high';
            if (hasValue) {
                if (value > 70) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
                else if (value < 30) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else { color = 'text-yellow-400'; bg = 'bg-yellow-900/20 border-yellow-800/30'; }
            }
        } else if (t.includes('macd') || t.includes('stoch_k') || t.includes('cci') || t.includes('william') || t.includes('ultimate') || t.includes('roc')) {
            group = 'Momentum'; icon = 'fa-arrows-left-right';
            if (t.includes('macd') && hasValue) {
                if (value >= 0) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
            }
            if (t.includes('ultimate') && hasValue) {
                if (value < 30) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else if (value > 70) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
            }
            if (t.includes('roc') && hasValue) {
                if (value > 0) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else if (value < 0) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
            }
            if (t.includes('william') && hasValue) {
                if (value > -20) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
                else if (value < -80) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
            }
            if (t.includes('cci') && hasValue) {
                if (value > 100) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
                else if (value < -100) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
            }
        } else if (t.includes('adx') || t.includes('plus_di') || t.includes('minus_di')) {
            group = 'Trend'; icon = 'fa-chart-area';
            if (t.includes('adx') && hasValue) {
                if (value > 25) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else { color = 'text-yellow-400'; bg = 'bg-yellow-900/20 border-yellow-800/30'; }
            }
            if (t.includes('plus_di') && hasValue) {
                color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30';
            }
            if (t.includes('minus_di') && hasValue) {
                color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30';
            }
        } else if (t.includes('sma') || t.includes('ema') || t.includes('ma(') || t.includes('mms')) {
            group = 'Moving Averages'; icon = 'fa-chart-line';
        } else if (t.includes('boll') || t.includes('atr') || t.includes('volatilit')) {
            group = 'Volatility'; icon = 'fa-wave-square';
        } else if (t.includes('volume') || t.includes('obv')) {
            group = 'Volume'; icon = 'fa-chart-bar';
            if (t.includes('obv') && hasValue) {
                if (value > 0) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else if (value < 0) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
            }
        } else if (t.includes('support') || t.includes('resist')) {
            group = 'Price Levels'; icon = 'fa-location-dot';
        }
        return { group, color, bg, icon };
    }

    const groups = {};
    indicators.forEach(ind => {
        const info = getIndicatorInfo(ind.type, ind.value);
        if (!groups[info.group]) groups[info.group] = [];
        groups[info.group].push({ ...ind, ...info });
    });

    const groupOrder = ['Momentum', 'Trend', 'Moving Averages', 'Volatility', 'Volume', 'Price Levels', 'Other'];
    const groupIcons = {
        Momentum: 'fa-gauge-high text-purple-400',
        Trend: 'fa-chart-area text-teal-400',
        'Moving Averages': 'fa-chart-line text-cyan-400',
        Volatility: 'fa-wave-square text-orange-400',
        Volume: 'fa-chart-bar text-blue-400',
        'Price Levels': 'fa-location-dot text-green-400',
        Other: 'fa-chart-line text-gray-400'
    };

    const groupHtml = groupOrder
        .filter(g => groups[g])
        .map(g => {
            const cards = groups[g].map(ind => {
                const val = ind.value != null ? ind.value.toFixed(2) : '--';
                return `
                    <div class="rounded-lg border ${ind.bg} p-3">
                        <div class="flex items-center gap-1.5 mb-1">
                            <i class="fas ${ind.icon} ${ind.color}"></i>
                            <span class="text-xs text-secondary">${ind.type || '--'}</span>
                        </div>
                        <div class="text-base font-bold ${ind.color}">${val}</div>
                    </div>`;
            }).join('');
            return `
                <div>
                    <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-2.5 flex items-center gap-2">
                        <i class="fas ${groupIcons[g] || 'fa-chart-line text-gray-400'}"></i>${g}
                    </h4>
                    <div class="grid grid-cols-2 sm:grid-cols-3 gap-2">
                        ${cards}
                    </div>
                </div>`;
        }).join('');

    const latestDate = indicators
        .filter(ind => ind.calculationDate)
        .map(ind => ind.calculationDate)
        .sort()
        .pop();

    el.innerHTML = `
        <div class="space-y-5">
            ${groupHtml}
            ${latestDate ? '<div class="text-xs text-secondary text-right pt-2 border-t border-gray-700">Last updated: ' + latestDate + '</div>' : ''}
        </div>`;
}

function renderPriceAnalysis(signal) {
    const el = document.getElementById('priceAnalysisContent');
    if (!el) return;
    if (!signal) {
        el.innerHTML = '<p class="text-secondary text-center py-4">No price analysis data</p>';
        return;
    }
    const sp = signal.supportLevel != null ? fmtPrice(signal.supportLevel) : '--';
    const rs = signal.resistanceLevel != null ? fmtPrice(signal.resistanceLevel) : '--';
    const ts = signal.trendStrength != null ? signal.trendStrength.toFixed(1) + '%' : '--';
    const vol = signal.volatility != null ? signal.volatility.toFixed(2) + '%' : '--';
    const rrPA = signal.targetPrice && signal.stopLoss && (signal.lastTradedPrice || signal.latestPrice)
        ? ((Math.abs(signal.targetPrice - (signal.lastTradedPrice || signal.latestPrice)) / Math.abs((signal.lastTradedPrice || signal.latestPrice) - signal.stopLoss)).toFixed(2))
        : null;
    const rrPADisplay = rrPA != null ? '1:' + rrPA : '--';
    const rrPAColor = rrPA != null ? (parseFloat(rrPA) >= 2 ? 'text-green-400' : parseFloat(rrPA) >= 1 ? 'text-yellow-400' : 'text-red-400') : 'text-secondary';
    const sma20 = signal.sma20 != null ? fmtPrice(signal.sma20) : '--';
    const sma50 = signal.sma50 != null ? fmtPrice(signal.sma50) : '--';
    const bbU = signal.bollingerUpper != null ? fmtPrice(signal.bollingerUpper) : '--';
    const bbL = signal.bollingerLower != null ? fmtPrice(signal.bollingerLower) : '--';
    const vsSma20 = signal.priceVsSma20 != null ? (signal.priceVsSma20 >= 0 ? '+' : '') + signal.priceVsSma20.toFixed(2) + '%' : '--';
    const vsSma20Color = signal.priceVsSma20 != null ? (signal.priceVsSma20 >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const pct52Low = signal.pctFrom52WLow != null ? (signal.pctFrom52WLow >= 0 ? '+' : '') + signal.pctFrom52WLow.toFixed(2) + '%' : '--';
    const pct52LowColor = signal.pctFrom52WLow != null ? (signal.pctFrom52WLow >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const pct52High = signal.pctFrom52WHigh != null ? (signal.pctFrom52WHigh >= 0 ? '+' : '') + signal.pctFrom52WHigh.toFixed(2) + '%' : '--';
    const pct52HighColor = signal.pctFrom52WHigh != null ? (signal.pctFrom52WHigh >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const pnlPct = signal.pnlPercent;
    const pnlDisplay = pnlPct != null ? (pnlPct >= 0 ? '+' : '') + pnlPct.toFixed(2) + '%' : '--';
    const pnlColor = pnlPct != null ? (pnlPct >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';

    // Historical returns
    const histRet = (val) => {
        if (val == null) return null;
        return { display: (val >= 0 ? '+' : '') + val.toFixed(2) + '%', color: val >= 0 ? 'text-green-400' : 'text-red-400' };
    };
    const ret5d = histRet(signal.historicalReturn5d);
    const ret10d = histRet(signal.historicalReturn10d);
    const ret20d = histRet(signal.historicalReturn20d);
    const hasHistReturns = ret5d || ret10d || ret20d;

    el.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-location-dot text-blue-400"></i>Key Levels
                </h4>
                <div class="space-y-3">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Support</span>
                        <span class="text-sm font-bold text-green-400">${sp}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Resistance</span>
                        <span class="text-sm font-bold text-red-400">${rs}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Trend Strength</span>
                        <span class="text-sm font-semibold">${ts}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Volatility</span>
                        <span class="text-sm font-semibold">${vol}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">R/R Ratio</span>
                        <span class="text-sm font-semibold ${rrPAColor}">${rrPADisplay}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-chart-line text-cyan-400"></i>Moving Averages
                </h4>
                <div class="space-y-3">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">SMA 20</span>
                        <span class="text-sm font-bold">${sma20}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">SMA 50</span>
                        <span class="text-sm font-bold">${sma50}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Price vs SMA 20</span>
                        <span class="text-sm font-bold ${vsSma20Color}">${vsSma20}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-chart-simple text-purple-400"></i>Bollinger Bands
                </h4>
                <div class="space-y-3">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Upper Band</span>
                        <span class="text-sm font-bold">${bbU}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Lower Band</span>
                        <span class="text-sm font-bold">${bbL}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-calendar text-yellow-400"></i>52-Week &amp; P&amp;L
                </h4>
                <div class="space-y-3">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">From 52W Low</span>
                        <span class="text-sm font-bold ${pct52LowColor}">${pct52Low}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">From 52W High</span>
                        <span class="text-sm font-bold ${pct52HighColor}">${pct52High}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">P&amp;L %</span>
                        <span class="text-sm font-bold ${pnlColor}">${pnlDisplay}</span>
                    </div>
                    ${hasHistReturns ? `
                    <div class="border-t border-gray-700 pt-2 mt-2">
                        <span class="text-xs text-secondary block mb-1.5">Historical Signal Returns</span>
                        ${ret5d ? `<div class="flex justify-between items-center py-0.5"><span class="text-xs text-secondary">5d</span><span class="text-xs font-bold ${ret5d.color}">${ret5d.display}</span></div>` : ''}
                        ${ret10d ? `<div class="flex justify-between items-center py-0.5"><span class="text-xs text-secondary">10d</span><span class="text-xs font-bold ${ret10d.color}">${ret10d.display}</span></div>` : ''}
                        ${ret20d ? `<div class="flex justify-between items-center py-0.5"><span class="text-xs text-secondary">20d</span><span class="text-xs font-bold ${ret20d.color}">${ret20d.display}</span></div>` : ''}
                    </div>` : ''}
                </div>
            </div>
        </div>`;
}

function renderRiskAssessment(signal, backtestData) {
    const el = document.getElementById('riskContent');
    if (!el) return;
    if (!signal) {
        el.innerHTML = '<p class="text-secondary text-center py-4">No risk assessment data</p>';
        return;
    }

    const eventRisk = signal.eventRisk;
    const divergence = signal.bullishDivergence ? 'bullish' : signal.bearishDivergence ? 'bearish' : null;
    const volConfirmed = signal.volumeConfirmed;
    const purposes = signal.eventPurposes && signal.eventPurposes.length ? signal.eventPurposes : null;

    // Signal quality filters
    const volPenalty = signal.volumePenaltyApplied;
    const adxFilter = signal.adxFilterApplied != null && signal.adxFilterApplied !== 0;
    const fiidiiScore = signal.fiidiiScore != null ? signal.fiidiiScore : 0;

    // Backtest summary
    const btWinRate = backtestData && backtestData.winRate != null ? backtestData.winRate.toFixed(1) + '%' : null;
    const btReturn = backtestData && backtestData.totalReturn != null ? backtestData.totalReturn : null;
    const btReturnDisplay = btReturn != null ? (btReturn >= 0 ? '+' : '') + Number(btReturn).toFixed(2) + '%' : null;
    const btReturnColor = btReturn != null && btReturn >= 0 ? 'text-green-400' : 'text-red-400';

    const riskInfo = signal.volatility != null
        ? (signal.volatility > 3 ? { label: 'High', cls: 'bg-red-600 text-white', icon: 'fa-shield-exclamation' }
            : signal.volatility > 1.5 ? { label: 'Medium', cls: 'bg-yellow-600 text-white', icon: 'fa-shield-halved' }
            : { label: 'Low', cls: 'bg-green-600 text-white', icon: 'fa-shield-check' })
        : null;

    el.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-shield-alt text-yellow-400"></i>Volatility Risk
                </h4>
                <div class="text-center py-3">
                    ${riskInfo
                        ? `<span class="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-bold ${riskInfo.cls} shadow-md"><i class="fas ${riskInfo.icon}"></i>${riskInfo.label}</span>`
                        : '<span class="text-secondary">--</span>'}
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-calendar-exclamation text-orange-400"></i>Event Risk
                </h4>
                <div class="rounded-lg p-3 border ${eventRisk ? 'bg-red-900/20 border-red-800/30' : 'bg-green-900/20 border-green-800/30'}">
                    <div class="flex items-center gap-2 text-sm">
                        <i class="fas ${eventRisk ? 'fa-exclamation-triangle text-red-400' : 'fa-check-circle text-green-400'}"></i>
                        <span class="font-medium ${eventRisk ? 'text-red-400' : 'text-green-400'}">${eventRisk ? 'Event risk detected' : 'No event risk'}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-code-branch text-purple-400"></i>Divergence
                </h4>
                <div class="rounded-lg p-3 border ${divergence === 'bullish' ? 'bg-green-900/20 border-green-800/30' : divergence === 'bearish' ? 'bg-red-900/20 border-red-800/30' : 'bg-gray-800/30 border-gray-700'}">
                    <div class="flex items-center gap-2 text-sm">
                        <i class="fas ${divergence === 'bullish' ? 'fa-arrow-trend-up text-green-400' : divergence === 'bearish' ? 'fa-arrow-trend-down text-red-400' : 'fa-minus text-secondary'}"></i>
                        <span class="font-medium ${divergence === 'bullish' ? 'text-green-400' : divergence === 'bearish' ? 'text-red-400' : 'text-secondary'}">${divergence ? (divergence === 'bullish' ? 'Bullish divergence' : 'Bearish divergence') : 'No divergence'}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-chart-bar text-blue-400"></i>Volume Confirmation
                </h4>
                <div class="rounded-lg p-3 border ${volConfirmed ? 'bg-green-900/20 border-green-800/30' : 'bg-yellow-900/20 border-yellow-800/30'}">
                    <div class="flex items-center gap-2 text-sm">
                        <i class="fas ${volConfirmed ? 'fa-check text-green-400' : 'fa-xmark text-yellow-400'}"></i>
                        <span class="font-medium ${volConfirmed ? 'text-green-400' : 'text-yellow-400'}">${volConfirmed ? 'Confirmed' : 'Unconfirmed'}</span>
                    </div>
                </div>
            </div>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-4">
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4 lg:col-span-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-filter text-cyan-400"></i>Signal Quality
                </h4>
                <div class="flex flex-wrap gap-3">
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border ${volConfirmed ? 'bg-green-900/20 border-green-800/30 text-green-400' : 'bg-yellow-900/20 border-yellow-800/30 text-yellow-400'}">
                        <i class="fas ${volConfirmed ? 'fa-check-circle' : 'fa-exclamation-triangle'}"></i>
                        ${volConfirmed ? 'Volume Confirmed' : 'Volume Unconfirmed'}
                    </span>
                    ${volPenalty ? `
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border bg-red-900/20 border-red-800/30 text-red-400">
                        <i class="fas fa-exclamation-triangle"></i>Volume Penalty Applied
                    </span>` : ''}
                    ${adxFilter ? `
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border bg-yellow-900/20 border-yellow-800/30 text-yellow-400">
                        <i class="fas fa-wave-square"></i>ADX Counter-Trend
                    </span>` : ''}
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border ${fiidiiScore > 0 ? 'bg-green-900/20 border-green-800/30 text-green-400' : fiidiiScore < 0 ? 'bg-red-900/20 border-red-800/30 text-red-400' : 'bg-gray-800/30 border-gray-700 text-secondary'}">
                        <i class="fas fa-building-columns"></i>
                        FII/DII: ${fiidiiScore > 0 ? '+' : ''}${fiidiiScore}
                    </span>
                    ${btWinRate ? `
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border ${parseFloat(btWinRate) >= 50 ? 'bg-green-900/20 border-green-800/30 text-green-400' : 'bg-red-900/20 border-red-800/30 text-red-400'}">
                        <i class="fas fa-vial"></i>
                        BT Win: ${btWinRate}
                    </span>` : ''}
                    ${btReturnDisplay ? `
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border ${btReturn >= 0 ? 'bg-green-900/20 border-green-800/30 text-green-400' : 'bg-red-900/20 border-red-800/30 text-red-400'}">
                        <i class="fas fa-coins"></i>
                        BT Return: ${btReturnDisplay}
                    </span>` : ''}
                </div>
            </div>
        </div>
        ${purposes ? `
        <div class="mt-4 rounded-lg border border-gray-700 bg-gray-800/30 p-4">
            <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                <i class="fas fa-bullhorn text-orange-400"></i>Event Purposes
            </h4>
            <div class="flex flex-wrap gap-2">
                ${purposes.map(p => '<span class="px-3 py-1 bg-orange-900/30 text-orange-400 rounded-full text-xs font-medium border border-orange-800/30">' + p + '</span>').join('')}
            </div>
        </div>` : ''}`;
}

// ─── FII / DII ──────────────────────────────────────────────────────────────
function updateFiidiiBar(data) {
    const fiiEl = document.getElementById('fiidiiFii');
    const diiEl = document.getElementById('fiidiiDii');
    const combinedEl = document.getElementById('fiidiiCombined');
    const dateEl = document.getElementById('fiidiiDate');
    const sentimentEl = document.getElementById('fiidiiSentiment');

    if (!data || data.status !== 'success' || !data.data || !Object.keys(data.data).length) {
        if (fiiEl) fiiEl.textContent = '--';
        if (diiEl) diiEl.textContent = '--';
        if (combinedEl) combinedEl.textContent = '--';
        if (dateEl) dateEl.textContent = 'No data';
        if (sentimentEl) { sentimentEl.textContent = 'No data'; sentimentEl.className = 'text-sm font-medium px-3 py-1 rounded-full bg-gray-700'; }
        return;
    }
    const d = data.data;
    if (fiiEl) fiiEl.textContent = d.fiiNet != null ? fmtPrice(d.fiiNet) : '--';
    if (diiEl) diiEl.textContent = d.diiNet != null ? fmtPrice(d.diiNet) : '--';
    if (combinedEl) combinedEl.textContent = d.combinedNet != null ? fmtPrice(d.combinedNet) : '--';
    if (dateEl) dateEl.textContent = d.date || '--';

    if (sentimentEl) {
        const sentiment = (d.sentiment || '').toLowerCase();
        sentimentEl.textContent = d.sentiment || 'Neutral';
        const bg = sentiment.includes('bullish') ? 'bg-green-600' :
                   sentiment.includes('bearish') ? 'bg-red-600' : 'bg-gray-600';
        sentimentEl.className = 'text-sm font-medium px-3 py-1 rounded-full text-white ' + bg;
    }
}

async function refreshFiiDii() {
    const btn = document.getElementById('fiidiiRefreshBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>'; }
    try {
        await triggerFiiDiiRefresh();
        if (_symbol) {
            const res = await getFiiDiiData();
            updateFiidiiBar(res);
        }
    } catch (e) {
        console.error('FII/DII refresh error:', e);
        const sentimentEl = document.getElementById('fiidiiSentiment');
        if (sentimentEl) { sentimentEl.textContent = 'Failed'; sentimentEl.className = 'text-sm font-medium px-3 py-1 rounded-full bg-red-600 text-white'; }
    } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-sync-alt"></i>'; }
    }
}

// ─── Events ─────────────────────────────────────────────────────────────────
function updateEventsWarning(data) {
    const warning = document.getElementById('eventsWarning');
    const none = document.getElementById('eventsNone');
    const list = document.getElementById('eventsList');
    if (!warning || !none || !list) return;
    if (!data || data.status !== 'success' || !data.data || !data.data.length) {
        warning.classList.add('hidden');
        none.classList.remove('hidden');
        return;
    }
    const events = data.data;
    const now = new Date();
    const fiveDays = new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    const upcoming = events.filter(e => {
        const d = new Date(e.eventDate + 'T00:00:00');
        return d >= now && d <= fiveDays;
    });
    if (!upcoming.length) {
        warning.classList.add('hidden');
        none.classList.remove('hidden');
        return;
    }
    warning.classList.remove('hidden');
    none.classList.add('hidden');
    list.textContent = upcoming.map(e => (e.purpose || '') + ' on ' + (e.eventDate || '')).join(' · ');
}

// ─── Holding Details ─────────────────────────────────────────────────────────────
async function updateHoldingDetails() {
    const container = document.getElementById('holdingDetails');
    const content = document.getElementById('holdingDetailsContent');
    if (!container || !content) return;
    
    const qty = _stockData?.quantity;
    if (!qty || parseFloat(qty) <= 0) {
        container.hidden = true;
        return;
    }

    container.hidden = false;
    
    let txns = [];
    try {
        const res = await getAllTransactionsByStock(parseInt(_stockId));
        if (res && res.status === 'success' && Array.isArray(res.data)) txns = res.data;
    } catch (e) {
        console.error('[HoldingDetails] Failed to load transactions:', e);
    }
    
    if (!txns.length) {
        content.innerHTML = '<tr><td colspan="8" class="text-center py-8 text-secondary">No transactions recorded for this stock yet.</td></tr>';
        return;
    }
    
    content.innerHTML = txns.map(tx => {
        const isBuy = tx.type === 'BUY';
        const value = (tx.quantity || 0) * (tx.price || 0);
        const badgeCls = isBuy
            ? 'bg-green-900/30 border border-green-700/50 text-green-400'
            : 'bg-red-900/30 border border-red-700/50 text-red-400';
        const badgeIcon = isBuy ? 'fa-arrow-down' : 'fa-arrow-up';
        return `
            <tr class="border-b border-gray-700 hover:bg-gray-800 transition-colors">
                <td class="px-4 py-3 whitespace-nowrap">${tx.transactionDate || '--'}</td>
                <td class="px-4 py-3">
                    <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold ${badgeCls}">
                        <i class="fas ${badgeIcon}"></i>${isBuy ? 'BUY' : 'SELL'}
                    </span>
                </td>
                <td class="px-4 py-3 text-right">${Number(tx.quantity || 0).toLocaleString('en-IN')}</td>
                <td class="px-4 py-3 text-right">${fmtPrice(tx.price)}</td>
                <td class="px-4 py-3 text-right">${fmtPrice(tx.fees)}</td>
                <td class="px-4 py-3 text-right font-medium">${fmtPrice(value)}</td>
                <td class="px-4 py-3 text-right ${tx.realizedPnl != null ? pnlColor(tx.realizedPnl) : 'text-secondary'}">${tx.realizedPnl != null ? fmtPrice(tx.realizedPnl) : '--'}</td>
                <td class="px-4 py-3 text-secondary max-w-[200px] truncate" title="${escHtml(tx.notes || '')}">${escHtml(tx.notes || '')}</td>
            </tr>`;
    }).join('');
}

// ─── Actions ────────────────────────────────────────────────────────────────
async function fillRsiGaps() {
    const btn = document.getElementById('fillRsiGapsBtn');
    if (!btn) return;
    
    const originalHtml = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-1"></i>Filling...';
    
    try {
        const res = await fillRsiGaps();
        if (res && res.status === 'success') {
            alert('RSI gaps filled successfully for the last 30 days!');
            await refreshIndicators();
        } else {
            alert('Failed to fill RSI gaps: ' + (res?.message || 'Unknown error'));
        }
    } catch (e) {
        console.error('Error filling RSI gaps:', e);
        alert('An error occurred while filling RSI gaps.');
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalHtml;
    }
}

async function refreshIndicators() {
    const btn = document.getElementById('refreshIndicatorsBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-1"></i>Calc...'; }
    try {
        await calculateIndicators(_stockId);
        const indicators = await getLatestIndicators(_stockId);
        renderIndicatorsContent(indicators);
    } catch (e) {
        console.error('Indicator refresh error:', e);
        const el = document.getElementById('indicatorsContent');
        if (el) el.innerHTML = '<p class="text-danger text-center py-4">⚠ Failed to refresh indicators</p>';
    } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-sync-alt mr-1"></i>Refresh'; }
    }
}

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
    
    // Advanced metrics
    setText('btSharpeRatio', r.sharpeRatio != null ? r.sharpeRatio.toFixed(2) : '--');
    setText('btCalmarRatio', r.calmarRatio != null ? r.calmarRatio.toFixed(2) : '--');
    setText('btSortinoRatio', r.sortinoRatio != null ? r.sortinoRatio.toFixed(2) : '--');
    setText('btProfitFactor', r.profitFactor != null ? r.profitFactor.toFixed(2) : '--');
    setText('btAvgWinningTrade', r.avgWinningTrade != null ? fmtPrice(r.avgWinningTrade) : '--');
    setText('btAvgLosingTrade', r.avgLosingTrade != null ? fmtPrice(r.avgLosingTrade) : '--');
    setText('btLargestWinnerTrade', r.largestWinnerCount != null ? r.largestWinnerCount : '0');
    setText('btLargestLoserTrade', r.largestLoserCount != null ? r.largestLoserCount : '0');
    
    // Color total return
    const retEl = document.getElementById('btTotalReturn');
    if (retEl && r.totalReturn != null) {
        const v = parseFloat(r.totalReturn);
        retEl.className = 'text-2xl font-bold ' + (v >= 0 ? 'text-green-400' : 'text-red-400');
    }
    
    // Render trade history
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
        const pnlClass = t.pnl != null ? (t.pnl >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
        const pnlText = t.pnl != null ? (t.pnl >= 0 ? '+' : '') + fmtPrice(t.pnl) : '--';
        const stopLossBadge = t.stopLossHit 
            ? '<span class="px-1.5 py-0.5 bg-red-500/20 text-red-400 text-xs rounded">SL</span>' 
            : '<span class="text-secondary">—</span>';
        
        return `<tr class="border-b border-gray-700 hover:bg-gray-700/30 transition-colors">
            <td class="px-4 py-3 text-secondary">${t.exitDate || t.entryDate || '--'}</td>
            <td class="px-4 py-3">
                <span class="px-2 py-0.5 rounded-full text-xs font-semibold ${isBuy ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}">
                    ${t.action}
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
    const trades = window._lastBacktestTrades || [];
    if (trades.length === 0) {
        alert('No trade data to export. Run a backtest first.');
        return;
    }
    
    let csv = 'Date,Action,Entry Price,Exit Price,Quantity,P&L,Exit Reason,Stop Loss Hit\n';
    trades.forEach(t => {
        csv += `${t.exitDate || t.entryDate || ''},${t.action},${t.entryPrice || ''},${t.exitPrice || ''},${t.quantity || ''},${t.pnl || ''},${t.exitReason || ''},${t.stopLossHit}\n`;
    });
    
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `backtest-${_stockId || 'data'}.csv`;
    a.click();
    URL.revokeObjectURL(url);
}

// Store trades globally for export
const _origRenderBacktestResults = renderBacktestResults;
renderBacktestResults = function(r) {
    window._lastBacktestTrades = r.tradeHistory || [];
    _origRenderBacktestResults(r);
};

// ─── Helpers ────────────────────────────────────────────────────────────────
function showErrorState() {
    document.getElementById('stockTitle').textContent = '⚠ Error loading data';
}

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

function fmtPrice(val) {
    if (val == null || isNaN(val)) return '--';
    return '₹' + Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ─── Watchlist Integration ──────────────────────────────────────────────────

// Close watchlist dropdown when clicking outside
document.addEventListener('click', function(event) {
    const container = document.getElementById('watchlistBtnContainer');
    const dropdown = document.getElementById('watchlistDropdown');
    if (container && dropdown && !container.contains(event.target)) {
        dropdown.classList.add('hidden');
    }
});

async function toggleWatchlistDropdown() {
    const dropdown = document.getElementById('watchlistDropdown');
    if (!dropdown) return;

    if (!dropdown.classList.contains('hidden')) {
        dropdown.classList.add('hidden');
        return;
    }

    dropdown.classList.remove('hidden');
    await loadWatchlistDropdown();
}

async function loadWatchlistDropdown() {
    const container = document.getElementById('watchlistDropdownItems');
    if (!container) return;

    try {
        const [watchlistsRes, membershipRes] = await Promise.all([
            getWatchlists(),
            _stockId ? getWatchlistsForStock(_stockId) : Promise.resolve({ data: [] })
        ]);

        const watchlists = watchlistsRes.data || [];
        const memberWatchlistIds = new Set(
            (membershipRes.data || []).map(w => w.id)
        );

        if (!watchlists.length) {
            container.innerHTML = '<p class="text-xs text-secondary text-center py-2">No watchlists yet.<br><a href="watchlist.html" class="text-blue-400">Create one</a></p>';
            return;
        }

        container.innerHTML = watchlists.map(wl => {
            const isMember = memberWatchlistIds.has(wl.id);
            return `<label class="flex items-center gap-3 px-3 py-2 hover:bg-gray-700/50 rounded-lg cursor-pointer text-sm">
                <input type="checkbox" ${isMember ? 'checked' : ''}
                       onchange="toggleWatchlistMembership(${wl.id}, '${wl.name.replace(/'/g, "\\'")}', this.checked)"
                       style="accent-color: #3b82f6; width: 16px; height: 16px;">
                <span class="flex-1">${wl.name}</span>
                <span class="text-xs text-secondary">${wl.itemCount || 0}</span>
            </label>`;
        }).join('');
    } catch (e) {
        console.error('Error loading watchlist dropdown:', e);
        container.innerHTML = '<p class="text-xs text-red-400 text-center py-2">Failed to load</p>';
    }
}

async function toggleWatchlistMembership(watchlistId, watchlistName, add) {
    if (!_stockId) return;

    try {
        if (add) {
            await addStockToWatchlist(watchlistId, _stockId);
            showWatchlistToast('Added to "' + watchlistName + '"', 'success');
        } else {
            await removeStockFromWatchlist(watchlistId, _stockId);
            showWatchlistToast('Removed from "' + watchlistName + '"', 'info');
        }
    } catch (e) {
        console.error('Watchlist toggle error:', e);
        showWatchlistToast(e.message || 'Failed to update watchlist', 'error');
        // Reload to restore checkbox state
        await loadWatchlistDropdown();
    }
}

function showWatchlistToast(msg, type) {
    const existing = document.querySelector('.wl-toast');
    if (existing) existing.remove();

    const toast = document.createElement('div');
    toast.className = 'wl-toast fixed bottom-4 right-4 px-6 py-3 rounded-lg shadow-lg z-50 text-white ' +
        (type === 'success' ? 'bg-green-600' : type === 'error' ? 'bg-red-600' : 'bg-gray-600');
    toast.innerHTML = '<i class="fas ' + (type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle') + ' mr-2"></i>' + msg;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}