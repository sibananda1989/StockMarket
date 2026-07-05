const { test, expect } = require('@playwright/test');

// ─── Mock Data ──────────────────────────────────────────────────────────────

const MOCK_STOCK = {
  id: 200, symbol: 'TEST', name: 'Test Stock', sector: 'Technology',
  lastTradedPrice: 150.50, addedAt: new Date().toISOString(), recordCount: 30,
};

const MOCK_STOCK2 = {
  id: 201, symbol: 'DUMMY', name: 'Dummy Corp', sector: 'Finance',
  lastTradedPrice: 85.00, addedAt: new Date().toISOString(), recordCount: 15,
};

const MOCK_WATCHLIST = {
  id: 1, name: 'Test Watchlist', description: 'Mock watchlist',
  itemCount: 1, createdAt: new Date().toISOString(),
};

const MOCK_WATCHLIST_WITH_ITEMS = (stocks) => ({
  id: 1, name: 'Test Watchlist',
  stocks: stocks || [{ ...MOCK_STOCK }],
});

const MOCK_PORTFOLIOS = {
  success: true,
  data: [{ id: 1, name: 'Default', default: true }],
  message: 'Portfolios loaded',
};

// ─── Test Helpers ───────────────────────────────────────────────────────────

/**
 * Sets up API mocks and navigates to the watchlist page.
 * The page loads watchlist cards and selects the first watchlist to show detail.
 */
async function setupWatchlistPage(page, stockCount = 1) {
  const stocks = stockCount === 1 ? [MOCK_STOCK] : [MOCK_STOCK, MOCK_STOCK2];

  await page.route('**/api/startup-tasks**', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [], message: 'No pending tasks' }),
    });
  });

  await page.route('**/api/watchlists', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [{ ...MOCK_WATCHLIST, itemCount: stockCount }] }),
    });
  });

  // Mock the items API call — counter tracks calls so TC-10 can return empty after add
  let itemsCallCount = 0;
  await page.route('**/api/watchlists/1/items', route => {
    itemsCallCount++;
    if (itemsCallCount > 1) {
      // Subsequent calls (e.g. after portfolio add) return empty
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: { id: 1, name: 'Test Watchlist', stocks: [] },
        }),
      });
    } else {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: { id: 1, name: 'Test Watchlist', stocks },
        }),
      });
    }
  });

  // Mock stock list API
  await page.route('**/api/stocks', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [] }),
    });
  });

  // Mock stock signal API (one endpoint per stock)
  await page.route('**/api/stocks/*/signal', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: null }),
    });
  });

  // Mock portfolios API (needed for portfolio modal dropdown)
  await page.route('**/api/portfolios', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_PORTFOLIOS),
    });
  });

  // Mock portfolio/history (defensive — some pages load this)
  await page.route('**/api/portfolio/history**', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: [] }),
    });
  });

  await page.goto('/watchlist.html');
  await page.waitForSelector('.watchlist-card', { timeout: 10000 });

  // Select the watchlist to show detail
  await page.evaluate((id) => {
    if (typeof window.selectWatchlist === 'function') {
      window.selectWatchlist(id);
    }
  }, MOCK_WATCHLIST.id);

  await page.waitForSelector('#detailTableBody .stock-row', { timeout: 10000 });

  // Return the itemsCallCount updater so TC-10 can access it
  return { itemsCallCount };
}

async function openPortfolioModalForFirstRow(page) {
  const cartBtn = page
    .locator('#detailTableBody .stock-row')
    .first()
    .locator('button[onclick*="openPortfolioModal"]');
  await expect(cartBtn).toBeAttached();
  await cartBtn.click();
}

async function clickAddToPortfolio(page) {
  await page.locator('#portfolioModal button:has-text("Add to Portfolio")').click();
}

async function clickCancel(page) {
  await page.locator('#portfolioModal button:has-text("Cancel")').click();
}

// ─── Tests ──────────────────────────────────────────────────────────────────

