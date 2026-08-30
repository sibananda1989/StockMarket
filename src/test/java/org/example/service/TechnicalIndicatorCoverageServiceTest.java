package org.example.service;

import org.example.dto.IndicatorCoverageDto;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.repository.DailyPriceRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TechnicalIndicatorCoverageServiceTest {

    @Mock
    private TechnicalIndicatorRepository indicatorRepository;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private TechnicalIndicatorCoverageService coverageService;

    @Test
    void getCoverage_noRowsYet_returnsAllMissing() {
        // Given
        when(indicatorRepository.findLatestTwoCalculationDates(eq(42L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        // When
        IndicatorCoverageDto dto = coverageService.getCoverage(42L);

        // Then
        assertEquals(42L, dto.getStockId());
        assertEquals(IndicatorType.values().length, dto.getExpected());
        assertEquals(0, dto.getAvailable());
        assertEquals(IndicatorType.values().length, dto.getMissing());
        assertEquals(List.of(IndicatorType.values()), dto.getMissingIndicators());
    }

    @Test
    void getCoverage_partialCompletion_reportsMissingNames() {
        // Given - stock has only short-term indicators calculated today
        LocalDate today = LocalDate.now();
        Set<IndicatorType> present = EnumSet.of(
                IndicatorType.RSI, IndicatorType.OBV, IndicatorType.VWAP,
                IndicatorType.SMA_20, IndicatorType.EMA_20, IndicatorType.WILLIAMS_R,
                IndicatorType.MACD_LINE, IndicatorType.BOLLINGER_UPPER,
                IndicatorType.BOLLINGER_LOWER);
        List<TechnicalIndicator> rows = present.stream()
                .map(t -> makeIndicator(42L, t, today))
                .collect(Collectors.toList());

        when(indicatorRepository.findLatestTwoCalculationDates(eq(42L), any(Pageable.class)))
                .thenReturn(List.of(today));
        when(indicatorRepository.findByStockIdAndCalculationDate(42L, today))
                .thenReturn(rows);

        // When
        IndicatorCoverageDto dto = coverageService.getCoverage(42L);

        // Then
        assertEquals(IndicatorType.values().length, dto.getExpected());
        assertEquals(present.size(), dto.getAvailable());
        assertEquals(IndicatorType.values().length - present.size(), dto.getMissing());
        assertFalse(dto.getMissingIndicators().contains(IndicatorType.RSI), "RSI should not be missing");
        assertTrue(dto.getMissingIndicators().contains(IndicatorType.SMA_200), "SMA_200 should be missing");
        assertTrue(dto.getMissingIndicators().contains(IndicatorType.MACD_SIGNAL), "MACD_SIGNAL should be missing");
        assertTrue(dto.getMissingIndicators().contains(IndicatorType.SENKOU_SPAN_B), "SENKOU_SPAN_B should be missing");
        assertEquals(today.toString(), dto.getCalculationDate());
    }

    @Test
    void getCoverage_completeCoverage_reportsZeroMissing() {
        // Given
        LocalDate today = LocalDate.now();
        List<TechnicalIndicator> rows = java.util.Arrays.stream(IndicatorType.values())
                .map(t -> makeIndicator(42L, t, today))
                .collect(Collectors.toList());

        when(indicatorRepository.findLatestTwoCalculationDates(eq(42L), any(Pageable.class)))
                .thenReturn(List.of(today));
        when(indicatorRepository.findByStockIdAndCalculationDate(42L, today))
                .thenReturn(rows);

        // When
        IndicatorCoverageDto dto = coverageService.getCoverage(42L);

        // Then
        assertEquals(IndicatorType.values().length, dto.getExpected());
        assertEquals(IndicatorType.values().length, dto.getAvailable());
        assertEquals(0, dto.getMissing());
        assertTrue(dto.getMissingIndicators().isEmpty());
    }

    private TechnicalIndicator makeIndicator(Long stockId, IndicatorType type, LocalDate date) {
        TechnicalIndicator ti = new TechnicalIndicator();
        ti.setIndicatorType(type);
        ti.setCalculationDate(date);
        ti.setValue(BigDecimal.valueOf(50.0));
        return ti;
    }
}
