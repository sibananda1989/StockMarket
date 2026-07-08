package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.IndicatorType;
import org.example.entity.Stock;
import org.example.entity.TechnicalIndicator;
import org.example.repository.TechnicalIndicatorRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TechnicalIndicatorPersistenceService {

    private final TechnicalIndicatorRepository indicatorRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DataIntegrityViolationException.class)
    public void saveOrUpdateIndicator(Stock stock, IndicatorType type, BigDecimal value, LocalDate date) {
        try {
            TechnicalIndicator indicator = indicatorRepository
                    .findByStockIdAndIndicatorTypeAndCalculationDate(stock.getId(), type, date)
                    .orElse(null);

            // BUG FIX (2026-07-06): Staleness guard — if the indicator record already
            // exists with a non-null value, skip the overwrite. This prevents the 4:15 PM
            // price sync (which uses potentially unadjusted prices) from overwriting the
            // correct 9 AM computation (which uses Yahoo's historically adjusted prices).
            // Without this guard, all 27+ indicators were systematically stale on weekdays.
            // See anchor summary 2026-07-06-rsi-investigation.md for full analysis.
            if (indicator != null && indicator.getValue() != null) {
                log.trace("Skipping overwrite of {} for {} on {}: existing value={}, new value={}",
                        type, stock.getSymbol(), date, indicator.getValue(), value);
                return;
            }

            if (indicator == null) {
                indicator = new TechnicalIndicator();
                indicator.setStock(stock);
                indicator.setIndicatorType(type);
                indicator.setCalculationDate(date);
            }

            indicator.setValue(value);
            indicatorRepository.save(indicator);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another transaction inserted this record between our
            // find-check and save. Fall back to find-and-update with staleness guard.
            TechnicalIndicator existing = indicatorRepository
                    .findByStockIdAndIndicatorTypeAndCalculationDate(stock.getId(), type, date)
                    .orElseThrow(() -> new RuntimeException("Concurrent insert detected but record not found on retry for " + type + " " + stock.getSymbol(), e));
            if (existing.getValue() != null) {
                log.trace("Skipping concurrent-overwrite of {} for {} on {}: existing value={}, new value={}",
                        type, stock.getSymbol(), date, existing.getValue(), value);
                return;
            }
            existing.setValue(value);
            indicatorRepository.save(existing);
        } catch (Exception e) {
            log.error("Failed to save indicator {} for stock {}: {}", type, stock.getSymbol(), e.getMessage());
            throw e;
        }
    }
}
