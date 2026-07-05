// Global variables
let stockChart = null;
let currentData = [];
let currentPage = 1;
const rowsPerPage = 20;
let sortDirection = {};
let currentSymbol = '';
let currentSummary = null;

// Debounce function
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    loadRecentSearches();
    
    // Check if symbol is in URL parameter
    const params = new URLSearchParams(window.location.search);
    const symbolFromUrl = params.get('symbol');
    
    if (symbolFromUrl) {
        document.getElementById('symbolInput').value = symbolFromUrl.toUpperCase();
        fetchStockHistory();
    }
    
    // Add enter key listener for symbol input
    document.getElementById('symbolInput').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            fetchStockHistory();
        }
    });
});

// Quick select symbol
function quickSelect(symbol) {
    document.getElementById('symbolInput').value = symbol;
    fetchStockHistory();
}

// Fetch stock history from API
async function fetchStockHistory() {
    const symbol = document.getElementById('symbolInput').value.trim().toUpperCase();
    const days = document.getElementById('daysSelect').value;

    if (!symbol) {
        showError('Please enter a stock symbol');
        return;
    }

    showLoading();
    console.log('Fetching stock history for:', symbol, 'days:', days);

    try {
        const response = await fetch(`/api/stocks/history?symbol=${symbol}&days=${days}`);
        console.log('Response status:', response.status);

        const data = await response.json();
        console.log('Response data:', data);

        if (!response.ok) {
            throw new Error(data.message || 'Failed to fetch stock data');
        }

        if (!data.data || !data.data.history || data.data.history.length === 0) {
            throw new Error('No data found for this symbol');
        }

        currentSymbol = symbol;
        currentSummary = data.data;
        currentData = data.data.history;
        displayData(data.data);
        saveToRecentSearches(symbol);
        
        // Show save button
        document.getElementById('saveBtn').classList.remove('hidden');

    } catch (error) {
        console.error('Error fetching stock history:', error);
        showError(error.message);
    }
}

// Refresh data
function refreshData() {
    if (document.getElementById('symbolInput').value.trim()) {
        fetchStockHistory();
    }
}

// Save to database
async function saveToDatabase() {
    if (!currentSymbol || !currentData || currentData.length === 0) {
        showError('No data to save. Please fetch stock data first.');
        return;
    }

    const saveBtn = document.getElementById('saveBtn');
    const originalText = saveBtn.innerHTML;
    saveBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Saving...';
    saveBtn.disabled = true;

    try {
        const requestBody = {
            symbol: currentSymbol,
            name: currentSymbol, // Can be enhanced with actual name from API
            sector: 'Other',
            history: currentData
        };

        const response = await fetch('/api/stocks/history/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestBody)
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Failed to save data');
        }

        alert(data.message || 'Data saved successfully to database!');
        
    } catch (error) {
        console.error('Error saving to database:', error);
        alert('Error saving data: ' + error.message);
    } finally {
        saveBtn.innerHTML = originalText;
        saveBtn.disabled = false;
    }
}

// Display data on the page
function displayData(summary) {
    hideAllStates();
    document.getElementById('contentState').classList.remove('hidden');
    
    // Update statistics cards
    document.getElementById('currentPrice').textContent = formatCurrency(summary.currentPrice);
    document.getElementById('highestPrice').textContent = formatCurrency(summary.highestPrice);
    document.getElementById('lowestPrice').textContent = formatCurrency(summary.lowestPrice);
    document.getElementById('averageClose').textContent = formatCurrency(summary.averageClose);
    
    const percentChangeEl = document.getElementById('percentChange');
    percentChangeEl.textContent = formatPercent(summary.percentChange);
    percentChangeEl.className = `text-2xl font-bold ${summary.percentChange >= 0 ? 'text-green-500' : 'text-red-500'}`;
    
    const priceChangeEl = document.getElementById('priceChange');
    priceChangeEl.textContent = formatPercent(summary.percentChange);
    priceChangeEl.className = `text-sm mt-1 ${summary.percentChange >= 0 ? 'text-green-500' : 'text-red-500'}`;
    
    document.getElementById('chartSymbol').textContent = summary.symbol;
    
    // Update chart
    updateChart(summary.history);
    
    // Update table
    currentPage = 1;
    renderTable(summary.history);
}

