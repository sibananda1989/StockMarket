// Watchlist Opportunities - Daily Opportunity Ranking
// State
let opportunities = [];
let currentSort = 'score';
let selectedPortfolioId = null;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    loadPortfolios();
});

// Load portfolios for dropdown
async function loadPortfolios() {
    try {
        const response = await fetch('/api/portfolios');
        const result = await response.json();

        if (result.status === 'success' && result.data) {
            const portfolios = result.data;
            const select = document.getElementById('portfolioSelect');

            select.innerHTML = portfolios.map(p =>
                `<option value="${p.id}">${p.name}${p.default ? ' (Default)' : ''}</option>`
            ).join('');

            // Select default portfolio
            const defaultPortfolio = portfolios.find(p => p.default);
            if (defaultPortfolio) {
                selectedPortfolioId = defaultPortfolio.id;
                select.value = defaultPortfolio.id;
            }

            await loadOpportunities();
        }
    } catch (error) {
        console.error('Error loading portfolios:', error);
        hideLoading();
        showEmpty();
    }
}

// Switch portfolio
async function switchPortfolio(portfolioId) {
    selectedPortfolioId = portfolioId || null;
    await loadOpportunities();
}

// Load opportunities from API
async function loadOpportunities() {
    showLoading();

    try {
        const url = selectedPortfolioId
            ? `/api/watchlists/opportunities?portfolioId=${selectedPortfolioId}`
            : '/api/watchlists/opportunities';

        const response = await fetch(url);
        const result = await response.json();

        if (result.status === 'success' && result.data) {
            opportunities = result.data.stocks || [];

            if (opportunities.length === 0) {
                hideLoading();
                showEmpty();
                return;
            }

            renderSummary(result.data.summary);
            renderOpportunities();
            renderWarnings(result.data.warnings);
            updateLastUpdated();

            hideLoading();
            showList();
        } else {
            throw new Error(result.message || 'Failed to load opportunities');
        }
    } catch (error) {
        console.error('Error loading opportunities:', error);
        hideLoading();
        showEmpty();
    }
}

// Refresh opportunities
async function refreshOpportunities() {
    const btn = document.getElementById('refreshBtn');
    btn.disabled = true;
    btn.innerHTML = '<div class="loading-spinner" style="width: 16px; height: 16px; border-width: 2px;"></div> <span>Refreshing...</span>';

    await loadOpportunities();

    btn.disabled = false;
    btn.innerHTML = '<i class="fas fa-sync-alt"></i> <span>Refresh</span>';
}

// Render summary
function renderSummary(summary) {
    if (!summary) return;

    document.getElementById('buyCount').textContent = summary.buyOpportunities || 0;
    document.getElementById('holdCount').textContent = summary.holdOpportunities || 0;
    document.getElementById('sellCount').textContent = summary.sellOpportunities || 0;
    document.getElementById('totalCount').textContent = summary.totalStocks || 0;
    document.getElementById('summaryBar').style.display = 'block';
    document.getElementById('sortControls').style.display = 'block';
}

// Render opportunities
function renderOpportunities() {
    const container = document.getElementById('opportunitiesList');
    container.innerHTML = opportunities.map(opp => createOpportunityCard(opp)).join('');
}

