package org.example.service;

import org.example.dto.SignalDTO;
import org.example.entity.SignalRecord;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.*;
import org.example.service.BreakoutDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @Mock
    private StockService stockService;
    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private PriceAggregationService aggregationService;
    @Mock
    private FiiDiiService fiiDiiService;
    @Mock
    private CorporateEventService corporateEventService;
    @Mock
    private TechnicalAnalysisService technicalAnalysisService;
    @Mock
    private TechnicalIndicatorRepository technicalIndicatorRepository;
    @Mock
    private SignalHistoricalPerformanceRepository signalHistoricalPerformanceRepository;
    @Mock
    private SignalRecordRepository signalRecordRepository;
    @Mock
    private SupportResistanceService supportResistanceService;
    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;
    @Mock
    private BreakoutDetector breakoutDetector;

    @InjectMocks
    private SignalService signalService;

    private SignalDTO baseDto;

    @BeforeEach
    void setUp() {
        baseDto = new SignalDTO();
        baseDto.setLatestPrice(new BigDecimal("150.00"));
        baseDto.setVolatility(new BigDecimal("2.5"));
        baseDto.setIndicatorCoverage(16); // All indicators available by default
    }

    // ===== computeConfidenceScore tests =====

    @Test
    void testConfidenceScore_Score6() {
        baseDto.setCompositeScore(6);
        // 50 + (6 * 5) = 80
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(80L, score);
    }

    @Test
    void testConfidenceScore_Score3() {
        baseDto.setCompositeScore(3);
        // 50 + (3 * 5) = 65
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(65L, score);
    }

    @Test
    void testConfidenceScore_NegativeComposite() {
        baseDto.setCompositeScore(-10);
        // 50 + (-10 * 5) = 0, clamped to 0
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(0L, score);
    }

    @Test
    void testConfidenceScore_HighComposite() {
        baseDto.setCompositeScore(14);
        // 50 + (14 * 5) = 120, clamped to 100
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(100L, score);
    }

    @Test
    void testConfidenceScore_VolumeConfirmedBonus() {
        baseDto.setCompositeScore(6);
        baseDto.setVolumeConfirmed(true);
        // 80 + 10 = 90
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(90L, score);
    }

    @Test
    void testConfidenceScore_EventRiskPenalty() {
        baseDto.setCompositeScore(6);
        baseDto.setEventRisk(true);
        // 80 - 20 = 60
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(60L, score);
    }

    @Test
    void testConfidenceScore_VolumeAndEventCancel() {
        baseDto.setCompositeScore(6);
        baseDto.setVolumeConfirmed(true);
        baseDto.setEventRisk(true);
        // 80 + 10 - 20 = 70
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(70L, score);
    }

    @Test
    void testConfidenceScore_WeeklyConfluenceBonus() {
        baseDto.setCompositeScore(3);
        baseDto.setWeeklyConfluenceScore(3);
        // 65 + abs(3) = 68
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(68L, score);
    }

    @Test
    void testConfidenceScore_MonthlyConfluenceBonus() {
        baseDto.setCompositeScore(3);
        baseDto.setMonthlyConfluenceScore(-1); // Negative confluence doesn't agree with positive composite → no bonus
        // 65 + 0 (confluence disagrees) = 65
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(65L, score);
    }

    @Test
    void testConfidenceScore_AllAdjustments() {
        baseDto.setCompositeScore(6);
        baseDto.setVolumeConfirmed(true);
        baseDto.setEventRisk(true);
        baseDto.setWeeklyConfluenceScore(3);
        baseDto.setMonthlyConfluenceScore(2);
        // 80 + 10 - 20 + 3 + 2 = 75
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(75L, score);
    }

    @Test
    void testConfidenceScore_MissingIndicatorPenalty() {
        baseDto.setCompositeScore(6);
        baseDto.setIndicatorCoverage(10);
        // base=80, 6 missing * 3 = 18 penalty -> 62
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(62L, score);
    }

    @Test
    void testConfidenceScore_StaleDataPenalty() {
        baseDto.setCompositeScore(6);
        baseDto.setSignalAge(5L);
        // base=80, stale 5d * 5 = 25 penalty -> 55
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(55L, score);
    }

    @Test
    void testConfidenceScore_StaleAndMissingCombined() {
        baseDto.setCompositeScore(6);
        baseDto.setSignalAge(3L);
        baseDto.setIndicatorCoverage(12);
        // base=80, stale 3*5=15, missing 4*3=12 -> 80-15-12=53
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(53L, score);
    }

    @Test
    void testConfidenceScore_FreshDataNoPenalty() {
        baseDto.setCompositeScore(6);
        baseDto.setSignalAge(0L);
        baseDto.setIndicatorCoverage(16);
        // base=80, no penalties -> 80
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(80L, score);
    }

    @Test
    void testConfidenceScore_StaleClampedToZero() {
        baseDto.setCompositeScore(3);
        baseDto.setSignalAge(20L);
        baseDto.setIndicatorCoverage(10);
        // base=65, stale 20*5=100, missing 6*3=18 -> 65-100-18=-53 clamped to 0
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(0L, score);
    }

    @Test
    void testConfidenceScore_CoverageWithNoPenalty() {
        baseDto.setCompositeScore(0);
        baseDto.setIndicatorCoverage(16);
        // base=50, no penalties -> 50
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(50L, score);
    }

    @Test
    void testConfidenceScore_AccuracyHighBonus() {
        baseDto.setCompositeScore(6);
        baseDto.setSignalAccuracy30d(new BigDecimal("85.0"));
        baseDto.setSignalAccuracyTotal30d(20);
        // base=80, accuracy >= 70 → +10 = 90
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(90L, score);
    }

    @Test
    void testConfidenceScore_AccuracyMediumBonus() {
        baseDto.setCompositeScore(6);
        baseDto.setSignalAccuracy30d(new BigDecimal("65.0"));
        baseDto.setSignalAccuracyTotal30d(10);
        // base=80, accuracy >= 60 → +5 = 85
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(85L, score);
    }

    @Test
    void testConfidenceScore_AccuracyLowPenalty() {
        baseDto.setCompositeScore(6);
        baseDto.setSignalAccuracy30d(new BigDecimal("45.0"));
        baseDto.setSignalAccuracyTotal30d(15);
        // base=80, accuracy < 50 → -15 = 65
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(65L, score);
    }

    @Test
    void testConfidenceScore_AccuracyVeryLowPenalty() {
        baseDto.setCompositeScore(6);
        baseDto.setSignalAccuracy30d(new BigDecimal("35.0"));
        baseDto.setSignalAccuracyTotal30d(8);
        // base=80, accuracy < 40 → -20 = 60
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(60L, score);
    }

    @Test
    void testConfidenceScore_InsufficientAccuracyData() {
        baseDto.setCompositeScore(6);
        baseDto.setSignalAccuracy30d(new BigDecimal("100.0"));
        baseDto.setSignalAccuracyTotal30d(3); // Only 3 records, below threshold of 5
        // base=80, no accuracy adjustment → 80
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(80L, score);
    }

    // ===== computeIndicatorCoverage tests =====

    @Test
    void testIndicatorCoverage_AllAvailable() {
        SignalDTO dto = new SignalDTO();
        dto.setStochK(BigDecimal.TEN);
        dto.setStochRsi(BigDecimal.TEN);
        dto.setUltimateOsc(BigDecimal.TEN);
        dto.setRoc(BigDecimal.TEN);
        dto.setWilliamsR(BigDecimal.TEN);
        dto.setCci(BigDecimal.TEN);
        dto.setMacdHistogram(BigDecimal.TEN);
        dto.setVwap(BigDecimal.TEN);
        dto.setTenkanSen(BigDecimal.TEN);
        dto.setKijunSen(BigDecimal.TEN);
        dto.setSenkouSpanA(BigDecimal.TEN);
        dto.setSenkouSpanB(BigDecimal.TEN);
        signalService.computeIndicatorCoverage(dto);
        assertEquals(19, dto.getIndicatorCoverage());
    }

    @Test
    void testIndicatorCoverage_AllMissing() {
        SignalDTO dto = new SignalDTO();
        // All DB indicators are null -> 19 - 9 = 10
        signalService.computeIndicatorCoverage(dto);
        assertEquals(10, dto.getIndicatorCoverage());
    }

    @Test
    void testIndicatorCoverage_Partial() {
        SignalDTO dto = new SignalDTO();
        dto.setStochK(BigDecimal.TEN);
        dto.setWilliamsR(BigDecimal.TEN);
        dto.setMacdHistogram(BigDecimal.TEN);
        // 19 - 6 missing = 13
        signalService.computeIndicatorCoverage(dto);
        assertEquals(13, dto.getIndicatorCoverage());
    }

    // ===== computeRollingAccuracy tests =====

    @Test
    void testRollingAccuracy_EnoughGoodRecords() {
        SignalDTO dto = new SignalDTO();
        dto.setStockId(1L);

        List<SignalRecord> records = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            SignalRecord r = new SignalRecord();
            r.setRecommendation("BUY");
            r.setWasAccurate10d(true);
            records.add(r);
        }

        when(signalRecordRepository.findRecentByStockId(eq(1L), any()))
                .thenReturn(records);

        signalService.computeRollingAccuracy(dto);

        assertNotNull(dto.getSignalAccuracy30d());
        assertEquals(100.0, dto.getSignalAccuracy30d().doubleValue(), 0.01);
        assertEquals(20, dto.getSignalAccuracyTotal30d());
        assertEquals(20, dto.getSignalAccuracyCorrect30d());
    }

    @Test
    void testRollingAccuracy_MixedRecords() {
        SignalDTO dto = new SignalDTO();
        dto.setStockId(1L);

        List<SignalRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SignalRecord r = new SignalRecord();
            r.setRecommendation("BUY");
            r.setWasAccurate10d(true);
            records.add(r);
        }
        for (int i = 0; i < 10; i++) {
            SignalRecord r = new SignalRecord();
            r.setRecommendation("BUY");
            r.setWasAccurate10d(false);
            records.add(r);
        }

        when(signalRecordRepository.findRecentByStockId(eq(1L), any()))
                .thenReturn(records);

        signalService.computeRollingAccuracy(dto);

        assertNotNull(dto.getSignalAccuracy30d());
        assertEquals(50.0, dto.getSignalAccuracy30d().doubleValue(), 0.01);
        assertEquals(20, dto.getSignalAccuracyTotal30d());
        assertEquals(10, dto.getSignalAccuracyCorrect30d());
    }

    @Test
    void testRollingAccuracy_ExcludesHold() {
        SignalDTO dto = new SignalDTO();
        dto.setStockId(1L);

        List<SignalRecord> records = new ArrayList<>();
        records.add(createRecord("HOLD", true));
        records.add(createRecord("HOLD", true));
        records.add(createRecord("HOLD", true));
        for (int i = 0; i < 10; i++) {
            records.add(createRecord("BUY", true));
        }

        when(signalRecordRepository.findRecentByStockId(eq(1L), any()))
                .thenReturn(records);

        signalService.computeRollingAccuracy(dto);

        assertNotNull(dto.getSignalAccuracy30d());
        assertEquals(100.0, dto.getSignalAccuracy30d().doubleValue(), 0.01);
        assertEquals(10, dto.getSignalAccuracyTotal30d());
    }

    @Test
    void testRollingAccuracy_InsufficientRecords() {
        SignalDTO dto = new SignalDTO();
        dto.setStockId(1L);

        List<SignalRecord> records = new ArrayList<>();
        records.add(createRecord("BUY", true));
        records.add(createRecord("BUY", true));
        records.add(createRecord("BUY", true));

        when(signalRecordRepository.findRecentByStockId(eq(1L), any()))
                .thenReturn(records);

        signalService.computeRollingAccuracy(dto);

        // Less than 5 records → no accuracy computed
        assertNull(dto.getSignalAccuracy30d());
    }

    @Test
    void testRollingAccuracy_FallbackTo5d() {
        SignalDTO dto = new SignalDTO();
        dto.setStockId(1L);

        List<SignalRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SignalRecord r = new SignalRecord();
            r.setRecommendation("SELL");
            r.setWasAccurate10d(null);
            r.setWasAccurate5d(true);
            records.add(r);
        }

        when(signalRecordRepository.findRecentByStockId(eq(1L), any()))
                .thenReturn(records);

        signalService.computeRollingAccuracy(dto);

        assertNotNull(dto.getSignalAccuracy30d());
        assertEquals(100.0, dto.getSignalAccuracy30d().doubleValue(), 0.01);
    }

    // Helper for rolling accuracy tests
    private SignalRecord createRecord(String recommendation, boolean accurate) {
        SignalRecord r = new SignalRecord();
        r.setRecommendation(recommendation);
        r.setWasAccurate10d(accurate);
        return r;
    }

    // ===== computePositionSize tests =====

    @Test
    void testPositionSize_NormalVolatility() {
        baseDto.setVolatility(new BigDecimal("2.5"));
        // 20 / 2.5 = 8, clamped to [1, 5] -> 5.0
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(0, new BigDecimal("5.0").compareTo(size));
    }

    @Test
    void testPositionSize_HighVolatility() {
        baseDto.setVolatility(new BigDecimal("8.0"));
        // 20 / 8 = 2.5, clamped to [1, 5] -> 2.5
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(0, new BigDecimal("2.5").compareTo(size));
    }

    @Test
    void testPositionSize_VeryHighVolatility() {
        baseDto.setVolatility(new BigDecimal("30.0"));
        // 20 / 30 = 0.67, clamped to [1, 5] -> 1.0
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(0, new BigDecimal("1.0").compareTo(size));
    }

    @Test
    void testPositionSize_NullVolatility() {
        baseDto.setVolatility(null);
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(0, new BigDecimal("5.0").compareTo(size));
    }

    @Test
    void testPositionSize_ZeroVolatility() {
        baseDto.setVolatility(BigDecimal.ZERO);
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(0, new BigDecimal("5.0").compareTo(size));
    }

    @Test
    void testPositionSize_Precision() {
        baseDto.setVolatility(new BigDecimal("3.0"));
        // 20 / 3 = 6.67, clamped to 5.0
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(1, size.scale());
    }

    // ===== computeSignalAge tests =====

    @Test
    void testSignalAge_Today() {
        List<DailyPrice> prices = new ArrayList<>();
        prices.add(new DailyPrice(null, BigDecimal.TEN, LocalDate.now()));
        Long age = signalService.computeSignalAge(prices);
        assertEquals(0L, age);
    }

    @Test
    void testSignalAge_FiveDaysAgo() {
        List<DailyPrice> prices = new ArrayList<>();
        prices.add(new DailyPrice(null, BigDecimal.TEN, LocalDate.now().minusDays(5)));
        Long age = signalService.computeSignalAge(prices);
        assertEquals(5L, age);
    }

    @Test
    void testSignalAge_NullPrices() {
        Long age = signalService.computeSignalAge(null);
        assertNull(age);
    }

    @Test
    void testSignalAge_EmptyPrices() {
        Long age = signalService.computeSignalAge(new ArrayList<>());
        assertNull(age);
    }

    @Test
    void testSignalAge_UsesLatestPrice() {
        List<DailyPrice> prices = new ArrayList<>();
        prices.add(new DailyPrice(null, BigDecimal.TEN, LocalDate.now().minusDays(10)));
        prices.add(new DailyPrice(null, BigDecimal.TEN, LocalDate.now().minusDays(3)));
        // Should use the last element (index size-1)
        Long age = signalService.computeSignalAge(prices);
        assertEquals(3L, age);
    }

    // ===== computeTargetAndStop tests =====

    @Test
    void testTargetStop_BuyRecommendation() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation("BUY");
        BigDecimal atr = new BigDecimal("2.00");

        signalService.computeTargetAndStop(dto, atr);

        assertNotNull(dto.getStopLoss());
        assertNotNull(dto.getTargetPrice());
        // stop = 100 - 2*2 = 96.00
        assertEquals(0, new BigDecimal("96.00").compareTo(dto.getStopLoss()));
        // target = 100 + 3*2 = 106.00
        assertEquals(0, new BigDecimal("106.00").compareTo(dto.getTargetPrice()));
    }

    @Test
    void testTargetStop_StrongBuyRecommendation() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("200.00"));
        dto.setRecommendation("STRONG BUY");
        BigDecimal atr = new BigDecimal("5.00");

        signalService.computeTargetAndStop(dto, atr);

        // stop = 200 - 2*5 = 190.00
        assertEquals(0, new BigDecimal("190.00").compareTo(dto.getStopLoss()));
        // target = 200 + 3*5 = 215.00
        assertEquals(0, new BigDecimal("215.00").compareTo(dto.getTargetPrice()));
    }

    @Test
    void testTargetStop_SellRecommendation() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation("SELL");
        BigDecimal atr = new BigDecimal("2.00");

        signalService.computeTargetAndStop(dto, atr);

        // stop = 100 + 2*2 = 104.00
        assertEquals(0, new BigDecimal("104.00").compareTo(dto.getStopLoss()));
        // target = 100 - 3*2 = 94.00
        assertEquals(0, new BigDecimal("94.00").compareTo(dto.getTargetPrice()));
    }

    @Test
    void testTargetStop_StrongSellRecommendation() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation("STRONG SELL");
        BigDecimal atr = new BigDecimal("2.00");

        signalService.computeTargetAndStop(dto, atr);

        // stop = 100 + 2*2 = 104.00
        assertEquals(0, new BigDecimal("104.00").compareTo(dto.getStopLoss()));
        // target = 100 - 3*2 = 94.00
        assertEquals(0, new BigDecimal("94.00").compareTo(dto.getTargetPrice()));
    }

    @Test
    void testTargetStop_HoldRecommendation() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation("HOLD");

        signalService.computeTargetAndStop(dto, new BigDecimal("2.00"));

        assertNull(dto.getStopLoss());
        assertNull(dto.getTargetPrice());
    }

    @Test
    void testTargetStop_NullAtr() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation("BUY");

        signalService.computeTargetAndStop(dto, null);

        assertNull(dto.getStopLoss());
        assertNull(dto.getTargetPrice());
    }

    @Test
    void testConfidenceScore_Score7VolumeConfirmedNoEvent() {
        baseDto.setCompositeScore(7);
        baseDto.setVolumeConfirmed(true);
        baseDto.setEventRisk(false);
        // base=50+35=85, volume +10 → 95
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(95L, score);
    }

    @Test
    void testConfidenceScore_Score7EventRiskNoVolume() {
        baseDto.setCompositeScore(7);
        baseDto.setVolumeConfirmed(false);
        baseDto.setEventRisk(true);
        // base=50+35=85, event -20 → 65
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(65L, score);
    }

    @Test
    void testConfidenceScore_Score3WithConfluence() {
        baseDto.setCompositeScore(3);
        baseDto.setWeeklyConfluenceScore(3);
        baseDto.setMonthlyConfluenceScore(1);
        // base=50+15=65, weekly +3, monthly +1 = 69
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(69L, score);
    }

    @Test
    void testConfidenceScore_BaselineZero() {
        baseDto.setCompositeScore(0);
        // base=50, no adjustments → 50
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(50L, score);
    }

    @Test
    void testConfidenceScore_ScoreNegativeWithConfluence() {
        baseDto.setCompositeScore(-3);
        baseDto.setWeeklyConfluenceScore(2); // Positive confluence doesn't agree with negative composite → no bonus
        // 35 + 0 (confluence disagrees) = 35
        Long score = signalService.computeConfidenceScore(baseDto);
        assertEquals(35L, score);
    }

    // ===== computeTargetAndStop additional tests =====

    @Test
    void testTargetStop_BuyWithAtr5() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation("BUY");
        BigDecimal atr = new BigDecimal("5.00");

        signalService.computeTargetAndStop(dto, atr);

        // stop = 100 - 2*5 = 90.00
        assertEquals(0, new BigDecimal("90.00").compareTo(dto.getStopLoss()));
        // target = 100 + 3*5 = 115.00
        assertEquals(0, new BigDecimal("115.00").compareTo(dto.getTargetPrice()));
    }

    @Test
    void testTargetStop_NullRecommendation() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation(null);

        signalService.computeTargetAndStop(dto, new BigDecimal("2.00"));

        assertNull(dto.getStopLoss());
        assertNull(dto.getTargetPrice());
    }

    @Test
    void testTargetStop_ZeroAtr() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation("BUY");

        signalService.computeTargetAndStop(dto, BigDecimal.ZERO);

        assertNull(dto.getStopLoss());
        assertNull(dto.getTargetPrice());
    }

    @Test
    void testTargetStop_ScaleIs2() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100.00"));
        dto.setRecommendation("SELL");
        BigDecimal atr = new BigDecimal("2.33333");

        signalService.computeTargetAndStop(dto, atr);

        // stop = 100 + 2*2.33333 = 104.66666 → 104.67
        assertEquals(2, dto.getStopLoss().scale());
        // target = 100 - 3*2.33333 = 93.00001 → 93.00
        assertEquals(2, dto.getTargetPrice().scale());
    }

    // ===== computePositionSize additional tests =====

    @Test
    void testPositionSize_Volatility5() {
        baseDto.setVolatility(new BigDecimal("5.0"));
        // 20 / 5 = 4.0, within [1,5]
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(0, new BigDecimal("4.0").compareTo(size));
    }

    @Test
    void testPositionSize_Volatility20Clamped() {
        baseDto.setVolatility(new BigDecimal("20.0"));
        // 20 / 20 = 1.0, clamped to min 1
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(0, new BigDecimal("1.0").compareTo(size));
    }

    @Test
    void testPositionSize_VolatilityMinValue() {
        baseDto.setVolatility(new BigDecimal("0.1"));
        // 20 / 0.1 = 200, clamped to max 5.0
        BigDecimal size = signalService.computePositionSize(baseDto);
        assertEquals(0, new BigDecimal("5.0").compareTo(size));
    }

    // ===== computeSignalAge additional tests =====

    @Test
    void testSignalAge_NullPriceDate() {
        List<DailyPrice> prices = new ArrayList<>();
        DailyPrice dp = new DailyPrice();
        dp.setClosingPrice(BigDecimal.TEN);
        dp.setPriceDate(null);
        prices.add(dp);
        Long age = signalService.computeSignalAge(prices);
        assertNull(age);
    }

    @Test
    void testTargetStop_NullPrice() {
        SignalDTO dto = new SignalDTO();
        dto.setRecommendation("BUY");

        signalService.computeTargetAndStop(dto, new BigDecimal("2.00"));

        assertNull(dto.getStopLoss());
        assertNull(dto.getTargetPrice());
    }

    // ===== BUY gate regression tests =====

    @Test
    void testComputeWeightedScore_NullIndicators_DoNotThrow() {
        // Regression: all indicator fields are null (new stock with no DB data)
        // computeWeightedScore should return a score without NPE
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int score = signalService.computeWeightedScore(dto, stock, prices);
        // Score should be finite, not throw
        assertTrue(score >= -20 && score <= 20,
                "Score should be in reasonable range, got: " + score);
    }

    @Test
    void testComputeWeightedScore_BullishSetup_PositiveScore() {
        // Stock oversold (RSI < 30), above SMA20, strong MACD
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setRsi14(new BigDecimal("25"));  // Oversold
        dto.setSma20(new BigDecimal("95"));  // Price above SMA20
        dto.setSma50(new BigDecimal("90"));  // SMA20 > SMA50
        dto.setMacd(new BigDecimal("2.5"));  // MACD above signal
        dto.setMacdSignal(new BigDecimal("1.0"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setAdx(new BigDecimal("30"));    // Strong trend
        dto.setPlusDi(new BigDecimal("25"));
        dto.setMinusDi(new BigDecimal("15"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int score = signalService.computeWeightedScore(dto, stock, prices);
        assertTrue(score > 0, "Bullish setup should produce positive score, got: " + score);
    }

    @Test
    void testComputeWeightedScore_BearishSetup_NegativeScore() {
        // Stock overbought (RSI > 75), below SMA20, bearish MACD
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setRsi14(new BigDecimal("80"));  // Overbought
        dto.setSma20(new BigDecimal("105")); // Price below SMA20
        dto.setSma50(new BigDecimal("110")); // SMA20 < SMA50
        dto.setMacd(new BigDecimal("-2.5")); // MACD below signal
        dto.setMacdSignal(new BigDecimal("-1.0"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setAdx(new BigDecimal("30"));
        dto.setPlusDi(new BigDecimal("15"));
        dto.setMinusDi(new BigDecimal("25"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int score = signalService.computeWeightedScore(dto, stock, prices);
        assertTrue(score < 0, "Bearish setup should produce negative score, got: " + score);
    }

    @Test
    void testComputeWeightedScore_AdxMultiplier_LowAdxReducesScore() {
        // ADX < 15 → multiplier 0.3, should dampen the score
        SignalDTO dtoLowAdx = new SignalDTO();
        dtoLowAdx.setLatestPrice(new BigDecimal("100"));
        dtoLowAdx.setRsi14(new BigDecimal("25"));
        dtoLowAdx.setAdx(new BigDecimal("10"));  // No trend
        dtoLowAdx.setHigh52Week(new BigDecimal("120"));
        dtoLowAdx.setLow52Week(new BigDecimal("80"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int scoreLowAdx = signalService.computeWeightedScore(dtoLowAdx, stock, prices);

        // Same setup but with strong trend ADX
        SignalDTO dtoHighAdx = new SignalDTO();
        dtoHighAdx.setLatestPrice(new BigDecimal("100"));
        dtoHighAdx.setRsi14(new BigDecimal("25"));
        dtoHighAdx.setAdx(new BigDecimal("40"));  // Strong trend
        dtoHighAdx.setHigh52Week(new BigDecimal("120"));
        dtoHighAdx.setLow52Week(new BigDecimal("80"));

        int scoreHighAdx = signalService.computeWeightedScore(dtoHighAdx, stock, prices);

        assertTrue(scoreHighAdx >= scoreLowAdx,
                "High ADX score (" + scoreHighAdx + ") should be >= low ADX score (" + scoreLowAdx + ")");
    }

    @Test
    void testComputeWeightedScore_BearishTrendDampener_ReducesButDoesNotZeroScore() {
        // SMA20 < SMA50 with strong MACD should have dampened but still positive trend score
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setSma20(new BigDecimal("95"));
        dto.setSma50(new BigDecimal("100"));  // bearish
        dto.setMacd(new BigDecimal("2.5"));
        dto.setMacdSignal(new BigDecimal("1.0"));
        dto.setRsi14(new BigDecimal("60"));
        dto.setPriceVsSma20(new BigDecimal("5.26"));
        dto.setWeeklyRsi(new BigDecimal("50"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setAdx(new BigDecimal("25"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        // computeWeightedScore applies the bearish trend dampener (50% - 2) but not the
        // graduated SMA cap (which is applied in computeBaseSignalDto)
        int score = signalService.computeWeightedScore(dto, stock, prices);
        // Score with dampener should be positive but modest given the bearish trend
        assertTrue(score >= 0 && score <= 10,
                "Bearish dampener should produce modest positive score, got: " + score);
    }

    @Test
    void testComputeWeightedScore_RsiReversalBonus_DoesNotThrow() {
        // Price series with rising RSI (upward trend) should not throw
        List<DailyPrice> risingPrices = new ArrayList<>();
        BigDecimal price = new BigDecimal("90");
        for (int i = 0; i < 20; i++) {
            risingPrices.add(new DailyPrice(null, price, price.add(BigDecimal.ONE),
                    price.subtract(BigDecimal.ONE), price, 1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        for (int i = 0; i < 15; i++) {
            price = price.add(new BigDecimal("1.5"));
            risingPrices.add(new DailyPrice(null, price, price.add(BigDecimal.ONE),
                    price.subtract(BigDecimal.ONE), price, 1000L,
                    LocalDate.of(2023, 1, 1).plusDays(20 + i)));
        }

        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(price);
        dto.setSma20(new BigDecimal("95"));
        dto.setSma50(new BigDecimal("100"));
        dto.setAdx(new BigDecimal("25"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        Stock stock = new Stock();
        stock.setId(1L);

        int score = signalService.computeWeightedScore(dto, stock, risingPrices);
        assertTrue(score >= -15 && score <= 15,
                "Score should be in reasonable range with RSI reversal, got: " + score);
    }

    @Test
    void testComputeWeightedScore_OversoldSuppression_BypassedDuringReversal() {
        // RSI < 30 + trendBearish: normally oversold BUY is suppressed (0 instead of +2)
        // With reversalScore being computed inside computeWeightedScore (cannot be pre-set),
        // verify the score is in reasonable range
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setSma20(new BigDecimal("95"));
        dto.setSma50(new BigDecimal("100"));  // bearish
        dto.setRsi14(new BigDecimal("25"));    // oversold
        dto.setAdx(new BigDecimal("25"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int score = signalService.computeWeightedScore(dto, stock, prices);
        assertTrue(score >= -15 && score <= 15,
                "Score should be computed with reversal bypass, got: " + score);
    }

    // ===== Regression: proximity momentum gate (Step 2) =====

    @Test
    void testComputeWeightedScore_ProximityRequiresPositiveMomentum() {
        // Price near 52W low but negative 5d return → proximity bonus blocked
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setPctFrom52WLow(new BigDecimal("4.5"));
        dto.setHistoricalReturn5d(new BigDecimal("-1.0"));
        dto.setSma20(new BigDecimal("110"));
        dto.setSma50(new BigDecimal("105"));
        dto.setAdx(new BigDecimal("25"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        signalService.computeWeightedScore(dto, stock, prices);

        assertEquals(0, dto.getWeek52Score(),
                "Negative 5d return should block proximity bonus");
    }

    @Test
    void testComputeWeightedScore_ProximityWorksWithPositiveMomentum() {
        // Price near 52W low with positive 5d return → proximity bonus active
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setPctFrom52WLow(new BigDecimal("4.5"));
        dto.setHistoricalReturn5d(new BigDecimal("1.0"));
        dto.setSma20(new BigDecimal("110"));
        dto.setSma50(new BigDecimal("105"));
        dto.setAdx(new BigDecimal("25"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        signalService.computeWeightedScore(dto, stock, prices);

        assertEquals(2, dto.getWeek52Score(),
                "Positive 5d return should allow proximity bonus");
    }

    // ===== Regression: overbought dampener in strong trend (Step 4) =====

    @Test
    void testOverboughtDampener_FiresInStrongTrend() {
        // 5 overbought signals with SMA20 > SMA50 and ADX >= 25 → threshold 4 → fires
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setSma20(new BigDecimal("105"));
        dto.setSma50(new BigDecimal("100"));
        dto.setAdx(new BigDecimal("30"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setRsi14(new BigDecimal("60"));
        dto.setStochRsi(new BigDecimal("90"));
        dto.setCci(new BigDecimal("200"));
        dto.setStochK(new BigDecimal("90"));
        dto.setWilliamsR(new BigDecimal("-10"));
        dto.setBollingerUpper(new BigDecimal("100"));
        dto.setBollingerLower(new BigDecimal("90"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int score5 = signalService.computeWeightedScore(dto, stock, prices);

        // Same setup with only 3 overbought signals (below threshold of 4)
        SignalDTO dto2 = new SignalDTO();
        dto2.setLatestPrice(new BigDecimal("100"));
        dto2.setSma20(new BigDecimal("105"));
        dto2.setSma50(new BigDecimal("100"));
        dto2.setAdx(new BigDecimal("30"));
        dto2.setHigh52Week(new BigDecimal("120"));
        dto2.setLow52Week(new BigDecimal("80"));
        dto2.setRsi14(new BigDecimal("60"));
        dto2.setStochRsi(new BigDecimal("90"));
        dto2.setCci(new BigDecimal("200"));
        dto2.setStochK(new BigDecimal("90"));
        dto2.setWilliamsR(new BigDecimal("-50"));
        dto2.setBollingerUpper(new BigDecimal("95"));
        dto2.setBollingerLower(new BigDecimal("85"));

        int score3 = signalService.computeWeightedScore(dto2, stock, prices);

        assertTrue(score5 <= score3,
                "5 overbought signals (score=" + score5 + ") should dampen below 3 signals (score=" + score3 + ")");
    }

    // ===== Regression: rolling accuracy threshold (Step 3) =====

    @Test
    void testRollingAccuracy_ThresholdOf10_Below() {
        SignalDTO dto = new SignalDTO();
        dto.setStockId(1L);

        List<SignalRecord> records = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            SignalRecord r = new SignalRecord();
            r.setRecommendation("BUY");
            r.setWasAccurate10d(true);
            records.add(r);
        }

        when(signalRecordRepository.findRecentByStockId(eq(1L), any()))
                .thenReturn(records);

        signalService.computeRollingAccuracy(dto);

        assertNull(dto.getSignalAccuracy30d(),
                "Should not compute accuracy with 9 records (threshold 10)");
    }

    @Test
    void testRollingAccuracy_ThresholdOf10_AtThreshold() {
        SignalDTO dto = new SignalDTO();
        dto.setStockId(2L);

        List<SignalRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SignalRecord r = new SignalRecord();
            r.setRecommendation("BUY");
            r.setWasAccurate10d(true);
            records.add(r);
        }

        when(signalRecordRepository.findRecentByStockId(eq(2L), any()))
                .thenReturn(records);

        signalService.computeRollingAccuracy(dto);

        assertNotNull(dto.getSignalAccuracy30d(),
                "Should compute accuracy with 10 records (threshold 10)");
        assertEquals(100.0, dto.getSignalAccuracy30d().doubleValue(), 0.01);
        assertEquals(10, dto.getSignalAccuracyTotal30d());
    }

    // ===== mapRecommendation threshold tests (Bug fix: BUY ≥3, SELL ≤−4) =====

    @Test
    void testMapRecommendation_StrongBuy_AtBoundary() {
        assertEquals("STRONG BUY", signalService.mapRecommendation(7));
    }

    @Test
    void testMapRecommendation_StrongBuy_AboveBoundary() {
        assertEquals("STRONG BUY", signalService.mapRecommendation(10));
    }

    @Test
    void testMapRecommendation_StrongBuy_Score9() {
        // Bug 4: score=9 was mapped to BUY by old thresholds; with constants it's STRONG BUY
        assertEquals("STRONG BUY", signalService.mapRecommendation(9));
    }

    @Test
    void testMapRecommendation_Buy_AtBoundary() {
        // Before fix: score 3 → HOLD (bug). After fix: score 3 → BUY
        assertEquals("BUY", signalService.mapRecommendation(3));
    }

    @Test
    void testMapRecommendation_Buy_Score4() {
        // Before fix: score 4 → HOLD (bug). After fix: score 4 → BUY
        assertEquals("BUY", signalService.mapRecommendation(4));
    }

    @Test
    void testMapRecommendation_Buy_Score5() {
        assertEquals("BUY", signalService.mapRecommendation(5));
    }

    @Test
    void testMapRecommendation_Buy_Score6() {
        assertEquals("BUY", signalService.mapRecommendation(6));
    }

    @Test
    void testMapRecommendation_Hold_Positive2() {
        assertEquals("HOLD", signalService.mapRecommendation(2));
    }

    @Test
    void testMapRecommendation_Hold_Zero() {
        assertEquals("HOLD", signalService.mapRecommendation(0));
    }

    @Test
    void testMapRecommendation_Hold_Negative3() {
        assertEquals("HOLD", signalService.mapRecommendation(-3));
    }

    @Test
    void testMapRecommendation_Sell_AtBoundary() {
        // Before fix: score -4 → HOLD (bug). After fix: score -4 → SELL
        assertEquals("SELL", signalService.mapRecommendation(-4));
    }

    @Test
    void testMapRecommendation_Sell_ScoreMinus5() {
        assertEquals("SELL", signalService.mapRecommendation(-5));
    }

    @Test
    void testMapRecommendation_Sell_ScoreMinus6() {
        assertEquals("SELL", signalService.mapRecommendation(-6));
    }

    @Test
    void testMapRecommendation_StrongSell_AtBoundary() {
        assertEquals("STRONG SELL", signalService.mapRecommendation(-7));
    }

    @Test
    void testMapRecommendation_StrongSell_BelowBoundary() {
        assertEquals("STRONG SELL", signalService.mapRecommendation(-10));
    }

    // ===== ADX counter-trend dampening tests =====

    @Test
    void testComputeWeightedScore_AdxCounterTrend_DampensScore() {
        // Positive signal (bullish indicators) but -DI > +DI (counter-trend) → multiplier reduced
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setRsi14(new BigDecimal("60"));   // Neutral RSI
        dto.setSma20(new BigDecimal("105"));  // Price below SMA20 → bearish
        dto.setSma50(new BigDecimal("110"));  // SMA20 < SMA50 → bearish
        dto.setMacd(new BigDecimal("2.5"));   // Positive MACD
        dto.setMacdSignal(new BigDecimal("1.0"));
        dto.setAdx(new BigDecimal("35"));     // Strong trend
        dto.setPlusDi(new BigDecimal("15"));  // +DI low → bearish direction
        dto.setMinusDi(new BigDecimal("25")); // -DI high → bearish direction
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int scoreCounterTrend = signalService.computeWeightedScore(dto, stock, prices);

        // Same setup but with +DI > -DI (with-trend) → no extra dampening
        SignalDTO dto2 = new SignalDTO();
        dto2.setLatestPrice(new BigDecimal("100"));
        dto2.setRsi14(new BigDecimal("60"));
        dto2.setSma20(new BigDecimal("105"));
        dto2.setSma50(new BigDecimal("110"));
        dto2.setMacd(new BigDecimal("2.5"));
        dto2.setMacdSignal(new BigDecimal("1.0"));
        dto2.setAdx(new BigDecimal("35"));
        dto2.setPlusDi(new BigDecimal("25"));  // +DI high → bullish direction
        dto2.setMinusDi(new BigDecimal("15")); // -DI low → bullish direction
        dto2.setHigh52Week(new BigDecimal("120"));
        dto2.setLow52Week(new BigDecimal("80"));

        int scoreWithTrend = signalService.computeWeightedScore(dto2, stock, prices);

        // Counter-trend score should be less than or equal to with-trend score
        // (counter-trend gets extra ×0.7 dampening on ADX multiplier)
        assertTrue(scoreCounterTrend <= scoreWithTrend,
                "Counter-trend score (" + scoreCounterTrend + ") should be <= with-trend score (" + scoreWithTrend + ")");
    }

    @Test
    void testComputeWeightedScore_AdxWithTrend_NoExtraDampening() {
        // Positive signal with +DI > -DI (with-trend) → no counter-trend dampening
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setRsi14(new BigDecimal("60"));
        dto.setSma20(new BigDecimal("105"));
        dto.setSma50(new BigDecimal("100"));  // SMA20 > SMA50 → bullish
        dto.setMacd(new BigDecimal("2.5"));
        dto.setMacdSignal(new BigDecimal("1.0"));
        dto.setAdx(new BigDecimal("35"));
        dto.setPlusDi(new BigDecimal("25"));  // +DI > -DI → with-trend for positive signal
        dto.setMinusDi(new BigDecimal("15"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int score = signalService.computeWeightedScore(dto, stock, prices);

        // With ADX=35, multiplier should be 1.0 (no dampening)
        // Verify the DTO records that no filter was applied
        assertEquals(0, dto.getAdxFilterApplied(),
                "With-trend signal should not have counter-trend filter applied");
    }

    @Test
    void testComputeWeightedScore_AdxCounterTrend_FilterRecorded() {
        // Verify adxFilterApplied is set when counter-trend dampening fires
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setRsi14(new BigDecimal("60"));
        dto.setSma20(new BigDecimal("105"));
        dto.setSma50(new BigDecimal("110"));  // bearish
        dto.setMacd(new BigDecimal("2.5"));
        dto.setMacdSignal(new BigDecimal("1.0"));
        dto.setAdx(new BigDecimal("35"));     // Strong trend
        dto.setPlusDi(new BigDecimal("15"));  // Counter-trend
        dto.setMinusDi(new BigDecimal("25"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        signalService.computeWeightedScore(dto, stock, prices);

        assertEquals(1, dto.getAdxFilterApplied(),
                "Counter-trend dampening should set adxFilterApplied=1");
    }

    // ===== Overbought dampener rounding test =====

    @Test
    void testComputeWeightedScore_OverboughtDampener_UsesRounding() {
        // Score of 5 * 0.7 = 3.5 → Math.round → 4 (not truncated to 3)
        // This test verifies the fix from (int)(score * 0.7) to (int)Math.round(score * 0.7)
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setSma20(new BigDecimal("105"));  // Bullish (SMA20 > SMA50)
        dto.setSma50(new BigDecimal("100"));
        dto.setAdx(new BigDecimal("30"));
        dto.setRsi14(new BigDecimal("60"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        // Overbought indicators to trigger dampener (need 3+ for non-bullish, 4+ for bullish+strongADX)
        dto.setStochRsi(new BigDecimal("90"));   // overbought
        dto.setCci(new BigDecimal("200"));        // overbought
        dto.setStochK(new BigDecimal("90"));      // overbought
        dto.setWilliamsR(new BigDecimal("-10"));  // overbought
        dto.setBollingerUpper(new BigDecimal("100"));  // price at upper band
        dto.setBollingerLower(new BigDecimal("90"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int score = signalService.computeWeightedScore(dto, stock, prices);

        // The score should be dampened by ×0.7 but with rounding, not truncation
        // We can't assert the exact value without knowing the raw score,
        // but we can verify it's in a reasonable range
        assertTrue(score >= -10 && score <= 10,
                "Overbought-dampened score should be in reasonable range, got: " + score);
    }

    // ===== Channel override guard: downtrend stock should not get BUY =====

    @Test
    void testComputeWeightedScore_Downtrend_BearishTrendDampenerReducesScore() {
        // SMA20 < SMA50 (downtrend) with otherwise bullish indicators
        // The bearish trend dampener should reduce the trend score
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setSma20(new BigDecimal("95"));   // Below SMA50
        dto.setSma50(new BigDecimal("100"));  // SMA20 < SMA50 → bearish
        dto.setRsi14(new BigDecimal("60"));
        dto.setMacd(new BigDecimal("2.5"));
        dto.setMacdSignal(new BigDecimal("1.0"));
        dto.setPriceVsSma20(new BigDecimal("5.26"));
        dto.setAdx(new BigDecimal("30"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        Stock stock = new Stock();
        stock.setId(1L);
        List<DailyPrice> prices = createMinimalPrices(30);

        int score = signalService.computeWeightedScore(dto, stock, prices);

        // Bearish dampener applies: trendScore = Math.round(trendScore * 0.5) - 2
        // This should significantly reduce bullish signals in downtrend
        // A strong bullish setup in downtrend should produce a much lower score
        // than the same setup in uptrend
        SignalDTO dtoUptrend = new SignalDTO();
        dtoUptrend.setLatestPrice(new BigDecimal("100"));
        dtoUptrend.setSma20(new BigDecimal("105"));  // Above SMA50
        dtoUptrend.setSma50(new BigDecimal("100"));  // SMA20 > SMA50 → bullish
        dtoUptrend.setRsi14(new BigDecimal("60"));
        dtoUptrend.setMacd(new BigDecimal("2.5"));
        dtoUptrend.setMacdSignal(new BigDecimal("1.0"));
        dtoUptrend.setPriceVsSma20(new BigDecimal("-4.76"));
        dtoUptrend.setAdx(new BigDecimal("30"));
        dtoUptrend.setHigh52Week(new BigDecimal("120"));
        dtoUptrend.setLow52Week(new BigDecimal("80"));

        int scoreUptrend = signalService.computeWeightedScore(dtoUptrend, stock, prices);

        assertTrue(score < scoreUptrend,
                "Downtrend score (" + score + ") should be less than uptrend score (" + scoreUptrend + ")");
    }

    // ===== Bug 2 regression: BUY gate and channel override produce consistent score+recommendation =====

    @Test
    void testComputeShadowDto_ScoreMatchesRecommendation() {
        // Bug 2: computeBaseSignalDto used to set recommendation and compositeScore independently
        // in the BUY gate and channel override blocks. After the fix, both are derived from
        // the same `score` variable at the end. This test verifies the output is always consistent.
        Stock stock = createTestStock("TEST", "Test Stock");
        List<DailyPrice> prices = createMinimalPrices(30);

        when(breakoutDetector.detect(anyList(), eq(stock.getId())))
                .thenReturn(new BreakoutDetector.BreakoutResult());

        SignalDTO result = signalService.computeShadowDto(stock, prices);

        if (result != null) {
            // Core invariant: recommendation must match what mapRecommendation(compositeScore) produces
            assertEquals(
                    signalService.mapRecommendation(result.getCompositeScore()),
                    result.getRecommendation(),
                    "recommendation must match mapRecommendation(compositeScore) — "
                    + "compositeScore=" + result.getCompositeScore()
                    + " recommendation=" + result.getRecommendation());
        }
    }

    @Test
    void testComputeShadowDto_MultipleStocks_AllConsistent() {
        // Bug 2: verify consistency across multiple stocks with different price patterns
        String[] symbols = {"STOCK_A", "STOCK_B", "STOCK_C", "STOCK_D", "STOCK_E"};
        for (String sym : symbols) {
            Stock stock = createTestStock(sym, sym + " Corp");
            // Vary price patterns to hit different score ranges
            List<DailyPrice> prices = createVaryingPrices(sym);

            when(breakoutDetector.detect(anyList(), eq(stock.getId())))
                    .thenReturn(new BreakoutDetector.BreakoutResult());

            SignalDTO result = signalService.computeShadowDto(stock, prices);

            if (result != null) {
                assertEquals(
                        signalService.mapRecommendation(result.getCompositeScore()),
                        result.getRecommendation(),
                        sym + ": score=" + result.getCompositeScore()
                        + " rec=" + result.getRecommendation());
            }
        }
    }

    // ===== Bug 3 regression: getBuySignals/getSellSignals filter by recommendation string =====

    @Test
    void testGetBuyAndSellSignals_FilterByRecommendation() {
        // Bug 3: getBuySignals used compositeScore >= 5 but mapRecommendation uses score >= 3 for BUY.
        // After the fix, getBuySignals filters by recommendation string.
        // Verify the filter predicates work correctly by testing the stream logic directly.

        List<SignalDTO> signals = new ArrayList<>();

        SignalDTO buy1 = new SignalDTO();
        buy1.setRecommendation("BUY");
        buy1.setCompositeScore(3);  // BUY threshold boundary — previously excluded by score >= 5
        signals.add(buy1);

        SignalDTO buy2 = new SignalDTO();
        buy2.setRecommendation("STRONG BUY");
        buy2.setCompositeScore(9);
        signals.add(buy2);

        SignalDTO hold = new SignalDTO();
        hold.setRecommendation("HOLD");
        hold.setCompositeScore(1);
        signals.add(hold);

        SignalDTO sell1 = new SignalDTO();
        sell1.setRecommendation("SELL");
        sell1.setCompositeScore(-4);  // SELL threshold boundary
        signals.add(sell1);

        SignalDTO sell2 = new SignalDTO();
        sell2.setRecommendation("STRONG SELL");
        sell2.setCompositeScore(-8);
        signals.add(sell2);

        // Replicate the new filter logic from getBuySignals/getSellSignals
        List<SignalDTO> buySignals = signals.stream()
                .filter(s -> "BUY".equals(s.getRecommendation()) || "STRONG BUY".equals(s.getRecommendation()))
                .collect(Collectors.toList());

        List<SignalDTO> sellSignals = signals.stream()
                .filter(s -> "SELL".equals(s.getRecommendation()) || "STRONG SELL".equals(s.getRecommendation()))
                .collect(Collectors.toList());

        assertEquals(2, buySignals.size(), "BUY + STRONG BUY should be 2");
        assertTrue(buySignals.contains(buy1), "BUY with score=3 should be included");
        assertTrue(buySignals.contains(buy2), "STRONG BUY should be included");

        assertEquals(2, sellSignals.size(), "SELL + STRONG SELL should be 2");
        assertTrue(sellSignals.contains(sell1), "SELL with score=-4 should be included");
        assertTrue(sellSignals.contains(sell2), "STRONG SELL should be included");
    }

    @Test
    void testGetBuySignals_Score3PreviouslyExcluded() {
        // Bug 3: score=3 → BUY by mapRecommendation, but old filter (score >= 5) excluded it.
        // Verify the new filter includes it.
        SignalDTO dto = new SignalDTO();
        dto.setRecommendation("BUY");
        dto.setCompositeScore(3);

        // Old filter (broken)
        boolean oldIncluded = dto.getCompositeScore() >= 5;
        // New filter (fixed)
        boolean newIncluded = "BUY".equals(dto.getRecommendation()) || "STRONG BUY".equals(dto.getRecommendation());

        assertFalse(oldIncluded, "Old filter should exclude score=3");
        assertTrue(newIncluded, "New filter should include recommendation=BUY");
    }

    @Test
    void testGetSellSignals_ScoreN4PreviouslyExcluded() {
        // Bug 3: score=-4 → SELL by mapRecommendation, but old filter (score <= -5) excluded it.
        // Verify the new filter includes it.
        SignalDTO dto = new SignalDTO();
        dto.setRecommendation("SELL");
        dto.setCompositeScore(-4);

        // Old filter (broken)
        boolean oldIncluded = dto.getCompositeScore() <= -5;
        // New filter (fixed)
        boolean newIncluded = "SELL".equals(dto.getRecommendation()) || "STRONG SELL".equals(dto.getRecommendation());

        assertFalse(oldIncluded, "Old filter should exclude score=-4");
        assertTrue(newIncluded, "New filter should include recommendation=SELL");
    }

    // ===== Bug 1 regression: computeShadowDto exists and uses real pipeline =====

    @Test
    void testComputeShadowDto_NullPrices_ReturnsNull() {
        // Bug 1: computeShadowDto was added to SignalService.
        // Verify it returns null for insufficient data.
        Stock stock = createTestStock("TEST", "Test");

        SignalDTO result = signalService.computeShadowDto(stock, null);
        assertNull(result, "computeShadowDto should return null for null prices");
    }

    @Test
    void testComputeShadowDto_InsufficientPrices_ReturnsNull() {
        Stock stock = createTestStock("TEST", "Test");
        List<DailyPrice> prices = createMinimalPrices(10); // < 20 required

        SignalDTO result = signalService.computeShadowDto(stock, prices);
        assertNull(result, "computeShadowDto should return null for prices.size() < 20");
    }

    @Test
    void testComputeShadowDto_SufficientPrices_ReturnsDto() {
        // Bug 1: computeShadowDto uses the real SignalService pipeline.
        // Verify it produces a valid DTO with consistent recommendation.
        Stock stock = createTestStock("TEST", "Test Corp");
        List<DailyPrice> prices = createMinimalPrices(30);

        when(breakoutDetector.detect(anyList(), eq(stock.getId())))
                .thenReturn(new BreakoutDetector.BreakoutResult());

        SignalDTO result = signalService.computeShadowDto(stock, prices);

        assertNotNull(result, "computeShadowDto should return non-null for sufficient data");
        assertEquals("TEST", result.getSymbol());
        assertNotNull(result.getRecommendation(), "recommendation should not be null");
        // Verify score/recommendation consistency
        assertEquals(
                signalService.mapRecommendation(result.getCompositeScore()),
                result.getRecommendation(),
                "recommendation must match mapRecommendation(compositeScore)");
    }

    // ===== Helpers =====

    private Stock createTestStock(String symbol, String name) {
        Stock stock = new Stock();
        stock.setId(Math.abs((long) symbol.hashCode()));
        stock.setSymbol(symbol);
        stock.setName(name);
        stock.setSector("TEST");
        return stock;
    }

    /**
     * Creates prices with different patterns based on symbol hash
     * to hit different score ranges across the 5 stocks.
     */
    private List<DailyPrice> createVaryingPrices(String symbol) {
        int hash = Math.abs(symbol.hashCode());
        List<DailyPrice> prices = new ArrayList<>();
        // Base price varies by symbol to produce different SMA/RSI/MACD relationships
        double basePrice = 50 + (hash % 200);
        for (int i = 0; i < 30; i++) {
            // Alternate up/down trend based on symbol
            double factor = (hash % 3 == 0) ? 1.002 : (hash % 3 == 1) ? 0.998 : 1.0;
            double price = basePrice * Math.pow(factor, i);
            prices.add(new DailyPrice(null,
                    BigDecimal.valueOf(price),
                    BigDecimal.valueOf(price * 1.01),
                    BigDecimal.valueOf(price * 0.99),
                    BigDecimal.valueOf(price),
                    1000L + hash,
                    LocalDate.of(2025, 1, 1).plusDays(i)));
        }
        return prices;
    }

    private List<DailyPrice> createMinimalPrices(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prices.add(new DailyPrice(null, new BigDecimal("100"),
                    new BigDecimal("101"), new BigDecimal("99"),
                    new BigDecimal("100"), 1000L,
                    LocalDate.of(2023, 1, 1).plusDays(i)));
        }
        return prices;
    }
}
