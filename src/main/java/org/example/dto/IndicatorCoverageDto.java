package org.example.dto;

import org.example.entity.IndicatorType;

import java.util.List;

/**
 * Reports which technical indicators are present (and which are missing)
 * for a given stock on a given date. Used by the dashboard's "missing
 * indicators" badge to surface long-look-back gaps (e.g. SMA-200) that
 * can't be calculated yet due to insufficient price history.
 *
 * Expected count = the total number of {@link IndicatorType} values (27
 * as of July 2026). Available count = rows written into
 * {@code technical_indicators} for {@code calculationDate}.
 */
public class IndicatorCoverageDto {

    private Long stockId;
    private Integer expected;
    private Integer available;
    private Integer missing;
    private List<IndicatorType> missingIndicators;
    private String calculationDate;

    public IndicatorCoverageDto() {}

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public Integer getExpected() {
        return expected;
    }

    public void setExpected(Integer expected) {
        this.expected = expected;
    }

    public Integer getAvailable() {
        return available;
    }

    public void setAvailable(Integer available) {
        this.available = available;
    }

    public Integer getMissing() {
        return missing;
    }

    public void setMissing(Integer missing) {
        this.missing = missing;
    }

    public List<IndicatorType> getMissingIndicators() {
        return missingIndicators;
    }

    public void setMissingIndicators(List<IndicatorType> missingIndicators) {
        this.missingIndicators = missingIndicators;
    }

    public String getCalculationDate() {
        return calculationDate;
    }

    public void setCalculationDate(String calculationDate) {
        this.calculationDate = calculationDate;
    }
}
