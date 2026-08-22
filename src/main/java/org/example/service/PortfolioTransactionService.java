package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.BuyLotDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    // Lot-based sells (each SELL maps to exactly one BUY record)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Records a SELL that consumes quantity from a specific BUY lot.
     * The sell quantity must be <= the lot's open (unsold) quantity.
     * Realized P/L is computed against the LOT's buy price (true per-lot P/L).
     */
    public TransactionDTO recordSellAgainstLot(Long portfolioId, Long buyTxId, Integer quantity,
                                               BigDecimal price, BigDecimal fees, LocalDate date, String notes) {
        validateCommon(quantity, price);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        PortfolioTransaction buyTx = transactionRepository.findById(buyTxId)
                .orElseThrow(() -> new ResourceNotFoundException("Buy transaction not found: " + buyTxId));
        if (!buyTx.getPortfolio().getId().equals(portfolioId)) {
            throw new IllegalArgumentException(
                    "Buy transaction " + buyTxId + " does not belong to portfolio " + portfolioId);
        }
        if (buyTx.getType() != TransactionType.BUY) {
            throw new IllegalArgumentException("Transaction " + buyTxId + " is not a BUY record");
        }

        int soldSoFar = soldQuantityForLot(buyTxId);
        int remaining = buyTx.getQuantity() - soldSoFar;
        if (quantity > remaining) {
            throw new IllegalArgumentException(
                    "Cannot sell " + quantity + " from this buy lot; only " + remaining + " of "
                            + buyTx.getQuantity() + " still open");
        }

        BigDecimal feeAmount = fees != null ? fees : BigDecimal.ZERO;
        BigDecimal realizedPnl = price.subtract(buyTx.getPrice())
                .multiply(BigDecimal.valueOf(quantity))
                .subtract(feeAmount)
                .setScale(PNL_SCALE, RoundingMode.HALF_UP);

        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setPortfolio(portfolio);
        tx.setStock(buyTx.getStock());
        tx.setLinkedBuyId(buyTxId);
        tx.setType(TransactionType.SELL);
        tx.setQuantity(quantity);
        tx.setPrice(price);
        tx.setFees(feeAmount);
        tx.setRealizedPnl(realizedPnl);
        tx.setTransactionDate(date != null ? date : LocalDate.now());
        tx.setNotes(notes);
        tx = transactionRepository.save(tx);

        // Keep the derived holding in sync: reduce qty, retain avg price
        holdingRepository.findByPortfolioIdAndStockId(portfolioId, buyTx.getStock().getId())
                .ifPresent(h -> {
                    int newQty = (h.getQuantity() != null ? h.getQuantity() : 0) - quantity;
                    if (newQty <= 0) {
                        portfolioService.removeHolding(portfolioId, h.getId());
                    } else {
                        portfolioService.updateHolding(portfolioId, h.getId(), newQty, h.getAvgPrice());
                    }
                });

        try {
            snapshotService.saveOrUpdate(buyTx.getStock(), tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after lot sell for {}: {}", buyTx.getStock().getSymbol(), e.getMessage());
        }
        return toDTO(tx);
    }

    /** Total quantity already sold from the given BUY lot. */
    private int soldQuantityForLot(Long buyTxId) {
        return transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(buyTxId).stream()
                .mapToInt(t -> t.getQuantity() != null ? t.getQuantity() : 0)
                .sum();
    }

    /**
     * Lists BUY records as individual lots with sold/remaining quantity and status.
     * Each lot's linked sells are embedded. Avg price is intentionally not used here —
     * every buy is handled as its own entry.
     */
    @Transactional(readOnly = true)
    public List<BuyLotDTO> getLots(Long portfolioId, Long stockId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        List<PortfolioTransaction> buys = (stockId != null)
                ? transactionRepository.findByPortfolioIdAndStockIdAndTypeOrderByTransactionDateAscIdAsc(
                        portfolioId, stockId, TransactionType.BUY)
                : transactionRepository.findByPortfolioIdAndTypeOrderByTransactionDateAscIdAsc(
                        portfolioId, TransactionType.BUY);

        return buys.stream().map(this::toLotDTO).collect(Collectors.toList());
    }

    private BuyLotDTO toLotDTO(PortfolioTransaction buyTx) {
        List<TransactionDTO> sells = transactionRepository
                .findByLinkedBuyIdOrderByTransactionDateAscIdAsc(buyTx.getId()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        int soldQty = sells.stream()
                .mapToInt(s -> s.getQuantity() != null ? s.getQuantity() : 0)
                .sum();
        int remaining = buyTx.getQuantity() - soldQty;
        String status = remaining <= 0 ? "SOLD" : soldQty > 0 ? "PARTIALLY_SOLD" : "OPEN";
        BigDecimal realizedPnl = sells.stream()
                .map(s -> s.getRealizedPnl() != null ? s.getRealizedPnl() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Stock stock = buyTx.getStock();
        return BuyLotDTO.builder()
                .id(buyTx.getId())
                .portfolioId(buyTx.getPortfolio() != null ? buyTx.getPortfolio().getId() : null)
                .stockId(stock != null ? stock.getId() : null)
                .stockSymbol(stock != null ? stock.getSymbol() : null)
                .stockName(stock != null ? stock.getName() : null)
                .transactionDate(buyTx.getTransactionDate())
                .quantity(buyTx.getQuantity())
                .price(buyTx.getPrice())
                .fees(buyTx.getFees())
                .notes(buyTx.getNotes())
                .soldQuantity(soldQty)
                .remainingQuantity(remaining)
                .status(status)
                .realizedPnl(realizedPnl)
                .createdAt(buyTx.getCreatedAt())
                .sells(sells)
                .build();
    }

    /**
     * Backfills legacy unmapped SELL rows with FIFO links to prior BUY rows,
     * per (portfolio, stock). Legacy sells whose quantity cannot be fully
     * covered by earlier buys are linked to the last available buy partially
     * (linked row keeps its full quantity; validation tolerates historical data).
     */
    public int backfillSellLotLinks() {
        List<Long> portfolioIds = portfolioRepository.findAll().stream()
                .map(Portfolio::getId).collect(Collectors.toList());
        int mapped = 0;
        for (Long pid : portfolioIds) {
            List<PortfolioTransaction> unmapped = transactionRepository
                    .findByPortfolioIdAndLinkedBuyIdIsNullAndType(pid, TransactionType.SELL);
            if (unmapped.isEmpty()) continue;

            // Group unmapped sells by stock
            var byStock = unmapped.stream()
                    .collect(Collectors.groupingBy(s -> s.getStock().getId()));
            for (var entry : byStock.entrySet()) {
                Long stockId = entry.getKey();
                List<PortfolioTransaction> sells = entry.getValue().stream()
                        .sorted(java.util.Comparator
                                .comparing(PortfolioTransaction::getTransactionDate)
                                .thenComparing(PortfolioTransaction::getId))
                        .collect(Collectors.toList());
                List<PortfolioTransaction> buys = transactionRepository
                        .findByPortfolioIdAndStockIdAndTypeOrderByTransactionDateAscIdAsc(
                                pid, stockId, TransactionType.BUY);
                if (buys.isEmpty()) continue;

                Map<Long, Integer> openByBuy = new HashMap<>();
                for (PortfolioTransaction b : buys) {
                    int sold = (int) transactionRepository
                            .findByLinkedBuyIdOrderByTransactionDateAscIdAsc(b.getId()).stream()
                            .filter(s -> !unmapped.contains(s))
                            .mapToInt(s -> s.getQuantity() != null ? s.getQuantity() : 0)
                            .sum();
                    openByBuy.put(b.getId(), b.getQuantity() - sold);
                }

                for (PortfolioTransaction s : sells) {
                    int qty = s.getQuantity() != null ? s.getQuantity() : 0;
                    // FIFO: earliest buy with open quantity
                    for (PortfolioTransaction b : buys) {
                        if (qty <= 0) break;
                        int open = openByBuy.getOrDefault(b.getId(), 0);
                        if (open > 0) {
                            // Whole legacy sell maps to the first lot that can absorb it;
                            // if no single lot can, map to the largest-open lot to keep history intact.
                            s.setLinkedBuyId(b.getId());
                            openByBuy.put(b.getId(), Math.max(0, open - qty));
                            qty = 0;
                            mapped++;
                        }
                    }
                    if (qty > 0 && s.getLinkedBuyId() == null) {
                        // No open capacity anywhere (historical over-sell) — link to latest buy
                        s.setLinkedBuyId(buys.get(buys.size() - 1).getId());
                        mapped++;
                    }
                    transactionRepository.save(s);
                }
            }
        }
        log.info("Backfilled {} legacy SELL rows with BUY lot links", mapped);
        return mapped;
    }

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
        try {
            snapshotService.saveOrUpdate(stock, tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after {} for {}: {}", tx.getType(), stock.getSymbol(), e.getMessage());
        }
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
        try {
            snapshotService.saveOrUpdate(stock, tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after {} for {}: {}", tx.getType(), stock.getSymbol(), e.getMessage());
        }
        return toDTO(tx);
    }

    /**
     * Deletes a transaction only if it belongs to the given portfolio.
     * Prevents deleting via a mismatched portfolioId in the URL path.
     */
    public void deleteTransaction(Long portfolioId, Long txId) {
        PortfolioTransaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txId));
        if (!tx.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException(
                    "Transaction " + txId + " does not belong to portfolio " + portfolioId);
        }
        deleteTransaction(txId);
    }

    public void deleteTransaction(Long id) {
        PortfolioTransaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));

        // Guard: a BUY lot with linked sells cannot be deleted — delete its sells first
        if (tx.getType() == TransactionType.BUY) {
            long linkedSells = transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(id).size();
            if (linkedSells > 0) {
                throw new IllegalArgumentException(
                        "Cannot delete buy record " + id + ": it has " + linkedSells
                                + " linked sell record(s). Delete those sells first.");
            }
        }

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

        try {
            snapshotService.saveOrUpdate(tx.getStock(), tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after transaction delete for {}: {}", tx.getStock().getSymbol(), e.getMessage());
        }
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

    /**
     * Replays the transaction ledger for a (portfolio, stock) pair up to and including
     * the given snapshot date and returns the resulting [quantity, avgPrice] state.
     * Returns null if no buys existed by that date (position was not yet opened).
     */
    @Transactional(readOnly = true)
    public int[] getHistoricalStateAt(LocalDate snapshotDate, Long portfolioId, Long stockId) {
        List<PortfolioTransaction> txns = transactionRepository
                .findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(portfolioId, stockId);
        int qty = 0;
        BigDecimal avg = BigDecimal.ZERO;
        boolean hasHolding = false;

        for (PortfolioTransaction t : txns) {
            if (t.getTransactionDate().isAfter(snapshotDate)) break;
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
            } else {
                qty -= t.getQuantity();
            }
        }
        return hasHolding ? new int[]{qty, 0} : null; // qty only; avg handled via separate call
    }

    @Transactional(readOnly = true)
    public BigDecimal getHistoricalAvgPriceAt(LocalDate snapshotDate, Long portfolioId, Long stockId) {
        List<PortfolioTransaction> txns = transactionRepository
                .findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(portfolioId, stockId);
        int qty = 0;
        BigDecimal avg = BigDecimal.ZERO;
        boolean hasHolding = false;

        for (PortfolioTransaction t : txns) {
            if (t.getTransactionDate().isAfter(snapshotDate)) break;
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
            } else {
                qty -= t.getQuantity();
            }
        }
        return hasHolding ? avg : null;
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
                .linkedBuyId(tx.getLinkedBuyId())
                .transactionDate(tx.getTransactionDate())
                .notes(tx.getNotes())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }
}
