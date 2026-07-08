package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.SupportResistanceDto;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.entity.SupportResistanceLevel;
import org.example.repository.DailyPriceRepository;
import org.example.repository.SupportResistanceLevelRepository;
import org.example.service.calculator.SupportResistanceCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportResistanceService {

    private final SupportResistanceLevelRepository srRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final StockService stockService;
    private final SupportResistanceCalculator calculator = new SupportResistanceCalculator();

    /**
     * Self-injection via the Spring proxy so that {@link #calculateForAllStocks()} can
     * invoke {@link #calculateForStock(Long)} through the proxy, ensuring each per-stock
     * call runs in its OWN transaction (instead of self-invocation, which would skip
     * the @Transactional advice and poison the whole async batch on a single failure).
     */
    @Autowired
    @Lazy
    private SupportResistanceService self;

    /**
     * Calculates and persists all S/R levels for a stock.
     */
    @Transactional
    @CacheEvict(cacheNames = "supportResistanceLevels", key = "#stockId")
    public SupportResistanceDto calculateForStock(Long stockId) {
        log.info("Calculating support/resistance levels for stock ID: {}", stockId);

        Stock stock = stockService.getStockById(stockId);
        List<DailyPrice> prices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId);

        if (prices.size() < 20) {
            log.warn("Insufficient price data ({}) for stock {}. Need at least 20 days.", prices.size(), stock.getSymbol());
            return null;
        }

        // Delete existing levels for today to avoid duplicates (bulk JPQL delete,
        // not the derived "select-then-delete" form which can leave stale rows
        // and trigger unique-constraint violations on recalculation)
        LocalDate today = LocalDate.now();
        srRepository.deleteByStockIdAndCalculationDateBulk(stockId, today);

        // Calculate all levels
        SupportResistanceCalculator.Result result = calculator.calculate(stock, prices);

        // Persist all levels
        srRepository.saveAll(result.levels);
        log.info("Saved {} S/R levels for stock {}", result.levels.size(), stock.getSymbol());

        return result.dto;
    }

    /**
     * Gets the latest S/R levels for a stock (default 180-day lookback, cached).
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "supportResistanceLevels", key = "#stockId")
    public SupportResistanceDto getLatestLevels(Long stockId) {
        stockService.getStockById(stockId);

        List<SupportResistanceLevel> levels = srRepository.findLatestLevels(stockId);
        if (levels.isEmpty()) {
            return null;
        }

        return convertToDto(stockId, levels);
    }

    /**
     * Gets S/R levels for a stock with a custom historical lookback.
     * When the lookback matches the default (180), returns the cached persisted levels.
     * For custom lookbacks, calculates on-the-fly without persisting.
     */
    @Transactional(readOnly = true)
    public SupportResistanceDto getLatestLevels(Long stockId, int lookbackDays) {
        // Use cached/persisted levels for the default lookback
        // NOTE: must go through self proxy so @Cacheable advice is applied
        if (lookbackDays == 180) {
            return self.getLatestLevels(stockId);
        }

        // For custom lookbacks, calculate on-the-fly (don't persist)
        Stock stock = stockService.getStockById(stockId);
        // Fetch only the needed window + buffer for swing/pivot computation
        int neededDays = Math.max(lookbackDays, 20);
        List<DailyPrice> prices = dailyPriceRepository.findLastNDays(stockId, neededDays);
        // Reverse from DESC to ASC order (calculator expects ascending)
        java.util.Collections.reverse(prices);

        if (prices.size() < 20) {
            log.warn("Insufficient price data ({}) for stock {} with lookback {}.",
                    prices.size(), stock.getSymbol(), lookbackDays);
            return null;
        }

        SupportResistanceCalculator.Result result = calculator.calculate(stock, prices, lookbackDays);
        log.debug("Calculated on-the-fly S/R levels for {} with lookback={} ({} major levels)",
                stock.getSymbol(), lookbackDays,
                result.dto.getMajorLevels() != null ? result.dto.getMajorLevels().size() : 0);
        return result.dto;
    }

    /**
     * Gets S/R levels as of a specific date.
     */
    @Transactional(readOnly = true)
    public SupportResistanceDto getLevelsAsOfDate(Long stockId, LocalDate asOfDate) {
        stockService.getStockById(stockId);

        List<SupportResistanceLevel> levels = srRepository.findLatestLevelsAsOfDate(stockId, asOfDate);
        if (levels.isEmpty()) {
            return null;
        }

        return convertToDto(stockId, levels);
    }

    /**
     * Gets historical S/R level snapshots.
     */
    @Transactional(readOnly = true)
    public List<SupportResistanceDto> getHistory(Long stockId, LocalDate fromDate, LocalDate toDate) {
        stockService.getStockById(stockId);

        // Single query: load all levels in the date range, then group in-memory
        List<SupportResistanceLevel> all = srRepository
                .findByStockIdAndCalculationDateBetweenOrderByCalculationDateAsc(stockId, fromDate, toDate);

        Map<LocalDate, List<SupportResistanceLevel>> byDate = all.stream()
                .collect(Collectors.groupingBy(SupportResistanceLevel::getCalculationDate));

        return byDate.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<SupportResistanceLevel>>comparingByKey().reversed())
                .map(e -> convertToDto(stockId, e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Calculates S/R for all stocks asynchronously.
     * Returns immediately; the batch runs on a background thread.
     * Each per-stock calculation runs in its own transaction (via self-proxy)
     * so that one failure does not poison the rest of the batch.
     */
    @Async
    public void calculateForAllStocks() {
        log.info("Starting batch S/R calculation for all stocks");
        List<Stock> stocks = stockService.getAllStocks();
        int count = 0;

        for (Stock stock : stocks) {
            try {
                self.calculateForStock(stock.getId());
                count++;
            } catch (Exception e) {
                log.warn("Could not calculate S/R for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
        log.info("Completed batch S/R calculation for {} stocks", count);
    }

    private SupportResistanceDto convertToDto(Long stockId, List<SupportResistanceLevel> levels) {
        Stock stock = stockService.getStockById(stockId);
        SupportResistanceDto dto = new SupportResistanceDto();
        dto.setStockId(stockId);
        dto.setSymbol(stock.getSymbol());
        dto.setCalculationDate(levels.get(0).getCalculationDate());

        List<SupportResistanceDto.SwingLevel> swingHighs = new ArrayList<>();
        List<SupportResistanceDto.SwingLevel> swingLows = new ArrayList<>();
        SupportResistanceDto.PivotLevels pivots = new SupportResistanceDto.PivotLevels();
        List<SupportResistanceDto.MajorLevel> majorLevels = new ArrayList<>();

        for (SupportResistanceLevel level : levels) {
            switch (level.getLevelType()) {
                case SWING_HIGH:
                    swingHighs.add(new SupportResistanceDto.SwingLevel(
                            level.getLevelValue(), level.getCalculationDate(),
                            level.getStrengthScore(), level.getLevelOrder()));
                    break;
                case SWING_LOW:
                    swingLows.add(new SupportResistanceDto.SwingLevel(
                            level.getLevelValue(), level.getCalculationDate(),
                            level.getStrengthScore(), level.getLevelOrder()));
                    break;
                case PIVOT_P: pivots.setPivot(level.getLevelValue()); break;
                case PIVOT_S1: pivots.setS1(level.getLevelValue()); break;
                case PIVOT_S2: pivots.setS2(level.getLevelValue()); break;
                case PIVOT_S3: pivots.setS3(level.getLevelValue()); break;
                case PIVOT_R1: pivots.setR1(level.getLevelValue()); break;
                case PIVOT_R2: pivots.setR2(level.getLevelValue()); break;
                case PIVOT_R3: pivots.setR3(level.getLevelValue()); break;
                case MAJOR_SUPPORT:
                    majorLevels.add(new SupportResistanceDto.MajorLevel(
                            level.getLevelValue(), "support",
                            level.getTouchCount() != null ? level.getTouchCount() : 1,
                            level.getStrengthScore() != null ? level.getStrengthScore() : BigDecimal.ZERO));
                    break;
                case MAJOR_RESISTANCE:
                    majorLevels.add(new SupportResistanceDto.MajorLevel(
                            level.getLevelValue(), "resistance",
                            level.getTouchCount() != null ? level.getTouchCount() : 1,
                            level.getStrengthScore() != null ? level.getStrengthScore() : BigDecimal.ZERO));
                    break;
            }
        }

        dto.setSwingHighs(swingHighs);
        dto.setSwingLows(swingLows);
        dto.setPivots(pivots);
        dto.setMajorLevels(majorLevels);

        return dto;
    }
}
