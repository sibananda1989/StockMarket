package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.DailyPriceDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.exception.NoPriceDataException;
import org.example.exception.PriceAlreadyExistsException;
import org.example.exception.StockNotFoundException;
import org.example.repository.DailyPriceRepository;

import org.example.service.PortfolioSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DailyPriceService {

    private final DailyPriceRepository dailyPriceRepository;
    private final StockService stockService;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final PortfolioSnapshotService snapshotService;
    private final EntityManager entityManager;

    @Transactional(noRollbackFor = PriceAlreadyExistsException.class)
    public DailyPrice saveDailyPrice(Long stockId, BigDecimal closingPrice, LocalDate priceDate,
                                     BigDecimal openingPrice, BigDecimal highPrice,
                                     BigDecimal lowPrice, Long volume) {
        Stock stock = stockService.getStockById(stockId);
        
        if (dailyPriceRepository.existsByStockIdAndPriceDate(stockId, priceDate)) {
            throw new PriceAlreadyExistsException(stockId, priceDate.toString());
        }
        
        DailyPrice dailyPrice = new DailyPrice(stock, closingPrice, openingPrice, 
                                               highPrice, lowPrice, volume, priceDate);
        DailyPrice saved = dailyPriceRepository.save(dailyPrice);

        // Trigger full technical indicator recalculation after saving new price data.
        // This ensures all 27+ indicators (not just RSI) are updated immediately
        // when a user enters or imports price data manually (Gap 5 fix).
        try {
            technicalAnalysisService.calculateIndicatorsForStock(stockId);
        } catch (Exception e) {
            log.warn("Indicator recalculation failed after price save for stock {}: {}", stockId, e.getMessage());
        }

        stockService.recalculatePortfolio(stock);

        try {
            snapshotService.saveOrUpdate(stock, priceDate);
        } catch (Exception e) {
            log.warn("Portfolio snapshot creation failed for stock {} on {}: {}", stockId, priceDate, e.getMessage());
        }

        return saved;
    }

    @Transactional(noRollbackFor = PriceAlreadyExistsException.class)
    public DailyPrice saveDailyPrice(DailyPriceDTO dto) {
        return saveDailyPrice(dto.getStockId(), dto.getClosingPrice(), dto.getPriceDate(),
                             dto.getOpeningPrice(), dto.getHighPrice(), 
                             dto.getLowPrice(), dto.getVolume());
    }

    @Transactional(readOnly = true)
    public List<DailyPrice> getAllPricesByStockId(Long stockId) {
        return dailyPriceRepository.findByStockIdOrderByPriceDateDesc(stockId);
    }

    @Transactional(readOnly = true)
    public List<DailyPrice> getLast14Days(Long stockId) {
    return dailyPriceRepository.findLastNDays(stockId, 14);
    }

    @Transactional(readOnly = true)
    public DailyPrice getLatestPrice(Long stockId) {
        return dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(stockId)
                // Stock may exist in the `stocks` table with zero price rows
                // (e.g. just added, history not yet synced). Throw a distinct
                // NoPriceDataException so the controller/frontend can show a
                // "data syncing" placeholder instead of the misleading
                // "Stock not found" error.
                .orElseThrow(() -> new NoPriceDataException(stockId, "price"));
    }

    @Transactional(readOnly = true)
    public List<DailyPrice> getPriceHistory(Long stockId, LocalDate fromDate, LocalDate toDate) {
        return dailyPriceRepository.findByStockIdAndPriceDateBetweenOrderByPriceDateDesc(
                stockId, fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public List<DailyPrice> getLastNDays(Long stockId, int days) {
        return dailyPriceRepository.findLastNDays(stockId, days);
    }

    @Transactional(readOnly = true)
    public List<DailyPriceDTO> getPriceHistoryDTO(Long stockId, LocalDate fromDate, LocalDate toDate) {
        return getPriceHistory(stockId, fromDate, toDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private DailyPriceDTO convertToDTO(DailyPrice dailyPrice) {
        DailyPriceDTO dto = new DailyPriceDTO();
        dto.setId(dailyPrice.getId());
        dto.setStockId(dailyPrice.getStock().getId());
        dto.setClosingPrice(dailyPrice.getClosingPrice());
        dto.setOpeningPrice(dailyPrice.getOpeningPrice());
        dto.setHighPrice(dailyPrice.getHighPrice());
        dto.setLowPrice(dailyPrice.getLowPrice());
        dto.setVolume(dailyPrice.getVolume());
        dto.setPriceDate(dailyPrice.getPriceDate());
        dto.setCreatedAt(dailyPrice.getCreatedAt());
        return dto;
    }

    /**
     * One-time data cleanup: removes duplicate DailyPrice records (same stock_id + price_date),
     * keeping only the row with the smallest id. This should be run once during maintenance
     * to clean up any duplicates that may have been created by sync issues.
     */
    @Transactional
    public void cleanupDuplicateDailyPrices() {
        String sql = "DELETE dp FROM daily_price dp " +
                     "INNER JOIN (" +
                     "   SELECT id, ROW_NUMBER() OVER (PARTITION BY stock_id, price_date ORDER BY id) AS rn " +
                     "   FROM daily_price " +
                     ") dup ON dp.id = dup.id " +
                     "WHERE dup.rn > 1";
        entityManager.createNativeQuery(sql).executeUpdate();
    }

}
