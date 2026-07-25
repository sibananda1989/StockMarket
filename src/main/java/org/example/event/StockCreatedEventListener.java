package org.example.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.TechnicalAnalysisService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockCreatedEventListener {

    private final TechnicalAnalysisService technicalAnalysisService;

    @Async
    @EventListener
    public void onStockCreated(StockCreatedEvent event) {
        Long stockId = event.getStockId();
        log.info("Async backfill triggered for newly created stock ID: {}", stockId);
        try {
            int filled = technicalAnalysisService.fillIndicatorGapsForStock(stockId, 365);
            log.info("Async backfill complete for stock {}: {} records filled", stockId, filled);
        } catch (Exception e) {
            log.error("Async backfill failed for stock {}: {}", stockId, e.getMessage(), e);
        }
    }
}
