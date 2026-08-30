package org.example.service;

import org.example.dto.EmaCrossResponseDTO;
import org.example.dto.EmaCrossSignalDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StockRepository;
import org.example.service.calculator.EmaSeriesCalculator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Standalone screener for the strategy "EMA-20 crossed above EMA-50".
 *
 * <p>The strategy (port of the HeRAI "20 EMA ↑ 50 EMA" screen) is an
 * <em>event</em> detector: EMA-20 crosses above EMA-50 within the last N
 * trading days. Everything is computed on-the-fly from the raw {@code daily_price}
 * table via one batched query ({@link DailyPriceRepository#findPricesForStockIdsSince})
 * — no EMA-50 is persisted and no per-stock N+1 fetch is performed.</p>
 *
 * <ul>
 *   <li>Anchor date = the latest trading date with (near-)complete price coverage,
 *       so a partially-synced "today" never drops the whole universe.</li>
 *   <li>Split artifacts: a single-day |Δclose|/prev-close jump &gt; {@value SPLIT_JUMP_PERCENT}%
 *       on the cross day is rejected (unadjusted stock splits create false crosses).</li>
 * </ul>
 */
@Service
public class EmaCrossScreenerService {

    /** Only a jump larger than this is treated as a split artifact. */
    public static final double SPLIT_JUMP_PERCENT = 30.0;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final double COMPLETE_COVERAGE_RATIO = 0.90;
    private static final int EMA_20_PERIOD = 20;
    private static final int EMA_50_PERIOD = 50;
    private static final int DEFAULT_LOOK_BACK_CALENDAR_DAYS = 180;

    private final DailyPriceRepository dailyPriceRepository;
    private final StockRepository stockRepository;

    public EmaCrossScreenerService(DailyPriceRepository dailyPriceRepository,
                                   StockRepository stockRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.stockRepository = stockRepository;
    }

    @Cacheable(value = "emaCross", key = "#days", unless = "#result == null")
    @Transactional(readOnly = true)
    public EmaCrossResponseDTO findEmaCrosses(int days) {
        List<Stock> stocks = stockRepository.findAll();
        List<Long> allStockIds = stocks.stream().map(Stock::getId).collect(Collectors.toList());
        if (allStockIds.isEmpty()) {
            return EmaCrossResponseDTO.builder()
                    .signals(List.of())
                    .anchorDate(LocalDate.now())
                    .universeScanned(0)
                    .warnings(List.of("No stocks in the universe"))
                    .build();
        }

        // Determine the anchor date = latest date with >= 90% of stocks having a price.
        Map<Long, String> symbolById = stocks.stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getSymbol));
        Map<Long, String> nameById = stocks.stream()
                .collect(Collectors.toMap(Stock::getId, Stock::getName));

        List<LocalDate> tradingDates = dailyPriceRepository.findDistinctTradingDatesAfter(
                LocalDate.now().minusDays(45));
        LocalDate anchorDate = findAnchorDate(tradingDates, allStockIds.size());

        // Batch fetch only the last ~180 calendar days of prices for the EMA-50 warm-up.
        LocalDate from = anchorDate.minusDays(DEFAULT_LOOK_BACK_CALENDAR_DAYS);
        List<DailyPrice> allPrices = dailyPriceRepository.findPricesForStockIdsSince(allStockIds, from);

        // Group by stock, keeping chronological (the query returns DESC, so reverse).
        Map<Long, List<DailyPrice>> pricesByStock = new LinkedHashMap<>();
        for (DailyPrice dp : allPrices) {
            pricesByStock.computeIfAbsent(dp.getStock().getId(), k -> new ArrayList<>()).add(dp);
        }
        for (List<DailyPrice> list : pricesByStock.values()) {
            list.sort(java.util.Comparator.comparing(DailyPrice::getPriceDate));
        }

        // Trading dates inside the window, ascending, limited to the trailing `days`.
        List<LocalDate> windowDates = tradingDates.stream()
                .filter(d -> !d.isAfter(anchorDate))
                .collect(Collectors.toList());
        if (windowDates.size() > days) {
            windowDates = windowDates.subList(windowDates.size() - days, windowDates.size());
        }

        List<EmaCrossSignalDTO> signals = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int universeScanned = 0;

        for (Map.Entry<Long, List<DailyPrice>> entry : pricesByStock.entrySet()) {
            Long stockId = entry.getKey();
            List<DailyPrice> prices = entry.getValue();
            if (prices.size() < EMA_50_PERIOD) {
                warnings.add(symbolById.getOrDefault(stockId, "" + stockId)
                        + ": insufficient history for EMA-50 (<50 trading days)");
                continue;
            }
            universeScanned++;

            List<BigDecimal> ema20;
            List<BigDecimal> ema50;
            try {
                ema20 = new EmaSeriesCalculator(EMA_20_PERIOD).calculate(prices);
                ema50 = new EmaSeriesCalculator(EMA_50_PERIOD).calculate(prices);
            } catch (Exception e) {
                // Fail-safe: a malformed price row (e.g. null close) must not kill the
                // whole-universe scan — skip this stock with a warning (aggregator convention).
                warnings.add(symbolById.getOrDefault(stockId, "" + stockId)
                        + ": skipped (" + e.getMessage() + ")");
                continue;
            }

            for (int i = 0; i < windowDates.size(); i++) {
                LocalDate date = windowDates.get(i);
                // Locate the bar index for this date; skip if the stock was not active that day.
                int idx = binarySearchDate(prices, date);
                if (idx < 0) continue;
                if (idx == 0) continue;
                int prevIdx = idx - 1;
                // Previous bar must also be within window (a gap between trading days).
                BigDecimal prev20 = ema20.get(prevIdx);
                BigDecimal prev50 = ema50.get(prevIdx);
                BigDecimal cur20 = ema20.get(idx);
                BigDecimal cur50 = ema50.get(idx);
                if (prev20.compareTo(prev50) <= 0 && cur20.compareTo(cur50) > 0) {
                    // Split-artifact guard: a single-day jump in close on the cross day
                    // suggests an unadjusted split, which would make the cross spurious.
                    BigDecimal prevClose = prices.get(prevIdx).getClosingPrice();
                    BigDecimal curClose = prices.get(idx).getClosingPrice();
                    if (isSplitJump(prevClose, curClose)) {
                        warnings.add(symbolById.getOrDefault(stockId, "" + stockId)
                                + ": cross on " + date + " rejected (likely split jump)");
                        continue;
                    }
                    signals.add(EmaCrossSignalDTO.builder()
                            .stockId(stockId)
                            .symbol(symbolById.getOrDefault(stockId, ""))
                            .companyName(nameById.getOrDefault(stockId, ""))
                            .crossDate(date)
                            .daysAgo(windowDates.size() - 1 - i)
                            .ema20AtCross(cur20)
                            .ema50AtCross(cur50)
                            .lastClose(curClose)
                            .build());
                }
            }
        }

        // Dedup: a stock may cross up, down and up again inside the window — keep only
        // the LATEST up-cross per stock (one row per stock, like the reference screener).
        Map<Long, EmaCrossSignalDTO> latestByStock = new LinkedHashMap<>();
        for (EmaCrossSignalDTO s : signals) {
            latestByStock.merge(s.getStockId(), s,
                    (a, b) -> a.getCrossDate().isAfter(b.getCrossDate()) ? a : b);
        }
        List<EmaCrossSignalDTO> deduped = new ArrayList<>(latestByStock.values());
        deduped.sort(java.util.Comparator
                .comparing(EmaCrossSignalDTO::getCrossDate).reversed()
                .thenComparing(EmaCrossSignalDTO::getSymbol, String.CASE_INSENSITIVE_ORDER));

        return EmaCrossResponseDTO.builder()
                .signals(deduped)
                .anchorDate(anchorDate)
                .universeScanned(universeScanned)
                .warnings(warnings)
                .build();
    }

    private LocalDate findAnchorDate(List<LocalDate> tradingDates, int totalStocks) {
        if (tradingDates == null || tradingDates.isEmpty()) {
            return LocalDate.now();
        }
        // Iterate from the latest date backwards, stop at the first with >=90% coverage.
        for (int i = tradingDates.size() - 1; i >= 0; i--) {
            LocalDate d = tradingDates.get(i);
            long count = dailyPriceRepository.countByPriceDate(d);
            if (count >= totalStocks * COMPLETE_COVERAGE_RATIO) {
                return d;
            }
        }
        return tradingDates.get(tradingDates.size() - 1);
    }

    private int binarySearchDate(List<DailyPrice> prices, LocalDate date) {
        int lo = 0, hi = prices.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = prices.get(mid).getPriceDate().compareTo(date);
            if (cmp < 0) lo = mid + 1;
            else if (cmp > 0) hi = mid - 1;
            else return mid;
        }
        return -1;
    }

    private boolean isSplitJump(BigDecimal prevClose, BigDecimal curClose) {
        if (prevClose == null || curClose == null || prevClose.signum() == 0) {
            return false;
        }
        BigDecimal diff = curClose.subtract(prevClose).abs();
        BigDecimal pct = diff.multiply(HUNDRED).divide(prevClose, 2, RoundingMode.HALF_UP);
        return pct.doubleValue() > SPLIT_JUMP_PERCENT;
    }
}