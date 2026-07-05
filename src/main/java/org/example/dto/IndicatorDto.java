package org.example.dto;

import org.example.entity.IndicatorType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class IndicatorDto {

    private IndicatorType type;
    private BigDecimal value;
    private LocalDate calculationDate;

    public IndicatorDto() {}

    public IndicatorDto(IndicatorType type, BigDecimal value, LocalDate calculationDate) {
        this.type = type;
        this.value = value;
        this.calculationDate = calculationDate;
    }

    public IndicatorType getType() {
        return type;
    }

    public void setType(IndicatorType type) {
        this.type = type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public LocalDate getCalculationDate() {
        return calculationDate;
    }

    public void setCalculationDate(LocalDate calculationDate) {
        this.calculationDate = calculationDate;
    }
}