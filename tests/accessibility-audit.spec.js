const { test, expect } = require('@playwright/test');
const AxeBuilder = require('@axe-core/playwright').default;

// All static HTML pages served by the app
const PAGES = [
  { path: '/', name: 'Dashboard' },
  { path: '/strategy.html', name: 'Strategy Manager' },
  { path: '/stocks.html', name: 'Stock Management' },
  { path: '/watchlist.html', name: 'Watchlists' },
  { path: '/watchlist-opportunities.html', name: 'Watchlist Opportunities' },
  { path: '/stock-detail.html?id=1', name: 'Stock Detail' },
  { path: '/institutional-dashboard.html', name: 'Institutional Dashboard' },
  { path: '/fundamentals-screener.html', name: 'Fundamentals Screener' },
  { path: '/rsi-analysis.html', name: 'RSI Analysis' },
  { path: '/stock-history.html', name: 'Stock History' },
  { path: '/history-summary.html', name: 'History Summary' },
  { path: '/price-entry.html', name: 'Price Entry' },
];

// Global accumulator for summary report
const allResults = {};

test.describe('Accessibility Audit (axe-core)', () => {

  for (const { path, name } of PAGES) {
    test(`${name} (${path})`, async ({ page }, testInfo) => {
      testInfo.setTimeout(30000);

      await page.goto(path, { waitUntil: 'networkidle', timeout: 15000 }).catch(() => {});
      await page.waitForTimeout(1500);

      const results = await new AxeBuilder({ page })
        .withTags([
          'wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa',
          'wcag22a', 'wcag22aa',
          'best-practice',
          'section508',
          'experimental'
        ])
        .disableRules(['css-orientation-lock'])
        .analyze();

      // Store results
      allResults[name] = results;

      // Attach full results to test report
      await testInfo.attach('axe-results', {
        body: JSON.stringify({
          url: path,
          violations: results.violations.map(v => ({
            id: v.id,
            impact: v.impact,
            description: v.description,
            help: v.help,
            helpUrl: v.helpUrl,
            nodes: v.nodes.map(n => ({
              target: n.target.join(', '),
              html: n.html.slice(0, 200),
              failureSummary: n.failureSummary?.slice(0, 300) || ''
            }))
          })),
          incomplete: results.incomplete.map(v => ({
            id: v.id,
            description: v.description,
            nodes: v.nodes.map(n => n.target.join(', '))
          }))
        }, null, 2),
        contentType: 'application/json'
      });

      // Log violations to stdout for immediate visibility
      if (results.violations.length > 0) {
        console.log(`\n📄 ${name} (${path}): ${results.violations.length} violation(s)`);
        const grouped = {};
        results.violations.forEach(v => {
          const key = `${v.impact}:${v.id}`;
          if (!grouped[key]) grouped[key] = { impact: v.impact, id: v.id, help: v.help, helpUrl: v.helpUrl, count: 0 };
          grouped[key].count += v.nodes.length;
        });
        Object.values(grouped).forEach(g => {
          const icon = g.impact === 'critical' ? '🔴' : g.impact === 'serious' ? '🟠' : '🟡';
          console.log(`   ${icon} [${g.impact}] ${g.id}: ${g.count} element(s) — ${g.helpUrl}`);
        });
      } else {
        console.log(`\n✅ ${name} (${path}): No violations`);
      }
    });
  }
});

// Summary report after all tests complete
test.afterAll(async () => {
  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  COMPLETE ACCESSIBILITY AUDIT REPORT');
  console.log('═══════════════════════════════════════════════════════════════\n');

  const summary = {};

  for (const [pageName, results] of Object.entries(allResults)) {
    const pageSummary = { critical: {}, serious: {}, moderate: {}, minor: {} };
    results.violations.forEach(v => {
      const sev = v.impact === 'critical' ? 'critical' : v.impact === 'serious' ? 'serious' : v.impact === 'moderate' ? 'moderate' : 'minor';
      if (!pageSummary[sev][v.id]) {
        pageSummary[sev][v.id] = {
          help: v.help,
          helpUrl: v.helpUrl,
          elements: 0
        };
      }
      pageSummary[sev][v.id].elements += v.nodes.length;
    });
    summary[pageName] = pageSummary;
  }

  // Per-page breakdown
  for (const [page, violations] of Object.entries(summary)) {
    const total = Object.values(violations).reduce((sum, sev) => sum + Object.keys(sev).length, 0);
    if (total === 0) {
      console.log(`✅ ${page}`);
    } else {
      console.log(`📄 ${page}:`);
      for (const [severity, issues] of Object.entries(violations)) {
        if (Object.keys(issues).length > 0) {
          const icon = severity === 'critical' ? '🔴' : severity === 'serious' ? '🟠' : severity === 'moderate' ? '🟡' : '⚪';
          console.log(`   ${icon} ${severity.toUpperCase()}:`);
          for (const [id, info] of Object.entries(issues)) {
            console.log(`      ${id} — ${info.help}`);
            console.log(`         ${info.elements} element(s) affected`);
            console.log(`         ${info.helpUrl}`);
          }
        }
      }
    }
  }

  // Totals
  let totalCrit = 0, totalSer = 0, totalMod = 0, totalMin = 0;
  for (const violations of Object.values(summary)) {
    totalCrit += Object.keys(violations.critical).length;
    totalSer += Object.keys(violations.serious).length;
    totalMod += Object.keys(violations.moderate).length;
    totalMin += Object.keys(violations.minor).length;
  }

  console.log('\n───────────────────────────────────────────────────────────────');
  console.log('  TOTALS');
  console.log('───────────────────────────────────────────────────────────────');
  console.log(`  🔴 Critical violations found:   ${totalCrit}`);
  console.log(`  🟠 Serious violations found:     ${totalSer}`);
  console.log(`  🟡 Moderate violations found:    ${totalMod}`);
  console.log(`  ⚪ Minor violations found:       ${totalMin}`);
  console.log('───────────────────────────────────────────────────────────────\n');
});
