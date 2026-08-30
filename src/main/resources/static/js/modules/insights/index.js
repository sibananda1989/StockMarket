// ─── Insights Tab — Signal Rendering, Score Breakdown, Timeline ──────────────
// Depends on: modules/state.js, modules/charts/line-charts.js

async function loadInsightsTab() {
    try {
        const days = parseInt(document.getElementById('dayRange').value) || 365;
        const signal = await getStockSignal(_stockId).catch(() => null);
        const signalData = signal && signal.status === 'success' ? signal.data : null;
        _cachedSignal = signalData;
        if (signalData && signalData.rsi14 != null) {
            _latestRsiValue = signalData.rsi14;
            updateRsiKpi(signalData.rsi14);
        }
        renderSignalContent(signalData);
        updateHeroSignal(signalData);
        renderScoreBreakdown(signalData);
        renderPriceAnalysis(signalData);

        const gen = _loadAllGen;
        getCachedSignalHistory(_stockId, days).then(res => {
            if (gen !== _loadAllGen) return;
            const historyData = res && res.status === 'success' ? res.data : null;
            renderSignalTimeline(historyData, signalData);
        }).catch(() => {});

        getLatestIndicators(_stockId).then(res => {
            if (gen !== _loadAllGen) return;
            var needsRecalc = !res || !res.length;
            if (!needsRecalc) {
                var today = new Date();
                var todayStr = today.getFullYear() + '-' + String(today.getMonth()+1).padStart(2,'0') + '-' + String(today.getDate()).padStart(2,'0');
                needsRecalc = res.every(function(ind) { return ind.calculationDate !== todayStr; });
            }
            if (needsRecalc) {
                calculateIndicators(_stockId).then(function() {
                    getLatestIndicators(_stockId).then(function(retry) {
                        if (gen !== _loadAllGen) return;
                        renderIndicatorsContent(retry);
                    }).catch(function() {});
                }).catch(function() {
                    renderIndicatorsContent(res);
                });
            } else {
                renderIndicatorsContent(res);
            }
        }).catch(function() {
            calculateIndicators(_stockId).then(function() {
                getLatestIndicators(_stockId).then(function(retry) {
                    if (gen !== _loadAllGen) return;
                    renderIndicatorsContent(retry);
                }).catch(function() {});
            }).catch(function() {});
        });

        loadRiskAssessment(signalData);
    } catch (e) {
        console.error('Insights tab error:', e);
        const fallback = document.getElementById('recommendationContent');
        if (fallback) fallback.innerHTML = '<p class="text-danger text-center py-8">⚠ Failed to load AI Insights. Ensure indicators are calculated for this stock.</p>';
    }
}

async function loadRiskAssessment(signalData) {
    try {
        renderRiskAssessment(signalData, null);
    } catch (e) {
        console.error('Risk assessment error:', e);
    }
}

