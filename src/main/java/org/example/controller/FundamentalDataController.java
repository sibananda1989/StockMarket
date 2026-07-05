package org.example.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.FundamentalDataDTO;
import org.example.dto.FundamentalScreenRequest;
import org.example.exception.ResourceNotFoundException;
import org.example.service.FundamentalDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fundamentals")
@RequiredArgsConstructor
public class FundamentalDataController {

    private final FundamentalDataService fundamentalDataService;

    @GetMapping("/{stockId}")
    public ResponseEntity<ApiResponse<FundamentalDataDTO>> getFundamentals(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        FundamentalDataDTO dto = fundamentalDataService.getFundamentals(stockId);
        if (dto == null) {
            return ResponseEntity.ok(ApiResponse.success("No fundamental data. Use POST to fetch.", null));
        }
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/fetch/{stockId}")
    public ResponseEntity<ApiResponse<FundamentalDataDTO>> fetchFundamentals(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        FundamentalDataDTO dto = fundamentalDataService.fetchAndSave(stockId);
        if (dto == null) {
            throw new ResourceNotFoundException("Could not fetch fundamentals for stock " + stockId);
        }
        return ResponseEntity.ok(ApiResponse.success("Fundamental data fetched successfully", dto));
    }

    @PostMapping("/fetch-all")
    public ResponseEntity<ApiResponse<Integer>> fetchAllFundamentals() {
        int count = fundamentalDataService.fetchForAllStocks();
        return ResponseEntity.ok(ApiResponse.success("Fetched fundamentals for " + count + " stocks", count));
    }

    @PostMapping("/screen")
    public ResponseEntity<ApiResponse<List<FundamentalDataDTO>>> screen(
            @RequestBody @Valid FundamentalScreenRequest request) {
        List<FundamentalDataDTO> results = fundamentalDataService.screen(request);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @GetMapping("/sectors")
    public ResponseEntity<ApiResponse<List<String>>> getSectors() {
        return ResponseEntity.ok(ApiResponse.success(fundamentalDataService.getDistinctSectors()));
    }
}
