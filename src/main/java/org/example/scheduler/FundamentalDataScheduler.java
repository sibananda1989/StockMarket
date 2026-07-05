package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.FundamentalDataService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FundamentalDataScheduler {

    private final FundamentalDataService fundamentalDataService;

    @Scheduled(cron = "0 0 8 * * SUN", zone = "UTC")
    public void weeklyFundamentalSync() {
        log.info("Starting weekly fundamental data sync...");
        int count = fundamentalDataService.fetchForAllStocks();
        log.info("Weekly fundamental data sync complete. Updated {} stocks", count);
    }
}
