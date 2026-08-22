package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.PortfolioTransactionService;
import org.example.startup.StartupTask;
import org.springframework.stereotype.Component;

/**
 * One-time data migration as a startup task: links legacy SELL rows
 * (created before lot-based selling existed) to their BUY records via
 * FIFO allocation, so every sell maps to exactly one buy lot.
 * Idempotent: already-linked sells are skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SellLotBackfillStartupTask implements StartupTask {

    private final PortfolioTransactionService transactionService;

    @Override
    public String getId() {
        return "sell-lot-backfill";
    }

    @Override
    public String getName() {
        return "Sell Lot Backfill";
    }

    @Override
    public String getDescription() {
        return "FIFO-link legacy unmapped SELL rows to BUY lots (one-time migration)";
    }

    @Override
    public String getCategory() {
        return "Portfolio";
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
            int mapped = transactionService.backfillSellLotLinks();
            log.info("Sell-lot backfill complete: {} legacy sells linked to buy lots", mapped);
        } catch (Exception e) {
            log.error("Sell-lot backfill failed: {}", e.getMessage(), e);
        }
    }
}
