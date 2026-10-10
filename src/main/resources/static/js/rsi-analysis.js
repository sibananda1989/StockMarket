let allRsiData = [];
let sortDirection = 'asc';

document.addEventListener('DOMContentLoaded', function() {
    loadRsiData();
});

async function loadRsiData() {
    try {
        const stocksResponse = await getAllStocks();
        const stocks = stocksResponse.data;
        
        // Get RSI for each stock
        const rsiPromises = stocks.map(async stock => {
            try {
                const rsiResponse = await getLatestRsi(stock.id);
                return {
                    ...stock,
                    latestRsi: rsiResponse.data.rsi14,
                    calculationDate: rsiResponse.data.calculationDate
                };
            } catch (error) {
                return {
                    ...stock,
                    latestRsi: null,
                    calculationDate: null
                };
            }
        });
        
        allRsiData = await Promise.all(rsiPromises);
        displayRsiData(allRsiData);
        updateCounts();
    } catch (error) {
        console.error('Error loading RSI data:', error);
        document.getElementById('rsiTableBody').innerHTML = 
            '<tr><td colspan="7" class="text-center text-danger">Error loading RSI data</td></tr>';
    }
}

function displayRsiData(data) {
    const tbody = document.getElementById('rsiTableBody');
    
    if (data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center py-8">No RSI data found</td></tr>';
        return;
    }
    
    tbody.innerHTML = data.map(item => `
        <tr class="${getRsiRowClass(item.latestRsi)}">
            <td><strong>${item.symbol}</strong></td>
            <td>${item.name}</td>
            <td><span class="badge badge-neutral">${item.sector || '--'}</span></td>
            <td>
                <span class="badge ${getRsiBadgeClass(item.latestRsi)}">
                    ${item.latestRsi ? item.latestRsi.toFixed(2) : 'N/A'}
                </span>
            </td>
            <td><span class="badge ${getRsiStatusBadgeClass(item.latestRsi)}">${getRsiStatusText(item.latestRsi)}</span></td>
            <td>${item.calculationDate ? formatDate(item.calculationDate) : '--'}</td>
            <td class="text-center">
                <button class="btn-action btn-recalculate" onclick="recalculateRsi(${item.id})" title="Recalculate RSI">
                    <i class="fas fa-calculator"></i>
                </button>
                <button class="btn-action btn-view" onclick="viewStockDetail(${item.id})" title="View Stock Details">
                    <i class="fas fa-eye"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

function getRsiRowClass(rsi) {
    if (!rsi) return '';
    if (rsi < 30) return 'rsi-oversold';
    if (rsi > 70) return 'rsi-overbought';
    return 'rsi-neutral';
}

function getRsiBadgeClass(rsi) {
    if (!rsi) return 'badge-neutral';
    if (rsi < 30) return 'badge-danger';
    if (rsi > 70) return 'badge-success';
    return 'badge-warning';
}

function getRsiStatusBadgeClass(rsi) {
    if (!rsi) return 'badge-neutral';
    if (rsi < 30) return 'badge-danger';
    if (rsi > 70) return 'badge-success';
    return 'badge-info';
}

function getRsiStatusText(rsi) {
    if (!rsi) return 'N/A';
    if (rsi < 30) return 'Oversold';
    if (rsi > 70) return 'Overbought';
    return 'Neutral';
}

function formatDate(dateString) {
    if (!dateString) return '--';
    const date = new Date(dateString);
    return date.toLocaleDateString();
}

function updateCounts() {
    const oversold = allRsiData.filter(item => item.latestRsi && item.latestRsi < 30).length;
    const overbought = allRsiData.filter(item => item.latestRsi && item.latestRsi > 70).length;
    
    document.getElementById('oversoldCount').textContent = oversold;
    document.getElementById('overboughtCount').textContent = overbought;
}

function sortRsi(direction) {
    sortDirection = direction;
    
    let sorted = [...allRsiData].filter(item => item.latestRsi !== null);
    
    sorted.sort((a, b) => {
        return direction === 'asc' 
            ? a.latestRsi - b.latestRsi 
            : b.latestRsi - a.latestRsi;
    });
    
    displayRsiData(sorted);
}

async function recalculateRsi(stockId) {
    try {
        await calculateRsi(stockId);
        loadRsiData();
    } catch (error) {
        alert('Error recalculating RSI: ' + error.message);
    }
}

async function recalculateAllRsi() {
    if (!confirm('This will recalculate RSI for all stocks. Continue?')) {
        return;
    }
    
    try {
        await calculateAllRsi();
        showSuccessAlert();
        loadRsiData();
    } catch (error) {
        alert('Error recalculating RSI: ' + error.message);
    }
}

function showSuccessAlert() {
    const alert = document.getElementById('successAlert');
    alert.style.display = 'block';
    setTimeout(() => {
        alert.style.display = 'none';
    }, 3000);
}

function viewStockDetail(id) {
    window.open(`stock-detail.html?id=${id}`, '_blank', 'noopener');
}
