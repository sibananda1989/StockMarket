package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.EmaCrossResponseDTO;
import org.example.service.EmaCrossScreenerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the EMA-20/50 crossover screener.
 */
@RestController
@RequestMapping("/api/screener/ema-cross")
public class EmaCrossScreenerController {

    private final EmaCrossScreenerService emaCrossScreenerService;

    public EmaCrossScreenerController(EmaCrossScreenerService emaCrossScreenerService) {
        this.emaCrossScreenerService = emaCrossScreenerService;
    }

    /**
     * Returns stocks where EMA-20 crossed above EMA-50 within the last {@code days}
     * complete trading days.
     *
     * @param days look-back window (clamped to 1..30). Default 5.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<EmaCrossResponseDTO>> getEmaCrosses(
            @RequestParam(defaultValue = "5") int days) {
        int clamped = Math.max(1, Math.min(30, days));
        EmaCrossResponseDTO response = emaCrossScreenerService.findEmaCrosses(clamped);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}