// GATE-TEST-MARKER
package org.example;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // Configure for latestIndicators cache
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(500));
        // Configure for indicatorHistory cache
        manager.setCacheNames(java.util.Arrays.asList("latestIndicators", "indicatorHistory", "stockHistory", "signals", "supportResistanceLevels", "institutionalScores", "smcPatterns"));
        return manager;
    }
}