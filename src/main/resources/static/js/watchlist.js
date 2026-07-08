let allWatchlists = [];
let allStocks = [];
let selectedWatchlistId = null;
let _selectedStock = null; // Currently selected stock from search

// ─── Chart Colors ───────────────────────────────────────────────────────────

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

let _sectorChart = null;

// ─── Signal Badge Helpers ───────────────────────────────────────────────────

function getRecommendationBadge(rec) {
switch (rec) {
case 'STRONG BUY': return 'bg-green-600';
case 'BUY': return 'bg-green-500';
case 'SELL': return 'bg-red-500';
case 'STRONG SELL': return 'bg-red-600';
default: return 'bg-gray-500';
}
}

let _signalTooltipHideTimer = null;

function renderSignalBadge(signal) {
if (!signal || !signal.recommendation) {
return '<span class="signal-badge bg-gray-500">--</span>';
}
const rec = signal.recommendation;
const score = signal.compositeScore ?? 0;
const bgClass = getRecommendationBadge(rec);

const tooltipRows = [
	[
		{ label: 'RSI (14)', value: signal.rsi14 != null ? signal.rsi14.toFixed(1) : '--' },
		{ label: 'MACD', value: signal.macdHistogram != null ? signal.macdHistogram.toFixed(2) : '--' },
	],
	[
		{ label: 'Bollinger', value: signal.bollingerScore != null ? signal.bollingerScore : '--' },
		{ label: 'Confidence', value: signal.confidenceScore != null ? signal.confidenceScore + '%' : '--' },
	],
	[
		{ label: 'Target', value: signal.targetPrice != null ? '₹' + signal.targetPrice.toFixed(1) : '--' },
		{ label: 'Stop Loss', value: signal.stopLoss != null ? '₹' + signal.stopLoss.toFixed(1) : '--' },
	],
];

const tooltipHtml = `
<div class="signal-tooltip">
<div class="tooltip-grid">
<div class="tooltip-row tooltip-row-full"><span class="tooltip-label">Score</span><span>${score}</span></div>
${tooltipRows.map(pair => pair.map(r => `<div class="tooltip-row"><span class="tooltip-label">${r.label}</span><span>${r.value}</span></div>`).join('')).join('')}
</div>
</div>
`;

return `<span class="signal-badge ${bgClass}" onmouseenter="showSignalTooltip(event)" onmouseleave="scheduleHideSignalTooltip(event)">${escHtml(rec)}${score !== 0 ? ` <span class="score">(${score >= 0 ? '+' : ''}${score})</span>` : ''}${tooltipHtml}</span>`;
}

function showSignalTooltip(e) {
clearTimeout(_signalTooltipHideTimer);
const badge = e.currentTarget;
const tooltip = badge.querySelector('.signal-tooltip');
if (!tooltip) return;

tooltip.style.display = 'block';

// Add mouseleave handler on tooltip itself to allow user to hover over it
tooltip.onmouseenter = () => clearTimeout(_signalTooltipHideTimer);
tooltip.onmouseleave = () => hideSignalTooltip();

// Position to the right of the badge
const badgeRect = badge.getBoundingClientRect();
const tipRect = tooltip.getBoundingClientRect();

let left = badgeRect.right + 8;
let top = badgeRect.top + (badgeRect.height / 2) - (tipRect.height / 2);

// Keep within viewport
if (left + tipRect.width > window.innerWidth - 8) left = badgeRect.left - tipRect.width - 8;
if (top < 8) top = 8;
if (top + tipRect.height > window.innerHeight - 8) top = window.innerHeight - tipRect.height - 8;

tooltip.style.left = left + 'px';
tooltip.style.top = top + 'px';
}

function scheduleHideSignalTooltip(e) {
_signalTooltipHideTimer = setTimeout(() => hideSignalTooltip(), 150);
}

function hideSignalTooltip() {
document.querySelectorAll('.signal-tooltip').forEach(t => {
t.style.display = 'none';
t.onmouseenter = null;
t.onmouseleave = null;
});
}

// ─── Add Stock Modal Helpers ────────────────────────────────────────────────

function clearSelectedStock() {
_selectedStock = null;
document.getElementById('selectedStockFields').classList.add('hidden');
document.getElementById('stockInWatchlistWarning').classList.add('hidden');
document.getElementById('addStockBtn').disabled = true;
document.getElementById('addStockSearch').value = '';
document.getElementById('addStockSearch').focus();
}

