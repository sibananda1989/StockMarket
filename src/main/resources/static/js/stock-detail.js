// ─── Stock Detail — Main Entry Point ─────────────────────────────────────────
// This file wires up the DOM-ready handler and global event bindings.
// All business logic lives in js/modules/.

document.addEventListener('DOMContentLoaded', function() {
    const urlParams = new URLSearchParams(window.location.search);
    _stockId = urlParams.get('id');
    _portfolioId = urlParams.get('portfolioId') ? parseInt(urlParams.get('portfolioId')) : null;

    if (!_stockId) {
        document.getElementById('stockTitle').textContent = 'Stock not found';
        document.getElementById('stockSubtitle').textContent = '';
        return;
    }

    // Fix history button link
    const historyBtn = document.getElementById('historyBtn');
    if (historyBtn) historyBtn.href = 'stock-history.html?stockId=' + _stockId;

    // Wire the S/R Refresh button
    const srRefreshBtn = document.getElementById('srRefreshBtn');
    if (srRefreshBtn) srRefreshBtn.addEventListener('click', refreshSupportResistance);

    // Wire the Fundamentals Refresh button
    const fundRefreshBtn = document.getElementById('fundRefreshBtn');
    if (fundRefreshBtn) fundRefreshBtn.addEventListener('click', () => loadFundamentalsTab(true));

    getStockById(_stockId)
        .then(response => {
            if (response.status === 'success' && response.data) {
                const stock = response.data.stock;
                _symbol = stock.symbol || '';
                _stockData = stock;
                document.getElementById('stockTitle').textContent = stock.name || 'Unknown';
                document.getElementById('stockSubtitle').textContent =
                    (_symbol ? _symbol + ' - ' : '') + (stock.sector || '');

                const holdingPromise = _portfolioId
                    ? getPortfolioHoldingByStock(_portfolioId, parseInt(_stockId))
                    : getAggregateHolding(parseInt(_stockId)).catch(() => null);
                holdingPromise
                    .then(holdingRes => {
                        if (holdingRes && holdingRes.status === 'success' && holdingRes.data) {
                            const h = holdingRes.data;
                            Object.assign(_stockData, {
                                quantity: h.quantity,
                                avgPrice: h.avgPrice,
                                investment: h.investment,
                                currentValue: h.currentValue,
                                pnl: h.pnl,
                                pnlPercent: h.pnlPercent
                            });
                        }
                        return loadStockData();
                    })
                    .catch(() => loadStockData());
            } else {
                document.getElementById('stockTitle').textContent = 'Error loading stock';
                document.getElementById('stockSubtitle').textContent = '';
            }
        })
        .catch(error => {
            console.error('Error fetching stock data:', error);
            document.getElementById('stockTitle').textContent = 'Error loading stock';
            document.getElementById('stockSubtitle').textContent = '';
        });

    // Scroll to stock name / hero summary after a brief delay so the page
    // lands directly on the stock info block instead of the navigation bar.
    // Uses a timeout to let any in-flight API calls start first; the actual
    // scroll target is always available since it's static HTML.
    const heroEl = document.getElementById('heroSummary');
    if (heroEl) {
        setTimeout(() => {
            heroEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 100);
    }
});
