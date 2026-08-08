(function () {
    'use strict';

    const state = {
        expanded: new Set(),            // strategy names currently expanded
        detailsCache: new Map()         // strategyName -> Promise<List> (lazy fetch, cached)
    };

    const els = {
        tbody: null,
        empty: null,
        loading: null,
        refreshBtn: null
    };

    function showLoading(show) {
        els.loading.classList.toggle('hidden', !show);
    }

    function signalBadge(signal) {
        const cls = signal === 'BUY' ? 'bg-green-800 text-green-100'
            : signal === 'SELL' ? 'bg-red-600 text-red-100'
                : 'bg-gray-500 text-gray-100';
        return '<span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ' + cls + '">' + signal + '</span>';
    }

    function renderCount(c) {
        return '<td class="px-4 py-3 text-center ' + (c > 0 ? 'font-semibold' : 'text-gray-500') + '">' + c + '</td>';
    }

    function renderRows(counts) {
        els.tbody.innerHTML = '';
        els.empty.classList.toggle('hidden', counts.length > 0);

        counts.forEach(row => {
            const tr = document.createElement('tr');
            tr.className = 'hover:bg-gray-800/50';

            const nameTd = document.createElement('td');
            nameTd.className = 'px-4 py-3';
            nameTd.innerHTML = '<button class="strategy-name font-medium text-blue-400 hover:text-blue-300 hover:underline cursor-pointer" data-strategy="' +
                encodeURIComponent(row.strategyName) + '">' +
                '<i class="fas fa-chevron-right mr-2 text-xs transition-transform"></i>' +
                escapeHtml(row.strategyName) + '</button>';

            tr.appendChild(nameTd);
            tr.appendChild(renderCell(row.buyCount));
            tr.appendChild(renderCell(row.sellCount));
            tr.appendChild(renderCell(row.holdCount));
            els.tbody.appendChild(tr);
        });

        // Wire up expansion toggles after render
        els.tbody.querySelectorAll('.strategy-name').forEach(btn => {
            btn.addEventListener('click', () => toggleStrategy(btn));
        });
    }

    function renderCell(c) {
        const td = document.createElement('td');
        td.className = 'px-4 py-3 text-center' + (c > 0 ? ' font-semibold' : ' text-gray-500');
        td.textContent = c;
        return td;
    }

    function renderExpansion(strategyName, rows) {
        const visible = (rows || []).filter(r => r.signal === 'BUY' || r.signal === 'SELL');
        const button = els.tbody.querySelector('.strategy-name[data-strategy="' + encodeURIComponent(strategyName) + '"]');

        let expandTr = button && button.closest('tr').nextElementSibling;
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
        td.colSpan = 4;
        td.className = 'px-4 py-3';

        if (visible.length === 0) {
            td.innerHTML = '<p class="text-gray-500 text-xs py-2">No BUY or SELL signals.</p>';
        } else {
            const ul = document.createElement('ul');
            ul.className = 'space-y-2';
            visible.forEach(r => {
                const li = document.createElement('li');
                li.className = 'flex items-start gap-3 py-1 border-b border-gray-800 last:border-0 hover:bg-gray-800/50 rounded';
                const symbol = r.symbol || '?';
                const stockId = r.stockId || '';
                const symbolLink = '<a href="/stock-detail.html?id=' + encodeURIComponent(stockId) + '&portfolioId=1" class="font-medium text-blue-400 hover:text-blue-300 hover:underline truncate block w-32">' + escapeHtml(symbol) + '</a>';
                li.innerHTML = symbolLink +
                     signalBadge(r.signal) +
                     (r.confidence != null
                         ? '<span class="text-xs text-gray-500 w-16 text-right">' + Math.round(r.confidence * 100) + '%</span>'
                         : '<span class="text-xs text-gray-600 w-16 text-right">—</span>') +
                     '<span class="text-xs text-gray-400 flex-1">' + escapeHtml(r.reason || '') + '</span>';
                ul.appendChild(li);
            });
            td.appendChild(ul);
        }

        tr.appendChild(td);
        if (button) {
            button.closest('tr').insertAdjacentElement('afterend', tr);
            const icon = button.querySelector('.fa-chevron-right');
            if (icon) icon.classList.add('rotate-90');
        }
        state.expanded.add(strategyName);
    }

    async function toggleStrategy(button) {
        const strategyName = decodeURIComponent(button.dataset.strategy);

        // Already expanded -> collapse immediately
        const current = button.closest('tr').nextElementSibling;
        if (current && current.classList.contains('strategy-expand')) {
            renderExpansion(strategyName, []); // collapses
            return;
        }

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

    async function load() {
        showLoading(true);
        try {
            const res = await getStrategyResults();
            renderRows((res && res.data) || []);
        } catch (e) {
            console.error('Failed to load strategy results', e);
            renderRows([]);
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

        if (!els.tbody || !els.refreshBtn) return;

        els.refreshBtn.addEventListener('click', refresh);
        load();
    });
})();
