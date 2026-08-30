package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Envelope response for the EMA-20/50 crossover screener.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmaCrossResponseDTO {

    private List<EmaCrossSignalDTO> signals;

    /** The latest trading date that had complete price coverage. */
    private LocalDate anchorDate;

    /** How many stocks were scanned for valid EMAs. */
    private int universeScanned;

    /** Non-fatal notes (e.g. skipped stocks lacking enough history, split artifacts). */
    private List<String> warnings;
}