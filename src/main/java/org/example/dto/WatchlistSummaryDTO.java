package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistSummaryDTO {
    private Integer totalStocks;
    private Integer buyOpportunities;
    private Integer holdOpportunities;
    private Integer sellOpportunities;
    private String strongestSignal;
    private String weakestSignal;
}
