package org.example.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignalAccuracyStartupTask implements StartupTask {

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
        return "Clear signal cache (historical backfill disabled on startup; forward accuracy runs daily at 3 AM)";
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
            log.info("Signal accuracy startup task: cache cleared (historical backfill disabled on startup - forward accuracy runs daily at 3 AM)");
        } catch (Exception e) {
            log.error("Signal accuracy startup task failed: {}", e.getMessage(), e);
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
