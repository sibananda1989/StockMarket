package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.example.dto.SignalDTO;
import org.example.dto.SignalHistoryPoint;
import org.example.dto.SupportResistanceDto;
import org.example.entity.CorporateEvent;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.SignalHistoricalPerformance;
import org.example.entity.SignalRecord;
import org.example.entity.Stock;
import org.example.entity.SupportResistanceLevel;
import org.example.entity.TechnicalIndicator;
import org.example.repository.CorporateEventRepository;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioHoldingRepository;

import org.example.repository.SignalHistoricalPerformanceRepository;
import org.example.repository.SignalRecordRepository;
import org.example.repository.SupportResistanceLevelRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.service.calculator.ATRCalculator;
import org.example.service.calculator.AdxCalculator;
import org.example.service.calculator.CandlestickPatternCalculator;
import org.example.service.calculator.ReversalDetector;
import org.example.service.calculator.CCICalculator;
import org.example.service.calculator.RocCalculator;
import org.example.service.calculator.StochDCalculator;
import org.example.service.calculator.StochKCalculator;
import org.example.service.calculator.StochRsiCalculator;
import org.example.service.calculator.UltimateOscillatorCalculator;
import org.example.service.calculator.WilliamsRCalculator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.example.service.ShadowSignalService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignalService {

    // ── Recommendation thresholds (single source of truth) ──
    static final int STRONG_BUY_THRESHOLD  =  7;
    static final int BUY_THRESHOLD         =  3;
    static final int SELL_THRESHOLD        = -4;
    static final int STRONG_SELL_THRESHOLD = -7;

    private final StockService stockService;
    private final DailyPriceRepository dailyPriceRepository;
    private final PriceAggregationService aggregationService;
    private final FiiDiiService fiiDiiService;
    private final CorporateEventService corporateEventService;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final StrategyInfluenceService strategyInfluenceService;
    private final SignalHistoricalPerformanceRepository signalHistoricalPerformanceRepository;
    private final SupportResistanceService supportResistanceService;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final SignalRecordRepository signalRecordRepository;
    private final BreakoutDetector breakoutDetector;
    private final CorporateEventRepository corporateEventRepository;
    private final SupportResistanceLevelRepository supportResistanceLevelRepository;

    @Value("${signal.gate.high-confidence.enabled:false}")
    private boolean highConfidenceGateEnabled;

    @Cacheable(value = "signals", key = "'allSignals'")
    public List<SignalDTO> getAllSignals() {
        List<Stock> stocks = stockService.getAllStocks();
        List<SignalDTO> signals = computeSignalsBulk(stocks);
        
        // Trigger async shadow signal computation for all stocks
        triggerAllShadowSignalComputation();
        
        return signals;
    }
    
    private void triggerAllShadowSignalComputation() {
        try {
            shadowSignalService.computeAndStoreAllShadowSignals();
        } catch (Exception e) {
            log.warn("Could not trigger all shadow signal computations: {}", e.getMessage());
        }
    }
    
    /**
     * Bulk signal computation - fetches all required data in 3 queries,
     * then computes signals in-memory. Reduces N+1 queries to constant 3.
     * This is the optimized path for computing signals for multiple stocks.
     */
    @Transactional(readOnly = true)
    public List<SignalDTO> computeSignalsBulk(List<Stock> stocks) {
        long startTime = System.currentTimeMillis();
        if (stocks.isEmpty()) return Collections.emptyList();
        
        List<Long> stockIds = stocks.stream().map(Stock::getId).toList();
        List<String> symbols = stocks.stream().map(Stock::getSymbol).toList();
        LocalDate today = LocalDate.now();
        
        // BULK FETCH: 3 queries total instead of N×12
        List<CorporateEvent> allEvents = corporateEventRepository
            .findBySymbolInAndEventDateBetween(symbols, today, today.plusDays(5));
        
        List<IndicatorType> types = Arrays.asList(
            IndicatorType.RSI, IndicatorType.MACD_LINE, IndicatorType.MACD_SIGNAL,
            IndicatorType.BOLLINGER_UPPER, IndicatorType.BOLLINGER_LOWER,
            IndicatorType.STOCH_K, IndicatorType.STOCH_D, IndicatorType.WILLIAMS_R,
            IndicatorType.ATR, IndicatorType.CCI, IndicatorType.STOCH_RSI,
            IndicatorType.ADX, IndicatorType.PLUS_DI, IndicatorType.MINUS_DI,
            IndicatorType.ULTIMATE_OSC, IndicatorType.ROC_12, IndicatorType.OBV,
            IndicatorType.VWAP, IndicatorType.TENKAN_SEN, IndicatorType.KIJUN_SEN,
            IndicatorType.SENKOU_SPAN_A, IndicatorType.SENKOU_SPAN_B, IndicatorType.CHIKOU_SPAN
        );
        
        List<TechnicalIndicator> allIndicators = technicalIndicatorRepository
            .findLatestByStockIdsAndTypes(stockIds, types);
        
        List<SupportResistanceLevel> allSR = supportResistanceLevelRepository
            .findLatestByStockIds(stockIds);
        
        // Convert to lookup maps (O(N) in memory)
        Map<String, List<CorporateEvent>> eventsBySymbol = allEvents.stream()
            .collect(Collectors.groupingBy(CorporateEvent::getSymbol));
        
        Map<Long, Map<IndicatorType, BigDecimal>> indicatorsByStock = allIndicators.stream()
            .collect(Collectors.groupingBy(
                ti -> ti.getStock().getId(),
                Collectors.toMap(TechnicalIndicator::getIndicatorType, TechnicalIndicator::getValue)
            ));
        
        Map<Long, SupportResistanceDto> srByStock = allSR.stream()
            .collect(Collectors.groupingBy(
                sr -> sr.getStock().getId(),
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    this::convertToSrDto
                )
            ));
        
        // Compute signals in parallel using pre-fetched data
        List<SignalDTO> signals = stocks.parallelStream()
            .map(stock -> {
                try {
                    List<DailyPrice> prices = dailyPriceRepository
                        .findAllByStockIdOrderByPriceDateAsc(stock.getId());
                    if (prices.size() < 20) return null;
                    
                    return computeBaseSignalDto(
                        stock, prices, false,
                        eventsBySymbol.getOrDefault(stock.getSymbol(), Collections.emptyList()),
                        indicatorsByStock.getOrDefault(stock.getId(), Collections.emptyMap()),
                        srByStock.get(stock.getId())
                    );
                } catch (Exception e) {
                    log.warn("Could not compute signal for {}: {}", stock.getSymbol(), e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Bulk signal computation: {} stocks in {} ms (DB queries: 3)", stocks.size(), elapsed);
        
        return signals;
    }
    
    /**
     * Converts a list of SupportResistanceLevel entities to a SupportResistanceDto.
     * Helper method for bulk computation.
     */
    private SupportResistanceDto convertToSrDto(List<org.example.entity.SupportResistanceLevel> levels) {
        if (levels.isEmpty()) return null;
        
        Long stockId = levels.get(0).getStock().getId();
        Stock stock = stockService.getStockById(stockId);
        SupportResistanceDto dto = new SupportResistanceDto();
        dto.setStockId(stockId);
        dto.setSymbol(stock.getSymbol());
        dto.setCalculationDate(levels.get(0).getCalculationDate());
        
        List<SupportResistanceDto.SwingLevel> swingHighs = new ArrayList<>();
        List<SupportResistanceDto.SwingLevel> swingLows = new ArrayList<>();
        SupportResistanceDto.PivotLevels pivots = new SupportResistanceDto.PivotLevels();
        List<SupportResistanceDto.MajorLevel> majorLevels = new ArrayList<>();
        
        for (org.example.entity.SupportResistanceLevel level : levels) {
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

    /**
     * Compute signals only for stocks held by the given portfolio. Returns empty list if
     * the portfolio does not exist or has no holdings. Server-side filtering avoids
     * sending the entire stock universe over the wire for users with many portfolios.
     */
    @Transactional
    public List<SignalDTO> getSignalsForPortfolio(Long portfolioId) {
        var holdings = portfolioHoldingRepository.findByPortfolioId(portfolioId);
        if (holdings.isEmpty()) {
            return List.of();
        }
        List<Stock> stocks = holdings.stream()
            .map(h -> h.getStock())
            .toList();
        return computeSignalsBulk(stocks);
    }

    @Cacheable(value = "signals", key = "'buySignals'")
    public List<SignalDTO> getBuySignals() {
        return getAllSignals().stream()
                .filter(s -> "BUY".equals(s.getRecommendation()) || "STRONG BUY".equals(s.getRecommendation()))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "signals", key = "'sellSignals'")
    public List<SignalDTO> getSellSignals() {
        return getAllSignals().stream()
                .filter(s -> "SELL".equals(s.getRecommendation()) || "STRONG SELL".equals(s.getRecommendation()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SignalDTO computeSignal(Long stockId) {
        try {
            Stock stock = stockService.getStockById(stockId);
            if (stock == null) return null;
            SignalDTO signal = computeSignal(stock);
            
            // Trigger async shadow signal computation
            triggerShadowSignalComputation(stockId);
            
            return signal;
        } catch (Exception e) {
            log.warn("Could not compute signal for stock ID {}: {}", stockId, e.getMessage(), e);
            return null;
        }
    }

    @Cacheable(value = "signals", key = "'stock_' + #stockId")
    public SignalDTO getComputedSignal(Long stockId) {
        return computeSignal(stockId);
    }

    public SignalDTO computeSignal(Stock stock) {
        List<DailyPrice> prices = dailyPriceRepository
                .findAllByStockIdOrderByPriceDateAsc(stock.getId());
        return computeSignalFromPrices(stock, prices);
    }
    
    private ShadowSignalService shadowSignalService;
    
    @Autowired
    public void setShadowSignalService(ShadowSignalService shadowSignalService) {
        this.shadowSignalService = shadowSignalService;
    }
    
    private void triggerShadowSignalComputation(Long stockId) {
        if (shadowSignalService != null) {
            try {
                shadowSignalService.computeAndStoreShadowSignal(stockId);
            } catch (Exception e) {
                log.warn("Could not trigger shadow signal computation for stock ID {}: {}", stockId, e.getMessage());
            }
        }
    }

    public SignalDTO computeSignalFromPrices(Stock stock, List<DailyPrice> prices) {
        SignalDTO dto = computeBaseSignalDto(stock, prices);
        if (dto == null) return null;

        // --- Actionable Dashboard: Extended fields ---
        BigDecimal atr = computeAtr(stock, prices);
        dto.setAtr(atr);

        // Open/High/Low from latest price
        if (!prices.isEmpty()) {
            DailyPrice latest = prices.get(prices.size() - 1);
            dto.setOpenPrice(latest.getOpeningPrice());
            dto.setHighPrice(latest.getHighPrice());
            dto.setLowPrice(latest.getLowPrice());
        }

        // Target, Stop
        computeTargetAndStop(dto, atr);

        // Signal age must be set before confidence score (confidence uses it)
        dto.setSignalAge(computeSignalAge(prices));

        // Compute indicator coverage: how many of 16 scoring factors had backing data
        computeIndicatorCoverage(dto);

        // Compute rolling accuracy from past records (must come before confidence)
        computeRollingAccuracy(dto);

        // Confidence, Position Size (confidence uses rolling accuracy data)
        dto.setConfidenceScore(computeConfidenceScore(dto));
        dto.setSuggestedPositionSize(computePositionSize(dto));

        // Historical returns
        fetchHistoricalReturns(dto);

        // Persist signal record for accuracy tracking (non-blocking, after confidence)
        saveSignalRecord(dto);

        return dto;
    }

    /**
     * Computes the base signal DTO with all indicators and scoring, but without
     * the extended trade-actionable fields (ATR, target/stop, etc.).
     * This is the core computation shared by the full signal pipeline and the
     * lightweight backtest path.
     */
    private SignalDTO computeBaseSignalDto(Stock stock, List<DailyPrice> prices) {
        return computeBaseSignalDto(stock, prices, false);
    }

    private SignalDTO computeBaseSignalDto(Stock stock, List<DailyPrice> prices, boolean historicalMode) {
        return computeBaseSignalDto(stock, prices, historicalMode, null);
    }

    private SignalDTO computeBaseSignalDto(Stock stock, List<DailyPrice> prices, boolean historicalMode,
                                            List<org.example.entity.CorporateEvent> preFetchedEvents) {
        return computeBaseSignalDto(stock, prices, historicalMode, preFetchedEvents, null, null);
    }
    
    /**
     * Bulk computation version - accepts pre-fetched data for all stocks to avoid N+1 queries.
     */
    private SignalDTO computeBaseSignalDto(Stock stock, List<DailyPrice> prices, boolean historicalMode,
                                            List<org.example.entity.CorporateEvent> allEvents,
                                            Map<IndicatorType, BigDecimal> allIndicators,
                                            SupportResistanceDto allSR) {
        if (prices == null || prices.size() < 20) return null;

        List<BigDecimal> closes = prices.stream()
                .map(DailyPrice::getClosingPrice)
                .collect(Collectors.toList());

        BigDecimal currentPrice = closes.get(closes.size() - 1);

        SignalDTO dto = new SignalDTO();
        dto.setStockId(stock.getId());
        dto.setSymbol(stock.getSymbol());
        dto.setName(stock.getName());
        dto.setSector(stock.getSector());
        dto.setLatestPrice(currentPrice);
        dto.setPnlPercent(stock.getPnlPercent());

        // --- Corporate Event Risk (use pre-fetched events when available to avoid N+1)
        List<org.example.entity.CorporateEvent> events = allEvents != null
                ? allEvents.stream().filter(e -> e.getSymbol().equals(stock.getSymbol())).toList()
                : corporateEventService.getEventsForSymbol(stock.getSymbol());
        dto.setEventRisk(events != null && !events.isEmpty());
        if (dto.isEventRisk()) {
            dto.setEventPurposes(events.stream()
                    .map(e -> e.getPurpose() + " (" + e.getEventDate() + ")")
                    .collect(java.util.stream.Collectors.toList()));
        }

        // --- Fetch DB-based Technical Indicators (skip in historical mode — DB stores only latest) ---
        Map<IndicatorType, BigDecimal> tableIndicators;
        if (historicalMode) {
            tableIndicators = Collections.emptyMap();
        } else if (allIndicators != null) {
            // Use pre-fetched bulk data
            tableIndicators = allIndicators;
        } else {
            // Fallback to individual fetch
            tableIndicators = fetchAdditionalIndicators(dto, stock.getId());
        }

        // --- Basic Indicators: compute SMA always from prices (DB stores only latest, wrong for history) ---
        dto.setSma20(calculateSMA(closes, 20));
        dto.setSma50(closes.size() >= 50 ? calculateSMA(closes, 50) : null);

        // EMA12/26 are not stored in DB — compute inline
        dto.setEma12(calculateEMA(closes, 12));
        dto.setEma26(calculateEMA(closes, 26));

        // --- MACD: prefer DB, fallback to inline O(N) EMA computation ---
        if (tableIndicators.containsKey(IndicatorType.MACD_LINE)) {
            dto.setMacd(tableIndicators.get(IndicatorType.MACD_LINE));
            if (tableIndicators.containsKey(IndicatorType.MACD_SIGNAL)) {
                dto.setMacdSignal(tableIndicators.get(IndicatorType.MACD_SIGNAL));
                dto.setMacdHistogram(dto.getMacd().subtract(dto.getMacdSignal()));
            }
        } else if (closes.size() >= 26) {
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

        // --- RSI: prefer DB, fallback to inline computation ---
        if (tableIndicators.containsKey(IndicatorType.RSI)) {
            dto.setRsi14(tableIndicators.get(IndicatorType.RSI));
        } else {
            dto.setRsi14(TechnicalAnalysisUtils.calculateRsi14(prices));
        }

        // --- Divergence detection (not stored in DB, always computed) ---
        // Sequential RSI computation: O(N) across all bars instead of O(N^2) per trough/peak
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
        dto.setBullishDivergence(TechnicalAnalysisUtils.hasBullishDivergence(prices, rsiHistory));
        dto.setBearishDivergence(TechnicalAnalysisUtils.hasBearishDivergence(prices, rsiHistory));

        // --- Multi-Timeframe Confluence (not stored in DB) ---
        List<DailyPrice> weeklyPrices = aggregationService.aggregateWeekly(prices);
        dto.setWeeklyRsi(TechnicalAnalysisUtils.calculateRsi14(weeklyPrices));
        List<DailyPrice> monthlyPrices = aggregationService.aggregateMonthly(prices);
        dto.setMonthlyRsi(TechnicalAnalysisUtils.calculateRsi14(monthlyPrices));

        // --- Volume Confirmation ---
        if (prices.size() >= 20) {
            List<DailyPrice> last20 = prices.subList(prices.size() - 20, prices.size());
            double avgVol = last20.stream().mapToLong(p -> p.getVolume() != null ? p.getVolume() : 0L).average().orElse(0);
            long currentVol = prices.get(prices.size() - 1).getVolume() != null ? prices.get(prices.size() - 1).getVolume() : 0L;
            dto.setVolumeConfirmed(currentVol >= avgVol * 1.2);
        }

        // --- Bollinger Bands: prefer DB, fallback to inline computation ---
        if (tableIndicators.containsKey(IndicatorType.BOLLINGER_UPPER)) {
            dto.setBollingerUpper(tableIndicators.get(IndicatorType.BOLLINGER_UPPER));
        }
        if (tableIndicators.containsKey(IndicatorType.BOLLINGER_LOWER)) {
            dto.setBollingerLower(tableIndicators.get(IndicatorType.BOLLINGER_LOWER));
        }
        // BB Middle is always SMA20
        dto.setBollingerMiddle(dto.getSma20());
        // Compute BB inline only if DB didn't provide upper/lower
        if (dto.getBollingerUpper() == null && dto.getSma20() != null) {
            BigDecimal std20 = calculateStdDev(closes, 20);
            dto.setBollingerUpper(dto.getSma20().add(std20.multiply(BigDecimal.valueOf(2))));
        }
        if (dto.getBollingerLower() == null && dto.getSma20() != null) {
            BigDecimal std20 = calculateStdDev(closes, 20);
            dto.setBollingerLower(dto.getSma20().subtract(std20.multiply(BigDecimal.valueOf(2))));
        }

        if (dto.getSma20() != null && dto.getSma20().compareTo(BigDecimal.ZERO) > 0) {
            dto.setPriceVsSma20(currentPrice.subtract(dto.getSma20())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(dto.getSma20(), 2, RoundingMode.HALF_UP));
        }

        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        List<DailyPrice> yearPrices = prices.stream()
                .filter(p -> !p.getPriceDate().isBefore(oneYearAgo))
                .collect(Collectors.toList());
        if (!yearPrices.isEmpty()) {
            BigDecimal high52 = yearPrices.stream().map(DailyPrice::getClosingPrice).max(BigDecimal::compareTo).orElse(currentPrice);
            BigDecimal low52 = yearPrices.stream().map(DailyPrice::getClosingPrice).min(BigDecimal::compareTo).orElse(currentPrice);
            dto.setHigh52Week(high52);
            dto.setLow52Week(low52);
            if (high52.compareTo(BigDecimal.ZERO) > 0) {
                dto.setPctFrom52WHigh(currentPrice.subtract(high52).multiply(BigDecimal.valueOf(100)).divide(high52, 2, RoundingMode.HALF_UP));
            }
            if (low52.compareTo(BigDecimal.ZERO) > 0) {
                dto.setPctFrom52WLow(currentPrice.subtract(low52).multiply(BigDecimal.valueOf(100)).divide(low52, 2, RoundingMode.HALF_UP));
            }
        }

        List<BigDecimal> sortedCloses = closes.stream().sorted().collect(Collectors.toList());
        int tenPct = Math.max(1, sortedCloses.size() / 10);

        // Prefer S1/R1 from the canonical S/R calculator (single source of truth across the page)
        SupportResistanceDto sr = allSR != null ? allSR : supportResistanceService.getLatestLevels(stock.getId());
        if (sr != null && sr.getPivots() != null && sr.getPivots().getS1() != null && sr.getPivots().getR1() != null) {
            dto.setSupportLevel(sr.getPivots().getS1());
            dto.setResistanceLevel(sr.getPivots().getR1());
        } else {
            // Fallback: percentile-based estimate if S/R has not been calculated yet
            dto.setSupportLevel(sortedCloses.subList(0, tenPct).stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(tenPct), 2, RoundingMode.HALF_UP));
            dto.setResistanceLevel(sortedCloses.subList(sortedCloses.size() - tenPct, sortedCloses.size()).stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(tenPct), 2, RoundingMode.HALF_UP));
        }

        dto.setTrendStrength(calculateTrendStrength(closes));
        dto.setVolatility(calculateVolatility(closes));

        // --- Historical mode: compute DB-only indicators inline ---
        // DB stores only latest values; for historical signals we need values from that date
        if (historicalMode) {
            try {
                if (dto.getStochK() == null && prices.size() >= 14) {
                    dto.setStochK(new StochKCalculator(14).calculate(prices));
                }
                if (dto.getStochD() == null && prices.size() >= 17) {
                    dto.setStochD(new StochDCalculator(14, 3).calculate(prices));
                }
                if (dto.getWilliamsR() == null && prices.size() >= 14) {
                    dto.setWilliamsR(new WilliamsRCalculator(14).calculate(prices));
                }
                if (dto.getCci() == null && prices.size() >= 20) {
                    dto.setCci(new CCICalculator(20).calculate(prices));
                }
                if (dto.getStochRsi() == null && prices.size() >= 14) {
                    dto.setStochRsi(new StochRsiCalculator(14, 14).calculate(prices));
                }
                if (dto.getAdx() == null && prices.size() >= 29) {
                    AdxCalculator.AdxResult adxResult = AdxCalculator.computeAdxResult(prices, 14);
                    dto.setAdx(adxResult.adx);
                    dto.setPlusDi(adxResult.plusDi);
                    dto.setMinusDi(adxResult.minusDi);
                }
                if (dto.getUltimateOsc() == null && prices.size() >= 28) {
                    dto.setUltimateOsc(new UltimateOscillatorCalculator(7, 14, 28).calculate(prices));
                }
                if (dto.getRoc() == null && prices.size() >= 13) {
                    dto.setRoc(new RocCalculator(12).calculate(prices));
                }
            } catch (Exception e) {
                log.debug("Historical inline indicator computation failed: {}", e.getMessage());
            }
        }

        // Breakout detection
        BreakoutDetector.BreakoutResult breakout = breakoutDetector.detect(prices, stock.getId());
        dto.setVolumeBreakout(breakout.volumeBreakout);
        dto.setGapUp(breakout.gapUp);
        dto.setGapDown(breakout.gapDown);
        dto.setRangeBreakout(breakout.rangeBreakout);
        dto.setBreakoutScore(breakout.score);

        int score = computeWeightedScore(dto, stock, prices);
        int fiidiiAdj = fiiDiiService.getScoreAdjustment();
        dto.setFiidiiScore(fiidiiAdj);
        score += fiidiiAdj;

        // ── Bearish trend graduated discount (replaces old binary score=4 cap) ──
        // Applies a 15% discount during SMA20<SMA50, but allows BUY signals
        // with strong reversal evidence. Reversal flags reduce or eliminate the discount.
        log.info("Pre-discount check: {} score={} sma20={} sma50={}", dto.getSymbol(), score, dto.getSma20(), dto.getSma50());
        if (dto.getSma20() != null && dto.getSma50() != null
                && dto.getSma20().compareTo(dto.getSma50()) < 0 && score > 0) {
            double discount = 0.85;
            // Reversal exception: MACD improving, RSI >= 50, price above SMA20
            boolean macdOk = dto.getMacdScore() > 0;
            boolean rsiOk = dto.getRsi14() != null && dto.getRsi14().doubleValue() >= 50;
            boolean priceOk = dto.getPriceVsSma20() != null && dto.getPriceVsSma20().doubleValue() > 0;
            boolean weeklyOk = dto.getWeeklyRsi() != null && dto.getWeeklyRsi().doubleValue() > 45;
            // High reversal conviction: all four conditions met
            if (macdOk && rsiOk && priceOk && weeklyOk) {
                discount = 1.0;  // No discount — full signal pass-through
            } else if (macdOk && rsiOk && priceOk) {
                discount = 0.9;  // Minimal 10% discount
            }
            // Reversal detection flags: if reversalScore >= 2, reduce discount
            if (dto.getReversalScore() >= 2) {
                discount = Math.max(discount, 0.9);
            }
            int originalScore = score;
            score = (int) Math.round(score * discount);
            if (score != originalScore) {
                log.info("Bearish trend discount applied for {}: score {} -> {} (SMA20={} < SMA50={}, discount={}%)",
                        dto.getSymbol(), originalScore, score, dto.getSma20(), dto.getSma50(), Math.round(discount * 100));
            }
        }

        dto.setScoreAfterDiscount(score);

        // ── Rolling accuracy penalty: suppress signals on stocks with poor history ──
        if (dto.getSignalAccuracy30d() != null && dto.getSignalAccuracyTotal30d() >= 5) {
            double acc = dto.getSignalAccuracy30d().doubleValue();
            if (acc < 35) {
                score -= 3;
                log.debug("Rolling accuracy penalty (-3) for {}: accuracy={}%", dto.getSymbol(), acc);
            } else if (acc < 45) {
                score -= 1;
                log.debug("Rolling accuracy penalty (-1) for {}: accuracy={}%", dto.getSymbol(), acc);
            }
        }

        // --- 3-Dimension High-Confidence BUY Gate ---
        // Prevents false BUYs when trend or momentum contradicts the score
        // Gated behind config flag for safe rollout (default: OFF)
        // All adjustments expressed as score modifications (not direct dto mutation)
        // so recommendation + compositeScore are always derived together at the end.
        if (highConfidenceGateEnabled && score >= BUY_THRESHOLD) {
            boolean trendOk = dto.getSma20() != null && dto.getSma50() != null
                    && dto.getSma20().compareTo(dto.getSma50()) > 0;
            boolean momentumOk = dto.getRsi14() != null && dto.getRsi14().doubleValue() < 75;
            boolean riskRewardOk = false;
            double maxPriceRatio = 1.0;
            // Price-action confirmation: strong bullish candlestick pattern relaxes the entry threshold
            if (dto.getCandlestickScore() >= 2) {
                maxPriceRatio = 1.05;
            }
            if (dto.getLatestPrice() != null && dto.getSma20() != null) {
                BigDecimal maxPrice = dto.getSma20().multiply(BigDecimal.valueOf(maxPriceRatio));
                riskRewardOk = dto.getLatestPrice().compareTo(maxPrice) <= 0;
            }
            if (!trendOk || !momentumOk || !riskRewardOk) {
                log.debug("BUY gate blocked {} — trend:{} momentum:{} riskReward:{}",
                        dto.getSymbol(), trendOk, momentumOk, riskRewardOk);
                // Clamp score to HOLD zone (score <= 4 < BUY_THRESHOLD) so
                // mapRecommendation() returns "HOLD"
                score = Math.min(score, BUY_THRESHOLD - 1);
                dto.setGateBlocked(true);
                List<String> reasons = new ArrayList<>();
                if (!trendOk) reasons.add("trend");
                if (!momentumOk) reasons.add("momentum");
                if (!riskRewardOk) reasons.add("risk:reward");
                dto.setGateBlockReasons(reasons);
            }
        }

        // Channel trading override: when price is at extreme 52-week positions
        // and other indicators agree, override the HOLD recommendation
        // Guard: requires trend confirmation to prevent value trap buys
        if (score > SELL_THRESHOLD && score < BUY_THRESHOLD
                && dto.getHigh52Week() != null && dto.getLow52Week() != null
                && dto.getLatestPrice() != null) {
            double price = dto.getLatestPrice().doubleValue();
            double high52 = dto.getHigh52Week().doubleValue();
            double low52 = dto.getLow52Week().doubleValue();
            double range = high52 - low52;
            if (range > 0) {
                double channelPct = ((price - low52) / range) * 100;

                // Trend confirmation: weekly SMA20 > SMA50 or weekly RSI > 40
                // Prevents buying into a fundamentally bearish stock just because it's cheap
                boolean weeklyTrendOk = (dto.getWeeklyRsi() != null && dto.getWeeklyRsi().doubleValue() > 40);

                // Near channel bottom (0-15%): override to BUY only if trend confirms
                boolean channelTrendBearish = dto.getSma20() != null && dto.getSma50() != null
                        && (dto.getSma20().compareTo(dto.getSma50()) < 0
                            || (dto.getLatestPrice() != null && dto.getLatestPrice().compareTo(dto.getSma50()) < 0));
                if (channelPct <= 15 && score >= -2 && weeklyTrendOk && !channelTrendBearish) {
                    // Force score into BUY zone
                    score = Math.max(score, BUY_THRESHOLD);
                }
                // Near channel top (85-100%): override to SELL if trend is not strongly bullish
                else if (channelPct >= 85 && score <= 2) {
                    // Force score into SELL zone
                    score = Math.min(score, SELL_THRESHOLD);
                }
            }
        }

        dto.setRecommendation(mapRecommendation(score));
        dto.setCompositeScore(score);
        log.info("Final signal for {}: score={} rec={}", dto.getSymbol(), score, dto.getRecommendation());

        // --- Actionable Dashboard: Extended fields (compute in bulk path too) ---
        BigDecimal atr = computeAtr(stock, prices);
        dto.setAtr(atr);

        // Open/High/Low from latest price
        if (!prices.isEmpty()) {
            DailyPrice latest = prices.get(prices.size() - 1);
            dto.setOpenPrice(latest.getOpeningPrice());
            dto.setHighPrice(latest.getHighPrice());
            dto.setLowPrice(latest.getLowPrice());
        }

        // Target, Stop
        computeTargetAndStop(dto, atr);

        // Signal age must be set before confidence score (confidence uses it)
        dto.setSignalAge(computeSignalAge(prices));

        // Compute indicator coverage: how many of 16 scoring factors had backing data
        computeIndicatorCoverage(dto);

        // Compute rolling accuracy from past records (must come before confidence)
        computeRollingAccuracy(dto);

        // Confidence, Position Size (confidence uses rolling accuracy data)
        dto.setConfidenceScore(computeConfidenceScore(dto));
        dto.setSuggestedPositionSize(computePositionSize(dto));

        return dto;
    }

    /**
     * Lightweight method for backtesting — computes only the recommendation
     * without the expensive extended trade metrics (ATR, target/stop, etc.).
     * Public for access by the scheduler in a different package.
     */
    public String computeRecommendationForBacktest(Stock stock, List<DailyPrice> prices) {
        SignalDTO dto = computeBaseSignalDto(stock, prices);
        return dto != null ? dto.getRecommendation() : null;
    }

    /**
     * Computes a shadow signal DTO — runs the full scoring pipeline
     * (indicators, weighted score, mapRecommendation, gate, channel override)
     * but does NOT persist a signal record, compute extended trade metrics
     * (ATR, target/stop), or trigger further shadow computation.
     * Used by ShadowSignalService for new-logic A/B comparison.
     */
    public SignalDTO computeShadowDto(Stock stock, List<DailyPrice> prices) {
        return computeBaseSignalDto(stock, prices);
    }

    /**
     * Inner class to hold pre-computed indicator values for a single date.
     * Used for incremental computation to avoid O(N²) complexity.
     */
    private static class HistoricalIndicatorSet {
        BigDecimal rsi14;
        BigDecimal ema12;
        BigDecimal ema26;
        BigDecimal macd;
        BigDecimal macdSignal;
        BigDecimal macdHistogram;
        BigDecimal bollingerUpper;
        BigDecimal bollingerLower;
        BigDecimal sma20;
        BigDecimal sma50;
        BigDecimal stochK;
        BigDecimal stochD;
        BigDecimal williamsR;
        BigDecimal cci;
        BigDecimal stochRsi;
        BigDecimal adx;
        BigDecimal plusDi;
        BigDecimal minusDi;
        BigDecimal ultimateOsc;
        BigDecimal roc;
        List<BigDecimal> rsiHistory; // For divergence detection
    }

    /**
     * Computes historical signal timeline for a stock by walking through price
     * bars at a regular step. Each sampled bar produces a SignalHistoryPoint
     * containing the recommendation, score, and closing price at that date.
     * Uses the lightweight backtest path (no ATR/target/stop) for performance.
     *
     * @param stockId  the stock to compute history for
     * @param days     how far back to go from today
     * @return chronologically sorted list of signal points (oldest first)
     */
    @Transactional(readOnly = true)
    public List<SignalHistoryPoint> getSignalHistory(Long stockId, int days) {
        long startTime = System.currentTimeMillis();
        try {
            Stock stock = stockService.getStockById(stockId);
            if (stock == null) return List.of();

            List<DailyPrice> prices = dailyPriceRepository
                    .findAllByStockIdOrderByPriceDateAsc(stock.getId());

            if (prices.size() < 20) return List.of();

            // Trim to the requested window
            LocalDate cutoff = LocalDate.now().minusDays(days);
            List<DailyPrice> windowed = prices.stream()
                    .filter(p -> !p.getPriceDate().isBefore(cutoff))
                    .collect(Collectors.toList());

            int firstWindowedIndex = 0;
            if (!windowed.isEmpty()) {
                LocalDate firstWindowedDate = windowed.get(0).getPriceDate();
                for (int i = 0; i < prices.size(); i++) {
                    if (!prices.get(i).getPriceDate().isBefore(firstWindowedDate)) {
                        firstWindowedIndex = i;
                        break;
                    }
                }
            }

            // Fetch corporate events once before the loop (avoid N+1 query)
            List<org.example.entity.CorporateEvent> preFetchedEvents =
                    corporateEventService.getEventsForSymbol(stock.getSymbol());

            // Adaptive step: fewer points for longer ranges
            int step;
            if (days <= 30) step = 1;
            else if (days <= 90) step = 3;
            else step = 5;

            // PHASE 1 OPTIMIZATION: Pre-compute all indicators in ONE O(N) pass
            List<HistoricalIndicatorSet> allIndicators = 
                computeAllIndicatorsIncrementally(prices);

            long precomputeTime = System.currentTimeMillis() - startTime;
            log.debug("[{}] Pre-computed {} indicator sets in {} ms", 
                stock.getSymbol(), allIndicators.size(), precomputeTime);

            List<SignalHistoryPoint> history = new ArrayList<>();
            for (int i = firstWindowedIndex; i < prices.size(); i++) {
                if (prices.get(i).getPriceDate().isBefore(cutoff)) {
                    continue;
                }
                if ((i - firstWindowedIndex) % step != 0) {
                    continue;
                }
                
                // Use pre-computed indicators instead of recalculating
                HistoricalIndicatorSet indicators = allIndicators.get(i);
                SignalDTO dto = computeBaseSignalDtoWithPrecomputedIndicators(
                    stock, prices.subList(0, i + 1), indicators, preFetchedEvents);
                
                if (dto != null) {
                    SignalHistoryPoint point = new SignalHistoryPoint(
                            prices.get(i).getPriceDate(),
                            dto.getRecommendation(),
                            dto.getCompositeScore(),
                            dto.getLatestPrice(),
                            null, null,
                            dto.getRsiScore(),
                            dto.getSmaScore(),
                            dto.getBollingerScore(),
                            dto.getMacdScore(),
                            dto.getTrendDirectionScore(),
                            dto.getCandlestickScore(),
                            dto.getCandlestickPattern(),
                            dto.getDivergenceScore(),
                            dto.getWeeklyConfluenceScore(),
                            dto.getFiidiiScore(),
                            dto.getRsi14(),
                            dto.getAdx(),
                            dto.getSma20(),
                            dto.getSma50(),
                            dto.getRawTrendScore(),
                            dto.getRawMomentumScore(),
                            dto.getRawStructureScore(),
                            BigDecimal.valueOf(dto.getAdxMultiplier()),
                            dto.getScoreAfterAdx(),
                            dto.getScoreAfterCandlestick(),
                            dto.getScoreAfterReversal(),
                            dto.getScoreAfterDiscount(),
                            null, null, null, null
                    );
                    history.add(point);
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[{}] Signal history: {} points in {} ms (step={}, precompute={} ms)",
                stock.getSymbol(), history.size(), totalTime, step, precomputeTime);

            // Merge accuracy data from persisted SignalRecord
            try {
                List<SignalRecord> records = signalRecordRepository.findByStockIdOrderByRecordedAtDesc(stockId);
                java.util.Map<LocalDate, SignalRecord> recordByDate = new java.util.HashMap<>();
                for (SignalRecord r : records) {
                    recordByDate.put(r.getRecordedAt(), r);
                }
                for (SignalHistoryPoint p : history) {
                    SignalRecord r = recordByDate.get(p.getPriceDate());
                    if (r != null) {
                        // Prefer 10-day, fallback to 5-day, then 20-day
                        if (r.getWasAccurate10d() != null) {
                            p.setWasAccurate(r.getWasAccurate10d());
                            p.setForwardReturn(r.getForwardReturn10d());
                        } else if (r.getWasAccurate5d() != null) {
                            p.setWasAccurate(r.getWasAccurate5d());
                            p.setForwardReturn(r.getForwardReturn5d());
                        } else if (r.getWasAccurate20d() != null) {
                            p.setWasAccurate(r.getWasAccurate20d());
                            p.setForwardReturn(r.getForwardReturn20d());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not merge accuracy data for stock {}: {}", stockId, e.getMessage());
            }

            return history;
        } catch (Exception e) {
            log.warn("Could not compute signal history for stock {}: {}", stockId, e.getMessage());
            return List.of();
        }
    }

    // ========== Actionable Dashboard Helper Methods ==========

    /**
     * Fetches ATR from TechnicalIndicator table or computes from prices.
     */
    private BigDecimal computeAtr(Stock stock, List<DailyPrice> prices) {
        try {
            Optional<TechnicalIndicator> indicator = technicalIndicatorRepository
                    .findFirstByStockIdAndIndicatorTypeOrderByCalculationDateDesc(stock.getId(), IndicatorType.ATR);
            if (indicator.isPresent() && indicator.get().getValue() != null) {
                return indicator.get().getValue();
            }
        } catch (Exception e) {
            log.debug("Could not fetch ATR from DB for stock {}: {}", stock.getSymbol(), e.getMessage());
        }

        // Fallback: compute from prices
        if (prices == null || prices.size() < 14) return null;
        try {
            ATRCalculator calculator = new ATRCalculator(14);
            return calculator.calculate(prices);
        } catch (Exception e) {
            log.debug("Could not compute ATR from prices for stock {}: {}", stock.getSymbol(), e.getMessage());
            return null;
        }
    }

    /**
     * Computes target price and stop loss based on recommendation and ATR.
     * Package-private for direct unit testing from the same package.
     */
    void computeTargetAndStop(SignalDTO dto, BigDecimal atr) {
        if (atr == null || atr.compareTo(BigDecimal.ZERO) == 0) {
            dto.setTargetPrice(null);
            dto.setStopLoss(null);
            return;
        }

        BigDecimal price = dto.getLatestPrice();
        if (price == null) {
            dto.setTargetPrice(null);
            dto.setStopLoss(null);
            return;
        }

        String rec = dto.getRecommendation();
        if (rec == null) {
            dto.setTargetPrice(null);
            dto.setStopLoss(null);
            return;
        }

        if ("BUY".equals(rec) || "STRONG BUY".equals(rec)) {
            BigDecimal rawStop = price.subtract(atr.multiply(BigDecimal.valueOf(2)));
            // Guard against negative stop-loss for low-price/high-volatility stocks
            if (rawStop.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Stop loss computed as negative for {} (price={}, atr={}), flooring to 0",
                        dto.getSymbol(), price, atr);
                rawStop = BigDecimal.ZERO;
            }
            dto.setStopLoss(rawStop.setScale(2, RoundingMode.HALF_UP));
            dto.setTargetPrice(price.add(atr.multiply(BigDecimal.valueOf(3)))
                    .setScale(2, RoundingMode.HALF_UP));
        } else if ("SELL".equals(rec) || "STRONG SELL".equals(rec)) {
            BigDecimal rawStop = price.add(atr.multiply(BigDecimal.valueOf(2)));
            dto.setStopLoss(rawStop.setScale(2, RoundingMode.HALF_UP));
            BigDecimal rawTarget = price.subtract(atr.multiply(BigDecimal.valueOf(3)));
            // Guard against negative target price
            if (rawTarget.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Target price computed as negative for {} (price={}, atr={}), flooring to 0",
                        dto.getSymbol(), price, atr);
                rawTarget = BigDecimal.ZERO;
            }
            dto.setTargetPrice(rawTarget.setScale(2, RoundingMode.HALF_UP));
        } else {
            dto.setTargetPrice(null);
            dto.setStopLoss(null);
        }
    }

    /**
     * Maps compositeScore to 0-100 confidence score with adjustments.
     * Factors in: volume confirmation (+10), event risk (-20), weekly/monthly
     * confluence (abs value), signal staleness (-5 per day past 1 day),
     * and indicator coverage (-3 per missing indicator).
     */
    Long computeConfidenceScore(SignalDTO dto) {
        int composite = dto.getCompositeScore();
        long base = 50 + (composite * 5L);
        base = Math.max(0, Math.min(100, base));

        // Adjustments
        if (dto.isVolumeConfirmed()) base += 10;
        if (dto.isEventRisk()) base -= 20;
        // Confluence only boosts confidence when it agrees with signal direction
        if (dto.getWeeklyConfluenceScore() != 0) {
            boolean weeklyAgrees = (composite > 0 && dto.getWeeklyConfluenceScore() > 0)
                                || (composite < 0 && dto.getWeeklyConfluenceScore() < 0);
            if (weeklyAgrees) {
                base += Math.abs(dto.getWeeklyConfluenceScore());
            }
        }
        if (dto.getMonthlyConfluenceScore() != 0) {
            boolean monthlyAgrees = (composite > 0 && dto.getMonthlyConfluenceScore() > 0)
                                 || (composite < 0 && dto.getMonthlyConfluenceScore() < 0);
            if (monthlyAgrees) {
                base += Math.abs(dto.getMonthlyConfluenceScore());
            }
        }

        // Signal staleness penalty: -5 per day past 1 day stale
        Long signalAge = dto.getSignalAge();
        if (signalAge != null && signalAge > 1) {
            base -= signalAge * 5;
        }

        // Missing indicator penalty: -3 per missing indicator (max possible penalty: 7 * 3 = 21)
        int coverage = dto.getIndicatorCoverage();
        if (coverage < 16) {
            base -= (16 - coverage) * 3L;
        }

        // Rolling accuracy bonus/penalty (only when enough data exists)
        BigDecimal accuracy = dto.getSignalAccuracy30d();
        int accuracyTotal = dto.getSignalAccuracyTotal30d();
        if (accuracy != null && accuracyTotal >= 5) {
            double acc = accuracy.doubleValue();
            if (acc >= 70) base += 10;
            else if (acc >= 60) base += 5;
            else if (acc < 40) base -= 20;
            else if (acc < 50) base -= 15;
        }

        return Math.max(0, Math.min(100, base));
    }

    /**
     * Computes suggested position size as % of portfolio based on volatility.
     */
    BigDecimal computePositionSize(SignalDTO dto) {
        BigDecimal vol = dto.getVolatility();
        if (vol == null || vol.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(5.0);
        }
        double size = 20.0 / vol.doubleValue();
        size = Math.max(1.0, Math.min(5.0, size));
        return BigDecimal.valueOf(size).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Computes signal age in days since the latest price date.
     */
    Long computeSignalAge(List<DailyPrice> prices) {
        if (prices == null || prices.isEmpty()) return null;
        DailyPrice latest = prices.get(prices.size() - 1);
        if (latest.getPriceDate() == null) return null;
        return ChronoUnit.DAYS.between(latest.getPriceDate(), LocalDate.now());
    }

    /**
     * Computes how many of the 19 scoring factors had backing data.
     * Stored on the DTO for display and used by computeConfidenceScore.
     * Package-private for direct unit testing.
     */
    void computeIndicatorCoverage(SignalDTO dto) {
        int count = 19;
        if (dto.getStochK() == null) count--;        // stochScore
        if (dto.getStochRsi() == null) count--;       // stochRsiScore
        if (dto.getUltimateOsc() == null) count--;    // ultimateOscScore
        if (dto.getRoc() == null) count--;            // rocScore
        if (dto.getWilliamsR() == null) count--;      // williamsRScore
        if (dto.getCci() == null) count--;            // cciScore
        if (dto.getMacdHistogram() == null) count--;  // macdScore
        if (dto.getVwap() == null) count--;           // vwapScore
        if (dto.getTenkanSen() == null || dto.getKijunSen() == null
                || dto.getSenkouSpanA() == null || dto.getSenkouSpanB() == null) count--;  // ichimokuScore
        dto.setIndicatorCoverage(Math.max(0, count));
    }

    /**
     * Persists a signal record for per-stock accuracy tracking.
     * Uses upsert (find by stockId + today, then update or create).
     * Package-private for direct unit testing.
     */
    void saveSignalRecord(SignalDTO dto) {
        if (dto.getStockId() == null) return;
        try {
            LocalDate today = LocalDate.now();
            Optional<SignalRecord> existing = signalRecordRepository
                    .findByStockIdAndRecordedAt(dto.getStockId(), today);
            SignalRecord record = existing.orElseGet(SignalRecord::new);
            record.setStockId(dto.getStockId());
            record.setRecordedAt(today);
            record.setRecommendation(dto.getRecommendation());
            record.setCompositeScore(dto.getCompositeScore());
            record.setConfidenceScore(dto.getConfidenceScore());
            record.setIndicatorCoverage(dto.getIndicatorCoverage());
            record.setPriceAtSignal(dto.getLatestPrice());
            signalRecordRepository.save(record);
        } catch (Exception e) {
            log.debug("Could not save signal record for stock {}: {}", dto.getStockId(), e.getMessage());
        }
    }

    /**
     * Backfills historical SignalRecord entries for all stocks.
     * For each stock, computes signals at every 3rd bar (starting at index 20)
     * and persists a SignalRecord if one doesn't already exist for that date.
     * Returns the total number of records created.
     */
    public int backfillSignalRecords() {
        List<Stock> stocks = stockService.getAllStocks();
        int totalCreated = 0;

        for (Stock stock : stocks) {
            try {
                List<DailyPrice> prices = dailyPriceRepository
                        .findAllByStockIdOrderByPriceDateAsc(stock.getId());

                if (prices.size() < 20) continue;

                List<SignalRecord> existing = signalRecordRepository
                        .findByStockIdOrderByRecordedAtDesc(stock.getId());
                Set<LocalDate> existingDates = new HashSet<>();
                for (SignalRecord r : existing) {
                    existingDates.add(r.getRecordedAt());
                }

                int created = 0;
                int step = 3;
                for (int i = 20; i < prices.size(); i += step) {
                    LocalDate signalDate = prices.get(i).getPriceDate();
                    if (existingDates.contains(signalDate)) continue;

                    List<DailyPrice> subList = prices.subList(0, i + 1);
                    SignalDTO dto = computeBaseSignalDto(stock, subList, true);
                    if (dto != null) {
                        SignalRecord record = new SignalRecord();
                        record.setStockId(stock.getId());
                        record.setRecordedAt(signalDate);
                        record.setRecommendation(dto.getRecommendation());
                        record.setCompositeScore(dto.getCompositeScore());
                        record.setPriceAtSignal(dto.getLatestPrice());
                        signalRecordRepository.save(record);
                        created++;
                    }
                }
                totalCreated += created;
                if (created > 0) {
                    log.info("Backfilled {} signal records for {}", created, stock.getSymbol());
                }
            } catch (Exception e) {
                log.debug("Could not backfill signal records for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }

        return totalCreated;
    }

    /**
     * Computes rolling signal accuracy from the last 30 non-HOLD evaluated records.
     * Uses 10-day accuracy as primary, falling back to 5-day then 20-day.
     * Sets signalAccuracy30d, signalAccuracyTotal30d, signalAccuracyCorrect30d on the DTO.
     * Package-private for direct unit testing.
     */
    void computeRollingAccuracy(SignalDTO dto) {
        if (dto.getStockId() == null) return;
        try {
            LocalDate since = LocalDate.now().minusDays(90);
            List<SignalRecord> records = signalRecordRepository
                    .findRecentByStockId(dto.getStockId(), since);

            List<SignalRecord> evaluated = records.stream()
                    .filter(r -> !"HOLD".equals(r.getRecommendation()))
                    .filter(r -> r.getWasAccurate10d() != null
                            || r.getWasAccurate5d() != null
                            || r.getWasAccurate20d() != null)
                    .limit(30)
                    .collect(Collectors.toList());

            int total = evaluated.size();
            if (total < 10) return; // Not enough data for meaningful accuracy

            long correct = evaluated.stream().filter(r -> {
                if (r.getWasAccurate10d() != null) return r.getWasAccurate10d();
                if (r.getWasAccurate5d() != null) return r.getWasAccurate5d();
                return r.getWasAccurate20d() != null && r.getWasAccurate20d();
            }).count();

            BigDecimal pct = BigDecimal.valueOf(correct * 100.0 / total)
                    .setScale(1, RoundingMode.HALF_UP);
            dto.setSignalAccuracy30d(pct);
            dto.setSignalAccuracyTotal30d(total);
            dto.setSignalAccuracyCorrect30d((int) correct);
        } catch (Exception e) {
            log.debug("Could not compute rolling accuracy for stock {}: {}", dto.getStockId(), e.getMessage());
        }
    }

    /**
     * Fetches historical returns for the recommendation from precomputed data.
     */
    private void fetchHistoricalReturns(SignalDTO dto) {
        String rec = dto.getRecommendation();
        if (rec == null) return;

        try {
            List<SignalHistoricalPerformance> performances = signalHistoricalPerformanceRepository
                    .findByRecommendationAndDaysForwardIn(rec, List.of(5, 10, 20));
            for (SignalHistoricalPerformance perf : performances) {
                if (perf.getDaysForward() == 5) dto.setHistoricalReturn5d(perf.getAvgReturn());
                else if (perf.getDaysForward() == 10) dto.setHistoricalReturn10d(perf.getAvgReturn());
                else if (perf.getDaysForward() == 20) dto.setHistoricalReturn20d(perf.getAvgReturn());
            }
        } catch (Exception e) {
            log.debug("Could not fetch historical returns for {}: {}", rec, e.getMessage());
        }
    }

    /**
     * Fetches additional technical indicators from DB.
     * Uses a single batch query to avoid N+1.
     */
    private Map<IndicatorType, BigDecimal> fetchAdditionalIndicators(SignalDTO dto, Long stockId) {
        try {
            List<TechnicalIndicator> indicators = technicalIndicatorRepository
                    .findByStockIdAndIndicatorTypeInOrderByCalculationDateDesc(stockId,
                            List.of(IndicatorType.STOCH_K, IndicatorType.STOCH_D,
                                    IndicatorType.WILLIAMS_R, IndicatorType.CCI,
                                    IndicatorType.STOCH_RSI, IndicatorType.ADX,
                                    IndicatorType.PLUS_DI, IndicatorType.MINUS_DI,
                                    IndicatorType.ULTIMATE_OSC, IndicatorType.ROC_12,
                                    IndicatorType.OBV,
                                    IndicatorType.VWAP,
                                    IndicatorType.TENKAN_SEN, IndicatorType.KIJUN_SEN,
                                    IndicatorType.SENKOU_SPAN_A, IndicatorType.SENKOU_SPAN_B,
                                    IndicatorType.CHIKOU_SPAN,
                                    IndicatorType.RSI, IndicatorType.SMA_20, IndicatorType.SMA_50,
                                    IndicatorType.MACD_LINE, IndicatorType.MACD_SIGNAL,
                                    IndicatorType.BOLLINGER_UPPER, IndicatorType.BOLLINGER_LOWER));

            // Process results: group by type, pick the latest (first) per type
            Map<IndicatorType, BigDecimal> latestValues = new HashMap<>();
            for (TechnicalIndicator ind : indicators) {
                if (!latestValues.containsKey(ind.getIndicatorType()) && ind.getValue() != null) {
                    latestValues.put(ind.getIndicatorType(), ind.getValue());
                }
            }

            if (latestValues.containsKey(IndicatorType.STOCH_K)) dto.setStochK(latestValues.get(IndicatorType.STOCH_K));
            if (latestValues.containsKey(IndicatorType.STOCH_D)) dto.setStochD(latestValues.get(IndicatorType.STOCH_D));
            if (latestValues.containsKey(IndicatorType.WILLIAMS_R)) dto.setWilliamsR(latestValues.get(IndicatorType.WILLIAMS_R));
            if (latestValues.containsKey(IndicatorType.CCI)) dto.setCci(latestValues.get(IndicatorType.CCI));

            if (latestValues.containsKey(IndicatorType.STOCH_RSI)) dto.setStochRsi(latestValues.get(IndicatorType.STOCH_RSI));
            if (latestValues.containsKey(IndicatorType.ADX)) dto.setAdx(latestValues.get(IndicatorType.ADX));
            if (latestValues.containsKey(IndicatorType.PLUS_DI)) dto.setPlusDi(latestValues.get(IndicatorType.PLUS_DI));
            if (latestValues.containsKey(IndicatorType.MINUS_DI)) dto.setMinusDi(latestValues.get(IndicatorType.MINUS_DI));
            if (latestValues.containsKey(IndicatorType.ULTIMATE_OSC)) dto.setUltimateOsc(latestValues.get(IndicatorType.ULTIMATE_OSC));
            if (latestValues.containsKey(IndicatorType.ROC_12)) dto.setRoc(latestValues.get(IndicatorType.ROC_12));
            if (latestValues.containsKey(IndicatorType.OBV)) dto.setObv(latestValues.get(IndicatorType.OBV));

            if (latestValues.containsKey(IndicatorType.VWAP)) dto.setVwap(latestValues.get(IndicatorType.VWAP));
            if (latestValues.containsKey(IndicatorType.TENKAN_SEN)) dto.setTenkanSen(latestValues.get(IndicatorType.TENKAN_SEN));
            if (latestValues.containsKey(IndicatorType.KIJUN_SEN)) dto.setKijunSen(latestValues.get(IndicatorType.KIJUN_SEN));
            if (latestValues.containsKey(IndicatorType.SENKOU_SPAN_A)) dto.setSenkouSpanA(latestValues.get(IndicatorType.SENKOU_SPAN_A));
            if (latestValues.containsKey(IndicatorType.SENKOU_SPAN_B)) dto.setSenkouSpanB(latestValues.get(IndicatorType.SENKOU_SPAN_B));
            if (latestValues.containsKey(IndicatorType.CHIKOU_SPAN)) dto.setChikouSpan(latestValues.get(IndicatorType.CHIKOU_SPAN));

            return latestValues;
        } catch (Exception e) {
            log.debug("Could not fetch additional indicators for stock {}: {}", stockId, e.getMessage());
            return null;
        }
    }

    /**
     * Computes weighted score for a signal.
     * Grouped into Trend (40%), Momentum (40%), Volume/Structure (20%) categories
     * with continuous ADX-based multipliers instead of hard caps.
     */
    int computeWeightedScore(SignalDTO dto, Stock stock, List<DailyPrice> prices) {
        // ── Category 1: Trend signals (max raw ±14) ──
        int trendScore = 0;

        // Pre-compute trend direction for dampening
        boolean trendBearish = dto.getSma20() != null && dto.getSma50() != null
                && dto.getSma20().compareTo(dto.getSma50()) < 0;

        // Divergence
        int divergenceScore = 0;
        if (dto.isBullishDivergence()) divergenceScore += 3;
        if (dto.isBearishDivergence()) divergenceScore -= 2;
        dto.setDivergenceScore(divergenceScore);
        trendScore += divergenceScore;

        // Multi-Timeframe Confluence
        int weeklyConfluence = 0;
        if (dto.getRsi14() != null && dto.getWeeklyRsi() != null) {
            double daily = dto.getRsi14().doubleValue();
            double weekly = dto.getWeeklyRsi().doubleValue();
            if (daily < 30 && weekly < 40) weeklyConfluence += 3;
            else if (daily > 75 && weekly > 65) weeklyConfluence -= 2;
            else if (daily > 70 && weekly > 60) weeklyConfluence -= 1;
            else if (daily > 50 && weekly > 50) weeklyConfluence += 1;
        }
        dto.setWeeklyConfluenceScore(weeklyConfluence);
        trendScore += weeklyConfluence;

        int monthlyConfluence = 0;
        if (dto.getRsi14() != null && dto.getMonthlyRsi() != null) {
            double daily = dto.getRsi14().doubleValue();
            double monthly = dto.getMonthlyRsi().doubleValue();
            if (daily < 30 && monthly < 50) monthlyConfluence += 1;
            else if (daily > 75 && monthly > 65) monthlyConfluence -= 1;
        }
        dto.setMonthlyConfluenceScore(monthlyConfluence);
        trendScore += monthlyConfluence;

        // MACD Crossover
        int macdScore = 0;
        if (dto.getMacd() != null && dto.getMacdSignal() != null) {
            double macdVal = dto.getMacd().doubleValue();
            double sigVal = dto.getMacdSignal().doubleValue();
            boolean macdAboveSignal = macdVal > sigVal;
            boolean macdAboveZero = macdVal > 1;
            boolean bothPositive = macdVal > 0 && sigVal > 0;
            boolean bothNegative = macdVal < 0 && sigVal < 0;

            if (macdAboveSignal && macdAboveZero) macdScore += 3;
            else if (macdAboveSignal && bothPositive) macdScore += 1;
            else if (macdAboveSignal && bothNegative) macdScore += 0;
            else if (!macdAboveSignal && bothNegative) macdScore -= 2;
            else if (!macdAboveSignal && bothPositive) macdScore -= 1;
        }
        dto.setMacdScore(macdScore);
        trendScore += macdScore;

        // Price vs SMAs
        int smaScore = 0;
        if (dto.getPriceVsSma20() != null) {
            double pct = dto.getPriceVsSma20().doubleValue();
            if (pct > 3) smaScore += 1;
            if (pct < -3) smaScore -= 1;
        }
        if (dto.getSma20() != null && dto.getSma50() != null && dto.getLatestPrice() != null) {
            if (dto.getSma20().compareTo(dto.getSma50()) < 0 && dto.getLatestPrice().compareTo(dto.getSma20()) < 0) {
                smaScore -= 2;
            } else if (dto.getSma20().compareTo(dto.getSma50()) > 0 && dto.getLatestPrice().compareTo(dto.getSma20()) > 0) {
                smaScore += 2;
            }
        }
        dto.setSmaScore(smaScore);
        trendScore += smaScore;

        // Trend-following bonus
        if (dto.getMacdScore() == 3
                && dto.getSma20() != null && dto.getSma50() != null && dto.getLatestPrice() != null
                && dto.getSma20().compareTo(dto.getSma50()) > 0
                && dto.getLatestPrice().compareTo(dto.getSma20()) > 0) {
            trendScore += 1;
        }

        // 52-Week Proximity (near 52W low with positive momentum = opportunity)
        // Zeroed when trend is bearish to prevent value-trap buys
        int week52Score = 0;
        if (dto.getPctFrom52WLow() != null && dto.getPctFrom52WLow().doubleValue() < 5) {
            boolean hasPositiveMomentum = dto.getHistoricalReturn5d() != null && dto.getHistoricalReturn5d().doubleValue() > 0;
            if (hasPositiveMomentum && !trendBearish) week52Score += 2;
        }
        dto.setWeek52Score(week52Score);
        trendScore += week52Score;

        // ── Trend Direction Penalties & Bonuses (symmetric for bullish/bearish confirmation) ──
        int trendDirectionScore = 0;
        if (prices.size() >= 20) {
            BigDecimal currentPrice = dto.getLatestPrice();
            List<DailyPrice> last20Prices = prices.subList(prices.size() - 20, prices.size());

            // 1. Distance from 20-day extremes (symmetric)
            BigDecimal high20d = last20Prices.stream()
                    .map(DailyPrice::getClosingPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(currentPrice);
            BigDecimal low20d = last20Prices.stream()
                    .map(DailyPrice::getClosingPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(currentPrice);
            if (currentPrice != null && high20d.compareTo(BigDecimal.ZERO) > 0) {
                double drawdownPct = currentPrice.subtract(high20d)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(high20d, 2, RoundingMode.HALF_UP)
                        .doubleValue();
                if (drawdownPct < -10) trendDirectionScore -= 3;
                else if (drawdownPct < -7) trendDirectionScore -= 2;
                else if (drawdownPct < -5) trendDirectionScore -= 1;
            }
            if (currentPrice != null && low20d.compareTo(BigDecimal.ZERO) > 0) {
                double rallyPct = currentPrice.subtract(low20d)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(low20d, 2, RoundingMode.HALF_UP)
                        .doubleValue();
                if (rallyPct > 10) trendDirectionScore += 3;
                else if (rallyPct > 7) trendDirectionScore += 2;
                else if (rallyPct > 5) trendDirectionScore += 1;
            }

            // 2. 20-day momentum (symmetric: recent decline penalty, recent rise bonus)
            BigDecimal price20dAgo = last20Prices.get(0).getClosingPrice();
            if (currentPrice != null && price20dAgo.compareTo(BigDecimal.ZERO) > 0) {
                double change20d = currentPrice.subtract(price20dAgo)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(price20dAgo, 2, RoundingMode.HALF_UP)
                        .doubleValue();
                if (change20d < -10) trendDirectionScore -= 2;
                else if (change20d < -5) trendDirectionScore -= 1;
                else if (change20d > 10) trendDirectionScore += 2;
                else if (change20d > 5) trendDirectionScore += 1;

                // 3. Rapid rally (context-aware): penalize blow-off tops, not recoveries from drawdown
                if (change20d > 7) {
                    // Check for recent 15%+ drawdown (recovery context = no penalty)
                    BigDecimal low30d = prices.subList(Math.max(0, prices.size() - 30), prices.size()).stream()
                            .map(DailyPrice::getClosingPrice)
                            .min(BigDecimal::compareTo)
                            .orElse(currentPrice);
                    BigDecimal high30d = prices.subList(Math.max(0, prices.size() - 30), prices.size()).stream()
                            .map(DailyPrice::getClosingPrice)
                            .max(BigDecimal::compareTo)
                            .orElse(currentPrice);
                    boolean recoveryFromDrawdown = low30d.compareTo(BigDecimal.ZERO) > 0
                            && high30d.subtract(low30d)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(low30d, 2, RoundingMode.HALF_UP)
                                .doubleValue() > 15;
                    if (!recoveryFromDrawdown) {
                        if (change20d > 15) trendDirectionScore -= 3;
                        else if (change20d > 10) trendDirectionScore -= 2;
                        else if (change20d > 7) trendDirectionScore -= 1;
                    }
                }
            }

            // 4. Higher highs / lower highs (symmetric)
            if (prices.size() >= 15) {
                List<BigDecimal> weeklyCloses = new ArrayList<>();
                for (int w = prices.size() - 1; w >= prices.size() - 15 && w >= 0; w -= 5) {
                    weeklyCloses.add(prices.get(w).getClosingPrice());
                }
                if (weeklyCloses.size() >= 3) {
                    boolean lowerHighs = weeklyCloses.get(0).compareTo(weeklyCloses.get(1)) < 0
                            && weeklyCloses.get(1).compareTo(weeklyCloses.get(2)) < 0;
                    boolean higherHighs = weeklyCloses.get(0).compareTo(weeklyCloses.get(1)) > 0
                            && weeklyCloses.get(1).compareTo(weeklyCloses.get(2)) > 0;
                    if (lowerHighs) trendDirectionScore -= 1;
                    else if (higherHighs && !trendBearish) trendDirectionScore += 1;
                }
            }
        }
        dto.setTrendDirectionScore(trendDirectionScore);
        trendScore += trendDirectionScore;

        // ── Bearish trend dampener: cap bullish trend contributions when SMA20 < SMA50 ──
        // Prevents false BUY from Ichimoku/MACD/etc during downtrends
        if (trendBearish && trendScore > 0) {
            trendScore = (int) Math.round(trendScore * 0.5) - 2;
        }

        // Ichimoku Cloud (capped at ±4 to prevent single-factor dominance)
        int ichimokuScore = 0;
        if (dto.getTenkanSen() != null && dto.getKijunSen() != null
                && dto.getSenkouSpanA() != null && dto.getSenkouSpanB() != null
                && dto.getLatestPrice() != null) {
            BigDecimal tenkan = dto.getTenkanSen();
            BigDecimal kijun = dto.getKijunSen();
            BigDecimal cloudTop = dto.getSenkouSpanA().max(dto.getSenkouSpanB());
            BigDecimal cloudBottom = dto.getSenkouSpanA().min(dto.getSenkouSpanB());

            if (tenkan.compareTo(kijun) > 0) ichimokuScore += 1;
            else ichimokuScore -= 1;

            if (dto.getLatestPrice().compareTo(cloudTop) > 0) ichimokuScore += 2;
            else if (dto.getLatestPrice().compareTo(cloudBottom) < 0) {
                // Graduated: how deep below the cloud?
                BigDecimal distanceBelow = cloudBottom.subtract(dto.getLatestPrice())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(cloudBottom, 2, RoundingMode.HALF_UP);
                if (distanceBelow.compareTo(BigDecimal.valueOf(3)) < 0) ichimokuScore -= 0;  // Near cloud
                else if (distanceBelow.compareTo(BigDecimal.valueOf(8)) < 0) ichimokuScore -= 1;
                else ichimokuScore -= 2;
            }

            if (dto.getSenkouSpanA().compareTo(dto.getSenkouSpanB()) > 0) ichimokuScore += 1;
            else ichimokuScore -= 1;

            ichimokuScore = Math.max(-4, Math.min(4, ichimokuScore));
            dto.setIchimokuScore(ichimokuScore);
        }
        trendScore += ichimokuScore;

        // ── Category 2: Momentum signals (max raw ±12) ──
        int momentumScore = 0;

        // RSI — oversold BUY suppressed in downtrend (oversold → more oversold)
        int rsiScore = 0;
        if (dto.getRsi14() != null) {
            double rsi = dto.getRsi14().doubleValue();
            if (rsi < 30) rsiScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 2;   // oversold: no BUY in downtrend
            else if (rsi < 40) rsiScore -= 2;
            else if (rsi < 50) rsiScore -= 1;
            else if (rsi >= 55 && rsi <= 65) rsiScore += 2;
            else if (rsi > 80) rsiScore -= 3;  // Extremely overbought
            else if (rsi > 75) rsiScore -= 2;  // Very overbought
            else if (rsi > 70) rsiScore -= 1;  // Overbought
        }
        dto.setRsiScore(rsiScore);
        momentumScore += rsiScore;

        // RSI reversal bonus: if RSI has risen for 3+ consecutive periods from a low base
        if (prices.size() >= 20 && dto.getRsi14() != null && dto.getRsi14().doubleValue() >= 45
                && dto.getRsi14().doubleValue() <= 75) {
            int risingPeriods = 0;
            for (int i = 0; i < 3; i++) {
                int endIdx = prices.size() - i;
                int startIdx = Math.max(0, endIdx - 14);
                if (startIdx >= 1 && endIdx > startIdx) {
                    BigDecimal prevRsi = TechnicalAnalysisUtils.calculateRsi14(
                            prices.subList(0, endIdx - 1));
                    BigDecimal currRsi = TechnicalAnalysisUtils.calculateRsi14(
                            prices.subList(0, endIdx));
                    if (prevRsi != null && currRsi != null && currRsi.compareTo(prevRsi) > 0) {
                        risingPeriods++;
                    }
                }
            }
            if (risingPeriods >= 3) {
                rsiScore += 2;
                momentumScore += 2;
                dto.setRsiScore(rsiScore);
                log.debug("RSI reversal bonus (+2) for {}: RSI rising {} consecutive periods",
                        dto.getSymbol(), risingPeriods);
            }
        }

        // Bollinger Bands — below lower band BUY suppressed in downtrend
        int bollingerScore = 0;
        if (dto.getBollingerLower() != null && dto.getBollingerUpper() != null && dto.getLatestPrice() != null) {
            double price = dto.getLatestPrice().doubleValue();
            if (price < dto.getBollingerLower().doubleValue()) bollingerScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 2;
            else if (price >= dto.getBollingerUpper().doubleValue()) bollingerScore -= 2;
        }
        dto.setBollingerScore(bollingerScore);
        momentumScore += bollingerScore;

        // Stochastic %K/%D — oversold BUY suppressed in downtrend
        int stochScore = 0;
        if (dto.getStochK() != null && dto.getStochD() != null) {
            double k = dto.getStochK().doubleValue();
            double d = dto.getStochD().doubleValue();
            if (k > d && k < 20 && d < 20) stochScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 2;
            else if (k < d && k > 80 && d > 80) stochScore -= 2;
            else if (k > d && k < 30) stochScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 1;
            else if (k > 80) stochScore -= 1;
        }
        dto.setStochScore(stochScore);
        momentumScore += stochScore;

        // StochRSI — oversold BUY suppressed in downtrend
        int stochRsiScore = 0;
        if (dto.getStochRsi() != null) {
            double srsi = dto.getStochRsi().doubleValue();
            if (srsi < 20) stochRsiScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 2;
            else if (srsi > 90) stochRsiScore -= 2;
            else if (srsi > 80) stochRsiScore -= 1;
            else if (srsi < 30) stochRsiScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 1;
        }
        dto.setStochRsiScore(stochRsiScore);
        momentumScore += stochRsiScore;

        // Ultimate Oscillator — oversold BUY suppressed in downtrend
        int ultimateOscScore = 0;
        if (dto.getUltimateOsc() != null) {
            double uo = dto.getUltimateOsc().doubleValue();
            if (uo < 30) ultimateOscScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 1;
            else if (uo > 70) ultimateOscScore -= 1;
        }
        dto.setUltimateOscScore(ultimateOscScore);
        momentumScore += ultimateOscScore;

        // ROC (symmetric now)
        int rocScore = 0;
        if (dto.getRoc() != null) {
            double roc = dto.getRoc().doubleValue();
            if (roc > 5) rocScore += 1;
            else if (roc < -5) rocScore -= 1;
        }
        dto.setRocScore(rocScore);
        momentumScore += rocScore;

        // Williams %R — oversold BUY suppressed in downtrend
        int williamsRScore = 0;
        if (dto.getWilliamsR() != null) {
            double wr = dto.getWilliamsR().doubleValue();
            if (wr < -80) williamsRScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 1;
            else if (wr > -20) williamsRScore -= 1;
        }
        dto.setWilliamsRScore(williamsRScore);
        momentumScore += williamsRScore;

        // CCI — oversold BUY suppressed in downtrend
        int cciScore = 0;
        if (dto.getCci() != null) {
            double cci = dto.getCci().doubleValue();
            if (cci < -100) cciScore += (trendBearish && dto.getReversalScore() < 2) ? 0 : 1;
            else if (cci > 200) cciScore -= 2;
            else if (cci > 100) cciScore -= 1;
        }
        dto.setCciScore(cciScore);
        momentumScore += cciScore;

        // VWAP — only contributes at >3% deviation to avoid constant noise
        int vwapScore = 0;
        if (dto.getVwap() != null && dto.getLatestPrice() != null) {
            BigDecimal vwapRatio = dto.getLatestPrice().subtract(dto.getVwap()).divide(dto.getVwap(), 4, RoundingMode.HALF_UP);
            if (vwapRatio.compareTo(new BigDecimal("-0.03")) < 0) vwapScore = 2;
            else if (vwapRatio.compareTo(new BigDecimal("0.03")) > 0) vwapScore = -2;
            dto.setVwapScore(vwapScore);
        }
        momentumScore += vwapScore;

        // ── Category 3: Volume/Structure signals (max raw ±6) ──
        int structureScore = 0;

        // OBV trend
        int obvScore = 0;
        if (prices.size() >= 3) {
            long netVol3d = 0;
            for (int i = prices.size() - 3; i < prices.size(); i++) {
                if (i > 0) {
                    BigDecimal currClose = prices.get(i).getClosingPrice();
                    BigDecimal prevClose = prices.get(i - 1).getClosingPrice();
                    long vol = prices.get(i).getVolume() != null ? prices.get(i).getVolume() : 0L;
                    if (currClose.compareTo(prevClose) > 0) netVol3d += vol;
                    else if (currClose.compareTo(prevClose) < 0) netVol3d -= vol;
                }
            }
            if (netVol3d > 0) obvScore += 1;
            else if (netVol3d < 0) obvScore -= 1;
        }
        dto.setObvScore(obvScore);
        structureScore += obvScore;

        // Support/Resistance Proximity (Channel Trading using 52-week range)
        // Zeroed when trend is bearish to prevent buying into downtrends
        int srScore = 0;
        if (dto.getHigh52Week() != null && dto.getLow52Week() != null && dto.getLatestPrice() != null) {
            double price = dto.getLatestPrice().doubleValue();
            double high52 = dto.getHigh52Week().doubleValue();
            double low52 = dto.getLow52Week().doubleValue();
            double range = high52 - low52;

            if (range > 0) {
                // Calculate position in 52-week channel (0%=bottom, 100%=top)
                double channelPosition = ((price - low52) / range) * 100;

                if (trendBearish) {
                    // In downtrend: only penalize (sell signals), don't reward (buy signals)
                    if (channelPosition >= 80) srScore -= 3;
                    else if (channelPosition >= 60) srScore -= 1;
                } else {
                    // In uptrend or neutral: full channel scoring
                    if (channelPosition <= 20) srScore += 3;
                    else if (channelPosition <= 40) srScore += 1;
                    else if (channelPosition <= 60) srScore += 0;
                    else if (channelPosition <= 80) srScore -= 1;
                    else srScore -= 3;
                }
            }
        }
        dto.setSrProximityScore(srScore);
        structureScore += srScore;

        // Breakout detection
        structureScore += dto.getBreakoutScore();

        // Volume confirmation
        if (dto.isVolumeConfirmed()) {
            structureScore += 1;
            dto.setVolumePenaltyApplied(false);
        } else {
            dto.setVolumePenaltyApplied(false);
        }

        // ── Factor group caps: prevent any single category from dominating ──
        // Historical data showed oversold cluster could inflate score by +13 alone
        momentumScore = Math.max(-5, Math.min(5, momentumScore));
        structureScore = Math.max(-3, Math.min(3, structureScore));

        // ── Combine categories ──
        double rawTotal = trendScore + momentumScore + structureScore;

        // ── Continuous ADX-based multiplier (replaces hard caps) ──
        // ADX 0-15: no trend → multiply by 0.3 (strong noise reduction)
        // ADX 15-25: weak trend → multiply by 0.5 + 0.02*(adx-15)
        // ADX 25-35: moderate trend → multiply by 0.7 + 0.01*(adx-25)
        // ADX 35+: strong trend → multiply by 1.0 (full confidence)
        double adxMultiplier = 1.0;
        if (dto.getAdx() != null) {
            double adx = dto.getAdx().doubleValue();
            if (adx < 15) {
                adxMultiplier = 0.3;
            } else if (adx < 25) {
                adxMultiplier = 0.5 + 0.02 * (adx - 15);
            } else if (adx < 35) {
                adxMultiplier = 0.7 + 0.01 * (adx - 25);
            } else {
                adxMultiplier = 1.0;
            }

            // Counter-trend dampening: if signal opposes ADX direction, reduce further
            if (dto.getPlusDi() != null && dto.getMinusDi() != null) {
                boolean withTrend = false;
                if (rawTotal > 0 && dto.getPlusDi().compareTo(dto.getMinusDi()) > 0) withTrend = true;
                if (rawTotal < 0 && dto.getMinusDi().compareTo(dto.getPlusDi()) > 0) withTrend = true;
                if (!withTrend && adx > 25) {
                    adxMultiplier *= 0.7;
                    dto.setAdxFilterApplied(1);
                }
            }
        }

        // ── Candlestick Pattern Detection (price action confirmation) ──
        CandlestickPatternCalculator candleCalc = new CandlestickPatternCalculator();
        CandlestickPatternCalculator.Result candleResult = candleCalc.detect(prices);
        dto.setCandlestickScore(candleResult.score());
        dto.setCandlestickPattern(candleResult.label());

        int score = (int) Math.round(rawTotal * adxMultiplier);
        score += candleResult.score();

        // Strategy influence from active strategy conditions
        if (strategyInfluenceService != null) {
            try {
                var influence = strategyInfluenceService.computeInfluence(stock.getId());
                if (influence != null && influence.multiplier() != 0) {
                    score = (int) Math.round(score + (score * influence.multiplier()));
                    dto.setStrategyInfluenceMultiplier(influence.multiplier());
                    dto.setStrategyInfluenceReason(influence.reason());
                }
            } catch (Exception e) {
                log.warn("Strategy influence failed for stock {}: {}", stock.getId(), e.getMessage());
            }
        }

        // ── Reversal Detection (multi-flag confirmation) ──
        ReversalDetector reversalDetector = new ReversalDetector();
        List<BigDecimal> reversalRsiValues = new ArrayList<>();
        if (prices.size() >= 20) {
            for (int i = 0; i < prices.size(); i++) {
                List<DailyPrice> subPrices = prices.subList(0, Math.min(i + 1, prices.size()));
                if (subPrices.size() >= 14) {
                    BigDecimal r = TechnicalAnalysisUtils.calculateRsi14(subPrices);
                    reversalRsiValues.add(r != null ? r : BigDecimal.ZERO);
                } else {
                    reversalRsiValues.add(BigDecimal.ZERO);
                }
            }
        }
        ReversalDetector.Result reversalResult = reversalDetector.detect(prices, reversalRsiValues, dto.getSma20());
        dto.setReversalScore(reversalResult.score());
        dto.setReversalFlags(reversalResult.flagBitmask());
        score += reversalResult.score();
        log.info("Reversal detection for {}: score={} flags={}", dto.getSymbol(),
                reversalResult.score(), reversalResult.flagSummary());

        // ── Compound overbought dampener ──
        // Bypassed when:
        // - Reversal detection confirms a genuine reversal (reversalScore >= 2)
        // - Stock is in confirmed uptrend (SMA20 > SMA50) with strong ADX (continuation setups are valid)
        boolean trendBullish = dto.getSma20() != null && dto.getSma50() != null
                && dto.getSma20().compareTo(dto.getSma50()) > 0;
        boolean strongAdx = dto.getAdx() != null && dto.getAdx().doubleValue() >= 25;
        if (dto.getStochRsi() != null && dto.getCci() != null && score > 0
                && dto.getReversalScore() < 2) {
            int overboughtCount = 0;
            if (dto.getStochRsi().doubleValue() > 85) overboughtCount++;
            if (dto.getCci().doubleValue() > 150) overboughtCount++;
            if (dto.getStochK() != null && dto.getStochK().doubleValue() > 80) overboughtCount++;
            if (dto.getWilliamsR() != null && dto.getWilliamsR().doubleValue() > -20) overboughtCount++;
            if (dto.getBollingerUpper() != null && dto.getLatestPrice() != null &&
                dto.getLatestPrice().doubleValue() >= dto.getBollingerUpper().doubleValue()) overboughtCount++;
            int threshold = (trendBullish && strongAdx) ? 4 : 3;
            if (overboughtCount >= threshold) {
                score = (int) Math.round(score * 0.7);
            }
        }

        // Store intermediate computation values for signal history trace
        dto.setRawTrendScore(trendScore);
        dto.setRawMomentumScore(momentumScore);
        dto.setRawStructureScore(structureScore);
        dto.setAdxMultiplier(adxMultiplier);
        int adxScore = (int) Math.round(rawTotal * adxMultiplier);
        dto.setScoreAfterAdx(adxScore);
        dto.setScoreAfterCandlestick(adxScore + candleResult.score());
        dto.setScoreAfterReversal(adxScore + candleResult.score() + reversalResult.score());

        return score;
    }

    /**
     * Lightweight scoring for historical signal computation.
     * Uses only pre-computed indicators, skips expensive operations like:
     * - Breakout detection
     * - Complex reversal patterns
     * - Full weighted score with all 16 factors
     * 
     * This is optimized for speed in backtesting scenarios.
     */
    private int computeLightweightScore(SignalDTO dto) {
        int score = 0;
        
        // RSI score (already in dto)
        if (dto.getRsi14() != null) {
            double rsi = dto.getRsi14().doubleValue();
            if (rsi < 30) score += 2;
            else if (rsi > 70) score -= 2;
        }
        
        // MACD score
        if (dto.getMacdHistogram() != null) {
            double hist = dto.getMacdHistogram().doubleValue();
            if (hist > 0) score += 1;
            else if (hist < 0) score -= 1;
        }
        
        // Bollinger Band score
        if (dto.getBollingerUpper() != null && dto.getBollingerLower() != null && dto.getLatestPrice() != null) {
            double price = dto.getLatestPrice().doubleValue();
            double upper = dto.getBollingerUpper().doubleValue();
            double lower = dto.getBollingerLower().doubleValue();
            if (price < lower) score += 2;
            else if (price > upper) score -= 2;
        }
        
        // Trend direction (SMA20 vs SMA50)
        if (dto.getSma20() != null && dto.getSma50() != null) {
            int trendCmp = dto.getSma20().compareTo(dto.getSma50());
            if (dto.getLatestPrice() != null) {
                double price = dto.getLatestPrice().doubleValue();
                double sma20 = dto.getSma20().doubleValue();
                double sma50 = dto.getSma50().doubleValue();
                
                if (price > sma20 && sma20 > sma50) score += 2;  // Bullish alignment
                else if (price < sma20 && sma20 < sma50) score -= 2;  // Bearish alignment
                else if (trendCmp > 0) score += 1;  // SMA20 > SMA50
                else if (trendCmp < 0) score -= 1;  // SMA20 < SMA50
            }
        }
        
        // Divergence bonus
        if (dto.isBullishDivergence()) score += 1;
        if (dto.isBearishDivergence()) score -= 1;
        
        // Volume confirmation
        if (dto.isVolumeConfirmed() && score != 0) {
            score += score > 0 ? 1 : -1;
        }
        
        // Weekly confluence (if available)
        if (dto.getWeeklyRsi() != null) {
            double weeklyRsi = dto.getWeeklyRsi().doubleValue();
            if (weeklyRsi < 40 && score > 0) score += 1;
            if (weeklyRsi > 60 && score < 0) score -= 1;
        }
        
        return score;
    }

    /**
     * Maps composite score to recommendation string.
     * Thresholds: STRONG_BUY ≥7, BUY ≥3, HOLD −3..+2, SELL ≤−4, STRONG_SELL ≤−7.
     * Package-private for direct unit testing.
     */
    String mapRecommendation(int score) {
        if (score >= STRONG_BUY_THRESHOLD) return "STRONG BUY";
        else if (score >= BUY_THRESHOLD) return "BUY";
        else if (score <= STRONG_SELL_THRESHOLD) return "STRONG SELL";
        else if (score <= SELL_THRESHOLD) return "SELL";
        else return "HOLD";
    }

    private BigDecimal calculateSMA(List<BigDecimal> prices, int period) {
        if (prices.size() < period) return null;
        List<BigDecimal> slice = prices.subList(prices.size() - period, prices.size());
        return slice.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateEMA(List<BigDecimal> prices, int period) {
        if (prices.size() < period) return null;
        BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));
        List<BigDecimal> initial = prices.subList(0, period);
        BigDecimal ema = initial.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        for (int i = period; i < prices.size(); i++) {
            ema = prices.get(i).multiply(k).add(ema.multiply(BigDecimal.ONE.subtract(k)));
        }
        return ema;
    }

    private BigDecimal calculateStdDev(List<BigDecimal> prices, int period) {
        List<BigDecimal> slice = prices.subList(Math.max(0, prices.size() - period), prices.size());
        BigDecimal mean = slice.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(slice.size()), 4, RoundingMode.HALF_UP);
        BigDecimal variance = slice.stream()
                .map(p -> p.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(slice.size() - 1), 4, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }

    private BigDecimal calculateTrendStrength(List<BigDecimal> prices) {
        if (prices.size() < 11) return BigDecimal.ZERO;
        List<BigDecimal> recent = prices.subList(prices.size() - 10, prices.size());
        long gains = 0;
        for (int i = 1; i < recent.size(); i++) {
            if (recent.get(i).compareTo(recent.get(i - 1)) > 0) gains++;
        }
        return BigDecimal.valueOf((double) gains / (recent.size() - 1) * 100)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVolatility(List<BigDecimal> prices) {
        if (prices.size() < 2) return BigDecimal.ZERO;
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal ret = prices.get(i).subtract(prices.get(i - 1))
                    .divide(prices.get(i - 1), 6, RoundingMode.HALF_UP);
            returns.add(ret);
        }

        if (returns.isEmpty()) return BigDecimal.ZERO;
        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        BigDecimal variance = returns.stream()
                .map(r -> r.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()) * 100)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * PHASE 1 OPTIMIZATION: Computes all technical indicators in a single O(N) pass
     * through the price data. This replaces the O(N²) approach where indicators were
     * recalculated from scratch for each historical date.
     *
     * @param prices all price data in chronological order
     * @return list of indicator sets, one per price bar
     */
    private List<HistoricalIndicatorSet> computeAllIndicatorsIncrementally(List<DailyPrice> prices) {
        List<HistoricalIndicatorSet> result = new ArrayList<>(prices.size());
        
        // RSI accumulators
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        
        // MACD accumulators
        BigDecimal ema12 = null;
        BigDecimal ema26 = null;
        List<BigDecimal> macdValues = new ArrayList<>();
        BigDecimal emaSignal = null;
        BigDecimal k12 = BigDecimal.valueOf(2.0 / 13);
        BigDecimal k26 = BigDecimal.valueOf(2.0 / 27);
        BigDecimal kSignal = BigDecimal.valueOf(2.0 / 10);
        
        // SMA accumulators (for rolling windows)
        List<BigDecimal> closeWindow = new ArrayList<>(20);
        
        // Stochastic accumulators
        List<BigDecimal> highWindow = new ArrayList<>(14);
        List<BigDecimal> lowWindow = new ArrayList<>(14);
        
        // Williams %R and CCI need additional windows
        List<BigDecimal> typicalPriceWindow = new ArrayList<>(20);
        List<BigDecimal> tpHistory = new ArrayList<>(20);
        
        // RSI history for divergence detection
        List<BigDecimal> rsiHistory = new ArrayList<>();
        
        // ADX accumulators
        BigDecimal trSum = BigDecimal.ZERO;
        BigDecimal plusDMsum = BigDecimal.ZERO;
        BigDecimal minusDMsum = BigDecimal.ZERO;
        BigDecimal smoothedPlusDM = BigDecimal.ZERO;
        BigDecimal smoothedMinusDM = BigDecimal.ZERO;
        BigDecimal smoothedTR = BigDecimal.ZERO;
        
        // Ultimate Oscillator accumulators
        List<BigDecimal> bp7 = new ArrayList<>(7);
        List<BigDecimal> tr7 = new ArrayList<>(7);
        List<BigDecimal> bp14 = new ArrayList<>(14);
        List<BigDecimal> tr14 = new ArrayList<>(14);
        List<BigDecimal> bp28 = new ArrayList<>(28);
        List<BigDecimal> tr28 = new ArrayList<>(28);
        
        // ROC
        List<BigDecimal> closesForROC = new ArrayList<>(13);
        
        BigDecimal prevClose = null;
        BigDecimal prevHigh = null;
        BigDecimal prevLow = null;
        
        for (int i = 0; i < prices.size(); i++) {
            DailyPrice p = prices.get(i);
            BigDecimal close = p.getClosingPrice();
            BigDecimal high = p.getHighPrice();
            BigDecimal low = p.getLowPrice();
            
            HistoricalIndicatorSet set = new HistoricalIndicatorSet();
            
            // Update windows
            closeWindow.add(close);
            if (closeWindow.size() > 50) closeWindow.remove(0);
            
            highWindow.add(high);
            if (highWindow.size() > 14) highWindow.remove(0);
            
            lowWindow.add(low);
            if (lowWindow.size() > 14) lowWindow.remove(0);
            
            BigDecimal typicalPrice = high.add(low).add(close).divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
            typicalPriceWindow.add(typicalPrice);
            if (typicalPriceWindow.size() > 20) typicalPriceWindow.remove(0);
            
            // ROC window
            closesForROC.add(close);
            if (closesForROC.size() > 13) closesForROC.remove(0);
            
            // Calculate True Range
            BigDecimal tr = BigDecimal.ZERO;
            BigDecimal plusDM = BigDecimal.ZERO;
            BigDecimal minusDM = BigDecimal.ZERO;
            
            if (i > 0) {
                BigDecimal highRange = high.subtract(prevHigh);
                BigDecimal lowRange = prevLow.subtract(low);
                BigDecimal closeRange = prevClose != null ? prevClose.subtract(low).max(high.subtract(prevClose)) : BigDecimal.ZERO;
                
                tr = highRange.max(lowRange).max(closeRange);
                
                if (highRange.compareTo(lowRange) > 0 && highRange.compareTo(BigDecimal.ZERO) > 0) {
                    plusDM = highRange;
                }
                if (lowRange.compareTo(highRange) > 0 && lowRange.compareTo(BigDecimal.ZERO) > 0) {
                    minusDM = lowRange;
                }
            }
            
            // RSI calculation (incremental)
            if (i >= 14) {
                BigDecimal gain = BigDecimal.ZERO;
                BigDecimal loss = BigDecimal.ZERO;
                
                if (i == 14) {
                    // Initial average
                    for (int j = 1; j <= 14; j++) {
                        BigDecimal change = prices.get(j).getClosingPrice()
                            .subtract(prices.get(j-1).getClosingPrice());
                        if (change.compareTo(BigDecimal.ZERO) > 0)
                            gain = gain.add(change);
                        else
                            loss = loss.add(change.abs());
                    }
                    avgGain = gain.divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                    avgLoss = loss.divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                } else {
                    BigDecimal change = close.subtract(prevClose);
                    if (change.compareTo(BigDecimal.ZERO) > 0) {
                        avgGain = avgGain.multiply(BigDecimal.valueOf(13))
                            .add(change)
                            .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                        avgLoss = avgLoss.multiply(BigDecimal.valueOf(13))
                            .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                    } else {
                        avgGain = avgGain.multiply(BigDecimal.valueOf(13))
                            .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                        avgLoss = avgLoss.multiply(BigDecimal.valueOf(13))
                            .add(change.abs())
                            .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                    }
                }
                
                if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
                    set.rsi14 = BigDecimal.valueOf(100);
                } else {
                    BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
                    set.rsi14 = BigDecimal.valueOf(100)
                        .subtract(BigDecimal.valueOf(100)
                            .divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
                }
                rsiHistory.add(set.rsi14);
            } else if (i == 14) {
                rsiHistory.add(BigDecimal.ZERO);
            }
            
            // MACD calculation (incremental)
            if (i >= 11 && ema12 == null) {
                // Initial EMA12
                ema12 = prices.subList(0, 12).stream()
                    .map(DailyPrice::getClosingPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            }
            if (i >= 25 && ema26 == null) {
                // Initial EMA26
                ema26 = prices.subList(0, 26).stream()
                    .map(DailyPrice::getClosingPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(26), 4, RoundingMode.HALF_UP);
            }
            if (i >= 11) {
                ema12 = close.multiply(k12).add(ema12.multiply(BigDecimal.ONE.subtract(k12)));
                
                if (i >= 25) {
                    ema26 = close.multiply(k26).add(ema26.multiply(BigDecimal.ONE.subtract(k26)));
                    
                    BigDecimal macd = ema12.subtract(ema26);
                    macdValues.add(macd);
                    
                    if (macdValues.size() == 9) {
                        emaSignal = macdValues.stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(9), 4, RoundingMode.HALF_UP);
                    }
                    
                    if (emaSignal != null) {
                        emaSignal = macd.multiply(kSignal)
                            .add(emaSignal.multiply(BigDecimal.ONE.subtract(kSignal)));
                        set.macd = macd;
                        set.macdSignal = emaSignal;
                        set.macdHistogram = macd.subtract(emaSignal);
                    }
                }
            }
            
            // SMA20 and SMA50
            if (closeWindow.size() >= 20) {
                set.sma20 = closeWindow.subList(closeWindow.size()-20, closeWindow.size()).stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(20), 4, RoundingMode.HALF_UP);
            }
            if (closeWindow.size() >= 50) {
                set.sma50 = closeWindow.subList(closeWindow.size()-50, closeWindow.size()).stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(50), 4, RoundingMode.HALF_UP);
            }
            
            // Bollinger Bands
            if (set.sma20 != null && closeWindow.size() >= 20) {
                List<BigDecimal> window20 = closeWindow.subList(closeWindow.size()-20, closeWindow.size());
                BigDecimal std = calculateStdDev(window20, 20);
                set.bollingerUpper = set.sma20.add(std.multiply(BigDecimal.valueOf(2)));
                set.bollingerLower = set.sma20.subtract(std.multiply(BigDecimal.valueOf(2)));
            }
            
            // Stochastic %K and %D
            if (highWindow.size() >= 14 && lowWindow.size() >= 14) {
                BigDecimal highestHigh = highWindow.stream()
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                BigDecimal lowestLow = lowWindow.stream()
                    .min(BigDecimal::compareTo).orElse(BigDecimal.ONE);
                
                if (highestHigh.compareTo(lowestLow) != 0) {
                    set.stochK = close.subtract(lowestLow)
                        .divide(highestHigh.subtract(lowestLow), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                }
                
                if (set.stochK != null && i >= 2) {
                    // %D = 3-day SMA of %K (simplified - would need to store last 3 %K values)
                    set.stochD = set.stochK; // Placeholder
                }
            }
            
            // Williams %R
            if (highWindow.size() >= 14 && lowWindow.size() >= 14 && set.stochK != null) {
                set.williamsR = set.stochK.subtract(BigDecimal.valueOf(100));
            }
            
            // CCI
            if (typicalPriceWindow.size() >= 20) {
                BigDecimal tpMean = typicalPriceWindow.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(20), 4, RoundingMode.HALF_UP);
                
                BigDecimal tpStd = typicalPriceWindow.stream()
                    .map(tp -> tp.subtract(tpMean).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(19), 4, RoundingMode.HALF_UP);
                tpStd = BigDecimal.valueOf(Math.sqrt(tpStd.doubleValue()));
                
                if (tpStd.compareTo(BigDecimal.ZERO) != 0) {
                    set.cci = typicalPrice.subtract(tpMean)
                        .divide(tpStd.multiply(BigDecimal.valueOf(0.015)), 4, RoundingMode.HALF_UP);
                }
            }
            
            // ADX (simplified incremental version)
            if (i > 0) {
                // Smooth TR, +DM, -DM
                if (i == 14) {
                    // Initial 14-period sums
                    for (int j = 1; j <= 14; j++) {
                        DailyPrice pj = prices.get(j);
                        DailyPrice pj1 = prices.get(j-1);
                        BigDecimal hRange = pj.getHighPrice().subtract(pj1.getHighPrice());
                        BigDecimal lRange = pj1.getLowPrice().subtract(pj.getLowPrice());
                        BigDecimal cRange = pj1.getClosingPrice().subtract(pj.getLowPrice())
                            .max(pj.getHighPrice().subtract(pj1.getClosingPrice()));
                        
                        smoothedTR = smoothedTR.add(hRange.max(lRange).max(cRange));
                        
                        if (hRange.compareTo(lRange) > 0 && hRange.compareTo(BigDecimal.ZERO) > 0) {
                            smoothedPlusDM = smoothedPlusDM.add(hRange);
                        }
                        if (lRange.compareTo(hRange) > 0 && lRange.compareTo(BigDecimal.ZERO) > 0) {
                            smoothedMinusDM = smoothedMinusDM.add(lRange);
                        }
                    }
                } else if (i > 14) {
                    smoothedTR = smoothedTR.multiply(BigDecimal.valueOf(13))
                        .add(tr)
                        .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                    smoothedPlusDM = smoothedPlusDM.multiply(BigDecimal.valueOf(13))
                        .add(plusDM)
                        .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                    smoothedMinusDM = smoothedMinusDM.multiply(BigDecimal.valueOf(13))
                        .add(minusDM)
                        .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
                }
                
                if (i >= 14 && smoothedTR.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal plusDI = smoothedPlusDM.divide(smoothedTR, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    BigDecimal minusDI = smoothedMinusDM.divide(smoothedTR, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    
                    set.plusDi = plusDI;
                    set.minusDi = minusDI;
                    
                    BigDecimal diDiff = plusDI.subtract(minusDI).abs();
                    BigDecimal diSum = plusDI.add(minusDI);
                    
                    if (diSum.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal dx = diDiff.divide(diSum, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                        
                        // For simplicity, use current dx as ADX (true ADX needs smoothing)
                        set.adx = dx;
                    }
                }
            }
            
            // ROC
            if (closesForROC.size() >= 13) {
                BigDecimal prevCloseROC = closesForROC.get(0);
                if (prevCloseROC.compareTo(BigDecimal.ZERO) != 0) {
                    set.roc = close.subtract(prevCloseROC)
                        .divide(prevCloseROC, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                }
            }
            
            // Store RSI history for divergence detection
            set.rsiHistory = new ArrayList<>(rsiHistory);
            
            result.add(set);
            
            // Update previous values
            prevClose = close;
            prevHigh = high;
            prevLow = low;
        }
        
        return result;
    }

    /**
     * Computes signal DTO using pre-computed indicators from the incremental pass.
     * This avoids recalculating indicators for each historical date.
     */
    private SignalDTO computeBaseSignalDtoWithPrecomputedIndicators(
            Stock stock, 
            List<DailyPrice> prices, 
            HistoricalIndicatorSet indicators,
            List<org.example.entity.CorporateEvent> allEvents) {
        
        if (prices == null || prices.size() < 20) return null;

        List<BigDecimal> closes = prices.stream()
                .map(DailyPrice::getClosingPrice)
                .collect(Collectors.toList());

        BigDecimal currentPrice = closes.get(closes.size() - 1);

        SignalDTO dto = new SignalDTO();
        dto.setStockId(stock.getId());
        dto.setSymbol(stock.getSymbol());
        dto.setName(stock.getName());
        dto.setSector(stock.getSector());
        dto.setLatestPrice(currentPrice);
        dto.setPnlPercent(stock.getPnlPercent());

        // Corporate events
        List<org.example.entity.CorporateEvent> events = allEvents != null
                ? allEvents.stream().filter(e -> e.getSymbol().equals(stock.getSymbol())).toList()
                : corporateEventService.getEventsForSymbol(stock.getSymbol());
        dto.setEventRisk(events != null && !events.isEmpty());

        // Use pre-computed indicators
        dto.setRsi14(indicators.rsi14);
        dto.setEma12(indicators.ema12);
        dto.setEma26(indicators.ema26);
        dto.setMacd(indicators.macd);
        dto.setMacdSignal(indicators.macdSignal);
        dto.setMacdHistogram(indicators.macdHistogram);
        dto.setSma20(indicators.sma20);
        dto.setSma50(indicators.sma50);
        dto.setBollingerUpper(indicators.bollingerUpper);
        dto.setBollingerLower(indicators.bollingerLower);
        dto.setStochK(indicators.stochK);
        dto.setStochD(indicators.stochD);
        dto.setWilliamsR(indicators.williamsR);
        dto.setCci(indicators.cci);
        dto.setAdx(indicators.adx);
        dto.setPlusDi(indicators.plusDi);
        dto.setMinusDi(indicators.minusDi);
        dto.setRoc(indicators.roc);

        // LIGHTWEIGHT HISTORICAL MODE: Skip expensive computations for backtesting
        // Only compute essential fields needed for signal recommendation
        
        // Multi-timeframe confluence (simplified - skip for very short price windows)
        if (prices.size() >= 60) {
            List<DailyPrice> weeklyPrices = aggregationService.aggregateWeekly(prices);
            dto.setWeeklyRsi(TechnicalAnalysisUtils.calculateRsi14(weeklyPrices));
        }
        if (prices.size() >= 180) {
            List<DailyPrice> monthlyPrices = aggregationService.aggregateMonthly(prices);
            dto.setMonthlyRsi(TechnicalAnalysisUtils.calculateRsi14(monthlyPrices));
        }

        // Volume confirmation (cheap O(N) calculation)
        if (prices.size() >= 20) {
            List<DailyPrice> last20 = prices.subList(prices.size() - 20, prices.size());
            double avgVol = last20.stream().mapToLong(p -> p.getVolume() != null ? p.getVolume() : 0L).average().orElse(0);
            long currentVol = prices.get(prices.size() - 1).getVolume() != null ? prices.get(prices.size() - 1).getVolume() : 0L;
            dto.setVolumeConfirmed(currentVol >= avgVol * 1.2);
        }

        // Divergence detection (uses pre-computed RSI history - already O(N))
        if (indicators.rsiHistory != null && !indicators.rsiHistory.isEmpty()) {
            dto.setBullishDivergence(TechnicalAnalysisUtils.hasBullishDivergence(prices, indicators.rsiHistory));
            dto.setBearishDivergence(TechnicalAnalysisUtils.hasBearishDivergence(prices, indicators.rsiHistory));
        }

        // Support/Resistance (use simple percentile fallback - cheap)
        List<BigDecimal> sortedCloses = closes.stream().sorted().collect(Collectors.toList());
        int tenPct = Math.max(1, sortedCloses.size() / 10);
        dto.setSupportLevel(sortedCloses.subList(0, tenPct).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(tenPct), 2, RoundingMode.HALF_UP));
        dto.setResistanceLevel(sortedCloses.subList(sortedCloses.size() - tenPct, sortedCloses.size()).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(tenPct), 2, RoundingMode.HALF_UP));

        // Trend strength and volatility (cheap O(N) calculations)
        dto.setTrendStrength(calculateTrendStrength(closes));
        dto.setVolatility(calculateVolatility(closes));

        // 52-week high/low (only if enough data)
        if (prices.size() >= 252) { // ~1 year of trading days
            List<DailyPrice> yearPrices = prices.subList(0, prices.size());
            BigDecimal high52 = yearPrices.stream().map(DailyPrice::getClosingPrice).max(BigDecimal::compareTo).orElse(currentPrice);
            BigDecimal low52 = yearPrices.stream().map(DailyPrice::getClosingPrice).min(BigDecimal::compareTo).orElse(currentPrice);
            dto.setHigh52Week(high52);
            dto.setLow52Week(low52);
            if (high52.compareTo(BigDecimal.ZERO) > 0) {
                dto.setPctFrom52WHigh(currentPrice.subtract(high52).multiply(BigDecimal.valueOf(100))
                    .divide(high52, 2, RoundingMode.HALF_UP));
            }
            if (low52.compareTo(BigDecimal.ZERO) > 0) {
                dto.setPctFrom52WLow(currentPrice.subtract(low52).multiply(BigDecimal.valueOf(100))
                    .divide(low52, 2, RoundingMode.HALF_UP));
            }
        }

        // Price vs SMA20
        if (dto.getSma20() != null && dto.getSma20().compareTo(BigDecimal.ZERO) > 0) {
            dto.setPriceVsSma20(currentPrice.subtract(dto.getSma20())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(dto.getSma20(), 2, RoundingMode.HALF_UP));
        }

        // SKIP breakout detection for historical mode (expensive candlestick pattern matching)
        dto.setVolumeBreakout(false);
        dto.setGapUp(false);
        dto.setGapDown(false);
        dto.setRangeBreakout(false);
        dto.setBreakoutScore(0);

        // Lightweight scoring: use only pre-computed indicators, skip complex logic
        int score = computeLightweightScore(dto);
        int fiidiiAdj = fiiDiiService.getScoreAdjustment();
        dto.setFiidiiScore(fiidiiAdj);
        score += fiidiiAdj;

        // Bearish trend discount
        if (dto.getSma20() != null && dto.getSma50() != null
                && dto.getSma20().compareTo(dto.getSma50()) < 0 && score > 0) {
            double discount = 0.85;
            boolean macdOk = dto.getMacdScore() > 0;
            boolean rsiOk = dto.getRsi14() != null && dto.getRsi14().doubleValue() >= 50;
            boolean priceOk = dto.getPriceVsSma20() != null && dto.getPriceVsSma20().doubleValue() > 0;
            boolean weeklyOk = dto.getWeeklyRsi() != null && dto.getWeeklyRsi().doubleValue() > 45;
            
            if (macdOk && rsiOk && priceOk && weeklyOk) {
                discount = 1.0;
            } else if (macdOk && rsiOk && priceOk) {
                discount = 0.9;
            }
            if (dto.getReversalScore() >= 2) {
                discount = Math.max(discount, 0.9);
            }
            int originalScore = score;
            score = (int) Math.round(score * discount);
            if (score != originalScore) {
                log.debug("Bearish trend discount for {}: {} -> {} (discount={}%)",
                    dto.getSymbol(), originalScore, score, Math.round(discount * 100));
            }
        }

        dto.setScoreAfterDiscount(score);

        // Rolling accuracy penalty
        if (dto.getSignalAccuracy30d() != null && dto.getSignalAccuracyTotal30d() >= 5) {
            double acc = dto.getSignalAccuracy30d().doubleValue();
            if (acc < 35) {
                score -= 3;
            } else if (acc < 45) {
                score -= 1;
            }
        }

        // High-confidence gate (if enabled)
        if (highConfidenceGateEnabled && score >= BUY_THRESHOLD) {
            boolean trendOk = dto.getSma20() != null && dto.getSma50() != null
                    && dto.getSma20().compareTo(dto.getSma50()) > 0;
            boolean momentumOk = dto.getRsi14() != null && dto.getRsi14().doubleValue() < 75;
            boolean riskRewardOk = false;
            if (dto.getLatestPrice() != null && dto.getSma20() != null) {
                riskRewardOk = dto.getLatestPrice().compareTo(
                    dto.getSma20().multiply(BigDecimal.valueOf(1.05))) <= 0;
            }
            if (!trendOk || !momentumOk || !riskRewardOk) {
                score = Math.min(score, BUY_THRESHOLD - 1);
                dto.setGateBlocked(true);
            }
        }

        // Channel trading override
        if (score > SELL_THRESHOLD && score < BUY_THRESHOLD
                && dto.getHigh52Week() != null && dto.getLow52Week() != null
                && dto.getLatestPrice() != null) {
            double price = dto.getLatestPrice().doubleValue();
            double high52 = dto.getHigh52Week().doubleValue();
            double low52 = dto.getLow52Week().doubleValue();
            double range = high52 - low52;
            if (range > 0) {
                double channelPct = ((price - low52) / range) * 100;
                boolean weeklyTrendOk = (dto.getWeeklyRsi() != null && dto.getWeeklyRsi().doubleValue() > 40);
                boolean channelTrendBearish = dto.getSma20() != null && dto.getSma50() != null
                        && (dto.getSma20().compareTo(dto.getSma50()) < 0
                            || (dto.getLatestPrice() != null && dto.getLatestPrice().compareTo(dto.getSma50()) < 0));
                
                if (channelPct <= 15 && score >= -2 && weeklyTrendOk && !channelTrendBearish) {
                    score = Math.max(score, BUY_THRESHOLD);
                } else if (channelPct >= 85 && score <= 2) {
                    score = Math.min(score, SELL_THRESHOLD);
                }
            }
        }

        dto.setRecommendation(mapRecommendation(score));
        dto.setCompositeScore(score);

        return dto;
    }
}
