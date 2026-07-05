package org.example.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Custom Micrometer metrics for the Institutional Activity Engine.
 * Tracks score computations, NSE API call volumes, and cache hit rates.
 * Exposed via /actuator/prometheus when micrometer-registry-prometheus is on the classpath.
 */
@Component
public class InstitutionalMetrics {

    private final Counter scoreComputations;
    private final Timer scoreComputationTime;
    private final Counter nseApiCalls;
    private final Counter nseApiErrors;
    private final Counter cacheEvictions;

    public InstitutionalMetrics(MeterRegistry registry) {
        this.scoreComputations = registry.counter("institutional.score.computations",
                "description", "Number of institutional score computations performed");

        this.scoreComputationTime = registry.timer("institutional.score.computation.time",
                "description", "Time taken to compute an institutional score");

        this.nseApiCalls = registry.counter("institutional.nse.api.calls",
                "description", "Number of NSE API calls made by institutional services");

        this.nseApiErrors = registry.counter("institutional.nse.api.errors",
                "description", "Number of failed NSE API calls from institutional services");

        this.cacheEvictions = registry.counter("institutional.cache.evictions",
                "description", "Number of institutional score cache evictions");
    }

    /**
     * Record one score computation.
     */
    public void recordScoreComputation() {
        scoreComputations.increment();
    }

    /**
     * Record the duration of a score computation.
     */
    public void recordScoreComputationTime(long millis) {
        scoreComputationTime.record(Duration.ofMillis(millis));
    }

    /**
     * Record an NSE API call.
     */
    public void recordNseApiCall() {
        nseApiCalls.increment();
    }

    /**
     * Record a failed NSE API call.
     */
    public void recordNseApiError() {
        nseApiErrors.increment();
    }

    /**
     * Record a cache eviction (triggered by data refresh).
     */
    public void recordCacheEviction() {
        cacheEvictions.increment();
    }
}
