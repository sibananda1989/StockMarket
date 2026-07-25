package org.example.strategy.base;

import lombok.RequiredArgsConstructor;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.TechnicalIndicator;
import org.example.strategy.model.StrategyResult;
import org.example.strategy.model.StrategySignal;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for all trading strategies in the multi-strategy signal system.
 * Each concrete strategy evaluates a specific technical condition and emits a
 * {@link StrategyResult} with a signal, confidence, and reason.
 *
 * <p>Subclasses must implement {@link #evaluate(Long, List, List)}.
 * Strategies are stateless and independently testable.</p>
 */
@RequiredArgsConstructor
public abstract class TradingStrategy {

    private final String name;
    private final int priority;

    /**
     * Evaluates this strategy against the provided indicators and price data.
     *
     * @param stockId     the stock identifier
     * @param indicators  latest technical indicator records for the stock
     * @param prices      recent daily price records (most recent first)
     * @return a {@link StrategyResult} with signal, confidence, and explanation
     */
    public abstract StrategyResult evaluate(Long stockId,
                                             List<TechnicalIndicator> indicators,
                                             List<DailyPrice> prices);

    /**
     * Returns a HOLD result with zero confidence when required data is missing.
     */
    protected StrategyResult insufficientData() {
        return StrategyResult.withoutContribution(StrategySignal.HOLD, 0.0, "Insufficient data", name, priority);
    }

    /**
     * Safely extracts the latest indicator value for a given type from the list.
     *
     * @param indicators the indicator list (may contain multiple dates per type)
     * @param type       the indicator type to look up
     * @return an Optional containing the BigDecimal value if found
     */
    protected Optional<Double> resolveIndicator(List<TechnicalIndicator> indicators, IndicatorType type) {
        if (indicators == null) {
            return Optional.empty();
        }
        return indicators.stream()
                .filter(ti -> ti.getIndicatorType() == type)
                .max(Comparator.comparing(TechnicalIndicator::getCalculationDate))
                .map(TechnicalIndicator::getValue)
                .map(BigDecimal::doubleValue);
    }

    /**
     * Safely extracts the second most recent indicator value for a given type.
     * Useful for crossover detection where both today's and yesterday's values
     * are needed. Returns empty if fewer than 2 values exist for the type.
     */
    protected Optional<Double> resolvePreviousIndicator(List<TechnicalIndicator> indicators, IndicatorType type) {
        if (indicators == null) {
            return Optional.empty();
        }
        return indicators.stream()
                .filter(ti -> ti.getIndicatorType() == type)
                .sorted(Comparator.comparing(TechnicalIndicator::getCalculationDate).reversed())
                .skip(1)
                .findFirst()
                .map(TechnicalIndicator::getValue)
                .map(BigDecimal::doubleValue);
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }
}
