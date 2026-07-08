package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.SupportResistanceDto;
import org.example.service.SupportResistanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/support-resistance")
@RequiredArgsConstructor
public class SupportResistanceController {

    private final SupportResistanceService supportResistanceService;

    @GetMapping("/{stockId}")
    public ResponseEntity<ApiResponse<SupportResistanceDto>> getLatestLevels(
            @PathVariable Long stockId,
            @RequestParam(required = false) Integer lookbackDays) {
        // Validate minimum lookback — need at least 20 days of price data
        if (lookbackDays != null && lookbackDays < 20) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("lookbackDays must be at least 20 for meaningful S/R levels"));
        }
        SupportResistanceDto dto;
        if (lookbackDays != null && lookbackDays > 0) {
            dto = supportResistanceService.getLatestLevels(stockId, lookbackDays);
        } else {
            dto = supportResistanceService.getLatestLevels(stockId);
        }
        if (dto == null) {
            return ResponseEntity.ok(ApiResponse.success("No S/R levels calculated yet. Call calculate first.", null));
        }
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{stockId}/as-of")
    public ResponseEntity<ApiResponse<SupportResistanceDto>> getLevelsAsOfDate(
            @PathVariable Long stockId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SupportResistanceDto dto = supportResistanceService.getLevelsAsOfDate(stockId, date);
        if (dto == null) {
            return ResponseEntity.ok(ApiResponse.success("No S/R levels found for the specified date.", null));
        }
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{stockId}/history")
    public ResponseEntity<ApiResponse<List<SupportResistanceDto>>> getHistory(
            @PathVariable Long stockId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        if (fromDate == null) fromDate = LocalDate.now().minusMonths(6);
        if (toDate == null) toDate = LocalDate.now();
        List<SupportResistanceDto> history = supportResistanceService.getHistory(stockId, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping("/calculate/{stockId}")
    public ResponseEntity<ApiResponse<SupportResistanceDto>> calculate(@PathVariable Long stockId) {
        SupportResistanceDto dto = supportResistanceService.calculateForStock(stockId);
        if (dto == null) {
            return ResponseEntity.ok(ApiResponse.success("Insufficient price data. Need at least 20 days.", null));
        }
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/calculate-all")
    public ResponseEntity<ApiResponse<String>> calculateAll() {
        supportResistanceService.calculateForAllStocks();
        return ResponseEntity.ok(ApiResponse.success("Batch S/R calculation started in background. Check logs for progress.", null));
    }
}
