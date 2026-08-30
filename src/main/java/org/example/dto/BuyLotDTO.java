package org.example.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A BUY transaction treated as an individual lot that can be partially or
 * fully sold. Each SELL linked to this lot appears in {@code sells}.
 */
@Data
@Builder
public class BuyLotDTO {
    private Long id;
    private Long portfolioId;
    private Long stockId;
    private String stockSymbol;
    private String stockName;
    private LocalDate transactionDate;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal fees;
    private String notes;

    /** Total quantity already sold from this lot (sum of linked SELLs). */
    private Integer soldQuantity;
    /** quantity - soldQuantity; 0 means the lot is fully SOLD. */
    private Integer remainingQuantity;
    /** OPEN, PARTIALLY_SOLD or SOLD. */
    private String status;

    /** Sum of realized P/L across all linked sells for this lot. */
    private BigDecimal realizedPnl;

    private LocalDateTime createdAt;
    private List<TransactionDTO> sells;
}
