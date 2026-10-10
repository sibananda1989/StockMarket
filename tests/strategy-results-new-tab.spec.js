const { test, expect } = require('@playwright/test');

// Verifies that stock-detail links on the Strategy Results page open in a NEW tab
// (target="_blank" rel="noopener") instead of navigating away:
//   (a) expanded strategy-run signal rows (renderRunRows)
//   (b) Top Buys / Top Sells lists (renderTopList)
test.describe('Strategy Results — stock-detail links open in new tab', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/startup-tasks', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'success', data: [] }),
    }));

    await page.route('**/api/strategy-results/top*', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'success',
        data: {
          topBuys: [
            { stockId: 1117, symbol: 'SAIL.NS', count: 3, strategyNames: ['EMA_CROSSOVER', 'MACD'], avgConfidence: 0.72 },
          ],
          topSells: [
            { stockId: 8, symbol: 'GAIL.NS', count: 2, strategyNames: ['MACD'], avgConfidence: 0.85 },
          ],
        },
      }),
    }));

    await page.route('**/api/strategy-results', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'success',
        data: [
          { strategyName: 'EMA_CROSSOVER', displayName: 'EMA 20/50 Cross', buyCount: 1, sellCount: 0, holdCount: 114, priority: 7, active: true },
        ],
      }),
    }));

    await page.route('**/api/strategy-results/EMA_CROSSOVER', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'success',
        data: [
          { stockId: 1117, symbol: 'SAIL.NS', signal: 'BUY', confidence: 0.7, reason: 'up 2d', eventDate: '2026-08-27' },
        ],
      }),
    }));
  });

  test('expanded run-row symbol link opens stock-detail in a new tab', async ({ page, context }) => {
    await page.goto('/strategy-results.html');
    await page.waitForLoadState('networkidle');

    await page.locator('.strategy-name[data-strategy="EMA_CROSSOVER"]').click();
    const link = page.locator('.strategy-expand li a[href*="stock-detail.html"]').first();
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('target', '_blank');
    await expect(link).toHaveAttribute('rel', 'noopener');

    const [popup] = await Promise.all([
      context.waitForEvent('page'),
      link.click(),
    ]);
    await popup.waitForLoadState('domcontentloaded');
    expect(popup.url()).toContain('/stock-detail.html?id=1117&portfolioId=1');

    // Original page must stay on strategy results.
    expect(page.url()).toContain('/strategy-results.html');
  });

  test('Top Buys / Top Sells list links open stock-detail in a new tab', async ({ page, context }) => {
    await page.goto('/strategy-results.html');
    await page.waitForLoadState('networkidle');

    const buyLink = page.locator('#topBuysList a[href*="stock-detail.html"]').first();
    await expect(buyLink).toBeVisible();
    await expect(buyLink).toHaveAttribute('target', '_blank');
    await expect(buyLink).toHaveAttribute('rel', 'noopener');

    const [popup] = await Promise.all([
      context.waitForEvent('page'),
      buyLink.click(),
    ]);
    await popup.waitForLoadState('domcontentloaded');
    expect(popup.url()).toContain('/stock-detail.html?id=1117&portfolioId=1');
    expect(page.url()).toContain('/strategy-results.html');
  });
});
