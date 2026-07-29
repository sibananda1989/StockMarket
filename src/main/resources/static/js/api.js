const API_BASE_URL = '/api';

async function apiCall(endpoint, options = {}) {
    const url = `${API_BASE_URL}${endpoint}`;
    
    const defaultOptions = {
        headers: {
            'Content-Type': 'application/json',
        },
    };
    
    const mergedOptions = { ...defaultOptions, ...options };
    
    try {
        const response = await fetch(url, mergedOptions);
        const text = await response.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (e) {
                throw new Error('Invalid JSON response from server: ' + e.message);
            }
        }

        if (!response.ok) {
            throw new Error((data && data.message) || 'API call failed');
        }

        return data;
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

// Stock API
async function getAllStocks() {
    return apiCall('/stocks');
}

async function getSectors() {
    return apiCall('/stocks/sectors');
}

// Startup Tasks API
async function getStartupTasks() {
    return apiCall('/startup-tasks');
}

// Data Availability API
async function getDataAvailability() {
    return apiCall('/data-availability');
}

async function runStartupTasks(taskIds) {
    return apiCall('/startup-tasks/run', {
        method: 'POST',
        body: JSON.stringify(taskIds)
    });
}

async function runStartupTask(taskId) {
    return apiCall(`/startup-tasks/run-one/${taskId}`, { method: 'POST' });
}

async function getStockById(id) {
    return apiCall(`/stocks/${id}`);
}

async function addStockApi(stock) {
    return apiCall('/stocks', {
        method: 'POST',
        body: JSON.stringify(stock),
    });
}

async function updateStockApi(id, stock) {
    return apiCall(`/stocks/${id}`, {
        method: 'PUT',
        body: JSON.stringify(stock),
    });
}

async function deleteStockApi(id) {
    return apiCall(`/stocks/${id}`, {
        method: 'DELETE',
    });
}

async function updatePortfolioApi(id, data) {
    return apiCall(`/stocks/${id}/portfolio`, {
        method: 'PATCH',
        body: JSON.stringify(data),
    });
}

async function csvImportApi(stocks) {
    return apiCall('/stocks/csv-import', {
        method: 'POST',
        body: JSON.stringify(stocks),
    });
}

async function getSnapshotHistory(stockId, days = 90) {
    return apiCall(`/stocks/${stockId}/snapshots?days=${days}`);
}

async function syncAllHistory() {
    return apiCall('/stocks/sync-history', { method: 'POST' });
}

async function syncStockHistory(stockId, days = 730) {
    return apiCall(`/stocks/${stockId}/sync-history?days=${days}`, { method: 'POST' });
}

// Price API
async function savePrice(priceData) {
    return apiCall('/prices', {
        method: 'POST',
        body: JSON.stringify(priceData),
    });
}

async function getPriceHistory(stockId, fromDate, toDate) {
    let endpoint = `/prices/stock/${stockId}`;
    const params = new URLSearchParams();
    
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    
    if (params.toString()) {
        endpoint += `?${params.toString()}`;
    }
    
    return apiCall(endpoint);
}

async function getLatestPrice(stockId) {
    return apiCall(`/prices/stock/${stockId}/latest`);
}

// Portfolio Sync API
async function syncPortfolioHistory() {
    return apiCall('/stocks/portfolio/sync', {
        method: 'POST',
    });
}

// RSI API
async function getLatestRsi(stockId) {
    return apiCall(`/rsi/stock/${stockId}`);
}

async function getRsiHistory(stockId, days = 30) {
    return apiCall(`/rsi/stock/${stockId}/history?days=${days}`);
}

async function calculateRsi(stockId) {
    return apiCall(`/rsi/calculate/${stockId}`, {
        method: 'POST',
    });
}

async function calculateAllRsi() {
    return apiCall('/rsi/calculate-all', {
        method: 'POST',
    });
}


// Technical Indicators API
// NOTE: getLatestIndicators / getAllIndicatorHistory / getIndicatorHistory / calculateIndicators
// return RAW DTOs from the backend (not wrapped in ApiResponse). Callers must treat the
// resolved value as the data itself, e.g. `res.length`, `res.every(...)`, `render(res)`.
// Only fillIndicatorGaps returns a wrapped ApiResponse (use res.status / res.data).
async function getLatestIndicators(stockId) {
    return apiCall(`/indicators/${stockId}`);
}

async function getAllIndicatorHistory(stockId, fromDate, toDate) {
    return apiCall(`/indicators/${stockId}/history?fromDate=${fromDate}&toDate=${toDate}`);
}

async function calculateIndicators(stockId) {
    return apiCall(`/indicators/calculate/${stockId}`, {
        method: 'POST',
    });
}

async function resetIndicators(stockId, days = 365) {
    return apiCall(`/indicators/reset/${stockId}?days=${days}`, {
        method: 'POST',
    });
}

/**
 * Get indicator coverage report for a stock.
 * Returns {expected, available, missing, missingIndicators: []}.
 */
async function getIndicatorCoverage(stockId) {
    return apiCall(`/indicators/coverage/${stockId}`);
}

async function fillRsiGaps() {
    return apiCall('/rsi/fill-gaps', {
        method: 'POST',
    });
}

async function fillIndicatorGaps(days = 7) {
    return apiCall(`/indicators/fill-gaps?days=${days}`, {
        method: 'POST',
    });
}

// Expose key functions to global scope for use in inline scripts
window.fillRsiGaps = fillRsiGaps;
window.fillIndicatorGaps = fillIndicatorGaps;
window.resetIndicators = resetIndicators;

// Portfolio API
async function getPortfolioHistory(days = 365, all = false, portfolioId = null) {
    let endpoint = '/portfolio/history?';
    const params = [];
    if (!all) params.push(`days=${days}`);
    if (all) params.push('all=true');
    if (portfolioId) params.push(`portfolioId=${portfolioId}`);
    return apiCall(`/portfolio/history?${params.join('&')}`);
}

async function getPortfolioScreener(date) {
    const q = date ? `?date=${date}` : '';
    return apiCall(`/portfolio/screener${q}`);
}

async function recalculateAllPortfolios() {
    return apiCall('/stocks/recalculate-portfolio', {
        method: 'POST',
    });
}

// ─── Portfolio CRUD (multi-portfolio) ──────────────────────────────────

async function getPortfolios() {
    return apiCall('/portfolios');
}

async function createPortfolio(name, description) {
    return apiCall('/portfolios', {
        method: 'POST',
        body: JSON.stringify({ name, description }),
    });
}

async function getPortfolio(id) {
    return apiCall(`/portfolios/${id}`);
}

async function deletePortfolio(id) {
    return apiCall(`/portfolios/${id}`, {
        method: 'DELETE',
    });
}

async function updatePortfolio(id, name, description) {
    return apiCall(`/portfolios/${id}`, {
        method: 'PUT',
        body: JSON.stringify({ name, description }),
    });
}

async function getDefaultPortfolio() {
    return apiCall('/portfolios/default');
}

async function getPortfolioHoldings(portfolioId) {
    return apiCall(`/portfolios/${portfolioId}/holdings`);
}

async function getPortfolioHoldingByStock(portfolioId, stockId) {
    return apiCall(`/portfolios/${portfolioId}/holdings/stock/${stockId}`);
}

async function getAggregateHolding(stockId) {
    return apiCall(`/portfolios/all/holdings/stock/${stockId}`);
}

async function addHolding(portfolioId, stockId, quantity, avgPrice) {
    return apiCall(`/portfolios/${portfolioId}/holdings`, {
        method: 'POST',
        body: JSON.stringify({ stockId, quantity, avgPrice }),
    });
}

async function updateHolding(portfolioId, holdingId, quantity, avgPrice) {
    return apiCall(`/portfolios/${portfolioId}/holdings/${holdingId}`, {
        method: 'PATCH',
        body: JSON.stringify({ quantity, avgPrice }),
    });
}

async function removeHolding(portfolioId, holdingId) {
    return apiCall(`/portfolios/${portfolioId}/holdings/${holdingId}`, {
        method: 'DELETE',
    });
}

async function removeHoldingByStock(portfolioId, stockId) {
    return apiCall(`/portfolios/${portfolioId}/holdings/stock/${stockId}`, {
        method: 'DELETE',
    });
}

async function recalculatePortfolioApi(portfolioId) {
    return apiCall(`/portfolios/${portfolioId}/recalculate`, {
        method: 'POST',
    });
}

// ─── Portfolio Transactions (ledger) ─────────────────────────────────

async function recordTransaction(portfolioId, payload) {
    return apiCall(`/portfolios/${portfolioId}/transactions`, {
        method: 'POST',
        body: JSON.stringify(payload),
    });
}

async function getTransactions(portfolioId, stockId) {
    const q = stockId != null ? `?stockId=${encodeURIComponent(stockId)}` : '';
    return apiCall(`/portfolios/${portfolioId}/transactions${q}`);
}

async function getAllTransactionsByStock(stockId) {
    return apiCall(`/portfolios/all/transactions-by-stock?stockId=${encodeURIComponent(stockId)}`);
}

async function deleteTransaction(portfolioId, txId) {
    return apiCall(`/portfolios/${portfolioId}/transactions/${txId}`, {
        method: 'DELETE',
    });
}

// FII/DII API
async function getFiiDiiData() {
    return apiCall('/fiidii');
}

async function triggerFiiDiiRefresh() {
    return apiCall('/fiidii/refresh', { method: 'POST' });
}

// Corporate Events API
async function getEventData(symbol) {
    return apiCall(`/events?symbol=${encodeURIComponent(symbol)}`);
}

async function triggerEventsRefresh() {
    return apiCall('/events/refresh', { method: 'POST' });
}

// Stock Signal API
async function getStockSignal(stockId) {
    return apiCall(`/signals/${stockId}`);
}

// Signal History API
async function getSignalHistory(stockId, days = 90) {
    return apiCall(`/signals/${stockId}/history?days=${days}`);
}

// Backtest API
async function getBacktestData(stockId, stopLoss, positionSizePct) {
    let endpoint = `/backtest/stock/${stockId}`;
    const params = new URLSearchParams();
    if (stopLoss !== undefined) params.append('stopLoss', stopLoss);
    if (positionSizePct !== undefined) params.append('positionSizePct', positionSizePct);
    const qs = params.toString();
    if (qs) endpoint += '?' + qs;
    return apiCall(endpoint);
}

// ─── Watchlist API ──────────────────────────────────────────────────────────

async function getWatchlists() {
    return apiCall('/watchlists');
}

async function createWatchlist(name, description) {
    return apiCall('/watchlists', {
        method: 'POST',
        body: JSON.stringify({ name, description }),
    });
}

async function updateWatchlist(id, name, description) {
    return apiCall(`/watchlists/${id}`, {
        method: 'PUT',
        body: JSON.stringify({ name, description }),
    });
}

async function deleteWatchlist(id) {
    return apiCall(`/watchlists/${id}`, {
        method: 'DELETE',
    });
}

async function getWatchlistDetail(id) {
    return apiCall(`/watchlists/${id}/detail`);
}

async function getWatchlistItems(id) {
    return apiCall(`/watchlists/${id}/items`);
}

async function addStockToWatchlist(watchlistId, stockId) {
    return apiCall(`/watchlists/${watchlistId}/items`, {
        method: 'POST',
        body: JSON.stringify({ stockId }),
    });
}

async function removeStockFromWatchlist(watchlistId, stockId) {
    return apiCall(`/watchlists/${watchlistId}/items/${stockId}`, {
        method: 'DELETE',
    });
}

async function addBatchToWatchlist(watchlistId, stockIds) {
    return apiCall(`/watchlists/${watchlistId}/items/batch`, {
        method: 'POST',
        body: JSON.stringify({ stockIds }),
    });
}

async function getWatchlistsForStock(stockId) {
    return apiCall(`/watchlists/stock/${stockId}`);
}

async function syncWatchlistHistory(watchlistId, days = 730) {
    return apiCall(`/watchlists/${watchlistId}/sync-history`, {
        method: 'POST',
        body: JSON.stringify({ days })
    });
}

// Stock Symbol Validation via Yahoo Finance
async function validateStockSymbol(symbol) {
    return apiCall(`/stocks/validate-symbol?symbol=${encodeURIComponent(symbol)}`);
}

async function searchStocksByName(name, limit = 10) {
    return apiCall(`/stocks/search?name=${encodeURIComponent(name)}&limit=${limit}`);
}

// ─── Support & Resistance API ────────────────────────────────────────────────

async function getSupportResistance(stockId, lookbackDays) {
    let endpoint = `/support-resistance/${stockId}`;
    if (lookbackDays) {
        endpoint += `?lookbackDays=${lookbackDays}`;
    }
    return apiCall(endpoint);
}

async function calculateSupportResistance(stockId) {
    return apiCall(`/support-resistance/calculate/${stockId}`, {
        method: 'POST',
    });
}

async function getSupportResistanceHistory(stockId, fromDate, toDate) {
    let endpoint = `/support-resistance/${stockId}/history`;
    const params = new URLSearchParams();
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    const qs = params.toString();
    if (qs) endpoint += '?' + qs;
    return apiCall(endpoint);
}

// ─── Institutional Activity API ─────────────────────────────────────────

// Shareholding Pattern
async function getInstitutionalHolding(stockId) {
    return apiCall(`/institutional/holdings/${stockId}`);
}

async function getInstitutionalHoldingHistory(stockId) {
    return apiCall(`/institutional/holdings/${stockId}/history`);
}

async function getAllInstitutionalHoldings() {
    return apiCall('/institutional/holdings/all');
}

async function fetchAllInstitutionalHoldings() {
    return apiCall('/institutional/holdings/fetch-all', { method: 'POST' });
}

// XBRL Detailed Shareholding
async function fetchAllXbrlData() {
    return apiCall('/institutional/xbrl/fetch-all', { method: 'POST' });
}

async function fetchXbrlForStock(stockId) {
    return apiCall(`/institutional/xbrl/fetch/${stockId}`, { method: 'POST' });
}

// Bulk Deals
async function getBulkDeals(stockId) {
    return apiCall(`/institutional/bulk-deals/${stockId}`);
}

async function getBulkDealsRange(from, to) {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return apiCall(`/institutional/bulk-deals/range?${params.toString()}`);
}

async function fetchBulkDeals(from, to) {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return apiCall(`/institutional/bulk-deals/fetch?${params.toString()}`, { method: 'POST' });
}

async function fetchTodayBulkDeals() {
    return apiCall('/institutional/bulk-deals/fetch-today', { method: 'POST' });
}

// Block Deals
async function getBlockDeals(stockId) {
    return apiCall(`/institutional/block-deals/${stockId}`);
}

async function getBlockDealsRange(from, to) {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return apiCall(`/institutional/block-deals/range?${params.toString()}`);
}

async function fetchBlockDeals(from, to) {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return apiCall(`/institutional/block-deals/fetch?${params.toString()}`, { method: 'POST' });
}

async function fetchTodayBlockDeals() {
    return apiCall('/institutional/block-deals/fetch-today', { method: 'POST' });
}

// Institutional Score
async function getInstitutionalScore(stockId) {
    return apiCall(`/institutional/score/${stockId}`);
}

async function getAllInstitutionalScores() {
    return apiCall('/institutional/score/all');
}

// Screeners
async function screenerFiiAccumulation() {
    return apiCall('/institutional/screeners/fii-accumulation');
}

async function screenerDiiAccumulation() {
    return apiCall('/institutional/screeners/dii-accumulation');
}

async function screenerMutualFundAccumulation() {
    return apiCall('/institutional/screeners/mf-accumulation');
}

async function screenerInstitutionalStrongBuy() {
    return apiCall('/institutional/screeners/institutional-strong-buy');
}

async function screenerInstitutionalPriceActionBuy() {
    return apiCall('/institutional/screeners/institutional-price-action-buy');
}

async function screenerRecentBulkDeals() {
    return apiCall('/institutional/screeners/recent-bulk-deals');
}

async function screenerRecentBlockDeals() {
    return apiCall('/institutional/screeners/recent-block-deals');
}

async function screenerAllInstitutional() {
    return apiCall('/institutional/screeners/all');
}

// ─── Fundamental Data API ─────────────────────────────────────────────────
async function getFundamentals(stockId) {
    return apiCall(`/fundamentals/${stockId}`);
}

async function fetchFundamentals(stockId) {
    return apiCall(`/fundamentals/fetch/${stockId}`, { method: 'POST' });
}

async function fetchAllFundamentals() {
    return apiCall('/fundamentals/fetch-all', { method: 'POST' });
}

async function screenFundamentals(criteria) {
    return apiCall('/fundamentals/screen', {
        method: 'POST',
        body: JSON.stringify(criteria),
    });
}

async function getFundamentalSectors() {
    return apiCall('/fundamentals/sectors');
}

// ─── Multi-Strategy Signal API ────────────────────────────────────────────

function _buildActiveParam(activeArray) {
    if (!activeArray || activeArray.length === 0) return '';
    return '?active=' + activeArray.map(encodeURIComponent).join('&active=');
}

async function getMultiStrategySignal(stockId, activeStrategies = null) {
    const qs = _buildActiveParam(activeStrategies);
    return apiCall(`/signals/multi-strategy/${stockId}${qs}`);
}

async function getMultiStrategyBreakdown(stockId, activeStrategies = null) {
    const qs = _buildActiveParam(activeStrategies);
    return apiCall(`/signals/multi-strategy/${stockId}/breakdown${qs}`);
}

async function compareMultiStrategySignals(stockId, activeStrategies = null) {
    const qs = _buildActiveParam(activeStrategies);
    return apiCall(`/signals/multi-strategy/compare/${stockId}${qs}`);
}

async function getMultiStrategySignalHistory(stockId, days = 365, activeStrategies = null) {
    const qs = _buildActiveParam(activeStrategies);
    return apiCall(`/signals/multi-strategy/${stockId}/history?days=${days}${qs ? '&' + qs.substring(1) : ''}`);
}

// ─── Strategy Config API ────────────────────────────────────────────────────

async function getStrategyConfigs() {
    return apiCall('/strategy-config');
}

async function toggleStrategyConfig(strategyName, active) {
    return apiCall(`/strategy-config/${encodeURIComponent(strategyName)}`, {
        method: 'PUT',
        body: JSON.stringify({ active })
    });
}

// ─── Score Parameters API ─────────────────────────────────────────────────

async function getScoreParameters() {
    return apiCall('/score-parameters');
}

async function updateScoreParameter(paramKey, enabled) {
    return apiCall(`/score-parameters/${encodeURIComponent(paramKey)}`, {
        method: 'PUT',
        body: JSON.stringify({ enabled: enabled })
    });
}

async function resetScoreParameters() {
    return apiCall('/score-parameters/reset', { method: 'POST' });
}

// ─── Strategy Condition API ────────────────────────────────────────────

async function getStrategyFull() {
    const res = await apiCall('/strategy-config/full');
    return res.data;
}

async function getStrategyStats() {
    const res = await apiCall('/strategy-config/stats');
    return res.data;
}

async function saveStrategyConditions(strategyName, conditionList) {
    const res = await apiCall(`/strategy-config/conditions/${encodeURIComponent(strategyName)}`, {
        method: 'PUT',
        body: JSON.stringify(conditionList)
    });
    return res.data;
}

async function updateStrategyPriority(strategyName, priority) {
    const res = await apiCall(`/strategy-config/${encodeURIComponent(strategyName)}/priority?priority=${priority}`, {
        method: 'PUT'
    });
    return res.data;
}

async function resetStrategyConditions() {
    const res = await apiCall('/strategy-config/conditions/reset', {
        method: 'POST'
    });
    return res.data;
}

async function toggleCondition(strategyName, conditionId, enabled) {
    const res = await apiCall(`/strategy-config/conditions/${encodeURIComponent(strategyName)}/${encodeURIComponent(conditionId)}`, {
        method: 'PATCH',
        body: JSON.stringify({ enabled })
    });
    return res.data;
}

// ─── Volume Spike Factor API ──────────────────────────────────────────────

async function getVolumeSpikeFactor() {
    return apiCall('/strategy-config/volume/spike-factor');
}

async function updateVolumeSpikeFactor(factor) {
    return apiCall(`/strategy-config/volume/spike-factor?factor=${factor}`, { method: 'PUT' });
}

// ─── SMC/ICT Pattern Detection API ──────────────────────────────────────────

async function getSMCPatterns(stockId, lookbackDays = 365) {
    return apiCall(`/smc/${stockId}?lookbackDays=${lookbackDays}`);
}
