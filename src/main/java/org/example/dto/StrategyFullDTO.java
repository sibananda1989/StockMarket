package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyFullDTO {

    private String strategyName;
    private String displayName;
    private int priority;
    private boolean active;
    private List<String> tags;
    private int totalStocksMatched;
    private List<StrategyConditionFullDTO> conditions;
}
