/**
 * PortfolioTransactions — lot-based buy/sell matching
 * Each BUY record is an individual lot: sell from it (partial or full),
 * expand a lot to see its linked sell records. When sold == qty the lot is SOLD.
 */
const PortfolioTransactions = (() => {
  // ─── STATE ───
  const state = {
    portfolios: [],
    priceMap: {},
    allLots: [],             // BuyLotDTO[]
    filteredLots: [],
    currentPage: 1,
    pageSize: 50,
    sort: { column: 'transactionDate', direction: 'desc' },
    expandedLotId: null,
    filters: {
      portfolioId: 'all',
      stockSearch: '',
      dateFrom: null,
      dateTo: null,
      status: 'all'
    }
  };

  let debounceTimer = null;

  // ─── DOM CACHE ───
  const els = {};

  function cacheElements() {
    els.tableWrapper = document.getElementById('table-wrapper');
    els.lotRows = document.getElementById('lot-rows');
    els.paginationInfo = document.getElementById('pagination-info');
    els.pageNumbers = document.getElementById('page-numbers');
    els.btnPrev = document.getElementById('btn-prev');
    els.btnNext = document.getElementById('btn-next');
    els.tableCount = document.getElementById('table-count');
    els.statTotal = document.getElementById('stat-total');
    els.statInvested = document.getElementById('stat-invested');
    els.statPl = document.getElementById('stat-pl');
    els.statPlPct = document.getElementById('stat-pl-pct');
    els.statProfitCount = document.getElementById('stat-profit-count');
    els.statLossCount = document.getElementById('stat-loss-count');
    els.statUnique = document.getElementById('stat-unique');
    els.cardPl = document.getElementById('card-pl');
    els.filterPortfolio = document.getElementById('filter-portfolio');
    els.filterStock = document.getElementById('filter-stock');
    els.filterDateFrom = document.getElementById('filter-date-from');
    els.filterDateTo = document.getElementById('filter-date-to');
    els.filterStatus = document.getElementById('filter-status');
  }

  // ─── INIT ───
  async function init() {
    cacheElements();
    bindEvents();
    await loadAll();
  }

  function bindEvents() {
    if (els.filterPortfolio) els.filterPortfolio.addEventListener('change', (e) => {
      state.filters.portfolioId = e.target.value;
      applyFiltersAndRender();
    });
    if (els.filterStock) els.filterStock.addEventListener('input', (e) => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        state.filters.stockSearch = e.target.value.trim().toLowerCase();
        applyFiltersAndRender();
      }, 300);
    });
    if (els.filterDateFrom) els.filterDateFrom.addEventListener('change', (e) => {
      state.filters.dateFrom = e.target.value || null;
      applyFiltersAndRender();
    });
    if (els.filterDateTo) els.filterDateTo.addEventListener('change', (e) => {
      state.filters.dateTo = e.target.value || null;
      applyFiltersAndRender();
    });
    if (els.filterStatus) els.filterStatus.addEventListener('change', (e) => {
      state.filters.status = e.target.value;
      applyFiltersAndRender();
    });
  }

  // ─── DATA LOADING ───
  async function loadAll() {
    showLoading(true);
    try {
      const portfoliosRes = await getPortfolios().catch(e => { console.warn('getPortfolios failed', e); return { data: [] }; });
      const stocksRes = await getAllStocks().catch(e => { console.warn('getAllStocks failed', e); return { data: [] }; });

      let portfolios = [];
      if (Array.isArray(portfoliosRes)) portfolios = portfoliosRes;
      else if (portfoliosRes && Array.isArray(portfoliosRes.data)) portfolios = portfoliosRes.data;
      else if (portfoliosRes && Array.isArray(portfoliosRes.data?.data)) portfolios = portfoliosRes.data.data;
      state.portfolios = portfolios;

      let stocks = [];
      if (Array.isArray(stocksRes)) stocks = stocksRes;
      else if (stocksRes && Array.isArray(stocksRes.data)) stocks = stocksRes.data;
      stocks.forEach(s => {
        const id = s.id != null ? s.id : s.stockId;
        const price = s.lastTradedPrice != null ? Number(s.lastTradedPrice) : (s.currentPrice != null ? Number(s.currentPrice) : 0);
        if (id != null) state.priceMap[id] = price;
      });

      renderPortfolioFilter(portfolios);

      if (!portfolios.length) {
        state.allLots = [];
        showEmpty('No portfolios found. Create a portfolio and add transactions.');
        updateSummary([]);
        showLoading(false);
        return;
      }

      // Fetch buy lots for ALL portfolios in parallel
      const lotPromises = portfolios.map(p =>
        getBuyLots(p.id)
          .then(res => {
            let lots = [];
            if (Array.isArray(res)) lots = res;
            else if (res && Array.isArray(res.data)) lots = res.data;
            else if (res && res.data && Array.isArray(res.data.data)) lots = res.data.data;
            return lots.map(l => ({
              ...l,
              portfolioId: l.portfolioId != null ? l.portfolioId : p.id,
              portfolioName: p.name || ('Portfolio #' + p.id)
            }));
          })
          .catch(e => {
            console.warn('Failed to load lots for portfolio', p.id, e);
            return [];
          })
      );

      const nested = await Promise.all(lotPromises);
      state.allLots = nested.flat();
      // Enrich with market context: current price + running P/L on open quantity
      state.allLots.forEach(l => {
        const ltp = state.priceMap[Number(l.stockId)] || 0;
        const bp = Number(l.price);
        l.currentPrice = ltp;
        l.openPl = ltp > 0 ? (ltp - bp) * Number(l.remainingQuantity) : null;
        l.openPlPct = (l.openPl != null && l.remainingQuantity > 0 && bp > 0)
          ? (l.openPl / (bp * Number(l.remainingQuantity))) * 100 : null;
        const soldCost = bp * Number(l.soldQuantity);
        l.realizedPlPct = (l.soldQuantity > 0 && soldCost > 0)
          ? (Number(l.realizedPnl || 0) / soldCost) * 100 : null;
      });
      applyFiltersAndRender();
    } catch (err) {
      console.error('loadAll error', err);
      showToast(err.message || 'Failed to load buy records', 'error');
      showEmpty('Failed to load buy records. Please retry.');
    } finally {
      showLoading(false);
    }
  }

  function renderPortfolioFilter(portfolios) {
    if (!els.filterPortfolio) return;
    const existing = els.filterPortfolio.querySelectorAll('option');
    for (let i = existing.length - 1; i >= 1; i--) existing[i].remove();
    portfolios.forEach(p => {
      const opt = document.createElement('option');
      opt.value = String(p.id);
      opt.textContent = p.name + (p.default ? ' (Default)' : '');
      els.filterPortfolio.appendChild(opt);
    });
    els.filterPortfolio.value = state.filters.portfolioId;
  }

  // ─── FILTERING & SORTING ───
  function applyFiltersAndRender() {
    let filtered = [...state.allLots];

    if (state.filters.portfolioId !== 'all') {
      const pid = String(state.filters.portfolioId);
      filtered = filtered.filter(l => String(l.portfolioId) === pid);
    }
    if (state.filters.stockSearch) {
      const q = state.filters.stockSearch;
      filtered = filtered.filter(l =>
        (l.stockSymbol && l.stockSymbol.toLowerCase().includes(q)) ||
        (l.stockName && l.stockName.toLowerCase().includes(q))
      );
    }
    if (state.filters.dateFrom) {
      filtered = filtered.filter(l => l.transactionDate && l.transactionDate.slice(0,10) >= state.filters.dateFrom);
    }
    if (state.filters.dateTo) {
      filtered = filtered.filter(l => l.transactionDate && l.transactionDate.slice(0,10) <= state.filters.dateTo);
    }
    if (state.filters.status !== 'all') {
      filtered = filtered.filter(l => l.status === state.filters.status);
    }

    const col = state.sort.column;
    const dir = state.sort.direction === 'asc' ? 1 : -1;
    filtered.sort((a, b) => {
      let av = a[col];
      let bv = b[col];
      if (col === 'transactionDate') {
        av = av ? new Date(av).getTime() : 0;
        bv = bv ? new Date(bv).getTime() : 0;
      }
      if (av == null && bv == null) return 0;
      if (av == null) return dir === 1 ? 1 : -1;
      if (bv == null) return dir === 1 ? -1 : 1;
      const NUM_COLS = new Set(['quantity', 'price', 'soldQuantity', 'remainingQuantity', 'realizedPnl', 'currentPrice', 'openPl']);
      if (NUM_COLS.has(col)) return dir * (Number(av) - Number(bv));
      if (typeof av === 'string') return dir * av.localeCompare(bv, undefined, { sensitivity: 'base' });
      return dir * (Number(av) - Number(bv));
    });

    state.filteredLots = filtered;
    state.currentPage = 1;
    updateSortIcons();
    updateSummary(filtered);
    renderTable();
    renderPagination();
    updateTableCount();
  }

  function sortBy(column) {
    if (state.sort.column === column) {
      state.sort.direction = state.sort.direction === 'asc' ? 'desc' : 'asc';
    } else {
      state.sort.column = column;
      state.sort.direction = (column === 'stockSymbol' || column === 'status') ? 'asc' : 'desc';
    }
    applyFiltersAndRender();
  }

  function updateSortIcons() {
    document.querySelectorAll('.sort-icon').forEach(el => {
      el.className = 'fas fa-sort sort-icon';
    });
    const active = document.getElementById('sort-' + state.sort.column);
    if (active) {
      active.className = state.sort.direction === 'asc'
        ? 'fas fa-sort-up sort-icon active'
        : 'fas fa-sort-down sort-icon active';
    }
  }

  // ─── RENDERING ───
  function statusBadge(status) {
    if (status === 'SOLD') return '<span class="badge px-2 py-0.5 rounded-full text-xs font-bold bg-green-500/15 text-green-400 border border-green-500/30">SOLD</span>';
    if (status === 'PARTIALLY_SOLD') return '<span class="badge px-2 py-0.5 rounded-full text-xs font-bold bg-yellow-500/15 text-yellow-400 border border-yellow-500/30">PARTIAL</span>';
    return '<span class="badge px-2 py-0.5 rounded-full text-xs font-bold bg-blue-500/15 text-blue-400 border border-blue-500/30">OPEN</span>';
  }

  function renderTable() {
    if (!els.lotRows) return;
    const start = (state.currentPage - 1) * state.pageSize;
    const pageData = state.filteredLots.slice(start, start + state.pageSize);

    if (!state.filteredLots.length) {
      els.lotRows.innerHTML = '';
      showEmptyState(true);
      return;
    }
    showEmptyState(false);

    els.lotRows.innerHTML = pageData.map(l => {
      const isExpanded = state.expandedLotId === l.id;
      const plClass = l.realizedPnl > 0.005 ? 'pl-profit' : l.realizedPnl < -0.005 ? 'pl-loss' : 'pl-neutral';
      const openPlClass = l.openPl > 0.005 ? 'pl-profit' : l.openPl < -0.005 ? 'pl-loss' : 'pl-neutral';
      const canSell = l.remainingQuantity > 0;
      const hasCurrent = (l.currentPrice || 0) > 0;
      const currentStr = hasCurrent ? fmtPrice(l.currentPrice) : '<span class="text-gray-500">—</span>';
      const sells = l.sells || [];
      const openPlCell = !canSell ? '<span class="text-gray-500">—</span>'
        : !hasCurrent ? '—'
        : `<div class="flex flex-col items-end leading-tight text-right">
             <span class="${openPlClass} font-semibold">${fmtPct(l.openPlPct)}</span>
             <span class="text-xs ${openPlClass} opacity-80">${fmtPriceWithSign(l.openPl)}</span>
           </div>`;
      const realizedCell = !sells.length ? '—'
        : `<div class="flex flex-col items-end leading-tight text-right">
             <span class="${plClass} font-semibold">${fmtPct(l.realizedPlPct)}</span>
             <span class="text-xs ${plClass} opacity-80">${fmtPriceWithSign(l.realizedPnl)}</span>
           </div>`;
      return `
        <tr class="border-b border-gray-800 hover:bg-gray-800/50 cursor-pointer ${isExpanded ? 'bg-gray-800/40' : ''}" onclick="PortfolioTransactions.toggleExpand(${l.id})">
          <td class="lot-sym font-medium">
            <i class="fas ${isExpanded ? 'fa-chevron-down' : 'fa-chevron-right'} text-secondary text-xs mr-2"></i>
            <a href="stock-detail.html?id=${l.stockId}&portfolioId=${l.portfolioId}" onclick="event.stopPropagation()" class="font-bold text-white hover:text-blue-400 transition-colors">${escHtml(l.stockSymbol || '—')}</a>
            <div class="text-xs text-secondary">${escHtml(l.stockName || '')}</div>
          </td>
          <td class="px-4 py-2 whitespace-nowrap">${fmtDate(l.transactionDate)}</td>
          <td class="px-4 py-2 text-right hide-mobile">${Number(l.quantity).toLocaleString('en-IN')}</td>
          <td class="px-4 py-2 text-right whitespace-nowrap">${fmtPrice(l.price)}</td>
          <td class="px-4 py-2 text-center">
            <span class="text-orange-400">${Number(l.soldQuantity).toLocaleString('en-IN')}</span>
            <span class="text-secondary">/</span>
            <span class="${canSell ? 'text-white font-semibold' : 'text-gray-500'}">${Number(l.remainingQuantity).toLocaleString('en-IN')}</span>
          </td>
          <td class="px-4 py-2 text-center">${statusBadge(l.status)}</td>
          <td class="px-4 py-2 text-right whitespace-nowrap hide-mobile">${currentStr}</td>
          <td class="px-4 py-1.5 text-right align-top">${openPlCell}</td>
          <td class="px-4 py-1.5 text-right align-top">${realizedCell}</td>
          <td class="px-4 py-2 text-center">
            ${canSell
              ? `<button onclick="event.stopPropagation();PortfolioTransactions.openSellModal(${l.id})" class="px-2 py-1 bg-orange-600 hover:bg-orange-700 text-white rounded text-xs font-medium"><i class="fas fa-tag mr-1"></i>Sell</button>`
              : '<span class="text-gray-600 text-xs">—</span>'}
          </td>
        </tr>
        ${isExpanded ? renderSellsRow(l) : ''}
      `;
    }).join('');
  }

  function renderSellsRow(l) {
    const sells = l.sells || [];
    const body = sells.length ? sells.map(s => `
          <tr class="border-b border-gray-800">
            <td class="px-4 py-2 whitespace-nowrap">${fmtDate(s.transactionDate)}</td>
            <td class="px-4 py-2 text-right">${Number(s.quantity).toLocaleString('en-IN')}</td>
            <td class="px-4 py-2 text-right whitespace-nowrap">${fmtPrice(s.price)}</td>
            <td class="px-4 py-2 text-right whitespace-nowrap">${s.fees != null ? fmtPrice(s.fees) : '—'}</td>
            <td class="px-4 py-2 text-right whitespace-nowrap ${s.realizedPnl > 0.005 ? 'pl-profit' : s.realizedPnl < -0.005 ? 'pl-loss' : 'pl-neutral'}">${fmtPriceWithSign(s.realizedPnl)}</td>
            <td class="px-4 py-2 text-xs text-secondary">${escHtml(s.notes || '—')}</td>
            <td class="px-4 py-2 text-center">
              <button onclick="PortfolioTransactions.deleteSell(${l.portfolioId}, ${s.id})" class="px-2 py-1 bg-red-600/80 hover:bg-red-600 text-white rounded text-xs" title="Delete this sell (restores lot quantity)"><i class="fas fa-trash"></i></button>
            </td>
          </tr>`).join('')
      : `<tr><td colspan="7" class="px-4 py-3 text-secondary text-xs text-center">No sells from this buy record yet — click <b>Sell</b> to mark a sale against it.</td></tr>`;
    return `
        <tr class="bg-gray-900/40">
          <td colspan="10" class="px-4 py-3">
            <div class="text-xs font-semibold uppercase tracking-wider text-secondary mb-2">
              <i class="fas fa-receipt mr-1 text-orange-400"></i> Sell records against buy #${l.id} — ${escHtml(l.stockSymbol)} (${sells.length ? Number(l.soldQuantity).toLocaleString('en-IN') : 0}/${Number(l.quantity).toLocaleString('en-IN')} sold)
            </div>
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-700 text-left text-xs text-secondary">
                    <th class="px-4 py-1.5">Sell Date</th>
                    <th class="px-4 py-1.5 text-right">Qty</th>
                    <th class="px-4 py-1.5 text-right">Price</th>
                    <th class="px-4 py-1.5 text-right">Fees</th>
                    <th class="px-4 py-1.5 text-right">Realized P/L</th>
                    <th class="px-4 py-1.5">Notes</th>
                    <th class="px-4 py-1.5 text-center">Actions</th>
                  </tr>
                </thead>
                <tbody>${body}</tbody>
              </table>
            </div>
          </td>
        </tr>`;
  }

  function renderPagination() {
    const total = state.filteredLots.length;
    const totalPages = Math.max(1, Math.ceil(total / state.pageSize));
    const cur = state.currentPage;

    if (els.paginationInfo) {
      if (total === 0) els.paginationInfo.textContent = 'No buy records';
      else {
        const start = (cur - 1) * state.pageSize + 1;
        const end = Math.min(cur * state.pageSize, total);
        els.paginationInfo.textContent = `Showing ${start}–${end} of ${total} buy records`;
      }
    }

    if (els.btnPrev) els.btnPrev.disabled = cur <= 1;
    if (els.btnNext) els.btnNext.disabled = cur >= totalPages;

    if (els.pageNumbers) {
      let html = '';
      const maxButtons = 5;
      let startPage = Math.max(1, cur - Math.floor(maxButtons / 2));
      let endPage = Math.min(totalPages, startPage + maxButtons - 1);
      if (endPage - startPage + 1 < maxButtons) startPage = Math.max(1, endPage - maxButtons + 1);

      if (startPage > 1) {
        html += `<button onclick="PortfolioTransactions.goToPage(1)" class="px-3 py-1.5 rounded border border-gray-600 bg-gray-800 text-white text-xs hover:bg-gray-700">1</button>`;
        if (startPage > 2) html += `<span class="text-secondary text-xs px-1">…</span>`;
      }

      for (let p = startPage; p <= endPage; p++) {
        const active = p === cur ? 'bg-blue-600 text-white border-blue-600' : 'bg-gray-800 text-white border-gray-600 hover:bg-gray-700';
        html += `<button onclick="PortfolioTransactions.goToPage(${p})" class="px-3 py-1.5 rounded border text-xs font-medium ${active}">${p}</button>`;
      }

      if (endPage < totalPages) {
        if (endPage < totalPages - 1) html += `<span class="text-secondary text-xs px-1">…</span>`;
        html += `<button onclick="PortfolioTransactions.goToPage(${totalPages})" class="px-3 py-1.5 rounded border border-gray-600 bg-gray-800 text-white text-xs hover:bg-gray-700">${totalPages}</button>`;
      }
      els.pageNumbers.innerHTML = html;
    }
  }

  function updateTableCount() {
    if (els.tableCount) {
      const total = state.filteredLots.length;
      const all = state.allLots.length;
      if (total === all) els.tableCount.textContent = `(${total})`;
      else els.tableCount.textContent = `(${total} of ${all})`;
    }
  }

  function updateSummary(data) {
    if (els.statTotal) els.statTotal.textContent = data.length.toLocaleString('en-IN');

    const invested = data.reduce((sum, l) => sum + Number(l.price) * Number(l.quantity), 0);
    if (els.statInvested) els.statInvested.textContent = fmtPriceCompact(invested);

    const realized = data.reduce((sum, l) => sum + Number(l.realizedPnl || 0), 0);
    if (els.statPl) {
      els.statPl.textContent = fmtPriceWithSign(realized);
      els.statPl.className = 'text-xl font-bold ' + (realized > 0.005 ? 'text-green-400' : realized < -0.005 ? 'text-red-400' : 'text-gray-400');
    }
    const investedSold = data.reduce((sum, l) => sum + Number(l.price) * Number(l.soldQuantity || 0), 0);
    const plPct = investedSold > 0 ? (realized / investedSold) * 100 : 0;
    if (els.statPlPct) {
      els.statPlPct.textContent = (plPct >= 0 ? '+' : '') + plPct.toFixed(2) + '%';
      els.statPlPct.className = 'text-xs font-semibold ' + (plPct > 0.005 ? 'text-green-400' : plPct < -0.005 ? 'text-red-400' : 'text-gray-400');
    }
    if (els.cardPl) {
      els.cardPl.className = 'card rounded-xl p-4 shadow-lg text-center ' + (realized > 0.005 ? 'border border-green-500/30' : realized < -0.005 ? 'border border-red-500/30' : '');
    }

    const soldLots = data.filter(l => l.status === 'SOLD').length;
    const openLots = data.filter(l => l.status !== 'SOLD').length;
    if (els.statProfitCount) els.statProfitCount.textContent = soldLots;
    if (els.statLossCount) els.statLossCount.textContent = openLots;

    const uniqueStocks = new Set(data.map(l => l.stockSymbol)).size;
    if (els.statUnique) els.statUnique.textContent = uniqueStocks.toLocaleString('en-IN');
  }

  // ─── EXPAND/COLLAPSE ───
  function toggleExpand(lotId) {
    state.expandedLotId = state.expandedLotId === lotId ? null : lotId;
    renderTable();
  }

  // ─── SELL MODAL ───
  function openSellModal(lotId) {
    const lot = state.allLots.find(l => l.id === lotId);
    if (!lot) return;
    document.getElementById('sell-lot-id').value = lotId;
    document.getElementById('sell-lot-info').innerHTML =
      `<b class="text-white">${escHtml(lot.stockSymbol)}</b> — buy #${lot.id} on ${fmtDate(lot.transactionDate)} @ ${fmtPrice(lot.price)} · open qty: <b class="text-white">${Number(lot.remainingQuantity).toLocaleString('en-IN')}</b>`;
    const qtyEl = document.getElementById('sell-qty');
    qtyEl.max = lot.remainingQuantity;
    qtyEl.value = lot.remainingQuantity;
    const ltp = state.priceMap[Number(lot.stockId)];
    document.getElementById('sell-price').value = ltp > 0 ? ltp : Number(lot.price);
    document.getElementById('sell-fees').value = '0';
    document.getElementById('sell-notes').value = '';
    document.getElementById('sell-date').value = new Date().toLocaleDateString('en-CA');
    const errEl = document.getElementById('sell-error');
    errEl.classList.add('hidden');
    const modal = document.getElementById('sell-modal');
    modal.classList.remove('hidden');
    qtyEl.focus();
  }

  function closeSellModal() {
    document.getElementById('sell-modal').classList.add('hidden');
  }

  async function submitSell(event) {
    event.preventDefault();
    const lotId = parseInt(document.getElementById('sell-lot-id').value, 10);
    const lot = state.allLots.find(l => l.id === lotId);
    if (!lot) return false;

    const qty = parseInt(document.getElementById('sell-qty').value, 10);
    const price = parseFloat(document.getElementById('sell-price').value);
    const fees = parseFloat(document.getElementById('sell-fees').value || '0');
    const notes = document.getElementById('sell-notes').value.trim();
    const dateVal = document.getElementById('sell-date').value;
    const errEl = document.getElementById('sell-error');
    const btn = document.getElementById('sell-submit');

    if (!qty || qty <= 0 || qty > lot.remainingQuantity) {
      errEl.textContent = `Quantity must be between 1 and ${lot.remainingQuantity} (open quantity for this buy record).`;
      errEl.classList.remove('hidden');
      return false;
    }

    btn.disabled = true;
    btn.textContent = 'Saving…';
    try {
      await sellFromLot(lot.portfolioId, {
        buyTransactionId: lot.id,
        quantity: qty,
        price: price,
        fees: fees,
        transactionDate: dateVal || undefined,
        notes: notes || undefined
      });
      closeSellModal();
      showToast(`Sold ${qty} × ${lot.stockSymbol} from buy #${lot.id}`, 'success');
      await loadAll(); // reload lots (status/remaining recomputed server-side)
    } catch (err) {
      errEl.textContent = err.message || 'Failed to record sell';
      errEl.classList.remove('hidden');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Confirm Sell';
    }
    return false;
  }

  async function deleteSell(portfolioId, txId) {
    if (!confirm('Delete this sell record? Its quantity returns to the buy lot.')) return;
    try {
      await deleteTransaction(portfolioId, txId);
      showToast('Sell record deleted', 'success');
      await loadAll();
    } catch (err) {
      showToast(err.message || 'Failed to delete sell record', 'error');
    }
  }

  // ─── PAGINATION ───
  function prevPage() {
    if (state.currentPage > 1) {
      state.currentPage--;
      state.expandedLotId = null;
      renderTable(); renderPagination();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }
  function nextPage() {
    const totalPages = Math.ceil(state.filteredLots.length / state.pageSize);
    if (state.currentPage < totalPages) {
      state.currentPage++;
      state.expandedLotId = null;
      renderTable(); renderPagination();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }
  function goToPage(p) {
    state.currentPage = p;
    state.expandedLotId = null;
    renderTable(); renderPagination();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  function changePageSize(val) {
    const n = parseInt(val, 10);
    state.pageSize = [25, 50, 100].includes(n) ? n : 50;
    state.currentPage = 1;
    state.expandedLotId = null;
    renderTable(); renderPagination();
  }

  // ─── FILTERS ───
  function clearFilters() {
    clearTimeout(debounceTimer);
    state.filters = { portfolioId: 'all', stockSearch: '', dateFrom: null, dateTo: null, status: 'all' };
    if (els.filterPortfolio) els.filterPortfolio.value = 'all';
    if (els.filterStock) els.filterStock.value = '';
    if (els.filterDateFrom) els.filterDateFrom.value = '';
    if (els.filterDateTo) els.filterDateTo.value = '';
    if (els.filterStatus) els.filterStatus.value = 'all';
    state.expandedLotId = null;
    applyFiltersAndRender();
  }

  // ─── EXPORT ───
  function exportCSV() {
    if (!state.filteredLots.length) {
      showToast('No buy records to export', 'info');
      return;
    }
    const headers = ['#', 'Buy ID', 'Stock Symbol', 'Stock Name', 'Portfolio', 'Buy Date', 'Buy Qty', 'Buy Price', 'Sold Qty', 'Remaining Qty', 'Status', 'Current Price', 'Realized P/L', 'Realized P/L %', 'Open P/L', 'Open P/L %', 'Sell Records', 'Notes'];
    const rows = state.filteredLots.map((l, i) => [
      i + 1,
      l.id,
      csvEscape(l.stockSymbol),
      csvEscape(l.stockName),
      csvEscape(l.portfolioName),
      l.transactionDate || '',
      l.quantity,
      Number(l.price).toFixed(2),
      l.soldQuantity,
      l.remainingQuantity,
      l.status,
      l.currentPrice ? Number(l.currentPrice).toFixed(2) : '',
      Number(l.realizedPnl || 0).toFixed(2),
      l.realizedPlPct != null ? Number(l.realizedPlPct).toFixed(2) : '',
      l.openPl != null ? Number(l.openPl).toFixed(2) : '',
      l.openPlPct != null ? Number(l.openPlPct).toFixed(2) : '',
      (l.sells || []).length,
      csvEscape(l.notes || '')
    ]);
    const csv = [headers, ...rows].map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `buy-lots-${new Date().toLocaleDateString('en-CA')}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    showToast('CSV exported: ' + state.filteredLots.length + ' buy records', 'success');
  }

  // ─── HELPERS ───
  function showLoading(isLoading) {
    const skel = document.getElementById('loading-skeleton');
    if (isLoading) {
      if (skel) skel.classList.remove('hidden');
    } else {
      if (skel) skel.classList.add('hidden');
    }
  }

  function showEmptyState(show) {
    if (!els.tableWrapper) return;
    const emptyEl = document.getElementById('empty-state');
    const pagination = document.getElementById('pagination-bar');
    if (show) {
      if (emptyEl) emptyEl.classList.remove('hidden');
      els.tableWrapper.classList.add('hidden');
      if (pagination) pagination.classList.add('hidden');
    } else {
      if (emptyEl) emptyEl.classList.add('hidden');
      els.tableWrapper.classList.remove('hidden');
      if (pagination) pagination.classList.remove('hidden');
    }
  }

  function showEmpty(msg) {
    if (els.lotRows) els.lotRows.innerHTML = '';
    showEmptyState(true);
    const emptyEl = document.getElementById('empty-state');
    if (emptyEl) {
      const h3 = emptyEl.querySelector('h3');
      if (h3) h3.textContent = 'No buy records found';
      const p = emptyEl.querySelector('p');
      if (p) p.textContent = msg;
      emptyEl.classList.remove('hidden');
    }
  }

  function fmtPrice(val) {
    if (val == null || isNaN(val)) return '--';
    return Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  function fmtPriceCompact(val) {
    if (val == null || isNaN(val)) return '--';
    if (val >= 10000000) return (val/10000000).toFixed(2) + ' Cr';
    if (val >= 100000) return (val/100000).toFixed(2) + ' L';
    return fmtPrice(val);
  }
  function fmtPct(val) {
    if (val == null || isNaN(val)) return '--';
    const sign = val > 0.005 ? '+' : '';
    return sign + Number(val).toFixed(2) + '%';
  }
  function fmtPriceWithSign(val) {
    if (val == null || isNaN(val)) return '--';
    const sign = val > 0.005 ? '+' : '';
    return sign + fmtPrice(val);
  }
  function fmtDate(str) {
    if (!str) return '--';
    try {
      const d = new Date(str);
      if (isNaN(d.getTime())) return escHtml(String(str));
      return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch { return escHtml(String(str)); }
  }
  function escHtml(str) {
    if (str == null) return '';
    const div = document.createElement('div');
    div.textContent = String(str);
    return div.innerHTML;
  }
  function csvEscape(s) {
    if (s == null) return '';
    return String(s).replace(/"/g, '""');
  }
  function showToast(msg, type) {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const bg = type === 'success' ? 'bg-green-600' : type === 'error' ? 'bg-red-600' : 'bg-gray-700';
    const icon = type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle';
    const toast = document.createElement('div');
    toast.className = `px-4 py-3 rounded-lg shadow-lg text-white text-sm flex items-center gap-2 ${bg} transform transition-all`;
    toast.innerHTML = `<i class="fas ${icon}"></i><span>${escHtml(msg)}</span>`;
    container.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; toast.style.transform = 'translateX(20px)'; setTimeout(() => toast.remove(), 300); }, 3000);
  }

  // Public API
  return { init, sortBy, prevPage, nextPage, goToPage, changePageSize, clearFilters, exportCSV,
           toggleExpand, openSellModal, closeSellModal, submitSell, deleteSell };
})();

document.addEventListener('DOMContentLoaded', PortfolioTransactions.init);
