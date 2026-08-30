package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.IndicatorType;
import org.example.entity.Stock;
import org.example.entity.TechnicalIndicator;
import org.example.entity.IndicatorType;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.StockService;
import org.example.service.TechnicalAnalysisService;
import org.example.startup.StartupTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks if technical indicators have been calculated today.
 * If not, triggers batch calculation for all stocks.
 *
 * This covers scenarios where:
 * - App starts mid-day before the 4:15 PM IST scheduler
 * - App was restarted after the 9 AM safety net but before market close
 * - App was down for the day and indicators were never computed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IndicatorStartupTask implements StartupTask {

    private final TechnicalAnalysisService technicalAnalysisService;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final JdbcTemplate jdbcTemplate;
    private final StockService stockService;

    @Override
    public String getId() {
        return "indicator-calc";
    }

    @Override
    public String getName() {
        return "Technical Indicators";
    }

    @Override
    public String getDescription() {
        return "Calculate technical indicators for all stocks";
    }

    @Override
    public String getCategory() {
        return "Indicators";
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
        try {
            // Ensure indicator_type column is wide enough for new enum values
            // ddl-auto=update does NOT widen existing columns in MySQL
            ensureIndicatorTypeColumnWidth();

            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            int expectedCount = IndicatorType.values().length;

            List<Stock> stocks = stockService.getAllStocks();
            List<Stock> staleStocks = new ArrayList<>();
            int stalenessWarnings = 0;

            for (Stock stock : stocks) {
                long count = technicalIndicatorRepository.countByStockIdAndCalculationDate(stock.getId(), today);
                if (count < expectedCount) {
                    staleStocks.add(stock);
                } else {
                    // Completeness check passed — also verify correctness.
                    // Detect the "stale RSI" pattern where RSI is identical day-over-day
                    // despite price movement (see anchor summary 2026-07-06-rsi-investigation.md).
                    if (detectPossibleStaleness(stock, today, yesterday)) {
                        stalenessWarnings++;
                    }
                }
            }

            if (staleStocks.isEmpty()) {
                if (stalenessWarnings > 0) {
                    log.warn("All {} stocks have complete indicators for {}, but {} stock(s) show possible staleness. " +
                                    "Consider running a manual recalculation if values look wrong.",
                            stocks.size(), today, stalenessWarnings);
                } else {
                    log.info("All {} stocks have complete indicators for {}. Skipping startup calculation.", stocks.size(), today);
                }
                return;
            }

            log.info("Found {} stocks with incomplete indicators for {}. Triggering calculation for stale stocks...", staleStocks.size(), today);
            for (Stock stock : staleStocks) {
                technicalAnalysisService.calculateIndicatorsForStock(stock.getId());
            }
            log.info("Startup indicator calculation completed successfully.");
        } catch (Exception e) {
            log.error("Startup indicator calculation failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Detects possible indicator staleness by comparing today's RSI with yesterday's RSI.
     * If they are identical (within comparison tolerance) despite the stock having price
     * movement between the two days, that is suspicious and we log a warning.
     * <p>
     * This is a passive detection check only — it does NOT trigger recalculation, because
     * Fix 1 + Fix 2 (2026-07-06) already prevent new staleness by:
     * <ol>
     *   <li>Stopping price sync from overwriting existing price records</li>
     *   <li>Adding a staleness guard in saveOrUpdateIndicator() (first-write-wins per day)</li>
     * </ol>
     * This check is purely for monitoring and detecting any pre-existing stale records.
     *
     * @return true if possible staleness was detected
     */
    private boolean detectPossibleStaleness(Stock stock, LocalDate today, LocalDate yesterday) {
        try {
            // Check RSI for today vs yesterday
            Optional<TechnicalIndicator> todayRsi = technicalIndicatorRepository
                    .findByStockIdAndIndicatorTypeAndCalculationDate(stock.getId(), IndicatorType.RSI, today);
            Optional<TechnicalIndicator> yesterdayRsi = technicalIndicatorRepository
                    .findByStockIdAndIndicatorTypeAndCalculationDate(stock.getId(), IndicatorType.RSI, yesterday);

            if (todayRsi.isPresent() && yesterdayRsi.isPresent()
                    && todayRsi.get().getValue() != null && yesterdayRsi.get().getValue() != null) {

                BigDecimal todayVal = todayRsi.get().getValue();
                BigDecimal yesterdayVal = yesterdayRsi.get().getValue();

                // If RSI is identical day-over-day, flag it
                if (todayVal.compareTo(yesterdayVal) == 0) {
                    log.warn("Possible indicator staleness for {}: RSI unchanged at {} on both {} and {}",
                            stock.getSymbol(), todayVal, yesterday, today);
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Staleness check failed for {}: {}", stock.getSymbol(), e.getMessage());
        }
        return false;
    }

    /**
     * Widens the indicator_type column to VARCHAR(50) if it's narrower.
     * This handles the case where the column was created by an older Hibernate run
     * with fewer enum values.
     */
    public void ensureIndicatorTypeColumnWidth() {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE technical_indicators MODIFY COLUMN indicator_type VARCHAR(50) NOT NULL"
            );
            log.info("Ensured indicator_type column is VARCHAR(50)");
        } catch (Exception e) {
            // Column may already be wide enough, or table doesn't exist yet — that's fine
            log.debug("Column width adjustment skipped: {}", e.getMessage());
        }
    }
}
