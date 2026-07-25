package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.DataAvailabilitySummaryDTO;
import org.example.service.DataAvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-availability")
@RequiredArgsConstructor
public class DataAvailabilityController {
    private final DataAvailabilityService service;

    @GetMapping
    public ResponseEntity<ApiResponse<DataAvailabilitySummaryDTO>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(service.getSummary()));
    }
}
