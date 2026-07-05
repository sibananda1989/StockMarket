// ─── State ──────────────────────────────────────────────────────────────────
let allResults = [];
let sortField = 'marketCap';
let sortOrder = 'desc';

// ─── DOM Ready ──────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function() {
    loadSectors();
    applyFilters();
});

// ─── Load Sectors ───────────────────────────────────────────────────────────
async function loadSectors() {
    try {
        const res = await getFundamentalSectors();
        const sectors = res && res.status === 'success' ? res.data : [];
        const sel = document.getElementById('filterSector');
        const current = sel.value;
        sel.innerHTML = '<option value="">All</option>';
        sectors.forEach(function(s) {
            if (s) sel.innerHTML += '<option value="' + escHtml(s) + '">' + escHtml(s) + '</option>';
        });
        if (current) sel.value = current;
    } catch (e) {
        console.error('Error loading sectors:', e);
    }
}

// ─── Apply Filters ─────────────────────────────────────────────────────────
async function applyFilters() {
    const criteria = {};

    const peMin = document.getElementById('filterPeMin').value;
    const peMax = document.getElementById('filterPeMax').value;
    const mcapMin = document.getElementById('filterMcapMin').value;
    const mcapMax = document.getElementById('filterMcapMax').value;
    const roeMin = document.getElementById('filterRoeMin').value;
    const deMax = document.getElementById('filterDeMax').value;
    const divYieldMin = document.getElementById('filterDivYieldMin').value;
    const sector = document.getElementById('filterSector').value;

    if (peMin) criteria.peMin = parseFloat(peMin);
    if (peMax) criteria.peMax = parseFloat(peMax);
    if (mcapMin) criteria.marketCapMin = parseFloat(mcapMin);
    if (mcapMax) criteria.marketCapMax = parseFloat(mcapMax);
    if (roeMin) criteria.roeMin = parseFloat(roeMin);
    if (deMax) criteria.debtToEquityMax = parseFloat(deMax);
    if (divYieldMin) criteria.dividendYieldMin = parseFloat(divYieldMin);
    if (sector) criteria.sector = sector;
    criteria.sortBy = sortField;
    criteria.sortOrder = sortOrder;
    criteria.limit = 500;

    try {
        const res = await screenFundamentals(criteria);
        allResults = res && res.status === 'success' ? res.data : [];
        renderTable();
    } catch (e) {
        console.error('Error screening:', e);
        document.getElementById('resultsBody').innerHTML = '<tr><td colspan="11" class="text-center text-secondary py-8">Error loading results. Make sure fundamentals have been fetched.</td></tr>';
    }
}

// ─── Render Table ───────────────────────────────────────────────────────────
function renderTable() {
    const body = document.getElementById('resultsBody');
    const count = document.getElementById('resultCount');

    if (!allResults || allResults.length === 0) {
        body.innerHTML = '<tr><td colspan="11" class="text-center text-secondary py-8">No results. Try adjusting filters or <button onclick="fetchAllFunds()" class="text-blue-400 hover:underline">fetch fundamentals first</button>.</td></tr>';
        if (count) count.textContent = '(0)';
        return;
    }

    if (count) count.textContent = '(' + allResults.length + ')';

    // Update sort indicators
    document.querySelectorAll('#resultsTable th.sortable').forEach(function(th) {
        th.classList.remove('active');
        const icon = th.querySelector('.sort-icon');
        if (icon) icon.className = 'sort-icon fas';
    });
    const activeTh = document.querySelector('#resultsTable th.sortable[data-sort="' + sortField + '"]');
    if (activeTh) {
        activeTh.classList.add('active');
        const icon = activeTh.querySelector('.sort-icon');
        if (icon) icon.className = 'sort-icon fas fa-sort-' + (sortOrder === 'asc' ? 'up' : 'down');
    }

    body.innerHTML = allResults.map(function(r) {
        const peStr = r.peRatio != null ? fmt(r.peRatio) : '—';
        const epsStr = r.epsTtm != null ? '₹' + fmt(r.epsTtm) : '—';
        const roeStr = r.roe != null ? fmt(r.roe) + '%' : '—';
        const deStr = r.debtToEquity != null ? fmt(r.debtToEquity) : '—';
        const divStr = r.dividendYield != null ? (r.dividendYield * 100).toFixed(2) + '%' : '—';
        const betaStr = r.beta != null ? fmt(r.beta) : '—';
        const revStr = r.revenueTtm != null ? formatCr(r.revenueTtm) : '—';

        const peClass = r.peRatio ? (r.peRatio < 0 ? '' : r.peRatio <= 15 ? 'text-green-400' : r.peRatio <= 25 ? 'text-yellow-400' : 'text-red-400') : '';
        const roeClass = r.roe ? (r.roe >= 20 ? 'text-green-400' : r.roe >= 10 ? 'text-yellow-400' : 'text-red-400') : '';
        const deClass = r.debtToEquity ? (r.debtToEquity <= 0.5 ? 'text-green-400' : r.debtToEquity <= 1.5 ? 'text-yellow-400' : 'text-red-400') : '';

        return '<tr class="border-b border-gray-700 hover:bg-gray-700/50 transition-colors">' +
            '<td class="px-3 py-3"><a href="stock-detail.html?id=' + r.stockId + '" class="font-bold hover:text-blue-400 transition-colors">' + escHtml(r.symbol) + '</a></td>' +
            '<td class="px-3 py-3">' + escHtml(r.name || '—') + '</td>' +
            '<td class="px-3 py-3"><span class="text-xs bg-gray-600/50 px-1.5 py-0.5 rounded">' + escHtml(r.sector || '—') + '</span></td>' +
            '<td class="px-3 py-3 text-right font-medium">' + formatCr(r.marketCap) + '</td>' +
            '<td class="px-3 py-3 text-right font-medium ' + peClass + '">' + peStr + '</td>' +
            '<td class="px-3 py-3 text-right font-medium">' + epsStr + '</td>' +
            '<td class="px-3 py-3 text-right font-medium ' + roeClass + '">' + roeStr + '</td>' +
            '<td class="px-3 py-3 text-right font-medium ' + deClass + '">' + deStr + '</td>' +
            '<td class="px-3 py-3 text-right font-medium">' + divStr + '</td>' +
            '<td class="px-3 py-3 text-right font-medium">' + betaStr + '</td>' +
            '<td class="px-3 py-3 text-right font-medium">' + revStr + '</td>' +
            '</tr>';
    }).join('');
}

