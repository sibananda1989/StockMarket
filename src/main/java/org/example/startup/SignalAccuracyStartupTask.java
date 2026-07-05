package org.example.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.SignalService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignalAccuracyStartupTask implements StartupTask {

    private final SignalService signalService;

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
            log.info("Starting signal accuracy backfill...");

            int created = signalService.backfillSignalRecords();
            log.info("Created {} historical signal records", created);

            log.info("Signal accuracy backfill complete (forward accuracy will be marked by daily scheduler)");
        } catch (Exception e) {
            log.error("Signal accuracy backfill failed: {}", e.getMessage(), e);
        }
    }
}
