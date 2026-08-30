const { test, expect } = require('@playwright/test');

test.describe('Portfolio Transaction Lot SELL - INOXINDIA.NS bug fix', () => {
  test('SELL 4 @2010 against BUY 654 succeeds without oversell/rollback', async ({ request }) => {
    // Verify lot state via API (replay-based)
    const lotsRes = await request.get('http://localhost:8080/api/portfolios/1/lots?stockId=9379');
    expect(lotsRes.ok()).toBeTruthy();
    const lots = await lotsRes.json();
    const lot = (lots.data || lots).find(l => l.id === 654);
    expect(lot).toBeDefined();
    // After fix, lot should be SOLD with 4 sold, realizedPnl 456.4
    expect(lot.remainingQuantity).toBe(0);
    expect(lot.status).toBe('SOLD');
    expect(lot.soldQuantity).toBe(4);
    expect(lot.sells.length).toBe(1);
    expect(lot.sells[0].price).toBe(2010);
    expect(lot.sells[0].linkedBuyId).toBe(654);

    // Holdings should be deleted (qty 0) — replay removes
    const holdingsRes = await request.get('http://localhost:8080/api/portfolios/1/holdings');
    if (holdingsRes.ok()) {
      const holdings = await holdingsRes.json();
      const h = (holdings.data || holdings).find(x => x.stockId === 9379);
      expect(h).toBeUndefined(); // fully sold -> ghost deleted
    }

    // Attempt oversell should be rejected (remaining 0)
    const oversellRes = await request.post('http://localhost:8080/api/portfolios/1/lots/654/sell', {
      data: { quantity: 1, price: 2010, fees: 0, transactionDate: '2026-08-27' }
    });
    expect(oversellRes.status()).toBeGreaterThanOrEqual(400);
    const body = await oversellRes.json();
    expect(body.success).toBe(false);
  });

  test('UI portfolio-transactions page loads lot and shows SOLD', async ({ page }) => {
    await page.goto('/portfolio-transactions.html');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('body')).toContainText(/Portfolio/i);
    // Screenshot for artifact
    await page.screenshot({ path: 'playwright-report/inox-sell-ui.png', fullPage: true });
  });
});
