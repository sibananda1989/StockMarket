(function () {
    'use strict';

    const charts = {};

    const state = {
        expanded: new Set(),            // strategy names currently expanded
        detailsCache: new Map(),        // strategyName -> List (lazy fetch, cached)
        counts: [],                     // raw per-strategy counts from the API
        sortMode: 'priority',           // priority | net-bull | net-bear | total
activeOnly: false,              // true = show only enabled strategies (default: show all)
detailFilter: {},               // strategyName -> 'SIGNALS' | 'BUY' | 'SELL' | 'ALL'
detailSort: {}                  // strategyName -> { key: 'name'|'signal'|'confidence'|'eventDate', dir: 'asc'|'desc'|'none' }
    };

    const els = {
        tbody: null,
        empty: null,
        loading: null,
        refreshBtn: null,
        kpiRow: null,
        chartsRow: null,
        distributionChart: null,
        sentimentMixChart: null,
        snapshotMeta: null,
        sortSelect: null,
        activeOnlyToggle: null,
        top5Row: null,
        topBuysList: null,
        topSellsList: null,
        topBuysEmpty: null,
        topSellsEmpty: null
    };

    function showLoading(show) {
        els.loading.classList.toggle('hidden', !show);
    }

    function destroyChart(key) {
        if (charts[key]) { charts[key].destroy(); charts[key] = null; }
    }

    function signalBadge(signal) {
        const cls = signal === 'BUY' ? 'bg-green-800 text-green-100'
            : signal === 'SELL' ? 'bg-red-600 text-red-100'
                : 'bg-gray-500 text-gray-100';
        return '<span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ' + cls + '">' + signal + '</span>';
    }

    // ── KPIs / summary ──────────────────────────────────────────────────

    function computeKpis(counts) {
        let buy = 0, sell = 0, hold = 0;
        counts.forEach(c => { buy += c.buyCount; sell += c.sellCount; hold += c.holdCount; });
        return {
            buy, sell, hold,
            net: buy - sell,
            strategies: counts.length,
            stocks: Math.max.apply(null, [0].concat(counts.map(c => c.buyCount + c.sellCount + c.holdCount)))
        };
    }

    function kpiCard(label, value, color, icon, sub) {
        const card = document.createElement('div');
        card.className = 'card rounded-xl shadow-lg p-4';
        card.innerHTML =
            '<div class="flex items-center justify-between">' +
                '<span class="text-sm text-gray-400">' + label + '</span>' +
                '<i class="fas ' + icon + '" style="color:' + color + '"></i>' +
            '</div>' +
            '<div class="text-3xl font-bold mt-2" style="color:' + color + '">' + value + '</div>' +
            '<div class="text-xs text-gray-500 mt-1">' + sub + '</div>';
        return card;
    }

    function renderKpis(k) {
        els.kpiRow.innerHTML = '';
        els.kpiRow.appendChild(kpiCard('Buy signals', k.buy, '#22c55e', 'fa-arrow-trend-up', 'directional BUY calls'));
        els.kpiRow.appendChild(kpiCard('Sell signals', k.sell, '#ef4444', 'fa-arrow-trend-down', 'directional SELL calls'));
        const netColor = k.net > 0 ? '#22c55e' : k.net < 0 ? '#ef4444' : '#9ca3af';
        els.kpiRow.appendChild(kpiCard('Net sentiment', (k.net > 0 ? '+' : '') + k.net, netColor, 'fa-scale-balanced', 'BUY − SELL across universe'));
        els.kpiRow.appendChild(kpiCard('Stocks scanned', k.stocks, '#60a5fa', 'fa-chart-line', 'universe covered'));
    }

    function renderSnapshotMeta(k) {
        els.snapshotMeta.textContent = k.strategies + ' strategies · ' + k.stocks + ' stocks';
    }

    // ── Sorting ─────────────────────────────────────────────────────────

    function applySort(counts) {
        const arr = [...counts];
        const net = c => c.buyCount - c.sellCount;
        const total = c => c.buyCount + c.sellCount + c.holdCount;
        switch (state.sortMode) {
            case 'net-bull': arr.sort((a, b) => net(b) - net(a) || b.priority - a.priority); break;
            case 'net-bear': arr.sort((a, b) => net(a) - net(b) || b.priority - a.priority); break;
            case 'total': arr.sort((a, b) => total(b) - total(a) || b.priority - a.priority); break;
            case 'priority':
            default: arr.sort((a, b) => b.priority - a.priority || a.strategyName.localeCompare(b.strategyName)); break;
        }
        return arr;
    }

    // ── Charts ──────────────────────────────────────────────────────────

    function renderDistributionChart(sorted) {
        destroyChart('distribution');
        if (typeof Chart === 'undefined' || !sorted.length || !els.distributionChart) return;
        charts.distribution = new Chart(els.distributionChart, {
            type: 'bar',
            data: {
                labels: sorted.map(c => c.displayName || c.strategyName),
                datasets: [
                    { label: 'BUY', data: sorted.map(c => c.buyCount), backgroundColor: 'rgba(34,197,94,0.85)', borderRadius: 3 },
                    { label: 'SELL', data: sorted.map(c => c.sellCount), backgroundColor: 'rgba(239,68,68,0.85)', borderRadius: 3 },
                    { label: 'HOLD', data: sorted.map(c => c.holdCount), backgroundColor: 'rgba(107,114,128,0.55)', borderRadius: 3 }
                ]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { stacked: true, ticks: { precision: 0, color: '#94a3b8' }, grid: { color: 'rgba(148,163,184,0.08)' } },
                    y: { stacked: true, ticks: { color: '#cbd5e1' }, grid: { display: false } }
                },
                plugins: {
                    legend: { labels: { color: '#94a3b8', padding: 12 } },
                    tooltip: { mode: 'index', intersect: false }
                }
            }
        });
    }

    function renderSentimentMix(k) {
        destroyChart('sentiment');
        const total = k.buy + k.sell + k.hold;
        if (typeof Chart === 'undefined' || total <= 0 || !els.sentimentMixChart) return;
        charts.sentiment = new Chart(els.sentimentMixChart, {
            type: 'doughnut',
            data: {
                labels: ['BUY', 'SELL', 'HOLD'],
                datasets: [{
                    data: [k.buy, k.sell, k.hold],
                    backgroundColor: ['rgba(34,197,94,0.85)', 'rgba(239,68,68,0.85)', 'rgba(107,114,128,0.55)'],
                    borderWidth: 2,
                    borderColor: '#1e293b'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '62%',
                plugins: {
                    legend: { position: 'bottom', labels: { color: '#94a3b8', padding: 12 } },
                    tooltip: {
                        callbacks: {
                            label: ctx => {
                                const pct = ((ctx.raw / total) * 100).toFixed(1);
                                return ctx.label + ': ' + ctx.raw + ' (' + pct + '%)';
                            }
                        }
                    }
                }
            }
        });
    }

    // ── Table cells ─────────────────────────────────────────────────────

    function renderCell(c) {
        const td = document.createElement('td');
        td.className = 'px-4 py-3 text-center' + (c > 0 ? ' font-semibold' : ' text-gray-500');
        td.textContent = c;
        return td;
    }

    function renderNetCell(net) {
        const td = document.createElement('td');
        td.className = 'px-4 py-3 text-center font-semibold ' +
            (net > 0 ? 'text-green-400' : net < 0 ? 'text-red-400' : 'text-gray-500');
        td.textContent = net > 0 ? '+' + net : '' + net;
        return td;
    }

    function seg(pct, color) {
        const d = document.createElement('div');
        d.className = 'h-full';
        d.style.width = pct + '%';
        d.style.background = color;
        return d;
    }

    function signalMixBar(buy, sell, hold) {
        const total = buy + sell + hold;
        const wrap = document.createElement('div');
        wrap.className = 'w-40 h-2.5 rounded-full overflow-hidden bg-gray-800 flex';
        wrap.title = 'BUY ' + buy + ' / SELL ' + sell + ' / HOLD ' + hold;
        if (total > 0) {
            wrap.appendChild(seg(buy / total * 100, '#22c55e'));
            wrap.appendChild(seg(sell / total * 100, '#ef4444'));
            wrap.appendChild(seg(hold / total * 100, '#6b7280'));
        }
        return wrap;
    }

    function renderRows(sorted) {
        els.tbody.innerHTML = '';
        els.empty.classList.toggle('hidden', sorted.length > 0);

        sorted.forEach(row => {
            const disabled = row.active === false;
            const tr = document.createElement('tr');
            tr.className = 'hover:bg-gray-800/50' + (disabled ? ' opacity-60' : '');

            const nameTd = document.createElement('td');
            nameTd.className = 'px-4 py-3';
            const disabledBadge = disabled
                ? '<span class="ml-2 px-1.5 py-0.5 rounded text-[10px] font-medium bg-gray-700 text-gray-400" title="Disabled — not part of the live aggregate signal">disabled</span>'
                : '';
            nameTd.innerHTML = '<button class="strategy-name font-medium text-blue-400 hover:text-blue-300 hover:underline cursor-pointer" data-strategy="' +
                encodeURIComponent(row.strategyName) + '">' +
                '<i class="fas fa-chevron-right mr-2 text-xs transition-transform"></i>' +
                escapeHtml(row.displayName || row.strategyName) + disabledBadge + '</button>';

            tr.appendChild(nameTd);
            tr.appendChild(renderNetCell(row.buyCount - row.sellCount));
            tr.appendChild(renderCell(row.buyCount));
            tr.appendChild(renderCell(row.sellCount));
            tr.appendChild(renderCell(row.holdCount));

            const mixTd = document.createElement('td');
            mixTd.className = 'px-4 py-3';
            mixTd.appendChild(signalMixBar(row.buyCount, row.sellCount, row.holdCount));
            tr.appendChild(mixTd);

            els.tbody.appendChild(tr);
        });

        // Wire up expansion toggles after render
        els.tbody.querySelectorAll('.strategy-name').forEach(btn => {
            btn.addEventListener('click', () => toggleStrategy(btn));
        });
    }

    // ── Expansion (detail rows) with filter chips ───────────────────────

    function currentFilter(strategyName) {
        return state.detailFilter[strategyName] || 'SIGNALS';
    }

    function filterRows(filter, rows) {
        if (!rows) return [];
        if (filter === 'ALL') return rows;
        if (filter === 'BUY') return rows.filter(r => r.signal === 'BUY');
        if (filter === 'SELL') return rows.filter(r => r.signal === 'SELL');
        return rows.filter(r => r.signal === 'BUY' || r.signal === 'SELL'); // 'SIGNALS'
    }

    // ── Per-stock expansion sorting ────────────────────────────────────

// Each sortable column starts on its most natural order: text ascending for
// Name/Signal, newest date first is NOT used — dates start oldest-first, and
// Confidence starts highest-first because that is the useful default.
const DETAIL_SORT_DEFAULT_DIR = {
    name: 'asc',
    signal: 'asc',
    eventDate: 'asc',
    confidence: 'desc'
};

const DETAIL_SORT_LABEL = {
    name: 'Name',
    signal: 'Signal',
    confidence: 'Confidence',
    eventDate: 'Date'
};

function currentDetailSort(strategyName) {
    return state.detailSort[strategyName] || { key: null, dir: 'none' };
}

/**
 * Advances a column through its three states:
 *   none -> natural first order -> reversed -> none (API default order)
 */
function cycleDetailSort(strategyName, key) {
    const cur = currentDetailSort(strategyName);
    let next;
    if (cur.key !== key) {
        next = { key: key, dir: DETAIL_SORT_DEFAULT_DIR[key] };
    } else if (cur.dir === DETAIL_SORT_DEFAULT_DIR[key]) {
        next = { key: key, dir: DETAIL_SORT_DEFAULT_DIR[key] === 'asc' ? 'desc' : 'asc' };
    } else {
        next = { key: null, dir: 'none' };
    }
    state.detailSort[strategyName] = next;
    return next;
}

// Null/absent confidence or date always sorts last, regardless of direction,
// so blank rows never masquerade as the best (or worst) entry.
function compareNullable(a, b, dir) {
    const aMissing = a === null || a === undefined;
    const bMissing = b === null || b === undefined;
    if (aMissing && bMissing) return 0;
    if (aMissing) return 1;
    if (bMissing) return -1;
    const cmp = a < b ? -1 : a > b ? 1 : 0;
    return dir === 'desc' ? -cmp : cmp;
}

function sortDetailRows(rows, strategyName) {
    const s = currentDetailSort(strategyName);
    if (!s.key || s.dir === 'none' || !rows.length) return rows;
    const arr = [...rows];
    arr.sort((x, y) => {
        switch (s.key) {
            case 'name': {
                // Falls back to symbol so stocks without a name still order sensibly.
                const xa = (x.name || x.symbol || '').toLowerCase();
                const ya = (y.name || y.symbol || '').toLowerCase();
                const cmp = xa < ya ? -1 : xa > ya ? 1 : 0;
                return s.dir === 'desc' ? -cmp : cmp;
            }
            case 'signal':
                return compareNullable(x.signal, y.signal, s.dir);
            case 'confidence':
                return compareNullable(x.confidence, y.confidence, s.dir);
            case 'eventDate':
                return compareNullable(x.eventDate, y.eventDate, s.dir);
            default:
                return 0;
        }
    });
    return arr;
}

function paintExpansionList(container, strategyName, rows) {
    const filtered = filterRows(currentFilter(strategyName), rows);
    const visible = sortDetailRows(filtered, strategyName);
    const hasDates = rows.some(r => r.eventDate);
        container.className = 'expansion-list mt-2';
        container.innerHTML = ''; // clear any previous render so re-filtering doesn't accumulate
        if (visible.length === 0) {
            container.innerHTML = '<p class="text-gray-500 text-xs py-2">No signals in this view.</p>';
            return;
        }
        const sort = currentDetailSort(strategyName);
        const header = document.createElement('div');
        header.className = 'flex items-center gap-3 py-2 text-xs text-gray-400 border-b border-gray-700 font-medium uppercase tracking-wide';

        // Sortable columns render as buttons carrying the 3-state cycle.
        // Reason is intentionally not sortable — it is free text, not a value.
        const sortBtn = (key, width, align) => {
            const active = sort.key === key;
            const arrow = active ? (sort.dir === 'desc' ? ' \u25BC' : ' \u25B2') : '';
            const b = document.createElement('button');
            b.type = 'button';
            b.className = width + (align ? ' ' + align : '') +
                ' uppercase tracking-wide font-medium text-left transition-colors hover:text-gray-200 cursor-pointer' +
                (active ? ' text-gray-100' : '');
            b.textContent = DETAIL_SORT_LABEL[key] + arrow;
            b.title = 'Sort by ' + DETAIL_SORT_LABEL[key].toLowerCase();
            b.addEventListener('click', () => {
                cycleDetailSort(strategyName, key);
                paintExpansionList(container, strategyName, rows);
            });
            return b;
        };

        header.appendChild(sortBtn('name', 'w-56', ''));
        header.appendChild(sortBtn('signal', 'w-20', ''));
        header.appendChild(sortBtn('confidence', 'w-24', 'text-right'));
        if (hasDates) header.appendChild(sortBtn('eventDate', 'w-24', ''));
        const reasonLabel = document.createElement('span');
        reasonLabel.className = 'flex-1';
        reasonLabel.textContent = 'Reason';
        header.appendChild(reasonLabel);
        container.appendChild(header);
        const ul = document.createElement('ul');
        ul.className = 'space-y-2';
        visible.forEach(r => {
            const li = document.createElement('li');
            li.className = 'flex items-start gap-3 py-1 border-b border-gray-800 last:border-0 hover:bg-gray-800/50 rounded';
            const displayName = r.name || r.symbol || '?';
            const stockId = r.stockId || '';
            const nameLink = '<a href="/stock-detail.html?id=' + encodeURIComponent(stockId) + '&portfolioId=1" target="_blank" rel="noopener" class="font-medium text-blue-400 hover:text-blue-300 hover:underline truncate block w-56" title="' + escapeHtml(r.symbol || '') + '">' + escapeHtml(displayName) + '</a>';
            li.innerHTML = nameLink +
                 '<span class="w-20 inline-flex">' + signalBadge(r.signal) + '</span>' +
                 (r.confidence != null
                     ? '<span class="text-xs text-gray-500 w-24 text-right">' + Math.round(r.confidence * 100) + '%</span>'
                     : '<span class="text-xs text-gray-600 w-24 text-right">—</span>') +
                 (hasDates
                     ? (r.eventDate
                         ? '<span class="text-xs text-gray-400 w-24 whitespace-nowrap" title="Signal event date">' + escapeHtml(r.eventDate) + '</span>'
                         : '<span class="w-24"></span>')
                     : '') +
                 '<span class="text-xs text-gray-400 flex-1">' + escapeHtml(r.reason || '') + '</span>';
            ul.appendChild(li);
        });
        container.appendChild(ul);
    }

    function buildChips(strategyName) {
        const bar = document.createElement('div');
        bar.className = 'flex items-center gap-2';
        const label = document.createElement('span');
        label.className = 'text-xs text-gray-500 mr-1';
        label.textContent = 'Show:';
        bar.appendChild(label);

        const filter = currentFilter(strategyName);
        const options = [['SIGNALS', 'Signals (B/S)'], ['BUY', 'Buy'], ['SELL', 'Sell'], ['ALL', 'All']];
        options.forEach(([val, txt]) => {
            const b = document.createElement('button');
            b.className = 'px-2.5 py-1 rounded-full text-xs font-medium transition-colors ' +
                (filter === val ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-300 hover:bg-gray-600');
            b.textContent = txt;
            b.dataset.filter = val;
            b.dataset.strategy = encodeURIComponent(strategyName);
            b.addEventListener('click', () => {
                state.detailFilter[strategyName] = val;
                bar.querySelectorAll('button').forEach(x => {
                    const active = x.dataset.filter === val;
                    x.className = 'px-2.5 py-1 rounded-full text-xs font-medium transition-colors ' +
                        (active ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-300 hover:bg-gray-600');
                });
                const button = els.tbody.querySelector('.strategy-name[data-strategy="' + encodeURIComponent(strategyName) + '"]');
                const expandTr = button && button.closest('tr').nextElementSibling;
                const list = expandTr && expandTr.querySelector('.expansion-list');
                if (list) paintExpansionList(list, strategyName, state.detailsCache.get(strategyName) || []);
            });
            bar.appendChild(b);
        });
        return bar;
    }

    function renderExpansion(strategyName, rows) {
        const button = els.tbody.querySelector('.strategy-name[data-strategy="' + encodeURIComponent(strategyName) + '"]');
        let expandTr = button && button.closest('tr').nextElementSibling;

        // Already expanded -> collapse immediately
        if (expandTr && expandTr.classList.contains('strategy-expand')) {
            expandTr.remove();
            state.expanded.delete(strategyName);
            if (button) {
                const icon = button.querySelector('.fa-chevron-right');
                if (icon) icon.classList.remove('rotate-90');
            }
            return;
        }

        const tr = document.createElement('tr');
        tr.className = 'strategy-expand bg-gray-900/60';
        const td = document.createElement('td');
        td.colSpan = 6;
        td.className = 'px-4 py-3';

        td.appendChild(buildChips(strategyName));
        const listContainer = document.createElement('div');
        td.appendChild(listContainer);

        tr.appendChild(td);
        if (button) {
            button.closest('tr').insertAdjacentElement('afterend', tr);
            const icon = button.querySelector('.fa-chevron-right');
            if (icon) icon.classList.add('rotate-90');
        }
        state.expanded.add(strategyName);
        paintExpansionList(listContainer, strategyName, rows);
    }

    async function toggleStrategy(button) {
        const strategyName = decodeURIComponent(button.dataset.strategy);

        try {
            if (!state.detailsCache.has(strategyName)) {
                const res = await getStrategyResultsFor(strategyName);
                state.detailsCache.set(strategyName, (res && res.data) || []);
            }
            renderExpansion(strategyName, state.detailsCache.get(strategyName));
        } catch (e) {
            console.error('Failed to load details for ' + strategyName, e);
            alert('Failed to load details for ' + strategyName + ': ' + (e.message || 'Unknown error'));
        }
    }

    // ── Top-level render + data flow ────────────────────────────────────

    function visibleCounts() {
        return state.activeOnly ? state.counts.filter(c => c.active) : state.counts;
    }

    function renderAll(counts) {
        state.counts = counts;
        const visible = visibleCounts();
        const has = visible.length > 0;
        els.kpiRow.classList.toggle('hidden', !has);
        els.chartsRow.classList.toggle('hidden', !has);

        if (has) {
            const kpis = computeKpis(visible);
            renderKpis(kpis);
            renderSnapshotMeta(kpis);
            const sorted = applySort(visible);
            renderRows(sorted);
            renderDistributionChart(sorted);
            renderSentimentMix(kpis);
        } else {
            renderRows([]);
            if (state.counts.length > 0) {
                els.empty.textContent = 'No active strategies. Untoggle "Active only" to see all.';
            }
        }
    }

    function reapplySort() {
        state.expanded.clear();
        const sorted = applySort(visibleCounts());
        renderRows(sorted);
        renderDistributionChart(sorted);
    }

    function renderTopList(listEl, emptyEl, items) {
        listEl.innerHTML = '';
        const has = items && items.length > 0;
        emptyEl.classList.toggle('hidden', has);
        listEl.classList.toggle('hidden', !has);
        if (!has) return;
        items.forEach((it, idx) => {
            const li = document.createElement('li');
            li.className = 'flex items-center gap-3 py-2 px-2 rounded hover:bg-gray-800/50';
            const rank = '<span class="w-6 text-center text-xs font-bold text-gray-400">#' + (idx + 1) + '</span>';
            const sym = '<a href="/stock-detail.html?id=' + encodeURIComponent(it.stockId) + '&portfolioId=1" target="_blank" rel="noopener" class="font-medium text-blue-400 hover:text-blue-300 hover:underline w-24 truncate">' + escapeHtml(it.symbol) + '</a>';
            const badge = '<span class="px-2 py-0.5 rounded-full text-xs font-bold ' + (it.count >= 3 ? 'bg-green-800 text-green-100' : 'bg-gray-700 text-gray-300') + '" title="' + escapeHtml((it.strategyDisplayNames || it.strategyNames || []).join(', ')) + '">' + it.count + ' strategies</span>';
            const conf = it.avgConfidence != null ? '<span class="text-xs text-gray-500 ml-auto">' + Math.round(it.avgConfidence * 100) + '% avg</span>' : '';
            li.innerHTML = rank + sym + badge + conf;
            listEl.appendChild(li);
        });
    }

    async function loadTopStocks() {
        try {
            const includeInactive = !state.activeOnly;
            const res = await getTopStrategyResults(5, includeInactive);
            const data = res && res.data ? res.data : { topBuys: [], topSells: [] };
            if (els.top5Row) els.top5Row.classList.remove('hidden');
            renderTopList(els.topBuysList, els.topBuysEmpty, data.topBuys || []);
            renderTopList(els.topSellsList, els.topSellsEmpty, data.topSells || []);
        } catch (e) {
            console.error('Failed to load top stocks', e);
            if (els.top5Row) els.top5Row.classList.add('hidden');
        }
    }

    async function load() {
        showLoading(true);
        try {
            const res = await getStrategyResults();
            renderAll((res && res.data) || []);
            await loadTopStocks();
        } catch (e) {
            console.error('Failed to load strategy results', e);
            renderAll([]);
            els.empty.textContent = 'Failed to load strategy results: ' + (e.message || 'Unknown error');
            els.empty.classList.remove('hidden');
        } finally {
            showLoading(false);
        }
    }

    async function refresh() {
        els.refreshBtn.disabled = true;
        const spinner = els.refreshBtn.querySelector('.fa-rotate');
        if (spinner) spinner.classList.add('fa-spin');
        try {
            await refreshStrategyResults();
            state.expanded.clear();
            state.detailsCache.clear();
            await load();
        } catch (e) {
            console.error('Refresh failed', e);
            alert('Refresh failed: ' + (e.message || 'Unknown error'));
        } finally {
            els.refreshBtn.disabled = false;
            if (spinner) spinner.classList.remove('fa-spin');
        }
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    document.addEventListener('DOMContentLoaded', () => {
        els.tbody = document.getElementById('strategy-results-tbody');
        els.empty = document.getElementById('empty-state');
        els.loading = document.getElementById('loading');
        els.refreshBtn = document.getElementById('refreshBtn');
        els.kpiRow = document.getElementById('kpiRow');
        els.chartsRow = document.getElementById('chartsRow');
        els.distributionChart = document.getElementById('distributionChart');
        els.sentimentMixChart = document.getElementById('sentimentMixChart');
        els.snapshotMeta = document.getElementById('snapshotMeta');
        els.sortSelect = document.getElementById('sortSelect');
        els.activeOnlyToggle = document.getElementById('activeOnlyToggle');
        els.top5Row = document.getElementById('top5Row');
        els.topBuysList = document.getElementById('topBuysList');
        els.topSellsList = document.getElementById('topSellsList');
        els.topBuysEmpty = document.getElementById('topBuysEmpty');
        els.topSellsEmpty = document.getElementById('topSellsEmpty');

        if (!els.tbody || !els.refreshBtn) return;

        els.refreshBtn.addEventListener('click', refresh);
        if (els.sortSelect) {
            els.sortSelect.addEventListener('change', () => {
                state.sortMode = els.sortSelect.value;
                reapplySort();
            });
        }
        if (els.activeOnlyToggle) {
            els.activeOnlyToggle.addEventListener('change', async () => {
                state.activeOnly = els.activeOnlyToggle.checked;
                state.expanded.clear();
                renderAll(state.counts);
                await loadTopStocks();
            });
        }
        load();
    });
})();
