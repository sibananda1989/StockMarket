package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategyBreakdownDTO {
    private String strategyName;
    private String signal;
    private double confidence;
    private int priority;
    private double weightedScore;
    private String reason;
}
