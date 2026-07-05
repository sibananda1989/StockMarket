package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.WatchlistOpportunityResponseDTO;
import org.example.service.WatchlistOpportunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistOpportunityController {

    private final WatchlistOpportunityService watchlistOpportunityService;

    @GetMapping("/opportunities")
    public ResponseEntity<ApiResponse<WatchlistOpportunityResponseDTO>> getOpportunities(
            @RequestParam(required = false) Long portfolioId) {
        WatchlistOpportunityResponseDTO response = watchlistOpportunityService.getOpportunities(portfolioId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
