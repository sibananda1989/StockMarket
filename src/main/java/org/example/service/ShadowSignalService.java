package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.SignalDTO;
import org.example.entity.DailyPrice;
import org.example.entity.ShadowSignalRecord;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.ShadowSignalRecordRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.calculator.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShadowSignalService {

    private final StockService stockService;
    private final DailyPriceRepository dailyPriceRepository;
    private final ShadowSignalRecordRepository shadowSignalRecordRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final CorporateEventService corporateEventService;
    private final SupportResistanceService supportResistanceService;
    private final FiiDiiService fiiDiiService;
    private final PriceAggregationService aggregationService;
    private final BreakoutDetector breakoutDetector;

    private SignalService signalService;

    @Lazy
    @Autowired
    public void setSignalService(SignalService signalService) {
        this.signalService = signalService;
    }

    @Value("${shadow.signal.version:old-logic}")
    private String shadowVersion;

    @Value("${shadow.signal.enabled:true}")
    private boolean shadowEnabled;

    @Async
    @Transactional
    public CompletableFuture<Void> computeAndStoreShadowSignal(Long stockId) {
        if (!shadowEnabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            Stock stock = stockService.getStockById(stockId);
            if (stock == null) {
                log.debug("Stock not found for shadow signal computation: {}", stockId);
                return CompletableFuture.completedFuture(null);
            }
            
            List<DailyPrice> prices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stock.getId());
            if (prices.size() < 20) {
                log.debug("Insufficient prices for shadow signal: {} ({} bars)", stockId, prices.size());
                return CompletableFuture.completedFuture(null);
            }
            
            SignalDTO shadowDto = computeShadowSignal(stock, prices);
            if (shadowDto != null) {
                saveShadowSignalRecord(shadowDto);
            }
            
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.warn("Error computing shadow signal for stock {}: {}", stockId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async
    @Transactional
    public CompletableFuture<Void> computeAndStoreAllShadowSignals() {
        if (!shadowEnabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            List<Stock> stocks = stockService.getAllStocks();
            List<CompletableFuture<Void>> futures = stocks.parallelStream()
                .map(stock -> computeAndStoreShadowSignal(stock.getId()))
                .collect(Collectors.toList());
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.warn("Error computing all shadow signals: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    public SignalDTO computeShadowSignal(Stock stock, List<DailyPrice> prices) {
        if (shadowVersion.equals("old-logic")) {
            return computeOldLogicSignal(stock, prices);
        } else {
            return computeNewLogicSignal(stock, prices);
        }
    }

    private SignalDTO computeOldLogicSignal(Stock stock, List<DailyPrice> prices) {
        SignalDTO dto = new SignalDTO();
        dto.setStockId(stock.getId());
        dto.setSymbol(stock.getSymbol());
        dto.setName(stock.getName());
        dto.setSector(stock.getSector());
        
        List<BigDecimal> closes = prices.stream()
            .map(DailyPrice::getClosingPrice)
            .collect(Collectors.toList());
        
        BigDecimal currentPrice = closes.get(closes.size() - 1);
        dto.setLatestPrice(currentPrice);
        
        // Old SMA calculation (population stddev instead of sample)
        dto.setSma20(calculateSMA(closes, 20));
        dto.setSma50(closes.size() >= 50 ? calculateSMA(closes, 50) : null);
        
        // Old MACD calculation (inline, no DB fallback)
        computeOldMacd(dto, closes);
        
        // Old RSI calculation (inline, no DB fallback)
        dto.setRsi14(TechnicalAnalysisUtils.calculateRsi14(prices));
        
        // Old Bollinger Bands (population stddev)
        computeOldBollingerBands(dto, closes);
        
        // Old 52-week high/low
        compute52WeekHighLow(dto, prices);
        
        // Old support/resistance
        computeOldSupportResistance(dto, closes);
        
        // Old divergence detection
        List<BigDecimal> rsiHistory = computeOldRsiHistory(prices);
        dto.setBullishDivergence(TechnicalAnalysisUtils.hasBullishDivergence(prices, rsiHistory));
        dto.setBearishDivergence(TechnicalAnalysisUtils.hasBearishDivergence(prices, rsiHistory));
        
        // Old multi-timeframe RSI
        List<DailyPrice> weeklyPrices = aggregationService.aggregateWeekly(prices);
        dto.setWeeklyRsi(TechnicalAnalysisUtils.calculateRsi14(weeklyPrices));
        List<DailyPrice> monthlyPrices = aggregationService.aggregateMonthly(prices);
        dto.setMonthlyRsi(TechnicalAnalysisUtils.calculateRsi14(monthlyPrices));
        
        // Old volume confirmation
        computeOldVolumeConfirmation(dto, prices);
        
        // Old event risk
        List<org.example.entity.CorporateEvent> events = corporateEventService.getEventsForSymbol(stock.getSymbol());
        dto.setEventRisk(events != null && !events.isEmpty());
        
        // Old scoring logic
        int score = computeOldWeightedScore(dto, stock, prices);
        int fiidiiAdj = fiiDiiService.getScoreAdjustment();
        dto.setFiidiiScore(fiidiiAdj);
        score += fiidiiAdj;
        
        // Old recommendation thresholds
        // NOTE: Intentionally diverges from SignalService.mapRecommendation() (7/3/-4/-7).
        // Old logic uses (7/5/-5/-7) as an A/B baseline — shadow signals are an
        // independent comparison track, not a live signal source.
        if (score >= 7) dto.setRecommendation("STRONG BUY");
        else if (score >= 5) dto.setRecommendation("BUY");
        else if (score <= -7) dto.setRecommendation("STRONG SELL");
        else if (score <= -5) dto.setRecommendation("SELL");
        else dto.setRecommendation("HOLD");
        
        dto.setCompositeScore(score);
        
        // Old confidence score
        dto.setConfidenceScore(computeOldConfidenceScore(dto));
        
        return dto;
    }

    private SignalDTO computeNewLogicSignal(Stock stock, List<DailyPrice> prices) {
        return signalService.computeShadowDto(stock, prices);
    }

    private BigDecimal calculateSMA(List<BigDecimal> closes, int period) {
        if (closes.size() < period) return null;
        BigDecimal sum = closes.subList(closes.size() - period, closes.size()).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    private void computeOldMacd(SignalDTO dto, List<BigDecimal> closes) {
        if (closes.size() < 26) return;
        
        BigDecimal k12 = BigDecimal.valueOf(2.0 / 13);
        BigDecimal k26 = BigDecimal.valueOf(2.0 / 27);
        BigDecimal kSignal = BigDecimal.valueOf(2.0 / 10);
        
        BigDecimal ema12 = closes.subList(0, 12).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        BigDecimal ema26 = closes.subList(0, 26).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(26), 4, RoundingMode.HALF_UP);
        
        for (int i = 12; i < 26; i++) {
            ema12 = closes.get(i).multiply(k12).add(ema12.multiply(BigDecimal.ONE.subtract(k12)));
        }
        
        List<BigDecimal> macdValues = new ArrayList<>();
        BigDecimal emaSignal = null;
        for (int i = 26; i < closes.size(); i++) {
            ema12 = closes.get(i).multiply(k12).add(ema12.multiply(BigDecimal.ONE.subtract(k12)));
            ema26 = closes.get(i).multiply(k26).add(ema26.multiply(BigDecimal.ONE.subtract(k26)));
            macdValues.add(ema12.subtract(ema26));
        }
        
        if (macdValues.size() >= 9) {
            emaSignal = macdValues.subList(0, 9).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(9), 4, RoundingMode.HALF_UP);
            for (int i = 9; i < macdValues.size(); i++) {
                emaSignal = macdValues.get(i).multiply(kSignal).add(emaSignal.multiply(BigDecimal.ONE.subtract(kSignal)));
            }
        }
        
        dto.setMacd(ema12.subtract(ema26));
        if (emaSignal != null) {
            dto.setMacdSignal(emaSignal);
            dto.setMacdHistogram(dto.getMacd().subtract(emaSignal));
        }
    }

    private void computeOldBollingerBands(SignalDTO dto, List<BigDecimal> closes) {
        if (closes.size() < 20 || dto.getSma20() == null) return;
        
        // Old logic: population stddev (/period instead of /(period-1))
        double sum = closes.subList(closes.size() - 20, closes.size()).stream()
            .mapToDouble(BigDecimal::doubleValue).sum();
        double sma = sum / 20;
        
        double varianceSum = closes.subList(closes.size() - 20, closes.size()).stream()
            .mapToDouble(price -> Math.pow(price.doubleValue() - sma, 2))
            .sum();
        double stddev = Math.sqrt(varianceSum / 20); // Population stddev (old bug)
        
        dto.setBollingerUpper(BigDecimal.valueOf(sma + (2 * stddev)).setScale(2, RoundingMode.HALF_UP));
        dto.setBollingerLower(BigDecimal.valueOf(sma - (2 * stddev)).setScale(2, RoundingMode.HALF_UP));
        dto.setBollingerMiddle(dto.getSma20());
    }

    private void compute52WeekHighLow(SignalDTO dto, List<DailyPrice> prices) {
        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        List<DailyPrice> yearPrices = prices.stream()
            .filter(p -> !p.getPriceDate().isBefore(oneYearAgo))
            .collect(Collectors.toList());
        
        if (!yearPrices.isEmpty()) {
            BigDecimal high52 = yearPrices.stream().map(DailyPrice::getClosingPrice).max(BigDecimal::compareTo).orElse(dto.getLatestPrice());
            BigDecimal low52 = yearPrices.stream().map(DailyPrice::getClosingPrice).min(BigDecimal::compareTo).orElse(dto.getLatestPrice());
            dto.setHigh52Week(high52);
            dto.setLow52Week(low52);
            
            if (high52.compareTo(BigDecimal.ZERO) > 0) {
                dto.setPctFrom52WHigh(dto.getLatestPrice().subtract(high52).multiply(BigDecimal.valueOf(100)).divide(high52, 2, RoundingMode.HALF_UP));
            }
            if (low52.compareTo(BigDecimal.ZERO) > 0) {
                dto.setPctFrom52WLow(dto.getLatestPrice().subtract(low52).multiply(BigDecimal.valueOf(100)).divide(low52, 2, RoundingMode.HALF_UP));
            }
        }
    }

    private void computeOldSupportResistance(SignalDTO dto, List<BigDecimal> closes) {
        List<BigDecimal> sortedCloses = closes.stream().sorted().collect(Collectors.toList());
        int tenPct = Math.max(1, sortedCloses.size() / 10);
        
        dto.setSupportLevel(sortedCloses.subList(0, tenPct).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(tenPct), 2, RoundingMode.HALF_UP));
        
        dto.setResistanceLevel(sortedCloses.subList(sortedCloses.size() - tenPct, sortedCloses.size()).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(tenPct), 2, RoundingMode.HALF_UP));
    }

    private List<BigDecimal> computeOldRsiHistory(List<DailyPrice> prices) {
        List<BigDecimal> rsiHistory = new ArrayList<>(Collections.nCopies(prices.size(), BigDecimal.ZERO));
        if (prices.size() >= 15) {
            BigDecimal avgGain = BigDecimal.ZERO;
            BigDecimal avgLoss = BigDecimal.ZERO;
            for (int i = 1; i <= 14; i++) {
                BigDecimal change = prices.get(i).getClosingPrice().subtract(prices.get(i - 1).getClosingPrice());
                if (change.compareTo(BigDecimal.ZERO) > 0) {
                    avgGain = avgGain.add(change);
                } else {
                    avgLoss = avgLoss.add(change.abs());
                }
            }
            avgGain = avgGain.divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
            avgLoss = avgLoss.divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
            
            if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
                rsiHistory.set(14, BigDecimal.valueOf(100));
            } else {
                BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
                rsiHistory.set(14, BigDecimal.valueOf(100).subtract(
                    BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP)));
            }
            
            for (int i = 15; i < prices.size(); i++) {
                BigDecimal change = prices.get(i).getClosingPrice().subtract(prices.get(i - 1).getClosingPrice());
                BigDecimal gain = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
                BigDecimal loss = change.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ZERO : change.abs();
                avgGain = avgGain.multiply(BigDecimal.valueOf(13)).add(gain).divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                avgLoss = avgLoss.multiply(BigDecimal.valueOf(13)).add(loss).divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                
                if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
                    rsiHistory.set(i, BigDecimal.valueOf(100));
                } else {
                    BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
                    rsiHistory.set(i, BigDecimal.valueOf(100).subtract(
                        BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP)));
                }
            }
        }
        return rsiHistory;
    }

    private void computeOldVolumeConfirmation(SignalDTO dto, List<DailyPrice> prices) {
        if (prices.size() >= 20) {
            List<DailyPrice> last20 = prices.subList(prices.size() - 20, prices.size());
            double avgVol = last20.stream().mapToLong(p -> p.getVolume() != null ? p.getVolume() : 0L).average().orElse(0);
            long currentVol = prices.get(prices.size() - 1).getVolume() != null ? prices.get(prices.size() - 1).getVolume() : 0L;
            dto.setVolumeConfirmed(currentVol >= avgVol * 1.2);
        }
    }

    private int computeOldWeightedScore(SignalDTO dto, Stock stock, List<DailyPrice> prices) {
        int score = 0;
        
        // Old RSI scoring
        if (dto.getRsi14() != null) {
            double rsi = dto.getRsi14().doubleValue();
            if (rsi > 70) score -= 2;
            else if (rsi > 60) score -= 1;
            else if (rsi < 30) score += 2;
            else if (rsi < 40) score += 1;
            dto.setRsiScore(rsi > 70 ? -2 : (rsi > 60 ? -1 : (rsi < 30 ? 2 : (rsi < 40 ? 1 : 0))));
        }
        
        // Old SMA scoring
        if (dto.getSma20() != null && dto.getSma50() != null) {
            if (dto.getLatestPrice().compareTo(dto.getSma20()) > 0) score += 1;
            if (dto.getLatestPrice().compareTo(dto.getSma50()) > 0) score += 1;
            if (dto.getSma20().compareTo(dto.getSma50()) > 0) score += 1;
            dto.setSmaScore((dto.getLatestPrice().compareTo(dto.getSma20()) > 0 ? 1 : 0) +
                          (dto.getLatestPrice().compareTo(dto.getSma50()) > 0 ? 1 : 0) +
                          (dto.getSma20().compareTo(dto.getSma50()) > 0 ? 1 : 0));
        }
        
        // Old Bollinger scoring
        if (dto.getBollingerUpper() != null && dto.getBollingerLower() != null) {
            if (dto.getLatestPrice().compareTo(dto.getBollingerUpper()) > 0) score -= 2;
            else if (dto.getLatestPrice().compareTo(dto.getBollingerLower()) < 0) score += 2;
            else if (dto.getLatestPrice().compareTo(dto.getBollingerMiddle()) > 0) score += 1;
            else score -= 1;
            
            dto.setBollingerScore(
                dto.getLatestPrice().compareTo(dto.getBollingerUpper()) > 0 ? -2 :
                dto.getLatestPrice().compareTo(dto.getBollingerLower()) < 0 ? 2 :
                dto.getLatestPrice().compareTo(dto.getBollingerMiddle()) > 0 ? 1 : -1
            );
        }
        
        // Old 52-week scoring
        if (dto.getPctFrom52WLow() != null) {
            double pct = dto.getPctFrom52WLow().doubleValue();
            if (pct <= 15) score += 2;
            else if (pct <= 30) score += 1;
            dto.setWeek52Score(pct <= 15 ? 2 : (pct <= 30 ? 1 : 0));
        }
        
        // Old MACD scoring
        if (dto.getMacdHistogram() != null) {
            if (dto.getMacdHistogram().compareTo(BigDecimal.ZERO) > 0) score += 1;
            else score -= 1;
            dto.setMacdScore(dto.getMacdHistogram().compareTo(BigDecimal.ZERO) > 0 ? 1 : -1);
        }
        
        // Old divergence scoring
        if (dto.isBullishDivergence()) score += 1;
        if (dto.isBearishDivergence()) score -= 1;
        dto.setDivergenceScore((dto.isBullishDivergence() ? 1 : 0) + (dto.isBearishDivergence() ? -1 : 0));
        
        // Old weekly/monthly confluence
        if (dto.getWeeklyRsi() != null) {
            if (dto.getWeeklyRsi().doubleValue() > 60) dto.setWeeklyConfluenceScore(1);
            else if (dto.getWeeklyRsi().doubleValue() < 40) dto.setWeeklyConfluenceScore(-1);
        }
        if (dto.getMonthlyRsi() != null) {
            if (dto.getMonthlyRsi().doubleValue() > 60) dto.setMonthlyConfluenceScore(1);
            else if (dto.getMonthlyRsi().doubleValue() < 40) dto.setMonthlyConfluenceScore(-1);
        }
        
        // Old volume penalty
        if (!dto.isVolumeConfirmed()) score -= 1;
        
        return score;
    }

    private Long computeOldConfidenceScore(SignalDTO dto) {
        int composite = dto.getCompositeScore();
        long base = 50 + (composite * 5L);
        base = Math.max(0, Math.min(100, base));
        
        if (dto.isVolumeConfirmed()) base += 10;
        if (dto.isEventRisk()) base -= 20;
        
        return Math.max(0, Math.min(100, base));
    }

    @Transactional
    public void saveShadowSignalRecord(SignalDTO dto) {
        try {
            LocalDate today = LocalDate.now();
            Optional<ShadowSignalRecord> existing = shadowSignalRecordRepository
                .findByStockIdAndRecordedAtAndShadowVersion(dto.getStockId(), today, shadowVersion);
            
            ShadowSignalRecord record = existing.orElseGet(ShadowSignalRecord::new);
            record.setStockId(dto.getStockId());
            record.setRecordedAt(today);
            record.setShadowVersion(shadowVersion);
            record.setRecommendation(dto.getRecommendation());
            record.setCompositeScore(dto.getCompositeScore());
            record.setConfidenceScore(dto.getConfidenceScore());
            record.setIndicatorCoverage(16); // Old logic had 16 factors
            record.setPriceAtSignal(dto.getLatestPrice());
            
            // Store all scoring components
            record.setRsi14(dto.getRsi14());
            record.setSma20(dto.getSma20());
            record.setSma50(dto.getSma50());
            record.setMacd(dto.getMacd());
            record.setMacdSignal(dto.getMacdSignal());
            record.setMacdHistogram(dto.getMacdHistogram());
            record.setBollingerUpper(dto.getBollingerUpper());
            record.setBollingerLower(dto.getBollingerLower());
            
            record.setDivergenceScore(dto.getDivergenceScore());
            record.setWeeklyConfluenceScore(dto.getWeeklyConfluenceScore());
            record.setMonthlyConfluenceScore(dto.getMonthlyConfluenceScore());
            record.setRsiScore(dto.getRsiScore());
            record.setBollingerScore(dto.getBollingerScore());
            record.setSmaScore(dto.getSmaScore());
            record.setWeek52Score(dto.getWeek52Score());
            record.setMacdScore(dto.getMacdScore());
            
            // Store intermediate scores
            record.setRawTrendScore(0);
            record.setRawMomentumScore(0);
            record.setRawStructureScore(0);
            record.setAdxMultiplier(BigDecimal.ONE);
            record.setScoreAfterAdx(dto.getCompositeScore());
            record.setScoreAfterCandlestick(dto.getCompositeScore());
            record.setScoreAfterReversal(dto.getCompositeScore());
            record.setScoreAfterDiscount(dto.getCompositeScore());
            
            shadowSignalRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Error saving shadow signal record for stock {}: {}", dto.getStockId(), e.getMessage(), e);
        }
    }

    public String getShadowVersion() {
        return shadowVersion;
    }

    @Transactional(readOnly = true)
    public List<ShadowSignalRecord> getShadowSignalsForDate(LocalDate date) {
        return shadowSignalRecordRepository.findByRecordedAtAndShadowVersion(date, shadowVersion);
    }

    @Transactional(readOnly = true)
    public List<ShadowSignalRecord> getDivergentSignalsForDate(LocalDate date) {
        return shadowSignalRecordRepository.findDivergentSignals(date, shadowVersion);
    }
}