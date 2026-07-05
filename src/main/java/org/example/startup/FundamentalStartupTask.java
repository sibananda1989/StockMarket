package org.example.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.FundamentalDataService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FundamentalStartupTask implements StartupTask {

    private final FundamentalDataService fundamentalDataService;

    @Override
    public String getId() {
        return "fundamental-data";
    }

    @Override
    public String getName() {
        return "Fundamental Data Refresh";
    }

    @Override
    public String getDescription() {
        return "Refresh fundamental data for all stocks";
    }

    @Override
    public String getCategory() {
        return "Data Sync";
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
        log.info("Starting fundamental data refresh for stale stocks...");
        try {
            int count = fundamentalDataService.fetchStaleStocksBatch(7);
            log.info("Fundamental data refresh complete. Updated {} stocks", count);
        } catch (Exception e) {
            log.error("Fundamental data refresh failed: {}", e.getMessage());
        }
    }
}
