package org.example.service.institutional;

import org.example.dto.InstitutionalScoreDTO;
import org.example.dto.SignalDTO;
import org.example.entity.*;
import org.example.repository.*;
import org.example.service.SignalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionalScoreServiceTest {

    @Mock private InstitutionalHoldingRepository holdingRepository;
    @Mock private BulkDealRepository bulkDealRepository;
    @Mock private BlockDealRepository blockDealRepository;
    @Mock private StockRepository stockRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private SignalService signalService;

    private InstitutionalScoreService scoreService;

    private Stock testStock;
    private InstitutionalHolding testHolding;
    private InstitutionalScoreDTO.InstitutionalScoreDTOBuilder builder;

    @BeforeEach
    void setUp() {
        scoreService = new InstitutionalScoreService(
                holdingRepository, bulkDealRepository, blockDealRepository,
                stockRepository, dailyPriceRepository, signalService);

        testStock = new Stock();
        testStock.setId(1L);
        testStock.setSymbol("RELIANCE");
        testStock.setName("Reliance Industries Ltd");
        testStock.setSector("Oil & Gas");
        testStock.setLastTradedPrice(new BigDecimal("2850.00"));

        testHolding = new InstitutionalHolding();
        testHolding.setId(1L);
        testHolding.setStock(testStock);
        testHolding.setQuarterEndDate(LocalDate.of(2026, 3, 31));
        testHolding.setFiiHoldingPct(new BigDecimal("18.31"));
        testHolding.setFiiChangeQoq(new BigDecimal("1.25"));
        testHolding.setDiiHoldingPct(new BigDecimal("1.32"));
        testHolding.setDiiChangeQoq(new BigDecimal("0.45"));
        testHolding.setMutualFundHoldingPct(new BigDecimal("9.52"));
        testHolding.setMutualFundChangeQoq(new BigDecimal("0.78"));
        testHolding.setPromoterHoldingPct(new BigDecimal("50.01"));
        testHolding.setPublicHoldingPct(new BigDecimal("49.99"));
        testHolding.setDataSource("NSE_XBRL");

        builder = InstitutionalScoreDTO.builder();
    }

    // ─── Fixture helpers ────────────────────────────────────────────────

    private void mockLatestHolding(InstitutionalHolding h) {
        when(holdingRepository.findLatestHolding(1L))
                .thenReturn(Optional.ofNullable(h));
    }

    // ═══════════════════════════════════════════════════════════════════
    // FII HOLDING SCORE (0-20)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeFiiHoldingScore_changeAbove2_Returns20() {
        testHolding.setFiiChangeQoq(new BigDecimal("2.50"));
        mockLatestHolding(testHolding);
        assertEquals(20, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_changeExactly2_Returns20() {
        testHolding.setFiiChangeQoq(new BigDecimal("2.00"));
        mockLatestHolding(testHolding);
        assertEquals(20, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_change1to2_Returns15() {
        testHolding.setFiiChangeQoq(new BigDecimal("1.50"));
        mockLatestHolding(testHolding);
        assertEquals(15, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_change0_5to1_Returns10() {
        testHolding.setFiiChangeQoq(new BigDecimal("0.75"));
        mockLatestHolding(testHolding);
        assertEquals(10, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_change0_1to0_5_Returns5() {
        testHolding.setFiiChangeQoq(new BigDecimal("0.30"));
        mockLatestHolding(testHolding);
        assertEquals(5, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_changeBelow0_1_Returns0() {
        testHolding.setFiiChangeQoq(new BigDecimal("0.05"));
        mockLatestHolding(testHolding);
        assertEquals(0, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_negativeChange_Returns0() {
        testHolding.setFiiChangeQoq(new BigDecimal("-0.50"));
        mockLatestHolding(testHolding);
        assertEquals(0, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_nullFiiChange_TriggersFallback() {
        testHolding.setFiiHoldingPct(null);
        testHolding.setFiiChangeQoq(null);
        mockLatestHolding(testHolding);
        // No second quarter for promoter shift → 0
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(1L))
                .thenReturn(List.of(testHolding));
        assertEquals(0, scoreService.computeFiiHoldingScore(1L, builder));
    }

    // ─── FII Fallback: Promoter-to-Public Shift ──────────────────────────

    @Test
    void computeFiiHoldingScore_fallbackPromoterDown2_Returns5() {
        mockLatestHolding(null); // No holding → triggers fallback
        InstitutionalHolding q1 = new InstitutionalHolding();
        q1.setPromoterHoldingPct(new BigDecimal("50.01"));
        q1.setFiiHoldingPct(null);
        InstitutionalHolding q0 = new InstitutionalHolding();
        q0.setPromoterHoldingPct(new BigDecimal("52.50"));
        q0.setFiiHoldingPct(null);
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(1L))
                .thenReturn(List.of(q1, q0));
        assertEquals(5, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_fallbackPromoterDown1_Returns3() {
        mockLatestHolding(null);
        InstitutionalHolding q1 = new InstitutionalHolding();
        q1.setPromoterHoldingPct(new BigDecimal("51.00"));
        q1.setFiiHoldingPct(null);
        InstitutionalHolding q0 = new InstitutionalHolding();
        q0.setPromoterHoldingPct(new BigDecimal("52.00"));
        q0.setFiiHoldingPct(null);
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(1L))
                .thenReturn(List.of(q1, q0));
        assertEquals(3, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_fallbackPromoterDown0_5_Returns2() {
        mockLatestHolding(null);
        InstitutionalHolding q1 = new InstitutionalHolding();
        q1.setPromoterHoldingPct(new BigDecimal("51.50"));
        q1.setFiiHoldingPct(null);
        InstitutionalHolding q0 = new InstitutionalHolding();
        q0.setPromoterHoldingPct(new BigDecimal("52.00"));
        q0.setFiiHoldingPct(null);
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(1L))
                .thenReturn(List.of(q1, q0));
        assertEquals(2, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_fallbackSingleQuarter_Returns0() {
        mockLatestHolding(null);
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(1L))
                .thenReturn(List.of(testHolding)); // Only 1 quarter
        assertEquals(0, scoreService.computeFiiHoldingScore(1L, builder));
    }

    @Test
    void computeFiiHoldingScore_fallbackPromoterIncreased_Returns0() {
        mockLatestHolding(null);
        InstitutionalHolding q1 = new InstitutionalHolding();
        q1.setPromoterHoldingPct(new BigDecimal("53.00"));
        q1.setFiiHoldingPct(null);
        InstitutionalHolding q0 = new InstitutionalHolding();
        q0.setPromoterHoldingPct(new BigDecimal("52.00"));
        q0.setFiiHoldingPct(null);
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(1L))
                .thenReturn(List.of(q1, q0));
        assertEquals(0, scoreService.computeFiiHoldingScore(1L, builder));
    }

    // ═══════════════════════════════════════════════════════════════════
    // DII HOLDING SCORE (0-15)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeDiiHoldingScore_changeAbove2_Returns15() {
        testHolding.setDiiChangeQoq(new BigDecimal("3.00"));
        mockLatestHolding(testHolding);
        assertEquals(15, scoreService.computeDiiHoldingScore(1L, builder));
    }

    @Test
    void computeDiiHoldingScore_changeExactly2_Returns15() {
        testHolding.setDiiChangeQoq(new BigDecimal("2.00"));
        mockLatestHolding(testHolding);
        assertEquals(15, scoreService.computeDiiHoldingScore(1L, builder));
    }

    @Test
    void computeDiiHoldingScore_change1to2_Returns10() {
        testHolding.setDiiChangeQoq(new BigDecimal("1.50"));
        mockLatestHolding(testHolding);
        assertEquals(10, scoreService.computeDiiHoldingScore(1L, builder));
    }

    @Test
    void computeDiiHoldingScore_change0_5to1_Returns7() {
        testHolding.setDiiChangeQoq(new BigDecimal("0.75"));
        mockLatestHolding(testHolding);
        assertEquals(7, scoreService.computeDiiHoldingScore(1L, builder));
    }

    @Test
    void computeDiiHoldingScore_change0_1to0_5_Returns3() {
        testHolding.setDiiChangeQoq(new BigDecimal("0.30"));
        mockLatestHolding(testHolding);
        assertEquals(3, scoreService.computeDiiHoldingScore(1L, builder));
    }

    @Test
    void computeDiiHoldingScore_changeBelow0_1_Returns0() {
        testHolding.setDiiChangeQoq(new BigDecimal("0.05"));
        mockLatestHolding(testHolding);
        assertEquals(0, scoreService.computeDiiHoldingScore(1L, builder));
    }

    @Test
    void computeDiiHoldingScore_nullChange_Returns0() {
        testHolding.setDiiHoldingPct(null);
        testHolding.setDiiChangeQoq(null);
        mockLatestHolding(testHolding);
        assertEquals(0, scoreService.computeDiiHoldingScore(1L, builder));
    }

    @Test
    void computeDiiHoldingScore_noHolding_Returns0() {
        when(holdingRepository.findLatestHolding(1L)).thenReturn(Optional.empty());
        assertEquals(0, scoreService.computeDiiHoldingScore(1L, builder));
    }

    // ═══════════════════════════════════════════════════════════════════
    // MF HOLDING SCORE (0-15)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeMutualFundHoldingScore_changeAbove2_Returns15() {
        testHolding.setMutualFundChangeQoq(new BigDecimal("2.50"));
        mockLatestHolding(testHolding);
        assertEquals(15, scoreService.computeMutualFundHoldingScore(1L, builder));
    }

    @Test
    void computeMutualFundHoldingScore_change0_1_Returns3() {
        testHolding.setMutualFundChangeQoq(new BigDecimal("0.15"));
        mockLatestHolding(testHolding);
        assertEquals(3, scoreService.computeMutualFundHoldingScore(1L, builder));
    }

    @Test
    void computeMutualFundHoldingScore_noHolding_Returns0() {
        when(holdingRepository.findLatestHolding(1L)).thenReturn(Optional.empty());
        assertEquals(0, scoreService.computeMutualFundHoldingScore(1L, builder));
    }

    @Test
    void computeMutualFundHoldingScore_nullPct_Returns0() {
        testHolding.setMutualFundHoldingPct(null);
        testHolding.setMutualFundChangeQoq(null);
        mockLatestHolding(testHolding);
        assertEquals(0, scoreService.computeMutualFundHoldingScore(1L, builder));
    }

    // ═══════════════════════════════════════════════════════════════════
    // BULK DEAL SCORE (0-15)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeBulkDealScore_buyIn7Days_Returns15() {
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(3);
        assertEquals(15, scoreService.computeBulkDealScore(1L, builder));
    }

    @Test
    void computeBulkDealScore_buyIn30Days_Returns10() {
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(0)   // 7-day → 0
                .thenReturn(2);  // 30-day → 2
        assertEquals(10, scoreService.computeBulkDealScore(1L, builder));
    }

    @Test
    void computeBulkDealScore_buyIn90Days_Returns5() {
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(0)   // 7-day → 0
                .thenReturn(0)   // 30-day → 0
                .thenReturn(1);  // 90-day → 1
        assertEquals(5, scoreService.computeBulkDealScore(1L, builder));
    }

    @Test
    void computeBulkDealScore_noBuys_Returns0() {
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(0)   // 7-day → 0
                .thenReturn(0)   // 30-day → 0
                .thenReturn(0);  // 90-day → 0
        assertEquals(0, scoreService.computeBulkDealScore(1L, builder));
        verify(bulkDealRepository, times(3)).countInstitutionalBuysSince(anyLong(), any());
    }

    // ═══════════════════════════════════════════════════════════════════
    // BLOCK DEAL SCORE (0-10)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeBlockDealScore_buyIn7Days_Returns10() {
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(1);
        assertEquals(10, scoreService.computeBlockDealScore(1L, builder));
    }

    @Test
    void computeBlockDealScore_buyIn30Days_Returns7() {
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(0)  // 7-day
                .thenReturn(1); // 30-day
        assertEquals(7, scoreService.computeBlockDealScore(1L, builder));
    }

    @Test
    void computeBlockDealScore_buyIn90Days_Returns3() {
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(0)  // 7-day
                .thenReturn(0)  // 30-day
                .thenReturn(1); // 90-day
        assertEquals(3, scoreService.computeBlockDealScore(1L, builder));
    }

    @Test
    void computeBlockDealScore_noBuys_Returns0() {
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any(LocalDate.class)))
                .thenReturn(0, 0, 0);
        assertEquals(0, scoreService.computeBlockDealScore(1L, builder));
    }

    // ═══════════════════════════════════════════════════════════════════
    // DELIVERY SCORE (0-10)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeDeliveryScore_bulkQtyHigh_Returns10() {
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                eq(1L), any(), any()))
                .thenReturn(List.of(createBulkDeal("BUY", true, 1000L)));
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 500L));
        assertEquals(10, scoreService.computeDeliveryScore(1L, builder));
    }

    @Test
    void computeDeliveryScore_bulkQtyMedium_Returns7() {
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                eq(1L), any(), any()))
                .thenReturn(List.of(createBulkDeal("BUY", true, 250L)));
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 500L));
        assertEquals(7, scoreService.computeDeliveryScore(1L, builder));
    }

    @Test
    void computeDeliveryScore_bulkQtyLow_Returns3() {
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                eq(1L), any(), any()))
                .thenReturn(List.of(createBulkDeal("BUY", true, 120L)));
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 500L));
        assertEquals(3, scoreService.computeDeliveryScore(1L, builder));
    }

    @Test
    void computeDeliveryScore_bulkQtyMinimal_Returns0() {
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                eq(1L), any(), any()))
                .thenReturn(List.of(createBulkDeal("BUY", true, 50L)));
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 500L));
        assertEquals(0, scoreService.computeDeliveryScore(1L, builder));
    }

    @Test
    void computeDeliveryScore_fewerThan20Prices_Returns0() {
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(15, 500L));
        assertEquals(0, scoreService.computeDeliveryScore(1L, builder));
    }

    @Test
    void computeDeliveryScore_zeroAvgVolume_Returns0() {
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                eq(1L), any(), any()))
                .thenReturn(new ArrayList<>());
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 0L));
        assertEquals(0, scoreService.computeDeliveryScore(1L, builder));
    }

    // ═══════════════════════════════════════════════════════════════════
    // VOLUME SCORE (0-10)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeVolumeScore_ratioAbove2_Returns10() {
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 100L, 300L)); // latest 300 vs avg ~100
        assertEquals(10, scoreService.computeVolumeScore(1L, builder));
    }

    @Test
    void computeVolumeScore_ratio1_5_Returns7() {
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 100L, 170L));
        assertEquals(7, scoreService.computeVolumeScore(1L, builder));
    }

    @Test
    void computeVolumeScore_ratio1_2_Returns3() {
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 100L, 130L));
        assertEquals(3, scoreService.computeVolumeScore(1L, builder));
    }

    @Test
    void computeVolumeScore_ratioBelow1_2_Returns0() {
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 100L, 110L));
        assertEquals(0, scoreService.computeVolumeScore(1L, builder));
    }

    @Test
    void computeVolumeScore_fewerThan20Prices_Returns0() {
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(15, 100L, 200L));
        assertEquals(0, scoreService.computeVolumeScore(1L, builder));
    }

    @Test
    void computeVolumeScore_nullLatestVolume_Returns0() {
        List<DailyPrice> prices = createPriceRecords(30, 100L, 200L);
        prices.get(prices.size() - 1).setVolume(null);
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(prices);
        assertEquals(0, scoreService.computeVolumeScore(1L, builder));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRICE ACTION SCORE (0-5)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computePriceActionScore_strongBuy_Returns5() {
        var signal = createSignal("STRONG BUY", 45.0, false);
        when(signalService.computeSignal(1L)).thenReturn(signal);
        assertEquals(5, scoreService.computePriceActionScore(1L, builder));
    }

    @Test
    void computePriceActionScore_buy_Returns4() {
        var signal = createSignal("BUY", 55.0, false);
        when(signalService.computeSignal(1L)).thenReturn(signal);
        assertEquals(4, scoreService.computePriceActionScore(1L, builder));
    }

    @Test
    void computePriceActionScore_rsiBelow30_Returns3() {
        var signal = createSignal("HOLD", 25.0, false);
        when(signalService.computeSignal(1L)).thenReturn(signal);
        assertEquals(3, scoreService.computePriceActionScore(1L, builder));
    }

    @Test
    void computePriceActionScore_bullishDivergence_Returns3() {
        var signal = createSignal("HOLD", 55.0, true);
        when(signalService.computeSignal(1L)).thenReturn(signal);
        assertEquals(3, scoreService.computePriceActionScore(1L, builder));
    }

    @Test
    void computePriceActionScore_hold_Returns1() {
        var signal = createSignal("HOLD", 50.0, false);
        when(signalService.computeSignal(1L)).thenReturn(signal);
        assertEquals(1, scoreService.computePriceActionScore(1L, builder));
    }

    @Test
    void computePriceActionScore_nullSignal_Returns0() {
        when(signalService.computeSignal(1L)).thenReturn(null);
        assertEquals(0, scoreService.computePriceActionScore(1L, builder));
    }

    @Test
    void computePriceActionScore_exception_Returns0() {
        when(signalService.computeSignal(1L)).thenThrow(new RuntimeException("API error"));
        assertEquals(0, scoreService.computePriceActionScore(1L, builder));
    }

    // ═══════════════════════════════════════════════════════════════════
    // ORCHESTRATION: computeScore(Stock)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeScore_buildsCompleteDTO() {
        mockLatestHolding(testHolding);
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any()))
                .thenReturn(0, 0, 0);
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any()))
                .thenReturn(0, 0, 0);
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 100L, 150L));
        var signal = createSignal("BUY", 55.0, false);
        when(signalService.computeSignal(1L)).thenReturn(signal);

        InstitutionalScoreDTO result = scoreService.computeScore(testStock);

        assertNotNull(result);
        assertEquals(1L, result.getStockId());
        assertEquals("RELIANCE", result.getSymbol());
        assertEquals("Reliance Industries Ltd", result.getName());
        assertEquals("Oil & Gas", result.getSector());
        assertEquals(0, result.getLatestPrice().compareTo(new BigDecimal("2850.00")));
        assertEquals(new BigDecimal("18.31"), result.getFiiHoldingPct());
        assertEquals(new BigDecimal("1.32"), result.getDiiHoldingPct());
        assertEquals(new BigDecimal("9.52"), result.getMutualFundHoldingPct());
        assertNotNull(result.getTotalScore());
        assertNotNull(result.getInstitutionalGrade());
        assertNotNull(result.getSignalRecommendation());
    }

    @Test
    void computeScore_totalScoreClampedAbove100() {
        mockLatestHolding(testHolding);
        // FII=20 + DII=15 + MF=15 + Bulk=15 + Block=10 + Delivery=10 + Volume=3 + Price=5 = 93 normally
        // But with high bulk scores:
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any()))
                .thenReturn(5); // returns 15 for 7-day
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any()))
                .thenReturn(3); // returns 10 for 7-day
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                eq(1L), any(), any())).thenReturn(List.of(createBulkDeal("BUY", true, 10000L)));
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(30, 100L, 300L));
        var signal = createSignal("STRONG BUY", 45.0, false);
        when(signalService.computeSignal(1L)).thenReturn(signal);

        InstitutionalScoreDTO result = scoreService.computeScore(testStock);

        assertNotNull(result);
        assertTrue(result.getTotalScore() <= 100, "Total score should be clamped at 100, got " + result.getTotalScore());
    }

    @Test
    void computeScore_totalScoreClampedBelow0() {
        // All components return 0, grade should be SELL
        mockLatestHolding(null); // FII fallback needs 2 quarters for any score
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(1L))
                .thenReturn(List.of(testHolding)); // Only 1 quarter → fallback returns 0
        when(bulkDealRepository.countInstitutionalBuysSince(eq(1L), any()))
                .thenReturn(0, 0, 0);
        when(blockDealRepository.countInstitutionalBuysSince(eq(1L), any()))
                .thenReturn(0, 0, 0);
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(1L))
                .thenReturn(createPriceRecords(15, 100L, 100L));
        when(signalService.computeSignal(1L)).thenReturn(createSignal("HOLD", 50.0, false));

        InstitutionalScoreDTO result = scoreService.computeScore(testStock);
        assertNotNull(result);
        assertTrue(result.getTotalScore() >= 0, "Total score should be clamped at minimum 0");
        assertEquals("SELL", result.getInstitutionalGrade());
    }

    // ═══════════════════════════════════════════════════════════════════
    // computeAllScores
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeAllScores_returnsSortedDescending() {
        Stock stock2 = new Stock(); stock2.setId(2L); stock2.setSymbol("STOCK2"); stock2.setName("Stock 2");
        Stock stock3 = new Stock(); stock3.setId(3L); stock3.setSymbol("STOCK3"); stock3.setName("Stock 3");
        when(stockRepository.findAll()).thenReturn(List.of(testStock, stock2, stock3));

        // Mock all dependencies so computeScore doesn't NPE
        mockLatestHolding(testHolding);
        when(bulkDealRepository.countInstitutionalBuysSince(anyLong(), any()))
                .thenReturn(0, 0, 0);
        when(blockDealRepository.countInstitutionalBuysSince(anyLong(), any()))
                .thenReturn(0, 0, 0);
        when(bulkDealRepository.findByStockIdAndDealDateBetweenOrderByDealDateDesc(
                anyLong(), any(), any())).thenReturn(new ArrayList<>());
        when(dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(anyLong()))
                .thenReturn(createPriceRecords(15, 100L, 100L));
        when(signalService.computeSignal(anyLong())).thenReturn(createSignal("HOLD", 50.0, false));

        // stock2 has no holding data → null holding → FII fallback needs history too
        when(holdingRepository.findLatestHolding(2L)).thenReturn(Optional.empty());
        when(holdingRepository.findLatestHolding(3L)).thenReturn(Optional.empty());
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(2L)).thenReturn(new ArrayList<>());
        when(holdingRepository.findByStockIdOrderByQuarterEndDateDesc(3L)).thenReturn(new ArrayList<>());

        List<InstitutionalScoreDTO> results = scoreService.computeAllScores();

        assertNotNull(results);
        assertFalse(results.isEmpty());
        // Should be sorted descending
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getTotalScore() >= results.get(i).getTotalScore(),
                    "Results should be sorted descending by score");
        }
    }

    @Test
    void computeAllScores_emptyStockList_returnsEmpty() {
        when(stockRepository.findAll()).thenReturn(new ArrayList<>());
        assertTrue(scoreService.computeAllScores().isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════
    // computeScore(Long) → returns null for missing stock
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeScoreByLongId_nullStock_returnsNull() {
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());
        assertNull(scoreService.computeScore(999L));
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRADE ASSIGNMENT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeGrade_80plus_returnsSTRONG_BUY() {
        assertEquals("STRONG_BUY", scoreService.computeGrade(80));
        assertEquals("STRONG_BUY", scoreService.computeGrade(95));
        assertEquals("STRONG_BUY", scoreService.computeGrade(100));
    }

    @Test
    void computeGrade_60to79_returnsBUY() {
        assertEquals("BUY", scoreService.computeGrade(60));
        assertEquals("BUY", scoreService.computeGrade(75));
        assertEquals("BUY", scoreService.computeGrade(79));
    }

    @Test
    void computeGrade_40to59_returnsNEUTRAL_POSITIVE() {
        assertEquals("NEUTRAL_POSITIVE", scoreService.computeGrade(40));
        assertEquals("NEUTRAL_POSITIVE", scoreService.computeGrade(50));
    }

    @Test
    void computeGrade_20to39_returnsNEUTRAL_NEGATIVE() {
        assertEquals("NEUTRAL_NEGATIVE", scoreService.computeGrade(20));
        assertEquals("NEUTRAL_NEGATIVE", scoreService.computeGrade(30));
    }

    @Test
    void computeGrade_below20_returnsSELL() {
        assertEquals("SELL", scoreService.computeGrade(0));
        assertEquals("SELL", scoreService.computeGrade(10));
        assertEquals("SELL", scoreService.computeGrade(19));
    }

    // ═══════════════════════════════════════════════════════════════════
    // getSignalRecommendation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void getSignalRecommendation_returnsRec() {
        when(signalService.computeSignal(1L)).thenReturn(createSignal("BUY", 50.0, false));
        assertEquals("BUY", scoreService.getSignalRecommendation(1L));
    }

    @Test
    void getSignalRecommendation_nullSignal_returnsNA() {
        when(signalService.computeSignal(1L)).thenReturn(null);
        assertEquals("N/A", scoreService.getSignalRecommendation(1L));
    }

    @Test
    void getSignalRecommendation_exception_returnsNA() {
        when(signalService.computeSignal(1L)).thenThrow(new RuntimeException("fail"));
        assertEquals("N/A", scoreService.getSignalRecommendation(1L));
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER STATE VERIFICATION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void computeFiiHoldingScore_setsBuilderFields() {
        testHolding.setFiiChangeQoq(new BigDecimal("1.25"));
        mockLatestHolding(testHolding);
        scoreService.computeFiiHoldingScore(1L, builder);
        var dto = builder.build();
        assertEquals(0, new BigDecimal("18.31").compareTo(dto.getFiiHoldingPct()));
        assertEquals(0, new BigDecimal("1.25").compareTo(dto.getFiiChangeQoq()));
        assertEquals(LocalDate.of(2026, 3, 31), dto.getLatestQuarterEnd());
    }

    // ─── Test Helpers ────────────────────────────────────────────────────

    private BulkDeal createBulkDeal(String buySell, boolean institutional, long qty) {
        BulkDeal d = new BulkDeal();
        d.setStock(testStock);
        d.setDealDate(LocalDate.now().minusDays(1));
        d.setClientName("Test Client");
        d.setBuySell(buySell);
        d.setQuantity(qty);
        d.setTradePrice(new BigDecimal("100.00"));
        d.setClientCategory(institutional ? "FII" : "UNKNOWN");
        d.setIsInstitutional(institutional);
        return d;
    }

    private List<DailyPrice> createPriceRecords(int count, long avgVolume) {
        return createPriceRecords(count, avgVolume, avgVolume);
    }

    private List<DailyPrice> createPriceRecords(int count, long avgVolume, long latestVolume) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DailyPrice p = new DailyPrice();
            p.setId((long) i);
            p.setStock(testStock);
            p.setPriceDate(LocalDate.now().minusDays(count - i));
            p.setClosingPrice(new BigDecimal("100.00"));
            p.setVolume(i == count - 1 ? latestVolume : avgVolume);
            prices.add(p);
        }
        return prices;
    }

    private SignalDTO createSignal(String rec, double rsi, boolean bullishDivergence) {
        SignalDTO signal = new SignalDTO();
        signal.setRecommendation(rec);
        signal.setRsi14(BigDecimal.valueOf(rsi));
        signal.setBullishDivergence(bullishDivergence);
        return signal;
    }
}
