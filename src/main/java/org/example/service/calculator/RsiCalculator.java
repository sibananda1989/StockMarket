package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public class RsiCalculator implements IndicatorCalculator {

    private final int period;

    public RsiCalculator(int period) {
        this.period = period;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        return calculateAll(prices).values().stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public java.util.Map<LocalDate, BigDecimal> calculateAll(List<DailyPrice> prices) {
        if (prices.size() <= period) {
            throw new IllegalArgumentException("Need at least " + (period + 1) + " days of price data to calculate RSI-" + period);
        }

        // BUG FIX (2026-07-06): Use LinkedHashMap to preserve insertion order.
        // HashMap has no guaranteed iteration order, so calculate() was returning
        // an ARBITRARY RSI value instead of the latest one. This caused ALL RSI
        // values stored in the database to be random/wrong for every stock.
        // See anchor summary for full analysis.
        java.util.Map<LocalDate, BigDecimal> results = new java.util.LinkedHashMap<>();
        BigDecimal[] gains = new BigDecimal[prices.size() - 1];
        BigDecimal[] losses = new BigDecimal[prices.size() - 1];
        for (int i = 0; i < gains.length; i++) {
            BigDecimal change = prices.get(i + 1).getClosingPrice()
                    .subtract(prices.get(i).getClosingPrice());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains[i] = change;
                losses[i] = BigDecimal.ZERO;
            } else {
                gains[i] = BigDecimal.ZERO;
                losses[i] = change.abs();
            }
        }

        BigDecimal sumGain = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sumGain = sumGain.add(gains[i]);
            sumLoss = sumLoss.add(losses[i]);
        }
        BigDecimal avgGain = sumGain.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = sumLoss.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // The first RSI value is calculated at the end of the first 'period'
        BigDecimal firstRsi = computeRsiValue(avgGain, avgLoss);
        results.put(prices.get(period).getPriceDate(), firstRsi);

        for (int i = period; i < gains.length; i++) {
            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1))
                    .add(gains[i])
                    .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1))
                    .add(losses[i])
                    .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
            
            results.put(prices.get(i + 1).getPriceDate(), computeRsiValue(avgGain, avgLoss));
        }

        return results;
    }

    private BigDecimal computeRsiValue(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(
                        BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
    }
}