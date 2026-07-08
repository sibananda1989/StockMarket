(function () {
    'use strict';

    const state = {
        strategies: [],
        dirty: false,
        openPanelId: null,
        editingId: null,
        editingCondId: null
    };

    const strategyContainers = {
        list: null,
        activeCount: null,
        saveBtn: null
    };

    function showToast(msg, type) {
        const existing = document.querySelector('.toast-notification');
        if (existing) existing.remove();
        const toast = document.createElement('div');
        toast.className = 'toast-notification fixed top-4 right-4 z-50 px-4 py-3 rounded-lg shadow-lg text-sm font-medium transition-all duration-300 ' +
            (type === 'error' ? 'bg-red-600 text-white' : 'bg-green-600 text-white');
        toast.innerHTML = '<i class="fas ' + (type === 'error' ? 'fa-exclamation-circle' : 'fa-check-circle') + ' mr-2"></i>' + msg;
        document.body.appendChild(toast);
        setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 3000);
    }

    function signalBadge(signal) {
        const cls = signal === 'BUY' ? 'bg-green-800 text-green-100'
            : signal === 'SELL' ? 'bg-red-600 text-red-100'
                : 'bg-gray-500 text-gray-100';
        return '<span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ' + cls + '">' + signal + '</span>';
    }

    function confidenceDot(conf) {
        const color = conf === 'high' ? 'bg-green-400'
            : conf === 'mid' ? 'bg-amber-400'
                : 'bg-gray-400';
        return '<span class="inline-block w-2 h-2 rounded-full ' + color + ' mr-1.5"></span>';
    }

    function renderConditionText(c) {
        switch (c.operator) {
            case '<': return c.fieldLabel + ' < <code>' + c.thresholdValue + '</code>';
            case '>': return c.fieldLabel + ' > <code>' + c.thresholdValue + '</code>';
            case '<=': return c.fieldLabel + ' \u2264 <code>' + c.thresholdValue + '</code>';
            case '>=': return c.fieldLabel + ' \u2265 <code>' + c.thresholdValue + '</code>';
            case '==': return c.fieldLabel + ' = <code>' + c.thresholdValue + '</code>';
            case 'between': return c.fieldLabel + ' between <code>' + c.thresholdValue + '</code> \u2013 <code>' + c.thresholdValue2 + '</code>';
            case 'within': return c.fieldLabel + ' within \u00b1<code>' + c.thresholdValue + '</code>';
            case 'crossover_above': return 'MACD line crosses above signal line';
            case 'crossover_below': return 'MACD line crosses below signal line';
            case 'both_above': return 'MACD and signal both above <code>0</code>';
            case 'both_below': return 'MACD and signal both below <code>0</code>';
            case 'alignment_bullish': return 'Price > SMA20 > SMA50 \u2014 full bullish alignment';
            case 'alignment_bearish': return 'Price < SMA20 < SMA50 \u2014 full bearish alignment';
            case 'golden_cross': return 'Golden cross \u2014 SMA20 crosses above SMA50';
            case 'death_cross': return 'Death cross \u2014 SMA20 crosses below SMA50';
            case 'mixed': return 'Price between SMA20 and SMA50 \u2014 mixed alignment';
            case 'obv_up': return 'OBV trending up over last 5 days';
            case 'obv_down': return 'OBV trending down over last 5 days';
            case 'Hammer': return c.fieldLabel + ' \u2014 bullish reversal at support';
            case 'Bullish Engulfing': return c.fieldLabel + ' \u2014 strong bullish reversal';
            case 'Morning Star': return c.fieldLabel + ' \u2014 bullish bottoming pattern';
            case 'Shooting Star': return c.fieldLabel + ' \u2014 bearish reversal at resistance';
            case 'Bearish Engulfing': return c.fieldLabel + ' \u2014 strong bearish reversal';
            case 'Evening Star': return c.fieldLabel + ' \u2014 bearish topping pattern';
            case 'no_pattern': return c.fieldLabel;
            case 'Volume Breakout': return c.fieldLabel + ' (close > resistance, vol \u2265 2x avg)';
            case 'Gap Up': return c.fieldLabel + ' (today low > prev high)';
            case 'Gap Down': return c.fieldLabel + ' (today high < prev low)';
            case 'Range Breakout Up': return c.fieldLabel + ' (Bollinger squeeze + close > upper)';
            case 'Range Breakout Down': return c.fieldLabel + ' (Bollinger squeeze + close < lower)';
            case 'NONE': return c.fieldLabel;
            default: return c.fieldLabel || (c.operator + ' ' + (c.thresholdValue || ''));
        }
    }

    function buildLocalState(data) {
        return data.map(s => ({
            strategyName: s.strategyName,
            displayName: s.displayName,
            priority: s.priority,
            active: s.active,
            tags: s.tags || [],
            totalStocksMatched: s.totalStocksMatched || 0,
            conditions: (s.conditions || []).map(c => ({ ...c })),
            originalPriority: s.priority,
            originalConditions: JSON.stringify(s.conditions || [])
        }));
    }

    function serializeConditions(strategy) {
        return strategy.conditions.map(c => ({
            conditionId: c.conditionId,
            signal: c.signal,
            fieldLabel: c.fieldLabel,
            operator: c.operator,
            thresholdValue: c.thresholdValue,
            thresholdValue2: c.thresholdValue2,
            confidence: c.confidence,
            displayOrder: c.displayOrder,
            enabled: c.enabled
        }));
    }

    async function init() {
        strategyContainers.list = document.getElementById('strategyList');
        strategyContainers.activeCount = document.getElementById('activeCount');
        strategyContainers.saveBtn = document.getElementById('saveBtn');
        try {
            const data = await getStrategyFull();
            state.strategies = buildLocalState(data);
            renderStrategies();
            updateStatsCounts();
        } catch (err) {
            showToast('Failed to load strategy config: ' + err.message, 'error');
        }
    }

    function renderStrategies() {
        const html = state.strategies.map((s, idx) => {
            const activeCount = state.strategies.filter(x => x.active).length;
            strategyContainers.activeCount.textContent = activeCount + ' of 7 active';
            const disabled = !s.active ? 'strategy-disabled' : '';
            return `
                <div class="border-b border-gray-700 last:border-b-0" data-strategy-idx="${idx}">
                    <div class="flex items-center gap-4 px-4 py-4 ${disabled}">
                        <div class="flex-1 min-w-0">
                            <div class="font-semibold text-white">${s.displayName || s.strategyName}</div>
                            <div class="flex flex-wrap gap-1.5 mt-1">
                                ${s.tags.map(t => '<span class="text-xs px-2 py-0.5 rounded-full bg-blue-950 text-blue-300">' + t + '</span>').join('')}
                            </div>
                        </div>
                        <div class="flex items-center gap-2 text-sm text-gray-400">
                            <i class="fas fa-chart-bar text-blue-400"></i>
                            <span class="stock-count-pill font-mono" data-strategy="${s.strategyName}">
                                ${s.totalStocksMatched !== undefined ? s.totalStocksMatched : '\u2014'}
                            </span>
                            <span class="text-xs text-gray-400">stocks matched</span>
                        </div>
                        <div class="flex items-center gap-2">
                            <label class="text-xs text-gray-400">Priority</label>
                            <input type="range" min="1" max="10" value="${s.priority}"
                                data-strategy="${s.strategyName}"
                                class="w-20 h-1.5 rounded-full appearance-none bg-gray-600 accent-blue-500 cursor-pointer priority-slider"
                                ${!s.active ? 'disabled' : ''}>
                            <span class="priority-readout font-mono text-sm w-5 text-center text-white">${s.priority}</span>
                        </div>
                        <button class="px-3 py-1.5 text-sm text-gray-300 hover:text-white hover:bg-gray-700 rounded-lg transition-colors rules-btn"
                            data-strategy="${s.strategyName}">
                            <i class="fas fa-list-ul mr-1.5"></i>Rules
                            <i class="fas fa-chevron-down ml-1 text-xs"></i>
                        </button>
                        <div class="strategy-toggle ${s.active ? 'active' : ''}"
                            data-strategy="${s.strategyName}">
                        </div>
                    </div>
                    <div class="rules-panel hidden border-t border-gray-700 bg-gray-800/50" data-strategy="${s.strategyName}"></div>
                </div>
            `;
        }).join('');
        strategyContainers.list.innerHTML = html;
        attachEventListeners();
    }

    function attachEventListeners() {
        document.querySelectorAll('.priority-slider').forEach(slider => {
            slider.addEventListener('input', function () {
                const strategyName = this.dataset.strategy;
                const s = state.strategies.find(x => x.strategyName === strategyName);
                if (!s) return;
                s.priority = parseInt(this.value);
                this.parentElement.querySelector('.priority-readout').textContent = s.priority;
                state.dirty = true;
                updateSaveButton();
            });
        });

        document.querySelectorAll('.rules-btn').forEach(btn => {
            btn.addEventListener('click', function () {
                const strategyName = this.dataset.strategy;
                togglePanel(strategyName);
            });
        });

        document.querySelectorAll('.strategy-toggle').forEach(el => {
            el.addEventListener('click', async function () {
                const strategyName = this.dataset.strategy;
                const s = state.strategies.find(x => x.strategyName === strategyName);
                if (!s) return;
                const wasActive = s.active;
                const newActive = !wasActive;
                s.active = newActive;
                this.classList.toggle('active');
                const row = this.closest('[data-strategy-idx]');
                if (row) {
                    const content = row.querySelector('.flex.items-center.gap-4');
                    if (content) {
                        if (newActive) {
                            content.classList.remove('strategy-disabled');
                            content.querySelectorAll('.priority-slider').forEach(sl => sl.disabled = false);
                        } else {
                            content.classList.add('strategy-disabled');
                            content.querySelectorAll('.priority-slider').forEach(sl => sl.disabled = true);
                        }
                    }
                }
                const activeCount = state.strategies.filter(x => x.active).length;
                if (strategyContainers.activeCount) {
                    strategyContainers.activeCount.textContent = activeCount + ' of 7 active';
                }
                try {
                    await toggleStrategyConfig(strategyName, newActive);
                } catch (err) {
                    s.active = wasActive;
                    this.classList.toggle('active');
                    if (row) {
                        const content = row.querySelector('.flex.items-center.gap-4');
                        if (content) {
                            if (wasActive) {
                                content.classList.remove('strategy-disabled');
                                content.querySelectorAll('.priority-slider').forEach(sl => sl.disabled = false);
                            } else {
                                content.classList.add('strategy-disabled');
                                content.querySelectorAll('.priority-slider').forEach(sl => sl.disabled = true);
                            }
                        }
                    }
                    strategyContainers.activeCount.textContent = state.strategies.filter(x => x.active).length + ' of 7 active';
                    showToast('Failed to update strategy: ' + err.message, 'error');
                }
            });
        });
    }

    function togglePanel(strategyName) {
        if (state.editingId && state.editingCondId !== null) {
            if (!confirm('Discard unsaved condition changes?')) return;
            state.editingCondId = null;
        }
        if (state.openPanelId === strategyName) {
            document.querySelector('.rules-panel[data-strategy="' + strategyName + '"]').classList.add('hidden');
            state.openPanelId = null;
            return;
        }
        if (state.openPanelId) {
            const prev = document.querySelector('.rules-panel[data-strategy="' + state.openPanelId + '"]');
            if (prev) prev.classList.add('hidden');
        }
        state.openPanelId = strategyName;
        state.editingId = null;
        state.editingCondId = null;
        const panel = document.querySelector('.rules-panel[data-strategy="' + strategyName + '"]');
        panel.classList.remove('hidden');
        const s = state.strategies.find(x => x.strategyName === strategyName);
        if (s) renderPanel(strategyName);
    }

    function renderPanel(strategyName) {
        const s = state.strategies.find(x => x.strategyName === strategyName);
        if (!s) return;
        const panel = document.querySelector('.rules-panel[data-strategy="' + strategyName + '"]');
        const isEditing = state.editingId === strategyName;

        let conditionsHtml = '';
        if (s.conditions.length === 0 && isEditing) {
            conditionsHtml = '<p class="text-gray-500 text-sm py-4 text-center">No conditions defined. Add one below.</p>';
        } else {
            conditionsHtml = s.conditions.map((c, ci) => {
                if (isEditing && state.editingCondId === c.conditionId) {
                    return renderConditionEditForm(s, c, ci);
                }
                return renderConditionView(c, ci, strategyName);
            }).join('');
        }

        panel.innerHTML = `
            <div class="px-4 py-3">
                <div class="flex items-center justify-between mb-3">
                    <span class="text-sm font-medium text-gray-300">
                        <i class="fas fa-list-check text-gray-400 mr-1.5"></i>Signal conditions \u00b7 ${s.conditions.length} rules
                    </span>
                    <button class="px-3 py-1 text-sm rounded-lg transition-colors edit-mode-btn
                        ${isEditing ? 'bg-green-800 hover:bg-green-900 text-green-100' : 'bg-gray-700 hover:bg-gray-600 text-gray-200'}">
                        <i class="fas ${isEditing ? 'fa-check' : 'fa-pencil'} mr-1"></i>${isEditing ? 'Done editing' : 'Edit rules'}
                    </button>
                </div>
                ${isEditing ? '<div class="bg-blue-900/30 border border-blue-700 rounded-lg px-3 py-2 mb-3 text-xs text-blue-300"><i class="fas fa-info-circle mr-1"></i>Changes are saved when you click Save above.</div>' : ''}
                <div class="space-y-2 condition-list">${conditionsHtml}</div>
                ${isEditing ? '<button class="w-full mt-3 py-2 border-2 border-dashed border-gray-600 rounded-lg text-sm text-gray-400 hover:text-gray-200 hover:border-gray-400 transition-colors add-condition-btn"><i class="fas fa-plus mr-1"></i>Add condition</button>' : ''}
                <div class="mt-3 pt-3 border-t border-gray-700 text-xs text-gray-500">
                    <i class="fas fa-info-circle mr-1"></i>Returns HOLD if insufficient data is available for this stock.
                </div>
            </div>
        `;

        panel.querySelector('.edit-mode-btn').addEventListener('click', function () {
            if (isEditing) {
                state.editingId = null;
                state.editingCondId = null;
            } else {
                state.editingId = strategyName;
                state.editingCondId = null;
            }
            renderPanel(strategyName);
        });

        panel.querySelectorAll('.condition-toggle').forEach(btn => {
            btn.addEventListener('click', async function () {
                const conditionId = this.dataset.conditionId;
                const currentlyEnabled = this.dataset.enabled === 'true';
                const newEnabled = !currentlyEnabled;
                const c = s.conditions.find(x => x.conditionId === conditionId);
                if (c) {
                    c.enabled = newEnabled;
                }
                renderPanel(strategyName);
                try {
                    await toggleCondition(strategyName, conditionId, newEnabled);
                } catch (err) {
                    if (c) {
                        c.enabled = currentlyEnabled;
                    }
                    renderPanel(strategyName);
                    showToast('Failed to toggle: ' + err.message, 'error');
                }
            });
        });

        if (isEditing) {
            const addBtn = panel.querySelector('.add-condition-btn');
            if (addBtn) {
                addBtn.addEventListener('click', function () {
                    const newCond = {
                        conditionId: 'cond_' + Date.now(),
                        signal: 'BUY',
                        fieldLabel: 'RSI',
                        operator: '<',
                        thresholdValue: null,
                        thresholdValue2: null,
                        confidence: 'mid',
                        displayOrder: s.conditions.length,
                        stockCount: 0,
                        enabled: true
                    };
                    s.conditions.push(newCond);
                    state.dirty = true;
                    updateSaveButton();
                    renderPanel(strategyName);
                });
            }

            panel.querySelectorAll('.delete-condition-btn').forEach(btn => {
                btn.addEventListener('click', function () {
                    const condId = this.dataset.conditionId;
                    s.conditions = s.conditions.filter(c => c.conditionId !== condId);
                    state.dirty = true;
                    updateSaveButton();
                    renderPanel(strategyName);
                });
            });

            panel.querySelectorAll('.edit-condition-btn').forEach(btn => {
                btn.addEventListener('click', function () {
                    state.editingCondId = this.dataset.conditionId;
                    renderPanel(strategyName);
                });
            });

            panel.querySelectorAll('.cancel-edit-btn').forEach(btn => {
                btn.addEventListener('click', function () {
                    state.editingCondId = null;
                    renderPanel(strategyName);
                });
            });

            panel.querySelectorAll('.apply-edit-btn').forEach(btn => {
                btn.addEventListener('click', function () {
                    const condId = this.dataset.conditionId;
                    const c = s.conditions.find(x => x.conditionId === condId);
                    if (!c) return;
                    const form = this.closest('.edit-form');
                    c.signal = form.querySelector('.edit-signal').value;
                    c.confidence = form.querySelector('.edit-confidence').value;
                    c.fieldLabel = form.querySelector('.edit-field').value;
                    c.operator = form.querySelector('.edit-operator').value;
                    const val1 = form.querySelector('.edit-value');
                    c.thresholdValue = val1 && val1.value !== '' ? parseFloat(val1.value) : null;
                    const val2 = form.querySelector('.edit-value2');
                    c.thresholdValue2 = val2 && val2.value !== '' ? parseFloat(val2.value) : null;
                    state.dirty = true;
                    updateSaveButton();
                    state.editingCondId = null;
                    renderPanel(strategyName);
                });
            });

            panel.querySelectorAll('.edit-operator').forEach(sel => {
                sel.addEventListener('change', function () {
                    const form = this.closest('.edit-form');
                    const showVal = ['<', '>', '<=', '>=', '==', 'between', 'within'].includes(this.value);
                    const showVal2 = this.value === 'between';
                    form.querySelector('.edit-value-wrap').classList.toggle('hidden', !showVal);
                    form.querySelector('.edit-value2-wrap').classList.toggle('hidden', !showVal2);
                });
            });
        }
    }

    function renderConditionView(c, idx, strategyName) {
        const disabledCls = !c.enabled ? 'opacity-40' : '';
        const toggleTitle = c.enabled ? 'Disable this condition' : 'Enable this condition';
        const offBadge = !c.enabled ? '<span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-gray-600 text-gray-300 mr-2">OFF</span>' : '';
        return `
            <div class="flex items-start gap-3 p-3 rounded-lg bg-gray-800/50 border border-gray-700/50 condition-card ${disabledCls}">
                <div class="flex-shrink-0">
                    ${offBadge}${c.enabled ? signalBadge(c.signal) : ''}
                </div>
                <div class="flex-1 min-w-0">
                    <div class="text-sm text-gray-200">${renderConditionText(c)}</div>
                    <div class="flex items-center gap-3 mt-1 text-xs text-gray-400">
                        <span class="flex items-center">${confidenceDot(c.confidence)}${c.confidence}</span>
                        <span class="flex items-center"><i class="fas fa-chart-bar text-blue-400 mr-1"></i>${c.stockCount || 0} stocks</span>
                    </div>
                </div>
                <div class="flex items-center gap-1 flex-shrink-0">
                    <button class="condition-toggle px-2 py-1 text-xs rounded transition-colors ${c.enabled ? 'bg-green-800 hover:bg-green-900 text-green-100' : 'bg-gray-600 hover:bg-gray-500 text-gray-200'}"
                        data-strategy="${strategyName}" data-condition-id="${c.conditionId}" data-enabled="${c.enabled}"
                        title="${toggleTitle}">
                        ${c.enabled ? 'ON' : 'OFF'}
                    </button>
                    ${state.editingId ? '<div class="flex gap-1 flex-shrink-0"><button class="edit-condition-btn px-2 py-1 text-xs text-gray-400 hover:text-white hover:bg-gray-700 rounded transition-colors" data-condition-id="' + c.conditionId + '"><i class="fas fa-pencil"></i></button><button class="delete-condition-btn px-2 py-1 text-xs text-red-400 hover:text-red-300 hover:bg-red-900/30 rounded transition-colors" data-condition-id="' + c.conditionId + '"><i class="fas fa-trash"></i></button></div>' : ''}
                </div>
            </div>
        `;
    }

    function renderConditionEditForm(s, c, ci) {
        const showVal = ['<', '>', '<=', '>=', '==', 'between', 'within'].includes(c.operator);
        const showVal2 = c.operator === 'between';
        return `
            <div class="p-3 rounded-lg bg-gray-700/50 border border-blue-500/50 edit-form">
                <div class="grid grid-cols-2 gap-2 text-xs">
                    <div>
                        <label class="text-gray-400 block mb-0.5">Signal</label>
                        <select class="edit-signal w-full px-2 py-1 rounded bg-gray-800 border border-gray-600 text-white text-xs">
                            <option value="BUY" ${c.signal === 'BUY' ? 'selected' : ''}>BUY</option>
                            <option value="SELL" ${c.signal === 'SELL' ? 'selected' : ''}>SELL</option>
                            <option value="HOLD" ${c.signal === 'HOLD' ? 'selected' : ''}>HOLD</option>
                        </select>
                    </div>
                    <div>
                        <label class="text-gray-400 block mb-0.5">Confidence</label>
                        <select class="edit-confidence w-full px-2 py-1 rounded bg-gray-800 border border-gray-600 text-white text-xs">
                            <option value="high" ${c.confidence === 'high' ? 'selected' : ''}>high</option>
                            <option value="mid" ${c.confidence === 'mid' ? 'selected' : ''}>mid</option>
                            <option value="low" ${c.confidence === 'low' ? 'selected' : ''}>low</option>
                        </select>
                    </div>
                    <div>
                        <label class="text-gray-400 block mb-0.5">Field</label>
                        <input type="text" class="edit-field w-full px-2 py-1 rounded bg-gray-800 border border-gray-600 text-white text-xs" value="${c.fieldLabel}">
                    </div>
                    <div>
                        <label class="text-gray-400 block mb-0.5">Operator</label>
                        <select class="edit-operator w-full px-2 py-1 rounded bg-gray-800 border border-gray-600 text-white text-xs">
                            ${['<','>','<=','>=','==','between','within','crossover_above','crossover_below','both_above','both_below','alignment_bullish','alignment_bearish','golden_cross','death_cross','mixed','obv_up','obv_down'].map(op =>
                                '<option value="' + op + '" ' + (c.operator === op ? 'selected' : '') + '>' + op + '</option>'
                            ).join('')}
                        </select>
                    </div>
                    <div class="edit-value-wrap ${showVal ? '' : 'hidden'}">
                        <label class="text-gray-400 block mb-0.5">Value</label>
                        <input type="number" step="any" class="edit-value w-full px-2 py-1 rounded bg-gray-800 border border-gray-600 text-white text-xs" value="${c.thresholdValue !== null && c.thresholdValue !== undefined ? c.thresholdValue : ''}">
                    </div>
                    <div class="edit-value2-wrap ${showVal2 ? '' : 'hidden'}">
                        <label class="text-gray-400 block mb-0.5">Value 2</label>
                        <input type="number" step="any" class="edit-value2 w-full px-2 py-1 rounded bg-gray-800 border border-gray-600 text-white text-xs" value="${c.thresholdValue2 !== null && c.thresholdValue2 !== undefined ? c.thresholdValue2 : ''}">
                    </div>
                </div>
                <div class="flex justify-end gap-2 mt-2">
                    <button class="cancel-edit-btn px-3 py-1 text-xs rounded bg-gray-600 hover:bg-gray-500 text-gray-200 transition-colors" data-condition-id="${c.conditionId}">Cancel</button>
                    <button class="apply-edit-btn px-3 py-1 text-xs rounded bg-blue-600 hover:bg-blue-700 text-white transition-colors" data-condition-id="${c.conditionId}">Apply</button>
                </div>
            </div>
        `;
    }

    function updateStatsCounts() {
        if (!state.strategies.length) return;
        document.querySelectorAll('.stock-count-pill').forEach(el => {
            const name = el.dataset.strategy;
            const s = state.strategies.find(x => x.strategyName === name);
            if (s) el.textContent = s.totalStocksMatched !== undefined ? s.totalStocksMatched : '\u2014';
        });
    }

    function updateSaveButton() {
        if (strategyContainers.saveBtn) {
            const isDisabled = !state.dirty;
            strategyContainers.saveBtn.disabled = isDisabled;
            // Use gray styling instead of opacity to preserve contrast
            strategyContainers.saveBtn.classList.toggle('cursor-not-allowed', isDisabled);
            if (isDisabled) {
                strategyContainers.saveBtn.classList.remove('bg-blue-600', 'hover:bg-blue-700', 'text-white');
                strategyContainers.saveBtn.classList.add('bg-gray-600', 'text-gray-300');
            } else {
                strategyContainers.saveBtn.classList.remove('bg-gray-600', 'text-gray-300');
                strategyContainers.saveBtn.classList.add('bg-blue-600', 'hover:bg-blue-700', 'text-white');
            }
        }
    }

    async function handleSave() {
        if (!state.dirty) return;
        strategyContainers.saveBtn.disabled = true;
        try {
            for (const s of state.strategies) {
                if (s.priority !== s.originalPriority) {
                    await updateStrategyPriority(s.strategyName, s.priority);
                    s.originalPriority = s.priority;
                }
                const currentConds = JSON.stringify(serializeConditions(s));
                if (currentConds !== s.originalConditions) {
                    await saveStrategyConditions(s.strategyName, serializeConditions(s));
                    s.originalConditions = currentConds;
                }
            }
            state.dirty = false;
            updateSaveButton();
            showToast('Saved', 'success');
            for (const s of state.strategies) {
                s.originalPriority = s.priority;
                s.originalConditions = JSON.stringify(serializeConditions(s));
            }
            renderStrategies();
            updateStatsCounts();
        } catch (err) {
            showToast(err.message || 'Failed to save', 'error');
            strategyContainers.saveBtn.disabled = false;
        }
    }

    async function handleReset() {
        if (!confirm('Reset all strategy conditions to defaults? Priorities and active/inactive state are not affected. This cannot be undone.')) return;
        try {
            await resetStrategyConditions();
            showToast('Reset to defaults', 'success');
            const data = await getStrategyFull();
            state.strategies = buildLocalState(data);
            state.dirty = false;
            state.openPanelId = null;
            state.editingId = null;
            state.editingCondId = null;
            renderStrategies();
            updateStatsCounts();
            updateSaveButton();
        } catch (err) {
            showToast('Failed to reset: ' + err.message, 'error');
        }
    }

    window.addEventListener('beforeunload', function (e) {
        if (state.dirty) {
            e.preventDefault();
            e.returnValue = 'You have unsaved changes.';
        }
    });

    document.addEventListener('DOMContentLoaded', function () {
        init();

        const saveBtn = document.getElementById('saveBtn');
        if (saveBtn) saveBtn.addEventListener('click', handleSave);

        const resetLink = document.getElementById('resetLink');
        if (resetLink) resetLink.addEventListener('click', handleReset);
    });

})();
