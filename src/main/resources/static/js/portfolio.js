let allStocks = [];
let portfolioHistory = [];
let portfolios = [];
let currentPortfolioId = null;
const charts = {};
let chartInstances = {};
let sortField = 'pnlPercent';
let sortDirection = 'desc';
let signalSortField = 'compositeScore';
let signalSortDirection = 'desc';

document.addEventListener('DOMContentLoaded', () => {
  loadAll();

  // Add window cleanup handlers to prevent memory leaks
  window.addEventListener('beforeunload', () => {
    Object.keys(charts).forEach(key => {
      if (charts[key]) {
        charts[key].destroy();
        delete charts[key];
      }
    });
  });
});

// Track which portfolio each signal was loaded for — so we can invalidate
// filters when the user switches portfolios without re-hitting /api/signals
// when the data is already correct.

async function loadAll() {
  try {
    await loadPortfolios();
    await Promise.all([loadStocks(), loadHistoryChart()]);
    loadSignals();
  } catch (e) {
    console.error('Error loading dashboard:', e);
  }
}

// ─── Portfolio Context ────────────────────────────────────────────────────

async function loadPortfolios() {
  try {
    const res = await getPortfolios();
    portfolios = res.data || [];
    renderPortfolioSelector();
    // Auto-select default if none selected
    if (!currentPortfolioId && portfolios.length > 0) {
      const defaultPort = portfolios.find(p => p.default) || portfolios[0];
      currentPortfolioId = defaultPort.id;
      document.getElementById('portfolioSelector').value = currentPortfolioId;
    }
  } catch (e) {
    console.error('Error loading portfolios:', e);
  }
}

function renderPortfolioSelector() {
  const sel = document.getElementById('portfolioSelector');
  if (!sel) return;
  sel.innerHTML = portfolios.map(p =>
    `<option value="${p.id}">${escHtml(p.name)}${p.default ? ' (Default)' : ''}</option>`
  ).join('');
  if (currentPortfolioId) sel.value = currentPortfolioId;
}

async function switchPortfolio(id) {
  currentPortfolioId = parseInt(id);
  document.getElementById('portfolioSelector').value = currentPortfolioId;
  await Promise.all([loadStocks(), loadHistoryChart()]);
  await loadSignals();
}

function showPortfolioModal(editId) {
  const modal = document.getElementById('portfolioModal');
  const title = document.getElementById('portfolioModalTitle');
  const nameInput = document.getElementById('portfolioNameInput');
  const descInput = document.getElementById('portfolioDescInput');
  const idInput = document.getElementById('portfolioIdInput');
  const deleteBtn = document.getElementById('deletePortfolioBtn');

  if (editId) {
    const p = portfolios.find(x => x.id === editId);
    if (!p) return;
    title.textContent = 'Edit Portfolio';
    nameInput.value = p.name;
    descInput.value = p.description || '';
    idInput.value = editId;
    deleteBtn.style.display = p.default ? 'none' : 'inline-block';
  } else {
    title.textContent = 'New Portfolio';
    nameInput.value = '';
    descInput.value = '';
    idInput.value = '';
    deleteBtn.style.display = 'none';
  }
  modal.classList.remove('hidden');
}

function hidePortfolioModal() {
  document.getElementById('portfolioModal').classList.add('hidden');
}

async function removeHoldingFromPortfolio(holdingId) {
  if (!currentPortfolioId) return;
  const holding = allStocks.find(s => s.id === holdingId);
  if (!holding) return;
  if (!confirm(`Remove ${holding.symbol || 'this holding'} from the portfolio?`)) return;
  try {
    await removeHolding(currentPortfolioId, holdingId);
    showToast(`${holding.symbol || 'Holding'} removed from portfolio`, 'success');
    await switchPortfolio(currentPortfolioId);
  } catch (e) {
    showToast(e.message || 'Failed to remove holding', 'error');
  }
}

async function savePortfolio() {
  const nameInput = document.getElementById('portfolioNameInput');
  const descInput = document.getElementById('portfolioDescInput');
  const idInput = document.getElementById('portfolioIdInput');
  const name = nameInput.value.trim();
  if (!name) { showToast('Portfolio name is required', 'error'); return; }

  try {
    if (idInput.value) {
      // Edit existing portfolio
      await updatePortfolio(parseInt(idInput.value), name, descInput.value.trim());
      showToast('Portfolio updated!', 'success');
    } else {
      await createPortfolio(name, descInput.value.trim());
      showToast('Portfolio created!', 'success');
    }
    hidePortfolioModal();
    await loadPortfolios();
  } catch (e) {
    showToast('Error: ' + e.message, 'error');
  }
}

async function confirmDeletePortfolio() {
  const idInput = document.getElementById('portfolioIdInput');
  const id = parseInt(idInput.value);
  if (!id) return;
  const p = portfolios.find(x => x.id === id);
  if (!p) return;
  if (!confirm(`Delete "${p.name}"? This cannot be undone.`)) return;

  try {
    await deletePortfolio(id);
    showToast('Portfolio deleted', 'success');
    hidePortfolioModal();
    currentPortfolioId = null;
    await loadPortfolios();
    await loadStocks();
  } catch (e) {
    showToast('Error: ' + e.message, 'error');
  }
}

