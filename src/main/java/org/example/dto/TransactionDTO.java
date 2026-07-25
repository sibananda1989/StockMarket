package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDTO {

    private Long id;
    private Long portfolioId;
    private Long stockId;
    private String stockSymbol;
    private String stockName;
    private TransactionType type;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal fees;
    private BigDecimal realizedPnl;
    private LocalDate transactionDate;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
