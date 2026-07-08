package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.StockSyncService;
import org.example.service.SupportResistanceService;
import org.example.service.TechnicalAnalysisService;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketAnalysisScheduler {

    private final StockSyncService stockSyncService;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final SupportResistanceService supportResistanceService;

    /**
     * Weekly backfill: Sunday 7 AM UTC — fetches Yahoo Finance history for stocks with < 365 days of data.
     */
    @Scheduled(cron = "0 0 7 * * SUN", zone = "UTC")
    public void weeklyBackfill() {
        log.info("Starting weekly Yahoo Finance backfill for stocks with insufficient data");
        int saved = stockSyncService.backfillAllStocks();
        log.info("Weekly backfill complete. {} records saved", saved);
    }

    /**
     * Fallback: recalculate all technical indicators for all stocks at 9 AM (manual trigger safety net).
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Retryable(
        value = { RuntimeException.class, IllegalArgumentException.class, IllegalStateException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    public void calculateDailyIndicators() {
        log.info("Starting technical indicator recalculation");
        technicalAnalysisService.calculateForAllStocks();
        log.info("Technical indicator recalculation completed");
    }

    @Recover
    public void recover(Exception e) {
        log.error("Max retries exceeded for scheduler task. Last exception: {}", e.getMessage(), e);
    }
}
