document.addEventListener('DOMContentLoaded', function() {
    loadStocks();
    setDefaultDate();
    document.getElementById('priceForm').addEventListener('submit', handlePriceSubmit);
});

async function loadStocks() {
    try {
        const response = await getAllStocks();
        const stockSelect = document.getElementById('stockSelect');
        
        stockSelect.innerHTML = '<option value="">Select Stock</option>';
        
        response.data.forEach(stock => {
            const option = document.createElement('option');
            option.value = stock.id;
            option.textContent = `${stock.symbol} - ${stock.name}`;
            stockSelect.appendChild(option);
        });
    } catch (error) {
        console.error('Error loading stocks:', error);
        const stockSelect = document.getElementById('stockSelect');
        if (stockSelect) {
            stockSelect.innerHTML = '<option value="">Error loading stocks</option>';
            document.getElementById('submitBtn') && (document.getElementById('submitBtn').disabled = true);
        }
    }
}

function setDefaultDate() {
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('priceDate').value = today;
}

async function handlePriceSubmit(event) {
    event.preventDefault();
    
    const stockId = document.getElementById('stockSelect').value;
    const priceDate = document.getElementById('priceDate').value;
    const closingPrice = document.getElementById('closingPrice').value;
    const openingPrice = document.getElementById('openingPrice').value || null;
    const highPrice = document.getElementById('highPrice').value || null;
    const lowPrice = document.getElementById('lowPrice').value || null;
    const volume = document.getElementById('volume').value || null;
    
   op if (!stockId || !priceDate || !closingPrice) {
        alert('Please fill in all required fields');
        return;
    }
    
    const priceData = {
        stockId: parseInt(stockId),
        priceDate: priceDate,
        closingPrice: parseFloat(closingPrice),
        openingPrice: openingPrice ? parseFloat(openingPrice) : null,
        highPrice: highPrice ? parseFloat(highPrice) : null,
        lowPrice: lowPrice ? parseFloat(lowPrice) : null,
        volume: volume ? parseInt(volume) : null
    };
    
    try {
        await savePrice(priceData);
        
        // Trigger RSI calculation for this stock
        await calculateRsi(parseInt(stockId));
        
        showSuccessAlert();
        document.getElementById('priceForm').reset();
        setDefaultDate();
    } catch (error) {
        console.error('Error saving price:', error);
        showErrorAlert(error.message);
    }
}

function showSuccessAlert() {
    const alert = document.getElementById('successAlert');
    alert.style.display = 'block';
    setTimeout(() => {
        alert.style.display = 'none';
    }, 3000);
}

function showErrorAlert(message) {
    const alert = document.getElementById('errorAlert');
    alert.textContent = 'Error saving price: ' + message;
    alert.style.display = 'block';
    setTimeout(() => {
        alert.style.display = 'none';
    }, 5000);
}
