package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.SignalHistoricalPerformance;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.SignalHistoricalPerformanceRepository;
import org.example.repository.StockRepository;
import org.example.service.SignalService;
import org.example.startup.StartupTask;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignalPerformanceScheduler implements StartupTask {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final SignalService signalService;
    private final SignalHistoricalPerformanceRepository performanceRepository;

    private static final List<Integer> FORWARD_DAYS = List.of(5, 10, 20);
    private static final List<String> RECOMMENDATIONS = List.of("STRONG BUY", "BUY", "HOLD", "SELL", "STRONG SELL");
    private static final int MIN_PRICES = 60;
    private static final int SAMPLING_STEP = 5;

    @Override
    public String getId() {
        return "signal-performance";
    }

    @Override
    public String getName() {
        return "Signal Performance";
    }

    @Override
    public String getDescription() {
        return "Compute historical forward returns for signals";
    }

    @Override
    public String getCategory() {
        return "Signals";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public void execute() {
        computeHistoricalReturns();
    }

    /**
     * Precomputes historical forward returns for each signal recommendation type.
     * For each stock, walks through historical price data, generates signals,
     * and measures forward returns after 5, 10, and 20 trading days.
     * Averages results by recommendation type.
     */
    public void computeHistoricalReturns() {
        log.info("Starting daily signal performance precomputation");

        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            log.warn("No stocks found. Skipping performance precomputation.");
            return;
        }

        // Accumulate raw returns by (recommendation, daysForward)
        Map<String, Map<Integer, List<BigDecimal>>> rawReturns = new HashMap<>();
        for (String rec : RECOMMENDATIONS) {
            rawReturns.put(rec, new HashMap<>());
            for (int days : FORWARD_DAYS) {
                rawReturns.get(rec).put(days, new ArrayList<>());
            }
        }

        int stocksProcessed = 0;
        for (Stock stock : stocks) {
            try {
                List<DailyPrice> prices = dailyPriceRepository
                        .findAllByStockIdOrderByPriceDateAsc(stock.getId());
                if (prices.size() < MIN_PRICES) {
                    log.debug("Insufficient price data for {} ({}), skipping", stock.getSymbol(), prices.size());
                    continue;
                }

                processStock(stock, prices, rawReturns);
                stocksProcessed++;
            } catch (Exception e) {
                log.warn("Error processing stock {} for performance: {}", stock.getSymbol(), e.getMessage());
            }
        }

        log.info("Processed {} stocks for performance precomputation", stocksProcessed);

        // Aggregate and persist
        LocalDate today = LocalDate.now();
        int totalRecords = 0;
        for (String recommendation : RECOMMENDATIONS) {
            for (int daysForward : FORWARD_DAYS) {
                List<BigDecimal> returns = rawReturns.get(recommendation).get(daysForward);
                if (returns == null || returns.isEmpty()) {
                    log.debug("No data for {} / {}d forward, skipping", recommendation, daysForward);
                    continue;
                }

                BigDecimal avgReturn = returns.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(returns.size()), 4, RoundingMode.HALF_UP);

                // Upsert
                Optional<SignalHistoricalPerformance> existing = performanceRepository
                        .findByRecommendationAndDaysForward(recommendation, daysForward);
                SignalHistoricalPerformance record;
                if (existing.isPresent()) {
                    record = existing.get();
                    record.setAvgReturn(avgReturn);
                    record.setSampleSize(returns.size());
                    record.setLastUpdated(today);
                } else {
                    record = new SignalHistoricalPerformance();
                    record.setRecommendation(recommendation);
                    record.setDaysForward(daysForward);
                    record.setAvgReturn(avgReturn);
                    record.setSampleSize(returns.size());
                    record.setLastUpdated(today);
                }
                performanceRepository.save(record);
                totalRecords++;
            }
        }

        log.info("Signal performance precomputation complete. {} records saved/updated.", totalRecords);
    }

    /**
     * Processes a single stock: walks through historical data, computes signals
     * at sampled intervals, measures forward returns, and accumulates results.
     */
    private void processStock(Stock stock, List<DailyPrice> prices,
                              Map<String, Map<Integer, List<BigDecimal>>> rawReturns) {
        int maxStartIndex = prices.size() - 20;

        for (int i = 20; i < maxStartIndex; i += SAMPLING_STEP) {
            List<DailyPrice> historicalSlice = prices.subList(0, i + 1);
            try {
                String recommendation = signalService.computeRecommendationForBacktest(stock, historicalSlice);
                if (recommendation == null) continue;

                BigDecimal priceAtSignal = prices.get(i).getClosingPrice();
                if (priceAtSignal == null || priceAtSignal.compareTo(BigDecimal.ZERO) == 0) continue;

                Map<Integer, List<BigDecimal>> recReturns = rawReturns.get(recommendation);
                if (recReturns == null) continue;

                for (int daysForward : FORWARD_DAYS) {
                    int forwardIndex = i + daysForward;
                    if (forwardIndex >= prices.size()) continue;

                    BigDecimal forwardPrice = prices.get(forwardIndex).getClosingPrice();
                    if (forwardPrice == null || forwardPrice.compareTo(BigDecimal.ZERO) == 0) continue;

                    BigDecimal returnPct = forwardPrice.subtract(priceAtSignal)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(priceAtSignal, 4, RoundingMode.HALF_UP);

                    recReturns.get(daysForward).add(returnPct);
                }
            } catch (Exception e) {
                log.trace("Could not compute signal at index {} for {}: {}", i, stock.getSymbol(), e.getMessage());
            }
        }
    }
}
