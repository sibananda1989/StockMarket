package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeRecordDTO {
    private LocalDate entryDate;
    private LocalDate exitDate;
    private String action;
    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private BigDecimal quantity;
    private BigDecimal pnl;
    private String exitReason;
    private boolean stopLossHit;
    private LocalDate stopLossDate;
}