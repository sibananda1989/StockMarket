// ─── FII/DII Bar ─────────────────────────────────────────────────────────────
// Depends on: modules/state.js, api.js

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
