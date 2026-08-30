package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.StrategyDailyWeightService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class StrategyDailyWeightScheduler {

    private final StrategyDailyWeightService weightService;

    /**
     * Computes daily strategy weights for all stocks every weekday at 10 AM.
     * Runs after market close to ensure latest price data is available.
     */
    @Scheduled(cron = "0 0 10 * * MON-FRI")
    public void computeDailyWeights() {
        log.info("Starting daily strategy weight computation...");
        LocalDate today = LocalDate.now();
        weightService.computeForAllStocks(today);
        log.info("Daily strategy weight computation completed");
    }
}
