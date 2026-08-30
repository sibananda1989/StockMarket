package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
public class StrategyBreakdownDTO {
    private String strategyName;
    private String signal;
    private double confidence;
    private int priority;
    private double weightedScore;
    private String reason;

    @JsonProperty("latestVolume")
    private Long latestVolume;

    @JsonProperty("avgVolume")
    private Double avgVolume;

    @JsonProperty("spikeThreshold")
    private Double spikeThreshold;

    public StrategyBreakdownDTO(String strategyName, String signal, double confidence, int priority,
            double weightedScore, String reason, Long latestVolume, Double avgVolume, Double spikeThreshold) {
        this.strategyName = strategyName;
        this.signal = signal;
        this.confidence = confidence;
        this.priority = priority;
        this.weightedScore = weightedScore;
        this.reason = reason;
        this.latestVolume = latestVolume;
        this.avgVolume = avgVolume;
        this.spikeThreshold = spikeThreshold;
    }
}