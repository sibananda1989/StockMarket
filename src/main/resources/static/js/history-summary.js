let allResults = [];
let fetchTime = null;
let progressAnimId = null;

document.addEventListener('DOMContentLoaded', () => {
    fetchLocalSummaries();
});

async function fetchLocalSummaries() {
    const days = document.getElementById('daysSelect').value;
    showLoading();

    try {
        const response = await fetch(`/api/stocks/history/summary/local?days=${days}`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Failed to fetch local summaries');
        }

        allResults = data.data || [];
        fetchTime = new Date();
        displayResults(allResults);
    } catch (error) {
        showError(error.message);
    }
}

function startProgressAnimation() {
    const bar = document.getElementById('progressBar');
    const label = document.getElementById('progressLabel');
    const pct = document.getElementById('progressPercent');
    let progress = 0;
    const steps = [
        { limit: 10, msg: 'Connecting to Yahoo Finance...', rate: 2 },
        { limit: 25, msg: 'Fetching symbol list...', rate: 1.5 },
        { limit: 45, msg: 'Downloading historical data...', rate: 1.2 },
        { limit: 65, msg: 'Processing summaries...', rate: 0.8 },
        { limit: 80, msg: 'Almost there...', rate: 0.5 },
        { limit: 90, msg: 'Finalizing results...', rate: 0.3 }
    ];

    function step() {
        if (progressAnimId === null) return;
        let currentStep = steps.find(s => progress < s.limit) || steps[steps.length - 1];
        if (progress < 90) {
            progress += currentStep.rate;
            if (progress > currentStep.limit) progress = currentStep.limit;
        }
        bar.style.width = Math.min(progress, 90) + '%';
        label.textContent = currentStep.msg;
        pct.textContent = Math.round(progress) + '%';
        progressAnimId = requestAnimationFrame(step);
    }
    progressAnimId = requestAnimationFrame(step);
}

function stopProgressAnimation() {
    if (progressAnimId !== null) {
        cancelAnimationFrame(progressAnimId);
        progressAnimId = null;
    }
    const bar = document.getElementById('progressBar');
    const label = document.getElementById('progressLabel');
    const pct = document.getElementById('progressPercent');
    bar.style.width = '100%';
    label.textContent = 'Complete';
    pct.textContent = '100%';
}

async function fetchAllSummaries() {
    const days = document.getElementById('daysSelect').value;
    showLoading();
    startProgressAnimation();

    try {
        const response = await fetch(`/api/stocks/history/summary/all?days=${days}`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Failed to fetch summaries');
        }

        allResults = data.data || [];
        fetchTime = new Date();
        stopProgressAnimation();
        await new Promise(r => setTimeout(r, 400));
        displayResults(allResults);
    } catch (error) {
        stopProgressAnimation();
        await new Promise(r => setTimeout(r, 400));
        showError(error.message);
    }
}

function displayResults(results) {
    hideAllStates();

    if (!results.length) {
        document.getElementById('emptyState').classList.remove('hidden');
        return;
    }

    document.getElementById('contentState').classList.remove('hidden');
    document.getElementById('summaryCards').classList.remove('hidden');

    const success = results.filter(r => r.success);
    const failed = results.filter(r => !r.success);

    document.getElementById('totalStocks').textContent = results.length;
    document.getElementById('successCount').textContent = success.length;
    document.getElementById('failedCount').textContent = failed.length;
    document.getElementById('lastFetched').textContent = fetchTime.toLocaleTimeString();

    renderTable(results);
}

function renderTable(results) {
    const tbody = document.getElementById('resultsTableBody');

    if (!results.length) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center py-8 text-secondary">No results to display</td></tr>';
        return;
    }

    tbody.innerHTML = results.map(r => {
        if (!r.success) {
            return `
                <tr class="border-b border-gray-700 result-error">
                    <td class="px-4 py-3 font-bold">${r.symbol}</td>
                    <td class="px-4 py-3">${r.yahooSymbol || '--'}</td>
                    <td class="px-4 py-3 text-right" colspan="5">
                        <span class="badge badge-danger">Failed: ${r.error}</span>
                    </td>
                    <td class="px-4 py-3 text-right">--</td>
                    <td class="px-4 py-3 text-center">
                        <span class="badge badge-danger">Error</span>
                    </td>
                </tr>`;
        }

        const s = r.summary;
        const changeClass = s.percentChange >= 0 ? 'text-green-500' : 'text-red-500';

        return `
            <tr class="border-b border-gray-700 result-success">
                <td class="px-4 py-3 font-bold">${r.symbol}</td>
                <td class="px-4 py-3"><span class="badge badge-info">${r.yahooSymbol}</span></td>
                <td class="px-4 py-3 text-right font-medium">${currency(s.currentPrice)}</td>
                <td class="px-4 py-3 text-right text-green-500">${currency(s.highestPrice)}</td>
                <td class="px-4 py-3 text-right text-red-500">${currency(s.lowestPrice)}</td>
                <td class="px-4 py-3 text-right">${currency(s.averageClose)}</td>
                <td class="px-4 py-3 text-right ${changeClass} font-medium">${pct(s.percentChange)}</td>
                <td class="px-4 py-3 text-right">${s.totalDays}</td>
                <td class="px-4 py-3 text-center">
                    <span class="badge badge-success">Success</span>
                </td>
            </tr>`;
    }).join('');
}

function filterTable() {
    const term = document.getElementById('tableSearch').value.toLowerCase();
    const filtered = term
        ? allResults.filter(r =>
            r.symbol.toLowerCase().includes(term) ||
            (r.yahooSymbol && r.yahooSymbol.toLowerCase().includes(term)))
        : allResults;
    renderTable(filtered);
}

function currency(val) {
    return '\u20B9' + val.toFixed(2);
}

function pct(val) {
    const sign = val >= 0 ? '+' : '';
    return sign + val.toFixed(2) + '%';
}

function showLoading() {
    hideAllStates();
    document.getElementById('loadingState').classList.remove('hidden');
    document.getElementById('progressBar').style.width = '0%';
    document.getElementById('progressLabel').textContent = 'Starting...';
    document.getElementById('progressPercent').textContent = '0%';
}

function showError(message) {
    hideAllStates();
    document.getElementById('errorState').classList.remove('hidden');
    document.getElementById('errorMessage').textContent = message;
}

function hideAllStates() {
    stopProgressAnimation();
    document.getElementById('loadingState').classList.add('hidden');
    document.getElementById('errorState').classList.add('hidden');
    document.getElementById('emptyState').classList.add('hidden');
    document.getElementById('contentState').classList.add('hidden');
    document.getElementById('summaryCards').classList.add('hidden');
}