// ─── Sort ───────────────────────────────────────────────────────────────────
function sortBy(field) {
    if (sortField === field) {
        sortOrder = sortOrder === 'asc' ? 'desc' : 'asc';
    } else {
        sortField = field;
        sortOrder = 'desc';
    }
    applyFilters();
}

// ─── Reset Filters ─────────────────────────────────────────────────────────
function resetFilters() {
    document.querySelectorAll('.filter-card input').forEach(function(inp) { inp.value = ''; });
    document.getElementById('filterSector').value = '';
    sortField = 'marketCap';
    sortOrder = 'desc';
    applyFilters();
}

// ─── Fetch All Fundamentals ─────────────────────────────────────────────────
async function fetchAllFunds() {
    const btn = event.target;
    const orig = btn.innerHTML;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-1"></i>Fetching...';
    btn.disabled = true;
    try {
        const res = await fetchAllFundamentals();
        if (res && res.status === 'success') {
            showToast('Fetched fundamentals for all stocks');
            await loadSectors();
            await applyFilters();
        }
    } catch (e) {
        console.error('Error fetching all fundamentals:', e);
        showToast('Failed to fetch fundamentals', 'error');
    } finally {
        btn.innerHTML = orig;
        btn.disabled = false;
    }
}

// ─── Export CSV ─────────────────────────────────────────────────────────────
function exportCsv() {
    if (!allResults || allResults.length === 0) {
        showToast('No data to export', 'error');
        return;
    }

    const headers = ['Symbol', 'Name', 'Sector', 'Mkt Cap (Cr)', 'P/E', 'EPS', 'ROE%', 'D/E', 'Div%', 'Beta', 'Rev (Cr)'];
    const rows = allResults.map(function(r) {
        return [
            r.symbol,
            '"' + (r.name || '') + '"',
            '"' + (r.sector || '') + '"',
            r.marketCap ? (r.marketCap / 10000000).toFixed(2) : '',
            r.peRatio != null ? fmt(r.peRatio) : '',
            r.epsTtm != null ? fmt(r.epsTtm) : '',
            r.roe != null ? fmt(r.roe) : '',
            r.debtToEquity != null ? fmt(r.debtToEquity) : '',
            r.dividendYield != null ? (r.dividendYield * 100).toFixed(2) : '',
            r.beta != null ? fmt(r.beta) : '',
            r.revenueTtm ? (r.revenueTtm / 10000000).toFixed(2) : ''
        ];
    });

    const csv = [headers.join(','), ...rows.map(function(r) { return r.join(','); })].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fundamentals-screener.csv';
    a.click();
    URL.revokeObjectURL(url);
}

// ─── Helpers ───────────────────────────────────────────────────────────────
function fmt(num) {
    if (num == null) return '';
    if (typeof num === 'string') num = parseFloat(num);
    return num.toFixed(2);
}

function formatCr(num) {
    if (num == null) return '—';
    const cr = num / 10000000;
    if (cr >= 100) return '₹' + (cr / 100).toFixed(2) + 'K Cr';
    return '₹' + cr.toFixed(2) + ' Cr';
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

function showToast(msg, type) {
    const toast = document.createElement('div');
    toast.className = 'fixed bottom-4 right-4 px-4 py-2 rounded-lg shadow-lg text-sm font-medium z-50 transition-all duration-300 ' +
        (type === 'error' ? 'bg-red-600 text-white' : 'bg-green-600 text-white');
    toast.textContent = msg;
    document.body.appendChild(toast);
    setTimeout(function() { toast.remove(); }, 3000);
}
