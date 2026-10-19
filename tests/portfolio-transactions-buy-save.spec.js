const { test, expect } = require('@playwright/test');

// Regression for the P0 bug where a successful BUY threw immediately after
// saving. submitBuy called closeBuyModal() (which nulls _selectedBuyStock) and
// then read _selectedBuyStock.symbol, so the TypeError was caught and:
//   - the success toast never appeared, and
//   - await loadAll() never ran, leaving the table stale until manual refresh.
//
// The POST is stubbed so this test never creates a real transaction.
test.describe('Transactions — successful buy shows toast and refreshes the table', () => {
  let postCalls = 0;
  let lotsReloaded = 0;

  test.beforeEach(async ({ page }) => {
    postCalls = 0;
    lotsReloaded = 0;

    // loadAll() calls getPortfolios() and getAllStocks() before lots.
    // /api/stocks is genuinely slow (multi-second), so stub it to keep the
    // test fast and deterministic.
    await page.route('**/api/portfolios', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'success', data: [{ id: 1, name: 'Test Portfolio' }] })
    }));

    await page.route('**/api/stocks', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'success',
        data: [{ id: 1, symbol: 'TEST.NS', name: 'Test Corp', lastTradedPrice: 120 }]
      })
    }));

    await page.route('**/api/portfolios/*/lots*', async route => {
      lotsReloaded++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'success',
          data: [{
            lotId: 1, stockId: 1, portfolioId: 1,
            stockSymbol: 'TEST.NS', stockName: 'Test Corp',
            quantity: 10, price: 100, fees: 0,
            soldQuantity: 0, remainingQuantity: 10,
            realizedPnl: 0, status: 'OPEN',
            transactionDate: '2026-01-05'
          }]
        })
      });
    });

    await page.route('**/api/portfolios/*/transactions', async route => {
      if (route.request().method() === 'POST') {
        postCalls++;
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 'success',
            message: 'created',
            data: { id: 999 }
          })
        });
        return;
      }
      await route.fallback();
    });
  });

  test('toast appears and lots are refetched after a successful buy', async ({ page }) => {
    const pageErrors = [];
    page.on('pageerror', e => pageErrors.push(e.message));

    await page.goto('/portfolio-transactions.html');
    await expect(page.locator('#stat-total')).not.toHaveText('--');

    const lotsBefore = lotsReloaded;

    await page.evaluate(() => PortfolioTransactions.openBuyModal());
    await page.fill('#buy-qty', '5');
    await page.fill('#buy-price', '250');
    // Select the stock through the same public entry point the search dropdown
    // uses, so _selectedBuyStock is populated the way it is in real use.
    await page.evaluate(() => PortfolioTransactions.selectBuyStock({
      stockId: 1, symbol: 'TEST.NS', name: 'Test Corp'
    }));

    await page.evaluate(() => PortfolioTransactions.submitBuy(new Event('submit')));

    // The POST must have been attempted.
    expect(postCalls).toBe(1);

    // The bug: loadAll() was skipped, so lots were never refetched.
    expect(lotsReloaded).toBeGreaterThan(lotsBefore);

    // The bug: this TypeError was swallowed by the catch block.
    expect(pageErrors.filter(m => /Cannot read properties of null/.test(m))).toEqual([]);

    // The success toast must be visible.
    await expect(page.locator('.toast, [id*="toast"]').first()).toBeVisible({ timeout: 3000 });
    await expect(page.locator('body')).toContainText(/Bought 5 × TEST\.NS/);
  });
});
