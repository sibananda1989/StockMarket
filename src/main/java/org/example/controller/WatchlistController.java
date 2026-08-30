package org.example.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ApiResponse;
import org.example.dto.WatchlistDTO;
import org.example.dto.WatchlistDetailDTO;
import org.example.entity.WatchlistItem;
import org.example.service.StockSyncService;
import org.example.service.WatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlists")
@RequiredArgsConstructor
@Slf4j
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final StockSyncService stockSyncService;

    // ─── Watchlist CRUD ─────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<WatchlistDTO>>> getAllWatchlists() {
        return ResponseEntity.ok(ApiResponse.success(watchlistService.getAllWatchlists()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WatchlistDTO>> createWatchlist(
            @Valid @RequestBody CreateWatchlistRequest request) {
        WatchlistDTO dto = watchlistService.createWatchlist(request.getName(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Watchlist created", dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WatchlistDTO>> updateWatchlist(
            @PathVariable Long id,
            @Valid @RequestBody CreateWatchlistRequest request) {
        WatchlistDTO dto = watchlistService.renameWatchlist(id, request.getName(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.success("Watchlist updated", dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWatchlist(@PathVariable Long id) {
        watchlistService.deleteWatchlist(id);
        return ResponseEntity.ok(ApiResponse.success("Watchlist deleted", null));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<ApiResponse<WatchlistDetailDTO>> getWatchlistDetail(@PathVariable Long id) {
        WatchlistDetailDTO dto = watchlistService.getWatchlistDetail(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // ─── Watchlist Items ────────────────────────────────────────────────────

    @GetMapping("/{id}/items")
    public ResponseEntity<ApiResponse<WatchlistDetailDTO>> getWatchlistItems(@PathVariable Long id) {
        WatchlistDetailDTO dto = watchlistService.getWatchlistDetail(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addStockToWatchlist(
            @PathVariable Long id,
            @RequestBody AddStockRequest request) {
        WatchlistItem item = watchlistService.addStockToWatchlist(id, request.getStockId());
        Map<String, Object> result = new HashMap<>();
        result.put("watchlistItemId", item.getId());
        result.put("stockId", item.getStock().getId());
        result.put("symbol", item.getStock().getSymbol());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock added to watchlist", result));
    }

    @DeleteMapping("/{watchlistId}/items/{stockId}")
    public ResponseEntity<ApiResponse<Void>> removeStockFromWatchlist(
            @PathVariable Long watchlistId,
            @PathVariable Long stockId) {
        watchlistService.removeStockFromWatchlist(watchlistId, stockId);
        return ResponseEntity.ok(ApiResponse.success("Stock removed from watchlist", null));
    }

    @PostMapping("/{id}/items/batch")
    public ResponseEntity<ApiResponse<Void>> addBatchStocks(
            @PathVariable Long id,
            @RequestBody BatchAddRequest request) {
        List<WatchlistItem> items = watchlistService.addBatchStocks(id, request.getStockIds());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(items.size() + " stocks added to watchlist", null));
    }

    // ─── Queries ────────────────────────────────────────────────────────────

    @GetMapping("/stock/{stockId}")
    public ResponseEntity<ApiResponse<List<WatchlistDTO>>> getWatchlistsForStock(
            @PathVariable Long stockId) {
        return ResponseEntity.ok(ApiResponse.success(watchlistService.getWatchlistsForStock(stockId)));
    }

    // ─── Sync History ──────────────────────────────────────────────────────

    @PostMapping("/{id}/sync-history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncWatchlistHistory(
            @PathVariable Long id,
            @Valid @RequestBody SyncWatchlistHistoryRequest request) {
        log.info("Syncing {} days of history for all stocks in watchlist {}", request.getDays(), id);
        List<Long> stockIds = watchlistService.getStockIdsInWatchlist(id);
        int synced = 0;
        int failed = 0;
        for (Long stockId : stockIds) {
            try {
                stockSyncService.syncStockHistory(stockId, request.getDays());
                synced++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to sync history for stock {}: {}", stockId, e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("synced", synced);
        result.put("failed", failed);
        result.put("days", request.getDays());
        return ResponseEntity.ok(ApiResponse.success("History sync complete", result));
    }

    // ─── Request DTOs ───────────────────────────────────────────────────────

    public static class CreateWatchlistRequest {
        @jakarta.validation.constraints.NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        private String name;

        @Size(max = 255, message = "Description must be at most 255 characters")
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class AddStockRequest {
        @jakarta.validation.constraints.NotNull(message = "Stock ID is required")
        private Long stockId;

        public Long getStockId() { return stockId; }
        public void setStockId(Long stockId) { this.stockId = stockId; }
    }

    public static class BatchAddRequest {
        private List<Long> stockIds;

        public List<Long> getStockIds() { return stockIds; }
        public void setStockIds(List<Long> stockIds) { this.stockIds = stockIds; }
    }

    public static class SyncWatchlistHistoryRequest {
        @NotNull(message = "Days parameter is required")
        @Min(value = 30, message = "Days must be at least 30")
        @Max(value = 3650, message = "Days must be at most 3650")
        private Integer days = 730;

        public Integer getDays() { return days; }
        public void setDays(Integer days) { this.days = days; }
    }
}