function checkIfInWatchlist(symbol) {
const warning = document.getElementById('stockInWatchlistWarning');
const text = document.getElementById('stockInWatchlistText');
const existing = allStocks.find(s => s.symbol.toUpperCase() === symbol.toUpperCase());
if (existing && selectedWatchlistId) {
getWatchlistItems(selectedWatchlistId).then(res => {
const stocks = res.data?.stocks || [];
const inWatchlist = stocks.some(s => s.id === existing.id);
if (inWatchlist) {
text.textContent = symbol + ' is already in this watchlist.';
warning.classList.remove('hidden');
document.getElementById('addStockBtn').disabled = true;
} else {
warning.classList.add('hidden');
document.getElementById('addStockBtn').disabled = false;
}
}).catch(() => {
warning.classList.add('hidden');
});
} else {
warning.classList.add('hidden');
}
}

async function confirmAddSingleStock() {
if (!_selectedStock) return;
const watchlistId = parseInt(document.getElementById('addStockWatchlistId').value);
const stock = _selectedStock;

// Check if stock exists locally
let localStock = allStocks.find(s => s.symbol.toUpperCase() === stock.symbol.toUpperCase());

// If not local, create it first
if (!localStock) {
const selectedSector = document.getElementById('qcSector').value || 'Other';
try {
const createRes = await addStockApi({
symbol: stock.symbol,
name: stock.name,
sector: selectedSector,
yahooSymbol: stock.symbol
});
localStock = createRes.data;
const stocksRes = await getAllStocks();
allStocks = stocksRes.data || [];
} catch (e) {
showToast(e.message || 'Failed to create stock', 'error');
return;
}
}

// Add to watchlist
try {
await addStockToWatchlist(watchlistId, localStock.id);
showToast(stock.symbol + ' added to watchlist', 'success');
closeModal('addStockModal');
await selectWatchlist(watchlistId);
const res = await getWatchlists();
allWatchlists = res.data || [];
renderWatchlists();

// Re-fetch after background sync completes to pick up LTP and record count
setTimeout(() => selectWatchlist(watchlistId), 5000);
} catch (e) {
showToast(e.message || 'Failed to add stock', 'error');
}
}

// ─── Add to Portfolio Modal ─────────────────────────────────────────────────

function openPortfolioModal(stockId, symbol, name) {
document.getElementById('portfolioStockId').value = stockId;
document.getElementById('portfolioSymbol').value = symbol;
document.getElementById('portfolioName').value = name;
document.getElementById('portfolioQty').value = '';
document.getElementById('portfolioAvgPrice').value = '';
// Load portfolios into selector
const sel = document.getElementById('portfolioTargetSelector');
if (sel) {
getPortfolios().then(res => {
const portfolios = res.data || [];
sel.innerHTML = portfolios.map(p =>
`<option value="${p.id}">${escHtml(p.name)}${p.default ? ' (Default)' : ''}</option>`
).join('');
}).catch(() => {
sel.innerHTML = '<option value="">No portfolios available</option>';
});
}
openModal('portfolioModal');
}

function closePortfolioModal() {
closeModal('portfolioModal');
}

async function confirmAddToPortfolio() {
const stockId = parseInt(document.getElementById('portfolioStockId').value);
const symbol = document.getElementById('portfolioSymbol').value;
const qty = document.getElementById('portfolioQty').value;
const avgPrice = document.getElementById('portfolioAvgPrice').value;
const portfolioId = parseInt(document.getElementById('portfolioTargetSelector').value);
if (!portfolioId) {
showToast('Please select a target portfolio', 'error');
return;
}
if (!qty || parseInt(qty) <= 0) {
showToast('Quantity must be greater than 0', 'error');
return;
}
if (!avgPrice || parseFloat(avgPrice) < 0) {
showToast('Avg price must be 0 or more', 'error');
return;
}
try {
await addHolding(portfolioId, stockId, parseInt(qty), parseFloat(avgPrice));

showToast(symbol + ' added to portfolio', 'success');
closePortfolioModal();
if (selectedWatchlistId) {
await selectWatchlist(selectedWatchlistId);
}
} catch (e) {
showToast(e.message || 'Failed to add to portfolio', 'error');
}
}

// ─── Initialization ─────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
loadAll();
});

