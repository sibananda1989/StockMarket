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
public class FundamentalDataDTO {
    private Long id;
    private Long stockId;
    private String symbol;
    private String name;

    private BigDecimal peRatio;
    private BigDecimal forwardPe;
    private BigDecimal epsTtm;
    private BigDecimal epsForward;
    private BigDecimal bookValue;
    private BigDecimal priceToBook;
    private BigDecimal dividendYield;
    private BigDecimal roe;
    private BigDecimal debtToEquity;
    private BigDecimal profitMargin;
    private Long marketCap;
    private Long revenueTtm;
    private Long sharesOutstanding;
    private String sector;
    private String industry;
    private String businessSummary;
    private BigDecimal beta;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal fiftyTwoWeekLow;
    private LocalDate fetchedDate;
}
