// ─── Watchlist Integration ───────────────────────────────────────────────────
// Depends on: modules/state.js, api.js

// Close watchlist dropdown when clicking outside
document.addEventListener('click', function(event) {
    const container = document.getElementById('watchlistBtnContainer');
    const dropdown = document.getElementById('watchlistDropdown');
    if (container && dropdown && !container.contains(event.target)) {
        dropdown.classList.add('hidden');
    }
});

async function toggleWatchlistDropdown() {
    const dropdown = document.getElementById('watchlistDropdown');
    if (!dropdown) return;

    if (!dropdown.classList.contains('hidden')) {
        dropdown.classList.add('hidden');
        return;
    }

    dropdown.classList.remove('hidden');
    await loadWatchlistDropdown();
}

async function loadWatchlistDropdown() {
    const container = document.getElementById('watchlistDropdownItems');
    if (!container) return;

    try {
        const [watchlistsRes, membershipRes] = await Promise.all([
            getWatchlists(),
            _stockId ? getWatchlistsForStock(_stockId) : Promise.resolve({ data: [] })
        ]);

        const watchlists = watchlistsRes.data || [];
        const memberWatchlistIds = new Set(
            (membershipRes.data || []).map(w => w.id)
        );

        if (!watchlists.length) {
            container.innerHTML = '<p class="text-xs text-secondary text-center py-2">No watchlists yet.<br><a href="watchlist.html" class="text-blue-400">Create one</a></p>';
            return;
        }

        container.innerHTML = watchlists.map(wl => {
            const isMember = memberWatchlistIds.has(wl.id);
            return `<label class="flex items-center gap-3 px-3 py-2 hover:bg-gray-700/50 rounded-lg cursor-pointer text-sm">
                <input type="checkbox" ${isMember ? 'checked' : ''}
                       onchange="toggleWatchlistMembership(${wl.id}, '${wl.name.replace(/'/g, "\\'")}', this.checked)"
                       style="accent-color: #3b82f6; width: 16px; height: 16px;">
                <span class="flex-1">${wl.name}</span>
                <span class="text-xs text-secondary">${wl.itemCount || 0}</span>
            </label>`;
        }).join('');
    } catch (e) {
        console.error('Error loading watchlist dropdown:', e);
        container.innerHTML = '<p class="text-xs text-red-400 text-center py-2">Failed to load</p>';
    }
}

async function toggleWatchlistMembership(watchlistId, watchlistName, add) {
    if (!_stockId) return;

    try {
        if (add) {
            await addStockToWatchlist(watchlistId, _stockId);
            showWatchlistToast('Added to "' + watchlistName + '"', 'success');
        } else {
            await removeStockFromWatchlist(watchlistId, _stockId);
            showWatchlistToast('Removed from "' + watchlistName + '"', 'info');
        }
    } catch (e) {
        console.error('Watchlist toggle error:', e);
        showWatchlistToast(e.message || 'Failed to update watchlist', 'error');
        await loadWatchlistDropdown();
    }
}

function showWatchlistToast(msg, type) {
    const existing = document.querySelector('.wl-toast');
    if (existing) existing.remove();

    const toast = document.createElement('div');
    toast.className = 'wl-toast fixed bottom-4 right-4 px-6 py-3 rounded-lg shadow-lg z-50 text-white ' +
        (type === 'success' ? 'bg-green-600' : type === 'error' ? 'bg-red-600' : 'bg-gray-600');
    toast.innerHTML = '<i class="fas ' + (type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle') + ' mr-2"></i>' + msg;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}