async function loadAll() {
try {
const [watchlistsRes, stocksRes] = await Promise.all([
getWatchlists(),
getAllStocks()
]);
allWatchlists = watchlistsRes.data || [];
allStocks = stocksRes.data || [];
renderWatchlists();
} catch (e) {
console.error('Error loading watchlist data:', e);
showToast('Failed to load watchlists', 'error');
}
}

// ─── Render Watchlist Cards ────────────────────────────────────────────────

function renderWatchlists() {
const grid = document.getElementById('watchlistGrid');
const empty = document.getElementById('emptyState');

if (!allWatchlists.length) {
grid.innerHTML = '';
empty.classList.remove('hidden');
document.getElementById('watchlistDetail').classList.add('hidden');
return;
}

empty.classList.add('hidden');

grid.innerHTML = allWatchlists.map(wl => {
const count = wl.itemCount || 0;
return `<div class="card watchlist-card rounded-xl p-6 shadow-lg cursor-pointer" onclick="selectWatchlist(${wl.id})">
<div class="flex items-start justify-between mb-4">
<div>
<h3 class="text-lg font-bold">${escHtml(wl.name)}</h3>
${wl.description ? `<p class="text-sm text-secondary mt-1">${escHtml(wl.description)}</p>` : ''}
</div>
<span class="bg-blue-600 text-white text-xs font-bold px-3 py-1 rounded-full">${count}</span>
</div>
<div class="flex justify-between items-center mt-4 pt-3 border-t border-gray-700">
<span class="text-xs text-secondary">
<i class="far fa-calendar-alt mr-1"></i>${formatDate(wl.createdAt)}
</span>
<div class="flex gap-2">
<button onclick="event.stopPropagation(); openEditModal(${wl.id})"
class="text-sm text-blue-400 hover:text-blue-300 transition-colors" title="Rename">
<i class="fas fa-edit"></i>
</button>
<button onclick="event.stopPropagation(); openDeleteModal(${wl.id}, '${escHtml(wl.name)}')"
class="text-sm text-red-400 hover:text-red-300 transition-colors" title="Delete">
<i class="fas fa-trash"></i>
</button>
</div>
</div>
</div>`;
}).join('');
}

// ─── Select Watchlist (show detail) ────────────────────────────────────────

async function selectWatchlist(id) {
selectedWatchlistId = id;
const detail = document.getElementById('watchlistDetail');
detail.classList.remove('hidden');

const wl = allWatchlists.find(w => w.id === id);
document.getElementById('detailWatchlistName').textContent = wl ? wl.name : 'Watchlist';
document.getElementById('detailWatchlistDesc').textContent = wl && wl.description ? wl.description : '';
document.getElementById('detailTitle').scrollIntoView({ behavior: 'smooth' });

try {
const res = await getWatchlistItems(id);
const detailData = res.data;
const stocks = detailData.stocks || [];

// Fetch signals for all stocks in parallel
let signalsMap = {};
if (stocks.length > 0) {
const signalResults = await Promise.all(
stocks.map(s => getStockSignal(s.id).catch(() => null))
);
signalResults.forEach((result, idx) => {
if (result && result.data) signalsMap[stocks[idx].id] = result.data;
});
}

renderDetailStocks(stocks, signalsMap);
renderSectorChart(stocks);
} catch (e) {
console.error('Error loading watchlist items:', e);
document.getElementById('detailTableBody').innerHTML =
'<tr><td colspan="8" class="text-center py-4 text-red-500">Failed to load stocks</td></tr>';
}
}

function closeDetail() {
document.getElementById('watchlistDetail').classList.add('hidden');
document.getElementById('sectorChartSection').classList.add('hidden');
if (_sectorChart) { _sectorChart.destroy(); _sectorChart = null; }
selectedWatchlistId = null;
}

