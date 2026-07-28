package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.SMCPatternDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.service.calculator.ATRCalculator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart Money Concepts (SMC/ICT) Pattern Detection Service.
 * <p>
 * Rewritten to match the LuxAlgo "Smart Money Concepts" TradingView indicator methodology.
 * Key concepts from the Pine Script:
 * <ul>
 *   <li><b>Two-tier structure</b> — Swing (size=50) + Internal (size=5)</li>
 *   <li><b>Leg-based swing detection</b> — {@code high[size] > highest(high,size)} style pivots</li>
 *   <li><b>BOS vs CHoCH by trend direction</b> — cross pivot AGAINST trend = CHoCH, WITH trend = BOS</li>
 *   <li><b>Order blocks</b> — highest/lowest at same-candle index in parsed arrays between pivot and breakout</li>
 *   <li><b>High volatility parsing</b> — swap parsedHigh/parsedLow when range ≥ 2×ATR(200)</li>
 *   <li><b>EQH/EQL</b> — equal swing levels within threshold×ATR</li>
 *   <li><b>Trailing extremes</b> — running max/min for premium/discount zones</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SMCDetectionService {

    private final DailyPriceRepository dailyPriceRepository;
    private final StockService stockService;

    // ── LuxAlgo Constants ────────────────────────────────────────────────────
    private static final int MIN_LOOKBACK = 30;
    /** Swing structure leg size (default 50 — major swings). */
    private static final int SWING_STRENGTH = 50;
    /** Internal structure leg size (default 5 — minor swings). */
    private static final int INTERNAL_STRENGTH = 5;
    /** ATR period for volatility measure (200 as in Pine). */
    private static final int ATR_PERIOD = 200;
    /** High volatility threshold: range ≥ 2 × ATR. */
    private static final double HIGH_VOLATILITY_THRESHOLD = 2.0;
    /** EQH/EQL threshold as fraction of ATR. */
    private static final double EQUAL_THRESHOLD_FACTOR = 0.1;
    /** Maximum results per pattern type. */
    private static final int MAX_PER_TYPE = 20;

    // ── Legacy constants (for FVG, LS, IND, dedup) ──────────────────────────
    private static final double MIN_FVG_GAP_PCT = 0.001;
    private static final double FVG_MIN_ATR_MULTIPLIER = 0.5;
    private static final double SWEEP_THRESHOLD_PCT = 0.003;
    private static final double INDUCEMENT_THRESHOLD_PCT = 0.002;
    private static final int LS_LOOKBACK_BARS = 20;
    private static final int PIVOT_WINDOW = 5;
    private static final double OVERLAP_ATR_FRACTION = 0.5;

    // ── Enums ────────────────────────────────────────────────────────────────
    private enum Direction { BULLISH, BEARISH }
    private enum Leg { BEARISH_LEG, BULLISH_LEG }
    private enum FVGState { OPEN, PARTIALLY_FILLED, FILLED }

    // ── Internal Data Types (LuxAlgo style) ────────────────────────────────

    /** A swing pivot point. Pine's {@code pivot} UDT using null instead of ZERO. */
    private static class Pivot {
        BigDecimal currentLevel;
        BigDecimal lastLevel;
        boolean crossed = false;
        LocalDate barTime;
        int barIndex = -1;
    }

    /** Trend state — just a bias, flips on every BOS/CHoCH. null = undefined/neutral (first detection). */
    private static class Trend {
        Direction bias;
    }

    /** Trailing extremes for PD zones + strong/weak labels. */
    private static class TrailingExtremes {
        BigDecimal top;
        BigDecimal bottom;
        LocalDate lastTopTime;
        LocalDate lastBottomTime;
        int lastTopIndex = -1;
        int lastBottomIndex = -1;
    }

    /** Order block record. Pine's {@code orderBlock} UDT. */
    private record OrderBlock(BigDecimal barHigh, BigDecimal barLow, LocalDate barTime, Direction bias) {}

    /**
     * Structure state for one level (swing OR internal).
     * Mirrors the Pine Script's per-level variables: swingHigh, swingLow, trend, orderBlocks.
     */
    private static class StructureState {
        final Pivot swingHigh = new Pivot();
        final Pivot swingLow = new Pivot();
        final Trend trend = new Trend();
        final List<OrderBlock> orderBlocks = new ArrayList<>();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = "smcPatterns", unless = "#result == null")
    public SMCPatternDTO detect(Long stockId, int lookbackDays) {
        Stock stock = stockService.getStockById(stockId);
        int neededDays = Math.max(lookbackDays, MIN_LOOKBACK);
        // Fetch extra buffer for large swing strength
        List<DailyPrice> prices = dailyPriceRepository.findLastNDays(stockId, neededDays + SWING_STRENGTH);
        Collections.reverse(prices);
        if (prices.size() < MIN_LOOKBACK) {
            log.warn("Insufficient data ({}) for SMC on {}", prices.size(), stock.getSymbol());
            return null;
        }
        return detectFromPrices(prices, stock);
    }

    public SMCPatternDTO detectFromPrices(List<DailyPrice> prices, Stock stock) {
        long startNanos = System.nanoTime();

        DailyPrice lastPrice = prices.get(prices.size() - 1);
        SMCPatternDTO dto = new SMCPatternDTO();
        dto.setStockId(stock.getId());
        dto.setSymbol(stock.getSymbol());
        dto.setCalculationDate(lastPrice.getPriceDate());

        // 0. Compute ATR measures
        BigDecimal atr200 = computeATR(prices, ATR_PERIOD);    // Volatility gauge (for high-vol filtering)
        BigDecimal atr14 = computeATR(prices, 14);               // For threshold checks

        // 1. Compute parsed (high-vol filtered) price arrays — LuxAlgo style
        List<BigDecimal> parsedHighs = computeParsedHighs(prices, atr200);
        List<BigDecimal> parsedLows = computeParsedLows(prices, atr200);

        // 2. Build structure states for both levels
        StructureState swingState = new StructureState();
        StructureState internalState = new StructureState();

        // 3. Result collectors
        List<SMCPatternDTO.BOSEntry> swingBOS = new ArrayList<>();
        List<SMCPatternDTO.CHoCHEntry> swingCHoCH = new ArrayList<>();
        List<SMCPatternDTO.BOSEntry> internalBOS = new ArrayList<>();
        List<SMCPatternDTO.CHoCHEntry> internalCHoCH = new ArrayList<>();
        List<SMCPatternDTO.EqualHighLowEntry> equalHighLows = new ArrayList<>();
        List<SMCPatternDTO.FVGEntry> fvgEntries = new ArrayList<>();
        List<SMCPatternDTO.InducementEntry> inducementEntries = new ArrayList<>();
        List<SMCPatternDTO.LiquiditySweepEntry> liquiditySweepEntries = new ArrayList<>();

        // Trailing extremes (for PD zones + strong/weak labels)
        TrailingExtremes trailing = new TrailingExtremes();
        trailing.top = prices.get(0).getHighPrice();
        trailing.bottom = prices.get(0).getLowPrice();
        trailing.lastTopTime = prices.get(0).getPriceDate();
        trailing.lastBottomTime = prices.get(0).getPriceDate();

        // For legacy LS/IND detection: collect swing levels
        Set<Integer> legacyHighIdx = new HashSet<>();
        Set<Integer> legacyLowIdx = new HashSet<>();

        int n = prices.size();
        int maxLegSize = Math.max(SWING_STRENGTH, INTERNAL_STRENGTH);

        // 4. MAIN LOOP — one pass over prices
        for (int i = maxLegSize; i < n; i++) {
            DailyPrice curr = prices.get(i);
            BigDecimal high = curr.getHighPrice();
            BigDecimal low = curr.getLowPrice();
            BigDecimal close = curr.getClosingPrice();

            // Update trailing extremes
            if (trailing.top == null || high.compareTo(trailing.top) > 0) {
                trailing.top = high;
                trailing.lastTopTime = curr.getPriceDate();
                trailing.lastTopIndex = i;
            }
            if (trailing.bottom == null || low.compareTo(trailing.bottom) < 0) {
                trailing.bottom = low;
                trailing.lastBottomTime = curr.getPriceDate();
                trailing.lastBottomIndex = i;
            }

            // 4a. Process swing structure (size = SWING_STRENGTH)
            if (i >= SWING_STRENGTH) {
                processStructureLevel(prices, parsedHighs, parsedLows, i, swingState,
                        SWING_STRENGTH, swingBOS, swingCHoCH, equalHighLows, atr14,
                        trailing, legacyHighIdx, legacyLowIdx);
            }

            // 4b. Process internal structure (size = INTERNAL_STRENGTH)
            if (i >= INTERNAL_STRENGTH) {
                processStructureLevel(prices, parsedHighs, parsedLows, i, internalState,
                        INTERNAL_STRENGTH, internalBOS, internalCHoCH, equalHighLows, atr14,
                        trailing, legacyHighIdx, legacyLowIdx);
            }

            // 4c. FVG detection (inline 3-candle gap)
            if (i >= 2) {
                addFvgIfQualified(fvgEntries, prices, i, atr14);
            }

            // 4d. Delete mitigated order blocks (Pine: deleteOrderBlocks())
            deleteMitigatedOrderBlocks(swingState, curr);
            deleteMitigatedOrderBlocks(internalState, curr);
        }

        // 5. Convert order blocks to DTOs
        List<SMCPatternDTO.OrderBlockEntry> swingOBEntries = convertOrderBlocksToDTO(swingState.orderBlocks, true);
        List<SMCPatternDTO.OrderBlockEntry> internalOBEntries = convertOrderBlocksToDTO(internalState.orderBlocks, false);

        // 6. Detect liquidity sweeps and inducements (legacy — using swing levels)
        if (!legacyHighIdx.isEmpty() || !legacyLowIdx.isEmpty()) {
            liquiditySweepEntries = detectLiquiditySweeps(prices, legacyHighIdx, legacyLowIdx, atr14);
            inducementEntries = detectInducements(prices, legacyHighIdx, legacyLowIdx, atr14);
        }

        // 7. Assemble DTO
        dto.setBreakOfStructure(cap(swingBOS));
        dto.setChangeOfCharacter(cap(swingCHoCH));
        dto.setInternalBreakOfStructure(cap(internalBOS));
        dto.setInternalChangeOfCharacter(cap(internalCHoCH));
        dto.setOrderBlocks(cap(swingOBEntries));
        dto.setInternalOrderBlocks(cap(internalOBEntries));
        dto.setFairValueGaps(cap(fvgEntries));
        dto.setEqualHighsLows(cap(equalHighLows));
        dto.setLiquiditySweeps(cap(liquiditySweepEntries));
        dto.setInducements(cap(inducementEntries));
        dto.setPremiumDiscountZones(detectPremiumDiscountZones(trailing));
        dto.setSwingHighLow(detectSwingHighLow(trailing, swingState.trend));

        // 8. De-duplicate overlapping labels
        deduplicateLabels(dto, atr14);

        int total = count(dto.getBreakOfStructure())
                  + count(dto.getChangeOfCharacter())
                  + count(dto.getInternalBreakOfStructure())
                  + count(dto.getInternalChangeOfCharacter())
                  + count(dto.getFairValueGaps())
                  + count(dto.getLiquiditySweeps())
                  + count(dto.getOrderBlocks())
                  + count(dto.getInternalOrderBlocks())
                  + (dto.getPremiumDiscountZones() != null ? 1 : 0)
                  + count(dto.getInducements())
                  + count(dto.getEqualHighsLows());
        dto.setTotalPatterns(total);

        log.debug("SMC(LuxAlgo) for {}: {} patterns in {} ms", stock.getSymbol(), total,
                (System.nanoTime() - startNanos) / 1_000_000);
        return dto;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LUXALGO: Structure detection (one level — called twice: swing + internal)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processes one bar for ONE structure level.
     * Pine equivalent: {@code getCurrentStructure()} + {@code displayStructure()}.
     */
    private void processStructureLevel(
            List<DailyPrice> prices,
            List<BigDecimal> parsedHighs,
            List<BigDecimal> parsedLows,
            int i,
            StructureState state,
            int size,
            List<SMCPatternDTO.BOSEntry> bosResults,
            List<SMCPatternDTO.CHoCHEntry> chochResults,
            List<SMCPatternDTO.EqualHighLowEntry> eqResults,
            BigDecimal atr,
            TrailingExtremes trailing,
            Set<Integer> legacyHighIdx,
            Set<Integer> legacyLowIdx) {

        DailyPrice curr = prices.get(i);
        BigDecimal high = curr.getHighPrice();
        BigDecimal low = curr.getLowPrice();
        BigDecimal close = curr.getClosingPrice();

        // ── Leg detection (Pine: leg(size)) ──
        // high[size] > ta.highest(high, size) → new bearish leg
        // low[size] < ta.lowest(low, size) → new bullish leg
        // ta.highest(size) looks at bars i-size+1 through i (inclusive) = size bars
        // high[size] is prices[i - size]
        BigDecimal swingHighPrice = prices.get(i - size).getHighPrice();
        BigDecimal swingLowPrice = prices.get(i - size).getLowPrice();

        // Find highest high and lowest low among the last `size` bars (i-size+1 to i)
        BigDecimal highestInRange = swingHighPrice;
        BigDecimal lowestInRange = swingLowPrice;
        for (int k = Math.max(0, i - size + 1); k <= i - 1; k++) {
            BigDecimal h = prices.get(k).getHighPrice();
            BigDecimal l = prices.get(k).getLowPrice();
            if (h.compareTo(highestInRange) > 0) highestInRange = h;
            if (l.compareTo(lowestInRange) < 0) lowestInRange = l;
        }
        // Include current bar (bar i) in the lookback
        if (high.compareTo(highestInRange) > 0) highestInRange = high;
        if (low.compareTo(lowestInRange) < 0) lowestInRange = low;

        boolean newLegHigh = swingHighPrice.compareTo(highestInRange) > 0;
        boolean newLegLow = swingLowPrice.compareTo(lowestInRange) < 0;

        Leg currentLeg = Leg.BULLISH_LEG;
        if (newLegHigh) currentLeg = Leg.BEARISH_LEG;
        else if (newLegLow) currentLeg = Leg.BULLISH_LEG;

        boolean startOfBearishLeg = (currentLeg == Leg.BEARISH_LEG);
        boolean startOfBullishLeg = (currentLeg == Leg.BULLISH_LEG);

        // Detect if leg JUST CHANGED (Pine: ta.change(leg) != 0 → startOfNewLeg)
        // Compute leg at i-1 to compare
        boolean newLegStarted = false;
        if (i > size) {
            BigDecimal prevSwingHigh = prices.get(i - 1 - size).getHighPrice();
            BigDecimal prevSwingLow = prices.get(i - 1 - size).getLowPrice();

            BigDecimal prevHighest = prevSwingHigh;
            BigDecimal prevLowest = prevSwingLow;
            for (int k = Math.max(0, i - size); k <= i - 2; k++) {
                BigDecimal h = prices.get(k).getHighPrice();
                BigDecimal l = prices.get(k).getLowPrice();
                if (h.compareTo(prevHighest) > 0) prevHighest = h;
                if (l.compareTo(prevLowest) < 0) prevLowest = l;
            }
            // Include bar i-1 in lookback for previous leg
            BigDecimal prevBarH = prices.get(i - 1).getHighPrice();
            BigDecimal prevBarL = prices.get(i - 1).getLowPrice();
            if (prevBarH.compareTo(prevHighest) > 0) prevHighest = prevBarH;
            if (prevBarL.compareTo(prevLowest) < 0) prevLowest = prevBarL;

            boolean prevNewHigh = prevSwingHigh.compareTo(prevHighest) > 0;
            boolean prevNewLow = prevSwingLow.compareTo(prevLowest) < 0;
            Leg prevLeg = Leg.BULLISH_LEG;
            if (prevNewHigh) prevLeg = Leg.BEARISH_LEG;
            else if (prevNewLow) prevLeg = Leg.BULLISH_LEG;

            newLegStarted = currentLeg != prevLeg;
        }

        if (newLegStarted) {
            if (startOfBullishLeg) {
                // ── New swing LOW (Bullish leg start) ──
                LocalDate swingTime = prices.get(i - size).getPriceDate();
                int swingIdx = i - size;

                // EQH/EQL check (Pine: getCurrentStructure with equalHighLow=true)
                if (state.swingLow.currentLevel != null) {
                    BigDecimal diff = state.swingLow.currentLevel.subtract(swingLowPrice).abs();
                    if (atr != null && atr.compareTo(BigDecimal.ZERO) > 0
                            && diff.compareTo(atr.multiply(BigDecimal.valueOf(EQUAL_THRESHOLD_FACTOR))) < 0) {
                        eqResults.add(new SMCPatternDTO.EqualHighLowEntry(
                                curr.getPriceDate(), swingLowPrice, false,
                                state.swingLow.currentLevel, "EQL"));
                    }
                }

                // Update swing low pivot
                state.swingLow.lastLevel = state.swingLow.currentLevel;
                state.swingLow.currentLevel = swingLowPrice;
                state.swingLow.crossed = false;
                state.swingLow.barTime = swingTime;
                state.swingLow.barIndex = swingIdx;

                // Update trailing
                trailing.bottom = swingLowPrice;
                trailing.lastBottomTime = swingTime;
                trailing.lastBottomIndex = swingIdx;

                // Add to legacy sets for LS/IND detection
                legacyLowIdx.add(swingIdx);

            } else if (startOfBearishLeg) {
                // ── New swing HIGH (Bearish leg start) ──
                LocalDate swingTime = prices.get(i - size).getPriceDate();
                int swingIdx = i - size;

                // EQH/EQL check
                if (state.swingHigh.currentLevel != null) {
                    BigDecimal diff = state.swingHigh.currentLevel.subtract(swingHighPrice).abs();
                    if (atr != null && atr.compareTo(BigDecimal.ZERO) > 0
                            && diff.compareTo(atr.multiply(BigDecimal.valueOf(EQUAL_THRESHOLD_FACTOR))) < 0) {
                        eqResults.add(new SMCPatternDTO.EqualHighLowEntry(
                                curr.getPriceDate(), swingHighPrice, true,
                                state.swingHigh.currentLevel, "EQH"));
                    }
                }

                // Update swing high pivot
                state.swingHigh.lastLevel = state.swingHigh.currentLevel;
                state.swingHigh.currentLevel = swingHighPrice;
                state.swingHigh.crossed = false;
                state.swingHigh.barTime = swingTime;
                state.swingHigh.barIndex = swingIdx;

                // Update trailing
                trailing.top = swingHighPrice;
                trailing.lastTopTime = swingTime;
                trailing.lastTopIndex = swingIdx;

                // Add to legacy sets for LS/IND detection
                legacyHighIdx.add(swingIdx);
            }
        }

        // ── BOS / CHoCH detection (Pine: displayStructure()) ──
        // Bullish: close crosses ABOVE swing high → tag = trend was BEARISH ? CHoCH : BOS
        if (state.swingHigh.currentLevel != null
                && close.compareTo(state.swingHigh.currentLevel) > 0
                && !state.swingHigh.crossed) {

            Direction prevTrend = state.trend.bias;
            state.swingHigh.crossed = true;
            state.trend.bias = Direction.BULLISH;

        // null trend (first detection ever) → always label as CHoCH (closer to Pine behavior)
        boolean isCHoCH = (prevTrend == null || prevTrend == Direction.BEARISH);
        String label = isCHoCH ? "CHoCH ↑" : "BOS ↑";

        if (isCHoCH) {
            chochResults.add(new SMCPatternDTO.CHoCHEntry(
                    curr.getPriceDate(), close, "BULLISH", label));
        } else {
            bosResults.add(new SMCPatternDTO.BOSEntry(
                    curr.getPriceDate(), close, "BULLISH",
                    state.swingHigh.currentLevel, label));
        }

        // Store order block
        storeOrderBlock(state, prices, parsedHighs, parsedLows, Direction.BULLISH,
                curr.getPriceDate(), i);
    }

    // Bearish: close crosses BELOW swing low → tag = trend was BULLISH ? CHoCH : BOS
    if (state.swingLow.currentLevel != null
            && close.compareTo(state.swingLow.currentLevel) < 0
            && !state.swingLow.crossed) {

            Direction prevTrend = state.trend.bias;
            state.swingLow.crossed = true;
            state.trend.bias = Direction.BEARISH;

        // null trend (first detection ever) → always label as CHoCH
        boolean isCHoCH = (prevTrend == null || prevTrend == Direction.BULLISH);
        String label = isCHoCH ? "CHoCH ↓" : "BOS ↓";

            if (isCHoCH) {
                chochResults.add(new SMCPatternDTO.CHoCHEntry(
                        curr.getPriceDate(), close, "BEARISH", label));
            } else {
                bosResults.add(new SMCPatternDTO.BOSEntry(
                        curr.getPriceDate(), close, "BEARISH",
                        state.swingLow.currentLevel, label));
            }

            // Store order block
            storeOrderBlock(state, prices, parsedHighs, parsedLows, Direction.BEARISH,
                    curr.getPriceDate(), i);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LUXALGO: Order Blocks (same-candle index, Pine style)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Stores an order block using the LuxAlgo method.
     * Pine's {@code storeOrdeBlock()}:
     * <ul>
     *   <li>BEARISH OB: find the INDEX of the MAX parsedHigh between pivot and current,
     *       then use BOTH parsedHighs[index] AND parsedLows[index] (same candle).</li>
     *   <li>BULLISH OB: find the INDEX of the MIN parsedLow between pivot and current,
     *       then use BOTH from that same candle.</li>
     * </ul>
     */
    private void storeOrderBlock(StructureState state,
                                  List<DailyPrice> prices,
                                  List<BigDecimal> parsedHighs,
                                  List<BigDecimal> parsedLows,
                                  Direction bias,
                                  LocalDate currentDate,
                                  int currentIndex) {
        Pivot pivot = (bias == Direction.BEARISH) ? state.swingLow : state.swingHigh;
        int pivotIdx = pivot.barIndex;
        if (pivotIdx < 0 || pivotIdx >= parsedHighs.size()) return;

        int maxIdx = Math.min(currentIndex, parsedHighs.size() - 1);

        if (bias == Direction.BEARISH) {
            // BEARISH: find INDEX of the MAX parsedHigh between pivot and current
            int bestIdx = pivotIdx;
            BigDecimal bestVal = parsedHighs.get(pivotIdx);
            for (int k = pivotIdx + 1; k <= maxIdx; k++) {
                if (parsedHighs.get(k).compareTo(bestVal) > 0) {
                    bestVal = parsedHighs.get(k);
                    bestIdx = k;
                }
            }
            // Use BOTH high and low from the same candle; record the OB candle's ACTUAL date
            LocalDate obDate = (bestIdx >= 0 && bestIdx < prices.size())
                    ? prices.get(bestIdx).getPriceDate()
                    : currentDate;
            OrderBlock ob = new OrderBlock(
                    parsedHighs.get(bestIdx), parsedLows.get(bestIdx), obDate, bias);
            state.orderBlocks.add(0, ob);
        } else {
            // BULLISH: find INDEX of the MIN parsedLow between pivot and current
            int bestIdx = pivotIdx;
            BigDecimal bestVal = parsedLows.get(pivotIdx);
            for (int k = pivotIdx + 1; k <= maxIdx; k++) {
                if (parsedLows.get(k).compareTo(bestVal) < 0) {
                    bestVal = parsedLows.get(k);
                    bestIdx = k;
                }
            }
            // Use BOTH high and low from the same candle; record the OB candle's ACTUAL date
            LocalDate obDate = (bestIdx >= 0 && bestIdx < prices.size())
                    ? prices.get(bestIdx).getPriceDate()
                    : currentDate;
            OrderBlock ob = new OrderBlock(
                    parsedHighs.get(bestIdx), parsedLows.get(bestIdx), obDate, bias);
            state.orderBlocks.add(0, ob);
        }

        // Cap at 100 (Pine behavior)
        if (state.orderBlocks.size() > 100) {
            state.orderBlocks.remove(state.orderBlocks.size() - 1);
        }
    }

    /**
     * Deletes mitigated order blocks (Pine: {@code crossedOderBlock} check).
     * BEARISH OB mitigated when high > ob.barHigh.
     * BULLISH OB mitigated when low < ob.barLow.
     */
    private void deleteMitigatedOrderBlocks(StructureState state, DailyPrice curr) {
        BigDecimal high = curr.getHighPrice();
        BigDecimal low = curr.getLowPrice();

        state.orderBlocks.removeIf(ob -> {
            if (ob.bias() == Direction.BEARISH) {
                return high.compareTo(ob.barHigh()) > 0;
            } else {
                return low.compareTo(ob.barLow()) < 0;
            }
        });
    }

    /** Converts internal order blocks to DTO entries. */
    private List<SMCPatternDTO.OrderBlockEntry> convertOrderBlocksToDTO(
            List<OrderBlock> obs, boolean isSwing) {
        if (obs == null || obs.isEmpty()) return List.of();
        List<SMCPatternDTO.OrderBlockEntry> result = new ArrayList<>();
        for (OrderBlock ob : obs) {
            BigDecimal midpoint = ob.barHigh().add(ob.barLow()).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            String direction = ob.bias() == Direction.BULLISH ? "BULLISH" : "BEARISH";
            String prefix = isSwing ? "" : "I";
            String label = prefix + (ob.bias() == Direction.BULLISH ? "OB ↑" : "OB ↓");
            result.add(new SMCPatternDTO.OrderBlockEntry(
                    ob.barTime(), midpoint, ob.barHigh(), ob.barLow(),
                    direction, false, label, "UNTOUCHED", "MEDIUM", null));
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LUXALGO: High Volatility Parsing
    // ═══════════════════════════════════════════════════════════════════════════

    /** Parsed high: on high-vol bars, use low instead. */
    private List<BigDecimal> computeParsedHighs(List<DailyPrice> prices, BigDecimal atr) {
        List<BigDecimal> parsed = new ArrayList<>();
        for (DailyPrice p : prices) {
            BigDecimal range = p.getHighPrice().subtract(p.getLowPrice());
            if (atr != null && atr.compareTo(BigDecimal.ZERO) > 0
                    && range.compareTo(atr.multiply(BigDecimal.valueOf(HIGH_VOLATILITY_THRESHOLD))) >= 0) {
                parsed.add(p.getLowPrice()); // Swap!
            } else {
                parsed.add(p.getHighPrice());
            }
        }
        return parsed;
    }

    /** Parsed low: on high-vol bars, use high instead. */
    private List<BigDecimal> computeParsedLows(List<DailyPrice> prices, BigDecimal atr) {
        List<BigDecimal> parsed = new ArrayList<>();
        for (DailyPrice p : prices) {
            BigDecimal range = p.getHighPrice().subtract(p.getLowPrice());
            if (atr != null && atr.compareTo(BigDecimal.ZERO) > 0
                    && range.compareTo(atr.multiply(BigDecimal.valueOf(HIGH_VOLATILITY_THRESHOLD))) >= 0) {
                parsed.add(p.getHighPrice()); // Swap!
            } else {
                parsed.add(p.getLowPrice());
            }
        }
        return parsed;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LUXALGO: Premium/Discount Zones + Swing High/Low (from trailing extremes)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Premium/Discount from trailing extremes (Pine: drawPremiumDiscountZones()). */
    private SMCPatternDTO.PremiumDiscountZones detectPremiumDiscountZones(TrailingExtremes trailing) {
        if (trailing.top == null || trailing.bottom == null) return null;
        BigDecimal midpoint = trailing.top.add(trailing.bottom).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        return new SMCPatternDTO.PremiumDiscountZones(
                trailing.top, trailing.bottom, midpoint, midpoint, midpoint, "PD Zone");
    }

    /** Strong/Weak High/Low (Pine: drawHighLowSwings()). */
    private SMCPatternDTO.SwingHighLow detectSwingHighLow(TrailingExtremes trailing, Trend trend) {
        if (trailing.top == null || trailing.bottom == null) return null;
        Direction bias = (trend != null && trend.bias != null) ? trend.bias : Direction.BULLISH;
        return new SMCPatternDTO.SwingHighLow(
                bias == Direction.BEARISH ? trailing.top : null,
                bias == Direction.BULLISH ? trailing.bottom : null,
                bias == Direction.BULLISH ? trailing.top : null,
                bias == Direction.BEARISH ? trailing.bottom : null,
                bias.name(), "Swing HL");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FVG (3-candle imbalance with state tracking)
    // ═══════════════════════════════════════════════════════════════════════════

    private void addFvgIfQualified(List<SMCPatternDTO.FVGEntry> results,
                                    List<DailyPrice> prices, int i, BigDecimal atr) {
        BigDecimal c1h = prices.get(i - 2).getHighPrice();
        BigDecimal c1l = prices.get(i - 2).getLowPrice();
        BigDecimal c3h = prices.get(i).getHighPrice();
        BigDecimal c3l = prices.get(i).getLowPrice();

        BigDecimal bottom, top;
        String direction;

        // Bullish FVG: candle3 low > candle1 high AND candle3 close > candle1 high
        if (c3l.compareTo(c1h) > 0
                && prices.get(i - 1).getClosingPrice().compareTo(c1h) > 0) {
            bottom = c1h;
            top = c3l;
            direction = "BULLISH";
        }
        // Bearish FVG: candle3 high < candle1 low AND candle3 close < candle1 low
        else if (c3h.compareTo(c1l) < 0
                && prices.get(i - 1).getClosingPrice().compareTo(c1l) < 0) {
            bottom = c3h;
            top = c1l;
            direction = "BEARISH";
        } else {
            return;
        }

        BigDecimal gap = top.subtract(bottom).abs();
        if (gap.compareTo(BigDecimal.ZERO) <= 0) return;

        // Minimum gap check
        boolean meetsMin;
        if (atr != null && atr.compareTo(BigDecimal.ZERO) > 0) {
            meetsMin = gap.compareTo(atr.multiply(BigDecimal.valueOf(FVG_MIN_ATR_MULTIPLIER))) >= 0;
        } else {
            BigDecimal midPrice = top.add(bottom).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            meetsMin = gap.compareTo(midPrice.multiply(BigDecimal.valueOf(MIN_FVG_GAP_PCT))) >= 0;
        }
        if (!meetsMin) return;

        BigDecimal midPrice = top.add(bottom).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

        // Determine fill state
        FVGState state = FVGState.OPEN;
        for (int k = i + 1; k < prices.size(); k++) {
            BigDecimal h = prices.get(k).getHighPrice();
            BigDecimal l = prices.get(k).getLowPrice();
            BigDecimal c = prices.get(k).getClosingPrice();
            if (h.compareTo(top) >= 0 && l.compareTo(bottom) <= 0) {
                state = FVGState.FILLED;
                break;
            }
            if ((c.compareTo(top) <= 0 && c.compareTo(bottom) >= 0)
                    || (h.compareTo(bottom) >= 0 && l.compareTo(top) <= 0)) {
                state = FVGState.PARTIALLY_FILLED;
            }
        }

        String label = direction.equals("BULLISH") ? "FVG ↑" : "FVG ↓";
        results.add(new SMCPatternDTO.FVGEntry(
                prices.get(i).getPriceDate(), midPrice, top, bottom,
                direction, label, gap, state.name()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Legacy: Liquidity Sweep (kept for backward compatibility)
    // ═══════════════════════════════════════════════════════════════════════════

    private List<SMCPatternDTO.LiquiditySweepEntry> detectLiquiditySweeps(
            List<DailyPrice> prices, Set<Integer> swingHighIdx, Set<Integer> swingLowIdx,
            BigDecimal atr) {
        List<SMCPatternDTO.LiquiditySweepEntry> results = new ArrayList<>();
        int n = prices.size();

        for (int i = PIVOT_WINDOW + 1; i < n; i++) {
            DailyPrice curr = prices.get(i);
            BigDecimal high = curr.getHighPrice();
            BigDecimal low = curr.getLowPrice();
            BigDecimal close = curr.getClosingPrice();

            int searchStart = Math.max(PIVOT_WINDOW, i - LS_LOOKBACK_BARS);

            // Bearish sweep (above a swing high, close below, next candle lower)
            for (int j = searchStart; j < i; j++) {
                if (!swingHighIdx.contains(j)) continue;
                BigDecimal sh = prices.get(j).getHighPrice();
                BigDecimal limit = sh.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(SWEEP_THRESHOLD_PCT)));
                if (high.compareTo(sh) > 0 && close.compareTo(sh) < 0 && high.compareTo(limit) <= 0) {
                    if (i + 1 < n && prices.get(i + 1).getClosingPrice().compareTo(close) < 0) {
                        results.add(new SMCPatternDTO.LiquiditySweepEntry(
                                curr.getPriceDate(), close, "BEARISH", sh, "LS ↓", "SINGLE_SWING"));
                        break;
                    }
                }
            }

            // Bullish sweep (below a swing low, close above, next candle higher)
            for (int j = searchStart; j < i; j++) {
                if (!swingLowIdx.contains(j)) continue;
                BigDecimal sl = prices.get(j).getLowPrice();
                BigDecimal limit = sl.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(SWEEP_THRESHOLD_PCT)));
                if (low.compareTo(sl) < 0 && close.compareTo(sl) > 0 && low.compareTo(limit) >= 0) {
                    if (i + 1 < n && prices.get(i + 1).getClosingPrice().compareTo(close) > 0) {
                        results.add(new SMCPatternDTO.LiquiditySweepEntry(
                                curr.getPriceDate(), close, "BULLISH", sl, "LS ↑", "SINGLE_SWING"));
                        break;
                    }
                }
            }
        }
        return results.isEmpty() ? null : results;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Legacy: Inducement (kept for backward compatibility)
    // ═══════════════════════════════════════════════════════════════════════════

    private List<SMCPatternDTO.InducementEntry> detectInducements(
            List<DailyPrice> prices, Set<Integer> swingHighIdx, Set<Integer> swingLowIdx,
            BigDecimal atr) {
        List<SMCPatternDTO.InducementEntry> results = new ArrayList<>();
        int n = prices.size();

        for (int i = PIVOT_WINDOW + 1; i < n; i++) {
            DailyPrice curr = prices.get(i);
            BigDecimal high = curr.getHighPrice();
            BigDecimal low = curr.getLowPrice();
            BigDecimal close = curr.getClosingPrice();

            int searchStart = Math.max(PIVOT_WINDOW, i - LS_LOOKBACK_BARS);

            // Bearish inducement (false breakout above swing high)
            for (int j = searchStart; j < i; j++) {
                if (!swingHighIdx.contains(j)) continue;
                BigDecimal sh = prices.get(j).getHighPrice();
                BigDecimal limit = sh.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(INDUCEMENT_THRESHOLD_PCT)));
                if (high.compareTo(sh) > 0 && high.compareTo(limit) <= 0 && close.compareTo(sh) < 0) {
                    if (i + 2 < n) {
                        BigDecimal c1 = prices.get(i + 1).getClosingPrice();
                        BigDecimal c2 = prices.get(i + 2).getClosingPrice();
                        if (c1.compareTo(close) < 0 && c2.compareTo(c1) < 0) {
                            results.add(new SMCPatternDTO.InducementEntry(
                                    curr.getPriceDate(), close, "BEARISH", sh, "IND ↓"));
                            break;
                        }
                    }
                }
            }

            // Bullish inducement (false breakdown below swing low)
            for (int j = searchStart; j < i; j++) {
                if (!swingLowIdx.contains(j)) continue;
                BigDecimal sl = prices.get(j).getLowPrice();
                BigDecimal limit = sl.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(INDUCEMENT_THRESHOLD_PCT)));
                if (low.compareTo(sl) < 0 && low.compareTo(limit) >= 0 && close.compareTo(sl) > 0) {
                    if (i + 2 < n) {
                        BigDecimal c1 = prices.get(i + 1).getClosingPrice();
                        BigDecimal c2 = prices.get(i + 2).getClosingPrice();
                        if (c1.compareTo(close) > 0 && c2.compareTo(c1) > 0) {
                            results.add(new SMCPatternDTO.InducementEntry(
                                    curr.getPriceDate(), close, "BULLISH", sl, "IND ↑"));
                            break;
                        }
                    }
                }
            }
        }
        return results.isEmpty() ? null : results;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ATR
    // ═══════════════════════════════════════════════════════════════════════════

    private BigDecimal computeATR(List<DailyPrice> prices, int period) {
        if (prices.size() < period + 1) return null;
        try {
            return new ATRCalculator(period).calculate(prices);
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Label De-duplication
    // ═══════════════════════════════════════════════════════════════════════════

    /** Priority: swing BOS > swing CHoCH > internal BOS > internal CHoCH > OB > IOB > FVG > EQH > LS > IND */
    private void deduplicateLabels(SMCPatternDTO dto, BigDecimal atr) {
        if (atr == null) return;
        Set<String> occupied = new HashSet<>();
        BigDecimal bucketSize = atr.multiply(BigDecimal.valueOf(OVERLAP_ATR_FRACTION));

        occupy(dto.getBreakOfStructure(), occupied, bucketSize, e ->
                ((SMCPatternDTO.BOSEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.BOSEntry) e).getSwingLevel(), bucketSize));
        occupy(dto.getChangeOfCharacter(), occupied, bucketSize, e ->
                ((SMCPatternDTO.CHoCHEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.CHoCHEntry) e).getPrice(), bucketSize));
        occupy(dto.getInternalBreakOfStructure(), occupied, bucketSize, e ->
                ((SMCPatternDTO.BOSEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.BOSEntry) e).getSwingLevel(), bucketSize));
        occupy(dto.getInternalChangeOfCharacter(), occupied, bucketSize, e ->
                ((SMCPatternDTO.CHoCHEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.CHoCHEntry) e).getPrice(), bucketSize));
        occupy(dto.getOrderBlocks(), occupied, bucketSize, e ->
                ((SMCPatternDTO.OrderBlockEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.OrderBlockEntry) e).getPrice(), bucketSize));
        occupy(dto.getInternalOrderBlocks(), occupied, bucketSize, e ->
                ((SMCPatternDTO.OrderBlockEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.OrderBlockEntry) e).getPrice(), bucketSize));
        occupy(dto.getFairValueGaps(), occupied, bucketSize, e ->
                ((SMCPatternDTO.FVGEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.FVGEntry) e).getPrice(), bucketSize));
        occupy(dto.getEqualHighsLows(), occupied, bucketSize, e ->
                ((SMCPatternDTO.EqualHighLowEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.EqualHighLowEntry) e).getLevel(), bucketSize));
        occupy(dto.getLiquiditySweeps(), occupied, bucketSize, e ->
                ((SMCPatternDTO.LiquiditySweepEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.LiquiditySweepEntry) e).getSweptLevel(), bucketSize));
        occupy(dto.getInducements(), occupied, bucketSize, e ->
                ((SMCPatternDTO.InducementEntry) e).getDate() + "@" + priceBucket(((SMCPatternDTO.InducementEntry) e).getInducedLevel(), bucketSize));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void occupy(List list, Set<String> occupied, BigDecimal bucketSize,
                        java.util.function.Function<Object, String> keyFn) {
        if (list == null || list.isEmpty()) return;
        Iterator it = list.iterator();
        while (it.hasNext()) {
            String key = keyFn.apply(it.next());
            if (occupied.contains(key)) {
                it.remove();
            } else {
                occupied.add(key);
            }
        }
    }

    private String priceBucket(BigDecimal price, BigDecimal bucketSize) {
        if (price == null || bucketSize == null || bucketSize.compareTo(BigDecimal.ZERO) <= 0) return "";
        BigDecimal bucket = price.divide(bucketSize, 0, RoundingMode.DOWN)
                .multiply(bucketSize)
                .setScale(2, RoundingMode.HALF_UP);
        return "BUCKET_" + bucket;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private <T> List<T> cap(List<T> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .skip(Math.max(0, list.size() - MAX_PER_TYPE))
                .collect(Collectors.toList());
    }

    private int count(List<?> list) { return list != null ? list.size() : 0; }
}
