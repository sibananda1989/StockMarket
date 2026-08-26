package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PortfolioAggregateDTO;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioDailyValue;
import org.example.entity.TransactionType;
import org.example.repository.PortfolioDailyValueRepository;
import org.example.repository.PortfolioRepository;
import org.example.repository.PortfolioTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioDailyValueService {

    private final PortfolioDailyValueRepository dailyValueRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioHistoryService historyService;
    private final PortfolioTransactionRepository transactionRepository;

    /**
     * Persist or update daily values for a single portfolio for all trading dates (all-time).
     * Implements approved spec R1-R8:
     * R1 entry gate: only portfolios with >=1 BUY and >=1 SELL get rows.
     * R2 BUY adds on purchase day; R3/R4 SELL subtracts original buy price (proportional if partial);
     * R5 heldQty, R6 closing_price on trading days with floor carry, R7 Σ qty×price, R8 trading-days only.
     * Delegates computation to PortfolioHistoryService which already implements R2-R8 via trading-day sweep;
     * this method enforces R1 and persists the result.
     */
    @Transactional
    public int backfillPortfolio(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));
        // R1: gate — portfolio must have at least one BUY and one SELL (overall, not per-date)
        List<org.example.entity.PortfolioTransaction> txns =
                transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId);
        boolean hasBuy = txns.stream().anyMatch(t -> t.getType() == TransactionType.BUY);
        boolean hasSell = txns.stream().anyMatch(t -> t.getType() == TransactionType.SELL);
        if (!hasBuy || !hasSell) {
            log.info("Skipping backfill for portfolio {}: hasBuy={}, hasSell={} — R1 gate (requires both)", portfolioId, hasBuy, hasSell);
            return 0;
        }
        List<PortfolioAggregateDTO> history = historyService.getAllHistory(portfolioId);
        int count = 0;
        for (PortfolioAggregateDTO dto : history) {
            upsert(portfolio, dto);
            count++;
        }
        log.info("Backfilled {} daily values for portfolio {} ({} → {})", count, portfolioId,
                history.isEmpty() ? "-" : history.get(0).getDate(),
                history.isEmpty() ? "-" : history.get(history.size() - 1).getDate());
        return count;
    }

    @Transactional
    public int backfillAllPortfolios() {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        int total = 0;
        for (Portfolio p : portfolios) {
            try {
                total += backfillPortfolio(p.getId());
            } catch (Exception e) {
                log.warn("Backfill failed for portfolio {}: {}", p.getId(), e.getMessage());
            }
        }
        // also persist aggregated (null) as portfolioId = 0 sentinel? We store per-portfolio only.
        log.info("Backfilled all portfolios total rows: {}", total);
        return total;
    }

    /**
     * Upsert a single date for a portfolio (called after transaction or price update).
     */
    @Transactional
    public void upsertForDate(Long portfolioId, LocalDate date) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return;
        // compute history for that single date via getHistory with days window covering date
        // simplest: recompute all and filter
        List<PortfolioAggregateDTO> history = historyService.getAllHistory(portfolioId);
        for (PortfolioAggregateDTO dto : history) {
            if (dto.getDate().equals(date)) {
                upsert(portfolio, dto);
                return;
            }
        }
        log.debug("No history point for portfolio {} on {}", portfolioId, date);
    }

    /**
     * Auto forward-rebuild for back-dated transactions: deletes and recomputes
     * all trading-day rows from {@code fromDate} onward (R1-R8). Called when a
     * BUY/SELL is inserted with an old {@code transaction_date}.
     */
    @Transactional
    public int rebuildFromDate(Long portfolioId, LocalDate fromDate) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return 0;
        // R1 gate
        List<org.example.entity.PortfolioTransaction> txns =
                transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId);
        boolean hasBuy = txns.stream().anyMatch(t -> t.getType() == TransactionType.BUY);
        boolean hasSell = txns.stream().anyMatch(t -> t.getType() == TransactionType.SELL);
        if (!hasBuy || !hasSell) {
            log.info("Skipping rebuildFromDate for portfolio {}: hasBuy={}, hasSell={}", portfolioId, hasBuy, hasSell);
            return 0;
        }
        // delete stale rows from fromDate onward (trading days only, but delete all >= fromDate)
        List<PortfolioDailyValue> toDelete = dailyValueRepository.findByPortfolioIdAndDateRange(portfolioId, fromDate, LocalDate.now().plusDays(1));
        if (!toDelete.isEmpty()) {
            dailyValueRepository.deleteAll(toDelete);
            dailyValueRepository.flush();
        }
        List<PortfolioAggregateDTO> history = historyService.getAllHistory(portfolioId);
        int count = 0;
        for (PortfolioAggregateDTO dto : history) {
            if (!dto.getDate().isBefore(fromDate)) {
                upsert(portfolio, dto);
                count++;
            }
        }
        log.info("Rebuilt {} daily values for portfolio {} from {} onward (back-dated {})", count, portfolioId, fromDate, fromDate);
        return count;
    }

    @Transactional(readOnly = true)
    public List<PortfolioDailyValue> getHistory(Long portfolioId, int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        return dailyValueRepository.findByPortfolioIdAndDateRange(portfolioId, from, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<PortfolioDailyValue> getAllHistory(Long portfolioId) {
        return dailyValueRepository.findByPortfolio_IdOrderByValueDateAsc(portfolioId);
    }

    // ── Trend DTOs from persisted table (for frontend chart) ──
    @Transactional(readOnly = true)
    public List<PortfolioAggregateDTO> getHistoryAsDto(Long portfolioId, int days) {
        if (portfolioId != null) {
            return getHistory(portfolioId, days).stream().map(this::toDto).collect(Collectors.toList());
        }
        // aggregated across all portfolios
        LocalDate from = LocalDate.now().minusDays(days);
        List<PortfolioDailyValue> all = dailyValueRepository.findAllByDateRange(from, LocalDate.now());
        return aggregate(all);
    }

    @Transactional(readOnly = true)
    public List<PortfolioAggregateDTO> getAllHistoryAsDto(Long portfolioId) {
        if (portfolioId != null) {
            return getAllHistory(portfolioId).stream().map(this::toDto).collect(Collectors.toList());
        }
        List<PortfolioDailyValue> all = dailyValueRepository.findAll();
        return aggregate(all);
    }

    private PortfolioAggregateDTO toDto(PortfolioDailyValue e) {
        return new PortfolioAggregateDTO(e.getValueDate(), e.getTotalInvestment(), e.getTotalCurrentValue(),
                e.getTotalPnl(), e.getTotalPnlPercent(), e.getHoldingsCount());
    }

    private List<PortfolioAggregateDTO> aggregate(List<PortfolioDailyValue> rows) {
        if (rows.isEmpty()) return List.of();
        Map<LocalDate, List<PortfolioDailyValue>> grouped = rows.stream()
                .collect(Collectors.groupingBy(PortfolioDailyValue::getValueDate, TreeMap::new, Collectors.toList()));
        List<PortfolioAggregateDTO> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<PortfolioDailyValue>> entry : grouped.entrySet()) {
            LocalDate date = entry.getKey();
            List<PortfolioDailyValue> list = entry.getValue();
            BigDecimal inv = list.stream().map(PortfolioDailyValue::getTotalInvestment).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cv = list.stream().map(PortfolioDailyValue::getTotalCurrentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pnl = cv.subtract(inv);
            BigDecimal pnlPct = inv.compareTo(BigDecimal.ZERO) > 0
                    ? pnl.multiply(BigDecimal.valueOf(100)).divide(inv, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            int holdings = list.stream().mapToInt(PortfolioDailyValue::getHoldingsCount).sum();
            result.add(new PortfolioAggregateDTO(date, inv, cv, pnl, pnlPct, holdings));
        }
        return result;
    }

    private void upsert(Portfolio portfolio, PortfolioAggregateDTO dto) {
        PortfolioDailyValue existing = dailyValueRepository
                .findByPortfolio_IdAndValueDate(portfolio.getId(), dto.getDate())
                .orElse(null);
        if (existing != null) {
            existing.setTotalInvestment(dto.getTotalInvestment());
            existing.setTotalCurrentValue(dto.getTotalCurrentValue());
            existing.setTotalPnl(dto.getTotalPnl());
            existing.setTotalPnlPercent(dto.getTotalPnlPercent());
            existing.setHoldingsCount(dto.getHoldingsCount());
            dailyValueRepository.save(existing);
        } else {
            PortfolioDailyValue entity = new PortfolioDailyValue(
                    portfolio,
                    dto.getDate(),
                    dto.getTotalInvestment(),
                    dto.getTotalCurrentValue(),
                    dto.getTotalPnl(),
                    dto.getTotalPnlPercent(),
                    dto.getHoldingsCount()
            );
            dailyValueRepository.save(entity);
        }
    }
}
