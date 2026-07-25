package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.*;
import org.example.dto.CreateTransactionRequest;
import org.example.dto.TransactionDTO;
import org.example.service.PortfolioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioManagementController {

    private final PortfolioService portfolioService;
    private final org.example.service.PortfolioTransactionService transactionService;

    public PortfolioManagementController(PortfolioService portfolioService,
            org.example.service.PortfolioTransactionService transactionService) {
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
    }

    @GetMapping("/all/holdings/stock/{stockId}")
    public ResponseEntity<ApiResponse<HoldingDTO>> getAggregateHolding(@PathVariable Long stockId) {
        HoldingDTO dto = portfolioService.getAggregateHolding(stockId);
        return ResponseEntity.ok(ApiResponse.success("Aggregate holding", dto));
    }

    // ══════════════════════════════════════════════════════════════════
    // Portfolio CRUD
    // ══════════════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<ApiResponse<List<PortfolioDTO>>> listPortfolios() {
        List<PortfolioDTO> portfolios = portfolioService.getAllPortfolios();
        return ResponseEntity.ok(ApiResponse.success("Portfolios", portfolios));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioDTO>> createPortfolio(
            @Valid @RequestBody CreatePortfolioRequest request) {
        PortfolioDTO dto = portfolioService.createPortfolio(request.getName(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Portfolio created", dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PortfolioDTO>> getPortfolio(@PathVariable Long id) {
        PortfolioDTO dto = portfolioService.getPortfolio(id);
        return ResponseEntity.ok(ApiResponse.success("Portfolio details", dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PortfolioDTO>> updatePortfolio(
            @PathVariable Long id,
            @Valid @RequestBody CreatePortfolioRequest request) {
        PortfolioDTO dto = portfolioService.updatePortfolio(id, request.getName(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.success("Portfolio updated", dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deletePortfolio(@PathVariable Long id) {
        portfolioService.deletePortfolio(id);
        return ResponseEntity.ok(ApiResponse.success("Portfolio deleted"));
    }

    @GetMapping("/default")
    public ResponseEntity<ApiResponse<PortfolioDTO>> getDefaultPortfolio() {
        var defaultPort = portfolioService.getDefaultPortfolio();
        PortfolioDTO dto = portfolioService.getPortfolio(defaultPort.getId());
        return ResponseEntity.ok(ApiResponse.success("Default portfolio", dto));
    }

    // ══════════════════════════════════════════════════════════════════
    // Holdings
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/{id}/holdings")
    public ResponseEntity<ApiResponse<List<HoldingDTO>>> getHoldings(@PathVariable Long id) {
        List<HoldingDTO> holdings = portfolioService.getHoldings(id);
        return ResponseEntity.ok(ApiResponse.success("Holdings", holdings));
    }

    @PostMapping("/{id}/holdings")
    public ResponseEntity<ApiResponse<HoldingDTO>> addHolding(
            @PathVariable Long id,
            @Valid @RequestBody AddHoldingRequest request) {
        HoldingDTO dto = portfolioService.addHolding(id, request.getStockId(),
                request.getQuantity(), request.getAvgPrice());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Holding added", dto));
    }

    @PatchMapping("/{portfolioId}/holdings/{holdingId}")
    public ResponseEntity<ApiResponse<HoldingDTO>> updateHolding(
            @PathVariable Long portfolioId,
            @PathVariable Long holdingId,
            @RequestBody AddHoldingRequest request) {
        HoldingDTO dto = portfolioService.updateHolding(portfolioId, holdingId,
                request.getQuantity(), request.getAvgPrice());
        return ResponseEntity.ok(ApiResponse.success("Holding updated", dto));
    }

    @DeleteMapping("/{portfolioId}/holdings/{holdingId}")
    public ResponseEntity<ApiResponse<String>> removeHolding(
            @PathVariable Long portfolioId,
            @PathVariable Long holdingId) {
        portfolioService.removeHolding(portfolioId, holdingId);
        return ResponseEntity.ok(ApiResponse.success("Holding removed"));
    }

    @GetMapping("/{portfolioId}/holdings/stock/{stockId}")
    public ResponseEntity<ApiResponse<HoldingDTO>> getHolding(
            @PathVariable Long portfolioId,
            @PathVariable Long stockId) {
        HoldingDTO dto = portfolioService.getHolding(portfolioId, stockId);
        return ResponseEntity.ok(ApiResponse.success("Holding", dto));
    }

    @DeleteMapping("/{portfolioId}/holdings/stock/{stockId}")
    public ResponseEntity<ApiResponse<String>> removeHoldingByStockId(
            @PathVariable Long portfolioId,
            @PathVariable Long stockId) {
        portfolioService.removeHoldingByStockId(portfolioId, stockId);
        return ResponseEntity.ok(ApiResponse.success("Holding removed"));
    }

    @PostMapping("/{id}/recalculate")
    public ResponseEntity<ApiResponse<String>> recalculate(@PathVariable Long id) {
        int count = portfolioService.recalculatePortfolio(id);
        return ResponseEntity.ok(ApiResponse.success("Recalculated " + count + " holdings"));
    }

    @PostMapping("/{id}/transactions")
    public ResponseEntity<ApiResponse<TransactionDTO>> recordTransaction(
            @PathVariable Long id,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionDTO dto;
        Long stockId = request.getStockId();
        Integer quantity = request.getQuantity();
        java.math.BigDecimal price = request.getPrice();
        java.math.BigDecimal fees = request.getFees();
        java.time.LocalDate date = request.getTransactionDate();
        String notes = request.getNotes();
        if (request.getType() == org.example.entity.TransactionType.BUY) {
            dto = transactionService.recordBuy(id, stockId, quantity, price, fees, date, notes);
        } else {
            dto = transactionService.recordSell(id, stockId, quantity, price, fees, date, notes);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transaction recorded", dto));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactions(
            @PathVariable Long id,
            @RequestParam(required = false) Long stockId) {
        List<TransactionDTO> txns = (stockId != null)
                ? transactionService.getTransactions(id, stockId)
                : transactionService.getTransactions(id);
        return ResponseEntity.ok(ApiResponse.success("Transactions", txns));
    }

    @DeleteMapping("/{id}/transactions/{txId}")
    public ResponseEntity<ApiResponse<String>> deleteTransaction(
            @PathVariable Long id,
            @PathVariable Long txId) {
        transactionService.deleteTransaction(txId);
        return ResponseEntity.ok(ApiResponse.success("Transaction deleted"));
    }
}
