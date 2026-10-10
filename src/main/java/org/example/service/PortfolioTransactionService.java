package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.BuyLotDTO;
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
import org.example.service.PortfolioDailyValueService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * The ledger is authoritative; {@link PortfolioHolding} rows are derived projections
 * recomputed by {@link PortfolioPositionReplayer} after every mutation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PortfolioTransactionService {

    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final PortfolioPositionReplayer replayer;
    private final PortfolioSnapshotWriter snapshotWriter;
    private final PortfolioDailyValueService dailyValueService;

    // ═══════════════════════════════════════════════════════════════════
    // Lot-based sells (each SELL maps to exactly one BUY record)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Records a SELL that consumes quantity from a specific BUY lot.
     * The sell quantity must be <= the lot's open (unsold) quantity.
     * Realized P/L is computed against the LOT's buy price (true per-lot P/L).
     */
    @CacheEvict(cacheNames = {"signals", "signalDto", "portfolioHistory"}, allEntries = true)
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
                .setScale(2, java.math.RoundingMode.HALF_UP);

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

        // Validate holding exists before reducing it — throw if missing (was silent skip)
        Long stockId = buyTx.getStock().getId();
        PortfolioHolding holding = holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No holding found for stock " + buyTx.getStock().getSymbol() + " in portfolio " + portfolioId));

        int newQty = (holding.getQuantity() != null ? holding.getQuantity() : 0) - quantity;
        if (newQty <= 0) {
            portfolioService.removeHolding(portfolioId, holding.getId());
        } else {
            portfolioService.updateHolding(portfolioId, holding.getId(), newQty, holding.getAvgPrice());
        }

        try {
            snapshotWriter.saveSnapshots(buyTx.getStock(), tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after lot sell for {}: {}", buyTx.getStock().getSymbol(), e.getMessage());
        }
        try {
            LocalDate txDate = tx.getTransactionDate();
            if (txDate != null && txDate.isBefore(LocalDate.now())) {
                dailyValueService.rebuildFromDate(portfolioId, txDate);
            } else {
                dailyValueService.upsertForDate(portfolioId, txDate != null ? txDate : LocalDate.now());
            }
        } catch (Exception e) {
            log.warn("Daily value rebuild failed after lot SELL for portfolio {}: {}", portfolioId, e.getMessage());
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
     * per (portfolio, stock).
     */
    public int backfillSellLotLinks() {
        List<Long> portfolioIds = portfolioRepository.findAll().stream()
                .map(Portfolio::getId).collect(Collectors.toList());
        int mapped = 0;
        for (Long pid : portfolioIds) {
            List<PortfolioTransaction> unmapped = transactionRepository
                    .findByPortfolioIdAndLinkedBuyIdIsNullAndType(pid, TransactionType.SELL);
            if (unmapped.isEmpty()) continue;

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
                    for (PortfolioTransaction b : buys) {
                        if (qty <= 0) break;
                        int open = openByBuy.getOrDefault(b.getId(), 0);
                        if (open > 0) {
                            s.setLinkedBuyId(b.getId());
                            openByBuy.put(b.getId(), Math.max(0, open - qty));
                            qty = 0;
                            mapped++;
                        }
                    }
                    if (qty > 0 && s.getLinkedBuyId() == null) {
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
    // Record transactions — single-source-of-truth via replayer
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Records a BUY transaction. Appends the ledger row, then replays the full
     * portfolio to derive and persist all holding projections.
     */
    @CacheEvict(cacheNames = {"signals", "signalDto", "portfolioHistory"}, allEntries = true)
    public TransactionDTO recordBuy(Long portfolioId, Long stockId, Integer quantity,
                                     BigDecimal price, BigDecimal fees, LocalDate date, String notes) {
        validateCommon(quantity, price);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + stockId));

        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setPortfolio(portfolio);
        tx.setStock(stock);
        tx.setType(TransactionType.BUY);
        tx.setQuantity(quantity);
        tx.setPrice(price);
        tx.setFees(fees != null ? fees : BigDecimal.ZERO);
        tx.setRealizedPnl(null);
        tx.setTransactionDate(date != null ? date : LocalDate.now());
        tx.setNotes(notes);
        tx = transactionRepository.save(tx);

        syncHoldingsFromReplay(portfolioId, stock);
        try {
            snapshotWriter.saveSnapshots(stock, tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after BUY for {}: {}", stock.getSymbol(), e.getMessage());
        }
        // auto forward-rebuild for portfolio_daily_values (R1-R8) — back-dated vs today
        try {
            LocalDate txDate = tx.getTransactionDate();
            if (txDate != null && txDate.isBefore(LocalDate.now())) {
                dailyValueService.rebuildFromDate(portfolioId, txDate);
            } else {
                dailyValueService.upsertForDate(portfolioId, txDate != null ? txDate : LocalDate.now());
            }
        } catch (Exception e) {
            log.warn("Daily value rebuild failed after BUY for portfolio {}: {}", portfolioId, e.getMessage());
        }
        return toDTO(tx);
    }

    /**
     * Records a SELL transaction. Validates holding existence (throws if missing),
     * appends the ledger row, then replays the full portfolio to derive projections.
     */
    @CacheEvict(cacheNames = {"signals", "signalDto", "portfolioHistory"}, allEntries = true)
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
        BigDecimal realizedPnl = price.subtract(avgPrice)
                .multiply(BigDecimal.valueOf(quantity))
                .subtract(feeAmount)
                .setScale(2, java.math.RoundingMode.HALF_UP);

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

        syncHoldingsFromReplay(portfolioId, stock);
        try {
            snapshotWriter.saveSnapshots(stock, tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after SELL for {}: {}", stock.getSymbol(), e.getMessage());
        }
        try {
            LocalDate txDate = tx.getTransactionDate();
            if (txDate != null && txDate.isBefore(LocalDate.now())) {
                dailyValueService.rebuildFromDate(portfolioId, txDate);
            } else {
                dailyValueService.upsertForDate(portfolioId, txDate != null ? txDate : LocalDate.now());
            }
        } catch (Exception e) {
            log.warn("Daily value rebuild failed after SELL for portfolio {}: {}", portfolioId, e.getMessage());
        }
        return toDTO(tx);
    }

    /**
     * Deletes a transaction only if it belongs to the given portfolio.
     * After deletion, replays the remaining ledger to overwrite all holdings.
     */
    @CacheEvict(cacheNames = {"signals", "signalDto", "portfolioHistory"}, allEntries = true)
    public void deleteTransaction(Long portfolioId, Long txId) {
        PortfolioTransaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txId));
        if (!tx.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException(
                    "Transaction " + txId + " does not belong to portfolio " + portfolioId);
        }
        deleteTransaction(txId);
    }

    @CacheEvict(cacheNames = {"signals", "signalDto", "portfolioHistory"}, allEntries = true)
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
        transactionRepository.delete(tx);

        // Full replay overwrites all projections for this portfolio
        Map<Long, PortfolioPositionReplayer.PositionState> states = replayer.replay(portfolioId);
        applyReplayToHoldings(portfolioId, states, tx.getStock().getId());

        try {
            snapshotWriter.saveSnapshots(tx.getStock(), tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after transaction delete for {}: {}", tx.getStock().getSymbol(), e.getMessage());
        }
        try {
            LocalDate txDate = tx.getTransactionDate();
            if (txDate != null) {
                dailyValueService.rebuildFromDate(portfolioId, txDate);
            }
        } catch (Exception e) {
            log.warn("Daily value rebuild failed after delete for portfolio {}: {}", portfolioId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Update (partial)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies a partial update to an existing transaction in place.
     *
     * <p>Only quantity, price, fees, transactionDate and notes may change — the
     * stock and the BUY/SELL type are fixed. Mutating in place (rather than
     * delete + recreate) is deliberate: {@code linked_buy_id} on existing sells
     * keeps pointing at a live BUY row, so lot linkage and stored realized P/L
     * stay intact.
     *
     * <p>A {@code null} argument means "leave unchanged".
     */
    @CacheEvict(cacheNames = {"signals", "signalDto", "portfolioHistory"}, allEntries = true)
    public TransactionDTO updateTransaction(Long portfolioId, Long txId,
                                            Integer quantity, BigDecimal price, BigDecimal fees,
                                            LocalDate transactionDate, String notes) {
        PortfolioTransaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txId));
        if (!tx.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException(
                    "Transaction " + txId + " does not belong to portfolio " + portfolioId);
        }

        LocalDate previousDate = tx.getTransactionDate();
        // Captured BEFORE mutation — tx is a managed entity, so re-reading it
        // afterwards would return the already-updated values.
        BigDecimal previousPrice = tx.getPrice();

        if (quantity != null) {
            tx.setQuantity(quantity);
        }
        if (price != null) {
            tx.setPrice(price);
        }
        if (fees != null) {
            tx.setFees(fees);
        }
        if (transactionDate != null) {
            tx.setTransactionDate(transactionDate);
        }
        if (notes != null) {
            tx.setNotes(notes);
        }

        // Validate the MERGED result, not just the incoming fields
        validateCommon(tx.getQuantity(), tx.getPrice());

        // A BUY's price/fees change invalidates the realized P/L already stored
        // against every sell linked to this lot — recompute those rows, otherwise
        // lot totals (which sum the stored values) silently go stale.
        if (tx.getType() == TransactionType.BUY
                && (previousPrice == null || !tx.getPrice().equals(previousPrice))) {
            recomputeLinkedSellPnl(txId);
        }

        transactionRepository.save(tx);

        Long stockId = tx.getStock().getId();
        Stock stock = tx.getStock();

        // Full replay overwrites all holdings for this portfolio
        Map<Long, PortfolioPositionReplayer.PositionState> states = replayer.replay(portfolioId);
        applyReplayToHoldings(portfolioId, states, stockId);

        try {
            snapshotWriter.saveSnapshots(stock, tx.getTransactionDate());
        } catch (Exception e) {
            log.warn("Snapshot creation failed after edit for {}: {}", stock.getSymbol(), e.getMessage());
        }

        try {
            // Rebuild from the EARLIER of the two dates: an edit that moves a
            // transaction backwards would otherwise leave every portfolio_daily_values
            // row between the old and new date holding pre-edit numbers.
            LocalDate rebuildFrom = previousDate != null && (tx.getTransactionDate() == null
                    || previousDate.isBefore(tx.getTransactionDate()))
                    ? previousDate
                    : tx.getTransactionDate();
            if (rebuildFrom != null) {
                dailyValueService.rebuildFromDate(portfolioId, rebuildFrom);
            }
        } catch (Exception e) {
            log.warn("Daily value rebuild failed after edit for portfolio {}: {}", portfolioId, e.getMessage());
        }

        return toDTO(tx);
    }

    /**
     * Recomputes and stores realized P/L for every SELL linked to the given BUY,
     * using the BUY's current price. Mirrors the formula in
     * {@link #recordSellAgainstLot}: (sellPrice - buyPrice) * qty - fees.
     */
    private void recomputeLinkedSellPnl(Long buyTxId) {
        PortfolioTransaction buyTx = transactionRepository.findById(buyTxId).orElse(null);
        if (buyTx == null) {
            return;
        }
        for (PortfolioTransaction sell : transactionRepository.findByLinkedBuyIdOrderByTransactionDateAscIdAsc(buyTxId)) {
            BigDecimal feeAmount = sell.getFees() != null ? sell.getFees() : BigDecimal.ZERO;
            BigDecimal realized = sell.getPrice()
                    .subtract(buyTx.getPrice())
                    .multiply(BigDecimal.valueOf(sell.getQuantity()))
                    .subtract(feeAmount)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            sell.setRealizedPnl(realized);
            transactionRepository.save(sell);
        }
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

    @Transactional(readOnly = true)
    public int[] getHistoricalStateAt(LocalDate snapshotDate, Long portfolioId, Long stockId) {
        List<PortfolioTransaction> txns = transactionRepository
                .findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(portfolioId, stockId);
        PortfolioPositionReplayer.PositionState state = PortfolioPositionReplayer.PositionState.EMPTY;
        for (PortfolioTransaction t : txns) {
            if (t.getTransactionDate().isAfter(snapshotDate)) break;
            if (t.getType() == TransactionType.BUY) {
                state = replayer.applyBuy(state, t.getQuantity(), t.getPrice(), t.getFees());
            } else {
                state = replayer.applySell(state, t.getQuantity(), t.getPrice(), t.getFees()).state();
            }
        }
        if (state.quantity() == null || state.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return new int[]{state.quantity().intValue(), 0};
    }

    @Transactional(readOnly = true)
    public BigDecimal getHistoricalAvgPriceAt(LocalDate snapshotDate, Long portfolioId, Long stockId) {
        List<PortfolioTransaction> txns = transactionRepository
                .findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(portfolioId, stockId);
        PortfolioPositionReplayer.PositionState state = PortfolioPositionReplayer.PositionState.EMPTY;
        for (PortfolioTransaction t : txns) {
            if (t.getTransactionDate().isAfter(snapshotDate)) break;
            if (t.getType() == TransactionType.BUY) {
                state = replayer.applyBuy(state, t.getQuantity(), t.getPrice(), t.getFees());
            } else {
                state = replayer.applySell(state, t.getQuantity(), t.getPrice(), t.getFees()).state();
            }
        }
        return state.quantity() != null && state.quantity().compareTo(BigDecimal.ZERO) > 0
                ? state.avgCost() : null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * After a mutation, replays the entire portfolio and syncs all holdings
     * to match the derived ledger state. Ghost rows (qty=0) are deleted.
     *
     * @param portfolioId the portfolio to replay
     * @param stock       the stock involved in the mutation (for snapshot context)
     */
    private void syncHoldingsFromReplay(Long portfolioId, Stock stock) {
        Map<Long, PortfolioPositionReplayer.PositionState> states = replayer.replay(portfolioId);
        applyReplayToHoldings(portfolioId, states, stock.getId());
    }

    /**
     * Applies replay results to the holding repository.
     * For each stock in the replay: upsert if qty>0, delete if qty==0.
     * Also deletes any holding rows not present in the replay (ghost cleanup).
     */
    private void applyReplayToHoldings(Long portfolioId,
                                        Map<Long, PortfolioPositionReplayer.PositionState> states,
                                        Long mutatedStockId) {
        // Update or insert holdings that exist in the replay
        for (Map.Entry<Long, PortfolioPositionReplayer.PositionState> entry : states.entrySet()) {
            Long stockId = entry.getKey();
            PortfolioPositionReplayer.PositionState state = entry.getValue();
            if (state.quantity() == null || state.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Optional<PortfolioHolding> existing = holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId);
            if (existing.isPresent()) {
                portfolioService.updateHolding(portfolioId, existing.get().getId(),
                        state.quantity().intValue(), state.avgCost());
            } else {
                Stock stock = stockRepository.findById(stockId).orElse(null);
                if (stock != null) {
                    portfolioService.addHolding(portfolioId, stockId,
                            state.quantity().intValue(), state.avgCost());
                }
            }
        }

        // Delete ghost holdings: rows in DB but not in replay (qty=0 or fully sold)
        List<PortfolioHolding> dbHoldings = holdingRepository.findByPortfolioId(portfolioId);
        for (PortfolioHolding h : dbHoldings) {
            if (!states.containsKey(h.getStock().getId())) {
                portfolioService.removeHolding(portfolioId, h.getId());
            }
        }

        // Reset stock-level fields when the mutated stock's holding was deleted
        PortfolioPositionReplayer.PositionState mutatedState = states.get(mutatedStockId);
        if (mutatedState == null || mutatedState.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            resetStockWhenHoldingRemoved(mutatedStockId);
        }
    }

    /**
     * Resets the Stock row's quantity/avgPrice after a portfolio holding has been removed,
     * recomputing from other portfolios' holdings (or zeroing if none).
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
                    ? weightedSum.divide(BigDecimal.valueOf(totalQty), 2, java.math.RoundingMode.HALF_UP)
                    : null);
        }
        stockRepository.save(stock);
    }

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
