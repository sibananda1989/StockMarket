package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.strategy.model.AggregatedSignalResult;

/**
 * DTO for comparing the new multi-strategy signal with the existing signal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonDTO {
    private SignalDTO existingSignal;
    private AggregatedSignalResult multiStrategySignal;
}
