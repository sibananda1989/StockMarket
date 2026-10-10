// EMA Cross Screener page logic.
// Uses the getEmaCrossSignals() wrapper from api.js.

let currentCrossData = null;

document.addEventListener('DOMContentLoaded', function () {
    loadEmaCrosses(document.getElementById('daysSelect').value || 5);
});

async function reloadEmaCross(days) {
    await loadEmaCrosses(days);
}

async function loadEmaCrosses(days) {
    const tableBody = document.getElementById('crossTableBody');
    const emptyState = document.getElementById('emptyState');
    const warningsContainer = document.getElementById('warningsContainer');
    const lastUpdated = document.getElementById('lastUpdated');

    // Show loading row.
    tableBody.innerHTML = `
        <tr><td colspan="6" class="px-4 py-8 text-center text-secondary">
            <div class="flex justify-center items-center gap-3">
                <div class="loading-spinner mr-3"></div>
                <span>Scanning for EMA crosses...</span>
            </div>
        </td></tr>`;
    emptyState.style.display = 'none';
    warningsContainer.style.display = 'none';

    try {
        const response = await getEmaCrossSignals(days);
        const data = response.data;
        currentCrossData = data;
        renderEmaCrosses(data);
        lastUpdated.textContent = 'Anchor date: ' + (data.anchorDate || '-');
    } catch (error) {
        console.error('Failed to load EMA crosses:', error);
        tableBody.innerHTML = `
            <tr><td colspan="6" class="px-4 py-8 text-center text-red-400">
                <i class="fas fa-exclamation-triangle mr-2"></i>Failed to load EMA crosses: ${error.message}
            </td></tr>`;
    }
}

function renderEmaCrosses(data) {
    const tableBody = document.getElementById('crossTableBody');
    const signals = (data && data.signals) || [];

    document.getElementById('crossCount').textContent = signals.length;
    document.getElementById('universeCount').textContent = data.universeScanned || 0;
    document.getElementById('warningCount').textContent = (data.warnings || []).length;
    document.getElementById('anchorDate').textContent = data.anchorDate || '-';

    if (!signals.length) {
        tableBody.innerHTML = '';
        document.getElementById('emptyState').style.display = '';
        return;
    }

    tableBody.innerHTML = signals.map(s => `
        <tr class="border-b border-gray-800 hover:bg-gray-800/40">
            <td class="px-4 py-3">
                <a href="stock-detail.html?id=${s.stockId}" target="_blank" rel="noopener" class="text-blue-400 hover:underline font-medium">${s.symbol}</a>
            </td>
            <td class="px-4 py-3 text-secondary">${s.companyName || '-'}</td>
            <td class="px-4 py-3 text-right text-green-400">${formatNum(s.ema20AtCross)}</td>
            <td class="px-4 py-3 text-right text-red-400">${formatNum(s.ema50AtCross)}</td>
            <td class="px-4 py-3 text-right">${formatNum(s.lastClose)}</td>
            <td class="px-4 py-3 text-center text-secondary">${s.crossDate}${s.daysAgo > 0 ? ` (${s.daysAgo}d ago)` : ''}</td>
        </tr>`).join('');

    renderWarnings(data.warnings);
}

function renderWarnings(warnings) {
    const container = document.getElementById('warningsContainer');
    if (warnings && warnings.length) {
        document.getElementById('warningsList').innerHTML =
            warnings.map(w => `<li>${w}</li>`).join('');
        container.style.display = '';
    } else {
        container.style.display = 'none';
    }
}

function formatNum(v) {
    if (v == null) return '-';
    return Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}