test.describe('Add to Portfolio Feature', () => {
  test.beforeEach(async ({ page }) => {
    page.on('console', msg => {
      if (msg.type() === 'error') console.error(`Console error: ${msg.text()}`);
    });
  });

  test('TC-1: should open portfolio modal when cart icon is clicked', async ({ page }) => {
    await setupWatchlistPage(page);

    await openPortfolioModalForFirstRow(page);

    const modal = page.locator('#portfolioModal');
    await expect(modal).toBeVisible();
    await expect(page.locator('#portfolioSymbol')).toHaveValue(MOCK_STOCK.symbol);
    await expect(page.locator('#portfolioName')).toHaveValue(MOCK_STOCK.name);
  });

  test('TC-2: modal shows empty inputs on each open', async ({ page }) => {
    await setupWatchlistPage(page);

    // First open
    await openPortfolioModalForFirstRow(page);
    await expect(page.locator('#portfolioQty')).toHaveValue('');
    await expect(page.locator('#portfolioAvgPrice')).toHaveValue('');

    // Close and reopen
    await clickCancel(page);
    await expect(page.locator('#portfolioModal')).toBeHidden();

    await openPortfolioModalForFirstRow(page);
    await expect(page.locator('#portfolioQty')).toHaveValue('');
    await expect(page.locator('#portfolioAvgPrice')).toHaveValue('');
  });

  test('TC-3: should reject empty quantity', async ({ page }) => {
    await setupWatchlistPage(page);

    await openPortfolioModalForFirstRow(page);
    await page.locator('#portfolioAvgPrice').fill('2500');
    await clickAddToPortfolio(page);

    await expect(page.locator('.toast')).toContainText('Quantity must be greater than 0');
    await expect(page.locator('#portfolioModal')).toBeVisible();
  });

  test('TC-4: should reject zero quantity', async ({ page }) => {
    await setupWatchlistPage(page);

    await openPortfolioModalForFirstRow(page);
    await page.locator('#portfolioQty').fill('0');
    await page.locator('#portfolioAvgPrice').fill('2500');
    await clickAddToPortfolio(page);

    await expect(page.locator('.toast')).toContainText('Quantity must be greater than 0');
    await expect(page.locator('#portfolioModal')).toBeVisible();
  });

  test('TC-5: should reject negative avgPrice', async ({ page }) => {
    await setupWatchlistPage(page);

    await openPortfolioModalForFirstRow(page);
    await page.locator('#portfolioQty').fill('10');
    await page.locator('#portfolioAvgPrice').fill('-50');
    await clickAddToPortfolio(page);

    await expect(page.locator('.toast')).toContainText('Avg price must be 0 or more');
    await expect(page.locator('#portfolioModal')).toBeVisible();
  });

  test('TC-6: should reject empty avgPrice', async ({ page }) => {
    await setupWatchlistPage(page);

    await openPortfolioModalForFirstRow(page);
    await page.locator('#portfolioQty').fill('10');
    await clickAddToPortfolio(page);

    await expect(page.locator('.toast')).toContainText('Avg price must be 0 or more');
    await expect(page.locator('#portfolioModal')).toBeVisible();
  });

  test('TC-7: cancel button closes modal', async ({ page }) => {
    await setupWatchlistPage(page);

    await openPortfolioModalForFirstRow(page);
    await page.locator('#portfolioQty').fill('10');
    await page.locator('#portfolioAvgPrice').fill('2500');
    await clickCancel(page);

    await expect(page.locator('#portfolioModal')).toBeHidden();
  });

  test('TC-8: close (X) button closes modal', async ({ page }) => {
    await setupWatchlistPage(page);

    await openPortfolioModalForFirstRow(page);
    await page.locator('#portfolioQty').fill('10');
    await page.locator('#portfolioAvgPrice').fill('2500');

    await page.locator('#portfolioModal .fa-times').click();
    await expect(page.locator('#portfolioModal')).toBeHidden();
  });

  test('TC-9: cart button shows correct stock for each row', async ({ page }) => {
    await setupWatchlistPage(page, 2);

    const rows = page.locator('#detailTableBody .stock-row');
    await expect(rows).toHaveCount(2);

    // Click cart on first row
    await rows.nth(0).locator('button[onclick*="openPortfolioModal"]').click();
    await expect(page.locator('#portfolioSymbol')).toHaveValue(MOCK_STOCK.symbol);

    // Close
    await clickCancel(page);

    // Click cart on second row
    await rows.nth(1).locator('button[onclick*="openPortfolioModal"]').click();
    await expect(page.locator('#portfolioSymbol')).toHaveValue(MOCK_STOCK2.symbol);
    await expect(page.locator('#portfolioSymbol')).not.toHaveValue(MOCK_STOCK.symbol);
  });

  test('TC-10: successful add removes stock from watchlist', async ({ page }) => {
    // Mock the holding add API to simulate success
    await page.route('**/api/portfolios/1/holdings', route => {
      if (route.request().method() === 'POST') {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ success: true, data: { id: 1 }, message: 'Added' }),
        });
      } else {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ success: true, data: [] }),
        });
      }
    });

    // The items mock in setupWatchlistPage uses a counter: first call returns stock,
    // second call (after portfolio add → re-select watchlist) returns empty
    await setupWatchlistPage(page, 1);

    await openPortfolioModalForFirstRow(page);
    await page.locator('#portfolioQty').fill('10');
    await page.locator('#portfolioAvgPrice').fill('2500');
    await clickAddToPortfolio(page);

    // Wait for success toast
    await expect(page.locator('.toast')).toContainText('added to portfolio');

    // After adding to portfolio, the page re-selects the watchlist.
    // The items mock returns empty on the second call, so the stock row should be gone.
    await page.waitForTimeout(500);
    await expect(page.locator('#detailTableBody .stock-row')).toHaveCount(0);
  });

  test('TC-11: form data does not leak between stocks', async ({ page }) => {
    await setupWatchlistPage(page, 2);

    const rows = page.locator('#detailTableBody .stock-row');
    await expect(rows).toHaveCount(2);

    // Open for stock A, fill data and cancel
    await rows.nth(0).locator('button[onclick*="openPortfolioModal"]').click();
    await page.locator('#portfolioQty').fill('10');
    await page.locator('#portfolioAvgPrice').fill('100');
    await clickCancel(page);

    // Open for stock B
    await rows.nth(1).locator('button[onclick*="openPortfolioModal"]').click();

    // Fields should be empty
    await expect(page.locator('#portfolioQty')).toHaveValue('');
    await expect(page.locator('#portfolioAvgPrice')).toHaveValue('');
  });
});
