package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.entity.CorporateEvent;
import org.example.service.CorporateEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class CorporateEventController {

    private final CorporateEventService corporateEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CorporateEvent>>> getEvents(
            @RequestParam(defaultValue = "") String symbol) {
        if (symbol.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("symbol parameter is required"));
        }
        List<CorporateEvent> events = corporateEventService.getEventsForSymbol(symbol);
        if (events.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No upcoming events for " + symbol.toUpperCase(), events));
        }
        return ResponseEntity.ok(ApiResponse.success("Upcoming events for " + symbol.toUpperCase(), events));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh() {
        int count = corporateEventService.fetchAndSaveEvents();
        return ResponseEntity.ok(ApiResponse.success("Refreshed " + count + " corporate events from NSE calendar"));
    }
}
