package org.example.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

/**
 * Clears the signals cache on every application startup.
 * Ensures fresh signals are computed from the latest scoring logic
 * starting with the very first request — no user interaction required.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SignalCacheInitializer {

    private final CacheManager cacheManager;

    @EventListener(ApplicationReadyEvent.class)
    public void clearSignalCacheOnStartup() {
        try {
            var cache = cacheManager.getCache("signals");
            if (cache != null) {
                cache.clear();
                log.info("Signal cache cleared on application startup");
            } else {
                log.debug("No 'signals' cache found — nothing to clear");
            }
        } catch (Exception e) {
            log.warn("Failed to clear signal cache on startup: {}", e.getMessage());
        }
    }
}
