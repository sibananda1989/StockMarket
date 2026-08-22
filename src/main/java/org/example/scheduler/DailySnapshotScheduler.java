package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.PortfolioSnapshotService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailySnapshotScheduler {

    private final PortfolioSnapshotService snapshotService;

    @Scheduled(cron = "0 45 5 * * MON-FRI", zone = "UTC")
    public void ensureRecentSnapshots() {
        try {
            LocalDate from = LocalDate.now().minusDays(7);
            snapshotService.backfillSnapshots(from, false);
            log.info("Daily snapshot check complete (from {})", from);
        } catch (Exception e) {
            log.error("Daily snapshot check failed", e);
        }
    }
}
