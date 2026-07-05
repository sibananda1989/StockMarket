package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter for external API calls.
 * Ensures minimum interval between successive calls to the same API key.
 */
@Slf4j
@Component
public class ApiRateLimiter {

    private final Map<String, Long> lastCallTimes = new ConcurrentHashMap<>();

    /**
     * Blocks the calling thread until the minimum interval for the given key has elapsed.
     *
     * @param key            Unique key for the API being rate-limited (e.g. "yahoo", "dhan", "nse")
     * @param minIntervalMs  Minimum time in milliseconds between successive calls
     */
    public void acquire(String key, long minIntervalMs) {
        long now = System.currentTimeMillis();
        Long lastCall = lastCallTimes.get(key);
        if (lastCall != null) {
            long elapsed = now - lastCall;
            if (elapsed < minIntervalMs) {
                long sleepMs = minIntervalMs - elapsed;
                log.debug("Rate limiter: waiting {}ms for {}", sleepMs, key);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        lastCallTimes.put(key, System.currentTimeMillis());
    }
}