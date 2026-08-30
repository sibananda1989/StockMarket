package org.example.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.SignalService;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignalAccuracyStartupTask implements StartupTask {

    private final SignalService signalService;
    private final CacheManager cacheManager;

    @Override
    public String getId() {
        return "signal-accuracy";
    }

    @Override
    public String getName() {
        return "Signal Accuracy Backfill";
    }

    @Override
    public String getDescription() {
        return "Create historical signal records (forward accuracy runs daily at 3 AM)";
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
            // Clear signal cache so new scoring logic takes effect
            clearSignalCache();

            log.info("Starting signal accuracy backfill...");

            int created = signalService.backfillSignalRecords();
            log.info("Created {} historical signal records", created);

            log.info("Signal accuracy backfill complete (forward accuracy will be marked by daily scheduler)");
        } catch (Exception e) {
            log.error("Signal accuracy backfill failed: {}", e.getMessage(), e);
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