function escHtml(str) {
  if (!str) return '';
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

// ─── End Portfolio Context ────────────────────────────────────────────────

/**
 * Returns the stock id for either a HoldingDTO (uses stockId) or a StockDTO (uses id).
 * Centralizing this avoids the footgun where s.id is sometimes a holding PK,
 * sometimes a stock PK, depending on which endpoint populated the array.
 */
function stockIdOf(s) {
  return s.stockId != null ? s.stockId : s.id;
}

/**
 * Returns the holding id if the row is a HoldingDTO (when currentPortfolioId is set),
 * otherwise returns null. Used by removeHoldingFromPortfolio etc.
 */
function holdingIdOf(s) {
  return (currentPortfolioId && s.id != null) ? s.id : null;
}

async function loadStocks() {
  try {
    // IMPORTANT — `allStocks` holds TWO different shapes depending on which branch:
    //   path A (portfolio selected):  HoldingDTO[]   → {id: HOLDING_PK, stockId: STOCK_PK, ...}
    //   path B (no portfolio):        StockDTO[]      → {id: STOCK_PK,            quantity, ...}
    // Any code that compares stock IDs MUST use s.stockId || s.id, NOT s.id alone.
    if (currentPortfolioId) {
      const res = await getPortfolio(currentPortfolioId);
      allStocks = (res.data && res.data.holdings) || [];
    } else {
      const res = await getAllStocks();
      allStocks = (res.data || []).filter(s => s.quantity != null && s.quantity > 0);
    }
    renderSummary();
    renderHoldingsTable();
    renderPerformers();
    renderSectorChart();
    renderIndustryChart();
    renderPnlDistributionChart();
    updateRiskCards(allStocks);
  } catch (e) {
    console.error('Error loading stocks:', e);
    document.getElementById('holdingsTableBody').innerHTML =
      '<tr><td colspan="10" class="text-center text-danger">Error loading data</td></tr>';
  }
}

async function loadHistoryChart() {
  const daysSelector = document.getElementById('historyDays').value;
  const isAll = daysSelector === 'all';
  const days = isAll ? 365 : parseInt(daysSelector);
  try {
    const res = await getPortfolioHistory(days, isAll, currentPortfolioId);
    portfolioHistory = res.data || [];
    renderTrendChart();
  } catch (e) {
    console.error('Error loading history:', e);
    destroyChart('trend');
    const parent = document.getElementById('portfolioTrendChart')?.parentElement;
    if (parent) {
      parent.innerHTML = '<p class="text-danger text-center py-8">Failed to load portfolio history</p>';
    }
  }
}

// ─── Summary Cards ─────────────────────────────────────────────────────────

function renderSummary() {
  if (!allStocks.length) {
    ['totalInvestment','totalCurrentValue','totalPnl','totalPnlPct','holdingsCount','winRate']
    .forEach(id => document.getElementById(id).textContent = '--');
    return;
  }

  const totalInv = allStocks.reduce((s, st) => s + (st.investment || 0), 0);
  const totalVal = allStocks.reduce((s, st) => s + (st.currentValue || 0), 0);
  const totalPnl = allStocks.reduce((s, st) => s + (st.pnl || 0), 0);
  const totalPnlPct = totalInv > 0 ? (totalPnl / totalInv) * 100 : 0;
  const winners = allStocks.filter(s => (s.pnl || 0) > 0).length;
  const winRate = allStocks.length > 0 ? (winners / allStocks.length) * 100 : 0;

  document.getElementById('totalInvestment').textContent = fmtPrice(totalInv);
  document.getElementById('totalCurrentValue').textContent = fmtPrice(totalVal);
  document.getElementById('holdingsCount').textContent = allStocks.length;

  const pnlEl = document.getElementById('totalPnl');
  pnlEl.textContent = fmtPrice(totalPnl);
  pnlEl.className = 'text-lg font-bold ' + (totalPnl >= 0 ? 'text-green-500' : 'text-red-500');
  document.getElementById('totalPnlCard').className = 'card stat-card rounded-xl p-4 shadow-lg ' + (totalPnl >= 0 ? 'green' : 'red');

  const pnlPctEl = document.getElementById('totalPnlPct');
  pnlPctEl.textContent = (totalPnlPct >= 0 ? '+' : '') + totalPnlPct.toFixed(2) + '%';
  pnlPctEl.className = 'text-lg font-bold ' + (totalPnlPct >= 0 ? 'text-green-500' : 'text-red-500');
  document.getElementById('totalPnlPctCard').className = 'card stat-card rounded-xl p-4 shadow-lg ' + (totalPnlPct >= 0 ? 'green' : 'red');

  const wrEl = document.getElementById('winRate');
  wrEl.textContent = winRate.toFixed(1) + '%';
  wrEl.className = 'text-lg font-bold ' + (winRate >= 50 ? 'text-green-500' : 'text-red-500');
}

// ─── Portfolio Trend Chart ────────────────────────────────────────────────

/**
 * Locate the chart container for the portfolio trend canvas. Robust against HTML
 * structure changes — we walk up from the canvas (if present) to the nearest
 * `.chart-container` ancestor. If nothing is found, we synthesize a fallback
 * anchor using the body so the script never throws on a missing canvas.
 */
function getTrendChartContainer() {
  const canvas = document.getElementById('portfolioTrendChart');
  const direct = canvas ? canvas.parentElement : null;
  // Prefer explicitly-tagged container; fall back to direct parent.
  if (direct && direct.classList && direct.classList.contains('chart-container')) {
    return direct;
  }
  const candidate = direct ? direct.closest('.chart-container') : document.querySelector('.chart-container');
  return candidate || direct || document.body;
}

function renderTrendChart() {
  destroyChart('trend');
  const container = getTrendChartContainer();

  // Restore the canvas if a previous render destroyed it (replaced container.innerHTML).
  if (!document.getElementById('portfolioTrendChart')) {
    const c = document.createElement('canvas');
    c.id = 'portfolioTrendChart';
    container.appendChild(c);
  }
  const canvas = document.getElementById('portfolioTrendChart');
  const emptyMsg = container.querySelector('.trend-empty-msg');

  if (!portfolioHistory.length) {
    // Preserve the canvas element so subsequent renders still work; show empty-state message.
    canvas.style.display = 'none';
    if (!emptyMsg) {
      const msg = document.createElement('p');
      msg.className = 'trend-empty-msg text-secondary text-center py-8';
      msg.textContent = 'No historical data yet. Sync portfolio or add snapshots.';
      container.appendChild(msg);
    } else {
      emptyMsg.style.display = 'block';
    }
    return;
  }

  // We have data: show canvas, hide empty message.
  canvas.style.display = 'block';
  if (emptyMsg) emptyMsg.style.display = 'none';

  // Parse date strings into Date objects for the Chart.js time scale.
  // Use Date.UTC to anchor to midnight UTC so the chart displays the correct
  // calendar date regardless of the user's local timezone offset.
  const parseDate = (d) => {
    const p = d.split('-');
    return new Date(Date.UTC(parseInt(p[0]), parseInt(p[1]) - 1, parseInt(p[2])));
  };

  charts.trend = new Chart(document.getElementById('portfolioTrendChart'), {
    type: 'line',
    data: {
      datasets: [{
        label: 'Investment (₹)',
        data: portfolioHistory.map(h => ({ x: parseDate(h.date), y: h.totalInvestment })),
        borderColor: '#f59e0b',
        backgroundColor: 'rgba(245,158,11,0.08)',
        fill: true,
        tension: 0.3,
        pointRadius: 2,
        borderDash: [5, 5]
      }, {
        label: 'Portfolio Value (₹)',
        data: portfolioHistory.map(h => ({ x: parseDate(h.date), y: h.totalCurrentValue })),
        borderColor: '#22c55e',
        backgroundColor: 'rgba(34,197,94,0.1)',
        fill: true,
        tension: 0.3,
        pointRadius: 2
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { position: 'top' },
        tooltip: {
          callbacks: {
            title: (items) => {
              if (items.length && items[0].parsed.x != null) {
                const d = new Date(items[0].parsed.x);
                return d.toLocaleDateString('en-IN', {
                  day: 'numeric', month: 'short', year: 'numeric'
                });
              }
              return '';
            },
            label: ctx => ctx.dataset.label + ': ' + fmtPrice(ctx.parsed.y),
            footer: (items) => {
              if (items.length < 2) return '';
              // items[0] = Investment, items[1] = Portfolio Value (order matches datasets array)
              const investment = items[0].parsed.y;
              const portfolioValue = items[1].parsed.y;
              const pnl = portfolioValue - investment;
              const sign = pnl >= 0 ? '+' : '';
              const pnlPct = investment > 0 ? ((pnl / investment) * 100).toFixed(2) + '%' : '0.00%';
              return 'P&L: ' + sign + fmtPrice(pnl) + '  (' + sign + pnlPct + ')';
            }
          }
        }
      },
      scales: {
        x: {
          type: 'time',
          time: {
            tooltipFormat: 'dd MMM yyyy',
            displayFormats: {
              day: 'dd MMM',
              week: 'dd MMM',
              month: 'MMM yyyy'
            },
            minUnit: 'day'
          },
          ticks: {
            source: 'data',
            maxRotation: 45,
            autoSkip: true
          },
          title: {
            display: true,
            text: 'Date'
          }
        },
        y: {
          title: { display: true, text: 'Amount (₹)' },
          ticks: { callback: v => '₹' + Number(v).toLocaleString('en-IN') }
        }
      }
    }
  });
}

// ─── Sector Allocation Chart (Doughnut) ──────────────────────────────────

function renderSectorChart() {
  destroyChart('sector');
  if (!allStocks.length) return;

  const sectorMap = {};
  allStocks.forEach(s => {
    const sec = s.sector || 'Other';
    sectorMap[sec] = (sectorMap[sec] || 0) + (s.currentValue || 0);
  });

  const labels = Object.keys(sectorMap);
  const data = Object.values(sectorMap);
  const colors = generateColors(labels.length);

  charts.sector = new Chart(document.getElementById('sectorChart'), {
    type: 'doughnut',
    data: {
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderWidth: 2,
        borderColor: '#1e293b'
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: 'right', labels: { padding: 12 } },
        tooltip: {
          callbacks: {
            label: ctx => {
              const total = ctx.dataset.data.reduce((a, b) => a + b, 0);
              const pct = ((ctx.raw / total) * 100).toFixed(1);
              return ctx.label + ': ' + fmtPrice(ctx.raw) + ' (' + pct + '%)';
            }
          }
        }
      }
    }
  });
}

