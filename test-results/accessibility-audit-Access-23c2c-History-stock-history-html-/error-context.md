# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: accessibility-audit.spec.js >> Accessibility Audit (axe-core) >> Stock History (/stock-history.html)
- Location: tests/accessibility-audit.spec.js:26:5

# Error details

```
Error: page.evaluate: Execution context was destroyed, most likely because of a navigation
```

# Test source

```ts
  1   | const { test, expect } = require('@playwright/test');
  2   | const AxeBuilder = require('@axe-core/playwright').default;
  3   | 
  4   | // All static HTML pages served by the app
  5   | const PAGES = [
  6   |   { path: '/', name: 'Dashboard' },
  7   |   { path: '/strategy.html', name: 'Strategy Manager' },
  8   |   { path: '/stocks.html', name: 'Stock Management' },
  9   |   { path: '/watchlist.html', name: 'Watchlists' },
  10  |   { path: '/watchlist-opportunities.html', name: 'Watchlist Opportunities' },
  11  |   { path: '/stock-detail.html?id=1', name: 'Stock Detail' },
  12  |   { path: '/institutional-dashboard.html', name: 'Institutional Dashboard' },
  13  |   { path: '/fundamentals-screener.html', name: 'Fundamentals Screener' },
  14  |   { path: '/rsi-analysis.html', name: 'RSI Analysis' },
  15  |   { path: '/stock-history.html', name: 'Stock History' },
  16  |   { path: '/history-summary.html', name: 'History Summary' },
  17  |   { path: '/price-entry.html', name: 'Price Entry' },
  18  | ];
  19  | 
  20  | // Global accumulator for summary report
  21  | const allResults = {};
  22  | 
  23  | test.describe('Accessibility Audit (axe-core)', () => {
  24  | 
  25  |   for (const { path, name } of PAGES) {
  26  |     test(`${name} (${path})`, async ({ page }, testInfo) => {
  27  |       testInfo.setTimeout(30000);
  28  | 
  29  |       await page.goto(path, { waitUntil: 'networkidle', timeout: 15000 }).catch(() => {});
  30  |       await page.waitForTimeout(1500);
  31  | 
  32  |       const results = await new AxeBuilder({ page })
  33  |         .withTags([
  34  |           'wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa',
  35  |           'wcag22a', 'wcag22aa',
  36  |           'best-practice',
  37  |           'section508',
  38  |           'experimental'
  39  |         ])
  40  |         .disableRules(['css-orientation-lock'])
> 41  |         .analyze();
      |          ^ Error: page.evaluate: Execution context was destroyed, most likely because of a navigation
  42  | 
  43  |       // Store results
  44  |       allResults[name] = results;
  45  | 
  46  |       // Attach full results to test report
  47  |       await testInfo.attach('axe-results', {
  48  |         body: JSON.stringify({
  49  |           url: path,
  50  |           violations: results.violations.map(v => ({
  51  |             id: v.id,
  52  |             impact: v.impact,
  53  |             description: v.description,
  54  |             help: v.help,
  55  |             helpUrl: v.helpUrl,
  56  |             nodes: v.nodes.map(n => ({
  57  |               target: n.target.join(', '),
  58  |               html: n.html.slice(0, 200),
  59  |               failureSummary: n.failureSummary?.slice(0, 300) || ''
  60  |             }))
  61  |           })),
  62  |           incomplete: results.incomplete.map(v => ({
  63  |             id: v.id,
  64  |             description: v.description,
  65  |             nodes: v.nodes.map(n => n.target.join(', '))
  66  |           }))
  67  |         }, null, 2),
  68  |         contentType: 'application/json'
  69  |       });
  70  | 
  71  |       // Log violations to stdout for immediate visibility
  72  |       if (results.violations.length > 0) {
  73  |         console.log(`\n📄 ${name} (${path}): ${results.violations.length} violation(s)`);
  74  |         const grouped = {};
  75  |         results.violations.forEach(v => {
  76  |           const key = `${v.impact}:${v.id}`;
  77  |           if (!grouped[key]) grouped[key] = { impact: v.impact, id: v.id, help: v.help, helpUrl: v.helpUrl, count: 0 };
  78  |           grouped[key].count += v.nodes.length;
  79  |         });
  80  |         Object.values(grouped).forEach(g => {
  81  |           const icon = g.impact === 'critical' ? '🔴' : g.impact === 'serious' ? '🟠' : '🟡';
  82  |           console.log(`   ${icon} [${g.impact}] ${g.id}: ${g.count} element(s) — ${g.helpUrl}`);
  83  |         });
  84  |       } else {
  85  |         console.log(`\n✅ ${name} (${path}): No violations`);
  86  |       }
  87  |     });
  88  |   }
  89  | });
  90  | 
  91  | // Summary report after all tests complete
  92  | test.afterAll(async () => {
  93  |   console.log('\n═══════════════════════════════════════════════════════════════');
  94  |   console.log('  COMPLETE ACCESSIBILITY AUDIT REPORT');
  95  |   console.log('═══════════════════════════════════════════════════════════════\n');
  96  | 
  97  |   const summary = {};
  98  | 
  99  |   for (const [pageName, results] of Object.entries(allResults)) {
  100 |     const pageSummary = { critical: {}, serious: {}, moderate: {}, minor: {} };
  101 |     results.violations.forEach(v => {
  102 |       const sev = v.impact === 'critical' ? 'critical' : v.impact === 'serious' ? 'serious' : v.impact === 'moderate' ? 'moderate' : 'minor';
  103 |       if (!pageSummary[sev][v.id]) {
  104 |         pageSummary[sev][v.id] = {
  105 |           help: v.help,
  106 |           helpUrl: v.helpUrl,
  107 |           elements: 0
  108 |         };
  109 |       }
  110 |       pageSummary[sev][v.id].elements += v.nodes.length;
  111 |     });
  112 |     summary[pageName] = pageSummary;
  113 |   }
  114 | 
  115 |   // Per-page breakdown
  116 |   for (const [page, violations] of Object.entries(summary)) {
  117 |     const total = Object.values(violations).reduce((sum, sev) => sum + Object.keys(sev).length, 0);
  118 |     if (total === 0) {
  119 |       console.log(`✅ ${page}`);
  120 |     } else {
  121 |       console.log(`📄 ${page}:`);
  122 |       for (const [severity, issues] of Object.entries(violations)) {
  123 |         if (Object.keys(issues).length > 0) {
  124 |           const icon = severity === 'critical' ? '🔴' : severity === 'serious' ? '🟠' : severity === 'moderate' ? '🟡' : '⚪';
  125 |           console.log(`   ${icon} ${severity.toUpperCase()}:`);
  126 |           for (const [id, info] of Object.entries(issues)) {
  127 |             console.log(`      ${id} — ${info.help}`);
  128 |             console.log(`         ${info.elements} element(s) affected`);
  129 |             console.log(`         ${info.helpUrl}`);
  130 |           }
  131 |         }
  132 |       }
  133 |     }
  134 |   }
  135 | 
  136 |   // Totals
  137 |   let totalCrit = 0, totalSer = 0, totalMod = 0, totalMin = 0;
  138 |   for (const violations of Object.values(summary)) {
  139 |     totalCrit += Object.keys(violations.critical).length;
  140 |     totalSer += Object.keys(violations.serious).length;
  141 |     totalMod += Object.keys(violations.moderate).length;
```