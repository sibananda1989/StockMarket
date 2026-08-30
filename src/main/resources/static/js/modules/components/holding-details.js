// ─── Holding Details ─────────────────────────────────────────────────────────
// Depends on: modules/state.js, api.js

async function updateHoldingDetails() {
    const container = document.getElementById('holdingDetails');
    const content = document.getElementById('holdingDetailsContent');
    if (!container || !content) return;

    const qty = _stockData?.quantity;
    if (!qty || parseFloat(qty) <= 0) {
        container.hidden = true;
        return;
    }

    container.hidden = false;

    let txns = [];
    try {
        const res = await getAllTransactionsByStock(parseInt(_stockId));
        if (res && res.status === 'success' && Array.isArray(res.data)) txns = res.data;
    } catch (e) {
        console.error('[HoldingDetails] Failed to load transactions:', e);
    }

    if (!txns.length) {
        content.innerHTML = '<tr><td colspan="8" class="text-center py-8 text-secondary">No transactions recorded for this stock yet.</td></tr>';
        return;
    }

    content.innerHTML = txns.map(tx => {
        const isBuy = tx.type === 'BUY';
        const value = (tx.quantity || 0) * (tx.price || 0);
        const badgeCls = isBuy
            ? 'bg-green-900/30 border border-green-700/50 text-green-400'
            : 'bg-red-900/30 border border-red-700/50 text-red-400';
        const badgeIcon = isBuy ? 'fa-arrow-down' : 'fa-arrow-up';
        return `
            <tr class="border-b border-gray-700 hover:bg-gray-800 transition-colors">
                <td class="px-4 py-3 whitespace-nowrap">${tx.transactionDate || '--'}</td>
                <td class="px-4 py-3">
                    <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold ${badgeCls}">
                        <i class="fas ${badgeIcon}"></i>${isBuy ? 'BUY' : 'SELL'}
                    </span>
                </td>
                <td class="px-4 py-3 text-right">${Number(tx.quantity || 0).toLocaleString('en-IN')}</td>
                <td class="px-4 py-3 text-right">${fmtPrice(tx.price)}</td>
                <td class="px-4 py-3 text-right">${fmtPrice(tx.fees)}</td>
                <td class="px-4 py-3 text-right font-medium">${fmtPrice(value)}</td>
                <td class="px-4 py-3 text-right ${tx.realizedPnl != null ? pnlColor(tx.realizedPnl) : 'text-secondary'}">${tx.realizedPnl != null ? fmtPrice(tx.realizedPnl) : '--'}</td>
                <td class="px-4 py-3 text-secondary max-w-[200px] truncate" title="${escHtml(tx.notes || '')}">${escHtml(tx.notes || '')}</td>
            </tr>`;
    }).join('');
}
