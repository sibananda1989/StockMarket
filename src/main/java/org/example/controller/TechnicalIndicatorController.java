package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.IndicatorDto;
import org.example.dto.IndicatorHistoryDto;
import org.example.entity.IndicatorType;
import org.example.service.TechnicalAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/indicators")
@RequiredArgsConstructor
@Slf4j
public class TechnicalIndicatorController {

    private final TechnicalAnalysisService analysisService;

    @Operation(summary = "Get latest values for all indicator types", description = "Returns the most recent calculated values for all available technical indicators for a given stock. Uses cached data if available.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful retrieval", content = @Content(mediaType = "application/json", schema = @Schema(implementation = IndicatorDto.class))),
            @ApiResponse(responseCode = "404", description = "Stock not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{stockId}")
    public ResponseEntity<List<IndicatorDto>> getAllLatestIndicators(@PathVariable Long stockId) {
        List<IndicatorDto> latest = analysisService.getLatestIndicators(stockId);
        return ResponseEntity.ok(latest);
    }

    @Operation(summary = "Get history of a specific indicator type", description = "Returns historical values for a given indicator type and stock. Optional query parameters allow filtering by date range (inclusive).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful retrieval", content = @Content(mediaType = "application/json", schema = @Schema(implementation = IndicatorHistoryDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date format"),
            @ApiResponse(responseCode = "404", description = "Stock not found or indicator type not available"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{stockId}/{type}")
    public ResponseEntity<IndicatorHistoryDto> getIndicatorHistory(
            @Parameter(description = "ID of the stock", required = true) @PathVariable Long stockId,
            @Parameter(description = "Type of indicator to retrieve", required = true) @PathVariable IndicatorType type,
            @Parameter(description = "Start date for filtering (inclusive, format: yyyy-MM-dd)", required = false) @RequestParam(required = false) String fromDateStr,
            @Parameter(description = "End date for filtering (inclusive, format: yyyy-MM-dd)", required = false) @RequestParam(required = false) String toDateStr) {

        List<IndicatorDto> history = analysisService.getIndicatorHistory(stockId, type);

        // Apply date range filtering if parameters are provided
        if (fromDateStr != null || toDateStr != null) {
            LocalDate fromDate = (fromDateStr != null) ? LocalDate.parse(fromDateStr) : null;
            LocalDate toDate = (toDateStr != null) ? LocalDate.parse(toDateStr) : null;

            history = history.stream()
                    .filter(dto -> {
                        LocalDate calcDate = dto.getCalculationDate();
                        boolean afterFrom = (fromDate == null || !calcDate.isBefore(fromDate));
                        boolean beforeTo = (toDate == null || !calcDate.isAfter(toDate));
                        return afterFrom && beforeTo;
                    })
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(new IndicatorHistoryDto(history));
    }

    @Operation(summary = "Trigger recalculation of all indicators", description = "Forces a recalculation of all technical indicators for a given stock using the latest price data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recalculation triggered successfully"),
            @ApiResponse(responseCode = "404", description = "Stock not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/calculate/{stockId}")
    public ResponseEntity<Void> triggerCalculation(@PathVariable Long stockId) {
        analysisService.calculateIndicatorsForStock(stockId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Fill gaps in indicators for the last N days", description = "Checks the last N days for each stock and fills in any missing indicator values (all 27+ types). Only recalculates dates that are incomplete, so this is fast on a well-maintained database.")
    @PostMapping("/fill-gaps")
    public ResponseEntity<org.example.dto.ApiResponse<Integer>> fillIndicatorGaps(
            @Parameter(description = "Number of days to look back (default: 7)") @RequestParam(defaultValue = "7") int days) {
        log.info("Received request to fill indicator gaps for the last {} days", days);
        long startTime = System.currentTimeMillis();
        int filled = analysisService.fillIndicatorGaps(days);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Indicator gap fill complete: {} records filled in {} ms", filled, elapsed);
        return ResponseEntity.ok(org.example.dto.ApiResponse.success("Indicator gaps filled", filled));
    }

    @Operation(summary = "Backfill historical indicators", description = "Computes and persists all technical indicators for every trading day going back the specified number of days. This is a long-running operation that walks through each day for each stock, calculates all 27+ indicators, and saves them to the database. Skips dates where indicators already exist.")
    @PostMapping("/backfill")
    public ResponseEntity<org.example.dto.ApiResponse<Integer>> backfillHistoricalIndicators(
            @Parameter(description = "Number of days to backfill (default: 365)") @RequestParam(defaultValue = "365") int days) {
        LocalDate fromDate = LocalDate.now().minusDays(days);
        log.info("Received request to backfill historical indicators for the last {} days (from {})", days, fromDate);

        // Run synchronously — for large datasets this may take minutes
        long startTime = System.currentTimeMillis();
        int saved = analysisService.backfillHistoricalIndicators(fromDate);
        long elapsed = System.currentTimeMillis() - startTime;

        log.info("Historical indicator backfill complete: {} records saved in {} ms", saved, elapsed);
        return ResponseEntity.ok(org.example.dto.ApiResponse.success("Backfill complete", saved));
    }
}