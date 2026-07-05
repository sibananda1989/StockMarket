package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.institutional.BlockDealService;
import org.example.service.institutional.BulkDealService;
import org.example.service.institutional.InstitutionalHoldingService;
import org.example.service.institutional.NseXbrlShareholdingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class InstitutionalHoldingScheduler {

    private final InstitutionalHoldingService holdingService;
    private final BulkDealService bulkDealService;
    private final BlockDealService blockDealService;
    private final NseXbrlShareholdingService xbrlService;

    /**
     * Weekly shareholding pattern sync — runs Sunday at 10:00 AM IST (4:30 AM UTC).
     * Fetches quarterly shareholding data for all stocks from NSE.
     */
    @Scheduled(cron = "0 30 4 * * SUN", zone = "UTC")
    public void weeklyShareholdingSync() {
        log.info("Starting weekly shareholding pattern sync...");
        int count = holdingService.fetchForAllStocks();
        log.info("Weekly shareholding sync complete. Updated {} stocks", count);
    }

    /**
     * Daily bulk deals fetch — runs weekdays at 6:00 PM IST (12:30 PM UTC).
     * After market close, fetches today's bulk deals.
     */
    @Scheduled(cron = "0 30 12 * * MON-FRI", zone = "UTC")
    public void dailyBulkDealsFetch() {
        log.info("Starting daily bulk deals fetch...");
        int count = bulkDealService.fetchTodayBulkDeals();
        log.info("Daily bulk deals fetch complete. Saved {} new deals", count);
    }

    /**
     * Daily block deals fetch — runs weekdays at 6:00 PM IST (12:30 PM UTC).
     * After market close, fetches today's block deals.
     */
    @Scheduled(cron = "0 35 12 * * MON-FRI", zone = "UTC")
    public void dailyBlockDealsFetch() {
        log.info("Starting daily block deals fetch...");
        int count = blockDealService.fetchTodayBlockDeals();
        log.info("Daily block deals fetch complete. Saved {} new deals", count);
    }

    /**
     * Weekly historical bulk/block deals backfill — runs Sunday at 11:00 AM IST (5:30 AM UTC).
     * Backfills the last 90 days of bulk and block deals.
     */
    @Scheduled(cron = "0 30 5 * * SUN", zone = "UTC")
    public void weeklyDealsBackfill() {
        log.info("Starting weekly bulk/block deals backfill...");

        LocalDate to = LocalDate.now();
        LocalDate from = LocalDate.now().minusDays(90);

        int bulkCount = bulkDealService.fetchBulkDeals(from, to);
        log.info("Backfilled {} bulk deals from last 90 days", bulkCount);

        int blockCount = blockDealService.fetchBlockDeals(from, to);
        log.info("Backfilled {} block deals from last 90 days", blockCount);
    }

    /**
     * Weekly XBRL shareholding detail fetch — runs Sunday at 11:30 AM IST (6:00 AM UTC).
     * Downloads and parses NSE XBRL filings to get detailed FII/DII/MF data.
     */
    @Scheduled(cron = "0 0 6 * * SUN", zone = "UTC")
    public void weeklyXbrlFetch() {
        log.info("Starting weekly XBRL detailed shareholding fetch...");
        int count = xbrlService.fetchForAllStocks();
        log.info("Weekly XBRL fetch complete. Updated {} stocks with detailed data", count);
    }
}
