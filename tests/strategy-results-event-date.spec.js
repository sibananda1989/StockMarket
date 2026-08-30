const { test, expect } = require('@playwright/test');

test.describe('Strategy Results — signal event date (cross date)', () => {
  test.beforeEach(async ({ page }) => {
    // Suppress startup-task modal
    await page.route('**/api/startup-tasks', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'success', data: [] }),
    }));

    // Summary counts: EMA_CROSSOVER + MACD
    await page.route('**/api/strategy-results', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'success',
        data: [
          { strategyName: 'EMA_CROSSOVER', buyCount: 2, sellCount: 1, holdCount: 112 },
          { strategyName: 'MACD', buyCount: 5, sellCount: 3, holdCount: 107 },
        ],
      }),
    }));

    // EMA_CROSSOVER rows CARRY eventDate (the cross date)
    await page.route('**/api/strategy-results/EMA_CROSSOVER', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'success',
        data: [
          { stockId: 1117, symbol: 'SAIL.NS', signal: 'BUY', confidence: 0.7, reason: 'EMA-20 crossed above EMA-50 (173.41 > 172.97) 2 day(s) ago', eventDate: '2026-08-27' },
          { stockId: 9360, symbol: 'TATSILV.NS', signal: 'BUY', confidence: 0.55, reason: 'EMA-20 crossed above EMA-50 (22.38 > 22.35) 4 day(s) ago', eventDate: '2026-08-25' },
          { stockId: 8, symbol: 'GAIL.NS', signal: 'SELL', confidence: 0.85, reason: 'EMA-20 crossed below EMA-50 (173.10 < 173.44) 0 day(s) ago', eventDate: '2026-08-29' },
        ],
      }),
    }));

    // MACD rows have NO eventDate (state-based strategy)
    await page.route('**/api/strategy-results/MACD', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'success',
        data: [
          { stockId: 100, symbol: 'RELIANCE', signal: 'BUY', confidence: 0.9, reason: 'MACD line above signal', eventDate: null },
        ],
      }),
    }));
  });

  test('expanded EMA_CROSSOVER rows show the cross date', async ({ page }) => {
    await page.goto('/strategy-results.html');
    await page.waitForLoadState('networkidle');

    await page.locator('.strategy-name[data-strategy="EMA_CROSSOVER"]').click();
    await expect(page.locator('.strategy-expand')).toBeVisible();

    const rows = page.locator('.strategy-expand li');
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(0)).toContainText('SAIL.NS');
    await expect(rows.nth(0)).toContainText('2026-08-27');
    await expect(rows.nth(1)).toContainText('2026-08-25');
    await expect(rows.nth(2)).toContainText('2026-08-29');
  });

  test('expanded MACD rows (no eventDate) render without a date cell', async ({ page }) => {
    await page.goto('/strategy-results.html');
    await page.waitForLoadState('networkidle');

    await page.locator('.strategy-name[data-strategy="MACD"]').click();
    await expect(page.locator('.strategy-expand')).toBeVisible();

    const row = page.locator('.strategy-expand li').first();
    await expect(row).toContainText('RELIANCE');
    await expect(row).not.toContainText(/20\d{2}-\d{2}-\d{2}/);
  });
});