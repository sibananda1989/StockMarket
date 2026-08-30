package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single EMA-20 crossed-above-EMA-50 event detected by the screener.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmaCrossSignalDTO {

    private Long stockId;

    private String symbol;

    private String companyName;

    /** The trading date on which EMA-20 crossed above EMA-50. */
    private LocalDate crossDate;

    /** 0 = today (the latest complete trading date), 1 = one day before, etc. */
    private int daysAgo;

    private BigDecimal ema20AtCross;

    private BigDecimal ema50AtCross;

    /** Last close captured in the scan window (anchor date). */
    private BigDecimal lastClose;
}