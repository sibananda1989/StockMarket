# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: signals-insights.spec.js >> Dashboard Signals & Insights >> should show recommendation badges with correct colors
- Location: tests/signals-insights.spec.js:200:3

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: locator('#signalsTableBody tr').first()
Timeout: 5000ms
- Expected substring  -  1
+ Received string     + 20

- STRONG BUY
+
+       RELIANCE
+       
+         HOLD
+       
+       28.5
+       ₹3,200.00
+       ₹2,600.00
+       85
+       5%
+       1d
+       +5.2%
+       -8.1%
+       Bullish
+       --
+       --
+       --
+       +12.30%
+       +4
+     

Call log:
  - Expect "toContainText" with timeout 5000ms
  - waiting for locator('#signalsTableBody tr').first()
    14 × locator resolved to <tr class="signal-row">…</tr>
       - unexpected value "
      RELIANCE
      
        HOLD
      
      28.5
      ₹3,200.00
      ₹2,600.00
      85
      5%
      1d
      +5.2%
      -8.1%
      Bullish
      --
      --
      --
      +12.30%
      +4
    "

```

```yaml
- row "RELIANCE HOLD  28.5 ₹3,200.00 ₹2,600.00 85 5% 1d +5.2% -8.1%  Bullish -- -- -- +12.30% +4":
  - cell "RELIANCE":
    - link "RELIANCE":
      - /url: stock-detail.html?id=1
  - cell "HOLD "
  - cell "28.5"
  - cell "₹3,200.00"
  - cell "₹2,600.00"
  - cell "85"
  - cell "5%"
  - cell "1d"
  - cell "+5.2%"
  - cell "-8.1%"
  - cell " Bullish"
  - cell "--"
  - cell "--"
  - cell "--"
  - cell "+12.30%"
  - cell "+4"
