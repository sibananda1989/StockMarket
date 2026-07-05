package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.IndicatorDto;
import org.example.dto.RsiDTO;
import org.example.entity.IndicatorType;
import org.example.service.TechnicalAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rsi")
@RequiredArgsConstructor
public class RsiController {

    private final TechnicalAnalysisService technicalAnalysisService;

    @GetMapping("/stock/{stockId}")
    public ResponseEntity<ApiResponse<RsiDTO>> getLatestRsi(@PathVariable Long stockId) {
        // Read from canonical TechnicalIndicator table (auto-calculated daily)
        for (IndicatorDto ind : technicalAnalysisService.getLatestIndicators(stockId)) {
            if (ind.getType() == IndicatorType.RSI) {
                RsiDTO dto = new RsiDTO();
                dto.setStockId(stockId);
                dto.setRsi14(ind.getValue());
                dto.setCalculationDate(ind.getCalculationDate());
                return ResponseEntity.ok(ApiResponse.success(dto));
            }
        }
        return ResponseEntity.ok(ApiResponse.error("No RSI data found for stock " + stockId));
    }

    @GetMapping("/stock/{stockId}/history")
    public ResponseEntity<ApiResponse<List<RsiDTO>>> getRsiHistory(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "30") int days) {
        List<IndicatorDto> indicatorHistory = technicalAnalysisService.getIndicatorHistory(stockId, IndicatorType.RSI);
        LocalDate since = LocalDate.now().minusDays(days);
        List<RsiDTO> rsiHistory = indicatorHistory.stream()
                .filter(ind -> !ind.getCalculationDate().isBefore(since))
                .map(ind -> {
                    RsiDTO dto = new RsiDTO();
                    dto.setStockId(stockId);
                    dto.setRsi14(ind.getValue());
                    dto.setCalculationDate(ind.getCalculationDate());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(rsiHistory));
    }

    @PostMapping("/calculate/{stockId}")
    public ResponseEntity<ApiResponse<String>> calculateRsi(@PathVariable Long stockId) {
        technicalAnalysisService.calculateIndicatorsForStock(stockId);
        return ResponseEntity.ok(ApiResponse.success("Indicators recalculated for stock " + stockId, null));
    }

    @PostMapping("/calculate-all")
    public ResponseEntity<ApiResponse<String>> calculateAllRsi() {
        technicalAnalysisService.calculateForAllStocks();
        return ResponseEntity.ok(ApiResponse.success("Indicators calculated for all stocks", null));
    }

    @PostMapping("/fill-gaps")
    public ResponseEntity<ApiResponse<String>> fillRsiGaps() {
        int filled = technicalAnalysisService.fillIndicatorGaps(30);
        return ResponseEntity.ok(ApiResponse.success("Indicator gaps filled for the last 30 days: " + filled + " records", null));
    }
}
