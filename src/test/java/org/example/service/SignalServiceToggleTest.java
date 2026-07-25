package org.example.service;

import org.example.dto.SignalDTO;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the "score parameter toggle" feature inside {@link SignalService}.
 *
 * The 3-arg {@code computeWeightedScore(dto, stock, prices)} delegates to the
 * 4-arg {@code computeWeightedScore(dto, stock, prices, disabled)} with the
 * disabled set obtained from {@link ScoreParameterService#getDisabledLegacyFactors()}
 * (via the null-safe {@code disabledFactors()} helper). These tests verify that
 * toggling legacy factors on/off changes the computed score as expected and that
 * the all-enabled path is byte-for-byte equivalent to the 3-arg path (backward
 * compatibility / golden-master parity).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignalServiceToggleTest {

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
    @Mock
    private ScoreParameterService scoreParameterService;

    @InjectMocks
    private SignalService signalService;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = createTestStock("TOGGLE", "Toggle Test Corp");
    }

    // ===== all-enabled parity (backward compatibility) =====

    @Test
    void allEnabled_parityWithThreeArg() {
        when(scoreParameterService.getDisabledLegacyFactors()).thenReturn(Collections.emptySet());

        int score3 = signalService.computeWeightedScore(buildDto(), stock, createMinimalPrices(30));
        int score4 = signalService.computeWeightedScore(buildDto(), stock, createMinimalPrices(30), Collections.emptySet());

        assertEquals(score3, score4,
                "3-arg (delegates to getDisabledLegacyFactors=empty) must equal 4-arg with empty disabled set");
    }

    // ===== disabling MOM_RSI14 removes the RSI contribution =====

    @Test
    void disablingRsi14LowersScore() {
        // RSI(14) = 25 (oversold, not in a bearish trend) contributes +2 to momentum.
        SignalDTO enabledDto = buildRsiDto(new BigDecimal("25"));
        SignalDTO disabledDto = buildRsiDto(new BigDecimal("25"));

        int enabledScore = signalService.computeWeightedScore(enabledDto, stock, createMinimalPrices(30), Collections.emptySet());
        int disabledScore = signalService.computeWeightedScore(disabledDto, stock, createMinimalPrices(30), Set.of("MOM_RSI14"));

        // The RSI score is still *computed* (and recorded) even when the factor is disabled,
        // but it is NOT added to the momentum total — so disabling lowers the score by exactly +2.
        assertEquals(2, enabledDto.getRsiScore(),
                "RSI(14)=25 in an uptrend should contribute a momentum score of +2");
        assertEquals(enabledDto.getRsiScore(), disabledDto.getRsiScore(),
                "RSI score is computed identically regardless of the toggle (only its inclusion changes)");
        assertTrue(enabledScore > disabledScore,
                "disabling MOM_RSI14 must lower the score (enabled=" + enabledScore + ", disabled=" + disabledScore + ")");
        assertEquals(2, enabledScore - disabledScore,
                "disabling MOM_RSI14 should remove exactly the +2 RSI contribution");
    }

    // ===== disabling MOD_ADX_MULTIPLIER neutralizes the ADX multiplier =====

    @Test
    void disablingAdxMultiplierNeutralizes() {
        // ADX = 10 (< 15) => no-trend multiplier 0.3 when enabled.
        SignalDTO enabledDto = buildAdxDto(new BigDecimal("10"));
        SignalDTO disabledDto = buildAdxDto(new BigDecimal("10"));

        int enabledScore = signalService.computeWeightedScore(enabledDto, stock, createMinimalPrices(30), Collections.emptySet());
        int disabledScore = signalService.computeWeightedScore(disabledDto, stock, createMinimalPrices(30), Set.of("MOD_ADX_MULTIPLIER"));

        assertEquals(1.0, disabledDto.getAdxMultiplier(), 0.0001,
                "disabling MOD_ADX_MULTIPLIER must neutralize the multiplier to 1.0");
        assertTrue(disabledDto.getAdxMultiplier() > enabledDto.getAdxMultiplier(),
                "disabled multiplier (1.0) must exceed the enabled no-trend multiplier (0.3)");
        assertTrue(disabledScore > enabledScore,
                "neutralizing the ADX dampener must increase the score (enabled=" + enabledScore + ", disabled=" + disabledScore + ")");
    }

    // ===== disabling POST_FII_DII zeroes the FII/DII score (full pipeline) =====

    @Test
    void disablingPostFiidiiZeroesFiidiiScore() {
        // FII/DII service returns a fixed adjustment of 5. ComputeShadowDto runs the full
        // computeBaseSignalDto pipeline (which applies the POST_FII_DII toggle to fiidiiScore).
        when(fiiDiiService.getScoreAdjustment()).thenReturn(5);
        when(breakoutDetector.detect(anyList(), eq(stock.getId())))
                .thenReturn(new BreakoutDetector.BreakoutResult());

        // Disabled path
        when(scoreParameterService.getDisabledLegacyFactors()).thenReturn(Set.of("POST_FII_DII"));
        SignalDTO disabledDto = signalService.computeShadowDto(stock, createMinimalPrices(30));
        assertNotNull(disabledDto);
        assertEquals(0, disabledDto.getFiidiiScore(),
                "POST_FII_DII disabled must zero the FII/DII score");

        // Enabled path (positive control)
        when(scoreParameterService.getDisabledLegacyFactors()).thenReturn(Collections.emptySet());
        SignalDTO enabledDto = signalService.computeShadowDto(stock, createMinimalPrices(30));
        assertNotNull(enabledDto);
        assertEquals(5, enabledDto.getFiidiiScore(),
                "POST_FII_DII enabled must include the FII/DII adjustment of 5");

        assertEquals(5, enabledDto.getCompositeScore() - disabledDto.getCompositeScore(),
                "the composite score must differ by exactly the FII/DII adjustment of 5");
    }

    // ===== Fixtures =====

    private SignalDTO buildDto() {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setRsi14(new BigDecimal("60"));
        dto.setSma20(new BigDecimal("105"));
        dto.setSma50(new BigDecimal("100"));
        dto.setMacd(new BigDecimal("2.5"));
        dto.setMacdSignal(new BigDecimal("1.0"));
        dto.setAdx(new BigDecimal("30"));
        dto.setPlusDi(new BigDecimal("25"));
        dto.setMinusDi(new BigDecimal("15"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setPriceVsSma20(new BigDecimal("-4.76"));
        dto.setWeeklyRsi(new BigDecimal("50"));
        dto.setMonthlyRsi(new BigDecimal("50"));
        return dto;
    }

    private SignalDTO buildRsiDto(BigDecimal rsi) {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setRsi14(rsi);
        dto.setSma20(new BigDecimal("105"));   // SMA20 > SMA50 => not bearish => oversold gives +2
        dto.setSma50(new BigDecimal("100"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setPriceVsSma20(new BigDecimal("-4.76"));
        return dto;
    }

    private SignalDTO buildAdxDto(BigDecimal adx) {
        SignalDTO dto = new SignalDTO();
        dto.setLatestPrice(new BigDecimal("100"));
        dto.setRsi14(new BigDecimal("25"));     // oversold => +2 momentum
        dto.setSma20(new BigDecimal("105"));
        dto.setSma50(new BigDecimal("100"));
        dto.setMacd(new BigDecimal("2.5"));
        dto.setMacdSignal(new BigDecimal("1.0"));
        dto.setAdx(adx);
        dto.setPlusDi(new BigDecimal("25"));
        dto.setMinusDi(new BigDecimal("15"));
        dto.setHigh52Week(new BigDecimal("120"));
        dto.setLow52Week(new BigDecimal("80"));
        dto.setPriceVsSma20(new BigDecimal("-4.76"));
        return dto;
    }

    private Stock createTestStock(String symbol, String name) {
        Stock stock = new Stock();
        stock.setId(Math.abs((long) symbol.hashCode()));
        stock.setSymbol(symbol);
        stock.setName(name);
        stock.setSector("TEST");
        return stock;
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