```

# Test source

```ts
  108 |     });
  109 | 
  110 |     await page.route('**/api/startup-tasks**', route => {
  111 |       route.fulfill({
  112 |         status: 200,
  113 |         contentType: 'application/json',
  114 |         body: JSON.stringify({ success: true, data: [], message: 'No pending tasks' }),
  115 |       });
  116 |     });
  117 | 
  118 |     await page.route('**/api/portfolios**', route => {
  119 |       route.fulfill({
  120 |         status: 200,
  121 |         contentType: 'application/json',
  122 |         body: JSON.stringify({ success: true, data: [], message: 'No portfolios' }),
  123 |       });
  124 |     });
  125 | 
  126 |     await page.route('**/api/stocks**', route => {
  127 |       route.fulfill({
  128 |         status: 200,
  129 |         contentType: 'application/json',
  130 |         body: JSON.stringify({ success: true, data: [], message: 'No stocks' }),
  131 |       });
  132 |     });
  133 | 
  134 |     await page.route('**/api/portfolio/history**', route => {
  135 |       route.fulfill({
  136 |         status: 200,
  137 |         contentType: 'application/json',
  138 |         body: JSON.stringify({ success: true, data: [], message: 'No history' }),
  139 |       });
  140 |     });
  141 | 
  142 |     await page.evaluate(() => {
  143 |       try {
  144 |         localStorage.removeItem('startupTasksSnoozeExpiry');
  145 |         localStorage.removeItem('startupTasksSnoozeDuration');
  146 |       } catch (e) {}
  147 |     });
  148 |   }
  149 | 
  150 |   // First test uses pure page.route to avoid addInitScript race condition on fresh pages
  151 |   test('should show signal stats counters with correct values', async ({ page }) => {
  152 |     await setupMocksRouteOnly(page);
  153 |     await page.goto('/index.html');
  154 |     // Wait for signals data to actually render (static section exists immediately)
  155 |     await page.waitForSelector('#signalsTableBody tr td a', { timeout: 10000 });
  156 | 
  157 |     // Default filters show only STRONG BUY, BUY, HOLD (SELL/STRONG SELL unchecked)
  158 |     // Mocks: RELIANCE=STRONG BUY, TCS=BUY, HDFCBANK=HOLD
  159 |     await expect(page.locator('#strongBuyCount')).toHaveText('1');
  160 |     await expect(page.locator('#buyCount')).toHaveText('1');
  161 |     await expect(page.locator('#holdCount')).toHaveText('1');
  162 |     await expect(page.locator('#sellCount')).toHaveText('0');
  163 |     await expect(page.locator('#strongSellCount')).toHaveText('0');
  164 |   });
  165 | 
  166 |   test('should render signal table with all stock rows', async ({ page }) => {
  167 |     await setupMocks(page);
  168 |     await page.goto('/index.html');
  169 |     await page.waitForSelector('#signalsSection');
  170 |     await page.waitForSelector('#signalsTableBody tr td a');
  171 | 
  172 |     // Default filters show only STRONG BUY, BUY, HOLD (3 rows)
  173 |     await expect(page.locator('#signalsTableBody tr')).toHaveCount(3);
  174 |     await expect(page.locator('#signalsTableBody')).toContainText('RELIANCE');
  175 |     await expect(page.locator('#signalsTableBody')).toContainText('TCS');
  176 |     await expect(page.locator('#signalsTableBody')).toContainText('HDFCBANK');
  177 |     await expect(page.locator('#signalsTableBody')).not.toContainText('INFY');
  178 |     await expect(page.locator('#signalsTableBody')).not.toContainText('ICICIBANK');
  179 |   });
  180 | 
  181 |   test('should show buy and sell opportunity cards', async ({ page }) => {
  182 |     await setupMocks(page);
  183 |     await page.goto('/index.html');
  184 |     await page.waitForSelector('#signalsSection');
  185 |     await page.waitForSelector('#signalsTableBody tr td a');
  186 | 
  187 |     const buyCard = page.locator('#buySignalsCard');
  188 |     const sellCard = page.locator('#sellSignalsCard');
  189 | 
  190 |     // Default filters show STRONG BUY, BUY, HOLD — only buy signals shown
  191 |     await expect(buyCard).toContainText('RELIANCE');
  192 |     await expect(buyCard).toContainText('STRONG BUY');
  193 |     await expect(buyCard).toContainText('TCS');
  194 |     await expect(buyCard).toContainText('BUY');
  195 | 
  196 |     await expect(buyCard).not.toContainText('INFY');
  197 |     await expect(sellCard).toContainText('No sell warnings');
  198 |   });
  199 | 
  200 |   test('should show recommendation badges with correct colors', async ({ page }) => {
  201 |     await setupMocks(page);
  202 |     await page.goto('/index.html');
  203 |     await page.waitForSelector('#signalsSection');
  204 |     await page.waitForSelector('#signalsTableBody tr td a');
  205 | 
  206 |     // Default filters: first 3 rows are STRONG BUY, BUY, HOLD
  207 |     const strongBuy = page.locator('#signalsTableBody tr').nth(0);
> 208 |     await expect(strongBuy).toContainText('STRONG BUY');
      |                             ^ Error: expect(locator).toContainText(expected) failed
  209 | 
  210 |     const buy = page.locator('#signalsTableBody tr').nth(1);
  211 |     await expect(buy).toContainText('BUY');
  212 | 
  213 |     const hold = page.locator('#signalsTableBody tr').nth(2);
  214 |     await expect(hold).toContainText('HOLD');
  215 |   });
  216 | 
  217 |   test('should sort signals by composite score descending by default', async ({ page }) => {
  218 |     await setupMocks(page);
  219 |     await page.goto('/index.html');
  220 |     await page.waitForSelector('#signalsSection');
  221 |     await page.waitForSelector('#signalsTableBody tr td a');
  222 | 
  223 |     const firstSymbol = await page.locator('#signalsTableBody tr').nth(0).locator('td').nth(0).textContent();
  224 |     expect(firstSymbol).toBe('RELIANCE');
  225 |   });
  226 | 
  227 |   test('should filter signals by symbol search', async ({ page }) => {
  228 |     await setupMocks(page);
  229 |     await page.goto('/index.html');
  230 |     await page.waitForSelector('#signalsSection');
  231 |     await page.waitForSelector('#signalsTableBody tr td a');
  232 | 
  233 |     await page.fill('#filterSymbol', 'BANK');
  234 |     await page.click('text=Apply');
  235 | 
  236 |     // Default filters show HOLD only (SELL/STRONG SELL unchecked)
  237 |     // Only HDFCBANK (HOLD) matches, ICICIBANK is STRONG SELL (filtered out)
  238 |     await expect(page.locator('#signalsTableBody tr')).toHaveCount(1);
  239 |     await expect(page.locator('#signalsTableBody')).toContainText('HDFCBANK');
  240 |     await expect(page.locator('#signalsTableBody')).not.toContainText('ICICIBANK');
  241 |   });
  242 | 
  243 |   test('should filter signals by recommendation checkboxes', async ({ page }) => {
  244 |     await setupMocks(page);
  245 |     await page.goto('/index.html');
  246 |     await page.waitForSelector('#signalsSection');
  247 |     await page.waitForSelector('#signalsTableBody tr td a');
  248 | 
  249 |     await page.uncheck('input.rec-filter[value="BUY"]');
  250 |     await page.uncheck('input.rec-filter[value="HOLD"]');
  251 |     await page.uncheck('input.rec-filter[value="SELL"]');
  252 |     await page.uncheck('input.rec-filter[value="STRONG SELL"]');
  253 |     await page.click('text=Apply');
  254 | 
  255 |     await expect(page.locator('#signalsTableBody tr')).toHaveCount(1);
  256 |     await expect(page.locator('#signalsTableBody')).toContainText('RELIANCE');
  257 |   });
  258 | 
  259 |   test('should filter by RSI min/max', async ({ page }) => {
  260 |     await setupMocks(page);
  261 |     await page.goto('/index.html');
  262 |     await page.waitForSelector('#signalsSection');
  263 |     await page.waitForSelector('#signalsTableBody tr td a');
  264 | 
  265 |     await page.fill('#filterRsiMin', '40');
  266 |     await page.fill('#filterRsiMax', '75');
  267 |     await page.click('text=Apply');
  268 | 
  269 |     // Default filters show STRONG BUY, BUY, HOLD
  270 |     // RSI range 40-75 matches: HDFCBANK (RSI 45, HOLD)
  271 |     // INFY (RSI 72.5) would match but is SELL (filtered out by default)
  272 |     // TCS (RSI 32) and RELIANCE (RSI 28.5) are below 40
  273 |     await expect(page.locator('#signalsTableBody tr')).toHaveCount(1);
  274 |     await expect(page.locator('#signalsTableBody')).toContainText('HDFCBANK');
  275 |   });
  276 | 
  277 |   test('should filter by min composite score', async ({ page }) => {
  278 |     await setupMocks(page);
  279 |     await page.goto('/index.html');
  280 |     await page.waitForSelector('#signalsSection');
  281 |     await page.waitForSelector('#signalsTableBody tr td a');
  282 | 
  283 |     await page.fill('#filterCompositeMin', '6');
  284 |     await page.click('text=Apply');
  285 | 
  286 |     await expect(page.locator('#signalsTableBody tr')).toHaveCount(1);
  287 |     await expect(page.locator('#signalsTableBody')).toContainText('RELIANCE');
  288 |   });
  289 | 
  290 |   test('should sort by column when clicking header', async ({ page }) => {
  291 |     await setupMocks(page);
  292 |     await page.goto('/index.html');
  293 |     await page.waitForSelector('#signalsSection');
  294 |     await page.waitForSelector('#signalsTableBody tr td a');
  295 | 
  296 |     await page.locator('#signalsTable th:has-text("Confidence")').click();
  297 |     await page.waitForTimeout(200);
  298 | 
  299 |     // Default filters: only RELIANCE (85), TCS (65), HDFCBANK (50)
  300 |     // Sorted by confidence descending: RELIANCE first (85)
  301 |     const firstSymbol = await page.locator('#signalsTableBody tr').nth(0).locator('td').nth(0).textContent();
  302 |     expect(firstSymbol).toBe('RELIANCE');
  303 |   });
  304 | 
  305 |   test('should toggle sort direction on click', async ({ page }) => {
  306 |     await setupMocks(page);
  307 |     await page.goto('/index.html');
  308 |     await page.waitForSelector('#signalsSection');
```