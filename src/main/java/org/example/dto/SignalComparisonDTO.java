package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalComparisonDTO {
    private Long stockId;
    private String productionRecommendation;
    private String shadowRecommendation;
    private int productionScore;
    private int shadowScore;
    private Long productionConfidence;
    private Long shadowConfidence;
    private boolean isDivergent;
    private List<String> divergenceReasons;
    private int scoreDifference;
}