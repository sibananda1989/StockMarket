package org.example.controller;

import jakarta.validation.constraints.Positive;
import org.example.dto.*;
import org.example.exception.ResourceNotFoundException;
import org.example.service.institutional.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/institutional")
@Validated
public class InstitutionalHoldingController {

    private final InstitutionalHoldingService holdingService;
    private final BulkDealService bulkDealService;
    private final BlockDealService blockDealService;
    private final InstitutionalScoreService scoreService;
    private final InstitutionalScreenerService screenerService;
    private final NseXbrlShareholdingService xbrlService;

    public InstitutionalHoldingController(InstitutionalHoldingService holdingService,
                                          BulkDealService bulkDealService,
                                          BlockDealService blockDealService,
                                          InstitutionalScoreService scoreService,
                                          InstitutionalScreenerService screenerService,
                                          NseXbrlShareholdingService xbrlService) {
        this.holdingService = holdingService;
        this.bulkDealService = bulkDealService;
        this.blockDealService = blockDealService;
        this.scoreService = scoreService;
        this.screenerService = screenerService;
        this.xbrlService = xbrlService;
    }

    // =====================================================================
    // Shareholding Pattern Endpoints
    // =====================================================================

    @GetMapping("/holdings/{stockId}")
    public ResponseEntity<ApiResponse<InstitutionalHoldingDTO>> getLatestHolding(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        InstitutionalHoldingDTO dto = holdingService.getLatestHolding(stockId);
        return ResponseEntity.ok(ApiResponse.success("Latest holding data", dto));
    }

    @GetMapping("/holdings/{stockId}/history")
    public ResponseEntity<ApiResponse<List<InstitutionalHoldingDTO>>> getHoldingHistory(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        List<InstitutionalHoldingDTO> history = holdingService.getHoldingHistory(stockId);
        return ResponseEntity.ok(ApiResponse.success("Holding history", history));
    }

    @GetMapping("/holdings/all")
    public ResponseEntity<ApiResponse<List<InstitutionalHoldingDTO>>> getAllLatestHoldings() {
        List<InstitutionalHoldingDTO> holdings = holdingService.getAllLatestHoldings();
        return ResponseEntity.ok(ApiResponse.success("Latest holdings for all stocks", holdings));
    }

    @PostMapping("/holdings/fetch-all")
    public ResponseEntity<ApiResponse<String>> fetchAllHoldings() {
        int count = holdingService.fetchForAllStocks();
        return ResponseEntity.ok(ApiResponse.success("Fetched shareholding data for " + count + " stocks",
                "Fetched " + count + " stocks"));
    }

    @PostMapping("/holdings/fetch/{stockId}")
    public ResponseEntity<ApiResponse<InstitutionalHoldingDTO>> fetchHolding(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        InstitutionalHoldingDTO dto = holdingService.fetchForStock(stockId);
        if (dto == null) {
            throw new ResourceNotFoundException("Failed to fetch or no data for stock " + stockId);
        }
        return ResponseEntity.ok(ApiResponse.success("Fetched holding data", dto));
    }

    // =====================================================================
    // XBRL Detailed Shareholding Endpoints
    // =====================================================================

    @PostMapping("/xbrl/fetch-all")
    public ResponseEntity<ApiResponse<String>> fetchAllXbrl() {
        int count = xbrlService.fetchForAllStocks();
        return ResponseEntity.ok(ApiResponse.success(
                "XBRL detailed data fetched for " + count + " stocks",
                "Updated " + count + " stocks"));
    }

    @PostMapping("/xbrl/fetch/{stockId}")
    public ResponseEntity<ApiResponse<String>> fetchXbrlForStock(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        boolean ok = xbrlService.fetchForStockId(stockId);
        if (!ok) {
            throw new ResourceNotFoundException("No XBRL data found for stock " + stockId);
        }
        return ResponseEntity.ok(ApiResponse.success("XBRL data fetched for stock " + stockId, "OK"));
    }

    // =====================================================================
    // Bulk Deal Endpoints
    // =====================================================================

    @GetMapping("/bulk-deals/{stockId}")
    public ResponseEntity<ApiResponse<List<BulkDealDTO>>> getBulkDeals(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        List<BulkDealDTO> deals = bulkDealService.getDealsForStock(stockId);
        return ResponseEntity.ok(ApiResponse.success("Bulk deals for stock", deals));
    }

