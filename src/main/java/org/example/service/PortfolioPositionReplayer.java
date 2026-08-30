package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.PortfolioTransaction;
import org.example.entity.TransactionType;
import org.example.repository.PortfolioTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-source-of-truth engine for computing portfolio position state
 * (quantity, average cost) by replaying the transaction ledger for a portfolio.
 *
 * State per stockId: { BigDecimal quantity, BigDecimal avgCost }
 * Transactions are sorted by (transactionDate, id) to ensure deterministic ordering.
 *
 * This class is stateless and has no Spring transaction annotations — it is a
 * pure replay engine called from within @Transactional methods of other services.
 */
@Service
@Slf4j
public class PortfolioPositionReplayer {

    private static final int AVG_MATH_SCALE = 4;
    private static final int STORE_SCALE = 2;

    private final PortfolioTransactionRepository transactionRepository;

    public PortfolioPositionReplayer(PortfolioTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Replay all transactions for the given portfolio and return the derived
     * position state for each stock (quantity + average cost).
     *
     * @param portfolioId the portfolio to replay
     * @return map of stockId -> PositionState
     */
    public Map<Long, PositionState> replay(Long portfolioId) {
        List<PortfolioTransaction> txns = transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId);

        Map<Long, PositionState> state = new LinkedHashMap<>();

        for (PortfolioTransaction txn : txns) {
            Long stockId = txn.getStock().getId();
            PositionState current = state.getOrDefault(stockId, PositionState.EMPTY);

            if (txn.getType() == TransactionType.BUY) {
                current = applyBuy(current, txn.getQuantity(), txn.getPrice(), txn.getFees());
            } else {
                PositionSellResult result = applySell(current, txn.getQuantity(), txn.getPrice(), txn.getFees());
                current = result.state();
                if (result.discrepancyDetected()) {
                    log.warn("Oversell detected for stock {} in portfolio {}: sold {} but only {} held",
                            txn.getStock().getSymbol(), portfolioId,
                            txn.getQuantity(), current.quantity());
                }
            }

            if (current.quantity() != null && current.quantity().compareTo(BigDecimal.ZERO) > 0) {
                state.put(stockId, current);
            } else {
                state.remove(stockId);
            }
        }

        return state;
    }

    /**
     * Replay transactions for a (portfolio, stock) pair up to and including the given instant,
     * returning the resulting position state.
     *
     * @param portfolioId the portfolio
     * @param stockId     the stock
     * @param upTo        replay only transactions with createdAt <= upTo
     * @return position state at that point in time, or null if no position existed
     */
    public PositionState historicalStateAt(Long portfolioId, Long stockId, java.time.LocalDateTime upTo) {
        List<PortfolioTransaction> txns = transactionRepository
                .findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(portfolioId, stockId);

        PositionState state = PositionState.EMPTY;

        for (PortfolioTransaction txn : txns) {
            if (txn.getCreatedAt() != null && txn.getCreatedAt().isAfter(upTo)) {
                break;
            }
            if (txn.getType() == TransactionType.BUY) {
                state = applyBuy(state, txn.getQuantity(), txn.getPrice(), txn.getFees());
            } else {
                PositionSellResult result = applySell(state, txn.getQuantity(), txn.getPrice(), txn.getFees());
                state = result.state();
            }
        }

        if (state.quantity() == null || state.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return state;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Core formulas
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Apply a BUY transaction to the current position state.
     * First buy sets avg = price + fees/qty.
     * Subsequent buys blend: (qty*avg + buyQty*price + fees) / (qty + buyQty).
     *
     * @return new PositionState with updated quantity and average cost
     */
    public PositionState applyBuy(PositionState state, int buyQty, BigDecimal price, BigDecimal fees) {
        BigDecimal feeAmount = fees != null ? fees : BigDecimal.ZERO;
        int currentQty = state.quantity() != null ? state.quantity().intValue() : 0;
        BigDecimal currentAvg = state.avgCost();

        if (currentQty == 0) {
            // First buy: per-share fee is added to price
            BigDecimal perShareFee = feeAmount.divide(
                    BigDecimal.valueOf(buyQty), AVG_MATH_SCALE, RoundingMode.HALF_UP);
            BigDecimal newAvg = price.add(perShareFee)
                    .setScale(STORE_SCALE, RoundingMode.HALF_UP);
            return new PositionState(BigDecimal.valueOf(buyQty), newAvg);
        }

        // Blend: old cost + new cost with fees, divided by new total qty
        BigDecimal oldCost = currentAvg.multiply(BigDecimal.valueOf(currentQty));
        BigDecimal newCost = price.multiply(BigDecimal.valueOf(buyQty)).add(feeAmount);
        int newQty = currentQty + buyQty;
        BigDecimal blendedAvg = oldCost.add(newCost)
                .divide(BigDecimal.valueOf(newQty), AVG_MATH_SCALE, RoundingMode.HALF_UP)
                .setScale(STORE_SCALE, RoundingMode.HALF_UP);
        return new PositionState(BigDecimal.valueOf(newQty), blendedAvg);
    }

    /**
     * Apply a SELL transaction to the current position state.
     * Returns the realized P&L and the updated state.
     * If sell quantity exceeds holding, clamps to remaining qty and logs a discrepancy.
     *
     * @return PositionSellResult containing the updated state and realized P&L
     */
    public PositionSellResult applySell(PositionState state, int sellQty, BigDecimal price, BigDecimal fees) {
        BigDecimal feeAmount = fees != null ? fees : BigDecimal.ZERO;
        BigDecimal currentQtyBD = state.quantity() != null ? state.quantity() : BigDecimal.ZERO;
        BigDecimal currentAvg = state.avgCost() != null ? state.avgCost() : BigDecimal.ZERO;

        int clampedQty = sellQty;
        boolean discrepancyDetected = false;
        if (sellQty > currentQtyBD.intValue()) {
            clampedQty = currentQtyBD.intValue();
            discrepancyDetected = true;
        }

        // Realized P&L = (sellPrice - avgCost) * qtySold - fees
        BigDecimal realizedPnl = BigDecimal.ZERO;
        if (clampedQty > 0 && currentAvg.compareTo(BigDecimal.ZERO) > 0) {
            realizedPnl = price.subtract(currentAvg)
                    .multiply(BigDecimal.valueOf(clampedQty))
                    .subtract(feeAmount)
                    .setScale(STORE_SCALE, RoundingMode.HALF_UP);
        }

        int newQty = currentQtyBD.intValue() - clampedQty;
        PositionState newState;
        if (newQty <= 0) {
            newState = PositionState.EMPTY;
        } else {
            // Average cost is retained on partial sell
            newState = new PositionState(BigDecimal.valueOf(newQty), currentAvg);
        }

        return new PositionSellResult(newState, realizedPnl, discrepancyDetected);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Inner types
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Immutable position state for a single stock within a portfolio.
     *
     * @param quantity total shares held (null means no position)
     * @param avgCost  weighted average cost per share (null when quantity is null)
     */
    public record PositionState(BigDecimal quantity, BigDecimal avgCost) {
        static final PositionState EMPTY = new PositionState(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Result of applying a sell transaction.
     *
     * @param state                updated position state after the sell
     * @param realizedPnl          realized profit/loss from the sell
     * @param discrepancyDetected  true if sell quantity was clamped to remaining qty
     */
    public record PositionSellResult(PositionState state, BigDecimal realizedPnl, boolean discrepancyDetected) {
    }
}
