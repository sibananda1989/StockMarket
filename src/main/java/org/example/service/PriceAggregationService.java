package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.DailyPrice;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceAggregationService {

    public List<DailyPrice> aggregateWeekly(List<DailyPrice> dailyPrices) {
        if (dailyPrices == null || dailyPrices.isEmpty()) return Collections.emptyList();

        // Sort daily prices ascending by date
        List<DailyPrice> sorted = dailyPrices.stream()
                .sorted(Comparator.comparing(DailyPrice::getPriceDate))
                .collect(Collectors.toList());

        Map<String, List<DailyPrice>> grouped = sorted.stream()
                .collect(Collectors.groupingBy(p -> {
                    LocalDate date = p.getPriceDate();
                    return date.getYear() + "-" + date.get(WeekFields.of(java.util.Locale.US).weekOfWeekBasedYear());
                }, LinkedHashMap::new, Collectors.toList()));

        List<DailyPrice> weeklyPrices = new ArrayList<>();
        for (List<DailyPrice> group : grouped.values()) {
            weeklyPrices.add(createAggregatedPrice(group));
        }
        return weeklyPrices;
    }

    public List<DailyPrice> aggregateMonthly(List<DailyPrice> dailyPrices) {
        if (dailyPrices == null || dailyPrices.isEmpty()) return Collections.emptyList();

        List<DailyPrice> sorted = dailyPrices.stream()
                .sorted(Comparator.comparing(DailyPrice::getPriceDate))
                .collect(Collectors.toList());

        Map<String, List<DailyPrice>> grouped = sorted.stream()
                .collect(Collectors.groupingBy(p -> {
                    LocalDate date = p.getPriceDate();
                    return date.getYear() + "-" + date.getMonthValue();
                }, LinkedHashMap::new, Collectors.toList()));

        List<DailyPrice> monthlyPrices = new ArrayList<>();
        for (List<DailyPrice> group : grouped.values()) {
            monthlyPrices.add(createAggregatedPrice(group));
        }
        return monthlyPrices;
    }

    private DailyPrice createAggregatedPrice(List<DailyPrice> group) {
        DailyPrice first = group.get(0);
        DailyPrice last = group.get(group.size() - 1);

        BigDecimal high = group.stream()
                .map(DailyPrice::getHighPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal low = group.stream()
                .map(DailyPrice::getLowPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        long volume = group.stream()
                .mapToLong(p -> p.getVolume() != null ? p.getVolume() : 0L)
                .sum();

        // We use a dummy stock as these are in-memory aggregations
        return new DailyPrice(
                first.getStock(),
                last.getClosingPrice(),
                first.getOpeningPrice(),
                high,
                low,
                volume,
                last.getPriceDate()
        );
    }
}
