package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.startup.StartupTask;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Clears the signal cache so the latest scoring logic takes effect immediately.
 * Historical signal backfill is NOT run on startup (removed to avoid heavy
 * O(stocks × days) computation and slow startup); run it manually if needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SignalStartupTask implements StartupTask {

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
        return "Clear signal cache (backfill removed from startup)";
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
            clearSignalCache();
            log.info("Signal startup task complete: cache cleared (backfill disabled on startup)");
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
