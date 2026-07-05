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
public class WatchlistOpportunityResponseDTO {
    private List<WatchlistOpportunityDTO> stocks;
    private WatchlistSummaryDTO summary;
    private List<String> warnings;
}
