package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PortfolioAggregateDTO;
import org.example.entity.PortfolioHolding;
import org.example.entity.PortfolioTransaction;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioTransactionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * All-time correct portfolio history service — replaces snapshot-based aggregation.
 * Computes investment/currentValue per date by replaying the transaction ledger
 * and using closing price on-or-before date. No dependency on portfolio_snapshots.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioHistoryService {

    private final PortfolioTransactionRepository transactionRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final PortfolioPositionReplayer replayer;

    @Transactional(readOnly = true)
    @Cacheable(value = "portfolioHistory", key = "#portfolioId + '-' + #days")
    public List<PortfolioAggregateDTO> getHistory(int days, Long portfolioId) {
        LocalDate fromDate = LocalDate.now().minusDays(days);
        return computeHistory(fromDate, portfolioId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "portfolioHistory", key = "#portfolioId + '-all'")
    public List<PortfolioAggregateDTO> getAllHistory(Long portfolioId) {
        LocalDate fromDate = findEarliestDate(portfolioId);
        return computeHistory(fromDate, portfolioId);
    }

    // Legacy overloads for aggregated across all portfolios
    @Transactional(readOnly = true)
    @Cacheable(value = "portfolioHistory", key = "'all-' + #days")
    public List<PortfolioAggregateDTO> getHistory(int days) {
        return getHistory(days, null);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "portfolioHistory", key = "'all-all'")
    public List<PortfolioAggregateDTO> getAllHistory() {
        return getAllHistory(null);
    }

    private LocalDate findEarliestDate(Long portfolioId) {
        List<PortfolioTransaction> txns;
        if (portfolioId != null) {
            txns = transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId);
        } else {
            // across all portfolios — use earliest price date as fallback
            List<LocalDate> dates = dailyPriceRepository.findDistinctTradingDatesAfter(LocalDate.of(2020, 1, 1));
            if (dates != null && !dates.isEmpty()) return dates.get(0);
            return LocalDate.now().minusDays(365);
        }
        if (txns != null && !txns.isEmpty()) {
            return txns.get(0).getTransactionDate();
        }
        // fallback: earliest price date or 1 year ago
        List<LocalDate> dates = dailyPriceRepository.findDistinctTradingDatesAfter(LocalDate.of(2020, 1, 1));
        if (dates != null && !dates.isEmpty()) return dates.get(0);
        return LocalDate.now().minusDays(365);
    }

    private List<PortfolioAggregateDTO> computeHistory(LocalDate fromDate, Long portfolioId) {
        // 1) trading dates — all distinct price dates from fromDate inclusive
        List<LocalDate> tradingDates = dailyPriceRepository.findDistinctTradingDatesAfter(fromDate);
        if (tradingDates == null) tradingDates = List.of();
        LocalDate today = LocalDate.now();
        if (!tradingDates.contains(today)) {
            tradingDates = new ArrayList<>(tradingDates);
            tradingDates.add(today);
            Collections.sort(tradingDates);
        }
        if (tradingDates.isEmpty()) {
            return List.of();
        }

        // 2) Load all transactions sorted by date + build ID lookup map
        List<PortfolioTransaction> allTxns;
        if (portfolioId != null) {
            allTxns = transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId);
        } else {
            allTxns = transactionRepository.findAll();
            allTxns.sort(Comparator.comparing(PortfolioTransaction::getTransactionDate).thenComparing(PortfolioTransaction::getId));
        }
        if (allTxns.isEmpty()) {
            return List.of();
        }

        // Map of txn ID → txn for looking up linked buy prices on SELL
        Map<Long, PortfolioTransaction> txnById = new HashMap<>();
        Set<Long> stockIds = new LinkedHashSet<>();
        for (PortfolioTransaction t : allTxns) {
            txnById.put(t.getId(), t);
            stockIds.add(t.getStock().getId());
        }

        // Also include legacy holdings (stocks with holdings but no transactions)
        Map<Long, PortfolioHolding> holdingByStock = new HashMap<>();
        if (portfolioId != null) {
            for (PortfolioHolding h : holdingRepository.findByPortfolioId(portfolioId)) {
                stockIds.add(h.getStock().getId());
                holdingByStock.put(h.getStock().getId(), h);
            }
        }

        List<Long> stockIdList = new ArrayList<>(stockIds);

        // 3) Batch load prices
        List<org.example.entity.DailyPrice> priceRows = dailyPriceRepository.findPricesForStockIdsSince(stockIdList, fromDate.minusDays(30));
        Map<Long, NavigableMap<LocalDate, BigDecimal>> priceMap = new HashMap<>();
        for (org.example.entity.DailyPrice dp : priceRows) {
            priceMap.computeIfAbsent(dp.getStock().getId(), k -> new TreeMap<>()).put(dp.getPriceDate(), dp.getClosingPrice());
        }

        // 4) Sweep dates with RUNNING CUMULATIVE investment
        //    BUY  → investment += qty × buy_price
        //    SELL → investment -= qty_sold × original_buy_price (from linked buy txn)
        BigDecimal runningInvestment = BigDecimal.ZERO;
        Map<Long, BigDecimal> holdingsQty = new HashMap<>(); // stockId → qty currently held

        // Seed legacy holdings (no transactions) into holdingsQty + investment
        for (Map.Entry<Long, PortfolioHolding> entry : holdingByStock.entrySet()) {
            Long sid = entry.getKey();
            PortfolioHolding h = entry.getValue();
            boolean hasTxn = allTxns.stream().anyMatch(t -> t.getStock().getId().equals(sid));
            if (!hasTxn && h.getQuantity() != null && h.getQuantity() > 0) {
                holdingsQty.put(sid, BigDecimal.valueOf(h.getQuantity()));
                BigDecimal avgPrice = h.getAvgPrice() != null ? h.getAvgPrice() : BigDecimal.ZERO;
                runningInvestment = runningInvestment.add(avgPrice.multiply(BigDecimal.valueOf(h.getQuantity())));
            }
        }

        int txnIdx = 0;
        List<PortfolioAggregateDTO> result = new ArrayList<>();

        for (LocalDate date : tradingDates) {
            // Process all transactions up to and including this date
            while (txnIdx < allTxns.size() && !allTxns.get(txnIdx).getTransactionDate().isAfter(date)) {
                PortfolioTransaction t = allTxns.get(txnIdx);
                Long sid = t.getStock().getId();
                BigDecimal qty = BigDecimal.valueOf(t.getQuantity());

                if (t.getType() == org.example.entity.TransactionType.BUY) {
                    // Add buy amount to investment
                    runningInvestment = runningInvestment.add(t.getPrice().multiply(qty));
                    holdingsQty.merge(sid, qty, BigDecimal::add);
                } else {
                    // SELL: subtract original buy price × qty sold
                    BigDecimal originalBuyPrice = getOriginalBuyPrice(t, txnById);
                    runningInvestment = runningInvestment.subtract(originalBuyPrice.multiply(qty));
                    holdingsQty.merge(sid, qty.negate(), BigDecimal::add);
                    if (holdingsQty.getOrDefault(sid, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
                        holdingsQty.remove(sid);
                    }
                }
                txnIdx++;
            }

            // Compute current value = Σ(qty_held × closing_price_on_that_day)
            BigDecimal totalCurrentValue = BigDecimal.ZERO;
            int holdingsCount = 0;
            for (Map.Entry<Long, BigDecimal> entry : holdingsQty.entrySet()) {
                BigDecimal qty = entry.getValue();
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal price = getPriceOnOrBefore(entry.getKey(), date, priceMap);
                if (price == null) price = BigDecimal.ZERO;
                totalCurrentValue = totalCurrentValue.add(price.multiply(qty));
                holdingsCount++;
            }

            // Skip dates before any investment exists
            if (holdingsCount == 0 && runningInvestment.compareTo(BigDecimal.ZERO) <= 0 && result.isEmpty()) {
                continue;
            }

            BigDecimal pnl = totalCurrentValue.subtract(runningInvestment);
            BigDecimal pnlPercent = runningInvestment.compareTo(BigDecimal.ZERO) > 0
                    ? pnl.multiply(BigDecimal.valueOf(100)).divide(runningInvestment, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            result.add(new PortfolioAggregateDTO(date, runningInvestment, totalCurrentValue, pnl, pnlPercent, holdingsCount));
        }

        return result;
    }

    /**
     * For a SELL transaction, find the original BUY price from its linked buy transaction.
     * Falls back to the sell price if no linked buy is found.
     */
    private BigDecimal getOriginalBuyPrice(PortfolioTransaction sellTxn, Map<Long, PortfolioTransaction> txnById) {
        if (sellTxn.getLinkedBuyId() != null) {
            PortfolioTransaction buyTxn = txnById.get(sellTxn.getLinkedBuyId());
            if (buyTxn != null) {
                return buyTxn.getPrice();
            }
        }
        // Fallback: use sell price (legacy data without linked buy)
        log.warn("SELL txn {} has no linked buy — using sell price {} as fallback", sellTxn.getId(), sellTxn.getPrice());
        return sellTxn.getPrice();
    }

    private BigDecimal getPriceOnOrBefore(Long stockId, LocalDate date, Map<Long, NavigableMap<LocalDate, BigDecimal>> priceMap) {
        NavigableMap<LocalDate, BigDecimal> map = priceMap.get(stockId);
        if (map != null) {
            Map.Entry<LocalDate, BigDecimal> e = map.floorEntry(date);
            if (e != null) return e.getValue();
        }
        // fallback single query (cached by batch mostly)
        return dailyPriceRepository.findClosingPriceOnOrBeforeDate(stockId, date).orElse(null);
    }
}
