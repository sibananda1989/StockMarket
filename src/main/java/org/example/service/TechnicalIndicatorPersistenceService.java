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

    private final TechnicalIndicatorRepository indicatorRepository;    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DataIntegrityViolationException.class)
    public void saveOrUpdateIndicator(Stock stock, IndicatorType type, BigDecimal value, LocalDate date) {
        try {
            TechnicalIndicator indicator = indicatorRepository
                    .findByStockIdAndIndicatorTypeAndCalculationDate(stock.getId(), type, date)
                    .orElse(new TechnicalIndicator());

            if (indicator.getId() == null) {
                indicator.setStock(stock);
                indicator.setIndicatorType(type);
                indicator.setCalculationDate(date);
            }

            indicator.setValue(value);
            indicatorRepository.save(indicator);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another transaction inserted this record between our
            // find-check and save. Fall back to find-and-update.
            log.warn("Duplicate key for {} on {} for stock {}, retrying with find+update", type, date, stock.getSymbol());
            TechnicalIndicator existing = indicatorRepository
                    .findByStockIdAndIndicatorTypeAndCalculationDate(stock.getId(), type, date)
                    .orElseThrow(() -> new RuntimeException("Concurrent insert detected but record not found on retry for " + type + " " + stock.getSymbol(), e));
            existing.setValue(value);
            indicatorRepository.save(existing);
        } catch (Exception e) {
            log.error("Failed to save indicator {} for stock {}: {}", type, stock.getSymbol(), e.getMessage());
            throw e;
        }
    }
}