function renderDetailStocks(stocks, signalsMap = {}) {
const tbody = document.getElementById('detailTableBody');
if (!stocks || !stocks.length) {
tbody.innerHTML = '<tr><td colspan="8" class="text-center py-8 text-secondary">No stocks in this watchlist. Add stocks to start tracking.</td></tr>';
return;
}

tbody.innerHTML = stocks.map(s => {
const ltpStr = s.lastTradedPrice != null ? fmtPrice(s.lastTradedPrice) : '--';
const signalHtml = renderSignalBadge(signalsMap[s.id]);
const addedStr = s.addedAt ? formatDate(s.addedAt) : '--';
const records = s.recordCount != null ? s.recordCount + ' days' : '0';
const recordsCls = s.recordCount > 0 ? 'text-green-400' : 'text-secondary';
return `<tr class="stock-row border-b border-gray-700">
<td><a href="stock-detail.html?id=${s.id}" class="font-bold hover:text-blue-400 transition-colors">${escHtml(s.symbol)}</a></td>
<td>${escHtml(s.name || '--')}</td>
<td><span class="badge badge-neutral">${escHtml(s.sector || '--')}</span></td>
<td class="text-right">${ltpStr}</td>
<td class="text-center">${signalHtml}</td>
<td class="text-center text-xs text-secondary">${addedStr}</td>
<td class="text-center text-xs ${recordsCls}">${records}</td>
<td class="text-center">
<button onclick="openPortfolioModal(${s.id}, '${escHtml(s.symbol)}', '${escHtml(s.name || s.symbol)}')" class="text-green-400 hover:text-green-300 transition-colors mr-2" title="Add to Portfolio">
<i class="fas fa-shopping-cart"></i>
</button>
<button onclick="toggleSyncDropdown(event, ${s.id})"
data-sync-stock="${s.id}"
class="text-blue-400 hover:text-blue-300 transition-colors mr-2" title="Sync History">
<i class="fas fa-sync-alt"></i>
</button>
<button onclick="removeStockFromDetail(${s.id}, '${escHtml(s.symbol)}')" class="text-red-400 hover:text-red-300 transition-colors" title="Remove from watchlist">
<i class="fas fa-times"></i>
</button>
</td>
</tr>`;
}).join('');
}

function renderSectorChart(stocks) {
const section = document.getElementById('sectorChartSection');
if (!stocks || !stocks.length) {
section.classList.add('hidden');
if (_sectorChart) { _sectorChart.destroy(); _sectorChart = null; }
return;
}

// Group stocks by sector
const sectorCounts = {};
stocks.forEach(s => {
const sector = s.sector || 'Other';
sectorCounts[sector] = (sectorCounts[sector] || 0) + 1;
});

const sectors = Object.entries(sectorCounts)
.sort((a, b) => b[1] - a[1]);
const labels = sectors.map(s => s[0]);
const data = sectors.map(s => s[1]);
const colors = generateColors(labels.length);

section.classList.remove('hidden');

// Destroy previous chart
if (_sectorChart) { _sectorChart.destroy(); _sectorChart = null; }

const canvas = document.getElementById('watchlistSectorChart');
const ctx = canvas.getContext('2d');

_sectorChart = new Chart(ctx, {
type: 'doughnut',
data: {
labels: labels,
datasets: [{
data: data,
backgroundColor: colors,
borderColor: 'rgba(30, 41, 59, 0.8)',
borderWidth: 2,
hoverOffset: 8
}]
},
options: {
responsive: true,
maintainAspectRatio: false,
cutout: '60%',
plugins: {
legend: {
position: 'right',
labels: {
color: '#9ca3af',
padding: 12,
usePointStyle: true,
pointStyle: 'circle',
font: { size: 12 }
}
},
tooltip: {
callbacks: {
label: function(ctx) {
const total = ctx.dataset.data.reduce((a, b) => a + b, 0);
const pct = ((ctx.raw / total) * 100).toFixed(1);
return ctx.label + ': ' + ctx.raw + ' stock' + (ctx.raw > 1 ? 's' : '') + ' (' + pct + '%)';
}
}
}
}
}
});
}

// ─── CRUD Operations ───────────────────────────────────────────────────────

function openCreateModal() {
document.getElementById('modalTitle').textContent = 'Create Watchlist';
document.getElementById('modalSubmitBtn').textContent = 'Create';
document.getElementById('editWatchlistId').value = '';
document.getElementById('wlName').value = '';
document.getElementById('wlDescription').value = '';
openModal('watchlistModal');
}

function openEditModal(id) {
const wl = allWatchlists.find(w => w.id === id);
if (!wl) return;
document.getElementById('modalTitle').textContent = 'Rename Watchlist';
document.getElementById('modalSubmitBtn').textContent = 'Save';
document.getElementById('editWatchlistId').value = id;
document.getElementById('wlName').value = wl.name;
document.getElementById('wlDescription').value = wl.description || '';
openModal('watchlistModal');
}