function renderIndustryChart() {
  destroyChart('industry');
  if (!allStocks.length) return;

  const industryMap = {};
  allStocks.forEach(s => {
    const ind = s.industry || 'Other';
    industryMap[ind] = (industryMap[ind] || 0) + (s.currentValue || 0);
  });

  const labels = Object.keys(industryMap);
  const data = Object.values(industryMap);
  const colors = generateColors(labels.length);

  charts.industry = new Chart(document.getElementById('industryChart'), {
    type: 'doughnut',
    data: {
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderWidth: 2,
        borderColor: '#1e293b'
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: 'right', labels: { padding: 12 } },
        tooltip: {
          callbacks: {
            label: ctx => {
              const total = ctx.dataset.data.reduce((a, b) => a + b, 0);
              const pct = ((ctx.raw / total) * 100).toFixed(1);
              return ctx.label + ': ' + fmtPrice(ctx.raw) + ' (' + pct + '%)';
            }
          }
        }
      }
    }
  });
}

// ─── P&L Distribution Chart (Horizontal Bar) ─────────────────────────────

function renderPnlDistributionChart() {
  destroyChart('pnlDist');
  if (!allStocks.length) return;

  const sorted = [...allStocks].sort((a, b) => (a.pnlPercent || 0) - (b.pnlPercent || 0));
  const labels = sorted.map(s => s.symbol);
  const data = sorted.map(s => s.pnlPercent || 0);
  const colors = data.map(v => v >= 0 ? 'rgba(34,197,94,0.8)' : 'rgba(239,68,68,0.8)');

  charts.pnlDist = new Chart(document.getElementById('pnlDistributionChart'), {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'P&L %',
        data,
        backgroundColor: colors,
        borderRadius: 4
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: ctx => 'P&L: ' + (ctx.raw >= 0 ? '+' : '') + ctx.raw.toFixed(2) + '%'
          }
        }
      },
      scales: {
        x: { title: { display: true, text: 'P&L %' } },
        y: { ticks: { font: { size: 11 }, autoSkip: false } }
      }
    }
  });
}

// ─── Signals & Insights ──────────────────────────────────────────────────

let allSignals = [];
let filteredSignals = [];
let fundamentalsMap = {}; // stockId -> { peRatio, marketCap }

async function loadSignals() {
  try {
    // Backend /api/signals returns signals for ALL stocks (no portfolio), or for a
    // single portfolio when ?portfolioId=X is supplied.
    const url = currentPortfolioId
      ? `/api/signals?portfolioId=${currentPortfolioId}`
      : '/api/signals';
    
    // Fetch signals and fundamentals in parallel
    const [signalsRes, fundRes] = await Promise.all([
      fetch(url).then(r => r.json()),
      screenFundamentals({ limit: 500, sortBy: 'marketCap', sortOrder: 'desc' }).catch(e => {
        console.warn('Could not load fundamentals for signals table:', e);
        return null;
      })
    ]);
    
    allSignals = signalsRes.data || [];

    if (currentPortfolioId && allStocks.length > 0) {
      // Defensive client-side filter — backend may not have applied the portfolioId
      // filter on every deployment. stockIdOf handles HoldingDTO vs StockDTO shapes.
      const holdingStockIds = new Set(allStocks.map(stockIdOf).filter(Boolean));
      allSignals = allSignals.filter(s => holdingStockIds.has(s.stockId));
    }

    // Load fundamentals map for P/E and Market Cap columns
    fundamentalsMap = {};
    if (fundRes && fundRes.status === 'success' && fundRes.data) {
      fundRes.data.forEach(function(f) {
        fundamentalsMap[f.stockId] = { peRatio: f.peRatio, marketCap: f.marketCap };
      });
    }

    if (allSignals.length) {
      applyFilters();
      updateMarketRegime(allSignals);
    } else {
      renderSortedSignals();
    }
  } catch (e) {
    console.error('Error loading signals:', e);
    const content = document.getElementById('signalsContent');
    if (content) content.innerHTML = '<div class="alert alert-danger text-center py-4">⚠ Failed to load signals. Check that price data is available</div>';
  }
}

