package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.CreateTransactionRequest;
import org.example.dto.TransactionDTO;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.PortfolioTransaction;
import org.example.entity.Stock;
import org.example.entity.TransactionType;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioRepository;
import org.example.repository.PortfolioTransactionRepository;
import org.example.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Records buy/sell transactions as an immutable ledger and derives holding state
 * (quantity, avg cost) plus realized P&L from the remaining transactions.
 *
 * Existing {@link PortfolioService} holding CRUD (addHolding/updateHolding/removeHolding)
 * is reused as the source of truth for the derived holding state so snapshots and
 * Stock sync stay in sync. The ledger is authoritative.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PortfolioTransactionService {

    private static final int AVG_MATH_SCALE = 4;
    private static final int STORE_SCALE = 2;
    private static final int PNL_SCALE = 2;

    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final PortfolioSnapshotService snapshotService;

    // ═══════════════════════════════════════════════════════════════════
    // Record transactions
    // ═══════════════════════════════════════════════════════════════════

    public TransactionDTO recordBuy(Long portfolioId, Long stockId, Integer quantity,
                                     BigDecimal price, BigDecimal fees, LocalDate date, String notes) {
        validateCommon(quantity, price);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + stockId));

        BigDecimal feeAmount = fees != null ? fees : BigDecimal.ZERO;
        Optional<PortfolioHolding> existing = holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId);

        if (existing.isPresent()) {
            PortfolioHolding holding = existing.get();
            // CSV-imported holdings may have null avgPrice / zero qty. Treat as a fresh
            // position so the transaction price becomes the cost basis (not blended with 0).
            if (holding.getAvgPrice() == null
                    || holding.getQuantity() == null || holding.getQuantity() == 0) {
                BigDecimal perShareFee = feeAmount.divide(
                        BigDecimal.valueOf(quantity), AVG_MATH_SCALE, RoundingMode.HALF_UP);
                BigDecimal effectiveAvg = price.add(perShareFee)
                        .setScale(STORE_SCALE, RoundingMode.HALF_UP);
                holdingRepository.delete(holding);
                portfolioService.addHolding(portfolioId, stockId, quantity, effectiveAvg);
            } else {
                int qty0 = holding.getQuantity();
                BigDecimal avg0 = holding.getAvgPrice();
                int newQty = qty0 + quantity;
                // Blended avg cost: (oldCost + newCostWithFees) / totalQty, scale 4 then store scale 2.
                BigDecimal oldCost = avg0.multiply(BigDecimal.valueOf(qty0));
                BigDecimal newCost = price.multiply(BigDecimal.valueOf(quantity)).add(feeAmount);
                BigDecimal newAvg = oldCost.add(newCost)
                        .divide(BigDecimal.valueOf(newQty), AVG_MATH_SCALE, RoundingMode.HALF_UP)
                        .setScale(STORE_SCALE, RoundingMode.HALF_UP);
                portfolioService.updateHolding(portfolioId, holding.getId(), newQty, newAvg);
            }
        } else {
            BigDecimal perShareFee = feeAmount.divide(BigDecimal.valueOf(quantity), AVG_MATH_SCALE, RoundingMode.HALF_UP);
            BigDecimal effectiveAvg = price.add(perShareFee)
                    .setScale(STORE_SCALE, RoundingMode.HALF_UP);
            portfolioService.addHolding(portfolioId, stockId, quantity, effectiveAvg);
        }

        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setPortfolio(portfolio);
        tx.setStock(stock);
        tx.setType(TransactionType.BUY);
        tx.setQuantity(quantity);
        tx.setPrice(price);
        tx.setFees(feeAmount);
        tx.setRealizedPnl(null);
        tx.setTransactionDate(date != null ? date : LocalDate.now());
        tx.setNotes(notes);
        tx = transactionRepository.save(tx);
        return toDTO(tx);
    }

    public TransactionDTO recordSell(Long portfolioId, Long stockId, Integer quantity,
                                      BigDecimal price, BigDecimal fees, LocalDate date, String notes) {
        validateCommon(quantity, price);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + stockId));

        PortfolioHolding holding = holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No holding found for stock " + stock.getSymbol() + " in this portfolio"));

        if (quantity > holding.getQuantity()) {
            throw new IllegalArgumentException(
                    "Cannot sell " + quantity + " shares; only " + holding.getQuantity() + " held");
        }

        BigDecimal feeAmount = fees != null ? fees : BigDecimal.ZERO;
        BigDecimal avgPrice = holding.getAvgPrice() != null ? holding.getAvgPrice() : BigDecimal.ZERO;
        // realizedPnl = (price - avgPrice) * qty - fees
        BigDecimal realizedPnl = price.subtract(avgPrice)
                .multiply(BigDecimal.valueOf(quantity))
                .subtract(feeAmount)
                .setScale(PNL_SCALE, RoundingMode.HALF_UP);

        int newQty = holding.getQuantity() - quantity;
        if (newQty == 0) {
            portfolioService.removeHolding(portfolioId, holding.getId());
        } else {
            // avgPrice retained on partial sell
            portfolioService.updateHolding(portfolioId, holding.getId(), newQty, avgPrice);
        }

        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setPortfolio(portfolio);
        tx.setStock(stock);
        tx.setType(TransactionType.SELL);
        tx.setQuantity(quantity);
        tx.setPrice(price);
        tx.setFees(feeAmount);
        tx.setRealizedPnl(realizedPnl);
        tx.setTransactionDate(date != null ? date : LocalDate.now());
        tx.setNotes(notes);
        tx = transactionRepository.save(tx);
        return toDTO(tx);
    }

    public void deleteTransaction(Long id) {
        PortfolioTransaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));

        Long portfolioId = tx.getPortfolio().getId();
        Long stockId = tx.getStock().getId();

        // Remove the row (append-only semantics preserved: we recompute from the remainder)
        transactionRepository.delete(tx);

        // Replay the REMAINING ledger in chronological order (date+id asc) using the
        // exact same semantics as recordBuy/recordSell so the reconstructed holding
        // matches the original state exactly.
        List<PortfolioTransaction> remaining =
                transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(portfolioId, stockId);

        int qty = 0;
        BigDecimal avg = BigDecimal.ZERO;
        boolean hasHolding = false;

        for (PortfolioTransaction t : remaining) {
            if (t.getType() == TransactionType.BUY) {
                int q = t.getQuantity();
                BigDecimal fee = t.getFees() != null ? t.getFees() : BigDecimal.ZERO;
                int newQty = qty + q;
                if (!hasHolding) {
                    BigDecimal perShareFee = fee.divide(
                            BigDecimal.valueOf(q), AVG_MATH_SCALE, RoundingMode.HALF_UP);
                    avg = t.getPrice().add(perShareFee)
                            .setScale(STORE_SCALE, RoundingMode.HALF_UP);
                    hasHolding = true;
                } else {
                    BigDecimal oldCost = avg.multiply(BigDecimal.valueOf(qty));
                    BigDecimal newCost = t.getPrice().multiply(BigDecimal.valueOf(q)).add(fee);
                    avg = oldCost.add(newCost)
                            .divide(BigDecimal.valueOf(newQty), AVG_MATH_SCALE, RoundingMode.HALF_UP)
                            .setScale(STORE_SCALE, RoundingMode.HALF_UP);
                }
                qty = newQty;
            } else { // SELL — holding qty reduced, avg retained
                qty -= t.getQuantity();
            }
        }

        if (!hasHolding || qty <= 0) {
            holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
                    .ifPresent(h -> {
                        portfolioService.removeHolding(portfolioId, h.getId());
                        resetStockWhenHoldingRemoved(stockId);
                    });
            return;
        }

        recomputeHolding(portfolioId, stockId, qty, avg);
    }

    /**
     * Resets the Stock row's quantity/avgPrice after a portfolio holding has been removed,
     * recomputing from the OTHER remaining portfolios' holdings (or zeroing if none),
     * so the Stock entity never shows stale values.
     */
    private void resetStockWhenHoldingRemoved(Long stockId) {
        Stock stock = stockRepository.findById(stockId).orElse(null);
        if (stock == null) return;
        List<PortfolioHolding> otherHoldings = holdingRepository.findByStockId(stockId);
        if (otherHoldings.isEmpty()) {
            stock.setQuantity(0);
            stock.setAvgPrice(null);
        } else {
            int totalQty = otherHoldings.stream()
                    .filter(h -> h.getQuantity() != null)
                    .mapToInt(PortfolioHolding::getQuantity)
                    .sum();
            BigDecimal weightedSum = BigDecimal.ZERO;
            for (PortfolioHolding h : otherHoldings) {
                if (h.getQuantity() != null && h.getAvgPrice() != null) {
                    weightedSum = weightedSum.add(
                            h.getAvgPrice().multiply(BigDecimal.valueOf(h.getQuantity())));
                }
            }
            stock.setQuantity(totalQty > 0 ? totalQty : 0);
            stock.setAvgPrice(totalQty > 0
                    ? weightedSum.divide(BigDecimal.valueOf(totalQty), STORE_SCALE, RoundingMode.HALF_UP)
                    : null);
        }
        stockRepository.save(stock);
    }

    /**
     * Upserts the derived holding from the recomputed ledger. netQty/newAvg are passed
     * as values (effectively final) so they can be safely used inside lambdas.
     */
    private void recomputeHolding(Long portfolioId, Long stockId, int netQty, BigDecimal newAvg) {
        holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
                .ifPresentOrElse(
                        h -> portfolioService.updateHolding(portfolioId, h.getId(), netQty, newAvg),
                        () -> portfolioService.addHolding(portfolioId, stockId, netQty, newAvg));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Read
    // ═══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<TransactionDTO> getTransactions(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        return transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionDTO> getTransactions(Long portfolioId, Long stockId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        return transactionRepository.findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(portfolioId, stockId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionDTO> getAllTransactionsByStock(Long stockId) {
        return transactionRepository.findByStockIdOrderByTransactionDateAscIdAsc(stockId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private void validateCommon(Integer quantity, BigDecimal price) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be zero or positive");
        }
    }

    private TransactionDTO toDTO(PortfolioTransaction tx) {
        Stock stock = tx.getStock();
        return TransactionDTO.builder()
                .id(tx.getId())
                .portfolioId(tx.getPortfolio() != null ? tx.getPortfolio().getId() : null)
                .stockId(stock != null ? stock.getId() : null)
                .stockSymbol(stock != null ? stock.getSymbol() : null)
                .stockName(stock != null ? stock.getName() : null)
                .type(tx.getType())
                .quantity(tx.getQuantity())
                .price(tx.getPrice())
                .fees(tx.getFees())
                .realizedPnl(tx.getRealizedPnl())
                .transactionDate(tx.getTransactionDate())
                .notes(tx.getNotes())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }
}