// ─── Signal Content Renderer ─────────────────────────────────────────────────
function renderSignalContent(signal) {
    const el = document.getElementById('recommendationContent');
    if (!el) return;
    if (!signal) {
        el.innerHTML = '<p class="text-secondary text-center py-4">Insufficient data for signal</p>';
        return;
    }
    const rec = signal.recommendation || 'NEUTRAL';
    const rawScore = signal.compositeScore;
    const scoreDisplay = rawScore != null ? (rawScore >= 0 ? '+' : '') + rawScore : '--';
    const isBuy = rec === 'STRONG BUY' || rec === 'BUY';
    const isSell = rec === 'STRONG SELL' || rec === 'SELL';

    const indicatorCoverageOk = signal.indicatorCoverage === 19;
    const trendOk = signal.sma20 > signal.sma50;
    const volumeOk = signal.volumeConfirmed === true;
    const highConviction = isBuy && indicatorCoverageOk && trendOk && volumeOk;

    let displayRec = rec;
    let displayScore = rawScore;
    let highConvictionWarning = '';

    if (isBuy && !highConviction) {
        displayRec = 'HOLD';
        displayScore = Math.min(rawScore, 4);
        highConvictionWarning = '<p class=\"text-yellow-400 text-xs mt-2\">Note: Signal not high-conviction (Missing: ' +
            (!indicatorCoverageOk ? 'Indicators, ' : '') +
            (!trendOk ? 'Trend, ' : '') +
            (!volumeOk ? 'Volume' : '') + ')</p>';
    }

    const badgeCls = (isBuy && highConviction) ? 'bg-gradient-to-r from-green-600 to-emerald-500' :
                     isSell ? 'bg-gradient-to-r from-red-600 to-rose-500' : 'bg-gradient-to-r from-gray-500 to-gray-400';
    const badgeIcon = (isBuy && highConviction) ? 'fa-thumbs-up' : isSell ? 'fa-thumbs-down' : 'fa-minus';
    const recText = (isBuy && highConviction) ? 'Bullish outlook — high conviction entry' :
                    isSell ? 'Bearish outlook — consider reducing exposure' :
                    (isBuy && !highConviction) ? 'Bullish signal detected, but conviction criteria not met' :
                    'Neutral outlook — wait for clearer signals';
    const scoreColor = displayScore != null ? (displayScore >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const scoreBarColor = displayScore != null ? (displayScore >= 0 ? 'bg-gradient-to-r from-green-500 to-emerald-400' : 'bg-gradient-to-r from-red-500 to-rose-400') : 'bg-gray-500';
    const scoreBarWidth = displayScore != null
        ? (displayScore >= 7 ? 100 : displayScore >= 3 ? 66 : displayScore <= -7 ? 100 : displayScore <= -4 ? 66 : 33)
        : 0;

    const tfRsi = (src, label) => {
        const v = src != null ? parseFloat(src) : null;
        const disp = v != null ? v.toFixed(2) : '--';
        const color = v != null ? (v > 70 ? 'text-red-400' : v < 30 ? 'text-green-400' : 'text-yellow-400') : 'text-secondary';
        const bg = v != null ? (v > 70 ? 'bg-red-900/20 border-red-800/30' : v < 30 ? 'bg-green-900/20 border-green-800/30' : 'bg-yellow-900/20 border-yellow-800/30') : 'bg-gray-800/30 border-gray-700';
        const icon = v != null ? (v > 70 ? 'fa-circle-up' : v < 30 ? 'fa-circle-down' : 'fa-circle-minus') : 'fa-circle';
        const status = v != null ? (v > 70 ? 'Overbought' : v < 30 ? 'Oversold' : 'Neutral') : '--';
        return { disp, color, bg, icon, status };
    };
    const dailyRsi = tfRsi(_latestRsiValue != null ? _latestRsiValue : signal.rsi14, 'Daily');
    const weeklyRsi = tfRsi(signal.weeklyRsi, 'Weekly');
    const monthlyRsi = tfRsi(signal.monthlyRsi, 'Monthly');
    const macdVal = signal.macdHistogram;
    const macdDisplay = macdVal != null ? (macdVal >= 0 ? 'Bullish' : 'Bearish') : '--';
    const macdValDisplay = macdVal != null ? (macdVal >= 0 ? '+' : '') + macdVal.toFixed(2) : '';
    const macdColor = macdVal != null ? (macdVal >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const macdBg = macdVal != null ? (macdVal >= 0 ? 'bg-green-900/20 border-green-800/30' : 'bg-red-900/20 border-red-800/30') : 'bg-gray-800/30 border-gray-700';
    const macdIcon = macdVal != null ? (macdVal >= 0 ? 'fa-arrow-trend-up' : 'fa-arrow-trend-down') : 'fa-chart-line';
    const bbUpper = signal.bollingerUpper;
    const bbLower = signal.bollingerLower;
    const bbLtp = signal.lastTradedPrice || signal.closePrice || signal.latestPrice;
    const bbActive = bbUpper != null && bbLower != null;
    let bbZone = '--', bbZoneColor = 'text-secondary';
    if (bbActive && bbLtp != null) {
        if (bbLtp >= bbUpper * 0.98) { bbZone = 'Upper'; bbZoneColor = 'text-red-400'; }
        else if (bbLtp <= bbLower * 1.02) { bbZone = 'Lower'; bbZoneColor = 'text-green-400'; }
        else { bbZone = 'Middle'; bbZoneColor = 'text-yellow-400'; }
    }
    const bbDisplay = bbActive ? bbZone : '--';
    const bbColor = bbActive ? bbZoneColor : 'text-secondary';
    const bbBg = bbActive ? 'bg-blue-900/20 border-blue-800/30' : 'bg-gray-800/30 border-gray-700';

    const targetDisplay = signal.targetPrice != null ? fmtPrice(signal.targetPrice) : '--';
    const stopDisplay = signal.stopLoss != null ? fmtPrice(signal.stopLoss) : '--';
    const targetVal = signal.targetPrice;
    const stopVal = signal.stopLoss;
    const ltpVal = signal.lastTradedPrice || signal.closePrice || signal.latestPrice;
    let rrRatio = null;
    if (targetVal != null && stopVal != null && ltpVal != null && targetVal !== stopVal) {
        const upside = Math.abs(targetVal - ltpVal);
        const downside = Math.abs(ltpVal - stopVal);
        if (downside > 0) rrRatio = (upside / downside).toFixed(2);
    }
    const rrDisplay = rrRatio != null ? '1:' + rrRatio : '--';
    const rrColor = rrRatio != null ? (parseFloat(rrRatio) >= 2 ? 'text-green-400' : parseFloat(rrRatio) >= 1 ? 'text-yellow-400' : 'text-red-400') : 'text-secondary';
    const conf = signal.confidenceScore;
    const confDisplay = conf != null ? conf + '/100' : '--';
    const confColor = conf != null ? (conf >= 70 ? 'text-green-400' : conf >= 40 ? 'text-yellow-400' : 'text-red-400') : 'text-secondary';
    const age = signal.signalAge;
    const ageDisplay = age != null ? age + 'd' : '--';
    const stale = age != null && age > 3;
    const factorFields = ['divergenceScore','weeklyConfluenceScore','monthlyConfluenceScore','rsiScore','macdScore','bollingerScore','stochScore','stochRsiScore','ultimateOscScore','rocScore','williamsRScore','cciScore','obvScore','smaScore','week52Score','srProximityScore','vwapScore','ichimokuScore','breakoutScore'];
    const totalFactors = factorFields.length;
    const coverage = signal.indicatorCoverage;
    const coverageDisplay = coverage != null ? coverage + '/' + totalFactors : '--';
    const accPct = signal.signalAccuracy30d;
    const accTotal = signal.signalAccuracyTotal30d;
    const accCorrect = signal.signalAccuracyCorrect30d;
    const hasAccuracy = accPct != null && accTotal >= 5;

    const factorValues = factorFields.map(f => signal[f] != null ? signal[f] : 0).filter(v => v !== 0);
    const bullishCount = factorValues.filter(v => v > 0).length;
    const bearishCount = factorValues.filter(v => v < 0).length;

    const breakoutBadges = [];
    if (signal.volumeBreakout) breakoutBadges.push('<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-yellow-900/30 border border-yellow-700/50 text-yellow-300 text-[10px] font-bold"><i class="fas fa-bolt"></i>VOLUME BREAKOUT</span>');
    if (signal.gapUp) breakoutBadges.push('<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-green-900/30 border border-green-700/50 text-green-300 text-[10px] font-bold"><i class="fas fa-arrow-up"></i>GAP UP</span>');
    if (signal.gapDown) breakoutBadges.push('<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-red-900/30 border border-red-700/50 text-red-300 text-[10px] font-bold"><i class="fas fa-arrow-down"></i>GAP DOWN</span>');
    if (signal.rangeBreakout) breakoutBadges.push('<span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-blue-900/30 border border-blue-700/50 text-blue-300 text-[10px] font-bold"><i class="fas fa-expand-arrows-alt"></i>RANGE BREAKOUT</span>');
    const breakoutHtml = breakoutBadges.length ? breakoutBadges.join('') : '';

    el.innerHTML = `
        <div class="rounded-xl overflow-hidden border border-gray-700 bg-gray-800/40">
            <div class="px-5 py-4 border-b border-gray-700 flex items-center gap-3 flex-wrap">
                <span class="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-white font-bold shadow-lg text-base sm:text-lg ${badgeCls}">
                    <i class="fas ${badgeIcon}"></i>${displayRec}
                </span>
                ${breakoutHtml}
            </div>
            <div class="p-5 space-y-5">
                <div>
                    <div class="flex items-center justify-between mb-1.5">
                        <span class="text-xs font-semibold text-secondary uppercase tracking-wider">Composite Score</span>
                        <span class="text-2xl font-extrabold ${scoreColor}">${scoreDisplay}</span>
                    </div>
                    <div class="w-full h-3 bg-gray-700 rounded-full overflow-hidden">
                        <div class="h-full rounded-full transition-all duration-500 ease-out ${scoreBarColor}" style="width: ${scoreBarWidth}%"></div>
                    </div>
                    <div class="flex items-center justify-between mt-1.5">
                        <div class="flex gap-3 text-xs">
                            <span class="text-green-400 font-semibold"><i class="fas fa-arrow-up mr-0.5"></i>${bullishCount}</span>
                            <span class="text-red-400 font-semibold"><i class="fas fa-arrow-down mr-0.5"></i>${bearishCount}</span>
                        </div>
                        <span class="text-xs text-secondary">${factorValues.length}/${factorFields.length} indicators active</span>
                    </div>
                </div>
                ${coverage != null && coverage < 16 ? `
                <div class="flex items-center gap-2">
                    <span class="text-xs text-secondary">Indicators Available:</span>
                    <span class="text-xs font-semibold px-2 py-0.5 rounded-full border ${coverage >= 14 ? 'bg-green-900/20 border-green-800/30 text-green-400' : coverage >= 10 ? 'bg-yellow-900/20 border-yellow-800/30 text-yellow-400' : 'bg-red-900/20 border-red-800/30 text-red-400'}">${coverageDisplay}</span>
                </div>` : ''}
                ${hasAccuracy ? `
                <div class="flex items-center gap-2">
                    <span class="text-xs text-secondary">Signal Accuracy (30d):</span>
                    <span class="text-xs font-semibold px-2 py-0.5 rounded-full border ${accPct >= 70 ? 'bg-green-900/20 border-green-800/30 text-green-400' : accPct >= 50 ? 'bg-yellow-900/20 border-yellow-800/30 text-yellow-400' : 'bg-red-900/20 border-red-800/30 text-red-400'}">${accPct.toFixed(1)}% (${accCorrect}/${accTotal})</span>
                </div>` : ''}
                <div class="grid grid-cols-2 sm:grid-cols-5 gap-3">
                    <div class="rounded-lg bg-gray-800/40 border border-gray-700 p-3 text-center">
                        <span class="text-xs text-secondary block">Target</span>
                        <span class="text-sm font-bold text-green-400">${targetDisplay}</span>
                    </div>
                    <div class="rounded-lg bg-gray-800/40 border border-gray-700 p-3 text-center">
                        <span class="text-xs text-secondary block">Stop Loss</span>
                        <span class="text-sm font-bold text-red-400">${stopDisplay}</span>
                    </div>
                    <div class="rounded-lg bg-gray-800/40 border border-gray-700 p-3 text-center">
                        <span class="text-xs text-secondary block">R/R Ratio</span>
                        <span class="text-sm font-bold ${rrColor}">${rrDisplay}</span>
                    </div>
                    <div class="rounded-lg bg-gray-800/40 border border-gray-700 p-3 text-center">
                        <span class="text-xs text-secondary block">Confidence</span>
                        <span class="text-sm font-bold ${confColor}">${confDisplay}</span>
                    </div>
                    <div class="rounded-lg bg-gray-800/40 border ${stale ? 'border-yellow-800/30' : 'border-gray-700'} p-3 text-center">
                        <span class="text-xs text-secondary block">${stale ? '<i class="fas fa-clock text-yellow-400 mr-1"></i>' : ''}Signal Age</span>
                        <span class="text-sm font-bold ${stale ? 'text-yellow-400' : ''}">${ageDisplay}</span>
                    </div>
                </div>
                <div class="grid grid-cols-3 gap-3">
                    <div class="rounded-lg border ${dailyRsi.bg} p-3 text-center">
                        <div class="flex items-center justify-center gap-1.5 mb-1.5">
                            <i class="fas ${dailyRsi.icon} ${dailyRsi.color}"></i>
                            <span class="text-xs uppercase tracking-wider text-secondary font-medium">RSI</span>
                        </div>
                        <div class="space-y-0.5 text-left px-2">
                            <div class="flex justify-between text-xs">
                                <span class="text-secondary">Daily</span>
                                <span class="font-bold ${dailyRsi.color}">${dailyRsi.disp}</span>
                            </div>
                            <div class="flex justify-between text-xs">
                                <span class="text-secondary">Weekly</span>
                                <span class="font-bold ${weeklyRsi.color}">${weeklyRsi.disp}</span>
                            </div>
                            <div class="flex justify-between text-xs">
                                <span class="text-secondary">Monthly</span>
                                <span class="font-bold ${monthlyRsi.color}">${monthlyRsi.disp}</span>
                            </div>
                        </div>
                    </div>
                    <div class="rounded-lg border ${macdBg} p-3 text-center">
                        <div class="flex items-center justify-center gap-1.5 mb-1.5">
                            <i class="fas ${macdIcon} ${macdColor}"></i>
                            <span class="text-xs uppercase tracking-wider text-secondary font-medium">MACD</span>
                        </div>
                        <div class="text-lg font-bold ${macdColor}">${macdDisplay}</div>
                        <p class="text-xs ${macdColor} mt-0.5">${macdValDisplay}</p>
                    </div>
                    <div class="rounded-lg border ${bbBg} p-3 text-center">
                        <div class="flex items-center justify-center gap-1.5 mb-1.5">
                            <i class="fas fa-chart-simple ${bbColor}"></i>
                            <span class="text-xs uppercase tracking-wider text-secondary font-medium">Bollinger</span>
                        </div>
                        <div class="text-lg font-bold ${bbColor}">${bbDisplay}</div>
                        <p class="text-xs text-secondary mt-0.5">&nbsp;</p>
                    </div>
                </div>
                <div class="text-sm text-secondary border-t border-gray-700 pt-3 flex items-center gap-2">
                    <i class="fas ${badgeIcon} ${scoreColor}"></i>
                    <span>${recText}</span>
                </div>
            </div>
        </div>`;
}

// ─── Score Breakdown ─────────────────────────────────────────────────────────
function renderScoreBreakdown(signal) {
    const el = document.getElementById('scoreBreakdownContent');
    if (!el) return;
    if (!signal) {
        el.classList.add('hidden');
        return;
    }

    const factors = [
        { label: 'RSI Divergence',     field: 'divergenceScore' },
        { label: 'Weekly Confluence',  field: 'weeklyConfluenceScore' },
        { label: 'Monthly Confluence', field: 'monthlyConfluenceScore' },
        { label: 'RSI 14',             field: 'rsiScore' },
        { label: 'MACD',               field: 'macdScore' },
        { label: 'Bollinger Bands',    field: 'bollingerScore' },
        { label: 'Stochastic',         field: 'stochScore' },
        { label: 'StochRSI',           field: 'stochRsiScore' },
        { label: 'Ultimate Oscillator', field: 'ultimateOscScore' },
        { label: 'ROC',               field: 'rocScore' },
        { label: 'Williams %R',        field: 'williamsRScore' },
        { label: 'CCI',                field: 'cciScore' },
        { label: 'OBV',                field: 'obvScore' },
        { label: 'SMA 20',             field: 'smaScore' },
        { label: '52W Proximity',      field: 'week52Score' },
        { label: 'S/R Proximity',      field: 'srProximityScore' },
        { label: 'VWAP',               field: 'vwapScore' },
        { label: 'Ichimoku Cloud',     field: 'ichimokuScore' },
        { label: 'Breakout',           field: 'breakoutScore' }
    ];

    const entries = factors
        .map(f => ({ label: f.label, score: signal[f.field] != null ? signal[f.field] : 0 }))
        .filter(e => e.score !== 0);

    if (!entries.length) {
        el.classList.add('hidden');
        return;
    }

    el.classList.remove('hidden');

    const total = entries.reduce((s, e) => s + e.score, 0);
    const bullish = entries.filter(e => e.score > 0);
    const bearish = entries.filter(e => e.score < 0);
    const maxMag = Math.max(...entries.map(e => Math.abs(e.score)));
    const sorted = [...entries].sort((a, b) => Math.abs(b.score) - Math.abs(a.score));

    const rows = sorted.map((e, i) => {
        const pos = e.score > 0;
        const color = pos ? 'text-green-400' : 'text-red-400';
        const barColor = pos ? 'bg-green-500' : 'bg-red-500';
        const width = Math.max(Math.abs(e.score) / maxMag * 100, 8);
        const pctOfTotal = total !== 0 ? (e.score / total * 100) : 0;
        const isTop = i === 0;
        return `
            <div class="flex items-center gap-2 py-1.5 ${isTop ? 'bg-gray-700/20 -mx-2 px-2 rounded' : ''}">
                <span class="text-xs ${isTop ? 'text-white font-semibold' : 'text-secondary'} w-36 shrink-0 truncate" title="${e.label}">${isTop && pos ? '<i class="fas fa-star text-yellow-400 mr-1 text-[10px]"></i>' : ''}${e.label}</span>
                <span class="text-xs font-bold ${color} w-8 text-right shrink-0">${pos ? '+' : ''}${e.score}</span>
                <div class="flex-1 h-2.5 bg-gray-700 rounded-full overflow-hidden">
                    <div class="h-full rounded-full ${barColor} transition-all" style="width: ${width}%"></div>
                </div>
                <span class="text-[10px] text-secondary w-10 text-right shrink-0">${pctOfTotal > 0 ? '+' : ''}${pctOfTotal.toFixed(0)}%</span>
            </div>`;
    }).join('');

    const bullSum = bullish.reduce((s, e) => s + e.score, 0);
    const bearSum = bearish.reduce((s, e) => s + e.score, 0);

    el.innerHTML = `
        <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
            <div class="flex items-center justify-between mb-3">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider flex items-center gap-2">
                    <i class="fas fa-chart-pie text-purple-400"></i>Score Breakdown
                </h4>
                <div class="flex gap-3 text-xs">
                    <span class="text-green-400 font-semibold"><i class="fas fa-arrow-up mr-0.5"></i>${bullSum > 0 ? '+' + bullSum : bullSum}</span>
                    <span class="text-gray-500">|</span>
                    <span class="text-red-400 font-semibold">${bearSum < 0 ? '' : '+'}${bearSum} <i class="fas fa-arrow-down ml-0.5"></i></span>
                </div>
            </div>
            ${rows}
            <div class="flex items-center justify-between mt-2 pt-2 border-t border-gray-700">
                <span class="text-xs text-secondary">${bullish.length} bullish · ${bearish.length} bearish</span>
                <span class="text-xs font-bold ${total >= 0 ? 'text-green-400' : 'text-red-400'}">Net: ${total >= 0 ? '+' : ''}${total}</span>
            </div>
        </div>`;
}

// ─── Signal Timeline ─────────────────────────────────────────────────────────
function renderSignalTimeline(history, currentSignal) {
    const el = document.getElementById('signalTimeline');
    if (!el) return;
    if (!history || !history.length) {
        el.innerHTML = '';
        return;
    }

    const colors = {
        'STRONG BUY': '#16a34a',
        'BUY': '#4ade80',
        'HOLD': '#6b7280',
        'NEUTRAL': '#6b7280',
        'SELL': '#f87171',
        'STRONG SELL': '#dc2626'
    };

    const sorted = [...history].sort((a, b) => new Date(a.priceDate) - new Date(b.priceDate));
    const first = new Date(sorted[0].priceDate).getTime();
    const last = new Date(sorted[sorted.length - 1].priceDate).getTime();
    const range = last - first || 1;

    let buyCount = 0, sellCount = 0, holdCount = 0;
    sorted.forEach(s => {
        const r = s.recommendation || 'NEUTRAL';
        if (r === 'STRONG BUY' || r === 'BUY') buyCount++;
        else if (r === 'STRONG SELL' || r === 'SELL') sellCount++;
        else holdCount++;
    });

    const evaluated = sorted.filter(s => s.wasAccurate != null);
    const accurateCount = evaluated.filter(s => s.wasAccurate === true).length;
    const wrongCount = evaluated.filter(s => s.wasAccurate === false).length;
    const evalPct = evaluated.length > 0 ? (accurateCount / evaluated.length * 100).toFixed(0) : null;

    const recent = sorted.slice(-5);
    const recentBuy = recent.filter(s => s.recommendation === 'STRONG BUY' || s.recommendation === 'BUY').length;
    const recentSell = recent.filter(s => s.recommendation === 'STRONG SELL' || s.recommendation === 'SELL').length;
    let trendIcon = '', trendText = '', trendColor = '';
    if (recentBuy > recentSell) {
        trendIcon = 'fa-arrow-trend-up';
        trendText = 'Bullish bias';
        trendColor = 'text-green-400';
    } else if (recentSell > recentBuy) {
        trendIcon = 'fa-arrow-trend-down';
        trendText = 'Bearish bias';
        trendColor = 'text-red-400';
    } else {
        trendIcon = 'fa-minus';
        trendText = 'Neutral bias';
        trendColor = 'text-secondary';
    }

    let flips = 0;
    for (let i = 1; i < Math.min(sorted.length, 10); i++) {
        const prev = sorted[sorted.length - 1 - i];
        const curr = sorted[sorted.length - i];
        const pCat = prev.recommendation === 'STRONG BUY' || prev.recommendation === 'BUY' ? 'buy' :
                     prev.recommendation === 'STRONG SELL' || prev.recommendation === 'SELL' ? 'sell' : 'hold';
        const cCat = curr.recommendation === 'STRONG BUY' || curr.recommendation === 'BUY' ? 'buy' :
                     curr.recommendation === 'STRONG SELL' || curr.recommendation === 'SELL' ? 'sell' : 'hold';
        if (pCat !== cCat) flips++;
    }

    let html = '<div class="mt-4 pt-3 border-t border-gray-700">';
    html += '<div class="flex items-center justify-between mb-2">';
    const dateRange = sorted.length >= 2 ? Math.round((last - first) / 86400000) + 'd' : '';
    html += `<span class="text-xs font-semibold text-secondary uppercase tracking-wider">Signal History (${dateRange})</span>`;
    const total = sorted.length;
    if (total > 0) {
        html += `<span class="text-xs text-secondary">${buyCount} BUY · ${holdCount} HOLD · ${sellCount} SELL</span>`;
    }
    html += '</div>';

    html += '<div class="flex h-5 rounded overflow-hidden">';
    sorted.forEach((s, i) => {
        const x = new Date(s.priceDate).getTime();
        let left = (x - first) / range * 100;
        let width;
        if (i < sorted.length - 1) {
            const nextX = new Date(sorted[i + 1].priceDate).getTime();
            width = (nextX - x) / range * 100;
        } else {
            width = 100 - left;
        }
        if (width < 1.5) width = 1.5;
        const color = colors[s.recommendation] || '#6b7280';
        const label = s.recommendation || '--';
        const score = s.compositeScore != null ? (s.compositeScore >= 0 ? '+' : '') + s.compositeScore : '';
        const accTag = s.wasAccurate != null ? (s.wasAccurate ? ' ✓' : ' ✗') : '';
        const fwdStr = s.forwardReturn != null ? (s.forwardReturn >= 0 ? ' +' : ' ') + Number(s.forwardReturn).toFixed(1) + '%' : '';
        html += `<div class="h-full" style="width:${width}%;background:${color};min-width:2px" title="${s.priceDate} | ${label} (${score})${accTag}${fwdStr}"></div>`;
    });
    html += '</div>';

    html += `<div class="flex items-center justify-between mt-2 text-xs">`;
    html += `<span class="flex items-center gap-1 ${trendColor}"><i class="fas ${trendIcon}"></i>${trendText} (last 5)</span>`;
    if (evalPct != null) {
        const evalColor = accurateCount >= wrongCount ? 'text-green-400' : 'text-red-400';
        html += `<span class="${evalColor}"><i class="fas ${accurateCount >= wrongCount ? 'fa-check-circle' : 'fa-times-circle'}"></i> ${accurateCount}/${evaluated.length} correct (${evalPct}%)</span>`;
    }
    html += `<span class="text-secondary">${flips} signal ${flips === 1 ? 'flip' : 'flips'} in last 10</span>`;
    if (currentSignal && currentSignal.recommendation) {
        const cur = currentSignal.recommendation;
        const prev = sorted.length >= 2 ? sorted[sorted.length - 2].recommendation : null;
        let change = '';
        if (prev && cur !== prev) {
            const dir = (cur === 'STRONG BUY' || cur === 'BUY') ? '↑' : (cur === 'STRONG SELL' || cur === 'SELL') ? '↓' : '→';
            const dColor = (cur === 'STRONG BUY' || cur === 'BUY') ? 'text-green-400' : (cur === 'STRONG SELL' || cur === 'SELL') ? 'text-red-400' : 'text-secondary';
            change = `<span class="${dColor}">${dir} from ${prev}</span>`;
        }
        if (change) html += `<span>${change}</span>`;
    }
    html += '</div>';

    if (evaluated.length > 0) {
        html += '<div class="flex h-3 rounded overflow-hidden mt-1">';
        sorted.forEach((s) => {
            if (s.wasAccurate == null) return;
            const x = new Date(s.priceDate).getTime();
            let left = (x - first) / range * 100;
            let width;
            const nextIdx = sorted.findIndex(t => t.wasAccurate != null && new Date(t.priceDate).getTime() > x);
            if (nextIdx !== -1) {
                width = (new Date(sorted[nextIdx].priceDate).getTime() - x) / range * 100;
            } else {
                width = 100 - left;
            }
            if (width < 2) width = 2;
            const bg = s.wasAccurate ? '#22c55e' : '#ef4444';
            const icon = s.wasAccurate ? '✓' : '✗';
            const fwd = s.forwardReturn != null ? (s.forwardReturn >= 0 ? '+' : '') + Number(s.forwardReturn).toFixed(1) + '%' : '';
            html += `<div class="h-full flex items-center justify-center text-xs font-bold text-white" style="width:${width}%;background:${bg};min-width:14px" title="${s.priceDate}: ${s.wasAccurate ? 'Correct' : 'Wrong'} (${fwd})">${icon}</div>`;
        });
        html += '</div>';
    }

    html += '<div class="flex items-center gap-3 mt-1.5">';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#16a34a"></span>Strong Buy</span>';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#4ade80"></span>Buy</span>';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#6b7280"></span>Hold</span>';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#f87171"></span>Sell</span>';
    html += '<span class="flex items-center gap-1 text-xs"><span class="w-2 h-2 rounded" style="background:#dc2626"></span>Strong Sell</span>';
    html += '</div>';

    html += '</div>';
    el.innerHTML = html;
}

// ─── Technical Indicators Content ────────────────────────────────────────────
function renderIndicatorsContent(indicators) {
    const el = document.getElementById('indicatorsContent');
    if (!el) return;
    if (!indicators || !indicators.length) {
        el.innerHTML = '<p class="text-secondary text-center py-4">No indicator data. Click Refresh to calculate.</p>';
        return;
    }

    function getIndicatorInfo(type, value) {
        const t = (type || '').toLowerCase();
        const hasValue = value != null;
        let group = 'Other', color = 'text-blue-400', bg = 'bg-blue-900/20 border-blue-800/30', icon = 'fa-chart-line';
        if (t.includes('stoch_rsi') || t.includes('stochrsi')) {
            group = 'Momentum'; icon = 'fa-gauge-high';
            if (hasValue) {
                if (value > 80) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
                else if (value < 20) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else { color = 'text-yellow-400'; bg = 'bg-yellow-900/20 border-yellow-800/30'; }
            }
        } else if (t.includes('rsi')) {
            group = 'Momentum'; icon = 'fa-gauge-high';
            if (hasValue) {
                if (value > 70) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
                else if (value < 30) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else { color = 'text-yellow-400'; bg = 'bg-yellow-900/20 border-yellow-800/30'; }
            }
        } else if (t.includes('macd') || t.includes('stoch_k') || t.includes('cci') || t.includes('william') || t.includes('ultimate') || t.includes('roc')) {
            group = 'Momentum'; icon = 'fa-arrows-left-right';
            if (t.includes('macd') && hasValue) {
                if (value >= 0) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
            }
            if (t.includes('ultimate') && hasValue) {
                if (value < 30) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else if (value > 70) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
            }
            if (t.includes('roc') && hasValue) {
                if (value > 0) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else if (value < 0) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
            }
            if (t.includes('william') && hasValue) {
                if (value > -20) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
                else if (value < -80) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
            }
            if (t.includes('cci') && hasValue) {
                if (value > 100) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
                else if (value < -100) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
            }
        } else if (t.includes('adx') || t.includes('plus_di') || t.includes('minus_di')) {
            group = 'Trend'; icon = 'fa-chart-area';
            if (t.includes('adx') && hasValue) {
                if (value > 25) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else { color = 'text-yellow-400'; bg = 'bg-yellow-900/20 border-yellow-800/30'; }
            }
            if (t.includes('plus_di') && hasValue) {
                color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30';
            }
            if (t.includes('minus_di') && hasValue) {
                color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30';
            }
        } else if (t.includes('sma') || t.includes('ema') || t.includes('ma(') || t.includes('mms')) {
            group = 'Moving Averages'; icon = 'fa-chart-line';
        } else if (t.includes('boll') || t.includes('atr') || t.includes('volatilit')) {
            group = 'Volatility'; icon = 'fa-wave-square';
        } else if (t.includes('volume') || t.includes('obv')) {
            group = 'Volume'; icon = 'fa-chart-bar';
            if (t.includes('obv') && hasValue) {
                if (value > 0) { color = 'text-green-400'; bg = 'bg-green-900/20 border-green-800/30'; }
                else if (value < 0) { color = 'text-red-400'; bg = 'bg-red-900/20 border-red-800/30'; }
            }
        } else if (t.includes('support') || t.includes('resist')) {
            group = 'Price Levels'; icon = 'fa-location-dot';
        }
        return { group, color, bg, icon };
    }

    const groups = {};
    indicators.forEach(ind => {
        const info = getIndicatorInfo(ind.type, ind.value);
        if (!groups[info.group]) groups[info.group] = [];
        groups[info.group].push({ ...ind, ...info });
    });

    const groupOrder = ['Momentum', 'Trend', 'Moving Averages', 'Volatility', 'Volume', 'Price Levels', 'Other'];
    const groupIcons = {
        Momentum: 'fa-gauge-high text-purple-400',
        Trend: 'fa-chart-area text-teal-400',
        'Moving Averages': 'fa-chart-line text-cyan-400',
        Volatility: 'fa-wave-square text-orange-400',
        Volume: 'fa-chart-bar text-blue-400',
        'Price Levels': 'fa-location-dot text-green-400',
        Other: 'fa-chart-line text-gray-400'
    };

    const groupHtml = groupOrder
        .filter(g => groups[g])
        .map(g => {
            const cards = groups[g].map(ind => {
                const val = ind.value != null ? ind.value.toFixed(2) : '--';
                return `
                    <div class="rounded-lg border ${ind.bg} p-3">
                        <div class="flex items-center gap-1.5 mb-1">
                            <i class="fas ${ind.icon} ${ind.color}"></i>
                            <span class="text-xs text-secondary">${ind.type || '--'}</span>
                        </div>
                        <div class="text-base font-bold ${ind.color}">${val}</div>
                    </div>`;
            }).join('');
            return `
                <div>
                    <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-2.5 flex items-center gap-2">
                        <i class="fas ${groupIcons[g] || 'fa-chart-line text-gray-400'}"></i>${g}
                    </h4>
                    <div class="grid grid-cols-2 sm:grid-cols-3 gap-2">
                        ${cards}
                    </div>
                </div>`;
        }).join('');

    const latestDate = indicators
        .filter(ind => ind.calculationDate)
        .map(ind => ind.calculationDate)
        .sort()
        .pop();

    el.innerHTML = `
        <div class="space-y-5">
            ${groupHtml}
            ${latestDate ? '<div class="text-xs text-secondary text-right pt-2 border-t border-gray-700">Last updated: ' + latestDate + '</div>' : ''}
        </div>`;
}

// ─── Price Analysis ──────────────────────────────────────────────────────────
function renderPriceAnalysis(signal) {
    const el = document.getElementById('priceAnalysisContent');
    if (!el) return;
    if (!signal) {
        el.innerHTML = '<p class="text-secondary text-center py-4">No price analysis data</p>';
        return;
    }
    const sp = signal.supportLevel != null ? fmtPrice(signal.supportLevel) : '--';
    const rs = signal.resistanceLevel != null ? fmtPrice(signal.resistanceLevel) : '--';
    const ts = signal.trendStrength != null ? signal.trendStrength.toFixed(1) + '%' : '--';
    const vol = signal.volatility != null ? signal.volatility.toFixed(2) + '%' : '--';
    const rrPA = signal.targetPrice && signal.stopLoss && (signal.lastTradedPrice || signal.latestPrice)
        ? ((Math.abs(signal.targetPrice - (signal.lastTradedPrice || signal.latestPrice)) / Math.abs((signal.lastTradedPrice || signal.latestPrice) - signal.stopLoss)).toFixed(2))
        : null;
    const rrPADisplay = rrPA != null ? '1:' + rrPA : '--';
    const rrPAColor = rrPA != null ? (parseFloat(rrPA) >= 2 ? 'text-green-400' : parseFloat(rrPA) >= 1 ? 'text-yellow-400' : 'text-red-400') : 'text-secondary';
    const sma20 = signal.sma20 != null ? fmtPrice(signal.sma20) : '--';
    const sma50 = signal.sma50 != null ? fmtPrice(signal.sma50) : '--';
    const bbU = signal.bollingerUpper != null ? fmtPrice(signal.bollingerUpper) : '--';
    const bbL = signal.bollingerLower != null ? fmtPrice(signal.bollingerLower) : '--';
    const vsSma20 = signal.priceVsSma20 != null ? (signal.priceVsSma20 >= 0 ? '+' : '') + signal.priceVsSma20.toFixed(2) + '%' : '--';
    const vsSma20Color = signal.priceVsSma20 != null ? (signal.priceVsSma20 >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const pct52Low = signal.pctFrom52WLow != null ? (signal.pctFrom52WLow >= 0 ? '+' : '') + signal.pctFrom52WLow.toFixed(2) + '%' : '--';
    const pct52LowColor = signal.pctFrom52WLow != null ? (signal.pctFrom52WLow >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const pct52High = signal.pctFrom52WHigh != null ? (signal.pctFrom52WHigh >= 0 ? '+' : '') + signal.pctFrom52WHigh.toFixed(2) + '%' : '--';
    const pct52HighColor = signal.pctFrom52WHigh != null ? (signal.pctFrom52WHigh >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';
    const pnlPct = signal.pnlPercent;
    const pnlDisplay = pnlPct != null ? (pnlPct >= 0 ? '+' : '') + pnlPct.toFixed(2) + '%' : '--';
    const pnlColor = pnlPct != null ? (pnlPct >= 0 ? 'text-green-400' : 'text-red-400') : 'text-secondary';

    const histRet = (val) => {
        if (val == null) return null;
        return { display: (val >= 0 ? '+' : '') + val.toFixed(2) + '%', color: val >= 0 ? 'text-green-400' : 'text-red-400' };
    };
    const ret5d = histRet(signal.historicalReturn5d);
    const ret10d = histRet(signal.historicalReturn10d);
    const ret20d = histRet(signal.historicalReturn20d);
    const hasHistReturns = ret5d || ret10d || ret20d;

    el.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-location-dot text-blue-400"></i>Key Levels
                </h4>
                <div class="space-y-3">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Support</span>
                        <span class="text-sm font-bold text-green-400">${sp}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Resistance</span>
                        <span class="text-sm font-bold text-red-400">${rs}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Trend Strength</span>
                        <span class="text-sm font-semibold">${ts}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Volatility</span>
                        <span class="text-sm font-semibold">${vol}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">R/R Ratio</span>
                        <span class="text-sm font-semibold ${rrPAColor}">${rrPADisplay}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-chart-line text-cyan-400"></i>Moving Averages
                </h4>
                <div class="space-y-3">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">SMA 20</span>
                        <span class="text-sm font-bold">${sma20}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">SMA 50</span>
                        <span class="text-sm font-bold">${sma50}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Price vs SMA 20</span>
                        <span class="text-sm font-bold ${vsSma20Color}">${vsSma20}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-chart-simple text-purple-400"></i>Bollinger Bands
                </h4>
                <div class="space-y-3">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Upper Band</span>
                        <span class="text-sm font-bold">${bbU}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">Lower Band</span>
                        <span class="text-sm font-bold">${bbL}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-calendar text-yellow-400"></i>52-Week &amp; P&amp;L
                </h4>
                <div class="space-y-3">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">From 52W Low</span>
                        <span class="text-sm font-bold ${pct52LowColor}">${pct52Low}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">From 52W High</span>
                        <span class="text-sm font-bold ${pct52HighColor}">${pct52High}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-secondary">P&amp;L %</span>
                        <span class="text-sm font-bold ${pnlColor}">${pnlDisplay}</span>
                    </div>
                    ${hasHistReturns ? `
                    <div class="border-t border-gray-700 pt-2 mt-2">
                        <span class="text-xs text-secondary block mb-1.5">Historical Signal Returns</span>
                        ${ret5d ? `<div class="flex justify-between items-center py-0.5"><span class="text-xs text-secondary">5d</span><span class="text-xs font-bold ${ret5d.color}">${ret5d.display}</span></div>` : ''}
                        ${ret10d ? `<div class="flex justify-between items-center py-0.5"><span class="text-xs text-secondary">10d</span><span class="text-xs font-bold ${ret10d.color}">${ret10d.display}</span></div>` : ''}
                        ${ret20d ? `<div class="flex justify-between items-center py-0.5"><span class="text-xs text-secondary">20d</span><span class="text-xs font-bold ${ret20d.color}">${ret20d.display}</span></div>` : ''}
                    </div>` : ''}
                </div>
            </div>
        </div>`;
}

// ─── Risk Assessment ─────────────────────────────────────────────────────────
function renderRiskAssessment(signal, backtestData) {
    const el = document.getElementById('riskContent');
    if (!el) return;
    if (!signal) {
        el.innerHTML = '<p class="text-secondary text-center py-4">No risk assessment data</p>';
        return;
    }

    const eventRisk = signal.eventRisk;
    const divergence = signal.bullishDivergence ? 'bullish' : signal.bearishDivergence ? 'bearish' : null;
    const volConfirmed = signal.volumeConfirmed;
    const purposes = signal.eventPurposes && signal.eventPurposes.length ? signal.eventPurposes : null;

    const volPenalty = signal.volumePenaltyApplied;
    const adxFilter = signal.adxFilterApplied != null && signal.adxFilterApplied !== 0;
    const fiidiiScore = signal.fiidiiScore != null ? signal.fiidiiScore : 0;

    const btWinRate = backtestData && backtestData.winRate != null ? backtestData.winRate.toFixed(1) + '%' : null;
    const btReturn = backtestData && backtestData.totalReturn != null ? backtestData.totalReturn : null;
    const btReturnDisplay = btReturn != null ? (btReturn >= 0 ? '+' : '') + Number(btReturn).toFixed(2) + '%' : null;
    const btReturnColor = btReturn != null && btReturn >= 0 ? 'text-green-400' : 'text-red-400';

    const riskInfo = signal.volatility != null
        ? (signal.volatility > 3 ? { label: 'High', cls: 'bg-red-600 text-white', icon: 'fa-shield-exclamation' }
            : signal.volatility > 1.5 ? { label: 'Medium', cls: 'bg-yellow-600 text-white', icon: 'fa-shield-halved' }
            : { label: 'Low', cls: 'bg-green-600 text-white', icon: 'fa-shield-check' })
        : null;

    el.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-shield-alt text-yellow-400"></i>Volatility Risk
                </h4>
                <div class="text-center py-3">
                    ${riskInfo
                        ? `<span class="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-bold ${riskInfo.cls} shadow-md"><i class="fas ${riskInfo.icon}"></i>${riskInfo.label}</span>`
                        : '<span class="text-secondary">--</span>'}
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-calendar-exclamation text-orange-400"></i>Event Risk
                </h4>
                <div class="rounded-lg p-3 border ${eventRisk ? 'bg-red-900/20 border-red-800/30' : 'bg-green-900/20 border-green-800/30'}">
                    <div class="flex items-center gap-2 text-sm">
                        <i class="fas ${eventRisk ? 'fa-exclamation-triangle text-red-400' : 'fa-check-circle text-green-400'}"></i>
                        <span class="font-medium ${eventRisk ? 'text-red-400' : 'text-green-400'}">${eventRisk ? 'Event risk detected' : 'No event risk'}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-code-branch text-purple-400"></i>Divergence
                </h4>
                <div class="rounded-lg p-3 border ${divergence === 'bullish' ? 'bg-green-900/20 border-green-800/30' : divergence === 'bearish' ? 'bg-red-900/20 border-red-800/30' : 'bg-gray-800/30 border-gray-700'}">
                    <div class="flex items-center gap-2 text-sm">
                        <i class="fas ${divergence === 'bullish' ? 'fa-arrow-trend-up text-green-400' : divergence === 'bearish' ? 'fa-arrow-trend-down text-red-400' : 'fa-minus text-secondary'}"></i>
                        <span class="font-medium ${divergence ? (divergence === 'bullish' ? 'text-green-400' : 'text-red-400') : 'text-secondary'}">${divergence ? (divergence === 'bullish' ? 'Bullish divergence' : 'Bearish divergence') : 'No divergence'}</span>
                    </div>
                </div>
            </div>
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-chart-bar text-blue-400"></i>Volume Confirmation
                </h4>
                <div class="rounded-lg p-3 border ${volConfirmed ? 'bg-green-900/20 border-green-800/30' : 'bg-yellow-900/20 border-yellow-800/30'}">
                    <div class="flex items-center gap-2 text-sm">
                        <i class="fas ${volConfirmed ? 'fa-check text-green-400' : 'fa-xmark text-yellow-400'}"></i>
                        <span class="font-medium ${volConfirmed ? 'text-green-400' : 'text-yellow-400'}">${volConfirmed ? 'Confirmed' : 'Unconfirmed'}</span>
                    </div>
                </div>
            </div>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-4">
            <div class="rounded-lg border border-gray-700 bg-gray-800/30 p-4 lg:col-span-4">
                <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                    <i class="fas fa-filter text-cyan-400"></i>Signal Quality
                </h4>
                <div class="flex flex-wrap gap-3">
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border ${volConfirmed ? 'bg-green-900/20 border-green-800/30 text-green-400' : 'bg-yellow-900/20 border-yellow-800/30 text-yellow-400'}">
                        <i class="fas ${volConfirmed ? 'fa-check-circle' : 'fa-exclamation-triangle'}"></i>
                        ${volConfirmed ? 'Volume Confirmed' : 'Volume Unconfirmed'}
                    </span>
                    ${volPenalty ? `
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border bg-red-900/20 border-red-800/30 text-red-400">
                        <i class="fas fa-exclamation-triangle"></i>Volume Penalty Applied
                    </span>` : ''}
                    ${adxFilter ? `
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border bg-yellow-900/20 border-yellow-800/30 text-yellow-400">
                        <i class="fas fa-wave-square"></i>ADX Counter-Trend
                    </span>` : ''}
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border ${fiidiiScore > 0 ? 'bg-green-900/20 border-green-800/30 text-green-400' : fiidiiScore < 0 ? 'bg-red-900/20 border-red-800/30 text-red-400' : 'bg-gray-800/30 border-gray-700 text-secondary'}">
                        <i class="fas fa-building-columns"></i>
                        FII/DII: ${fiidiiScore > 0 ? '+' : ''}${fiidiiScore}
                    </span>
                    ${btWinRate ? `
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border ${parseFloat(btWinRate) >= 50 ? 'bg-green-900/20 border-green-800/30 text-green-400' : 'bg-red-900/20 border-red-800/30 text-red-400'}">
                        <i class="fas fa-vial"></i>
                        BT Win: ${btWinRate}
                    </span>` : ''}
                    ${btReturnDisplay ? `
                    <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border ${btReturn >= 0 ? 'bg-green-900/20 border-green-800/30 text-green-400' : 'bg-red-900/20 border-red-800/30 text-red-400'}">
                        <i class="fas fa-coins"></i>
                        BT Return: ${btReturnDisplay}
                    </span>` : ''}
                </div>
            </div>
        </div>
        ${purposes ? `
        <div class="mt-4 rounded-lg border border-gray-700 bg-gray-800/30 p-4">
            <h4 class="text-xs font-semibold text-secondary uppercase tracking-wider mb-3 flex items-center gap-2">
                <i class="fas fa-bullhorn text-orange-400"></i>Event Purposes
            </h4>
            <div class="flex flex-wrap gap-2">
                ${purposes.map(p => '<span class="px-3 py-1 bg-orange-900/30 text-orange-400 rounded-full text-xs font-medium border border-orange-800/30">' + p + '</span>').join('')}
            </div>
        </div>` : ''}`;
}

// ─── Indicator Refresh ───────────────────────────────────────────────────────
async function refreshIndicators() {
    const btn = document.getElementById('refreshIndicatorsBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-1"></i>Calc...'; }
    try {
        await calculateIndicators(_stockId);
        const indicators = await getLatestIndicators(_stockId);
        renderIndicatorsContent(indicators);
    } catch (e) {
        console.error('Indicator refresh error:', e);
        const el = document.getElementById('indicatorsContent');
        if (el) el.innerHTML = '<p class="text-danger text-center py-4">⚠ Failed to refresh indicators</p>';
    } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-sync-alt mr-1"></i>Refresh'; }
    }
}
