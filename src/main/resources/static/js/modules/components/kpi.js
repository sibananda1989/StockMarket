// ─── KPI Updates ────────────────────────────────────────────────────────────
// Depends on: modules/state.js

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
