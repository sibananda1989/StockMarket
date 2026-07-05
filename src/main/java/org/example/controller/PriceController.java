package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.DailyPriceDTO;
import org.example.entity.DailyPrice;
import org.example.service.DailyPriceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceController {

    private final DailyPriceService dailyPriceService;

    @PostMapping
    public ResponseEntity<ApiResponse<DailyPriceDTO>> saveDailyPrice(@Valid @RequestBody DailyPriceDTO dto) {
        DailyPrice savedPrice = dailyPriceService.saveDailyPrice(dto);
        DailyPriceDTO responseDto = convertToDTO(savedPrice);
        return ResponseEntity.ok(ApiResponse.success("Price saved successfully", responseDto));
    }

    @GetMapping("/stock/{stockId}")
    public ResponseEntity<ApiResponse<List<DailyPriceDTO>>> getPriceHistory(
            @PathVariable Long stockId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        
        List<DailyPriceDTO> prices;
        
        if (fromDate != null && toDate != null) {
            prices = dailyPriceService.getPriceHistoryDTO(stockId, fromDate, toDate);
        } else {
            prices = dailyPriceService.getAllPricesByStockId(stockId)
                    .stream()
                    .map(this::convertToDTO)
                    .toList();
        }
        
        return ResponseEntity.ok(ApiResponse.success(prices));
    }

    @GetMapping("/stock/{stockId}/latest")
    public ResponseEntity<ApiResponse<DailyPriceDTO>> getLatestPrice(@PathVariable Long stockId) {
        // NoPriceDataException (a 404 with code "NO_PRICE_DATA") propagates
        // through GlobalExceptionHandler when the stock has no prices yet.
        // For all other cases, return the DTO (or null) inside a 200.
        DailyPrice latestPrice = dailyPriceService.getLatestPrice(stockId);
        DailyPriceDTO dto = latestPrice != null ? convertToDTO(latestPrice) : null;
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    private DailyPriceDTO convertToDTO(DailyPrice dailyPrice) {
        DailyPriceDTO dto = new DailyPriceDTO();
        dto.setId(dailyPrice.getId());
        dto.setStockId(dailyPrice.getStock().getId());
        dto.setClosingPrice(dailyPrice.getClosingPrice());
        dto.setOpeningPrice(dailyPrice.getOpeningPrice());
        dto.setHighPrice(dailyPrice.getHighPrice());
        dto.setLowPrice(dailyPrice.getLowPrice());
        dto.setVolume(dailyPrice.getVolume());
        dto.setPriceDate(dailyPrice.getPriceDate());
        dto.setCreatedAt(dailyPrice.getCreatedAt());
        return dto;
    }
}