function renderSortedSignals() {
  const data = filteredSignals.length ? filteredSignals : allSignals;
  if (!data || !data.length) {
    document.getElementById('signalsTableBody').innerHTML = '<tr><td colspan="16" class="text-center py-4 text-secondary">No matching signals</td></tr>';
    updateSignalStats([]);
    return;
  }

  const sorted = [...data].sort((a, b) => {
    const dir = signalSortDirection === 'asc' ? 1 : -1;
    let aVal = a[signalSortField];
    let bVal = b[signalSortField];

    // Handle nulls — sort to end
    if (aVal == null && bVal == null) return 0;
    if (aVal == null) return 1;
    if (bVal == null) return -1;

    // Compare by type
    if (typeof aVal === 'string') return dir * aVal.localeCompare(bVal);
    return dir * (aVal - bVal);
  });

  // Update sort icons
  document.querySelectorAll('[id^="sort-"]').forEach(el => el.innerHTML = '');
  const icon = document.getElementById(`sort-${signalSortField}`);
  if (icon) icon.innerHTML = signalSortDirection === 'asc' ? '&#9650;' : '&#9660;';

  updateSignalStats(data);
  renderOpportunityCards(data);

  const tbody = document.getElementById('signalsTableBody');
  tbody.innerHTML = sorted.map(s => {
    const displayRec = s.recommendation;
    const displayScore = s.compositeScore;
    const pnl = s.pnlPercent;
    const pnlStr = pnl != null ? (pnl >= 0 ? '+' : '') + pnl.toFixed(2) + '%' : '--';
    const rsiStr = s.rsi14 != null ? s.rsi14.toFixed(1) : '--';
    const pct52Low = s.pctFrom52WLow != null ? (s.pctFrom52WLow >= 0 ? '+' : '') + s.pctFrom52WLow.toFixed(1) + '%' : '--';
    const pct52High = s.pctFrom52WHigh != null ? (s.pctFrom52WHigh >= 0 ? '+' : '') + s.pctFrom52WHigh.toFixed(1) + '%' : '--';
    const macdStr = s.macdHistogram != null
      ? (s.macdHistogram >= 0
        ? '<span class="text-green-500"><i class="fas fa-arrow-up mr-1"></i>Bullish</span>'
        : '<span class="text-red-500"><i class="fas fa-arrow-down mr-1"></i>Bearish</span>')
      : '--';

    // Target — colored by comparison to latestPrice
    let targetStr = '--';
    let targetClass = 'text-gray-500';
    if (s.targetPrice != null) {
      targetStr = fmtPrice(s.targetPrice);
      targetClass = s.latestPrice != null && s.targetPrice > s.latestPrice ? 'text-green-500' : 'text-red-500';
    }

    // Stop Loss — always red
    let slStr = '--';
    if (s.stopLoss != null) {
      slStr = fmtPrice(s.stopLoss);
    }

    // Confidence badge
    const confStr = `<span class="px-2 py-0.5 rounded text-xs font-bold text-white ${getConfidenceBadge(s.confidenceScore)}">${s.confidenceScore ?? '--'}</span>`;

    // Position Size
    const posSize = s.suggestedPositionSize;
    const posSizeStr = posSize != null ? posSize + '%' : '--';

    // Age — bold if > 3, gray if > 7
    const age = s.signalAge;
    let ageStr = '--';
    let ageClass = '';
    if (age != null) {
      ageStr = age + 'd';
      ageClass = 'font-bold';
      if (age > 7) ageClass += ' text-gray-500';
    }

    const fund = fundamentalsMap[s.stockId] || {};
    const peStr = fund.peRatio != null ? fund.peRatio.toFixed(2) : '--';
    const mcStr = fund.marketCap != null ? formatMarketCap(fund.marketCap) : '--';

    // Signal badge with high conviction indicator
    // Liquidity badge
    const liqScore = s.liquidityScore?.compositeScore;
    let liqStr = '--';
    if (liqScore != null) {
      const badgeCls = getLiquidityBadge(liqScore);
      liqStr = `<span class="px-2 py-0.5 rounded text-xs font-bold text-white ${badgeCls}">${liqScore}</span>`;
    }

    const badgeClass = getRecommendationBadge(displayRec);
    const highConvBadge = displayRec !== s.recommendation ? '<i class="fas fa-info-circle text-yellow-400 ml-1" title="High conviction criteria not met"></i>' : '';

    // Strategy breakdown tooltip HTML
    const strategyTip = s.strategyBreakdown && s.strategyBreakdown.length
      ? renderStrategyBreakdownTooltip(s.strategyBreakdown)
      : '';

    return `<tr class="signal-row">
      <td><a href="stock-detail.html?id=${s.stockId}${currentPortfolioId ? `&portfolioId=${currentPortfolioId}` : ''}" class="font-bold hover:text-blue-400 transition-colors">${s.symbol}</a></td>
      <td class="strategy-tooltip-cell">
        <span class="px-2 py-0.5 rounded text-xs font-bold text-white ${badgeClass} strat-breakdown-toggle" data-strategy-tip="${strategyTip.replace(/"/g, '&quot;').replace(/'/g, '&#39;')}">${displayRec}${highConvBadge}</span>
      </td>
      <td class="text-right">${rsiStr}</td>
      <td class="text-right ${targetClass}">${targetStr}</td>
      <td class="text-right text-red-500">${slStr}</td>
      <td class="text-center">${confStr}</td>
      <td class="text-right">${posSizeStr}</td>
      <td class="text-right ${ageClass}">${ageStr}</td>
      <td class="text-right">${pct52Low}</td>
      <td class="text-right">${pct52High}</td>
      <td class="text-center">${macdStr}</td>
      <td class="text-right">${peStr}</td>
      <td class="text-right">${mcStr}</td>
      <td class="text-right">${liqStr}</td>
      <td class="text-right ${pnl >= 0 ? 'text-green-500' : 'text-red-500'}">${pnlStr}</td>
      <td class="text-right ${displayScore >= 0 ? 'text-green-500' : 'text-red-500'}">${displayScore >= 0 ? '+' : ''}${displayScore}</td>
    </tr>`;
  }).join('');
}

function sortSignalTable(field) {
  if (signalSortField === field) {
    signalSortDirection = signalSortDirection === 'asc' ? 'desc' : 'asc';
  } else {
    signalSortField = field;
    signalSortDirection = field === 'symbol' ? 'asc' : 'desc';
  }
  renderSortedSignals();
}

