package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.entity.FiiDiiData;
import org.example.service.FiiDiiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/fiidii")
@RequiredArgsConstructor
public class FiiDiiController {

    private final FiiDiiService fiidiiService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLatestFiiDii() {
        FiiDiiData data = fiidiiService.getLatest();
        if (data == null) {
            return ResponseEntity.ok(ApiResponse.success("No FII/DII data available yet", Map.of()));
        }

        BigDecimal combined = BigDecimal.ZERO;
        if (data.getFiiNet() != null) combined = combined.add(data.getFiiNet());
        if (data.getDiiNet() != null) combined = combined.add(data.getDiiNet());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", data.getDate().toString());
        result.put("fiiNet", data.getFiiNet());
        result.put("diiNet", data.getDiiNet());
        result.put("combinedNet", combined);
        result.put("sentiment", fiidiiService.getSentimentSummary());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh() {
        FiiDiiData data = fiidiiService.fetchAndSave();
        if (data == null) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch FII/DII data from NSE"));
        }
        return ResponseEntity.ok(ApiResponse.success("FII/DII data refreshed successfully for " + data.getDate()));
    }
}
