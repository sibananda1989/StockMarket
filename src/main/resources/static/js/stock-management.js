let allStocks = [];
let allWatchlists = [];
let parsedCsvData = [];
let alerts = JSON.parse(localStorage.getItem('stockAlerts') || '[]');
let sortField = null;
let sortDir = 'asc';
let stockMgmtWatchlistCloseHandler = null;
let portfolios = [];
let currentPortfolioId = null;

function stockIdOf(s) {
    return s.stockId != null ? s.stockId : s.id;
}

function holdingIdOf(s) {
    return (currentPortfolioId && s.id != null) ? s.id : null;
}

function getPnlRowClass(pnl) {
    if (pnl == null) return '';
    if (pnl <= -100) return 'pnl-loss-100';
    if (pnl <= -50)  return 'pnl-loss-50';
    if (pnl <= -40)  return 'pnl-loss-40';
    if (pnl <= -30)  return 'pnl-loss-30';
    if (pnl <= -20)  return 'pnl-loss-20';
    if (pnl <= -10)  return 'pnl-loss-10';
    if (pnl < 10)    return 'pnl-neutral';
    if (pnl < 20)    return 'pnl-gain-10';
    if (pnl < 30)    return 'pnl-gain-20';
    if (pnl < 40)    return 'pnl-gain-30';
    if (pnl < 50)    return 'pnl-gain-40';
    if (pnl < 70)    return 'pnl-gain-50';
    if (pnl < 100)   return 'pnl-gain-70';
    if (pnl < 150)   return 'pnl-gain-100';
    if (pnl < 200)   return 'pnl-gain-150';
    return 'pnl-gain-200';
}

document.addEventListener('DOMContentLoaded', async () => {
    await loadPortfoliosMgmt();
    loadStocks();
    setupDropZone();
});

async function loadPortfoliosMgmt() {
    try {
        const res = await getPortfolios();
        portfolios = res.data || [];
        const sel = document.getElementById('portfolioSelector');
        sel.innerHTML = '<option value="">All Stocks</option>' +
            portfolios.map(p => `<option value="${p.id}">${escHtml(p.name)}${p.default ? ' (Default)' : ''}</option>`).join('');
        if (!currentPortfolioId && portfolios.length > 0) {
            const defaultPort = portfolios.find(p => p.default) || portfolios[0];
            currentPortfolioId = defaultPort.id;
            sel.value = currentPortfolioId;
            const badge = document.getElementById('portfolioContextBadge');
            if (badge) badge.textContent = defaultPort.name;
        }
    } catch (_) {}
}

async function switchPortfolioMgmt(id) {
    currentPortfolioId = id ? parseInt(id) : null;
    const badge = document.getElementById('portfolioContextBadge');
    if (badge) {
        if (currentPortfolioId) {
            const p = portfolios.find(x => x.id === currentPortfolioId);
            badge.textContent = p ? p.name : 'Portfolio';
        } else {
            badge.textContent = 'All Stocks';
        }
    }
    loadStocks();
}

// ─── Load & Display ───────────────────────────────────────────────────────────

async function loadStocks() {
    try {
        if (currentPortfolioId) {
            const res = await getPortfolio(currentPortfolioId);
            allStocks = (res.data && res.data.holdings) || [];
        } else {
            const response = await getAllStocks();
            allStocks = (response.data || []).filter(s => s.quantity != null && s.quantity > 0);
        }
        displayStocks(allStocks);
        updateSummaryCards(allStocks);
        populateAlertStockDropdown();
        checkAllAlerts();
    } catch (error) {
        document.getElementById('stocksTableBody').innerHTML =
            '<tr><td colspan="11" class="text-center text-danger">Error loading stocks</td></tr>';
    }
}