async function saveWatchlist(event) {
event.preventDefault();
const id = document.getElementById('editWatchlistId').value;
const name = document.getElementById('wlName').value.trim();
const description = document.getElementById('wlDescription').value.trim() || null;

if (!name) {
showToast('Name is required', 'error');
return;
}

try {
if (id) {
await updateWatchlist(parseInt(id), name, description);
showToast('Watchlist updated', 'success');
} else {
await createWatchlist(name, description);
showToast('Watchlist created', 'success');
}
closeModal('watchlistModal');
await loadAll();
} catch (e) {
showToast(e.message || 'Failed to save watchlist', 'error');
}
}

function openDeleteModal(id, name) {
document.getElementById('deleteWatchlistId').value = id;
document.getElementById('deleteWlName').textContent = name;
openModal('deleteModal');
}

async function confirmDelete() {
const id = parseInt(document.getElementById('deleteWatchlistId').value);
try {
await deleteWatchlist(id);
showToast('Watchlist deleted', 'success');
closeModal('deleteModal');
if (selectedWatchlistId === id) {
closeDetail();
}
await loadAll();
} catch (e) {
showToast(e.message || 'Failed to delete watchlist', 'error');
}
}

// ─── Add Stock to Watchlist ────────────────────────────────────────────────

function openAddStockModal() {
if (!selectedWatchlistId) {
showToast('Please select a watchlist first', 'error');
return;
}
_selectedStock = null;
document.getElementById('addStockWatchlistId').value = selectedWatchlistId;
document.getElementById('addStockSearch').value = '';
document.getElementById('selectedStockFields').classList.add('hidden');
document.getElementById('stockInWatchlistWarning').classList.add('hidden');
document.getElementById('addStockBtn').disabled = true;

// Show local stocks as initial suggestions
displaySearchResultsWatchlist(allStocks.slice(0, 20).map(s => ({
symbol: s.symbol,
name: s.name,
exchange: '',
sector: s.sector || '',
industry: '',
quoteType: 'EQUITY',
isYahooFinance: true
})));

openModal('addStockModal');
document.getElementById('addStockSearch').focus();
}

// ─── Remove Stock ──────────────────────────────────────────────────────────

async function removeStockFromDetail(stockId, symbol) {
if (!selectedWatchlistId) return;
if (!confirm(`Remove ${symbol} from this watchlist?`)) return;

try {
await removeStockFromWatchlist(selectedWatchlistId, stockId);
showToast(`${symbol} removed from watchlist`, 'success');
await selectWatchlist(selectedWatchlistId);
// Refresh watchlist cards
const res = await getWatchlists();
allWatchlists = res.data || [];
renderWatchlists();
} catch (e) {
showToast(e.message || 'Failed to remove stock', 'error');
}
}

// ─── Sync History ──────────────────────────────────────────────────────────

function toggleSyncAllDropdown() {
const dd = document.getElementById('syncAllDropdown');
dd.classList.toggle('hidden');
// Close on outside click
document.addEventListener('click', function handler(e) {
if (!document.getElementById('syncAllDropdownWrapper').contains(e.target)) {
dd.classList.add('hidden');
document.removeEventListener('click', handler);
}
});
}

function toggleSyncDropdown(event, stockId) {
event.stopPropagation();
// Remove any existing floating sync menu
const existing = document.getElementById('syncMenuFloating');
if (existing) existing.remove();

// Calculate position from the clicked button
const rect = event.currentTarget.getBoundingClientRect();

// Build the dropdown menu with fixed width
const menu = document.createElement('div');
menu.id = 'syncMenuFloating';
menu.className = 'fixed bg-gray-800 border border-gray-600 rounded-lg shadow-lg z-50 py-1';
menu.style.top = (rect.bottom + 4) + 'px';
menu.style.left = rect.left + 'px';
menu.style.width = '110px';

const days = [30, 60, 90, 180, 365, 730];
days.forEach(d => {
const btn = document.createElement('button');
btn.className = 'w-full text-left px-3 py-1.5 hover:bg-gray-700 text-xs' + (d === 730 ? ' font-semibold' : '');
btn.textContent = d + ' Days';
btn.onclick = (e) => {
e.stopPropagation();
menu.remove();
syncSingleStockWithDays(stockId, d);
};
menu.appendChild(btn);
});

document.body.appendChild(menu);

// Close on outside click
setTimeout(() => {
document.addEventListener('click', function handler(e) {
if (!menu.contains(e.target) && e.target !== event.currentTarget) {
menu.remove();
document.removeEventListener('click', handler);
}
});
}, 0);
}

