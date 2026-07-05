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
public class InstitutionalHoldingDTO {
    private Long id;
    private Long stockId;
    private String symbol;
    private String name;
    private LocalDate quarterEndDate;

    private BigDecimal promoterHoldingPct;
    private BigDecimal fiiHoldingPct;
    private BigDecimal diiHoldingPct;
    private BigDecimal mutualFundHoldingPct;
    private BigDecimal insuranceHoldingPct;
    private BigDecimal publicHoldingPct;

    private BigDecimal promoterChangeQoq;
    private BigDecimal fiiChangeQoq;
    private BigDecimal diiChangeQoq;
    private BigDecimal mutualFundChangeQoq;

    private String dataSource;
    private LocalDate filingDate;

    // Previous quarter comparison
    private BigDecimal fiiHoldingPctPrev;
    private BigDecimal diiHoldingPctPrev;
    private BigDecimal mutualFundHoldingPctPrev;
}
