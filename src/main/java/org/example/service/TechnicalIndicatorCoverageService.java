package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.IndicatorCoverageDto;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.repository.DailyPriceRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;

/**
 * Computes per-stock indicator coverage — which indicators are present
 * for the latest calculation date and which are missing because the
 * stock doesn't yet have enough price history.
 *
 * Used by the dashboard to render the "X indicators missing" badge
 * so the user can know exactly which long-look-back metrics aren't
 * yet available.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TechnicalIndicatorCoverageService {

    private final TechnicalIndicatorRepository indicatorRepository;
    private final DailyPriceRepository dailyPriceRepository;

    /**
     * Returns a coverage report for the given stock on the most recent
     * calculation date that has at least one indicator row. If no rows
     * exist at all, an empty report (expected counts, 0 available) is
     * returned.
     */
    public IndicatorCoverageDto getCoverage(Long stockId) {
        IndicatorCoverageDto dto = new IndicatorCoverageDto();
        dto.setStockId(stockId);

        IndicatorType[] allTypes = IndicatorType.values();
        dto.setExpected(allTypes.length);

        LocalDate latestDate = latestCalculationDate(stockId);
        if (latestDate == null) {
            dto.setAvailable(0);
            dto.setMissing(allTypes.length);
            dto.setMissingIndicators(Arrays.asList(allTypes));
            dto.setCalculationDate(LocalDate.now().toString());
            return dto;
        }

        List<TechnicalIndicator> rows =
                indicatorRepository.findByStockIdAndCalculationDate(stockId, latestDate);
        Set<IndicatorType> present = rows.stream()
                .map(TechnicalIndicator::getIndicatorType)
                .collect(Collectors.toCollection(HashSet::new));

        List<IndicatorType> missing = Arrays.stream(allTypes)
                .filter(t -> !present.contains(t))
                .collect(Collectors.toList());

        dto.setAvailable(present.size());
        dto.setMissing(missing.size());
        dto.setMissingIndicators(missing);

        // How many days of price history this stock currently has
        long priceDays = dailyPriceRepository.countByStockId(stockId);
        dto.setCalculationDate(latestDate.toString());

        if (log.isDebugEnabled()) {
            log.debug("Coverage for stock {} on {}: {}/{} available, {} day(s) of price history",
                    stockId, latestDate, present.size(), allTypes.length, priceDays);
        }
        return dto;
    }

    /**
     * Returns the most recent calculation_date that has at least one
     * indicator row for {@code stockId}, or null if the stock has zero rows.
     */
    private LocalDate latestCalculationDate(Long stockId) {
        List<LocalDate> dates = indicatorRepository.findLatestTwoCalculationDates(stockId, PageRequest.of(0, 2));
        return (dates == null || dates.isEmpty()) ? null : dates.get(0);
    }
}