async function syncWatchlistHistoryWithDays(days) {
document.getElementById('syncAllDropdown').classList.add('hidden');
if (!selectedWatchlistId) return;

const btn = document.querySelector('button[onclick*="toggleSyncAllDropdown"]');
const originalHtml = btn.innerHTML;
btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Syncing...';
btn.disabled = true;

try {
const result = await syncWatchlistHistory(selectedWatchlistId, days);
const synced = result.data.synced || 0;
const failed = result.data.failed || 0;
if (failed > 0) {
showToast(`Synced ${synced} stocks, ${failed} failed (${days} days)`, 'error');
} else {
showToast(`History synced for ${synced} stocks (${days} days)`, 'success');
}
await selectWatchlist(selectedWatchlistId);
} catch (e) {
showToast(e.message || 'Sync failed', 'error');
} finally {
btn.innerHTML = originalHtml;
btn.disabled = false;
}
}

async function syncSingleStockWithDays(stockId, days) {
// Find the sync button by data attribute
const btn = document.querySelector(`button[data-sync-stock="${stockId}"]`);
if (!btn) return;

const originalHtml = btn.innerHTML;
btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
btn.disabled = true;

try {
await syncStockHistory(stockId, days);
showToast(`History synced (${days} days)`, 'success');
if (selectedWatchlistId) {
await selectWatchlist(selectedWatchlistId);
}
} catch (e) {
showToast(e.message || 'Sync failed', 'error');
} finally {
btn.innerHTML = originalHtml;
btn.disabled = false;
}
}

// ─── Helpers ───────────────────────────────────────────────────────────────

