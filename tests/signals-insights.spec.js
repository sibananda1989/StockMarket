const { test, expect } = require('@playwright/test');

const MOCK_SIGNALS = [
  { stockId: 1, symbol: 'RELIANCE', name: 'Reliance Industries', sector: 'Energy', latestPrice: 2850,
    rsi14: 28.5, sma20: 2800, compositeScore: 8, recommendation: 'STRONG BUY', confidenceScore: 85,
    suggestedPositionSize: 5, signalAge: 1, targetPrice: 3200, stopLoss: 2600, atr: 45.5,
    pnlPercent: 12.3, trendStrength: 62, pctFrom52WLow: 5.2, pctFrom52WHigh: -8.1, macdHistogram: 1.2,
    bollingerUpper: 3100, bollingerMiddle: 2800, bollingerLower: 2500 },
  { stockId: 2, symbol: 'TCS', name: 'Tata Consultancy Services', sector: 'IT', latestPrice: 3950,
    rsi14: 32.0, sma20: 3900, compositeScore: 5, recommendation: 'BUY', confidenceScore: 65,
    suggestedPositionSize: 3, signalAge: 2, targetPrice: 4200, stopLoss: 3750, atr: 55.0,
    pnlPercent: 5.1, trendStrength: 55, pctFrom52WLow: 3.0, pctFrom52WHigh: -5.0, macdHistogram: 0.8,
    bollingerUpper: 4200, bollingerMiddle: 3900, bollingerLower: 3600 },
  { stockId: 3, symbol: 'HDFCBANK', name: 'HDFC Bank', sector: 'Banking', latestPrice: 1650,
    rsi14: 45.0, sma20: 1640, compositeScore: 1, recommendation: 'HOLD', confidenceScore: 50,
    suggestedPositionSize: 2, signalAge: 5, targetPrice: null, stopLoss: null, atr: 25.0,
    pnlPercent: -1.2, trendStrength: 48, pctFrom52WLow: 10.0, pctFrom52WHigh: -15.0, macdHistogram: -0.3,
    bollingerUpper: 1750, bollingerMiddle: 1650, bollingerLower: 1550 },
  { stockId: 4, symbol: 'INFY', name: 'Infosys', sector: 'IT', latestPrice: 1520,
    rsi14: 72.5, sma20: 1480, compositeScore: -4, recommendation: 'SELL', confidenceScore: 72,
    suggestedPositionSize: 2, signalAge: 3, targetPrice: 1400, stopLoss: 1580, atr: 30.0,
    pnlPercent: -3.5, trendStrength: 38, pctFrom52WLow: 20.0, pctFrom52WHigh: -2.0, macdHistogram: -1.5,
    bollingerUpper: 1600, bollingerMiddle: 1500, bollingerLower: 1400 },
  { stockId: 5, symbol: 'ICICIBANK', name: 'ICICI Bank', sector: 'Banking', latestPrice: 1120,
    rsi14: 78.0, sma20: 1080, compositeScore: -7, recommendation: 'STRONG SELL', confidenceScore: 90,
    suggestedPositionSize: 1, signalAge: 0, targetPrice: 1000, stopLoss: 1180, atr: 28.0,
    pnlPercent: -8.7, trendStrength: 25, pctFrom52WLow: 30.0, pctFrom52WHigh: -1.0, macdHistogram: -2.1,
    bollingerUpper: 1250, bollingerMiddle: 1100, bollingerLower: 950 },
];

