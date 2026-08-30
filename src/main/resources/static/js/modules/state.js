// ─── Shared State & Formatters ───────────────────────────────────────────────
// This module must be loaded first — all other modules depend on these globals.

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

function escHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