// Create opportunity card
function createOpportunityCard(opp) {
    const signalClass = getSignalClass(opp.signal);
    const rankClass = opp.rank <= 3 ? 'top-3' : '';

    return `
        <div class="opportunity-card card rounded-xl p-4 shadow-lg" data-stock-id="${opp.stockId}">
            <div class="flex flex-wrap items-center gap-4">
                <!-- Rank -->
                <div class="rank-badge ${rankClass}">${opp.rank}</div>

                <!-- Symbol & Name -->
                <div class="flex-1 min-w-[150px]">
                    <a href="stock-detail.html?id=${opp.stockId}" target="_blank" rel="noopener" class="font-bold text-lg text-blue-400 hover:underline">${opp.symbol}</a>
                    <div class="text-sm text-secondary truncate max-w-[200px]">${opp.companyName || ''}</div>
                </div>

                <!-- Price -->
                <div class="text-right min-w-[100px]">
                    <div class="font-bold text-lg">₹${formatPrice(opp.currentPrice)}</div>
                    <div class="text-sm text-secondary">Current</div>
                </div>

                <!-- Signal -->
                <div class="min-w-[100px]">
                    <span class="signal-badge ${signalClass}">${opp.signal}</span>
                    <div class="text-xs text-secondary mt-1">Score: ${opp.compositeScore}</div>
                </div>

                <!-- Entry Zone -->
                <div class="text-center min-w-[120px]">
                    <div class="text-sm font-medium">₹${formatPrice(opp.entryZone?.low)} - ₹${formatPrice(opp.entryZone?.high)}</div>
                    <div class="text-xs text-secondary">Entry Zone</div>
                </div>

                <!-- Stop Loss -->
                <div class="text-center min-w-[80px]">
                    <div class="text-sm font-medium text-red-400">₹${formatPrice(opp.stopLoss)}</div>
                    <div class="text-xs text-secondary">Stop</div>
                </div>

                <!-- Target -->
                <div class="text-center min-w-[80px]">
                    <div class="text-sm font-medium text-green-400">₹${formatPrice(opp.target)}</div>
                    <div class="text-xs text-secondary">Target</div>
                </div>

                <!-- Risk/Reward -->
                <div class="text-center min-w-[70px]">
                    <div class="text-sm font-medium ${opp.riskRewardRatio >= 2 ? 'text-green-400' : opp.riskRewardRatio >= 1 ? 'text-yellow-400' : 'text-red-400'}">
                        ${opp.riskRewardRatio ? opp.riskRewardRatio + ':1' : 'N/A'}
                    </div>
                    <div class="text-xs text-secondary">R:R</div>
                </div>

                <!-- Agreement -->
                <div class="text-center min-w-[70px]">
                    <div class="text-sm font-medium">${opp.indicatorAgreement ? opp.indicatorAgreement.toFixed(0) + '%' : 'N/A'}</div>
                    <div class="text-xs text-secondary">Agree</div>
                </div>

                <!-- Confidence -->
                <div class="text-center min-w-[70px]">
                    <div class="text-sm font-medium">${opp.confidenceScore || 'N/A'}</div>
                    <div class="text-xs text-secondary">Conf</div>
                </div>
            </div>

            <!-- Reasons -->
            ${opp.reasons && opp.reasons.length > 0 ? `
                <div class="mt-3 pt-3 border-t border-gray-700">
                    <div class="flex flex-wrap gap-2">
                        ${opp.reasons.map(r => `
                            <span class="reason-tag" title="${r.interpretation}">
                                <i class="fas fa-info-circle mr-1"></i>${r.factor}: ${r.interpretation}
                            </span>
                        `).join('')}
                    </div>
                </div>
            ` : ''}
        </div>
    `;
}

// Sort opportunities
function sortOpportunities(criteria) {
    currentSort = criteria;

    // Update button states
    document.querySelectorAll('.sort-btn').forEach(btn => {
        btn.classList.remove('active');
        btn.classList.add('bg-gray-700', 'hover:bg-gray-600');
    });
    document.querySelector(`[data-sort="${criteria}"]`).classList.add('active');
    document.querySelector(`[data-sort="${criteria}"]`).classList.remove('bg-gray-700', 'hover:bg-gray-600');

    // Sort
    switch (criteria) {
        case 'score':
            opportunities.sort((a, b) => b.compositeScore - a.compositeScore);
            break;
        case 'riskReward':
            opportunities.sort((a, b) => (b.riskRewardRatio || 0) - (a.riskRewardRatio || 0));
            break;
        case 'agreement':
            opportunities.sort((a, b) => (b.indicatorAgreement || 0) - (a.indicatorAgreement || 0));
            break;
    }

    // Re-render
    renderOpportunities();
}

// Render warnings
function renderWarnings(warnings) {
    const container = document.getElementById('warningsContainer');
    const list = document.getElementById('warningsList');

    if (!warnings || warnings.length === 0) {
        container.style.display = 'none';
        return;
    }

    list.innerHTML = warnings.map(w => `<li><i class="fas fa-exclamation-circle mr-2"></i>${w}</li>`).join('');
    container.style.display = 'block';
}

// Update last updated timestamp
function updateLastUpdated() {
    const now = new Date();
    const timeStr = now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
    document.getElementById('lastUpdated').textContent = `Last updated: ${timeStr}`;
}

// Format price
function formatPrice(price) {
    if (price === null || price === undefined) return 'N/A';
    return parseFloat(price).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// Get signal class
function getSignalClass(signal) {
    if (!signal) return 'signal-hold';
    const normalized = signal.toLowerCase().replace(/\s+/g, '-');
    return `signal-${normalized}`;
}

// UI state helpers
function showLoading() {
    document.getElementById('loadingState').style.display = 'flex';
    document.getElementById('emptyState').style.display = 'none';
    document.getElementById('opportunitiesList').style.display = 'none';
}

function hideLoading() {
    document.getElementById('loadingState').style.display = 'none';
}

function showEmpty() {
    document.getElementById('emptyState').style.display = 'block';
    document.getElementById('opportunitiesList').style.display = 'none';
    document.getElementById('summaryBar').style.display = 'none';
    document.getElementById('sortControls').style.display = 'none';
}

function showList() {
    document.getElementById('opportunitiesList').style.display = 'block';
    document.getElementById('emptyState').style.display = 'none';
}