// Update chart
function updateChart(history) {
    const ctx = document.getElementById('stockChart').getContext('2d');
    
    // Destroy existing chart if it exists
    if (stockChart) {
        stockChart.destroy();
    }
    
    const labels = history.map(item => formatDate(item.date));
    const closePrices = history.map(item => item.close);
    
    const gradient = ctx.createLinearGradient(0, 0, 0, 400);
    gradient.addColorStop(0, 'rgba(59, 130, 246, 0.5)');
    gradient.addColorStop(1, 'rgba(59, 130, 246, 0.0)');
    
    stockChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Close Price',
                data: closePrices,
                borderColor: 'rgb(59, 130, 246)',
                backgroundColor: gradient,
                borderWidth: 2,
                fill: true,
                tension: 0.4,
                pointRadius: 0,
                pointHoverRadius: 6,
                pointHoverBackgroundColor: 'rgb(59, 130, 246)',
                pointHoverBorderColor: '#fff',
                pointHoverBorderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                intersect: false,
                mode: 'index'
            },
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    backgroundColor: 'rgba(15, 23, 42, 0.9)',
                    titleColor: '#f1f5f9',
                    bodyColor: '#f1f5f9',
                    borderColor: '#334155',
                    borderWidth: 1,
                    padding: 12,
                    displayColors: false,
                    callbacks: {
                        label: function(context) {
                            return `Close: ${formatCurrency(context.parsed.y)}`;
                        }
                    }
                }
            },
            scales: {
                x: {
                    grid: {
                        color: 'rgba(51, 65, 85, 0.5)'
                    },
                    ticks: {
                        color: '#94a3b8',
                        maxTicksLimit: 10
                    }
                },
                y: {
                    grid: {
                        color: 'rgba(51, 65, 85, 0.5)'
                    },
                    ticks: {
                        color: '#94a3b8',
                        callback: function(value) {
                            return formatCurrency(value);
                        }
                    }
                }
            }
        }
    });
}

// Render table
function renderTable(data) {
    const tbody = document.getElementById('historyTableBody');
    tbody.innerHTML = '';
    
    const startIndex = (currentPage - 1) * rowsPerPage;
    const endIndex = startIndex + rowsPerPage;
    const pageData = data.slice(startIndex, endIndex);
    
    pageData.forEach(item => {
        const row = document.createElement('tr');
        row.className = 'border-b border-gray-700 hover:bg-gray-800 transition-colors';
        
        const change = item.close - item.open;
        const changeClass = change >= 0 ? 'text-green-500' : 'text-red-500';
        
        row.innerHTML = `
            <td class="px-4 py-3">${formatDate(item.date)}</td>
            <td class="px-4 py-3 text-right">${formatCurrency(item.open)}</td>
            <td class="px-4 py-3 text-right text-green-500">${formatCurrency(item.high)}</td>
            <td class="px-4 py-3 text-right text-red-500">${formatCurrency(item.low)}</td>
            <td class="px-4 py-3 text-right ${changeClass}">${formatCurrency(item.close)}</td>
            <td class="px-4 py-3 text-right">${formatVolume(item.volume)}</td>
        `;
        
        tbody.appendChild(row);
    });
    
    // Update pagination info
    const totalPages = Math.ceil(data.length / rowsPerPage);
    document.getElementById('paginationInfo').textContent = 
        `Showing ${startIndex + 1} to ${Math.min(endIndex, data.length)} of ${data.length} entries`;
}

// Filter table
function filterTable() {
    const searchTerm = document.getElementById('tableSearch').value.toLowerCase();
    const filteredData = currentData.filter(item => 
        formatDate(item.date).toLowerCase().includes(searchTerm)
    );
    currentPage = 1;
    renderTable(filteredData);
}