function applyFilters() {
  if (!allSignals.length) return;

  let result = [...allSignals];

  const sym = document.getElementById('filterSymbol')?.value?.trim().toLowerCase();
  if (sym) result = result.filter(s => s.symbol?.toLowerCase().includes(sym));

  const rsiMin = parseFloat(document.getElementById('filterRsiMin')?.value);
  if (!isNaN(rsiMin)) result = result.filter(s => s.rsi14 != null && s.rsi14 >= rsiMin);

  const rsiMax = parseFloat(document.getElementById('filterRsiMax')?.value);
  if (!isNaN(rsiMax)) result = result.filter(s => s.rsi14 != null && s.rsi14 <= rsiMax);

  const confMin = parseFloat(document.getElementById('filterConfidence')?.value);
  if (!isNaN(confMin) && confMin > 0) result = result.filter(s => s.confidenceScore != null && s.confidenceScore >= confMin);

  const compMin = parseFloat(document.getElementById('filterCompositeMin')?.value);
  if (!isNaN(compMin)) result = result.filter(s => s.compositeScore != null && s.compositeScore >= compMin);

  const liqMin = parseFloat(document.getElementById('filterLiquidityMin')?.value);
  if (!isNaN(liqMin) && liqMin > 0) result = result.filter(s => s.liquidityScore?.compositeScore != null && s.liquidityScore.compositeScore >= liqMin);

  const checkedRecs = Array.from(document.querySelectorAll('.rec-filter:checked')).map(cb => cb.value);
  if (checkedRecs.length > 0) {
    result = result.filter(s => checkedRecs.includes(s.recommendation));
  }

  filteredSignals = result;
  renderSortedSignals();
}

function updateRiskCards(stocks) {
  if (!stocks || stocks.length === 0) return;

  const holdings = stocks.filter(s => s.currentValue != null && s.currentValue > 0);
  if (holdings.length === 0) return;

  const total = holdings.reduce((sum, s) => sum + parseFloat(s.currentValue), 0);
  if (total === 0) return;

  // Top 3 holdings by concentration
  const top3 = [...holdings].sort((a, b) => parseFloat(b.currentValue) - parseFloat(a.currentValue)).slice(0, 3);
  const concText = top3.map(s => `${s.symbol} ${(parseFloat(s.currentValue) / total * 100).toFixed(1)}%`).join(', ');

  // Sector exposure — top 2
  const sectors = {};
  holdings.forEach(s => {
    const sec = s.sector || 'Other';
    sectors[sec] = (sectors[sec] || 0) + parseFloat(s.currentValue);
  });
  const sortedSectors = Object.entries(sectors).sort((a, b) => b[1] - a[1]).slice(0, 2);
  const sectorText = sortedSectors.map(([sec, val]) => `${sec} ${(val / total * 100).toFixed(1)}%`).join(', ');

  document.getElementById('concRisk').textContent = concText;
  document.getElementById('sectorRisk').textContent = sectorText;
  document.getElementById('riskCard').style.display = 'block';

  // Highlight risk if top holding > 20% or any sector > 40%
  const topPct = parseFloat(top3[0].currentValue) / total * 100;
  const maxSectorPct = sortedSectors.length > 0 ? sortedSectors[0][1] / total * 100 : 0;
  const riskCard = document.getElementById('riskCard');
  if (topPct > 20 || maxSectorPct > 40) {
    riskCard.classList.add('red');
    riskCard.classList.remove('green');
  } else {
    riskCard.classList.add('green');
    riskCard.classList.remove('red');
  }
}

function updateMarketRegime(signals) {
  if (!signals || signals.length === 0) return;

  let totalWeight = 0, totalStrength = 0;
  signals.forEach(s => {
    if (s.trendStrength != null && s.latestPrice != null) {
      const weight = parseFloat(s.latestPrice);
      totalStrength += s.trendStrength * weight;
      totalWeight += weight;
    }
  });
  if (totalWeight === 0) return;

  const avg = totalStrength / totalWeight;
  let regime, cls;
  if (avg > 55) { regime = 'Bullish'; cls = 'text-green-500'; }
  else if (avg < 45) { regime = 'Bearish'; cls = 'text-red-500'; }
  else { regime = 'Neutral'; cls = 'text-yellow-500'; }

  const el = document.getElementById('marketRegime');
  if (el) {
    el.textContent = regime;
    el.className = 'text-lg font-bold ' + cls;
    document.getElementById('regimeCard').style.display = 'block';
  }
}

function getRecommendationBadge(rec) {
  switch (rec) {
    case 'STRONG BUY': return 'bg-green-600';
    case 'BUY': return 'bg-green-500';
    case 'SELL': return 'bg-red-500';
    case 'STRONG SELL': return 'bg-red-600';
    default: return 'bg-gray-500';
  }
}

function getLiquidityBadge(score) {
  if (!score && score !== 0) return 'bg-gray-600';
  if (score >= 80) return 'bg-cyan-600';
  if (score >= 60) return 'bg-cyan-500';
  if (score >= 40) return 'bg-yellow-600';
  if (score >= 20) return 'bg-orange-600';
  return 'bg-red-600';
}

function getConfidenceBadge(score) {
  if (!score && score !== 0) return 'bg-gray-600';
  if (score >= 70) return 'bg-green-600';
  if (score >= 40) return 'bg-yellow-600';
  return 'bg-red-600';
}

function updateSignalStats(data) {
  const strongBuy = data.filter(s => s.recommendation === 'STRONG BUY').length;
  const buy = data.filter(s => s.recommendation === 'BUY').length;
  const hold = data.filter(s => s.recommendation === 'HOLD').length;
  const sell = data.filter(s => s.recommendation === 'SELL').length;
  const strongSell = data.filter(s => s.recommendation === 'STRONG SELL').length;

  const sbEl = document.getElementById('strongBuyCount');
  const buyEl = document.getElementById('buyCount');
  const holdEl = document.getElementById('holdCount');
  const sellEl = document.getElementById('sellCount');
  const ssEl = document.getElementById('strongSellCount');
  if (sbEl) sbEl.textContent = strongBuy;
  if (buyEl) buyEl.textContent = buy;
  if (holdEl) holdEl.textContent = hold;
  if (sellEl) sellEl.textContent = sell;
  if (ssEl) ssEl.textContent = strongSell;
}

// High conviction criteria (relaxed: >= 16 instead of === 19)
function isHighConvictionBuy(signal) {
  if (!signal) return false;
  const indicatorCoverageOk = signal.indicatorCoverage >= 16;
  const trendOk = signal.sma20 > signal.sma50;
  const volumeOk = signal.volumeConfirmed === true;
  return signal.recommendation === 'STRONG BUY' && indicatorCoverageOk && trendOk && volumeOk;
}

