package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.SignalDTO;
import org.example.dto.SignalHistoryPoint;
import org.example.service.SignalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SignalDTO>>> getAllSignals(
            @RequestParam(required = false) Long portfolioId) {
        List<SignalDTO> signals = portfolioId != null
                ? signalService.getSignalsForPortfolio(portfolioId)
                : signalService.getAllSignals();
        return ResponseEntity.ok(ApiResponse.success(signals));
    }

    /**
     * Batch signals for specific stock ids — used by watchlist page to avoid
     * computing signals for every stock in the system.
     */
    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<List<SignalDTO>>> getSignalsBatch(
            @RequestParam("ids") List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.success(signalService.getSignalsForIds(ids)));
    }

    @GetMapping("/buy")
    public ResponseEntity<ApiResponse<List<SignalDTO>>> getBuySignals() {
        List<SignalDTO> signals = signalService.getBuySignals();
        return ResponseEntity.ok(ApiResponse.success(signals));
    }

    @GetMapping("/sell")
    public ResponseEntity<ApiResponse<List<SignalDTO>>> getSellSignals() {
        List<SignalDTO> signals = signalService.getSellSignals();
        return ResponseEntity.ok(ApiResponse.success(signals));
    }

    @GetMapping("/{stockId}")
    public ResponseEntity<ApiResponse<SignalDTO>> getSignalForStock(@PathVariable Long stockId) {
        SignalDTO signal = signalService.getComputedSignal(stockId);
        if (signal == null) {
            return ResponseEntity.ok(ApiResponse.success("Insufficient data for signal", null));
        }
        return ResponseEntity.ok(ApiResponse.success(signal));
    }

    @GetMapping("/{stockId}/history")
    public ResponseEntity<ApiResponse<List<SignalHistoryPoint>>> getSignalHistory(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "90") int days) {
        List<SignalHistoryPoint> history = signalService.getSignalHistory(stockId, days);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
