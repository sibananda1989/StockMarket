package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalScreenRequest {
    private BigDecimal peMin;
    private BigDecimal peMax;
    private BigDecimal marketCapMin;
    private BigDecimal marketCapMax;
    private BigDecimal roeMin;
    private BigDecimal debtToEquityMax;
    private BigDecimal dividendYieldMin;
    private String sector;
    private String sortBy;
    private String sortOrder;
    private int limit = 100;
    private int offset = 0;
}
