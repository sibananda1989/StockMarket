const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  page.on('console', msg => {
    const text = msg.text();
    if (msg.type() === 'error') {
      console.log('[ERROR]', text);
    } else {
      console.log('[LOG]', text);
    }
  });

  try {
    await page.goto('http://localhost:8080/stock-detail.html?id=7877', {
      waitUntil: 'networkidle',
      timeout: 30000,
    });

    await page.waitForTimeout(10000);

    // Directly test tooltip rendering by calling the crosshair callback with mock data
    const result = await page.evaluate(() => {
      // Simulate what happens in the crosshair callback
      const mockSig = {
        priceDate: '2026-07-13',
        recommendation: 'HOLD',
        compositeScore: 0,
        rsiScore: 0,
        smaScore: 0,
        bollingerScore: 0,
        macdScore: 0,
        trendDirectionScore: 0,
        divergenceScore: 0,
        weeklyConfluenceScore: 0,
        fiidiiScore: 1,
        candlestickPattern: null,
        candlestickScore: 0,
        strategyBreakdown: [
          {
            strategyName: 'RSI',
            signal: 'HOLD',
            confidence: 0.6,
            priority: 7,
            weightedScore: 0.0,
            reason: 'RSI neutral-low (44.6) | ADX ranging: mean-reversion boosted'
          },
          {
            strategyName: 'MACD',
            signal: 'BUY',
            confidence: 0.017995,
            priority: 7,
            weightedScore: 0.125965,
            reason: 'MACD line above signal'
          }
        ],
        buyThreshold: 3.0,
        sellThreshold: -3.0,
        rawScore: 0.2559649999999998
      };

      // Test the condition from the tooltip code
      const hasBreakdown = mockSig.strategyBreakdown && mockSig.strategyBreakdown.length && mockSig.buyThreshold != null;
      
      // Test building rows
      let rowsHtml = '';
      try {
        rowsHtml = mockSig.strategyBreakdown.map(s => {
          const sc = s.signal === 'BUY' ? '#22c55e' : s.signal === 'SELL' ? '#ef4444' : '#6b7280';
          const sbg = s.signal === 'BUY' ? 'rgba(34,197,94,0.1)' : s.signal === 'SELL' ? 'rgba(239,68,68,0.1)' : 'transparent';
          return `
            <div style="display:flex;flex-direction:column;gap:1px;padding:4px 6px;border-radius:6px;background:${sbg};">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <span style="font-weight:600;color:#e2e8f0;font-size:10px;">${s.strategyName}</span>
                <span style="color:${sc};font-weight:700;font-size:10px;">${s.signal}</span>
              </div>
              <div style="display:flex;justify-content:space-between;color:#64748b;font-size:9px;">
                <span>${(s.confidence * 100).toFixed(0)}% confidence</span>
                <span>priority ${s.priority} × ${(s.confidence * 100).toFixed(0)}% = <strong style="color:#e2e8f0;">${(s.weightedScore ?? s.contribution ?? 0).toFixed(2)}</strong></span>
              </div>
              ${s.reason ? `<div style="color:#64748b;font-size:9px;margin-top:1px;line-height:1.3;">${s.reason}</div>` : ''}
            </div>`;
        }).join('');
      } catch (e) {
        return { error: 'Row build failed: ' + e.message };
      }

      // Test threshold calculations
      let thresholdColor, thresholdLabel, thresholdDetail;
      try {
        thresholdColor = mockSig.rawScore != null
          ? (mockSig.rawScore >= mockSig.buyThreshold ? '#22c55e' :
             mockSig.rawScore <= mockSig.sellThreshold ? '#ef4444' : '#6b7280')
          : '#6b7280';
        thresholdLabel = mockSig.rawScore != null
          ? (mockSig.rawScore >= mockSig.buyThreshold ? '✓ BUY' :
             mockSig.rawScore <= mockSig.sellThreshold ? '✓ SELL' : '→ HOLD')
          : '—';
        thresholdDetail = mockSig.rawScore >= mockSig.buyThreshold ?
          `${mockSig.rawScore.toFixed(2)} ≥ ${mockSig.buyThreshold}` :
          mockSig.rawScore <= mockSig.sellThreshold ?
          `${mockSig.rawScore.toFixed(2)} ≤ ${mockSig.sellThreshold}` :
          `between thresholds`;
      } catch (e) {
        return { error: 'Threshold calc failed: ' + e.message };
      }

      return {
        hasBreakdown,
        rowsHtmlLength: rowsHtml.length,
        thresholdColor,
        thresholdLabel,
        thresholdDetail,
        rowsHtml: rowsHtml.substring(0, 1000)
      };
    });

    console.log('\n=== TOOLTIP RENDERING TEST ===');
    console.log(JSON.stringify(result, null, 2));

    // Also check if there are any issues with the chart crosshair by accessing the chart
    const chartInfo = await page.evaluate(() => {
      const container = document.getElementById('chartCandlestick');
      if (!container) return { error: 'No container' };
      
      // Try to find the lightweight-charts instance
      const lwContainer = container.querySelector('.tv-lightweight-charts');
      if (!lwContainer) return { error: 'No LW container' };
      
      return {
        lwExists: true,
        lwWidth: lwContainer.offsetWidth,
        lwHeight: lwContainer.offsetHeight,
        hasCanvas: !!lwContainer.querySelector('canvas')
      };
    });

    console.log('\n=== CHART INFO ===');
    console.log(JSON.stringify(chartInfo, null, 2));

  } catch (error) {
    console.error('Test error:', error.message);
  } finally {
    await browser.close();
  }
})();
