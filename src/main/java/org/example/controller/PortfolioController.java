package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.PortfolioAggregateDTO;
import org.example.dto.PortfolioSnapshotDTO;
import org.example.service.PortfolioSnapshotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

  private final PortfolioSnapshotService snapshotService;

  /**
   * Backward-compatible overload for tests calling getHistory(int, Boolean).
   * Delegates to the 3-param version with null portfolioId.
   */
  public ResponseEntity<ApiResponse<List<PortfolioAggregateDTO>>> getHistory(int days, Boolean all) {
    return getHistory(days, all, null);
  }

  @GetMapping("/history")
  public ResponseEntity<ApiResponse<List<PortfolioAggregateDTO>>> getHistory(
      @RequestParam(defaultValue = "365") int days,
      @RequestParam(required = false) Boolean all,
      @RequestParam(required = false) Long portfolioId) {
    List<PortfolioAggregateDTO> result;
    if (Boolean.TRUE.equals(all)) {
      result = portfolioId != null
          ? snapshotService.getAllAggregatedHistory(portfolioId)
          : snapshotService.getAllAggregatedHistory();
    } else {
      result = portfolioId != null
          ? snapshotService.getAggregatedHistory(days, portfolioId)
          : snapshotService.getAggregatedHistory(days);
    }
    return ResponseEntity.ok(ApiResponse.success(result != null ? result : List.of()));
  }

  @PostMapping("/backfill")
  public ResponseEntity<ApiResponse<String>> backfillSnapshots(
      @RequestParam(defaultValue = "false") boolean force,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
    LocalDate start = fromDate != null ? fromDate : LocalDate.now().minusDays(60);
    snapshotService.backfillSnapshots(start, force);
    String msg = force ? "Backfill complete (forced rebuild)" : "Backfill complete";
    return ResponseEntity.ok(ApiResponse.success(msg));
  }

  @PostMapping("/backfill/force")
  public ResponseEntity<ApiResponse<String>> forceBackfillSnapshots() {
    snapshotService.backfillSnapshots(LocalDate.of(2020, 1, 1), true);
    return ResponseEntity.ok(ApiResponse.success("Force backfill complete"));
  }

  @PostMapping("/backfill/date")
  public ResponseEntity<ApiResponse<String>> backfillDate(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    snapshotService.deleteSnapshotsByDate(date);
    snapshotService.backfillSnapshots(date, true);
    return ResponseEntity.ok(ApiResponse.success("Backfill completed for " + date));
  }

  @GetMapping("/screener")
  public ResponseEntity<ApiResponse<List<PortfolioSnapshotDTO>>> getScreener(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return ResponseEntity.ok(ApiResponse.success(snapshotService.getScreenerData(date)));
  }
}
