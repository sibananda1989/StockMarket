package org.example.dto;

import java.util.List;

public class IndicatorHistoryDto {

    private List<IndicatorDto> indicators;

    public IndicatorHistoryDto() {}

    public IndicatorHistoryDto(List<IndicatorDto> indicators) {
        this.indicators = indicators;
    }

    public List<IndicatorDto> getIndicators() {
        return indicators;
    }

    public void setIndicators(List<IndicatorDto> indicators) {
        this.indicators = indicators;
    }
}