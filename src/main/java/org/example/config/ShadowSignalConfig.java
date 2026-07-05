package org.example.config;

import org.example.service.ShadowSignalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class ShadowSignalConfig {

    @Autowired
    private ShadowSignalService shadowSignalService;

    /**
     * Daily scheduled job to compute shadow signals for all stocks
     */
    @Scheduled(cron = "${shadow.signal.cron:0 0 9 * * ?}")
    public void computeAllShadowSignals() {
        if (shadowSignalService != null) {
            shadowSignalService.computeAndStoreAllShadowSignals();
        }
    }
}