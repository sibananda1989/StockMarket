package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.SignalService;
import org.example.startup.StartupTask;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Clears the signal cache so the latest scoring logic takes effect immediately,
 * then backfills signal_records with consistent recommendation/compositeScore values.
 *
 * This is critical after code changes that modify scoring thresholds or
 * recommendation logic — without this, stale cached DTOs and persisted
 * records would continue using the old logic until the next DhanSync.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SignalStartupTask implements StartupTask {

    private final SignalService signalService;
    private final CacheManager cacheManager;

    @Override
    public String getId() {
        return "signal-cache";
    }

    @Override
    public String getName() {
        return "Signal Cache Clear";
    }

    @Override
    public String getDescription() {
        return "Clear signal cache and backfill records";
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
        try {
            // 1. Clear signal cache so new scoring logic takes effect
            clearSignalCache();

            // 2. Backfill signal_records with consistent recommendation/compositeScore
            int backfilled = signalService.backfillSignalRecords();
            log.info("Signal startup task complete: cache cleared, {} signal records backfilled", backfilled);
        } catch (Exception e) {
            log.error("Signal startup task failed: {}", e.getMessage(), e);
        }
    }

    private void clearSignalCache() {
        try {
            var cache = cacheManager.getCache("signals");
            if (cache != null) {
                cache.clear();
                log.info("Signal cache cleared on startup");
            }
        } catch (Exception e) {
            log.warn("Could not clear signal cache: {}", e.getMessage());
        }
    }
}
