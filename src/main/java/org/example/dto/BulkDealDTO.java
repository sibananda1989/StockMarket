package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDealDTO {
    private Long id;
    private Long stockId;
    private String symbol;
    private String name;
    private LocalDate dealDate;
    private String clientName;
    private String buySell;
    private Long quantity;
    private BigDecimal tradePrice;
    private BigDecimal dealValue;
    private String clientCategory;
    private Boolean isInstitutional;
    private String remarks;
}
