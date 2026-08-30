const { test, expect } = require('@playwright/test');

test.describe('EMA-20/50 Cross Screener', () => {
  test('API returns a valid screener envelope', async ({ request }) => {
    const res = await request.get('http://localhost:8080/api/screener/ema-cross?days=5');
    expect(res.status()).toBe(200);
    const body = await res.json();

    expect(body.status).toBe('success');
    const data = body.data;
    expect(data).toBeDefined();
    expect(data.anchorDate).toBeTruthy();
    expect(Array.isArray(data.signals)).toBeTruthy();
    expect(data.universeScanned).toBeGreaterThanOrEqual(0);
    expect(Array.isArray(data.warnings)).toBeTruthy();

    for (const s of data.signals) {
      expect(s.stockId).toBeTruthy();
      expect(s.symbol).toBeTruthy();
      expect(s.crossDate).toBeTruthy();
      expect(typeof s.daysAgo).toBe('number');
      expect(s.ema20AtCross).toBeTruthy();
      expect(s.ema50AtCross).toBeTruthy();
      // At the cross, EMA-20 must be above EMA-50.
      expect(Number(s.ema20AtCross)).toBeGreaterThan(Number(s.ema50AtCross));
    }
  });

  test('days param is clamped (negative / huge values still 200)', async ({ request }) => {
    for (const days of [-3, 999]) {
      const res = await request.get(`http://localhost:8080/api/screener/ema-cross?days=${days}`);
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.status).toBe('success');
    }
  });

  test('screener page loads and renders table structure', async ({ page }) => {
    await page.goto('/ema-cross-screener.html');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1')).toContainText(/EMA-20/i);
    await expect(page.locator('#crossTableBody')).toBeVisible();

    // Header row always present.
    await expect(page.locator('thead th').first()).toContainText(/Symbol/i);

    // Data-tolerant: either signal rows or the empty state (dev DB changes daily).
    const rowCount = await page.locator('#crossTableBody tr').count();
    if (rowCount > 0) {
      const rowHasSymbol = await page.locator('#crossTableBody tr').first()
        .locator('a[href^="stock-detail.html"]').count();
      expect(rowHasSymbol).toBeGreaterThanOrEqual(1);
    } else {
      await expect(page.locator('#emptyState')).toBeVisible();
    }

    await page.screenshot({ path: 'playwright-report/ema-cross-screener.png', fullPage: true });
  });

  test('navigation dropdown contains the new page link', async ({ page }) => {
    // index.html keeps fetching dashboard data — wait for DOM only, then for the nav text.
    await page.goto('/index.html');
    await page.waitForLoadState('domcontentloaded');
    const nav = page.locator('#navigation');
    await expect(nav).toBeVisible({ timeout: 15000 });
    await expect(nav).toContainText(/EMA Cross/i, { timeout: 15000 });
  });
});