function getDisplayRecommendation(signal) {
  return signal || {};
}

function renderOpportunityCards(data) {
  const buyCard = document.getElementById('buySignalsCard');
  const sellCard = document.getElementById('sellSignalsCard');
  const buyCountEl = document.getElementById('buyOpportunityCount');
  const sellCountEl = document.getElementById('sellWarningCount');
  
  if (!buyCard || !sellCard) return;

  // Prioritize STRONG BUY, fill remaining slots with BUY
  const strongBuySignals = data
    .filter(s => s.recommendation === 'STRONG BUY')
    .sort((a, b) => ((b.displayScore ?? b.compositeScore) || 0) - ((a.displayScore ?? a.compositeScore) || 0))
    .slice(0, 5);
  const buyFillCount = 5 - strongBuySignals.length;
  const buySignals = buyFillCount > 0
    ? strongBuySignals.concat(
        data
          .filter(s => s.recommendation === 'BUY')
          .filter(s => !strongBuySignals.find(sb => sb.stockId === s.stockId))
          .sort((a, b) => ((b.displayScore ?? b.compositeScore) || 0) - ((a.displayScore ?? a.compositeScore) || 0))
          .slice(0, buyFillCount)
      )
    : strongBuySignals;

  // Prioritize STRONG SELL, fill remaining slots with SELL
  const strongSellSignals = data
    .filter(s => s.recommendation === 'STRONG SELL')
    .sort((a, b) => (b.confidenceScore || 0) - (a.confidenceScore || 0))
    .slice(0, 5);
  const sellFillCount = 5 - strongSellSignals.length;
  const sellSignals = sellFillCount > 0
    ? strongSellSignals.concat(
        data
          .filter(s => s.recommendation === 'SELL')
          .filter(s => !strongSellSignals.find(ss => ss.stockId === s.stockId))
          .sort((a, b) => (b.confidenceScore || 0) - (a.confidenceScore || 0))
          .slice(0, sellFillCount)
      )
    : strongSellSignals;
  
  if (buyCountEl) buyCountEl.textContent = buySignals.length;
  if (sellCountEl) sellCountEl.textContent = sellSignals.length;

  buyCard.innerHTML = buySignals.length
    ? buySignals.map(s => {
        const displayRec = s.displayRecommendation || s.recommendation;
        const displayScore = s.displayScore ?? s.compositeScore;
        const badgeClass = displayRec === 'STRONG BUY' ? 'bg-green-600' : 'bg-green-500';
        return `
      <div class="flex items-center justify-between p-3 bg-gray-800/50 rounded-lg border border-gray-700/50 hover:border-green-500/50 transition-colors">
        <div class="flex items-center gap-3">
          <div class="flex flex-col">
            <a href="stock-detail.html?id=${s.stockId}${currentPortfolioId ? `&portfolioId=${currentPortfolioId}` : ''}" class="font-bold text-white hover:text-blue-400 text-sm">${s.symbol}</a>
            <span class="text-xs text-secondary">${s.name || ''}</span>
          </div>
          <span class="px-2 py-0.5 rounded text-xs font-bold text-white ${badgeClass}">${displayRec}</span>
        </div>
        <div class="text-right text-xs">
          <div class="text-green-400 font-bold text-sm">${s.confidenceScore ?? '--'}/100</div>
          <div class="text-secondary">Score: ${displayScore ?? '--'}</div>
          <div class="text-secondary">Target: ${s.targetPrice != null ? fmtPrice(s.targetPrice) : '--'}</div>
        </div>
      </div>`;
      }).join('')
    : '<p class="text-secondary text-sm py-3 text-center">No buy opportunities</p>';

  sellCard.innerHTML = sellSignals.length
    ? sellSignals.map(s => `
      <div class="flex items-center justify-between p-3 bg-gray-800/50 rounded-lg border border-gray-700/50 hover:border-red-500/50 transition-colors">
        <div class="flex items-center gap-3">
          <div class="flex flex-col">
            <a href="stock-detail.html?id=${s.stockId}${currentPortfolioId ? `&portfolioId=${currentPortfolioId}` : ''}" class="font-bold text-white hover:text-blue-400 text-sm">${s.symbol}</a>
            <span class="text-xs text-secondary">${s.name || ''}</span>
          </div>
          <span class="px-2 py-0.5 rounded text-xs font-bold text-white ${getRecommendationBadge(s.recommendation)}">${s.recommendation}</span>
        </div>
        <div class="text-right text-xs">
          <div class="text-red-400 font-bold text-sm">${s.confidenceScore ?? '--'}/100</div>
          <div class="text-secondary">Score: ${s.compositeScore ?? '--'}</div>
          <div class="text-secondary">Target: ${s.targetPrice != null ? fmtPrice(s.targetPrice) : '--'}</div>
        </div>
      </div>`).join('')
    : '<p class="text-secondary text-sm py-3 text-center">No sell warnings</p>';
}

// ─── Top / Bottom Performers ─────────────────────────────────────────────

function renderPerformers() {
  const withPnl = allStocks.filter(s => s.pnlPercent != null);
  if (!withPnl.length) {
    document.getElementById('topPerformers').innerHTML = '<p class="text-secondary">No data</p>';
    document.getElementById('bottomPerformers').innerHTML = '<p class="text-secondary">No data</p>';
    return;
  }

  const sorted = [...withPnl].sort((a, b) => (b.pnlPercent || 0) - (a.pnlPercent || 0));
  const top = sorted.slice(0, 5);
  const bottom = sorted.slice(-5).reverse();

  document.getElementById('topPerformers').innerHTML = renderPerformerList(top, true);
  document.getElementById('bottomPerformers').innerHTML = renderPerformerList(bottom, false);
}

function renderPerformerList(stocks, isTop) {
  if (!stocks.length) return '<p class="text-secondary">No data</p>';
  return stocks.map(s => {
    const pnl = s.pnlPercent || 0;
    const cls = pnl >= 0 ? 'text-green-500' : 'text-red-500';
    const icon = pnl >= 0 ? 'fa-caret-up' : 'fa-caret-down';
    return `<div class="flex items-center justify-between py-2 border-b border-gray-700 last:border-0">
      <div class="flex items-center gap-2">
        <i class="fas ${icon} ${cls}"></i>
        <a href="stock-detail.html?id=${s.stockId || s.id}${currentPortfolioId ? `&portfolioId=${currentPortfolioId}` : ''}" class="font-medium hover:text-blue-400">${s.symbol}</a>
        <span class="text-sm text-secondary">${s.name}</span>
      </div>
      <span class="font-semibold ${cls}">${pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}%</span>
    </div>`;
  }).join('');
}