function displayStocks(stocks) {
    const tbody = document.getElementById('stocksTableBody');
    if (!stocks.length) {
        tbody.innerHTML = '<tr><td colspan="11" class="text-center py-3">No stocks found</td></tr>';
        return;
    }

    tbody.innerHTML = stocks.map(stock => {
        const pnlClass = getPnlRowClass(stock.pnlPercent);
        const rowAlert = getRowAlertClass(stock);
        const triggeredAlerts = getTriggeredAlertsForStock(stock);
        const alertBadge = triggeredAlerts.length
            ? `<span class="badge badge-${triggeredAlerts[0].severity} alert-badge alert-triggered" title="${triggeredAlerts.map(a => a.message).join(', ')}"><i class="fas fa-bell mr-1"></i>${triggeredAlerts.length}</span>`
            : '';
        const sId = stockIdOf(stock);
        const detailUrl = `stock-detail.html?id=${sId}`;

        return `<tr class="${pnlClass} ${rowAlert}">
            <td><a href="${detailUrl}" class="font-bold hover:text-blue-400 transition-colors">${escHtml(stock.symbol)}</a></td>
            <td><a href="${detailUrl}" class="hover:text-blue-400 transition-colors">${escHtml(stock.name)}</a></td>
            <td><span class="badge badge-neutral text-xs">${escHtml(stock.sector || '--')}</span></td>
            <td class="text-right">${stock.quantity ?? '--'}</td>
            <td class="text-right">${fmt(stock.avgPrice)}</td>
            <td class="text-right">${fmt(stock.lastTradedPrice)}</td>
            <td class="text-right">${fmt(stock.investment)}</td>
            <td class="text-right">${fmt(stock.currentValue)}</td>
            <td class="text-right">${fmt(stock.pnl)}</td>
            <td><span class="pnl-cell ${pnlClass}">${stock.pnlPercent != null ? stock.pnlPercent.toFixed(2) + '%' : '--'}</span></td>
            <td class="text-center">
                <div class="flex items-center justify-center gap-1">
                    ${alertBadge}
                    <button class="btn-action btn-watchlist" onclick="openWatchlistDropdown(event, ${sId})" title="Manage Watchlist">
                        <i class="fas fa-list"></i>
                    </button>
                    <button class="btn-action btn-view" onclick="window.location.href='${detailUrl}'" title="View Details">
                        <i class="fas fa-eye"></i>
                    </button>
                    <button class="btn-action btn-edit" onclick="openEditModal(${currentPortfolioId ? stock.id : sId})" title="Edit Stock">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn-action btn-delete" onclick="openDeleteModal(${sId}, '${escHtml(stock.symbol)}')" title="Delete Stock">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

async function openWatchlistDropdown(event, stockId) {
    if (event) event.stopPropagation();
    closeStockManagementWatchlistDropdown();

    const button = event?.currentTarget;
    const rect = button?.getBoundingClientRect();
    const menu = document.createElement('div');
    menu.id = 'stockMgmtWatchlistMenu';
    menu.className = 'fixed bg-gray-800 border border-gray-700 rounded-lg shadow-2xl z-50 w-64 p-2';
    if (rect) {
        menu.style.top = (rect.bottom + 4) + 'px';
        menu.style.left = rect.left + 'px';
    }

    const title = document.createElement('div');
    title.className = 'px-2 py-2 text-sm font-semibold text-white border-b border-gray-700';
    title.textContent = 'Manage Watchlists';
    menu.appendChild(title);

    const loading = document.createElement('div');
    loading.className = 'px-2 py-3 text-xs text-secondary';
    loading.textContent = 'Loading watchlists...';
    menu.appendChild(loading);

    document.body.appendChild(menu);

    try {
        const watchlistsRes = await getWatchlists();
        const watchlists = watchlistsRes.data || [];
        const membershipRes = await getWatchlistsForStock(stockId).catch(() => ({ data: [] }));
        const memberIds = new Set((membershipRes.data || []).map(w => w.id));

        menu.removeChild(loading);

        if (!watchlists.length) {
            const empty = document.createElement('div');
            empty.className = 'px-2 py-3 text-xs text-secondary';
            empty.innerHTML = 'No watchlists yet.<br><a href="watchlist.html" class="text-blue-400 hover:text-blue-300">Create one</a>';
            menu.appendChild(empty);
            return;
        }

        watchlists.forEach(watchlist => {
            const label = document.createElement('label');
            label.className = 'flex items-center gap-3 px-2 py-2 hover:bg-gray-700/60 rounded cursor-pointer text-sm';
            label.dataset.watchlistId = watchlist.id;

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = memberIds.has(watchlist.id);
            checkbox.className = 'mt-0.5';
            checkbox.onchange = async (changeEvent) => {
                checkbox.disabled = true;
                try {
                    if (changeEvent.target.checked) {
                        await addStockToWatchlist(watchlist.id, stockId);
                        showToast(`Added to "${watchlist.name}"`, 'success');
                    } else {
                        await removeStockFromWatchlist(watchlist.id, stockId);
                        showToast(`Removed from "${watchlist.name}"`, 'info');
                    }
                } catch (error) {
                    changeEvent.target.checked = !changeEvent.target.checked;
                    showToast(error.message || 'Failed to update watchlist', 'danger');
                } finally {
                    checkbox.disabled = false;
                }
            };

            const text = document.createElement('span');
            text.className = 'flex-1';
            text.textContent = watchlist.name;

            const count = document.createElement('span');
            count.className = 'text-xs text-secondary';
            count.textContent = watchlist.itemCount || 0;

            label.append(checkbox, text, count);
            menu.appendChild(label);
        });
    } catch (error) {
        loading.textContent = 'Failed to load watchlists';
        loading.classList.replace('text-secondary', 'text-red-400');
    }

    stockMgmtWatchlistCloseHandler = function closeHandler(clickEvent) {
        if (!menu.contains(clickEvent.target)) {
            menu.remove();
            document.removeEventListener('click', stockMgmtWatchlistCloseHandler);
            stockMgmtWatchlistCloseHandler = null;
        }
    };
    document.addEventListener('click', stockMgmtWatchlistCloseHandler);
}

function closeStockManagementWatchlistDropdown() {
    const menu = document.getElementById('stockMgmtWatchlistMenu');
    if (menu) menu.remove();
    if (stockMgmtWatchlistCloseHandler) {
        document.removeEventListener('click', stockMgmtWatchlistCloseHandler);
        stockMgmtWatchlistCloseHandler = null;
    }
}

function escHtml(value) {
    if (value == null) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function fmt(val) {
    if (val == null) return '--';
    return '₹' + Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function updateSummaryCards(stocks) {
    // Only show summary when at least one stock has quantity data
    if (!stocks.some(s => s.quantity != null && s.quantity > 0)) return;

    const totalInv = stocks.reduce((s, x) => s + (x.investment || 0), 0);
    const totalCurr = stocks.reduce((s, x) => s + (x.currentValue || 0), 0);
    const totalPnl = totalCurr - totalInv;

    document.getElementById('totalInvestment').textContent = fmt(totalInv);
    document.getElementById('totalCurrentValue').textContent = fmt(totalCurr);
    const pnlEl = document.getElementById('totalPnl');
    pnlEl.textContent = fmt(totalPnl);
    pnlEl.className = 'fw-bold fs-5 ' + (totalPnl >= 0 ? 'text-success' : 'text-danger');
    document.getElementById('activeAlertsCount').textContent = alerts.length;
}

function filterStocks() {
    const term = document.getElementById('searchInput').value.toLowerCase();
    const filtered = term
        ? allStocks.filter(s => s.symbol.toLowerCase().includes(term) || s.name.toLowerCase().includes(term))
        : allStocks;
    displayStocks(filtered);
}

function sortBy(field) {
    if (sortField === field) sortDir = sortDir === 'asc' ? 'desc' : 'asc';
    else { sortField = field; sortDir = 'asc'; }

    document.querySelectorAll('#stocksTable thead th').forEach(th => th.classList.remove('sort-asc', 'sort-desc'));
    const fields = ['symbol', 'name', null, null, null, null, null, null, 'pnl', 'pnlPercent'];
    const idx = fields.indexOf(field);
    if (idx >= 0) document.querySelectorAll('#stocksTable thead th')[idx].classList.add(sortDir === 'asc' ? 'sort-asc' : 'sort-desc');

    const sorted = [...allStocks].sort((a, b) => {
        const av = a[field] ?? (sortDir === 'asc' ? Infinity : -Infinity);
        const bv = b[field] ?? (sortDir === 'asc' ? Infinity : -Infinity);
        if (typeof av === 'string') return sortDir === 'asc' ? av.localeCompare(bv) : bv.localeCompare(av);
        return sortDir === 'asc' ? av - bv : bv - av;
    });
    displayStocks(sorted);
}

// ─── Add / Edit / Delete ──────────────────────────────────────────────────────

function openAddStockModal() {
    _selectedStockMgmt = null;
    document.getElementById('addStockForm').reset();
    document.getElementById('selectedStockFields').classList.add('hidden');
    document.getElementById('addStockSearch').value = '';
    document.getElementById('mgmtSearchResults').classList.add('hidden');

    // Show local stocks as initial suggestions
    displaySearchResultsMgmt(allStocks.slice(0, 20).map(s => ({
        symbol: s.symbol, name: s.name, exchange: '', sector: s.sector || '',
        industry: '', quoteType: 'EQUITY', isLocal: true
    })));

    // Load portfolios into the dropdown; pre-select current portfolio if set
    const sel = document.getElementById('addPortfolioId');
    sel.innerHTML = '<option value="">Select Portfolio</option>';
    getPortfolios().then(res => {
        (res.data || []).forEach(p => {
            sel.innerHTML += `<option value="${p.id}">${escHtml(p.name)}${p.default ? ' (Default)' : ''}</option>`;
        });
        if (currentPortfolioId) sel.value = currentPortfolioId;
    }).catch(() => {});
    openModal('addStockModal');
    document.getElementById('addStockSearch').focus();
}

async function addStock(event) {
    event.preventDefault();
    if (!_selectedStockMgmt) { showToast('Please search and select a stock', 'warning'); return; }
    const symbol = _selectedStockMgmt.symbol.toUpperCase().trim();
    const name = _selectedStockMgmt.name.trim();
    const sector = document.getElementById('mgmtSector').value;
    const portfolioId = document.getElementById('addPortfolioId').value;
    if (!sector) { showToast('Please select a sector', 'warning'); return; }
    if (!portfolioId) { showToast('Please select a portfolio', 'warning'); return; }
    try {
        const res = await addStockApi({ symbol, name, sector, yahooSymbol: symbol });
        const newId = res.data.id;
        const qty = numOrNull('addQty');
        const avgPrice = numOrNull('addAvgPrice');
        if (portfolioId && (qty !== null || avgPrice !== null)) {
            await addHolding(parseInt(portfolioId), newId, qty, avgPrice);
        }
        closeModal('addStockModal');
        showToast(`${name} added successfully`, 'success');
        loadStocks();
    } catch (e) { showToast('Error adding stock: ' + e.message, 'danger'); }
}

async function updateStock(event) {
    event.preventDefault();
    const editId = document.getElementById('editStockId').value;
    const symbol = document.getElementById('editStockSymbol').value.toUpperCase().trim();
    const name = document.getElementById('editStockName').value.trim();
    const sector = document.getElementById('editStockSector').value;
    const yahooSymbol = document.getElementById('editYahooSymbol').value.trim() || null;
    if (!symbol || !name || !sector) { showToast('Please fill in all required fields', 'warning'); return; }
    try {
        await updateStockApi(editId, { symbol, name, sector, yahooSymbol });

        const targetPid = document.getElementById('editPortfolioId')?.value;
        const oldHoldingId = document.getElementById('editHoldingId')?.value;
        const qty = numOrNull('editQty');
        const avgPrice = numOrNull('editAvgPrice');

        if (targetPid) {
            const pId = parseInt(targetPid);
            const stockId = parseInt(editId);

            if (oldHoldingId && currentPortfolioId && currentPortfolioId !== pId) {
                // Portfolio changed — remove from old first
                await removeHolding(currentPortfolioId, oldHoldingId).catch(() => {});
            }

            if (currentPortfolioId === pId && oldHoldingId) {
                // Same portfolio — update existing holding
                await updateHolding(pId, oldHoldingId, qty, avgPrice);
            } else if (qty !== null || avgPrice !== null) {
                // New portfolio — try add, fallback to find-and-update
                try {
                    await addHolding(pId, stockId, qty, avgPrice);
                } catch (_) {
                    // Holding may already exist; find its ID and update
                    try {
                        const pfRes = await getPortfolio(pId);
                        const existing = (pfRes.data?.holdings || []).find(h => stockIdOf(h) === stockId);
                        if (existing) {
                            await updateHolding(pId, existing.id, qty, avgPrice);
                        }
                    } catch (_) {}
                }
            }
        } else {
            // "Stock Only" — update stock entity directly
            await updatePortfolioApi(editId, { quantity: qty, avgPrice });
        }

        closeModal('editStockModal');
        showToast(`${name} updated successfully`, 'success');
        loadStocks();
    } catch (e) { showToast('Error updating stock: ' + e.message, 'danger'); }
}

function openEditModal(id) {
    const s = allStocks.find(x => x.id === id);
    if (!s) return;
    document.getElementById('editStockId').value = stockIdOf(s);
    document.getElementById('editStockSymbol').value = s.symbol;
    document.getElementById('editStockName').value = s.name;
    document.getElementById('editStockSector').value = s.sector || '';
    document.getElementById('editYahooSymbol').value = s.yahooSymbol || '';
    document.getElementById('editQty').value = s.quantity ?? '';
    document.getElementById('editAvgPrice').value = s.avgPrice ?? '';
    const ltp = s.lastTradedPrice;
    const ltpInput = document.getElementById('editLtp');
    ltpInput.value = ltp != null ? '₹' + Number(ltp).toLocaleString('en-IN', { minimumFractionDigits: 2 }) : '--';
    ltpInput.dataset.ltp = ltp != null ? ltp : '';
    document.getElementById('editInvestment').value = s.investment != null ? '₹' + Number(s.investment).toLocaleString('en-IN', { minimumFractionDigits: 2 }) : '--';
    document.getElementById('editCurrentValue').value = s.currentValue != null ? '₹' + Number(s.currentValue).toLocaleString('en-IN', { minimumFractionDigits: 2 }) : '--';
    const pnl = s.pnl;
    document.getElementById('editPnl').value = pnl != null ? '₹' + Number(pnl).toLocaleString('en-IN', { minimumFractionDigits: 2 }) : '--';
    document.getElementById('editPnlPercent').value = s.pnlPercent != null ? s.pnlPercent.toFixed(2) + '%' : '--';

    // Set holding ID
    if (document.getElementById('editHoldingId')) {
        document.getElementById('editHoldingId').value = currentPortfolioId ? (holdingIdOf(s) || '') : '';
    }

    // Populate portfolio dropdown and pre-select
    const editPortfolioSel = document.getElementById('editPortfolioId');
    if (editPortfolioSel) {
        editPortfolioSel.innerHTML = '<option value="">Stock Only (no portfolio)</option>' +
            portfolios.map(p => `<option value="${p.id}">${escHtml(p.name)}${p.default ? ' (Default)' : ''}</option>`).join('');
        // If editing from a portfolio context, pre-select that portfolio
        if (currentPortfolioId) {
            editPortfolioSel.value = currentPortfolioId;
        }
    }

    recalcEditPreview();
    openModal('editStockModal');
}

function recalcEditPreview() {
    const qty = parseFloat(document.getElementById('editQty').value);
    const avgPrice = parseFloat(document.getElementById('editAvgPrice').value);
    const rawLtp = document.getElementById('editLtp').dataset.ltp;
    const ltp = rawLtp ? parseFloat(rawLtp) : null;

    const hasQty = !isNaN(qty) && qty > 0;
    const hasAvgPrice = !isNaN(avgPrice) && avgPrice > 0;

    if (hasQty && hasAvgPrice) {
        const investment = qty * avgPrice;
        document.getElementById('editInvestment').value = '₹' + investment.toLocaleString('en-IN', { minimumFractionDigits: 2 });

        if (ltp != null && !isNaN(ltp) && ltp > 0) {
            const currentValue = qty * ltp;
            const pnl = currentValue - investment;
            const pnlPercent = investment > 0 ? (pnl / investment) * 100 : 0;
            document.getElementById('editCurrentValue').value = '₹' + currentValue.toLocaleString('en-IN', { minimumFractionDigits: 2 });
            document.getElementById('editPnl').value = '₹' + pnl.toLocaleString('en-IN', { minimumFractionDigits: 2 });
            document.getElementById('editPnlPercent').value = pnlPercent.toFixed(2) + '%';
        } else {
            document.getElementById('editCurrentValue').value = '--';
            document.getElementById('editPnl').value = '--';
            document.getElementById('editPnlPercent').value = '--';
        }
    } else {
        document.getElementById('editInvestment').value = '--';
        document.getElementById('editCurrentValue').value = '--';
        document.getElementById('editPnl').value = '--';
        document.getElementById('editPnlPercent').value = '--';
    }
}

function numOrNull(id) {
    const v = document.getElementById(id).value;
    return v !== '' ? parseFloat(v) : null;
}

function openDeleteModal(id, symbol) {
    document.getElementById('deleteStockId').value = id;
    document.getElementById('deleteStockSymbol').textContent = symbol || '';
    const ctxMsg = document.getElementById('deleteContextMsg');
    if (ctxMsg) {
        if (currentPortfolioId) {
            const p = portfolios.find(x => x.id === currentPortfolioId);
            ctxMsg.textContent = `This will remove ${symbol} from "${p ? p.name : 'portfolio'}". The stock record is kept.`;
        } else {
            ctxMsg.textContent = `This will permanently delete ${symbol} from the database, including all portfolio holdings.`;
        }
    }
    openModal('deleteModal');
}

async function confirmDelete() {
    const id = document.getElementById('deleteStockId').value;
    try {
        if (currentPortfolioId) {
            await removeHoldingByStock(currentPortfolioId, parseInt(id));
            showToast('Holding removed from portfolio', 'success');
        } else {
            await deleteStockApi(id);
            showToast('Stock deleted', 'success');
        }
        closeModal('deleteModal');
        loadStocks();
    } catch (e) { showToast('Error: ' + e.message, 'danger'); }
}

// ─── CSV Upload ───────────────────────────────────────────────────────────────

function openCsvModal() {
    parsedCsvData = [];
    document.getElementById('csvPreviewSection').style.display = 'none';
    document.getElementById('csvImportResult').style.display = 'none';
    document.getElementById('importBtn').style.display = 'none';
    document.getElementById('csvFileInput').value = '';
    const today = new Date().toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    document.getElementById('uploadDateLabel').textContent = today;
    const portfolioNote = document.getElementById('csvPortfolioNote');
    if (portfolioNote) {
        portfolioNote.classList.toggle('hidden', !currentPortfolioId);
    }
    openModal('csvModal');
}

function setupDropZone() {
    const zone = document.getElementById('dropZone');
    zone.addEventListener('dragover', e => { e.preventDefault(); zone.classList.add('dragover'); });
    zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
    zone.addEventListener('drop', e => {
        e.preventDefault();
        zone.classList.remove('dragover');
        const file = e.dataTransfer.files[0];
        if (file) handleCsvFile(file);
    });
}

function handleCsvFile(file) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = e => {
        const text = e.target.result;
        parsedCsvData = parseCsv(text);
        renderCsvPreview(parsedCsvData);
    };
    reader.readAsText(file);
}

function parseCsv(text) {
    const lines = text.trim().split('\n').filter(l => l.trim());
    if (lines.length < 2) return [];

    // Detect separator: if first line has tabs, use tab; else comma
    const sep = lines[0].includes('\t') ? '\t' : ',';
    const headers = splitLine(lines[0], sep).map(h => h.toLowerCase());

    const colMap = {
        name:             findCol(headers, ['name']),
        quantity:         findCol(headers, ['quantity', 'qty']),
        avgPrice:         findCol(headers, ['avg price', 'avg cost', 'avgprice', 'average price']),
        lastTradedPrice:  findCol(headers, ['last traded', 'ltp', 'last traded price', 'current price']),
        investment:       findCol(headers, ['investment']),
        currentValue:     findCol(headers, ['current value', 'curr value']),
        pnl:              findCol(headers, ['p&l', 'pnl', 'profit & loss', 'profit']),
        pnlPercent:       findCol(headers, ['p&l %', 'p&l%', 'pnl%', 'return %', 'returns %'])
    };

    return lines.slice(1).map(line => {
        const cols = splitLine(line, sep);
        const name = cols[colMap.name] || '';
        if (!name) return null;
        return {
            name,
            symbol:           nameToSymbol(name),
            quantity:         parseNum(cols[colMap.quantity]),
            avgPrice:         parseNum(cols[colMap.avgPrice]),
            lastTradedPrice:  parseNum(cols[colMap.lastTradedPrice]),
            investment:       parseNum(cols[colMap.investment]),
            currentValue:     parseNum(cols[colMap.currentValue]),
            pnl:              parseNum(cols[colMap.pnl]),
            pnlPercent:       parseNum(cols[colMap.pnlPercent])
        };
    }).filter(Boolean);
}

/**
 * Splits a CSV/TSV line respecting quoted fields.
 * For comma-separated files, values like 4,849.65 must be quoted: "4,849.65"
 * Tab-separated files never have this ambiguity.
 */
function splitLine(line, sep) {
    if (sep === '\t') {
        return line.split('\t').map(c => c.trim().replace(/^"|"$/g, ''));
    }
    // Comma-separated: parse char by char to handle quoted fields
    const result = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
        const ch = line[i];
        if (ch === '"') {
            inQuotes = !inQuotes;
        } else if (ch === ',' && !inQuotes) {
            result.push(current.trim());
            current = '';
        } else {
            current += ch;
        }
    }
    result.push(current.trim());
    return result;
}

function findCol(headers, candidates) {
    for (const c of candidates) {
        const idx = headers.findIndex(h => h.includes(c));
        if (idx !== -1) return idx;
    }
    return -1;
}

function parseNum(val) {
    if (val == null || val === '' || val === '--') return null;
    // Remove currency symbols, spaces, % sign
    // Handle negative values in parentheses e.g. (1,234.56)
    let cleaned = String(val).trim()
        .replace(/₹/g, '')
        .replace(/\s/g, '')
        .replace(/%/g, '')
        .replace(/^\((.+)\)$/, '-$1')  // (1234) → -1234
        .replace(/,/g, '');            // remove ALL commas (Indian or Western format)
    const n = parseFloat(cleaned);
    return isNaN(n) ? null : n;
}

function nameToSymbol(name) {
    // Generate a symbol: uppercase first letters of each word, max 10 chars
    return name.toUpperCase().replace(/[^A-Z0-9]/g, '').substring(0, 10) ||
           name.toUpperCase().replace(/\s+/g, '').substring(0, 10);
}

function renderCsvPreview(data) {
    document.getElementById('csvPreviewSection').style.display = 'block';
    document.getElementById('csvRowCount').textContent = data.length + ' rows';
    const tbody = document.getElementById('csvPreviewBody');
    tbody.innerHTML = data.map(r => `
        <tr>
            <td>${r.name}</td>
            <td class="text-end">${r.quantity ?? '--'}</td>
            <td class="text-end">${r.avgPrice ?? '--'}</td>
            <td class="text-end">${r.lastTradedPrice ?? '--'}</td>
            <td class="text-end">${r.investment ?? '--'}</td>
            <td class="text-end">${r.currentValue ?? '--'}</td>
            <td class="text-end ${r.pnl < 0 ? 'text-danger' : 'text-success'}">${r.pnl ?? '--'}</td>
            <td class="text-end ${r.pnlPercent < 0 ? 'text-danger' : 'text-success'}">${r.pnlPercent != null ? r.pnlPercent + '%' : '--'}</td>
        </tr>`).join('');
    document.getElementById('importBtn').style.display = 'inline-block';
}

async function importCsv() {
    if (!parsedCsvData.length) return;
    const btn = document.getElementById('importBtn');
    btn.disabled = true;
    btn.textContent = 'Importing...';
    try {
        const payload = parsedCsvData.map(r => ({
            symbol: r.symbol,
            name: r.name,
            sector: 'Other',
            quantity: r.quantity,
            avgPrice: r.avgPrice,
            lastTradedPrice: r.lastTradedPrice,
            investment: r.investment,
            currentValue: r.currentValue,
            pnl: r.pnl,
            pnlPercent: r.pnlPercent
        }));
        const res = await csvImportApi(payload);
        const resultDiv = document.getElementById('csvImportResult');
        resultDiv.style.display = 'block';

        // If a portfolio is selected, upsert each imported stock as a holding
        if (currentPortfolioId) {
            const allStocksRes = await getAllStocks();
            const allExisting = allStocksRes.data || [];
            let holdingCount = 0;
            let updateCount = 0;
            for (const r of parsedCsvData) {
                const match = allExisting.find(s => s.symbol.toUpperCase() === (r.symbol || '').toUpperCase());
                if (match && (r.quantity != null || r.avgPrice != null)) {
                    try {
                        const existing = await getPortfolioHoldingByStock(currentPortfolioId, match.id);
                        await updateHolding(currentPortfolioId, existing.data.id, r.quantity || 0, r.avgPrice || 0);
                        updateCount++;
                    } catch (_) {
                        try {
                            await addHolding(currentPortfolioId, match.id, r.quantity || 0, r.avgPrice || 0);
                            holdingCount++;
                        } catch (__) {}
                    }
                }
            }
            resultDiv.innerHTML = `<div class="alert alert-success mb-0">✅ ${res.message} (${holdingCount} added, ${updateCount} updated in portfolio)</div>`;
        } else {
            resultDiv.innerHTML = `<div class="alert alert-success mb-0">✅ ${res.message}</div>`;
        }

        document.getElementById('csvPreviewSection').style.display = 'none';
        btn.style.display = 'none';
        loadStocks();
    } catch (e) {
        document.getElementById('csvImportResult').style.display = 'block';
        document.getElementById('csvImportResult').innerHTML = `<div class="alert alert-danger mb-0">❌ Import failed: ${e.message}</div>`;
    } finally {
        btn.disabled = false;
        btn.textContent = 'Import';
    }
}

function downloadSampleCsv() {
    const sample = `Name\tQuantity\tAvg Price\tLast Traded\tInvestment\tCurrent Value\tP&L\tP&L%
HDFC Bank\t6\t826.50\t780.85\t4959.00\t4685.10\t-273.90\t-5.52%
Infosys\t6\t1256.33\t1179.20\t7538.00\t7075.20\t-462.80\t-6.14%
Wipro\t21\t242.65\t197.91\t5095.65\t4156.11\t-939.54\t-18.44%`;
    const blob = new Blob([sample], { type: 'text/tab-separated-values' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'sample_portfolio.csv';
    a.click();
}

// ─── Alert System ─────────────────────────────────────────────────────────────

function openAlertModal() {
    populateAlertStockDropdown();
    renderAlertList();
    openModal('alertModal');
}

function populateAlertStockDropdown() {
    const sel = document.getElementById('alertStock');
    if (!sel) return;
    const existing = sel.value;
    sel.innerHTML = '<option value="">-- All Stocks (Global Alert) --</option>' +
        allStocks.map(s => `<option value="${stockIdOf(s)}">${s.symbol} - ${s.name}</option>`).join('');
    sel.value = existing;
}

function addAlert() {
    const stockId = document.getElementById('alertStock').value;
    const param = document.getElementById('alertParam').value;
    const condition = document.getElementById('alertCondition').value;
    const value = parseFloat(document.getElementById('alertValue').value);
    const severity = document.getElementById('alertSeverity').value;
    const message = document.getElementById('alertMessage').value.trim();

    if (isNaN(value)) { showToast('Please enter a valid alert value', 'warning'); return; }

    const stockName = stockId
        ? allStocks.find(s => stockIdOf(s) == stockId)?.name || 'Unknown'
        : 'All Stocks';

    alerts.push({
        id: Date.now(),
        stockId: stockId || null,
        stockName,
        param,
        condition,
        value,
        severity,
        message: message || `${stockName}: ${param} ${condition} ${value}`
    });

    saveAlerts();
    renderAlertList();
    document.getElementById('alertValue').value = '';
    document.getElementById('alertMessage').value = '';
    showToast('Alert added', 'success');
    checkAllAlerts();
}

function removeAlert(id) {
    alerts = alerts.filter(a => a.id !== id);
    saveAlerts();
    renderAlertList();
    displayStocks(allStocks);
    document.getElementById('activeAlertsCount').textContent = alerts.length;
}

function saveAlerts() {
    localStorage.setItem('stockAlerts', JSON.stringify(alerts));
}

function renderAlertList() {
    const container = document.getElementById('alertList');
    if (!alerts.length) {
        container.innerHTML = '<p class="text-secondary text-sm">No alerts configured.</p>';
        return;
    }
    const condLabels = { lt: '<', lte: '≤', gt: '>', gte: '≥', eq: '=' };
    container.innerHTML = alerts.map(a => `
        <div class="flex justify-between items-center border border-gray-700 rounded-lg p-3 mb-2 card">
            <div>
                <span class="badge badge-${a.severity} mr-2">${a.severity.toUpperCase()}</span>
                <strong>${a.stockName}</strong>
                <span class="text-secondary ml-2">${a.param} ${condLabels[a.condition]} ${a.value}</span>
                ${a.message ? `<div class="text-secondary text-sm mt-1">${a.message}</div>` : ''}
            </div>
            <button class="btn-action btn-delete" onclick="removeAlert(${a.id})" title="Remove Alert">
                <i class="fas fa-times"></i>
            </button>
        </div>`).join('');
}

function checkAllAlerts() {
    if (!alerts.length || !allStocks.length) return;
    const triggered = [];
    for (const alert of alerts) {
        const stocks = alert.stockId
            ? allStocks.filter(s => stockIdOf(s) == alert.stockId)
            : allStocks;
        for (const stock of stocks) {
            const val = stock[alert.param];
            if (val == null) continue;
            if (evaluateCondition(val, alert.condition, alert.value)) {
                triggered.push({ ...alert, triggeredStock: stock });
            }
        }
    }
    triggered.forEach(t => {
        showToast(`🔔 ${t.message || t.triggeredStock.symbol + ': ' + t.param + ' = ' + t.triggeredStock[t.param]}`, t.severity, 8000);
    });
    document.getElementById('activeAlertsCount').textContent = alerts.length;
    displayStocks(allStocks); // re-render to show alert badges
}

function evaluateCondition(actual, condition, threshold) {
    switch (condition) {
        case 'lt':  return actual < threshold;
        case 'lte': return actual <= threshold;
        case 'gt':  return actual > threshold;
        case 'gte': return actual >= threshold;
        case 'eq':  return actual === threshold;
        default:    return false;
    }
}

function getTriggeredAlertsForStock(stock) {
    return alerts.filter(a => {
        if (a.stockId && a.stockId != stockIdOf(stock)) return false;
        const val = stock[a.param];
        if (val == null) return false;
        return evaluateCondition(val, a.condition, a.value);
    });
}

function getRowAlertClass(stock) {
    const triggered = getTriggeredAlertsForStock(stock);
    if (!triggered.length) return '';
    if (triggered.some(a => a.severity === 'danger')) return 'alert-row-danger';
    return 'alert-row';
}

// ─── Stock Search Autocomplete (watchlist-style) ──────────────────────────────

let _selectedStockMgmt = null;
let searchTimeoutMgmt = null;

function clearSelectedStockMgmt() {
    _selectedStockMgmt = null;
    document.getElementById('selectedStockFields').classList.add('hidden');
    document.getElementById('addStockSearch').value = '';
    document.getElementById('addStockSearch').focus();
}

async function debounceSearchStocksMgmt(query, delay) {
    if (searchTimeoutMgmt !== null) clearTimeout(searchTimeoutMgmt);
    searchTimeoutMgmt = setTimeout(() => searchStocksMgmt(query), delay);
}

async function searchStocksMgmt(query) {
    const trimmed = query.trim();

    // Show local stocks as suggestions when < 3 chars
    if (trimmed.length < 3) {
        const local = allStocks.filter(s =>
            s.symbol.toLowerCase().includes(trimmed.toLowerCase()) ||
            (s.name || '').toLowerCase().includes(trimmed.toLowerCase())
        ).slice(0, 10).map(s => ({
            symbol: s.symbol, name: s.name, exchange: '', sector: s.sector || '',
            industry: '', quoteType: 'EQUITY', isLocal: true
        }));
        displaySearchResultsMgmt(local);
        return;
    }

    // Search local + Yahoo in parallel
    const local = allStocks.filter(s =>
        s.symbol.toLowerCase().includes(trimmed.toLowerCase()) ||
        (s.name || '').toLowerCase().includes(trimmed.toLowerCase())
    ).map(s => ({
        symbol: s.symbol, name: s.name, exchange: '', sector: s.sector || '',
        industry: '', quoteType: 'EQUITY', isLocal: true
    }));

    let yahoo = [];
    try {
        const res = await searchStocksByName(trimmed, 8);
        yahoo = (res.data || []).map(r => ({ ...r, isYahoo: true }));
    } catch (_) {}

    const seen = new Set(local.map(s => s.symbol.toUpperCase()));
    const merged = [...local, ...yahoo.filter(r => !seen.has(r.symbol.toUpperCase()))];
    displaySearchResultsMgmt(merged.slice(0, 12));
}

function displaySearchResultsMgmt(results) {
    const container = document.getElementById('mgmtSearchResults');
    if (!results.length) {
        container.innerHTML = '<div class="px-4 py-3 text-sm text-secondary text-center">No stocks found</div>';
        container.classList.remove('hidden');
        return;
    }

    container.innerHTML = results.map(stock => {
        const json = JSON.stringify(stock).replace(/'/g, "\\'").replace(/"/g, '&quot;');
        const localBadge = stock.isLocal ? '<span class="text-xs bg-blue-600/20 text-blue-400 px-1.5 py-0.5 rounded ml-2">Local</span>' : '';
        const exchBadge = stock.exchange ? `<span class="text-xs bg-gray-600 text-gray-300 px-1.5 py-0.5 rounded">${stock.exchange}</span>` : '';
        const typeBadge = stock.quoteType && stock.quoteType !== 'EQUITY' ? `<span class="text-xs bg-purple-600/20 text-purple-400 px-1.5 py-0.5 rounded ml-1">${stock.quoteType}</span>` : '';
        const detail = [stock.sector, stock.industry].filter(Boolean).join(' • ');
        return `
        <div class="px-4 py-3 hover:bg-gray-700/50 cursor-pointer border-b border-gray-700 last:border-0 transition-colors"
             onclick='selectStockFromSearchMgmt(${json})'>
            <div class="flex items-center justify-between">
                <div class="flex items-center">
                    <span class="font-bold text-white">${stock.symbol}</span>
                    ${localBadge}
                </div>
                <div class="flex items-center gap-1">${exchBadge}${typeBadge}</div>
            </div>
            <div class="text-sm text-gray-400 mt-0.5">${stock.name}</div>
            ${detail ? `<div class="text-xs text-gray-500 mt-1">${detail}</div>` : ''}
        </div>`;
    }).join('');

    container.classList.remove('hidden');
}

function hideSearchResultsMgmt() {
    const el = document.getElementById('mgmtSearchResults');
    if (el) { el.classList.add('hidden'); el.innerHTML = ''; }
}

function selectStockFromSearchMgmt(stock) {
    _selectedStockMgmt = stock;

    document.getElementById('selectedStockFields').classList.remove('hidden');
    document.getElementById('mgmtSymbol').value = stock.symbol || '';
    document.getElementById('mgmtName').value = stock.name || '';
    document.getElementById('mgmtExchange').value = stock.exchange || '';

    // Auto-detect sector
    let sector = stock.sector;
    if (!sector) {
        const local = allStocks.find(s => s.symbol.toUpperCase() === (stock.symbol || '').toUpperCase());
        if (local && local.sector) sector = local.sector;
    }

    const sectorSelect = document.getElementById('mgmtSector');
    sectorSelect.value = '';
    if (sector) {
        const yahooSector = sector.toLowerCase();
        for (let i = 0; i < sectorSelect.options.length; i++) {
            if (sectorSelect.options[i].value.toLowerCase() === yahooSector) {
                sectorSelect.value = sectorSelect.options[i].value;
                break;
            }
        }
        if (!sectorSelect.value) {
            const sectorMap = [
                { keywords: ['technology', 'tech', 'software', 'electronic', 'computer', 'internet', 'digital', 'semiconductor', 'information'], match: 'Technology' },
                { keywords: ['health', 'pharma', 'medical', 'biotech', 'drug'], match: 'Healthcare' },
                { keywords: ['energy', 'oil', 'gas', 'petroleum', 'coal', 'nuclear'], match: 'Energy' },
                { keywords: ['finance', 'bank', 'financial', 'insurance', 'credit', 'investment'], match: 'Finance' },
                { keywords: ['consumer discretionary', 'retail', 'auto', 'media', 'entertainment', 'leisure', 'apparel'], match: 'Consumer Discretionary' },
                { keywords: ['consumer staples', 'food', 'beverage', 'household', 'grocery', 'personal'], match: 'Consumer Staples' },
                { keywords: ['industrial', 'manufacturing', 'aerospace', 'defense', 'transport', 'logistics', 'machinery', 'construction'], match: 'Industrial' },
                { keywords: ['utility', 'utilities', 'electric', 'water', 'gas utility'], match: 'Utilities' },
                { keywords: ['real estate', 'property', 'reit'], match: 'Real Estate' },
                { keywords: ['material', 'mining', 'chemical', 'steel', 'cement', 'forest'], match: 'Materials' },
            ];
            for (const mapping of sectorMap) {
                if (mapping.keywords.some(kw => yahooSector.includes(kw))) {
                    sectorSelect.value = mapping.match;
                    break;
                }
            }
        }
        if (!sectorSelect.value && sector) {
            var opt = document.createElement('option');
            opt.value = sector;
            opt.textContent = sector;
            sectorSelect.appendChild(opt);
            sectorSelect.value = sector;
        }
    }

    hideSearchResultsMgmt();
    document.getElementById('addStockSearch').value = stock.name + ' (' + stock.symbol + ')';
    document.getElementById('addQty').focus();
}

// ─── Toast Notifications ──────────────────────────────────────────────────────

function showToast(message, type = 'info', duration = 4000) {
    const container = document.getElementById('alertToastContainer');
    const id = 'toast_' + Date.now();
    const bgMap = { 
        success: 'bg-green-600', 
        danger: 'bg-red-600', 
        warning: 'bg-yellow-600 text-black', 
        info: 'bg-blue-600' 
    };
    const iconMap = {
        success: 'fa-check-circle',
        danger: 'fa-exclamation-circle',
        warning: 'fa-exclamation-triangle',
        info: 'fa-info-circle'
    };
    const bg = bgMap[type] || 'bg-gray-600';
    const icon = iconMap[type] || 'fa-info-circle';
    const html = `
        <div id="${id}" class="toast align-items-center text-white ${bg} border-0 mb-2 px-4 py-3 rounded-lg shadow-lg flex items-center gap-3" role="alert">
            <i class="fas ${icon}"></i>
            <div class="flex-1">${message}</div>
            <button type="button" onclick="document.getElementById('${id}').remove()" class="text-white hover:text-gray-200">
                <i class="fas fa-times"></i>
            </button>
        </div>`;
    container.insertAdjacentHTML('beforeend', html);
    setTimeout(() => {
        const toastEl = document.getElementById(id);
        if (toastEl) toastEl.remove();
    }, duration);
}
