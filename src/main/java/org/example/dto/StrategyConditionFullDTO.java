package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConditionFullDTO {

    private String conditionId;
    private String signal;
    private String fieldLabel;
    private String operator;
    private BigDecimal thresholdValue;
    private BigDecimal thresholdValue2;
    private String confidence;
    private int displayOrder;
    private int stockCount;
    private boolean enabled;
}
