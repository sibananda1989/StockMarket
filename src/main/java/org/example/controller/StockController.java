package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.*;
import org.example.entity.Stock;
import org.example.event.StockCreatedEvent;
import org.example.repository.StockRepository;
import org.example.service.DailyPriceService;
import org.example.service.PortfolioSnapshotService;
import org.example.service.StockService;
import org.example.service.TechnicalAnalysisService;
import org.example.service.YahooFinanceService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final DailyPriceService dailyPriceService;
    private final StockService stockService;
    private final PortfolioSnapshotService snapshotService;
    private final YahooFinanceService yahooFinanceService;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StockRepository stockRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockDTO>>> getAllStocks() {
        return ResponseEntity.ok(ApiResponse.success(stockService.getAllStockDTOs()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockDTO>> createStock(@Valid @RequestBody StockDTO dto) {
        Stock stock = new Stock(dto.getSymbol(), dto.getName(), dto.getSector(), dto.getIndustry());
        stock.setYahooSymbol(dto.getYahooSymbol() != null && !dto.getYahooSymbol().isBlank()
                ? dto.getYahooSymbol() : dto.getSymbol());
        Stock saved = stockService.addStock(stock);
        StockDTO savedDto = stockService.getStockDTO(saved);
        applicationEventPublisher.publishEvent(new StockCreatedEvent(this, saved.getId()));
        return ResponseEntity.ok(ApiResponse.success("Stock created successfully", savedDto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StockDTO>> updateStock(@PathVariable Long id,
                                                              @Valid @RequestBody StockDTO dto) {
        Stock existing = stockService.getStockById(id);
        existing.setSymbol(dto.getSymbol());
        existing.setName(dto.getName());
        existing.setSector(dto.getSector());
        existing.setIndustry(dto.getIndustry());
        Stock updated = stockService.updateStock(id, existing);
        StockDTO updatedDto = stockService.getStockDTO(updated);
        return ResponseEntity.ok(ApiResponse.success("Stock updated successfully", updatedDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStock(@PathVariable Long id) {
        stockService.deleteStock(id);
        return ResponseEntity.ok(ApiResponse.success("Stock deleted successfully", null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockDetailsDTO>> getStockById(@PathVariable Long id) {
        Stock stock = stockService.getStockById(id);
        StockDTO stockDto = stockService.getStockDTO(stock);
        List<DailyPriceDTO> recentPrices = dailyPriceService.getPriceHistoryDTO(id,
                LocalDate.now().minusDays(90), LocalDate.now());
        List<RsiDTO> recentRsiValues = stockService.getRecentRsiValues(id, 90);
        StockDetailsDTO details = new StockDetailsDTO(stockDto, recentPrices, recentRsiValues);
        return ResponseEntity.ok(ApiResponse.success(details));
    }

    @GetMapping("/validate-symbol")
    public ResponseEntity<ApiResponse<SymbolValidationResult>> validateSymbol(
            @RequestParam String symbol) {
        SymbolValidationResult result = yahooFinanceService.validateSymbol(symbol.trim().toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/recalculate-portfolio")
    @CacheEvict(cacheNames = {"signals", "signalDto"}, allEntries = true)
    public ResponseEntity<ApiResponse<String>> recalculateAllPortfolios() {
        int count = stockService.recalculateAllPortfolios();
        return ResponseEntity.ok(ApiResponse.success("Portfolio recalculated for " + count + " stocks", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StockSearchResultDTO>>> searchStocksByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "10") int limit) {
        List<StockSearchResultDTO> results = yahooFinanceService.searchByName(name, limit);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PostMapping("/csv-import")
    public ResponseEntity<ApiResponse<List<StockDTO>>> csvImport(@RequestBody List<CsvImportRequest> requests) {
        List<StockDTO> results = requests.stream().map(req -> {
            String trimmedSymbol = req.getSymbol() != null ? req.getSymbol().trim() : null;
            boolean isNew = trimmedSymbol != null && !stockRepository.existsBySymbol(trimmedSymbol);
            Stock stock = stockService.getOrCreateStock(req.getSymbol(), req.getName(), req.getSector(), req.getIndustry());
            stock.setQuantity(req.getQuantity());
            stock.setAvgPrice(req.getAvgPrice());
            if (req.getLastTradedPrice() != null) {
                stock.setLastTradedPrice(req.getLastTradedPrice());
            }
            stockService.recalculatePortfolio(stock);
            stockService.syncPortfolioHoldings(stock);
            if (isNew) {
                applicationEventPublisher.publishEvent(new StockCreatedEvent(this, stock.getId()));
            }
            return stockService.getStockDTO(stock);
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @GetMapping("/{id}/daily-prices")
    public ResponseEntity<ApiResponse<List<DailyPriceDTO>>> getDailyPrices(
            @PathVariable Long id,
            @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(ApiResponse.success(dailyPriceService.getPriceHistoryDTO(id, 
            LocalDate.now().minusDays(days), LocalDate.now())));
    }

    @PatchMapping("/{id}/portfolio")
    public ResponseEntity<ApiResponse<StockDTO>> updatePortfolio(
            @PathVariable Long id,
            @RequestBody CsvImportRequest request) {
        Stock updated = stockService.updatePortfolioData(id, request);
        StockDTO dto = stockService.getStockDTO(updated);
        return ResponseEntity.ok(ApiResponse.success("Portfolio updated successfully", dto));
    }

    @GetMapping("/{id}/snapshots")
    public ResponseEntity<ApiResponse<List<PortfolioSnapshotDTO>>> getSnapshots(
            @PathVariable Long id,
            @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getLastN(id, days)));
    }

    @GetMapping("/{id}/snapshots/all")
    public ResponseEntity<ApiResponse<List<PortfolioSnapshotDTO>>> getAllSnapshots(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getHistory(id)));
    }

    @DeleteMapping("/by-prefix/{prefix}")
    public ResponseEntity<ApiResponse<String>> deleteBySymbolPrefix(@PathVariable String prefix) {
        int count = stockService.deleteStocksBySymbolPrefix(prefix);
        return ResponseEntity.ok(ApiResponse.success(
                "Deleted " + count + " stock(s) with symbol prefix '" + prefix + "'", null));
    }

    @GetMapping("/sectors")
    public ResponseEntity<ApiResponse<List<String>>> getAllSectors() {
        List<String> sectors = stockRepository.findDistinctSectors();
        return ResponseEntity.ok(ApiResponse.success(sectors));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("OK", null));
    }
}
