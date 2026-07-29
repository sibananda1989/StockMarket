package org.example.controller;

import jakarta.validation.Valid;
import org.example.exception.ResourceNotFoundException;
import org.example.dto.*;
import org.example.dto.CreateTransactionRequest;
import org.example.dto.TransactionDTO;
import org.example.entity.PortfolioHolding;
import org.example.repository.PortfolioHoldingRepository;
import org.example.service.PortfolioService;
import org.example.service.PortfolioTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioManagementController {

    private final PortfolioService portfolioService;
    private final PortfolioTransactionService transactionService;
    private final PortfolioHoldingRepository holdingRepository;

    public PortfolioManagementController(PortfolioService portfolioService,
            PortfolioTransactionService transactionService,
            PortfolioHoldingRepository holdingRepository) {
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.holdingRepository = holdingRepository;
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
        // Record as a buy transaction in the ledger
        transactionService.recordBuy(id, request.getStockId(), request.getQuantity(),
                request.getAvgPrice(), BigDecimal.ZERO, LocalDate.now(), null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Holding added", dto));
    }

    @PatchMapping("/{portfolioId}/holdings/{holdingId}")
    public ResponseEntity<ApiResponse<HoldingDTO>> updateHolding(
            @PathVariable Long portfolioId,
            @PathVariable Long holdingId,
            @RequestBody AddHoldingRequest request) {
        // First, get the current holding via repository to know old values
        PortfolioHolding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding not found: " + holdingId));
        if (!holding.getPortfolio().getId().equals(portfolioId)) {
            throw new IllegalArgumentException("Holding does not belong to this portfolio");
        }
        
        Integer oldQty = holding.getQuantity();
        BigDecimal oldAvg = holding.getAvgPrice();
        Long stockId = holding.getStock().getId();

        HoldingDTO updated = portfolioService.updateHolding(portfolioId, holdingId,
                request.getQuantity(), request.getAvgPrice());

        // Record transaction for quantity changes if oldQty exists
        if (oldQty != null && request.getQuantity() != null) {
            int qtyDiff = request.getQuantity() - oldQty;
            if (qtyDiff > 0) {
                // Buy increase
                BigDecimal priceToUse = request.getAvgPrice() != null ? request.getAvgPrice() : oldAvg;
                if (priceToUse != null) {
                    transactionService.recordBuy(portfolioId, stockId, qtyDiff, priceToUse,
                            BigDecimal.ZERO, LocalDate.now(), "Auto - quantity increase");
                }
            } else if (qtyDiff < 0) {
                // Sell reduction
                int sellQty = Math.abs(qtyDiff);
                if (oldAvg != null) {
                    transactionService.recordSell(portfolioId, stockId, sellQty, oldAvg,
                            BigDecimal.ZERO, LocalDate.now(), "Auto - quantity reduction");
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.success("Holding updated", updated));
    }

    @DeleteMapping("/{portfolioId}/holdings/{holdingId}")
    public ResponseEntity<ApiResponse<String>> removeHolding(
            @PathVariable Long portfolioId,
            @PathVariable Long holdingId) {
        // Fetch holding details before deletion to record sell transaction
        PortfolioHolding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding not found: " + holdingId));
        if (!holding.getPortfolio().getId().equals(portfolioId)) {
            throw new IllegalArgumentException("Holding does not belong to this portfolio");
        }

        Long stockId = holding.getStock().getId();
        Integer quantity = holding.getQuantity();
        BigDecimal avgPrice = holding.getAvgPrice();

        // Record sell transaction before removal
        if (quantity != null && quantity > 0 && avgPrice != null) {
            transactionService.recordSell(portfolioId, stockId, quantity, avgPrice,
                    BigDecimal.ZERO, LocalDate.now(), "Auto - position removed");
        }

        // Now let Service handle deletion (including snapshot before delete)
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
        // Fetch holding details before deletion to record sell transaction
        PortfolioHolding holding = holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Holding not found for portfolio " + portfolioId + " and stock " + stockId));

        Integer quantity = holding.getQuantity();
        BigDecimal avgPrice = holding.getAvgPrice();

        // Record sell transaction before removal
        if (quantity != null && quantity > 0 && avgPrice != null) {
            transactionService.recordSell(portfolioId, stockId, quantity, avgPrice,
                    BigDecimal.ZERO, LocalDate.now(), "Auto - position removed");
        }

        // Now let Service handle deletion (including snapshot before delete)
        portfolioService.removeHoldingByStockId(portfolioId, stockId);

        return ResponseEntity.ok(ApiResponse.success("Holding removed"));
    }

    @PostMapping("/{id}/recalculate")
    public ResponseEntity<ApiResponse<String>> recalculate(@PathVariable Long id) {
        int count = portfolioService.recalculatePortfolio(id);
        return ResponseEntity.ok(ApiResponse.success("Recalculated " + count + " holdings"));
    }

    // ──────────────────────────────────────────────────────────────
    // Transactions (ledger)
    // ──────────────────────────────────────────────────────────────

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

    @GetMapping("/all/transactions-by-stock")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getAllTransactionsByStock(
            @RequestParam Long stockId) {
        List<TransactionDTO> txns = transactionService.getAllTransactionsByStock(stockId);
        return ResponseEntity.ok(ApiResponse.success("All transactions across all portfolios", txns));
    }
}
