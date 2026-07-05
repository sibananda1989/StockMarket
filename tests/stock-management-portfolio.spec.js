const { test, expect } = require('@playwright/test');

const MOCK_PORTFOLIOS = {
  success: true,
  data: [
    { id: 1, name: 'Equity', default: true },
    { id: 2, name: 'Retirement', default: false },
  ],
  message: 'Portfolios loaded',
};

const MOCK_PORTFOLIO_HOLDINGS = {
  success: true,
  data: {
    id: 1,
    name: 'Equity',
    holdings: [
      {
        id: 101, stockId: 1001, symbol: 'RELIANCE', name: 'Reliance Industries',
        sector: 'Energy', quantity: 10, avgPrice: 2500, lastTradedPrice: 2850,
        investment: 25000, currentValue: 28500, pnl: 3500, pnlPercent: 14.0,
      },
      {
        id: 102, stockId: 1002, symbol: 'TCS', name: 'Tata Consultancy Services',
        sector: 'IT', quantity: 5, avgPrice: 3800, lastTradedPrice: 3950,
        investment: 19000, currentValue: 19750, pnl: 750, pnlPercent: 3.95,
      },
    ],
  },
  message: 'Portfolio loaded',
};

const MOCK_ALL_STOCKS = {
  success: true,
  data: [
    { id: 1001, symbol: 'RELIANCE', name: 'Reliance Industries', sector: 'Energy', quantity: 10, avgPrice: 2500, lastTradedPrice: 2850, investment: 25000, currentValue: 28500, pnl: 3500, pnlPercent: 14.0 },
    { id: 1002, symbol: 'TCS', name: 'Tata Consultancy Services', sector: 'IT', quantity: 5, avgPrice: 3800, lastTradedPrice: 3950, investment: 19000, currentValue: 19750, pnl: 750, pnlPercent: 3.95 },
    { id: 1003, symbol: 'INFY', name: 'Infosys', sector: 'IT', quantity: 20, avgPrice: 1450, lastTradedPrice: 1520, investment: 29000, currentValue: 30400, pnl: 1400, pnlPercent: 4.83 },
  ],
  message: 'Stocks loaded',
};

const MOCK_WATCHLISTS = {
  success: true,
  data: [
    { id: 50, name: 'Tech Stocks', itemCount: 2 },
    { id: 51, name: 'Blue Chip', itemCount: 1 },
  ],
  message: 'Watchlists loaded',
};

async function setupStockPage(page) {
  await page.route('**/api/portfolios', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_PORTFOLIOS),
    });
  });

  await page.route('**/api/portfolios/1', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_PORTFOLIO_HOLDINGS),
    });
  });

  await page.route('**/api/stocks', (route) => {
    const url = route.request().url();
    if (url.includes('/api/portfolios/1')) return;
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_ALL_STOCKS),
    });
  });

  await page.route('**/api/watchlists', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_WATCHLISTS),
    });
  });

  // Mock watchlist membership check — empty
  await page.route('**/api/watchlists/stock/**', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [], message: '' }),
    });
  });

  await page.goto('/stocks.html');
  await page.waitForLoadState('networkidle');

  // The frontend auto-selects the first/default portfolio on load.
  // Reset to "All Stocks" mode so tests start from a consistent default state.
  // Tests that need portfolio mode will switch explicitly via selectOption('#portfolioSelector', '1').
  await page.evaluate(() => {
    const sel = document.getElementById('portfolioSelector');
    if (sel) {
      sel.value = '';
      sel.dispatchEvent(new Event('change'));
    }
  });
  await page.waitForTimeout(300);
}