// ─── Holdings Table ──────────────────────────────────────────────────────

function renderHoldingsTable() {
  const tbody = document.getElementById('holdingsTableBody');
  if (!allStocks.length) {
    tbody.innerHTML = '<tr><td colspan="10" class="text-center py-8">No holdings found. Import stocks with portfolio data.</td></tr>';
    document.getElementById('tableSummary').textContent = '';
    return;
  }

  const invested = allStocks.filter(s => s.quantity && s.quantity > 0);
  document.getElementById('tableSummary').textContent =
    invested.length + ' active holdings · ' + allStocks.length + ' total stocks';

  const sorted = sortStocks(allStocks);
  tbody.innerHTML = sorted.map(s => {
    const pnl = s.pnl;
    const pnlPct = s.pnlPercent;
    const cls = pnl >= 0 ? 'text-green-500' : 'text-red-500';
    const badgeCls = pnl >= 0 ? 'bg-green-600' : 'bg-red-600';
    const linkUrl = `stock-detail.html?id=${s.stockId || s.id}${currentPortfolioId ? `&portfolioId=${currentPortfolioId}` : ''}`;
    return `<tr>
      <td><a href="${linkUrl}" class="font-bold hover:text-blue-400 transition-colors">${s.name || '--'}</a></td>
      <td>${s.sector || '--'}</td>
      <td class="text-right">${s.quantity ?? '--'}</td>
      <td class="text-right">${fmtPrice(s.avgPrice)}</td>
      <td class="text-right">${fmtPrice(s.lastTradedPrice)}</td>
      <td class="text-right">${fmtPrice(s.investment)}</td>
      <td class="text-right">${fmtPrice(s.currentValue)}</td>
      <td class="text-right ${cls}">${fmtPrice(pnl)}</td>
      <td class="text-right"><span class="px-2 py-0.5 rounded text-xs font-medium text-white ${badgeCls}">${pnlPct != null ? (pnlPct >= 0 ? '+' : '') + pnlPct.toFixed(2) + '%' : '--'}</span></td>
      <td class="text-center">
        <button onclick="openTransactionModal(${s.id}, ${stockIdOf(s)}, '${escHtml(s.symbol)}', 'BUY')" title="Buy more" class="text-green-400 hover:text-green-300 transition-colors mr-2">
          <i class="fas fa-plus-circle"></i>
        </button>
        <button onclick="openTransactionModal(${s.id}, ${stockIdOf(s)}, '${escHtml(s.symbol)}', 'SELL')" title="Sell" class="text-yellow-400 hover:text-yellow-300 transition-colors mr-2">
          <i class="fas fa-minus-circle"></i>
        </button>
        <button onclick="removeHoldingFromPortfolio(${s.id})" title="Remove from portfolio" class="text-red-400 hover:text-red-300 transition-colors">
          <i class="fas fa-trash-alt"></i>
        </button>
      </td>
    </tr>`;
  }).join('');
}

function sortStocks(stocks) {
  const dir = sortDirection === 'asc' ? 1 : -1;
  return [...stocks].sort((a, b) => {
    switch (sortField) {
      case 'symbol': return dir * a.symbol.localeCompare(b.symbol);
      case 'name': return dir * (a.name || '').localeCompare(b.name || '');
      case 'sector': return dir * (a.sector || '').localeCompare(b.sector || '');
      case 'quantity': return dir * ((a.quantity || 0) - (b.quantity || 0));
      case 'avgPrice': return dir * ((a.avgPrice || 0) - (b.avgPrice || 0));
      case 'ltp': return dir * ((a.lastTradedPrice || 0) - (b.lastTradedPrice || 0));
      case 'investment': return dir * ((a.investment || 0) - (b.investment || 0));
      case 'currentValue': return dir * ((a.currentValue || 0) - (b.currentValue || 0));
      case 'pnl': return dir * ((a.pnl || 0) - (b.pnl || 0));
      case 'pnlPercent': return dir * ((a.pnlPercent || 0) - (b.pnlPercent || 0));
      default: return 0;
    }
  });
}

function sortTable(field) {
  if (sortField === field) {
    sortDirection = sortDirection === 'asc' ? 'desc' : 'asc';
  } else {
    sortField = field;
    sortDirection = 'desc';
  }
  renderHoldingsTable();
}

// ─── Actions ─────────────────────────────────────────────────────────────

