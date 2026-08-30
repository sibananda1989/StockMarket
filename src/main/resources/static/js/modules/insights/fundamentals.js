// ─── Fundamentals Tab ────────────────────────────────────────────────────────
// Depends on: modules/state.js, api.js

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

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Market Cap <span class="text-green-400 text-xs">Live</span></div>';
    html += '<div class="text-lg font-bold">' + formatCr(data.marketCap) + '</div></div>';

    const peClass = colorVal(data.peRatio, 'low');
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">P/E Ratio</div>';
    html += '<div class="text-lg font-bold ' + peClass + '">' + v(fmt(data.peRatio)) + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Forward P/E</div>';
    html += '<div class="text-lg font-bold">' + v(fmt(data.forwardPe)) + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">EPS (TTM)</div>';
    html += '<div class="text-lg font-bold">' + v(data.epsTtm, '₹') + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Book Value</div>';
    html += '<div class="text-lg font-bold">' + v(data.bookValue, '₹') + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">P/B Ratio</div>';
    html += '<div class="text-lg font-bold">' + v(fmt(data.priceToBook)) + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Div Yield</div>';
    html += '<div class="text-lg font-bold">' + v(data.dividendYield ? (data.dividendYield * 100).toFixed(2) + '%' : null) + '</div></div>';

    const roeClass = colorVal(data.roe, 'high');
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">ROE</div>';
    html += '<div class="text-lg font-bold ' + roeClass + '">' + pct(data.roe) + '</div></div>';

    const deClass = colorVal(data.debtToEquity, 'neg');
    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Debt/Equity</div>';
    html += '<div class="text-lg font-bold ' + deClass + '">' + v(fmt(data.debtToEquity)) + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Profit Margin</div>';
    html += '<div class="text-lg font-bold">' + pct(data.profitMargin) + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Beta</div>';
    html += '<div class="text-lg font-bold">' + v(fmt(data.beta)) + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg lg:col-span-1"><div class="text-xs text-secondary mb-1">52W Range</div>';
    html += '<div class="text-lg font-bold">' + (data.fiftyTwoWeekLow ? '₹' + fmt(data.fiftyTwoWeekLow) : '—') + ' – ' + (data.fiftyTwoWeekHigh ? '₹' + fmt(data.fiftyTwoWeekHigh) : '—') + '</div></div>';

    html += '<div class="card rounded-xl p-3 shadow-lg"><div class="text-xs text-secondary mb-1">Revenue (TTM)</div>';
    html += '<div class="text-lg font-bold">' + formatCr(data.revenueTtm) + '</div></div>';

    html += '</div>';

    if (data.businessSummary) {
        html += '<div class="mt-4 pt-4 border-t border-gray-700">';
        html += '<h4 class="text-sm font-semibold mb-2 text-secondary">About</h4>';
        html += '<p class="text-sm text-secondary leading-relaxed">' + escHtml(data.businessSummary) + '</p>';
        html += '</div>';
    }

    container.innerHTML = html;
}
