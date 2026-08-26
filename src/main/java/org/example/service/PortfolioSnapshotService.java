package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PortfolioAggregateDTO;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.example.dto.PortfolioSnapshotDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.PortfolioSnapshot;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioTransactionRepository;
import org.example.repository.PortfolioSnapshotRepository;
import org.example.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final PortfolioTransactionRepository transactionRepository;
    @Lazy
    @Autowired
    private PortfolioTransactionService transactionService;

    /**
     * Save or update a snapshot for a stock in the given portfolio.
     * If portfolio is null, creates an un-scoped snapshot (legacy fallback).
     */
    @Transactional
    public PortfolioSnapshot saveOrUpdate(Stock stock, LocalDate date, Portfolio portfolio) {
        PortfolioSnapshot snap;

        if (portfolio != null) {
            snap = snapshotRepository
                    .findByStockIdAndPortfolioIdAndSnapshotDate(stock.getId(), portfolio.getId(), date)
                    .orElse(null);
            if (snap == null) {
                snap = new PortfolioSnapshot();
            }
            snap.setPortfolio(portfolio);
        } else {
            snap = snapshotRepository
                    .findByStockIdAndSnapshotDate(stock.getId(), date)
                    .orElse(new PortfolioSnapshot());
        }

        snap.setStock(stock);
        snap.setSnapshotDate(date);

        // Use historical position from transaction ledger when available, otherwise fall back to current holding.
        // This fixes the flat Investment line caused by using current qty for all historical dates.
        Integer qty;
        BigDecimal avgPrice;
        if (portfolio != null) {
            List<org.example.entity.PortfolioTransaction> txns = transactionRepository
                    .findByPortfolioIdAndStockIdOrderByTransactionDateAscIdAsc(portfolio.getId(), stock.getId());
            if (!txns.isEmpty()) {
                // Ledger exists — derive historical state at this snapshot date (qty=0 before first buy)
                BigDecimal histAvg = transactionService.getHistoricalAvgPriceAt(date, portfolio.getId(), stock.getId());
                int[] histState = transactionService.getHistoricalStateAt(date, portfolio.getId(), stock.getId());
                if (histState != null && histState[0] > 0) {
                    qty = histState[0];
                    avgPrice = histAvg;
                } else {
                    // No position at this date — investment/value should be zero (not current holding)
                    qty = 0;
                    avgPrice = histAvg; // may be null if fully sold
                }
            } else {
                PortfolioHolding holding = holdingRepository
                        .findByPortfolioIdAndStockId(portfolio.getId(), stock.getId())
                        .orElse(null);
                if (holding != null) {
                    qty = holding.getQuantity();
                    avgPrice = holding.getAvgPrice();
                } else {
                    qty = stock.getQuantity();
                    avgPrice = stock.getAvgPrice();
                }
            }
        } else {
            qty = stock.getQuantity();
            avgPrice = stock.getAvgPrice();
        }
        snap.setQuantity(qty);
        snap.setAvgPrice(avgPrice);

        // Calculate historical portfolio value based on closing price for the date
        BigDecimal historicalPrice = getClosingPriceForDate(stock, date);
        snap.setLastTradedPrice(historicalPrice);
        BigDecimal historicalInvestment = BigDecimal.ZERO;
        BigDecimal historicalCurrentValue = BigDecimal.ZERO;

        if (qty != null && qty > 0) {
            if (avgPrice != null) {
                historicalInvestment = avgPrice.multiply(BigDecimal.valueOf(qty));
            }
            if (historicalPrice != null) {
                historicalCurrentValue = historicalPrice.multiply(BigDecimal.valueOf(qty));
            } else {
                // No price data available — use avgPrice as fallback so currentValue
                // matches investment rather than defaulting to 0.
                // Prevents misleading "P&L = -investment" snapshot for newly added stocks.
                BigDecimal priceEstimate = stock.getLastTradedPrice() != null
                        ? stock.getLastTradedPrice()
                        : avgPrice;
                if (priceEstimate != null) {
                    historicalCurrentValue = priceEstimate.multiply(BigDecimal.valueOf(qty));
                }
            }
        }

        snap.setInvestment(historicalInvestment);
        snap.setCurrentValue(historicalCurrentValue);
        snap.setPnl(historicalCurrentValue.subtract(historicalInvestment));
        snap.setPnlPercent(historicalInvestment.compareTo(BigDecimal.ZERO) > 0
                ? snap.getPnl().multiply(BigDecimal.valueOf(100)).divide(historicalInvestment, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        snap.setVolume(stock.getVolume());

        return snapshotRepository.save(snap);
    }

    /**
     * Saves snapshot for every portfolio that holds this stock.
     * Falls back to an un-scoped snapshot if no portfolio holds it.
     */
    @Transactional
    public List<PortfolioSnapshot> saveOrUpdate(Stock stock, LocalDate date) {
        List<PortfolioHolding> holdings = holdingRepository.findByStockId(stock.getId());
        if (holdings.isEmpty()) {
            return List.of(saveOrUpdate(stock, date, (Portfolio) null));
        }
        return holdings.stream()
                .map(h -> saveOrUpdate(stock, date, h.getPortfolio()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PortfolioSnapshotDTO> getHistory(Long stockId) {
        return snapshotRepository.findByStockIdOrderBySnapshotDateAsc(stockId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PortfolioSnapshotDTO> getLastN(Long stockId, int days) {
        List<PortfolioSnapshot> snaps = snapshotRepository.findLastNSnapshots(stockId, days);
        java.util.Collections.reverse(snaps);
        return snaps.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PortfolioSnapshotDTO> getScreenerData(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return snapshotRepository.findAllBySnapshotDate(target)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public void deleteSnapshot(Long portfolioId, Long stockId) {
        snapshotRepository.deleteByPortfolioIdAndStockId(portfolioId, stockId);
    }

    @Transactional
    public void deleteSnapshotsByDate(LocalDate date) {
        snapshotRepository.deleteBySnapshotDate(date);
    }

    @Transactional(readOnly = true)
    public long getPriceDaysCount(Long stockId) {
        return snapshotRepository.countByStockId(stockId);
    }

    /**
     * Helper method to get closing price for a stock on or before a given date.
     */
    private BigDecimal getClosingPriceForDate(Stock stock, LocalDate date) {
        Optional<DailyPrice> exactPriceOpt = dailyPriceRepository.findByStockIdAndPriceDate(stock.getId(), date);
        if (exactPriceOpt.isPresent()) {
            return exactPriceOpt.get().getClosingPrice();
        }

        List<DailyPrice> pricesBefore = dailyPriceRepository.findByStockIdAndPriceDateBetweenOrderByPriceDateDesc(
                stock.getId(), LocalDate.ofEpochDay(0), date);

        if (!pricesBefore.isEmpty()) {
            return pricesBefore.get(0).getClosingPrice();
        }

        return null;
    }

    // ── Portfolio-scoped aggregation ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PortfolioAggregateDTO> getAggregatedHistory(int days, Long portfolioId) {
        LocalDate fromDate = LocalDate.now().minusDays(days);
        return mapAggregatedRows(snapshotRepository.findAggregatedHistory(fromDate, portfolioId));
    }

    @Transactional(readOnly = true)
    public List<PortfolioAggregateDTO> getAllAggregatedHistory(Long portfolioId) {
        return mapAggregatedRows(snapshotRepository.findAllAggregatedHistory(portfolioId));
    }

    /**
     * Legacy — aggregates across all portfolios (no filtering).
     * Used during transition phase.
     */
    @Transactional(readOnly = true)
    public List<PortfolioAggregateDTO> getAggregatedHistory(int days) {
        LocalDate fromDate = LocalDate.now().minusDays(days);
        return mapAggregatedRows(snapshotRepository.findAggregatedHistoryAll(fromDate));
    }

    @Transactional(readOnly = true)
    public List<PortfolioAggregateDTO> getAllAggregatedHistory() {
        return mapAggregatedRows(snapshotRepository.findAllAggregatedHistoryAll());
    }

    private List<PortfolioAggregateDTO> mapAggregatedRows(List<Object[]> rows) {
        if (rows == null) return List.of();
        return rows.stream()
                .filter(row -> row != null && row[0] != null)
                .map(row -> {
                    Object dateObj = row[0];
                    LocalDate date;
                    if (dateObj instanceof java.sql.Date sqlDate) {
                        date = sqlDate.toLocalDate();
                    } else if (dateObj instanceof LocalDate ld) {
                        date = ld;
                    } else {
                        date = LocalDate.parse(dateObj.toString());
                    }
                    BigDecimal investment = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                    BigDecimal currentValue = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                    BigDecimal pnl = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
                    int count = row[4] != null ? ((Number) row[4]).intValue() : 0;
                    BigDecimal pnlPercent = investment.compareTo(BigDecimal.ZERO) > 0
                            ? pnl.multiply(BigDecimal.valueOf(100)).divide(investment, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new PortfolioAggregateDTO(date, investment, currentValue, pnl, pnlPercent, count);
                }).collect(Collectors.toList());
    }

    /**
     * Backfill portfolio snapshots for a date range.
     * Uses the default portfolio for all stocks.
     */
    @Transactional
    public void backfillSnapshots(LocalDate fromDate) {
        backfillSnapshots(fromDate, false);
    }

    @Transactional
    public void backfillSnapshots(LocalDate fromDate, boolean force) {
        List<LocalDate> tradingDates = dailyPriceRepository.findDistinctTradingDatesAfter(fromDate);
        List<Stock> stocks = stockRepository.findAll();
        log.info("Backfilling portfolio snapshots: {} trading dates x {} stocks (force={})",
                tradingDates.size(), stocks.size(), force);

        int total = 0;
        for (LocalDate date : tradingDates) {
            // Skip dates with no market price data (weekends, holidays)
            long priceCount = dailyPriceRepository.countByPriceDate(date);
            if (priceCount == 0) {
                // Delete any existing stale snapshots for this date
                if (force) {
                    snapshotRepository.deleteBySnapshotDate(date);
                }
                continue;
            }

            int processed = 0;
            for (Stock stock : stocks) {
                List<PortfolioHolding> holdings = holdingRepository.findByStockId(stock.getId());
                if (holdings.isEmpty()) {
                    // Legacy stock-level portfolio — single un-scoped snapshot
                    if (!force && snapshotRepository.existsByStockIdAndSnapshotDate(stock.getId(), date)) {
                        continue;
                    }
                    saveOrUpdate(stock, date, (Portfolio) null);
                    processed++;
                } else {
                    // Multi-portfolio: create a snapshot per portfolio that holds this stock
                    for (PortfolioHolding holding : holdings) {
                        Portfolio portfolio = holding.getPortfolio();
                        if (!force && snapshotRepository
                                .findByStockIdAndPortfolioIdAndSnapshotDate(stock.getId(), portfolio.getId(), date)
                                .isPresent()) {
                            continue;
                        }
                        saveOrUpdate(stock, date, portfolio);
                        processed++;
                    }
                }
            }
            if (processed > 0) {
                log.debug("Processed {} snapshots for {}", processed, date);
                total += processed;
            }
        }
        log.info("Backfill complete: {} snapshot records processed", total);
    }

    /**
     * Rebuild snapshots for a single stock from the given date through today.
     * Used after a transaction to correct the forward trend (investment/value) that
     * was previously flattened by using current qty for all dates.
     */
    @Transactional
    public void rebuildSnapshotsForStockFrom(Stock stock, LocalDate fromDate) {
        List<LocalDate> tradingDates = dailyPriceRepository.findDistinctTradingDatesAfter(fromDate);
        if (tradingDates.isEmpty()) {
            // No price history — at least ensure today exists
            tradingDates = List.of(fromDate, LocalDate.now());
        }
        for (LocalDate date : tradingDates) {
            if (date.isBefore(fromDate) || date.isAfter(LocalDate.now())) continue;
            saveOrUpdate(stock, date);
        }
        // Ensure today snapshot if not a trading date (e.g., weekend)
        if (!tradingDates.contains(LocalDate.now())) {
            saveOrUpdate(stock, LocalDate.now());
        }
    }

    @Transactional
    public void backfillMissingSnapshots(LocalDate fromDate) {
        List<LocalDate> tradingDates = dailyPriceRepository.findDistinctTradingDatesAfter(fromDate);
        List<Stock> stocks = stockRepository.findAll();
        int total = 0;
        for (LocalDate date : tradingDates) {
            for (Stock stock : stocks) {
                List<PortfolioHolding> holdings = holdingRepository.findByStockId(stock.getId());
                if (holdings.isEmpty()) {
                    if (!snapshotRepository.existsByStockIdAndSnapshotDate(stock.getId(), date)) {
                        saveOrUpdate(stock, date, (Portfolio) null);
                        total++;
                    }
                } else {
                    for (PortfolioHolding h : holdings) {
                        if (snapshotRepository
                                .findByStockIdAndPortfolioIdAndSnapshotDate(stock.getId(), h.getPortfolio().getId(), date)
                                .isEmpty()) {
                            saveOrUpdate(stock, date, h.getPortfolio());
                            total++;
                        }
                    }
                }
            }
        }
        log.info("Missing snapshot backfill complete: {} records created", total);
    }

    private PortfolioSnapshotDTO toDTO(PortfolioSnapshot s) {
        PortfolioSnapshotDTO dto = new PortfolioSnapshotDTO();
        dto.setId(s.getId());
        dto.setStockId(s.getStock().getId());
        dto.setSymbol(s.getStock().getSymbol());
        dto.setName(s.getStock().getName());
        dto.setSnapshotDate(s.getSnapshotDate());
        dto.setQuantity(s.getQuantity());
        dto.setAvgPrice(s.getAvgPrice());
        dto.setLastTradedPrice(s.getLastTradedPrice());
        dto.setInvestment(s.getInvestment());
        dto.setCurrentValue(s.getCurrentValue());
        dto.setPnl(s.getPnl());
        dto.setPnlPercent(s.getPnlPercent());
        dto.setVolume(s.getVolume());
        return dto;
    }
}