test.describe('Stock Management — Portfolio Switching', () => {
  test.beforeEach(async ({ page }) => {
    page.on('console', msg => {
      if (msg.type() === 'error') console.error(`Console error: ${msg.text()}`);
    });
  });

  // ─── Portfolio Selector ────────────────────────────────────────────────────

  test('PS-1: portfolio selector is present with default All Stocks option', async ({ page }) => {
    await setupStockPage(page);
    const sel = page.locator('#portfolioSelector');
    await expect(sel).toBeVisible();
    await expect(sel.locator('option[value=""]')).toHaveText('All Stocks');
  });

  test('PS-2: portfolio selector lists all portfolios from API', async ({ page }) => {
    await setupStockPage(page);
    const options = page.locator('#portfolioSelector option');
    await expect(options).toHaveCount(3); // All Stocks + Equity + Retirement
    await expect(page.locator('#portfolioSelector option[value="1"]')).toHaveText('Equity (Default)');
    await expect(page.locator('#portfolioSelector option[value="2"]')).toHaveText('Retirement');
  });

  // ─── Data Loading ──────────────────────────────────────────────────────────

  test('PS-3: default (All Stocks) loads all stocks via getAllStocks', async ({ page }) => {
    await setupStockPage(page);
    // By default "All Stocks" is selected, so the page uses getAllStocks
    await page.waitForTimeout(500);
    const rows = page.locator('#stocksTableBody tr');
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(0)).toContainText('RELIANCE');
    await expect(rows.nth(1)).toContainText('TCS');
    await expect(rows.nth(2)).toContainText('INFY');
  });

  test('PS-4: switching to a portfolio loads holdings from getPortfolio', async ({ page }) => {
    await setupStockPage(page);
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    const rows = page.locator('#stocksTableBody tr');
    // Only 2 holdings in the Equity portfolio
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('RELIANCE');
    await expect(rows.nth(1)).toContainText('TCS');
    // INFY should NOT appear (not in this portfolio)
    await expect(rows.nth(0)).not.toContainText('INFY');
  });

  test('PS-5: switching back to All Stocks reloads all stocks', async ({ page }) => {
    await setupStockPage(page);
    // Switch to portfolio
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    // Switch back
    await page.selectOption('#portfolioSelector', '');
    await page.waitForTimeout(500);
    const rows = page.locator('#stocksTableBody tr');
    await expect(rows).toHaveCount(3);
  });

  // ─── Detail Links ──────────────────────────────────────────────────────────

  test('PS-6: stock detail link uses stockId (not holding ID) when portfolio is selected', async ({ page }) => {
    await setupStockPage(page);
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    // Click the RELIANCE symbol link (first row, first column)
    const link = page.locator('#stocksTableBody tr').nth(0).locator('td a').first();
    const href = await link.getAttribute('href');
    // Should use stockId=1001, not the holding ID 101
    expect(href).toContain('id=1001');
    expect(href).not.toContain('id=101');
  });

  test('PS-7: stock detail link uses stock id when no portfolio selected (legacy mode)', async ({ page }) => {
    await setupStockPage(page);
    await page.waitForTimeout(500);
    const link = page.locator('#stocksTableBody tr').nth(0).locator('td a').first();
    const href = await link.getAttribute('href');
    expect(href).toContain('id=1001');
  });

  // ─── Edit Modal ────────────────────────────────────────────────────────────

  test('PS-8: edit button passes holding ID when portfolio is selected', async ({ page }) => {
    await setupStockPage(page);
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    // Click edit button on first row
    const editBtn = page.locator('#stocksTableBody tr').nth(0).locator('.btn-edit');
    await editBtn.click();
    await page.waitForTimeout(300);
    // holding ID should be set (field is hidden type but has value)
    const holdingField = page.locator('#editHoldingId');
    const hid = await holdingField.inputValue();
    expect(hid).toBe('101');
    // Stock ID in the hidden field should be the stock PK (1001)
    const sid = await page.locator('#editStockId').inputValue();
    expect(sid).toBe('1001');
    // Verify stock name is correct
    await expect(page.locator('#editStockName')).toHaveValue('Reliance Industries');
  });

  test('PS-9: edit button works in All Stocks mode (no holdingId)', async ({ page }) => {
    await setupStockPage(page);
    await page.waitForTimeout(500);
    const editBtn = page.locator('#stocksTableBody tr').nth(0).locator('.btn-edit');
    await editBtn.click();
    await page.waitForTimeout(300);
    // In All Stocks mode, the holdingId field exists but should be empty
    const hid = await page.locator('#editHoldingId').inputValue();
    expect(hid).toBe('');
  });

  // ─── Delete Modal ──────────────────────────────────────────────────────────

  test('PS-10: delete modal shows stock symbol and confirms', async ({ page }) => {
    await setupStockPage(page);
    await page.waitForTimeout(500);
    const delBtn = page.locator('#stocksTableBody tr').nth(0).locator('.btn-delete');
    await delBtn.click();
    await page.waitForTimeout(300);
    // Modal should show the stock symbol
    await expect(page.locator('#deleteStockSymbol')).toContainText('RELIANCE');
    await expect(page.locator('#deleteModal')).toBeVisible();
  });

  test('PS-11: delete in portfolio mode triggers removeHolding call', async ({ page }) => {
    let removedHolding = null;
    await setupStockPage(page);

    // Intercept the holding removal call
    await page.route('**/api/portfolios/1/holdings/**', (route) => {
      if (route.request().method() === 'DELETE') {
        removedHolding = route.request().url();
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ success: true, data: null, message: 'Removed' }),
        });
      }
    });

    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    const delBtn = page.locator('#stocksTableBody tr').nth(0).locator('.btn-delete');
    await delBtn.click();
    await page.waitForTimeout(300);
    // Confirm delete
    await page.locator('#deleteModal button:has-text("Delete")').click();
    await page.waitForTimeout(500);
    // Should have called DELETE on holdings/101
    // confirmDelete calls removeHoldingByStock which uses stockId (1001), not holdingId (101)
    expect(removedHolding).toContain('/api/portfolios/1/holdings/stock/1001');
  });

  // ─── Watchlist ─────────────────────────────────────────────────────────────

  test('PS-12: watchlist dropdown uses stockId in portfolio mode', async ({ page }) => {
    let membershipUrl = null;
    await setupStockPage(page);
    await page.route('**/api/watchlists/stock/**', (route) => {
      membershipUrl = route.request().url();
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: '' }),
      });
    });

    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    // Click watchlist button
    const wlBtn = page.locator('#stocksTableBody tr').nth(0).locator('.btn-watchlist');
    await wlBtn.click();
    await page.waitForTimeout(500);
    // API call should use stockId 1001, not holdingId 101
    expect(membershipUrl).toContain('/1001');
  });

  // ─── Add Stock Modal ───────────────────────────────────────────────────────

  test('PS-13: add stock modal auto-selects current portfolio', async ({ page }) => {
    await setupStockPage(page);
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    // Open add stock modal
    await page.locator('button:has-text("Add Stock"):not([type="submit"])').click();
    await page.waitForTimeout(500);
    // Portfolio dropdown should default to "1" (Equity)
    const sel = page.locator('#addPortfolioId');
    await expect(sel).toHaveValue('1');
  });

  test('PS-14: add stock modal shows all portfolios when All Stocks is selected', async ({ page }) => {
    await setupStockPage(page);
    await page.waitForTimeout(500);
    await page.locator('button:has-text("Add Stock"):not([type="submit"])').click();
    await page.waitForTimeout(500);
    // Portfolio dropdown should default to "" (unselected)
    const sel = page.locator('#addPortfolioId');
    await expect(sel).toHaveValue('');
  });

  // ─── Search ────────────────────────────────────────────────────────────────

  test('PS-15: search filters holdings correctly in portfolio mode', async ({ page }) => {
    await setupStockPage(page);
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);

    // Search for TCS
    await page.fill('#searchInput', 'TCS');
    await page.waitForTimeout(500);
    const rows = page.locator('#stocksTableBody tr');
    await expect(rows).toHaveCount(1);
    await expect(rows).toContainText('TCS');
  });

  // ─── Summary Cards ─────────────────────────────────────────────────────────

  test('PS-16: summary cards update when switching portfolios', async ({ page }) => {
    await setupStockPage(page);
    await page.waitForTimeout(500);

    // All Stocks mode: get the investment value shown
    const allStocksInv = await page.locator('#totalInvestment').textContent();

    // Switch to Equity portfolio
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);

    const portfolioInv = await page.locator('#totalInvestment').textContent();
    // Equity portfolio has RELIANCE (25000) + TCS (19000) = 44000
    expect(portfolioInv).toBe('₹44,000.00');
  });

  // ─── Edit Modal Portfolio Selector ──────────────────────────────────────────

  test('PS-17: edit modal has portfolio dropdown pre-selected in portfolio mode', async ({ page }) => {
    await setupStockPage(page);
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    // Click edit on first row
    await page.locator('#stocksTableBody tr').nth(0).locator('.btn-edit').click();
    await page.waitForTimeout(300);
    // Portfolio dropdown should exist and be visible
    const pfSel = page.locator('#editPortfolioId');
    await expect(pfSel).toBeVisible();
    // Should default to current portfolio (Equity = 1)
    await expect(pfSel).toHaveValue('1');
  });

  test('PS-18: edit modal portfolio dropdown shows Stock Only option in All Stocks mode', async ({ page }) => {
    await setupStockPage(page);
    await page.waitForTimeout(500);
    await page.locator('#stocksTableBody tr').nth(0).locator('.btn-edit').click();
    await page.waitForTimeout(300);
    const pfSel = page.locator('#editPortfolioId');
    await expect(pfSel).toBeVisible();
    // Should default to "Stock Only" (empty value)
    await expect(pfSel).toHaveValue('');
    // Should show all portfolios as options
    const options = await pfSel.locator('option').allTextContents();
    expect(options).toContain('Stock Only (no portfolio)');
    expect(options).toContain('Equity (Default)');
    expect(options).toContain('Retirement');
  });

  // ─── Delete Context Message ────────────────────────────────────────────────

  test('PS-19: delete modal shows context message in portfolio mode', async ({ page }) => {
    await setupStockPage(page);
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    await page.locator('#stocksTableBody tr').nth(0).locator('.btn-delete').click();
    await page.waitForTimeout(300);
    const ctxMsg = page.locator('#deleteContextMsg');
    await expect(ctxMsg).toBeVisible();
    await expect(ctxMsg).toContainText('remove RELIANCE from "Equity"');
    await expect(ctxMsg).toContainText('stock record is kept');
  });

  test('PS-20: delete modal shows permanent delete message in All Stocks mode', async ({ page }) => {
    await setupStockPage(page);
    await page.waitForTimeout(500);
    await page.locator('#stocksTableBody tr').nth(0).locator('.btn-delete').click();
    await page.waitForTimeout(300);
    const ctxMsg = page.locator('#deleteContextMsg');
    await expect(ctxMsg).toBeVisible();
    await expect(ctxMsg).toContainText('permanently delete RELIANCE');
  });

  // ─── Context Badge ──────────────────────────────────────────────────────────

  test('PS-21: portfolio context badge updates when switching portfolios', async ({ page }) => {
    await setupStockPage(page);
    await page.waitForTimeout(500);
    // Default
    await expect(page.locator('#portfolioContextBadge')).toContainText('All Stocks');
    // Switch to portfolio
    await page.selectOption('#portfolioSelector', '1');
    await page.waitForTimeout(500);
    await expect(page.locator('#portfolioContextBadge')).toContainText('Equity');
  });
});
