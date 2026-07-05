package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalComparisonReportDTO {
    private LocalDate reportDate;
    private long totalSignals;
    private long divergentSignals;
    private double divergenceRate;
    private Map<String, Long> recommendationChanges;
    private Map<String, Long> rootCauseAnalysis;
    private List<SignalComparisonDTO> topDivergentSignals;
    private double averageScoreDifferenceNonDivergent;
    private double averageScoreDifferenceDivergent;
}