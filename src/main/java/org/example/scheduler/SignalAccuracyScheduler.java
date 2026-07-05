package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.SignalRecord;
import org.example.repository.DailyPriceRepository;
import org.example.repository.SignalRecordRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignalAccuracyScheduler {

    private final SignalRecordRepository signalRecordRepository;
    private final DailyPriceRepository dailyPriceRepository;

    private static final List<Integer> FORWARD_DAYS = List.of(5, 10, 20);

    /**
     * Runs daily at 3 AM to mark past signal records with actual forward returns
     * and accuracy flags. For each unmarked record that is old enough (at least
     * 5/10/20 trading days ago), looks forward in daily_prices to compute the
     * return and determine whether the signal was correct.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void markForwardAccuracy() {
        log.info("Starting signal accuracy forward-marking job");

        // Only mark records that are at least 5 trading days old
        LocalDate cutoff5d = LocalDate.now().minusDays(10); // buffer for non-trading days
        List<SignalRecord> unmarked = signalRecordRepository
                .findUnmarkedRecordsOlderThan(cutoff5d);

        if (unmarked.isEmpty()) {
            log.info("No unmarked signal records to process");
            return;
        }

        int marked = 0;
        for (SignalRecord record : unmarked) {
            try {
                boolean updated = markRecord(record);
                if (updated) {
                    signalRecordRepository.save(record);
                    marked++;
                }
            } catch (Exception e) {
                log.debug("Could not mark record {}: {}", record.getId(), e.getMessage());
            }
        }

        log.info("Signal accuracy forward-marking complete. {} records marked.", marked);
    }

    /**
     * Marks a single SignalRecord with forward return and accuracy.
     * Package-private for direct unit testing.
     */
    boolean markRecord(SignalRecord record) {
        if (record.getPriceAtSignal() == null
                || record.getPriceAtSignal().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        List<DailyPrice> prices = dailyPriceRepository
                .findAllByStockIdOrderByPriceDateAsc(record.getStockId());

        if (prices.isEmpty()) return false;

        // Find the index of the record's date in the price list
        int signalIndex = -1;
        for (int i = 0; i < prices.size(); i++) {
            if (prices.get(i).getPriceDate() != null
                    && prices.get(i).getPriceDate().equals(record.getRecordedAt())) {
                signalIndex = i;
                break;
            }
        }
        if (signalIndex < 0) return false;

        boolean anyMarked = false;
        String rec = record.getRecommendation();
        boolean isBuy = "BUY".equals(rec) || "STRONG BUY".equals(rec);
        boolean isSell = "SELL".equals(rec) || "STRONG SELL".equals(rec);
        boolean isHold = "HOLD".equals(rec);

        BigDecimal priceAtSignal = record.getPriceAtSignal();

        for (int daysForward : FORWARD_DAYS) {
            int forwardIndex = signalIndex + daysForward;
            if (forwardIndex >= prices.size()) continue;

            DailyPrice fp = prices.get(forwardIndex);
            if (fp.getClosingPrice() == null
                    || fp.getClosingPrice().compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal fwdReturn = fp.getClosingPrice().subtract(priceAtSignal)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(priceAtSignal, 4, RoundingMode.HALF_UP);

            boolean accurate = false;
            if (!isHold) {
                accurate = (isBuy && fwdReturn.compareTo(BigDecimal.ZERO) > 0)
                        || (isSell && fwdReturn.compareTo(BigDecimal.ZERO) < 0);
            }

            if (daysForward == 5) {
                record.setForwardReturn5d(fwdReturn);
                record.setWasAccurate5d(isHold ? null : accurate);
            } else if (daysForward == 10) {
                record.setForwardReturn10d(fwdReturn);
                record.setWasAccurate10d(isHold ? null : accurate);
            } else if (daysForward == 20) {
                record.setForwardReturn20d(fwdReturn);
                record.setWasAccurate20d(isHold ? null : accurate);
            }
            anyMarked = true;
        }

        return anyMarked;
    }
}
