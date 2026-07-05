const { test, expect } = require('@playwright/test');

test.describe('Stock Market Tracker Frontend', () => {
  async function dismissStartupModal(page) {
    // Mock startup tasks API to return empty list, preventing the modal from appearing
    await page.route('**/api/startup-tasks**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [], message: 'No pending tasks' }),
      });
    });
    // Also clear any localStorage snooze that might suppress the modal check
    await page.evaluate(() => {
      try {
        localStorage.removeItem('startupTasksSnoozeExpiry');
        localStorage.removeItem('startupTasksSnoozeDuration');
      } catch (e) {
        // localStorage might not be available on about:blank before navigation
      }
    });
  }

  test.beforeEach(async ({ page }) => {
    // Listen for console errors and fail the test if any occur
    page.on('console', msg => {
      if (msg.type() === 'error') {
        console.error(`Console error: ${msg.text()}`);
        // Don't fail immediately on console errors as some might be expected
        // We'll check for unexpected errors at the end of each test
      }
    });

    // Dismiss startup modal for all tests that load index.html
    await dismissStartupModal(page);
  });

  test.afterEach(async ({ page }) => {
    // Check for any unexpected console errors during the test
    const errors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });
    
    // If we collected any errors, fail the test
    if (errors.length > 0) {
      // Filter out expected errors (like 404s for non-existent resources)
      const unexpectedErrors = errors.filter(error => 
        !error.includes('Failed to load resource: the server responded with a status of 404') &&
        !error.includes('API Error: Error: Stock not found') &&
        !error.includes('API Error: TypeError: Failed to fetch')
      );
      
      if (unexpectedErrors.length > 0) {
        throw new Error(`Unexpected console errors: ${unexpectedErrors.join('; ')}`);
      }
    }
  });

  test('should load dashboard page successfully', async ({ page }) => {
    await page.goto('/index.html');
    await expect(page).toHaveTitle(/Stock Tracker - Dashboard/);
    
    // Check that the main heading is visible
    await expect(page.locator('h1:has-text("Portfolio Dashboard")')).toBeVisible();
    
    // Check that navigation links are present
    await expect(page.locator('nav a:has-text("Dashboard")')).toHaveClass(/bg-blue-600/);
    await expect(page.locator('nav a:has-text("Stock Management")')).toBeVisible();
    await expect(page.locator('nav a:has-text("Price Entry")')).toBeVisible();
    await expect(page.locator('nav a:has-text("RSI Analysis")')).toBeVisible();
    await expect(page.locator('nav a:has-text("Stock History")')).toBeVisible();
    await expect(page.locator('nav a:has-text("History Summary")')).toBeVisible();
    
    // Check that summary cards are present
    await expect(page.locator('#summaryCards')).toBeVisible();
    await expect(page.locator('#totalInvestment')).toBeVisible();
    await expect(page.locator('#totalCurrentValue')).toBeVisible();
    await expect(page.locator('#totalPnl')).toBeVisible();
    await expect(page.locator('#totalPnlPct')).toBeVisible();
    await expect(page.locator('#holdingsCount')).toBeVisible();
    await expect(page.locator('#winRate')).toBeVisible();
    
    // Check that charts are present
    await expect(page.locator('#portfolioTrendChart')).toBeVisible();
    await expect(page.locator('#sectorChart')).toBeVisible();
    await expect(page.locator('#pnlDistributionChart')).toBeVisible();
    
    // Check that signals section is present
    await expect(page.locator('#signalsSection')).toBeVisible();
    
    // Check that performers section is present
    await expect(page.locator('#topPerformers')).toBeVisible();
    await expect(page.locator('#bottomPerformers')).toBeVisible();
    
    // Check that holdings table is present
    await expect(page.locator('#holdingsTable')).toBeVisible();
  });

  test('should load stock management page successfully', async ({ page }) => {
    await page.goto('/stocks.html');
    await expect(page).toHaveTitle(/Stock Tracker - Stock Management/);
    
    // Check that the main heading is visible
    await expect(page.locator('h1:has-text("Stock Management")')).toBeVisible();
    
    // Check that the navigation is on the correct page
    await expect(page.locator('nav a:has-text("Stock Management")')).toHaveClass(/bg-blue-600/);
    
    // Check that the search input is present
    await expect(page.locator('#searchInput')).toBeVisible();
    
    // Check that the table is present (even if empty)
    await expect(page.locator('#stocksTableBody')).toBeVisible();
    
    // Check that action buttons are present (in the search and actions section)
    await expect(page.locator('button:has-text("Upload CSV")')).toBeVisible();
    // There are two buttons with "Alerts" text, we want the one that opens the modal
    await expect(page.locator('button:has-text("Alerts"):not(:has-text("Check Alerts Now"))')).toBeVisible();
    // There are two buttons with "Add Stock" text, we want the one that opens the modal
    await expect(page.locator('button:has-text("Add Stock"):not([type="submit"])')).toBeVisible();
  });

  test('should load price entry page successfully', async ({ page }) => {
    await page.goto('/price-entry.html');
    await expect(page).toHaveTitle(/Stock Tracker - Price Entry/);
    
    // Check that the main heading is visible
    await expect(page.locator('h1:has-text("Price Entry")')).toBeVisible();
    
    // Check that the navigation is on the correct page
    await expect(page.locator('nav a:has-text("Price Entry")')).toHaveClass(/bg-blue-600/);
    
    // Check that the form is present
    await expect(page.locator('#priceForm')).toBeVisible();
    
    // Check that the stock select is present
    await expect(page.locator('#stockSelect')).toBeVisible();
    
    // Check that form fields are present
    await expect(page.locator('#priceDate')).toBeVisible();
    await expect(page.locator('#openingPrice')).toBeVisible();
    await expect(page.locator('#closingPrice')).toBeVisible();
    await expect(page.locator('#highPrice')).toBeVisible();
    await expect(page.locator('#lowPrice')).toBeVisible();
    await expect(page.locator('#volume')).toBeVisible();
    
    // Check that submit button is present
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('should load RSI analysis page successfully', async ({ page }) => {
    await page.goto('/rsi-analysis.html');
    await expect(page).toHaveTitle(/Stock Tracker - RSI Analysis/);
    
    // Check that the main heading is visible
    await expect(page.locator('h1:has-text("RSI Analysis")')).toBeVisible();
    
    // Check that the navigation is on the correct page
    await expect(page.locator('nav a:has-text("RSI Analysis")')).toHaveClass(/bg-blue-600/);
    
    // Check that the RSI summary cards are present
    await expect(page.locator('#oversoldCount')).toBeVisible();
    await expect(page.locator('#overboughtCount')).toBeVisible();
    
    // Check that the RSI table is present
    await expect(page.locator('#rsiTableBody')).toBeVisible();
    
    // Check that the recalculate button is present
    await expect(page.locator('button:has-text("Recalculate All RSI")')).toBeVisible();
    
    // Check that sort buttons are present
    await expect(page.locator('button:has-text("Sort by RSI (Low to High)")')).toBeVisible();
    await expect(page.locator('button:has-text("Sort by RSI (High to Low)")')).toBeVisible();
  });

  test('should load stock history page successfully', async ({ page }) => {
    await page.goto('/stock-history.html');
    await expect(page).toHaveTitle(/Stock History Dashboard/);
    
    // Check that the main heading is visible
    await expect(page.locator('h1:has-text("Stock History Dashboard")')).toBeVisible();
    
    // Check that the navigation is on the correct page
    await expect(page.locator('nav a:has-text("Stock History")')).toHaveClass(/bg-blue-600/);
    
    // Check that the symbol input is present
    await expect(page.locator('#symbolInput')).toBeVisible();
    
    // Check that the days select is present
    await expect(page.locator('#daysSelect')).toBeVisible();
    
    // Check that action buttons are present
    await expect(page.locator('button:has-text("Fetch Data")')).toBeVisible();
    await expect(page.locator('button:has-text("Refresh")')).toBeVisible();
    // The "Save to DB" button is hidden by default and only appears when data is loaded
    await expect(page.locator('button:has-text("Export CSV")')).toBeVisible();
    
    // Check that quick symbol chips are present
    await expect(page.locator('button:has-text("RELIANCE.NS")')).toBeVisible();
    await expect(page.locator('button:has-text("TCS.NS")')).toBeVisible();
    await expect(page.locator('button:has-text("INFY.NS")')).toBeVisible();
    await expect(page.locator('button:has-text("HDFCBANK.NS")')).toBeVisible();
    await expect(page.locator('button:has-text("ICICIBANK.NS")')).toBeVisible();
    
    // Check that content sections are present (they might be hidden initially)
    // Just check that the elements exist in the DOM
    await expect(await page.locator('#contentState').count()).toBeGreaterThan(0);
    await expect(await page.locator('#stockChart').count()).toBeGreaterThan(0);
    await expect(await page.locator('#historyTableBody').count()).toBeGreaterThan(0);
    await expect(await page.locator('#paginationInfo').count()).toBeGreaterThan(0);
  });

  test('should load history summary page successfully', async ({ page }) => {
    await page.goto('/history-summary.html');
    await expect(page).toHaveTitle(/Stock Tracker - History Summary/);
    
    // Check that the main heading is visible
    await expect(page.locator('h1:has-text("Historical Data Summary")')).toBeVisible();
    
    // Check that the navigation is on the correct page
    await expect(page.locator('nav a:has-text("History Summary")')).toHaveClass(/bg-blue-600/);
    
    // Check that the days select is present
    await expect(page.locator('#daysSelect')).toBeVisible();
    
    // Check that the fetch button is present
    await expect(page.locator('button:has-text("Fetch All Summaries")')).toBeVisible();
    
    // Check that state containers exist in the DOM (they may be hidden)
    await expect(await page.locator('#loadingState').count()).toBeGreaterThan(0);
    await expect(await page.locator('#errorState').count()).toBeGreaterThan(0);
    await expect(await page.locator('#emptyState').count()).toBeGreaterThan(0);
    await expect(await page.locator('#contentState').count()).toBeGreaterThan(0);
    
    // Check that table is present in content state (in DOM)
    await expect(await page.locator('#resultsTableBody').count()).toBeGreaterThan(0);
  });

  test('should navigate between pages via header navigation', async ({ page }) => {
    await page.goto('/index.html');
    
    // Click on Stock Management link
    await page.click('nav a:has-text("Stock Management")');
    await expect(page).toHaveURL(/.*stocks.html/);
    await expect(page.locator('h1:has-text("Stock Management")')).toBeVisible();
    
    // Click on Price Entry link
    await page.click('nav a:has-text("Price Entry")');
    await expect(page).toHaveURL(/.*price-entry.html/);
    await expect(page.locator('h1:has-text("Price Entry")')).toBeVisible();
    
    // Click on RSI Analysis link
    await page.click('nav a:has-text("RSI Analysis")');
    await expect(page).toHaveURL(/.*rsi-analysis.html/);
    await expect(page.locator('h1:has-text("RSI Analysis")')).toBeVisible();
    
    // Click on Stock History link
    await page.click('nav a:has-text("Stock History")');
    await expect(page).toHaveURL(/.*stock-history.html/);
    await expect(page.locator('h1:has-text("Stock History Dashboard")')).toBeVisible();
    
    // Click on History Summary link
    await page.click('nav a:has-text("History Summary")');
    await expect(page).toHaveURL(/.*history-summary.html/);
    await expect(page.locator('h1:has-text("Historical Data Summary")')).toBeVisible();
    
    // Click back to Dashboard
    await page.click('nav a:has-text("Dashboard")');
    await expect(page).toHaveURL(/.*index.html/);
    await expect(page.locator('h1:has-text("Portfolio Dashboard")')).toBeVisible();
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // Go to dashboard
    await page.goto('/index.html');
    
    // Wait for the page to load
    await page.waitForTimeout(2000);
    
    // Check that we don't see any obvious error messages in the UI
    // The application should show loading states or empty states, not crash
    const loadingStates = await page.locator('.skeleton').count();
    const emptyStates = await page.locator('text=/No holdings found|No data|Loading/i').count();
    
    // At least one of these should be present if there's no data
    expect(loadingStates + emptyStates).toBeGreaterThan(0);
  });

  test('should verify theme toggle works', async ({ page }) => {
    await page.goto('/index.html');
    
    // Check initial state (should be dark by default based on body class)
    await expect(page.locator('body')).toHaveClass(/dark/);
    
    // Click the theme toggle button (the button that contains the icon with id="themeIcon")
    await page.click('button:has(#themeIcon)');
    
    // Check that we switched to light theme
    await expect(page.locator('body')).toHaveClass(/light/);
    
    // Click again to switch back
    await page.click('button:has(#themeIcon)');
    
    // Check that we're back to dark theme
    await expect(page.locator('body')).toHaveClass(/dark/);
  });

  test('should test search functionality in stock management', async ({ page }) => {
    await page.goto('/stocks.html');
    
    // Wait for page to load
    await page.waitForTimeout(2000);
    
    // Type in search box
    await page.fill('#searchInput', 'RELIANCE');
    
    // Wait for filtering to occur
    await page.waitForTimeout(1000);
    
    // Clear search
    await page.fill('#searchInput', '');
    await page.waitForTimeout(1000);
  });

  test('should test sorting functionality in holdings table', async ({ page }) => {
    await page.goto('/index.html');
    
    // Wait for page to load
    await page.waitForTimeout(2000);
    
    // Click on a sortable header to test sorting
    await page.click('th:has-text("Symbol")');
    await page.waitForTimeout(1000);
    
    // Click again to reverse sort
    await page.click('th:has-text("Symbol")');
    await page.waitForTimeout(1000);
  });

  test('should test portfolio recalculation', async ({ page }) => {
    await page.goto('/index.html');
    
    // Wait for page to load
    await page.waitForTimeout(2000);
    
    // Click recalculate button
    await page.click('button:has-text("Recalculate All")');
    
    // Wait for recalculation to complete (show toast)
    await page.waitForTimeout(2000);
  });

  test('should test RSI analysis functionality', async ({ page }) => {
    await page.goto('/rsi-analysis.html');
    
    // Wait for page to load
    await page.waitForTimeout(2000);
    
    // Click sort buttons
    await page.click('button:has-text("Sort by RSI (Low to High)")');
    await page.waitForTimeout(1000);
    
    await page.click('button:has-text("Sort by RSI (High to Low)")');
    await page.waitForTimeout(1000);
    
    // Click recalculate button
    await page.click('button:has-text("Recalculate All RSI")');
    await page.waitForTimeout(2000);
  });

  test('should test stock history functionality', async ({ page }) => {
    // Mock stock history API to return reliable data (backend may be slow under parallel load)
    const historyData = Array.from({ length: 50 }, (_, i) => ({
      date: `2024-${String(i % 12 + 1).padStart(2, '0')}-${String(i % 28 + 1).padStart(2, '0')}`,
      open: 2800 + Math.random() * 100,
      high: 2900 + Math.random() * 50,
      low: 2750 + Math.random() * 50,
      close: 2850 + Math.random() * 80,
      volume: 1000000 + Math.floor(Math.random() * 500000),
    }));

    await page.route('**/api/stocks/history**', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            symbol: 'RELIANCE.NS',
            currentPrice: 2850,
            highestPrice: 2900,
            lowestPrice: 2800,
            averageClose: 2850,
            percentChange: 2.5,
            history: historyData,
          },
          message: 'History loaded',
        }),
      });
    });

    await page.goto('/stock-history.html');
    await page.waitForTimeout(1000);

    // Enter a symbol and fetch data
    await page.fill('#symbolInput', 'RELIANCE.NS');
    await page.selectOption('#daysSelect', '30');
    await page.click('button:has-text("Fetch Data")');

    // Wait for content to render (mock returns instantly)
    await page.waitForSelector('#contentState:not(.hidden)', { timeout: 10000 });

    // Test table search
    await page.fill('#tableSearch', '2024');
    await page.waitForTimeout(500);
    await page.fill('#tableSearch', '');
    await page.waitForTimeout(500);

    // Test pagination
    await page.click('button:has-text("Next")');
    await page.waitForTimeout(500);
    await page.click('button:has-text("Previous")');
    await page.waitForTimeout(500);

    // Test table sorting
    await page.click('th:has-text("Date")');
    await page.waitForTimeout(500);
    await page.click('th:has-text("Close")');
    await page.waitForTimeout(500);
  });

  test('should test history summary functionality', async ({ page }) => {
    await page.goto('/history-summary.html');
    
    // Wait for page to load
    await page.waitForTimeout(2000);
    
    // Select different time periods
    await page.selectOption('#daysSelect', '7');
    await page.waitForTimeout(1000);
    await page.selectOption('#daysSelect', '30');
    await page.waitForTimeout(1000);
    await page.selectOption('#daysSelect', '200');
    await page.waitForTimeout(1000);
    
    // Click fetch button
    await page.click('button:has-text("Fetch All Summaries")');
    await page.waitForTimeout(3000);
    
    // Test table search - first check if the element exists and is visible
    const tableSearch = page.locator('#tableSearch');
    if (await tableSearch.isVisible()) {
      await tableSearch.fill('RELIANCE');
      await page.waitForTimeout(1000);
      await tableSearch.fill('');
      await page.waitForTimeout(1000);
    }
  });

  test('should verify data tables display correctly', async ({ page }) => {
    // Mock common APIs to ensure pages load reliably under parallel load
    async function mockPageApis(page) {
      await page.route('**/api/startup-tasks**', route => {
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
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
      await page.route('**/api/signals**', route => {
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
      });
      await page.route('**/api/watchlists**', route => {
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
      });
    }

    await mockPageApis(page);

    // Test holdings table on dashboard
    await page.goto('/index.html');
    await page.waitForSelector('#holdingsTable', { timeout: 10000 });

    const holdingsTable = await page.locator('#holdingsTable');
    await expect(holdingsTable).toBeVisible();

    // Check table headers
    const headers = await holdingsTable.locator('thead th').allTextContents();
    expect(headers.length).toBeGreaterThan(0);

    // Test stocks table
    await page.goto('/stocks.html');
    await page.waitForSelector('#stocksTableBody', { timeout: 10000 });

    const stocksTable = await page.locator('#stocksTableBody');
    await expect(stocksTable).toBeVisible();

    // Test RSI table
    await page.goto('/rsi-analysis.html');
    await page.waitForSelector('#rsiTableBody', { timeout: 10000 });

    const rsiTable = await page.locator('#rsiTableBody');
    await expect(rsiTable).toBeVisible();

    // Test history summary table
    await page.goto('/history-summary.html');
    // resultsTableBody exists in DOM but may be hidden until data loads
    await page.waitForSelector('#resultsTableBody', { state: 'attached', timeout: 10000 });

    const summaryTable = await page.locator('#resultsTableBody');
    await expect(summaryTable).toBeAttached();
  });

  test('should verify portfolio calculations are displayed correctly', async ({ page }) => {
    await page.goto('/index.html');
    await page.waitForTimeout(2000);
    
    // Check that summary cards have values (even if --)
    await expect(page.locator('#totalInvestment')).toHaveText(/--|₹/);
    await expect(page.locator('#totalCurrentValue')).toHaveText(/--|₹/);
    await expect(page.locator('#totalPnl')).toHaveText(/--|₹/);
    await expect(page.locator('#totalPnlPct')).toHaveText(/--|%/);
    await expect(page.locator('#holdingsCount')).toHaveText(/--|\d+/);
    await expect(page.locator('#winRate')).toHaveText(/--|%/);
    
    // Check that if values are present, they are formatted correctly
    const totalInvestment = await page.locator('#totalInvestment').textContent();
    if (totalInvestment !== '--') {
      expect(totalInvestment).toMatch(/^₹[\d,]+\.\d{2}$/);
    }
    
    const totalCurrentValue = await page.locator('#totalCurrentValue').textContent();
    if (totalCurrentValue !== '--') {
      expect(totalCurrentValue).toMatch(/^₹[\d,]+\.\d{2}$/);
    }
  });

  test('should verify technical indicators render correctly', async ({ page }) => {
    await page.goto('/rsi-analysis.html');
    await page.waitForTimeout(2000);
    
    // RSI table body should exist (may be empty if no data)
    await expect(page.locator('#rsiTableBody')).toBeVisible();
    
    // Check that summary cards are present
    await expect(page.locator('#oversoldCount')).toBeVisible();
    await expect(page.locator('#overboughtCount')).toBeVisible();
    
    // Check that action buttons are present
    await expect(page.locator('button:has-text("Recalculate All RSI")')).toBeVisible();
    await expect(page.locator('button:has-text("Sort by RSI (Low to High)")')).toBeVisible();
    await expect(page.locator('button:has-text("Sort by RSI (High to Low)")')).toBeVisible();
  });
});