    @GetMapping("/bulk-deals/range")
    public ResponseEntity<ApiResponse<List<BulkDealDTO>>> getBulkDealsByRange(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null) to = LocalDate.now();
        List<BulkDealDTO> deals = bulkDealService.getDealsInDateRange(from, to);
        return ResponseEntity.ok(ApiResponse.success("Bulk deals from " + from + " to " + to, deals));
    }

    @PostMapping("/bulk-deals/fetch")
    public ResponseEntity<ApiResponse<String>> fetchBulkDeals(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        int count = bulkDealService.fetchBulkDeals(from, to);
        return ResponseEntity.ok(ApiResponse.success("Fetched " + count + " bulk deals", "Saved " + count + " new bulk deals"));
    }

    @PostMapping("/bulk-deals/fetch-today")
    public ResponseEntity<ApiResponse<String>> fetchTodayBulkDeals() {
        int count = bulkDealService.fetchTodayBulkDeals();
        return ResponseEntity.ok(ApiResponse.success("Fetched " + count + " today's bulk deals", "Saved " + count + " new bulk deals"));
    }

    @GetMapping("/bulk-deals/top-institutional")
    public ResponseEntity<ApiResponse<List<BulkDealDTO>>> getTopInstitutionalBulkDeals(
            @RequestParam(defaultValue = "30") @Positive(message = "days must be positive") int days) {
        List<BulkDealDTO> deals = bulkDealService.getTopInstitutionalDeals(days);
        return ResponseEntity.ok(ApiResponse.success("Top institutional bulk deals", deals));
    }

    // =====================================================================
    // Block Deal Endpoints
    // =====================================================================

    @GetMapping("/block-deals/{stockId}")
    public ResponseEntity<ApiResponse<List<BlockDealDTO>>> getBlockDeals(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        List<BlockDealDTO> deals = blockDealService.getDealsForStock(stockId);
        return ResponseEntity.ok(ApiResponse.success("Block deals for stock", deals));
    }

    @GetMapping("/block-deals/range")
    public ResponseEntity<ApiResponse<List<BlockDealDTO>>> getBlockDealsByRange(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null) to = LocalDate.now();
        List<BlockDealDTO> deals = blockDealService.getDealsInDateRange(from, to);
        return ResponseEntity.ok(ApiResponse.success("Block deals from " + from + " to " + to, deals));
    }

    @PostMapping("/block-deals/fetch")
    public ResponseEntity<ApiResponse<String>> fetchBlockDeals(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        int count = blockDealService.fetchBlockDeals(from, to);
        return ResponseEntity.ok(ApiResponse.success("Fetched " + count + " block deals", "Saved " + count + " new block deals"));
    }

    @PostMapping("/block-deals/fetch-today")
    public ResponseEntity<ApiResponse<String>> fetchTodayBlockDeals() {
        int count = blockDealService.fetchTodayBlockDeals();
        return ResponseEntity.ok(ApiResponse.success("Fetched " + count + " today's block deals", "Saved " + count + " new block deals"));
    }

    // =====================================================================
    // Institutional Score Endpoints
    // =====================================================================

    @GetMapping("/score/{stockId}")
    public ResponseEntity<ApiResponse<InstitutionalScoreDTO>> getScore(
            @PathVariable @Positive(message = "stockId must be positive") Long stockId) {
        InstitutionalScoreDTO score = scoreService.computeScore(stockId);
        if (score == null) {
            throw new ResourceNotFoundException("Could not compute score for stock " + stockId);
        }
        return ResponseEntity.ok(ApiResponse.success("Institutional score", score));
    }

    @GetMapping("/score/all")
    public ResponseEntity<ApiResponse<List<InstitutionalScoreDTO>>> getAllScores() {
        List<InstitutionalScoreDTO> scores = scoreService.computeAllScores();
        return ResponseEntity.ok(ApiResponse.success("All institutional scores", scores));
    }

    // =====================================================================
    // Screener Endpoints
    // =====================================================================

    @GetMapping("/screeners/fii-accumulation")
    public ResponseEntity<ApiResponse<List<ScreenerResultDTO>>> screenerFiiAccumulation() {
        List<ScreenerResultDTO> results = screenerService.screenerFiiAccumulation();
        return ResponseEntity.ok(ApiResponse.success("FII accumulation screen results", results));
    }

    @GetMapping("/screeners/dii-accumulation")
    public ResponseEntity<ApiResponse<List<ScreenerResultDTO>>> screenerDiiAccumulation() {
        List<ScreenerResultDTO> results = screenerService.screenerDiiAccumulation();
        return ResponseEntity.ok(ApiResponse.success("DII accumulation screen results", results));
    }

    @GetMapping("/screeners/mf-accumulation")
    public ResponseEntity<ApiResponse<List<ScreenerResultDTO>>> screenerMutualFundAccumulation() {
        List<ScreenerResultDTO> results = screenerService.screenerMutualFundAccumulation();
        return ResponseEntity.ok(ApiResponse.success("Mutual Fund accumulation screen results", results));
    }

    @GetMapping("/screeners/institutional-strong-buy")
    public ResponseEntity<ApiResponse<List<ScreenerResultDTO>>> screenerInstitutionalStrongBuy() {
        List<ScreenerResultDTO> results = screenerService.screenerInstitutionalStrongBuy();
        return ResponseEntity.ok(ApiResponse.success("Institutional Strong Buy screen results", results));
    }

    @GetMapping("/screeners/institutional-price-action-buy")
    public ResponseEntity<ApiResponse<List<ScreenerResultDTO>>> screenerInstitutionalAndPriceAction() {
        List<ScreenerResultDTO> results = screenerService.screenerInstitutionalAndPriceAction();
        return ResponseEntity.ok(ApiResponse.success("Institutional + Price Action Buy screen results", results));
    }

    @GetMapping("/screeners/recent-bulk-deals")
    public ResponseEntity<ApiResponse<List<ScreenerResultDTO>>> screenerRecentBulkDeals() {
        List<ScreenerResultDTO> results = screenerService.screenerRecentBulkDeals();
        return ResponseEntity.ok(ApiResponse.success("Recent bulk deal stocks", results));
    }

    @GetMapping("/screeners/recent-block-deals")
    public ResponseEntity<ApiResponse<List<ScreenerResultDTO>>> screenerRecentBlockDeals() {
        List<ScreenerResultDTO> results = screenerService.screenerRecentBlockDeals();
        return ResponseEntity.ok(ApiResponse.success("Recent block deal stocks", results));
    }

    @GetMapping("/screeners/all")
    public ResponseEntity<ApiResponse<List<ScreenerResultDTO>>> screenerAll() {
        List<ScreenerResultDTO> results = screenerService.screenerAll();
        return ResponseEntity.ok(ApiResponse.success("All screeners combined", results));
    }
}
