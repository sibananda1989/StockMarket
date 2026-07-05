package org.example.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.FiiDiiService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FiiDiiStartupTask implements StartupTask {

    private final FiiDiiService fiidiiService;

    @Override
    public String getId() {
        return "fiidii-data";
    }

    @Override
    public String getName() {
        return "FII/DII Data Fetch";
    }

    @Override
    public String getDescription() {
        return "Fetch latest institutional data from NSE";
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
        log.info("Startup FII/DII data fetch from NSE...");
        try {
            var data = fiidiiService.fetchAndSave();
            if (data != null) {
                log.info("Startup FII/DII fetch complete for date: {}", data.getDate());
            } else {
                log.warn("Startup FII/DII fetch returned no data");
            }
        } catch (Exception e) {
            log.error("Startup FII/DII fetch failed: {}", e.getMessage());
        }
    }
}