// Sort table
function sortTable(columnIndex) {
    const keys = ['date', 'open', 'high', 'low', 'close', 'volume'];
    const key = keys[columnIndex];
    
    sortDirection[key] = sortDirection[key] === 'asc' ? 'desc' : 'asc';
    
    currentData.sort((a, b) => {
        let valA = a[key];
        let valB = b[key];
        
        if (key === 'date') {
            valA = new Date(valA);
            valB = new Date(valB);
        }
        
        if (sortDirection[key] === 'asc') {
            return valA > valB ? 1 : -1;
        } else {
            return valA < valB ? 1 : -1;
        }
    });
    
    renderTable(currentData);
}

// Pagination
function previousPage() {
    if (currentPage > 1) {
        currentPage--;
        renderTable(currentData);
    }
}

function nextPage() {
    const totalPages = Math.ceil(currentData.length / rowsPerPage);
    if (currentPage < totalPages) {
        currentPage++;
        renderTable(currentData);
    }
}

// Export to CSV
function exportCSV() {
    if (currentData.length === 0) {
        showError('No data to export');
        return;
    }
    
    const headers = ['Date', 'Open', 'High', 'Low', 'Close', 'Volume'];
    const rows = currentData.map(item => [
        formatDate(item.date),
        item.open.toFixed(2),
        item.high.toFixed(2),
        item.low.toFixed(2),
        item.close.toFixed(2),
        item.volume
    ]);
    
    const csvContent = [headers, ...rows]
        .map(row => row.join(','))
        .join('\n');
    
    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${document.getElementById('symbolInput').value}_history.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
}

// Recent searches
function saveToRecentSearches(symbol) {
    let recentSearches = JSON.parse(localStorage.getItem('recentStockSearches') || '[]');
    
    // Remove if already exists
    recentSearches = recentSearches.filter(s => s !== symbol);
    
    // Add to beginning
    recentSearches.unshift(symbol);
    
    // Keep only last 5
    recentSearches = recentSearches.slice(0, 5);
    
    localStorage.setItem('recentStockSearches', JSON.stringify(recentSearches));
    loadRecentSearches();
}

function loadRecentSearches() {
    const recentSearches = JSON.parse(localStorage.getItem('recentStockSearches') || '[]');
    
    if (recentSearches.length > 0) {
        document.getElementById('recentSearches').classList.remove('hidden');
        const chipsContainer = document.getElementById('recentSearchesChips');
        chipsContainer.innerHTML = recentSearches.map(symbol => `
            <button onclick="quickSelect('${symbol}')" 
                    class="px-3 py-1 bg-gray-700 hover:bg-gray-600 text-gray-200 rounded-full text-sm transition-colors">
                ${symbol}
            </button>
        `).join('');
    } else {
        document.getElementById('recentSearches').classList.add('hidden');
    }
}

// Utility functions
function formatCurrency(value) {
    return '₹' + value.toFixed(2);
}

function formatPercent(value) {
    const sign = value >= 0 ? '+' : '';
    return sign + value.toFixed(2) + '%';
}

function formatVolume(value) {
    if (value >= 10000000) {
        return (value / 10000000).toFixed(2) + ' Cr';
    } else if (value >= 100000) {
        return (value / 100000).toFixed(2) + ' L';
    } else if (value >= 1000) {
        return (value / 1000).toFixed(2) + ' K';
    }
    return value.toString();
}

function formatDate(dateStr) {
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-IN', { 
        day: '2-digit', 
        month: 'short', 
        year: 'numeric' 
    });
}

// UI state functions
function showLoading() {
    hideAllStates();
    document.getElementById('loadingState').classList.remove('hidden');
}

function showError(message) {
    hideAllStates();
    document.getElementById('errorState').classList.remove('hidden');
    document.getElementById('errorMessage').textContent = message;
}

function hideAllStates() {
    document.getElementById('loadingState').classList.add('hidden');
    document.getElementById('errorState').classList.add('hidden');
    document.getElementById('emptyState').classList.add('hidden');
    document.getElementById('contentState').classList.add('hidden');
}

// Dark/Light mode toggle (optional enhancement)
function toggleTheme() {
    document.body.classList.toggle('dark');
    document.body.classList.toggle('light');
}
