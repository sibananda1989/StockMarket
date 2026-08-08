// Shared Navigation Component
// Injects consistent navigation across all pages

const NAV_ITEMS = [
  { href: 'index.html', label: 'Dashboard', icon: 'fas fa-chart-pie' },
  { href: 'strategy.html', label: 'Strategy', icon: 'fas fa-chess-knight' },
  { href: 'strategy-results.html', label: 'Strategy Results', icon: 'fas fa-chart-bar' },
  { href: 'stocks.html', label: 'Stock Management', icon: 'fas fa-database' }
];

const DROPDOWN_ITEMS = [
  { href: 'watchlist-opportunities.html', label: 'Opportunities', icon: 'fas fa-bullseye' },
  { href: 'watchlist.html', label: 'Watchlists', icon: 'fas fa-eye' },
  { href: 'price-entry.html', label: 'Price Entry', icon: 'fas fa-keyboard' },
  { href: 'rsi-analysis.html', label: 'RSI Analysis', icon: 'fas fa-chart-line' },
  { href: 'stock-history.html', label: 'Stock History', icon: 'fas fa-history' },
  { href: 'history-summary.html', label: 'History Summary', icon: 'fas fa-file-alt' },
  { href: 'institutional-dashboard.html', label: 'Institutional', icon: 'fas fa-building-columns' },
  { href: 'fundamentals-screener.html', label: 'Fundamentals', icon: 'fas fa-filter' },
  { href: '#', label: 'Fill RSI Gaps', icon: 'fas fa-magic', onclick: 'handleFillRsiGapsFromNav()' }
];

// Current page detection - matches exact filename
const CURRENT_PAGE = (() => {
  const path = window.location.pathname;
  const filename = path.split('/').pop() || 'index.html';
  return filename;
})();

function createNavigation() {
  const nav = document.createElement('nav');
  nav.className = 'card rounded-xl p-4 mb-6 shadow-lg';
  
  const div = document.createElement('div');
  div.className = 'flex flex-wrap gap-4 items-center';
  
  NAV_ITEMS.forEach(item => {
    const isActive = item.href === CURRENT_PAGE;
    const a = document.createElement('a');
    a.href = item.href;
    a.className = `px-4 py-2 rounded-lg font-medium transition-colors flex items-center gap-2 ${
      isActive 
        ? 'bg-blue-600 text-white' 
        : 'hover:bg-gray-700 text-gray-300'
    }`;
    
    const icon = document.createElement('i');
    icon.className = `${item.icon} text-sm`;
    a.appendChild(icon);
    a.appendChild(document.createTextNode(item.label));
    
    div.appendChild(a);
  });

  const dropdownWrapper = document.createElement('div');
  dropdownWrapper.className = 'relative';
  
  const dropdownBtn = document.createElement('button');
  dropdownBtn.className = 'px-4 py-2 rounded-lg font-medium transition-colors flex items-center gap-2 hover:bg-gray-700 text-gray-300';
  dropdownBtn.innerHTML = '<i class="fas fa-ellipsis-h text-sm"></i><span>More</span>';
  
  const dropdownMenu = document.createElement('div');
  dropdownMenu.className = 'hidden absolute right-0 mt-2 w-48 bg-gray-800 border border-gray-600 rounded-lg shadow-lg z-50 py-1';
  
  DROPDOWN_ITEMS.forEach(item => {
    const isActive = item.href === CURRENT_PAGE;
    const a = document.createElement('a');
    a.href = item.href;
    a.className = `block px-4 py-2 text-sm transition-colors flex items-center gap-3 ${
      isActive 
        ? 'bg-blue-600 text-white' 
        : 'text-gray-300 hover:bg-gray-700'
    }`;
    
    const icon = document.createElement('i');
    icon.className = `${item.icon} text-sm`;
    a.appendChild(icon);
    a.appendChild(document.createTextNode(item.label));
    
    dropdownMenu.appendChild(a);
  });
  
  dropdownBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    dropdownMenu.classList.toggle('hidden');
  });
  
  document.addEventListener('click', (e) => {
    if (!dropdownWrapper.contains(e.target)) {
      dropdownMenu.classList.add('hidden');
    }
  });
  
  dropdownWrapper.appendChild(dropdownBtn);
  dropdownWrapper.appendChild(dropdownMenu);
  div.appendChild(dropdownWrapper);
  
  nav.appendChild(div);
  return nav;
}

function injectNavigation() {
  const newNav = createNavigation();

  // Preferred: inject into the #navigation placeholder every page provides.
  // This guarantees the nav sits at the top of the page (above hero content),
  // regardless of the header's CSS classes.
  const placeholder = document.getElementById('navigation');
  if (placeholder) {
    // Reset to a clean state so repeated injectNavigation() calls never stack navs.
    placeholder.classList.remove('mb-6');
    placeholder.innerHTML = '';
    placeholder.appendChild(newNav);
    return;
  }

  // Legacy fallback: replace an existing nav element if present.
  const existingNav = document.querySelector('nav.card.rounded-xl.p-4.mb-6.shadow-lg');
  if (existingNav) {
    existingNav.parentNode.replaceChild(newNav, existingNav);
    return;
  }

  // Last resort: try to find header and insert after it
  const header = document.querySelector('.flex.justify-between.items-center.mb-8, .flex.justify-between.items-center.mb-6');
  if (header && header.parentNode) {
    header.parentNode.insertBefore(newNav, header.nextSibling);
  }
}

// Auto-inject on DOM ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', injectNavigation);
} else {
  injectNavigation();
}

// Export for manual use
window.NavigationComponent = { injectNavigation, createNavigation, NAV_ITEMS };

// Handle Fill RSI Gaps from navigation dropdown
async function handleFillRsiGapsFromNav() {
    try {
        if (confirm('Fill missing RSI values for the last 30 days for all stocks?')) {
            const res = await fillRsiGaps();
            if (res && res.status === 'success') {
                alert('RSI gaps filled successfully!');
            } else {
                alert('Failed to fill RSI gaps: ' + (res?.message || 'Unknown error'));
            }
        }
    } catch (e) {
        console.error('Error filling RSI gaps:', e);
        alert('An error occurred while filling RSI gaps.');
    }
}

// Make it globally available
window.handleFillRsiGapsFromNav = handleFillRsiGapsFromNav;