package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.repository.DailyPriceRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Detects and handles stock splits in historical price data.
 *
 * Stock splits cause artificial price jumps/drops that distort technical indicators
 * (especially RSI, moving averages, etc.). This service detects splits by looking for
 * single-day price changes greater than 40% and applies split adjustments to
 * pre-split historical data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockSplitDetector {

    private final DailyPriceRepository dailyPriceRepository;

    /** Minimum price change percentage to consider as a potential split */
    private static final double SPLIT_THRESHOLD_PERCENT = 40.0;

    /**
     * Detect and fix stock splits for a given stock.
     *
     * @param stockId the stock ID to check
     * @return the number of records adjusted, or 0 if no split detected
     */
    @CacheEvict(cacheNames = "signals", allEntries = true)
    public int detectAndFixSplits(Long stockId) {
        List<DailyPrice> prices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId);

        if (prices.size() < 3) {
            return 0;
        }

        // Look for splits: single-day price drops > 40% or increases > 100%
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal prevClose = prices.get(i - 1).getClosingPrice();
            BigDecimal currClose = prices.get(i).getClosingPrice();

            if (prevClose.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            double pctChange = currClose.subtract(prevClose)
                    .divide(prevClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

            // Detect split: significant price drop (> 40%) or reverse split (> 100% increase)
            if (pctChange < -SPLIT_THRESHOLD_PERCENT || pctChange > 100.0) {
                BigDecimal splitRatio = prevClose.divide(currClose, 6, RoundingMode.HALF_UP);

                log.warn("Stock split detected for stock ID {} on {}: {} -> {} (ratio: {})",
                        stockId, prices.get(i).getPriceDate(), prevClose, currClose, splitRatio);

                return applySplitAdjustment(stockId, prices, i, splitRatio);
            }
        }

        return 0;
    }

    /**
     * Apply split adjustment to all pre-split historical data.
     *
     * @param stockId the stock ID
     * @param prices all prices in chronological order
     * @param splitIndex the index where the split occurred (first post-split day)
     * @param splitRatio the split ratio (pre-split price / post-split price)
     * @return the number of records adjusted
     */
    private int applySplitAdjustment(Long stockId, List<DailyPrice> prices,
                                     int splitIndex, BigDecimal splitRatio) {
        int adjustedCount = 0;

        for (int i = 0; i < splitIndex; i++) {
            DailyPrice price = prices.get(i);

            BigDecimal newClose = price.getClosingPrice().divide(splitRatio, 4, RoundingMode.HALF_UP);
            BigDecimal newOpen = price.getOpeningPrice() != null ?
                    price.getOpeningPrice().divide(splitRatio, 4, RoundingMode.HALF_UP) : null;
            BigDecimal newHigh = price.getHighPrice() != null ?
                    price.getHighPrice().divide(splitRatio, 4, RoundingMode.HALF_UP) : null;
            BigDecimal newLow = price.getLowPrice() != null ?
                    price.getLowPrice().divide(splitRatio, 4, RoundingMode.HALF_UP) : null;

            price.setClosingPrice(newClose);
            price.setOpeningPrice(newOpen);
            price.setHighPrice(newHigh);
            price.setLowPrice(newLow);

            dailyPriceRepository.save(price);
            adjustedCount++;
        }

        log.info("Adjusted {} pre-split records for stock ID {} with ratio {}",
                adjustedCount, stockId, splitRatio);

        return adjustedCount;
    }

    /**
     * Check if a stock has a stock split in its recent history.
     *
     * @param stockId the stock ID to check
     * @param days the number of recent days to check (default: 90)
     * @return true if a split is detected
     */
    public boolean hasRecentSplit(Long stockId, int days) {
        LocalDate cutoff = LocalDate.now().minusDays(days);
        List<DailyPrice> prices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId);

        for (int i = 1; i < prices.size(); i++) {
            if (prices.get(i).getPriceDate().isBefore(cutoff)) {
                continue;
            }

            BigDecimal prevClose = prices.get(i - 1).getClosingPrice();
            BigDecimal currClose = prices.get(i).getClosingPrice();

            if (prevClose.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            double pctChange = currClose.subtract(prevClose)
                    .divide(prevClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

            if (Math.abs(pctChange) > SPLIT_THRESHOLD_PERCENT) {
                return true;
            }
        }

        return false;
    }
}
