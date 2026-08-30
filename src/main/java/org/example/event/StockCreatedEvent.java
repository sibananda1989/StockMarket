package org.example.event;

import org.springframework.context.ApplicationEvent;

public class StockCreatedEvent extends ApplicationEvent {
    private final Long stockId;

    public StockCreatedEvent(Object source, Long stockId) {
        super(source);
        this.stockId = stockId;
    }

    public Long getStockId() {
        return stockId;
    }
}
