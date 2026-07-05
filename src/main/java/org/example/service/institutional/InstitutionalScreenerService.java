package org.example.service.institutional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.InstitutionalHoldingDTO;
import org.example.dto.InstitutionalScoreDTO;
import org.example.dto.ScreenerResultDTO;
import org.example.entity.InstitutionalHolding;
import org.example.entity.Stock;
import org.example.repository.BlockDealRepository;
import org.example.repository.BulkDealRepository;
import org.example.repository.InstitutionalHoldingRepository;
import org.example.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Institutional Screeners
 *
 * Implements 7 stock screeners based on institutional activity:
 * 1. FII Accumulation Stocks
 * 2. DII Accumulation Stocks
 * 3. Mutual Fund Accumulation Stocks
 * 4. Institutional Strong Buy
 * 5. Institutional + Price Action Buy
 * 6. Recent Bulk Deal Stocks
 * 7. Recent Block Deal Stocks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstitutionalScreenerService {

    private final InstitutionalHoldingRepository holdingRepository;
    private final InstitutionalScoreService scoreService;
    private final BulkDealRepository bulkDealRepository;
    private final BlockDealRepository blockDealRepository;
    private final StockRepository stockRepository;

    // =====================================================================
    // Screener 1: FII Accumulation Stocks
    // =====================================================================
    // Criteria:
    //   - FII holding increased QoQ (fiiChangeQoq > 0.5%)
    //   - OR (if FII data not available) promoter holding decreased > 1%
    // =====================================================================
    public List<ScreenerResultDTO> screenerFiiAccumulation() {
        List<Stock> stocks = stockRepository.findAll();
        List<ScreenerResultDTO> results = new ArrayList<>();

        for (Stock stock : stocks) {
            ScreenerResultDTO result = checkFiiAccumulation(stock);
            if (result != null && result.isFiiAccumulation()) {
                results.add(result);
            }
        }

        results.sort((a, b) -> {
            BigDecimal aChange = a.getFiiChangeQoq() != null ? a.getFiiChangeQoq() : BigDecimal.ZERO;
            BigDecimal bChange = b.getFiiChangeQoq() != null ? b.getFiiChangeQoq() : BigDecimal.ZERO;
            return bChange.compareTo(aChange);
        });

        return results;
    }

    private ScreenerResultDTO checkFiiAccumulation(Stock stock) {
        ScreenerResultDTO result = baseScreenerResult(stock);
        if (result == null) return null;

        Optional<InstitutionalHolding> latest = holdingRepository.findLatestHolding(stock.getId());
        if (latest.isPresent()) {
            InstitutionalHolding h = latest.get();
            result.setFiiHoldingPct(h.getFiiHoldingPct());
            result.setFiiChangeQoq(h.getFiiChangeQoq());

            // If FII data available, check change
            if (h.getFiiHoldingPct() != null && h.getFiiChangeQoq() != null) {
                result.setFiiAccumulation(h.getFiiChangeQoq().doubleValue() >= 0.5);
                return result;
            }

            // Fallback: promoter decreased > 1% suggests institutional interest
            if (h.getPromoterChangeQoq() != null) {
                result.setFiiAccumulation(h.getPromoterChangeQoq().doubleValue() <= -1.0);
                return result;
            }
        }

        result.setFiiAccumulation(false);
        return result;
    }

    // =====================================================================
    // Screener 2: DII Accumulation Stocks
    // =====================================================================
    // Criteria:
    //   - DII holding increased QoQ (diiChangeQoq > 0.5%)
    // =====================================================================
    public List<ScreenerResultDTO> screenerDiiAccumulation() {
        List<Stock> stocks = stockRepository.findAll();
        List<ScreenerResultDTO> results = new ArrayList<>();

        for (Stock stock : stocks) {
            ScreenerResultDTO result = checkDiiAccumulation(stock);
            if (result != null && result.isDiiAccumulation()) {
                results.add(result);
            }
        }

        results.sort((a, b) -> {
            BigDecimal aChange = a.getDiiChangeQoq() != null ? a.getDiiChangeQoq() : BigDecimal.ZERO;
            BigDecimal bChange = b.getDiiChangeQoq() != null ? b.getDiiChangeQoq() : BigDecimal.ZERO;
            return bChange.compareTo(aChange);
        });

        return results;
    }

    private ScreenerResultDTO checkDiiAccumulation(Stock stock) {
        ScreenerResultDTO result = baseScreenerResult(stock);
        if (result == null) return null;

        Optional<InstitutionalHolding> latest = holdingRepository.findLatestHolding(stock.getId());
        if (latest.isPresent()) {
            InstitutionalHolding h = latest.get();
            result.setDiiHoldingPct(h.getDiiHoldingPct());
            result.setDiiChangeQoq(h.getDiiChangeQoq());

            if (h.getDiiHoldingPct() != null && h.getDiiChangeQoq() != null) {
                result.setDiiAccumulation(h.getDiiChangeQoq().doubleValue() >= 0.5);
            } else {
                result.setDiiAccumulation(false);
            }
        } else {
            result.setDiiAccumulation(false);
        }
        return result;
    }

    // =====================================================================
    // Screener 3: Mutual Fund Accumulation Stocks
    // =====================================================================
    // Criteria:
    //   - MF holding increased QoQ (mutualFundChangeQoq > 0.5%)
    // =====================================================================
    public List<ScreenerResultDTO> screenerMutualFundAccumulation() {
        List<Stock> stocks = stockRepository.findAll();
        List<ScreenerResultDTO> results = new ArrayList<>();

        for (Stock stock : stocks) {
            ScreenerResultDTO result = checkMutualFundAccumulation(stock);
            if (result != null && result.isMutualFundAccumulation()) {
                results.add(result);
            }
        }

        results.sort((a, b) -> {
            BigDecimal aChange = a.getMutualFundChangeQoq() != null ? a.getMutualFundChangeQoq() : BigDecimal.ZERO;
            BigDecimal bChange = b.getMutualFundChangeQoq() != null ? b.getMutualFundChangeQoq() : BigDecimal.ZERO;
            return bChange.compareTo(aChange);
        });

        return results;
    }

    private ScreenerResultDTO checkMutualFundAccumulation(Stock stock) {
        ScreenerResultDTO result = baseScreenerResult(stock);
        if (result == null) return null;

        Optional<InstitutionalHolding> latest = holdingRepository.findLatestHolding(stock.getId());
        if (latest.isPresent()) {
            InstitutionalHolding h = latest.get();
            result.setMutualFundHoldingPct(h.getMutualFundHoldingPct());
            result.setMutualFundChangeQoq(h.getMutualFundChangeQoq());

            if (h.getMutualFundHoldingPct() != null && h.getMutualFundChangeQoq() != null) {
                result.setMutualFundAccumulation(h.getMutualFundChangeQoq().doubleValue() >= 0.5);
            } else {
                result.setMutualFundAccumulation(false);
            }
        } else {
            result.setMutualFundAccumulation(false);
        }
        return result;
    }

    // =====================================================================
    // Screener 4: Institutional Strong Buy
    // =====================================================================
    // Criteria:
    //   - Institutional score >= 60 (composite of FII + DII + MF + deals + volume + price)
    // =====================================================================
    public List<ScreenerResultDTO> screenerInstitutionalStrongBuy() {
        List<InstitutionalScoreDTO> scores = scoreService.computeAllScores();
        List<ScreenerResultDTO> results = new ArrayList<>();

        for (InstitutionalScoreDTO score : scores) {
            if (score.getTotalScore() >= 60) {
                ScreenerResultDTO result = ScreenerResultDTO.builder()
                        .stockId(score.getStockId())
                        .symbol(score.getSymbol())
                        .name(score.getName())
                        .sector(score.getSector())
                        .institutionalStrongBuy(true)
                        .institutionalScore(score.getTotalScore())
                        .institutionalAndPriceActionBuy(false)
                        .fiiAccumulation(false)
                        .diiAccumulation(false)
                        .mutualFundAccumulation(false)
                        .recentBulkDeal(false)
                        .recentBlockDeal(false)
                        .latestPrice(score.getLatestPrice())
                        .fiiHoldingPct(score.getFiiHoldingPct())
                        .diiHoldingPct(score.getDiiHoldingPct())
                        .mutualFundHoldingPct(score.getMutualFundHoldingPct())
                        .fiiChangeQoq(score.getFiiChangeQoq())
                        .diiChangeQoq(score.getDiiChangeQoq())
                        .mutualFundChangeQoq(score.getMutualFundChangeQoq())
                        .signalRecommendation(score.getSignalRecommendation())
                        .build();
                results.add(result);
            }
        }
        results.sort((a, b) -> Integer.compare(b.getInstitutionalScore(), a.getInstitutionalScore()));
        return results;
    }

    // =====================================================================
    // Screener 5: Institutional + Price Action Buy
    // =====================================================================
    // Criteria:
    //   - Institutional score >= 40
    //   - AND existing technical signal is BUY or STRONG BUY
    // =====================================================================
    public List<ScreenerResultDTO> screenerInstitutionalAndPriceAction() {
        List<InstitutionalScoreDTO> scores = scoreService.computeAllScores();
        List<ScreenerResultDTO> results = new ArrayList<>();

        for (InstitutionalScoreDTO score : scores) {
            if (score.getTotalScore() >= 40
                    && ("BUY".equals(score.getSignalRecommendation())
                        || "STRONG BUY".equals(score.getSignalRecommendation()))) {

                ScreenerResultDTO result = ScreenerResultDTO.builder()
                        .stockId(score.getStockId())
                        .symbol(score.getSymbol())
                        .name(score.getName())
                        .sector(score.getSector())
                        .institutionalAndPriceActionBuy(true)
                        .institutionalScore(score.getTotalScore())
                        .institutionalStrongBuy(false)
                        .fiiAccumulation(false)
                        .diiAccumulation(false)
                        .mutualFundAccumulation(false)
                        .recentBulkDeal(false)
                        .recentBlockDeal(false)
                        .latestPrice(score.getLatestPrice())
                        .fiiHoldingPct(score.getFiiHoldingPct())
                        .diiHoldingPct(score.getDiiHoldingPct())
                        .mutualFundHoldingPct(score.getMutualFundHoldingPct())
                        .fiiChangeQoq(score.getFiiChangeQoq())
                        .diiChangeQoq(score.getDiiChangeQoq())
                        .mutualFundChangeQoq(score.getMutualFundChangeQoq())
                        .signalRecommendation(score.getSignalRecommendation())
                        .build();
                results.add(result);
            }
        }
        results.sort((a, b) -> Integer.compare(b.getInstitutionalScore(), a.getInstitutionalScore()));
        return results;
    }

    // =====================================================================
    // Screener 6: Recent Bulk Deal Stocks
    // =====================================================================
    // Criteria:
    //   - Any institutional bulk BUY in last 30 days
    // =====================================================================
    public List<ScreenerResultDTO> screenerRecentBulkDeals() {
        LocalDate since = LocalDate.now().minusDays(30);
        List<Object[]> counts = bulkDealRepository.countInstitutionalBuysGroupedByStock(since);
        List<ScreenerResultDTO> results = new ArrayList<>();

        for (Object[] row : counts) {
            Long stockId = (Long) row[0];
            Long dealCount = (Long) row[1];

            Stock stock = stockRepository.findById(stockId).orElse(null);
            if (stock == null) continue;

            ScreenerResultDTO result = baseScreenerResult(stock);
            if (result == null) continue;
            result.setRecentBulkDeal(true);
            result.setInstitutionalScore(dealCount.intValue());

            Map<String, Object> extra = new HashMap<>();
            extra.put("bulkDealCount", dealCount);
            result.setAdditionalData(extra);

            results.add(result);
        }

        results.sort((a, b) -> {
            int aCount = a.getAdditionalData() != null
                    ? (int) a.getAdditionalData().getOrDefault("bulkDealCount", 0) : 0;
            int bCount = b.getAdditionalData() != null
                    ? (int) b.getAdditionalData().getOrDefault("bulkDealCount", 0) : 0;
            return Integer.compare(bCount, aCount);
        });

        return results;
    }

    // =====================================================================
    // Screener 7: Recent Block Deal Stocks
    // =====================================================================
    // Criteria:
    //   - Any institutional block BUY in last 30 days
    // =====================================================================
    public List<ScreenerResultDTO> screenerRecentBlockDeals() {
        LocalDate since = LocalDate.now().minusDays(30);
        List<Object[]> counts = blockDealRepository.countInstitutionalBuysGroupedByStock(since);
        List<ScreenerResultDTO> results = new ArrayList<>();

        for (Object[] row : counts) {
            Long stockId = (Long) row[0];
            Long dealCount = (Long) row[1];

            Stock stock = stockRepository.findById(stockId).orElse(null);
            if (stock == null) continue;

            ScreenerResultDTO result = baseScreenerResult(stock);
            if (result == null) continue;
            result.setRecentBlockDeal(true);
            result.setInstitutionalScore(dealCount.intValue());

            Map<String, Object> extra = new HashMap<>();
            extra.put("blockDealCount", dealCount);
            result.setAdditionalData(extra);

            results.add(result);
        }

        results.sort((a, b) -> {
            int aCount = a.getAdditionalData() != null
                    ? (int) a.getAdditionalData().getOrDefault("blockDealCount", 0) : 0;
            int bCount = b.getAdditionalData() != null
                    ? (int) b.getAdditionalData().getOrDefault("blockDealCount", 0) : 0;
            return Integer.compare(bCount, aCount);
        });

        return results;
    }

    // =====================================================================
    // All Screeners Combined
    // =====================================================================
    public List<ScreenerResultDTO> screenerAll() {
        List<Stock> stocks = stockRepository.findAll();
        List<ScreenerResultDTO> results = new ArrayList<>();

        for (Stock stock : stocks) {
            ScreenerResultDTO result = baseScreenerResult(stock);
            if (result == null) continue;

            // Check FII
            ScreenerResultDTO fiiCheck = checkFiiAccumulation(stock);
            if (fiiCheck != null) {
                result.setFiiAccumulation(fiiCheck.isFiiAccumulation());
                result.setFiiHoldingPct(fiiCheck.getFiiHoldingPct());
                result.setFiiChangeQoq(fiiCheck.getFiiChangeQoq());

                // Also get DII and MF data from this
                ScreenerResultDTO diiCheck = checkDiiAccumulation(stock);
                if (diiCheck != null) {
                    result.setDiiAccumulation(diiCheck.isDiiAccumulation());
                    result.setDiiHoldingPct(diiCheck.getDiiHoldingPct());
                    result.setDiiChangeQoq(diiCheck.getDiiChangeQoq());
                }

                ScreenerResultDTO mfCheck = checkMutualFundAccumulation(stock);
                if (mfCheck != null) {
                    result.setMutualFundAccumulation(mfCheck.isMutualFundAccumulation());
                    result.setMutualFundHoldingPct(mfCheck.getMutualFundHoldingPct());
                    result.setMutualFundChangeQoq(mfCheck.getMutualFundChangeQoq());
                }
            }

            // Check institutional score
            InstitutionalScoreDTO score = scoreService.computeScore(stock.getId());
            if (score != null) {
                result.setInstitutionalScore(score.getTotalScore());
                result.setSignalRecommendation(score.getSignalRecommendation());
                result.setInstitutionalStrongBuy(score.getTotalScore() >= 60);
                result.setInstitutionalAndPriceActionBuy(score.getTotalScore() >= 40
                        && "BUY".equals(score.getSignalRecommendation())
                        || "STRONG BUY".equals(score.getSignalRecommendation()));
            }

            // Check bulk/block deals
            LocalDate monthAgo = LocalDate.now().minusDays(30);
            result.setRecentBulkDeal(bulkDealRepository
                    .countInstitutionalBuysSince(stock.getId(), monthAgo) > 0);
            result.setRecentBlockDeal(blockDealRepository
                    .countInstitutionalBuysSince(stock.getId(), monthAgo) > 0);

            results.add(result);
        }

        return results;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private ScreenerResultDTO baseScreenerResult(Stock stock) {
        if (stock == null) return null;
        return ScreenerResultDTO.builder()
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .sector(stock.getSector())
                .latestPrice(stock.getLastTradedPrice())
                .fiiAccumulation(false)
                .diiAccumulation(false)
                .mutualFundAccumulation(false)
                .institutionalStrongBuy(false)
                .institutionalAndPriceActionBuy(false)
                .recentBulkDeal(false)
                .recentBlockDeal(false)
                .build();
    }
}