async function refreshData() {
  const btns = document.querySelectorAll('button');
  btns.forEach(b => { if (b.textContent.includes('Refresh')) { b.disabled = true; b.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Loading...'; }});
  await loadAll();
  btns.forEach(b => { if (b.textContent.includes('Loading')) { b.disabled = false; b.innerHTML = '<i class="fas fa-sync-alt mr-2"></i>Refresh'; }});
}

async function recalculatePortfolio() {
  if (!confirm('Recalculate P&L for ' + (currentPortfolioId ? 'this portfolio' : 'all stocks') + '?')) return;
  try {
    if (currentPortfolioId) {
      await recalculatePortfolioApi(currentPortfolioId);
    } else {
      await recalculateAllPortfolios();
    }
    showToast('Portfolio recalculated successfully!', 'success');
    await loadStocks();
  } catch (e) {
    showToast('Error: ' + e.message, 'error');
  }
}

// ─── Helpers ─────────────────────────────────────────────────────────────

function fmtPrice(val) {
  if (val == null || isNaN(val)) return '--';
  return '₹' + Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatMarketCap(val) {
  if (val == null) return '--';
  const cr = val / 10000000;
  if (cr >= 100) return (cr / 100).toFixed(1) + 'K Cr';
  return cr.toFixed(1) + ' Cr';
}

function destroyChart(key) {
  if (charts[key]) { charts[key].destroy(); delete charts[key]; }
}

const CHART_COLORS = [
  '#3b82f6', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#ec4899', '#14b8a6', '#f97316', '#6366f1',
  '#84cc16', '#d946ef', '#0ea5e9', '#a855f7', '#eab308'
];

function generateColors(count) {
  if (count <= CHART_COLORS.length) return CHART_COLORS.slice(0, count);
  return Array.from({ length: count }, (_, i) =>
    'hsl(' + (i * 360 / count) + ', 65%, 55%)'
  );
}

function showToast(msg, type) {
  const container = document.getElementById('summaryCards');
  const toast = document.createElement('div');
  toast.className = 'fixed bottom-4 right-4 px-6 py-3 rounded-lg shadow-lg z-50 text-white ' +
    (type === 'success' ? 'bg-green-600' : 'bg-red-600');
  toast.innerHTML = '<i class="fas ' + (type === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle') + ' mr-2"></i>' + msg;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 3000);
}

// ─── Strategy Breakdown Tooltip ──────────────────────────────────────────

/**
 * Renders a compact HTML table showing each strategy's contribution to the signal.
 */
function renderStrategyBreakdownTooltip(breakdown) {
  if (!breakdown || !breakdown.length) return '';
  const rows = breakdown.map(s => {
    const signalColor = s.signal === 'BUY' || s.signal === 'STRONG BUY' ? '#22c55e'
      : s.signal === 'SELL' || s.signal === 'STRONG SELL' ? '#ef4444'
      : '#9ca3af';
    const confPct = (s.confidence * 100).toFixed(0) + '%';
    const contrib = s.weightedScore != null ? s.weightedScore.toFixed(2) : '0.00';
    let volumeDetails = '';
    if (s.strategyName === 'VOLUME' && (s.latestVolume != null || s.avgVolume != null || s.spikeThreshold != null)) {
        const latestVol = s.latestVolume != null ? Number(s.latestVolume).toLocaleString('en-IN') : 'N/A';
        const avgVolNum = s.avgVolume != null ? Number(s.avgVolume) : 0;
        const avgVol = avgVolNum > 0 ? Math.round(avgVolNum).toLocaleString('en-IN') : 'N/A';
        const spikeThresh = (s.spikeThreshold != null && avgVolNum > 0) 
            ? (Number(s.spikeThreshold) / avgVolNum).toFixed(1) + 'x' 
            : 'N/A';
        volumeDetails = `<span class="strat-breakdown-vol" style="color:#8892a0;font-size:9px;margin-left:4px;">Vol: ${latestVol} | Avg: ${avgVol} | Thresh: ${spikeThresh}</span>`;
    }
    return `<div class="strat-breakdown-row">
      <span class="strat-breakdown-name">${escHtml(s.strategyName)}</span>
      <span class="strat-breakdown-signal" style="color:${signalColor}">${s.signal}</span>
      <span class="strat-breakdown-conf">${confPct}</span>
      <span class="strat-breakdown-prio">P${s.priority}</span>
      <span class="strat-breakdown-contrib">${contrib}</span>
      ${volumeDetails}
    </div>`;
  }).join('');
  return `<div class="strat-breakdown-tooltip-inner">
    <div class="strat-breakdown-header">
      <span>Strategy</span><span>Signal</span><span>Conf</span><span>Pri</span><span>Contrib</span>
    </div>
    ${rows}
  </div>`;
}

// ─── Buy / Sell Transactions ───────────────────────────────────────────

let txnModalStockId = null;
let txnModalHoldingId = null;
let txnModalSymbol = null;
let txnModalMode = 'BUY';

function openTransactionModal(holdingId, stockId, symbol, mode) {
  txnModalHoldingId = holdingId;
  txnModalStockId = stockId;
  txnModalSymbol = symbol;
  txnModalMode = mode;

  const modal = document.getElementById('transactionModal');
  const title = document.getElementById('transactionModalTitle');
  title.textContent = (mode === 'BUY' ? 'Buy ' : 'Sell ') + (symbol || 'Stock');
  document.getElementById('txnType').value = mode;
  document.getElementById('txnQuantity').value = '';
  document.getElementById('txnPrice').value = '';
  document.getElementById('txnFees').value = '';
  document.getElementById('txnDate').value = new Date().toISOString().slice(0, 10);
  document.getElementById('txnNotes').value = '';
  document.getElementById('txnError').textContent = '';
  document.getElementById('txnError').style.display = 'none';
  modal.classList.remove('hidden');
}

function hideTransactionModal() {
  document.getElementById('transactionModal').classList.add('hidden');
}

async function submitTransaction() {
  if (!currentPortfolioId || !txnModalStockId) return;
  const type = document.getElementById('txnType').value;
  const quantity = parseInt(document.getElementById('txnQuantity').value);
  const price = parseFloat(document.getElementById('txnPrice').value);
  const feesRaw = document.getElementById('txnFees').value;
  const fees = feesRaw ? parseFloat(feesRaw) : 0;
  const date = document.getElementById('txnDate').value || new Date().toISOString().slice(0, 10);
  const notes = document.getElementById('txnNotes').value.trim();

  const errEl = document.getElementById('txnError');
  if (!quantity || quantity <= 0) { errEl.textContent = 'Quantity must be positive'; errEl.style.display = 'block'; return; }
  if (isNaN(price) || price < 0) { errEl.textContent = 'Price must be zero or positive'; errEl.style.display = 'block'; return; }

  const payload = { stockId: txnModalStockId, type, quantity, price, fees, transactionDate: date, notes };
  try {
    await recordTransaction(currentPortfolioId, payload);
    showToast((type === 'BUY' ? 'Bought ' : 'Sold ') + quantity + ' of ' + (txnModalSymbol || ''), 'success');
    hideTransactionModal();
    await switchPortfolio(currentPortfolioId);
  } catch (e) {
    errEl.textContent = e.message || 'Failed to record transaction';
    errEl.style.display = 'block';
  }
}


// Click handler for strategy breakdown tooltips
// Uses event delegation on the signals table body
let strategyTooltipTimer = null;
let activeTooltipEl = null;

document.addEventListener('click', function(e) {
  const toggle = e.target.closest('.strat-breakdown-toggle');
  if (!toggle) {
    // Click outside → remove all tooltips
    document.querySelectorAll('.strat-breakdown-popup').forEach(el => el.remove());
    activeTooltipEl = null;
    return;
  }
  e.stopPropagation();

  // Remove any other open tooltips
  document.querySelectorAll('.strat-breakdown-popup').forEach(el => {
    if (el._trigger !== toggle) el.remove();
  });

  // If clicking the already-active one, close it
  if (activeTooltipEl === toggle) {
    document.querySelectorAll('.strat-breakdown-popup').forEach(el => el.remove());
    activeTooltipEl = null;
    return;
  }

  const tipHtml = toggle.getAttribute('data-strategy-tip');
  if (!tipHtml) return;

  // Remove existing popup for this toggle
  const existing = toggle.parentElement.querySelector('.strat-breakdown-popup');
  if (existing) { existing.remove(); activeTooltipEl = null; return; }

  const popup = document.createElement('div');
  popup.className = 'strat-breakdown-popup';
  popup._trigger = toggle;
  popup.innerHTML = tipHtml;
  toggle.parentElement.appendChild(popup);
  activeTooltipEl = toggle;
});