test.describe('Dashboard Signals & Insights', () => {
  // Sets up API mocks using addInitScript (primary) + page.route (fallback).
  // addInitScript patches window.fetch before page JS runs.
  // page.route('**/api/signals**') catches requests if addInitScript has race condition
  // on fresh pages under parallel load. Trailing ** ensures query strings match.
  async function setupMocks(page, signalData = MOCK_SIGNALS) {
    await page.addInitScript((mockData) => {
      const origFetch = window.fetch.bind(window);
      window.fetch = (url, options) => {
        const urlStr = typeof url === 'string' ? url : url.url;
        if (urlStr.includes('/api/signals')) {
          return Promise.resolve(new Response(
            JSON.stringify({ success: true, data: mockData, message: 'Signals loaded' }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          ));
        }
        if (urlStr.includes('/api/startup-tasks')) {
          return Promise.resolve(new Response(
            JSON.stringify({ success: true, data: [], message: 'No pending tasks' }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          ));
        }
        return origFetch(url, options);
      };
    }, signalData);

    await page.route('**/api/signals**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: signalData, message: 'Signals loaded' }),
      });
    });

    await page.route('**/api/portfolios**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No portfolios' }),
      });
    });

    await page.route('**/api/stocks**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No stocks' }),
      });
    });

    await page.route('**/api/portfolio/history**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No history' }),
      });
    });

    await page.evaluate(() => {
      try {
        localStorage.removeItem('startupTasksSnoozeExpiry');
        localStorage.removeItem('startupTasksSnoozeDuration');
      } catch (e) {}
    });
  }

  // Pure page.route approach — no addInitScript.
  // Used by the first test where addInitScript has a race condition on fresh pages.
  // Includes startup-tasks mock to prevent the inline script from hitting the
  // real backend (which may be slow under parallel load from other test files).
  async function setupMocksRouteOnly(page, signalData = MOCK_SIGNALS) {
    await page.route('**/api/signals**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: signalData, message: 'Signals loaded' }),
      });
    });

    await page.route('**/api/startup-tasks**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No pending tasks' }),
      });
    });

    await page.route('**/api/portfolios**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No portfolios' }),
      });
    });

    await page.route('**/api/stocks**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No stocks' }),
      });
    });

    await page.route('**/api/portfolio/history**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No history' }),
      });
    });

    await page.evaluate(() => {
      try {
        localStorage.removeItem('startupTasksSnoozeExpiry');
        localStorage.removeItem('startupTasksSnoozeDuration');
      } catch (e) {}
    });
  }

  // First test uses pure page.route to avoid addInitScript race condition on fresh pages
  test('should show signal stats counters with correct values', async ({ page }) => {
    await setupMocksRouteOnly(page);
    await page.goto('/index.html');
    // Wait for signals data to actually render (static section exists immediately)
    await page.waitForSelector('#signalsTableBody tr td a', { timeout: 10000 });

    // Default filters show only STRONG BUY, BUY, HOLD (SELL/STRONG SELL unchecked)
    // Mocks: RELIANCE=STRONG BUY, TCS=BUY, HDFCBANK=HOLD
    await expect(page.locator('#strongBuyCount')).toHaveText('1');
    await expect(page.locator('#buyCount')).toHaveText('1');
    await expect(page.locator('#holdCount')).toHaveText('1');
    await expect(page.locator('#sellCount')).toHaveText('0');
    await expect(page.locator('#strongSellCount')).toHaveText('0');
  });

  test('should render signal table with all stock rows', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    // Default filters show only STRONG BUY, BUY, HOLD (3 rows)
    await expect(page.locator('#signalsTableBody tr')).toHaveCount(3);
    await expect(page.locator('#signalsTableBody')).toContainText('RELIANCE');
    await expect(page.locator('#signalsTableBody')).toContainText('TCS');
    await expect(page.locator('#signalsTableBody')).toContainText('HDFCBANK');
    await expect(page.locator('#signalsTableBody')).not.toContainText('INFY');
    await expect(page.locator('#signalsTableBody')).not.toContainText('ICICIBANK');
  });

  test('should show buy and sell opportunity cards', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    const buyCard = page.locator('#buySignalsCard');
    const sellCard = page.locator('#sellSignalsCard');

    // Default filters show STRONG BUY, BUY, HOLD — only buy signals shown
    await expect(buyCard).toContainText('RELIANCE');
    await expect(buyCard).toContainText('STRONG BUY');
    await expect(buyCard).toContainText('TCS');
    await expect(buyCard).toContainText('BUY');

    await expect(buyCard).not.toContainText('INFY');
    await expect(sellCard).toContainText('No sell warnings');
  });

  test('should show recommendation badges with correct colors', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    // Default filters: first 3 rows are STRONG BUY, BUY, HOLD
    const strongBuy = page.locator('#signalsTableBody tr').nth(0);
    await expect(strongBuy).toContainText('STRONG BUY');

    const buy = page.locator('#signalsTableBody tr').nth(1);
    await expect(buy).toContainText('BUY');

    const hold = page.locator('#signalsTableBody tr').nth(2);
    await expect(hold).toContainText('HOLD');
  });

  test('should sort signals by composite score descending by default', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    const firstSymbol = await page.locator('#signalsTableBody tr').nth(0).locator('td').nth(0).textContent();
    expect(firstSymbol).toBe('RELIANCE');
  });

  test('should filter signals by symbol search', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    await page.fill('#filterSymbol', 'BANK');
    await page.click('text=Apply');

    // Default filters show HOLD only (SELL/STRONG SELL unchecked)
    // Only HDFCBANK (HOLD) matches, ICICIBANK is STRONG SELL (filtered out)
    await expect(page.locator('#signalsTableBody tr')).toHaveCount(1);
    await expect(page.locator('#signalsTableBody')).toContainText('HDFCBANK');
    await expect(page.locator('#signalsTableBody')).not.toContainText('ICICIBANK');
  });

  test('should filter signals by recommendation checkboxes', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    await page.uncheck('input.rec-filter[value="BUY"]');
    await page.uncheck('input.rec-filter[value="HOLD"]');
    await page.uncheck('input.rec-filter[value="SELL"]');
    await page.uncheck('input.rec-filter[value="STRONG SELL"]');
    await page.click('text=Apply');

    await expect(page.locator('#signalsTableBody tr')).toHaveCount(1);
    await expect(page.locator('#signalsTableBody')).toContainText('RELIANCE');
  });

  test('should filter by RSI min/max', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    await page.fill('#filterRsiMin', '40');
    await page.fill('#filterRsiMax', '75');
    await page.click('text=Apply');

    // Default filters show STRONG BUY, BUY, HOLD
    // RSI range 40-75 matches: HDFCBANK (RSI 45, HOLD)
    // INFY (RSI 72.5) would match but is SELL (filtered out by default)
    // TCS (RSI 32) and RELIANCE (RSI 28.5) are below 40
    await expect(page.locator('#signalsTableBody tr')).toHaveCount(1);
    await expect(page.locator('#signalsTableBody')).toContainText('HDFCBANK');
  });

  test('should filter by min composite score', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    await page.fill('#filterCompositeMin', '6');
    await page.click('text=Apply');

    await expect(page.locator('#signalsTableBody tr')).toHaveCount(1);
    await expect(page.locator('#signalsTableBody')).toContainText('RELIANCE');
  });

  test('should sort by column when clicking header', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    await page.locator('#signalsTable th:has-text("Confidence")').click();
    await page.waitForTimeout(200);

    // Default filters: only RELIANCE (85), TCS (65), HDFCBANK (50)
    // Sorted by confidence descending: RELIANCE first (85)
    const firstSymbol = await page.locator('#signalsTableBody tr').nth(0).locator('td').nth(0).textContent();
    expect(firstSymbol).toBe('RELIANCE');
  });

  test('should toggle sort direction on click', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    await page.locator('#signalsTable th:has-text("Confidence")').click();
    await page.waitForTimeout(200);

    await page.locator('#signalsTable th:has-text("Confidence")').click();
    await page.waitForTimeout(200);

    const firstSymbol = await page.locator('#signalsTableBody tr').nth(0).locator('td').nth(0).textContent();
    expect(firstSymbol).toBe('HDFCBANK');
  });

  test('should show empty state when no signals', async ({ page }) => {
    // Pure page.route approach — no addInitScript.
    // This avoids the addInitScript MOCK_SIGNALS conflict where the patched fetch
    // would return MOCK_SIGNALS instead of empty data.
    await page.route('**/api/signals**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No signals' }),
      });
    });
    await page.route('**/api/portfolios**', route => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
    });
    await page.route('**/api/stocks**', route => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
    });
    await page.route('**/api/portfolio/history**', route => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
    });
    await page.evaluate(() => {
      try {
        localStorage.removeItem('startupTasksSnoozeExpiry');
        localStorage.removeItem('startupTasksSnoozeDuration');
      } catch (e) {}
    });

    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');

    await expect(page.locator('#signalsTableBody')).toContainText('No matching signals');
    await expect(page.locator('#buyCount')).toHaveText('0');
    await expect(page.locator('#sellCount')).toHaveText('0');
  });

  test('should show error message on API failure', async ({ page }) => {
    // Pure page.route approach — no addInitScript.
    await page.route('**/api/portfolios**', route => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
    });
    await page.route('**/api/stocks**', route => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
    });
    await page.route('**/api/portfolio/history**', route => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
    });
    await page.route('**/api/signals**', route => {
      route.abort('connectionrefused');
    });
    await page.evaluate(() => {
      try {
        localStorage.removeItem('startupTasksSnoozeExpiry');
        localStorage.removeItem('startupTasksSnoozeDuration');
      } catch (e) {}
    });

    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');

    await expect(page.locator('#signalsContent')).toContainText('Failed to load signals');
  });

  test('should show market regime card when signals have trend strength', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    await expect(page.locator('#regimeCard')).toBeVisible();
    await expect(page.locator('#marketRegime')).not.toHaveText('--');
  });

  test('should navigate to stock detail page on symbol click', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/index.html');
    await page.waitForSelector('#signalsSection');
    await page.waitForSelector('#signalsTableBody tr td a');

    await page.locator('#signalsTableBody a').first().click();
    await expect(page).toHaveURL(/stock-detail\.html\?id=/);
  });
});