function escHtml(str) {
if (!str) return '';
return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
.replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

function formatDate(dateStr) {
if (!dateStr) return '';
const d = new Date(dateStr);
return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function fmtPrice(val) {
if (val == null || isNaN(val)) return '--';
return '₹' + Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ─── Stock Search Autocomplete (Watchlist Add Stock) ────────────────────────

let searchTimeoutWatchlist = null;

async function debounceSearchStocksWatchlist(query, delay) {
if (searchTimeoutWatchlist !== null) {
clearTimeout(searchTimeoutWatchlist);
}
searchTimeoutWatchlist = setTimeout(() => {
searchStocksWatchlist(query);
}, delay);
}

async function searchStocksWatchlist(query) {
const trimmedQuery = query.trim();

// If less than 3 chars, show local stocks as suggestions
if (trimmedQuery.length < 3) {
const localMatches = allStocks.filter(s =>
s.symbol.toLowerCase().includes(trimmedQuery.toLowerCase()) ||
(s.name || '').toLowerCase().includes(trimmedQuery.toLowerCase())
).slice(0, 10).map(s => ({
symbol: s.symbol,
name: s.name,
exchange: '',
sector: s.sector || '',
industry: '',
quoteType: 'EQUITY',
isYahooFinance: true
}));
displaySearchResultsWatchlist(localMatches);
return;
}

// Search both local stocks AND Yahoo Finance in parallel
const localMatches = allStocks.filter(s =>
s.symbol.toLowerCase().includes(trimmedQuery.toLowerCase()) ||
(s.name || '').toLowerCase().includes(trimmedQuery.toLowerCase())
).map(s => ({
symbol: s.symbol,
name: s.name,
exchange: '',
sector: s.sector || '',
industry: '',
quoteType: 'EQUITY',
isYahooFinance: true,
isLocal: true
}));

let yahooResults = [];
try {
const response = await searchStocksByName(trimmedQuery, 8);
yahooResults = (response.data || []).map(r => ({ ...r, isYahoo: true }));
} catch (error) {
console.error('Yahoo search error:', error);
}

// Merge: local first (marked as local), then Yahoo results not already local
const seenSymbols = new Set(localMatches.map(s => s.symbol.toUpperCase()));
const merged = [
...localMatches.map(s => ({ ...s, isLocal: true })),
...yahooResults.filter(r => !seenSymbols.has(r.symbol.toUpperCase())).map(r => ({ ...r, isYahoo: true }))
];

displaySearchResultsWatchlist(merged.slice(0, 12));
}

function displaySearchResultsWatchlist(results) {
const container = document.getElementById('qcSearchResults');
if (!results.length) {
container.innerHTML = '<div class="px-4 py-3 text-sm text-secondary text-center">No stocks found</div>';
container.classList.remove('hidden');
return;
}

container.innerHTML = results.map(stock => {
const stockJson = JSON.stringify(stock).replace(/'/g, "\\'").replace(/"/g, '&quot;');
const localBadge = stock.isLocal ? '<span class="text-xs bg-blue-600/20 text-blue-400 px-1.5 py-0.5 rounded ml-2">Local</span>' : '';
const exchangeBadge = stock.exchange ? `<span class="text-xs bg-gray-600 text-gray-300 px-1.5 py-0.5 rounded">${escHtml(stock.exchange)}</span>` : '';
const quoteBadge = stock.quoteType && stock.quoteType !== 'EQUITY' ? `<span class="text-xs bg-purple-600/20 text-purple-400 px-1.5 py-0.5 rounded ml-1">${escHtml(stock.quoteType)}</span>` : '';
const detailLine = [stock.sector, stock.industry].filter(Boolean).map(escHtml).join(' • ');

return `
<div class="px-4 py-3 hover:bg-gray-700/50 cursor-pointer border-b border-gray-700 last:border-0 transition-colors"
onclick='selectStockFromSearchWatchlist(${stockJson})'>
<div class="flex items-center justify-between">
<div class="flex items-center">
<span class="font-bold text-white">${escHtml(stock.symbol)}</span>
${localBadge}
</div>
<div class="flex items-center gap-1">
${exchangeBadge}
${quoteBadge}
</div>
</div>
<div class="text-sm text-gray-400 mt-0.5">${escHtml(stock.name)}</div>
${detailLine ? `<div class="text-xs text-gray-500 mt-1">${detailLine}</div>` : ''}
</div>`;
}).join('');

container.classList.remove('hidden');
}

function hideSearchResultsWatchlist() {
const container = document.getElementById('qcSearchResults');
if (container) {
container.classList.add('hidden');
container.innerHTML = '';
}
}

function selectStockFromSearchWatchlist(stock) {
_selectedStock = stock;

// Show the fields panel
document.getElementById('selectedStockFields').classList.remove('hidden');

// Fill read-only fields
document.getElementById('qcSymbol').value = stock.symbol || '';
document.getElementById('qcName').value = stock.name || '';
document.getElementById('qcExchange').value = stock.exchange || '';

// Auto-select sector — if Yahoo search didn't return sector, try local data
let sector = stock.sector;
if (!sector) {
const local = allStocks.find(s => s.symbol.toUpperCase() === (stock.symbol || '').toUpperCase());
if (local && local.sector) sector = local.sector;
}

const sectorSelect = document.getElementById('qcSector');
sectorSelect.value = ''; // Reset first
if (sector) {
const yahooSector = sector.toLowerCase();
// 1. Exact match
for (let i = 0; i < sectorSelect.options.length; i++) {
if (sectorSelect.options[i].value.toLowerCase() === yahooSector) {
sectorSelect.value = sectorSelect.options[i].value;
break;
}
}
// 2. Partial match: Yahoo "Technology Services" → "Technology", "Health Services" → "Healthcare"
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
// 3. Fallback — if sector from Yahoo doesn't match any known mapping, add it as a new option
if (!sectorSelect.value && sector) {
var opt = document.createElement('option');
opt.value = sector;
opt.textContent = sector;
sectorSelect.appendChild(opt);
sectorSelect.value = sector;
}
}

// Hide search results
hideSearchResultsWatchlist();

// Show selected stock name in search input
document.getElementById('addStockSearch').value = stock.name + ' (' + stock.symbol + ')';

// Enable add button
document.getElementById('addStockBtn').disabled = false;

// Check if already in watchlist
checkIfInWatchlist(stock.symbol);
}

function showToast(msg, type) {
const existing = document.querySelector('.toast');
if (existing) existing.remove();

const toast = document.createElement('div');
toast.className = 'toast fixed bottom-4 right-4 px-6 py-3 rounded-lg shadow-lg z-50 text-white ' +
(type === 'success' ? 'bg-green-600' : 'bg-red-600');
toast.innerHTML = '<i class="fas ' + (type === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle') + ' mr-2"></i>' + msg;
document.body.appendChild(toast);
setTimeout(() => toast.remove(), 3000);
}
