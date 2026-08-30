package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.PortfolioAggregateDTO;
import org.example.dto.PortfolioSnapshotDTO;
import org.example.entity.PortfolioDailyValue;
import org.example.service.PortfolioDailyValueService;
import org.example.service.PortfolioHistoryService;
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
  private final PortfolioHistoryService historyService;
  private final PortfolioDailyValueService dailyValueService;

  /**
   * Backward-compatible overload for tests calling getHistory(int, Boolean).
   * Delegates to the 3-param version with null portfolioId.
   */
  public ResponseEntity<ApiResponse<List<PortfolioAggregateDTO>>> getHistory(int days, Boolean all) {
    return getHistory(days, all, null, "ledger");
  }

  @GetMapping("/history")
  public ResponseEntity<ApiResponse<List<PortfolioAggregateDTO>>> getHistory(
      @RequestParam(defaultValue = "365") int days,
      @RequestParam(required = false) Boolean all,
      @RequestParam(required = false) Long portfolioId,
      @RequestParam(required = false, defaultValue = "daily") String source) {
    // source=ledger: replay portfolio_transactions ledger + join daily_prices for closing prices.
    //                (Legacy path; not cached; works without portfolio_daily_values being populated.)
    // source=snapshot: read aggregated portfolio_snapshots grouped by date.
    // source=daily (default): read persisted portfolio_daily_values table only.
    //                No fallback — if the table is empty, the chart is empty. Repopulate via
    //                POST /api/portfolio/daily-values/backfill.
    if ("ledger".equalsIgnoreCase(source)) {
      List<PortfolioAggregateDTO> ledgerResult;
      if (Boolean.TRUE.equals(all)) {
        ledgerResult = portfolioId != null
            ? historyService.getAllHistory(portfolioId)
            : historyService.getAllHistory();
      } else {
        ledgerResult = portfolioId != null
            ? historyService.getHistory(days, portfolioId)
            : historyService.getHistory(days);
      }
      return ResponseEntity.ok(ApiResponse.success(ledgerResult != null ? ledgerResult : List.of()));
    }
    if ("snapshot".equalsIgnoreCase(source)) {
      List<PortfolioAggregateDTO> snapResult;
      if (Boolean.TRUE.equals(all)) {
        snapResult = portfolioId != null
            ? snapshotService.getAllAggregatedHistory(portfolioId)
            : snapshotService.getAllAggregatedHistory();
      } else {
        snapResult = portfolioId != null
            ? snapshotService.getAggregatedHistory(days, portfolioId)
            : snapshotService.getAggregatedHistory(days);
      }
      return ResponseEntity.ok(ApiResponse.success(snapResult != null ? snapResult : List.of()));
    }
    // default: persisted daily values table (portfolio_daily_values) — no fallback
    List<PortfolioAggregateDTO> result;
    if (Boolean.TRUE.equals(all)) {
      result = dailyValueService.getAllHistoryAsDto(portfolioId);
    } else {
      result = dailyValueService.getHistoryAsDto(portfolioId, days);
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

  // ── New persisted daily values (per portfolio, date, investment, currentValue + logging) ──
  @PostMapping("/daily-values/backfill")
  public ResponseEntity<ApiResponse<String>> backfillDailyValues(
      @RequestParam(required = false) Long portfolioId) {
    int rows;
    if (portfolioId != null) {
      rows = dailyValueService.backfillPortfolio(portfolioId);
    } else {
      rows = dailyValueService.backfillAllPortfolios();
    }
    return ResponseEntity.ok(ApiResponse.success("Daily values backfilled: " + rows + " rows"));
  }

  @GetMapping("/daily-values")
  public ResponseEntity<ApiResponse<List<PortfolioDailyValue>>> getDailyValues(
      @RequestParam(required = false) Long portfolioId,
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(required = false) Boolean all) {
    List<PortfolioDailyValue> result;
    if (Boolean.TRUE.equals(all)) {
      result = dailyValueService.getAllHistory(portfolioId != null ? portfolioId : 1L);
    } else {
      result = dailyValueService.getHistory(portfolioId != null ? portfolioId : 1L, days);
    }
    return ResponseEntity.ok(ApiResponse.success(result));
  }
}
