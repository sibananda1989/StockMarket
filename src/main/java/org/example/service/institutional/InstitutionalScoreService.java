package org.example.service.institutional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.InstitutionalScoreDTO;
import org.example.dto.SignalDTO;
import org.example.entity.*;
import org.example.repository.*;
import org.example.service.SignalService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Institutional Score Service
 *
 * Computes a 0-100 institutional activity score per stock.
 *
 * Scoring components:
 *   +20 FII holding increased QoQ
 *   +15 DII holding increased QoQ
 *   +15 Mutual Fund holding increased QoQ
 *   +15 Recent institutional bulk purchase
 *   +10 Recent institutional block purchase
 *   +10 Delivery > 60% (from deal volume / total volume)
 *   +10 Volume > 2x average
 *   +5  Positive price action signal
 *   ─────────────────
 *   Total: 100 points
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstitutionalScoreService {

    private final InstitutionalHoldingRepository holdingRepository;
    private final BulkDealRepository bulkDealRepository;
    private final BlockDealRepository blockDealRepository;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final SignalService signalService;

    /**
     * Computes the institutional score for a single stock by ID.
     * Results are cached with 15-minute TTL and evicted when new data is fetched.
     */
    @Transactional
    public InstitutionalScoreDTO computeScore(Long stockId) {
        Stock stock = stockRepository.findById(stockId).orElse(null);
        if (stock == null) return null;
        return computeScore(stock);
    }

    /**
     * Computes the institutional score for a single stock.
     * Results are cached with 15-minute TTL and evicted when new data is fetched.
     */
    @Cacheable(value = "institutionalScores", key = "#stock.id")
    @Transactional
    public InstitutionalScoreDTO computeScoreCached(Stock stock) {
        return computeScore(stock);
    }

    /**
     * Computes the institutional score for all stocks.
     * The aggregated list is cached and evicted when new data is fetched.
     */
    @Cacheable(value = "institutionalScores", key = "'all'")
    @Transactional
    public List<InstitutionalScoreDTO> computeAllScores() {
        List<Stock> stocks = stockRepository.findAll();
        return stocks.stream()
                .map(s -> computeScoreCached(s))
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()))
                .collect(Collectors.toList());
    }

    /**
     * Core scoring logic — computes the 0-100 institutional score.
     * Optimized to fetch holding and signal ONCE, not N times.
     */
    @Transactional
    public InstitutionalScoreDTO computeScore(Stock stock) {
        Long stockId = stock.getId();
        InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder = InstitutionalScoreDTO.builder()
                .stockId(stockId)
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .sector(stock.getSector())
                .latestPrice(stock.getLastTradedPrice());

        // Fetch holding ONCE — shared across FII, DII, MF components
        Optional<InstitutionalHolding> holding = holdingRepository.findLatestHolding(stockId);

        // Compute signal ONCE — shared across price action and recommendation
        SignalDTO signal = computeSignalSafely(stockId);

        int total = 0;
        total += computeFiiHoldingScore(stockId, holding, builder);
        total += computeDiiHoldingScore(stockId, holding, builder);
        total += computeMutualFundHoldingScore(stockId, holding, builder);
        total += computeBulkDealScore(stockId, builder);
        total += computeBlockDealScore(stockId, builder);
        total += computeDeliveryScore(stockId, builder);
        total += computeVolumeScore(stockId, builder);
        total += computePriceActionScore(stockId, signal, builder);

        // Clamp to [0, 100]
        total = Math.max(0, Math.min(100, total));

        // Grade + signal
        String grade = computeGrade(total);
        String signalRec = signal != null ? signal.getRecommendation() : "N/A";

        return builder
                .totalScore(total)
                .institutionalGrade(grade)
                .signalRecommendation(signalRec)
                .build();
    }

    // ========== Component Score Calculations ==========

    /**
     * FII Holding Score (0-20)
     *
     * Formula:
     *   If FII % data exists:
     *     Δ ≥ 2.0% → 20 points
     *     Δ ≥ 1.0% → 15 points
     *     Δ ≥ 0.5% → 10 points
     *     Δ ≥ 0.1% → 5  points
     *     Δ < 0.1% → 0  points (no material change)
     *   If no FII data:
     *     Falls back to promoter → public shift detection
     *     Public % increased → +5 (proxy for institutional interest)
     */
    int computeFiiHoldingScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        return computeFiiHoldingScore(stockId, holdingRepository.findLatestHolding(stockId), builder);
    }

    /**
     * FII Holding Score — optimized overload that accepts a pre-fetched holding.
     */
    private int computeFiiHoldingScore(Long stockId, Optional<InstitutionalHolding> latest,
                                       InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        if (latest.isEmpty()) {
            builder.fiiHoldingPct(null);
            builder.fiiChangeQoq(null);
            builder.latestQuarterEnd(null);
            // Fallback: check promoter → public shift
            return computePromoterToPublicShiftScore(stockId, builder);
        }

        InstitutionalHolding h = latest.get();
        builder.fiiHoldingPct(h.getFiiHoldingPct());
        builder.fiiChangeQoq(h.getFiiChangeQoq());
        builder.latestQuarterEnd(h.getQuarterEndDate());

        // If we have direct FII data from BSE XBRL
        if (h.getFiiHoldingPct() != null && h.getFiiChangeQoq() != null) {
            double change = h.getFiiChangeQoq().doubleValue();
            if (change >= 2.0) return 20;
            if (change >= 1.0) return 15;
            if (change >= 0.5) return 10;
            if (change >= 0.1) return 5;
            return 0;
        }

        // Fallback: use promoter change as inverse proxy (promoter↓ = institutional interest↑)
        return computePromoterToPublicShiftScore(stockId, builder);
    }

    /**
     * Fallback: If FII/DII data not available, use promoter→public shift.
     * When promoter holding decreases, it often means institutional buying.
     */
    private int computePromoterToPublicShiftScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        List<InstitutionalHolding> history = holdingRepository.findByStockIdOrderByQuarterEndDateDesc(stockId);
        if (history.size() < 2) return 0;

        InstitutionalHolding current = history.get(0);
        InstitutionalHolding previous = history.get(1);

        if (current.getPromoterHoldingPct() != null && previous.getPromoterHoldingPct() != null) {
            BigDecimal promoChange = current.getPromoterHoldingPct().subtract(previous.getPromoterHoldingPct());
            double change = promoChange.doubleValue();

            // If FII% is null, use promoter↓ as a weak signal for institutional interest
            if (current.getFiiHoldingPct() == null) {
                if (current.getFiiChangeQoq() != null) return 0; // Already handled above

                if (change <= -2.0) return 5;   // Significant promoter reduction
                if (change <= -1.0) return 3;
                if (change <= -0.5) return 2;
            }
        }
        return 0;
    }

    /**
     * DII Holding Score (0-15)
     *
     * Formula:
     *   Δ ≥ 2.0% → 15 points
     *   Δ ≥ 1.0% → 10 points
     *   Δ ≥ 0.5% → 7  points
     *   Δ ≥ 0.1% → 3  points
     *   Δ < 0.1% → 0  points
     */
    int computeDiiHoldingScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        return computeDiiHoldingScore(stockId, holdingRepository.findLatestHolding(stockId), builder);
    }

    /**
     * DII Holding Score — optimized overload that accepts a pre-fetched holding.
     */
    private int computeDiiHoldingScore(Long stockId, Optional<InstitutionalHolding> latest,
                                       InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        if (latest.isEmpty()) return 0;

        InstitutionalHolding h = latest.get();
        builder.diiHoldingPct(h.getDiiHoldingPct());
        builder.diiChangeQoq(h.getDiiChangeQoq());

        if (h.getDiiHoldingPct() == null || h.getDiiChangeQoq() == null) return 0;

        double change = h.getDiiChangeQoq().doubleValue();
        if (change >= 2.0) return 15;
        if (change >= 1.0) return 10;
        if (change >= 0.5) return 7;
        if (change >= 0.1) return 3;
        return 0;
    }

    /**
     * Mutual Fund Holding Score (0-15)
     *
     * Formula:
     *   Δ ≥ 2.0% → 15 points
     *   Δ ≥ 1.0% → 10 points
     *   Δ ≥ 0.5% → 7  points
     *   Δ ≥ 0.1% → 3  points
     *   Δ < 0.1% → 0  points
     */
    int computeMutualFundHoldingScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        return computeMutualFundHoldingScore(stockId, holdingRepository.findLatestHolding(stockId), builder);
    }

    /**
     * Mutual Fund Holding Score — optimized overload that accepts a pre-fetched holding.
     */
    private int computeMutualFundHoldingScore(Long stockId, Optional<InstitutionalHolding> latest,
                                              InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        if (latest.isEmpty()) return 0;

        InstitutionalHolding h = latest.get();
        builder.mutualFundHoldingPct(h.getMutualFundHoldingPct());
        builder.mutualFundChangeQoq(h.getMutualFundChangeQoq());

        if (h.getMutualFundHoldingPct() == null || h.getMutualFundChangeQoq() == null) return 0;

        double change = h.getMutualFundChangeQoq().doubleValue();
        if (change >= 2.0) return 15;
        if (change >= 1.0) return 10;
        if (change >= 0.5) return 7;
        if (change >= 0.1) return 3;
        return 0;
    }

    /**
     * Bulk Deal Score (0-15)
     *
     * Formula:
     *   Institutional bulk BUY in last 7 days → 15 points
     *   Institutional bulk BUY in last 30 days → 10 points
     *   Institutional bulk BUY in last 90 days → 5 points
     *   No institutional bulk BUY → 0 points
     */
    int computeBulkDealScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        int buys7 = bulkDealRepository.countInstitutionalBuysSince(stockId, LocalDate.now().minusDays(7));
        if (buys7 > 0) {
            builder.recentBulkBuys(buys7);
            return 15;
        }

        int buys30 = bulkDealRepository.countInstitutionalBuysSince(stockId, LocalDate.now().minusDays(30));
        if (buys30 > 0) {
            builder.recentBulkBuys(buys30);
            return 10;
        }

        int buys90 = bulkDealRepository.countInstitutionalBuysSince(stockId, LocalDate.now().minusDays(90));
        if (buys90 > 0) {
            builder.recentBulkBuys(buys90);
            return 5;
        }

        builder.recentBulkBuys(0);
        return 0;
    }

    /**
     * Block Deal Score (0-10)
     *
     * Formula:
     *   Institutional block BUY in last 7 days → 10 points
     *   Institutional block BUY in last 30 days → 7 points
     *   Institutional block BUY in last 90 days → 3 points
     *   No institutional block BUY → 0 points
     */
    int computeBlockDealScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        int buys7 = blockDealRepository.countInstitutionalBuysSince(stockId, LocalDate.now().minusDays(7));
        if (buys7 > 0) {
            builder.recentBlockBuys(buys7);
            return 10;
        }

        int buys30 = blockDealRepository.countInstitutionalBuysSince(stockId, LocalDate.now().minusDays(30));
        if (buys30 > 0) {
            builder.recentBlockBuys(buys30);
            return 7;
        }

        int buys90 = blockDealRepository.countInstitutionalBuysSince(stockId, LocalDate.now().minusDays(90));
        if (buys90 > 0) {
            builder.recentBlockBuys(buys90);
            return 3;
        }

        builder.recentBlockBuys(0);
        return 0;
    }

    /**
     * Delivery Score (0-10)
     *
     * Delivery % = (total deal volume in stock trades / total volume) × 100
     * Derivation: We use the ratio of bulk+block deal volume to total volume
     * as a proxy for delivery concentration.
     *
     * Formula:
     *   Deal volume / total volume > 60% → 10 points
     *   Deal volume / total volume > 40% → 7 points
     *   Deal volume / total volume > 20% → 3 points
     *   Otherwise → 0 points
     *
     * For now, this is a simplified proxy using available data.
     */
    int computeDeliveryScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        // Use bulk deals total quantity in last 30 days vs average 30-day volume
        LocalDate monthAgo = LocalDate.now().minusDays(30);

        // Sum bulk deal quantities for this stock in last 30 days
        List<BulkDeal> bulkDeals = bulkDealRepository
                .findByStockIdAndDealDateBetweenOrderByDealDateDesc(stockId, monthAgo, LocalDate.now());
        long bulkQty = bulkDeals.stream()
                .filter(d -> "BUY".equals(d.getBuySell()) && Boolean.TRUE.equals(d.getIsInstitutional()))
                .mapToLong(d -> d.getQuantity() != null ? d.getQuantity() : 0L)
                .sum();

        // Get average daily volume from daily prices
        List<DailyPrice> recentPrices = dailyPriceRepository
                .findAllByStockIdOrderByPriceDateAsc(stockId);
        if (recentPrices.size() < 20) return 0;

        List<DailyPrice> last30Days = recentPrices.subList(
                Math.max(0, recentPrices.size() - 30), recentPrices.size());
        double avgVolume = last30Days.stream()
                .filter(p -> p.getVolume() != null)
                .mapToLong(DailyPrice::getVolume)
                .average().orElse(0);

        if (avgVolume <= 0) return 0;

        double deliveryRatio = (bulkQty / avgVolume) * 100.0;

        if (deliveryRatio > 60) return 10;
        if (deliveryRatio > 40) return 7;
        if (deliveryRatio > 20) return 3;
        return 0;
    }

    /**
     * Volume Score (0-10)
     *
     * Formula:
     *   Latest volume > 2.0× 20-day avg → 10 points
     *   Latest volume > 1.5× 20-day avg → 7 points
     *   Latest volume > 1.2× 20-day avg → 3 points
     *   Otherwise → 0 points
     */
    int computeVolumeScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        List<DailyPrice> prices = dailyPriceRepository
                .findAllByStockIdOrderByPriceDateAsc(stockId);
        if (prices.size() < 20) return 0;

        DailyPrice latestPrice = prices.get(prices.size() - 1);
        if (latestPrice.getVolume() == null || latestPrice.getVolume() <= 0) {
            return 0;
        }

        long latestVol = latestPrice.getVolume();
        List<DailyPrice> last20 = prices.subList(prices.size() - 20, prices.size());
        double avgVol = last20.stream()
                .filter(p -> p.getVolume() != null)
                .mapToLong(DailyPrice::getVolume)
                .average().orElse(0);

        builder.averageVolume((long) avgVol);
        builder.latestVolume(latestVol);

        if (avgVol <= 0) return 0;

        double ratio = (double) latestVol / avgVol;
        if (ratio >= 2.0) return 10;
        if (ratio >= 1.5) return 7;
        if (ratio >= 1.2) return 3;
        return 0;
    }

    /**
     * Price Action Score (0-5)
     *
     * Formula:
     *   Existing signal is STRONG BUY → 5 points
     *   Existing signal is BUY → 4 points
     *   RSI < 30 (oversold) → 3 points
     *   Bullish divergence detected → 3 points
     *   Otherwise → 0 points
     */
    int computePriceActionScore(Long stockId, InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        try {
            return computePriceActionScore(stockId, signalService.computeSignal(stockId), builder);
        } catch (Exception e) {
            log.debug("Could not compute price action score for {}: {}", stockId, e.getMessage());
            return 0;
        }
    }

    /**
     * Price Action Score — optimized overload that accepts a pre-computed signal.
     */
    private int computePriceActionScore(Long stockId, SignalDTO signal,
                                        InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder) {
        if (signal == null) return 0;

        String rec = signal.getRecommendation();
        if ("STRONG BUY".equals(rec)) return 5;
        if ("BUY".equals(rec)) return 4;

        // Check RSI oversold
        if (signal.getRsi14() != null && signal.getRsi14().doubleValue() < 30) {
            return 3;
        }

        // Check bullish divergence
        if (signal.isBullishDivergence()) return 3;

        return 1; // Slight positive for HOLD with positive indicators
    }

    /**
     * Retrieves the signal recommendation from SignalService.
     */
    String getSignalRecommendation(Long stockId) {
        try {
            var signal = signalService.computeSignal(stockId);
            return signal != null ? signal.getRecommendation() : "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Safely computes the signal for a stock, returning null on any exception.
     * Used by computeScoreInternal to avoid duplicate signal computation.
     */
    @Transactional
    private SignalDTO computeSignalSafely(Long stockId) {
        try {
            return signalService.computeSignal(stockId);
        } catch (Exception e) {
            log.debug("Could not compute signal for {}: {}", stockId, e.getMessage());
            return null;
        }
    }

    /**
     * Grades the total score.
     *
     *   ≥ 80  → STRONG BUY
     *   ≥ 60  → BUY
     *   ≥ 40  → NEUTRAL (positive tilt)
     *   ≥ 20  → NEUTRAL (negative tilt)
     *   < 20  → SELL
     */
    String computeGrade(int totalScore) {
        if (totalScore >= 80) return "STRONG_BUY";
        if (totalScore >= 60) return "BUY";
        if (totalScore >= 40) return "NEUTRAL_POSITIVE";
        if (totalScore >= 20) return "NEUTRAL_NEGATIVE";
        return "SELL";
    }
}
