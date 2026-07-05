package org.example.service.calculator;

import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Average Directional Index (ADX) Calculator
 *
 * Measures trend STRENGTH (not direction). Tells you IF a trend exists and how strong it is.
 *
 * Formula (multi-step Wilder's method):
 *   Step 1: True Range (TR) = max(High-Low, |High-PrevClose|, |Low-PrevClose|)
 *   Step 2: +DM = (High - PrevHigh) if > 0 AND > -DM, else 0
 *           -DM = (PrevLow - Low) if > 0 AND > +DM, else 0
 *   Step 3: Smoothed TR, +DM, -DM using Wilder's smoothing (period N)
 *           Smoothed = PrevSmoothed × (N-1)/N + Current × 1/N
 *   Step 4: +DI = (Smoothed +DM / Smoothed TR) × 100
 *           -DI = (Smoothed -DM / Smoothed TR) × 100
 *   Step 5: DX = |+DI - -DI| / (+DI + -DI) × 100
 *   Step 6: ADX = Wilder's smoothing of DX over N periods
 *
 * Parameters: period = 14 (default, used for all smoothing steps)
 * Range: 0-100 (always positive)
 * Minimum data: 2 × period + 1 days (e.g., 29 for period=14)
 *
 * Signals:
 *   ADX > 25 → Strong trend (trend-following strategies work)
 *   ADX < 20 → No trend / ranging (mean-reversion strategies work)
 *   ADX > 40 → Very strong trend (caution, may be overextended)
 */
public class AdxCalculator implements IndicatorCalculator {

    private final int period;

    public AdxCalculator() {
        this(14);
    }

    public AdxCalculator(int period) {
        this.period = period;
    }

    @Override
    public BigDecimal calculate(List<DailyPrice> prices) {
        int minRequired = 2 * period + 1;
        if (prices.size() < minRequired) {
            throw new IllegalArgumentException(
                    "Need at least " + minRequired + " days of price data to calculate ADX-" + period);
        }

        return computeAdxResult(prices).adx;
    }

    @Override
    public IndicatorType getSupportedType() {
        return IndicatorType.ADX;
    }

    /**
     * Full ADX computation returning all three values (ADX, +DI, -DI).
     * Used by all three ADX-related calculators to avoid redundant computation.
     */
    public static AdxResult computeAdxResult(List<DailyPrice> prices, int period) {
        int n = prices.size();

        // Step 1: True Range, +DM, -DM
        BigDecimal[] tr = new BigDecimal[n];
        BigDecimal[] plusDm = new BigDecimal[n];
        BigDecimal[] minusDm = new BigDecimal[n];

        tr[0] = prices.get(0).getHighPrice().subtract(prices.get(0).getLowPrice());
        plusDm[0] = BigDecimal.ZERO;
        minusDm[0] = BigDecimal.ZERO;

        for (int i = 1; i < n; i++) {
            DailyPrice today = prices.get(i);
            DailyPrice yesterday = prices.get(i - 1);

            BigDecimal high = today.getHighPrice();
            BigDecimal low = today.getLowPrice();
            BigDecimal prevHigh = yesterday.getHighPrice();
            BigDecimal prevLow = yesterday.getLowPrice();
            BigDecimal prevClose = yesterday.getClosingPrice();

            // TR = max(High-Low, |High-PrevClose|, |Low-PrevClose|)
            BigDecimal highLow = high.subtract(low);
            BigDecimal highPrevClose = high.subtract(prevClose).abs();
            BigDecimal lowPrevClose = low.subtract(prevClose).abs();
            tr[i] = highLow.max(highPrevClose).max(lowPrevClose);

            // +DM = (High - PrevHigh) if > 0 AND > -DM, else 0
            BigDecimal upMove = high.subtract(prevHigh);
            BigDecimal downMove = prevLow.subtract(low);

            if (upMove.compareTo(BigDecimal.ZERO) > 0 && upMove.compareTo(downMove) > 0) {
                plusDm[i] = upMove;
            } else {
                plusDm[i] = BigDecimal.ZERO;
            }

            if (downMove.compareTo(BigDecimal.ZERO) > 0 && downMove.compareTo(upMove) > 0) {
                minusDm[i] = downMove;
            } else {
                minusDm[i] = BigDecimal.ZERO;
            }
        }

        // Step 2: Wilder's smoothing for TR, +DM, -DM (initial SMA for first 'period' values)
        BigDecimal smoothedTr = BigDecimal.ZERO;
        BigDecimal smoothedPlusDm = BigDecimal.ZERO;
        BigDecimal smoothedMinusDm = BigDecimal.ZERO;

        for (int i = 0; i < period; i++) {
            smoothedTr = smoothedTr.add(tr[i]);
            smoothedPlusDm = smoothedPlusDm.add(plusDm[i]);
            smoothedMinusDm = smoothedMinusDm.add(minusDm[i]);
        }

        // Collect DX, +DI, -DI values starting from the (period)th index
        List<BigDecimal> dxValues = new java.util.ArrayList<>();
        java.util.List<BigDecimal> diPlusValues = new java.util.ArrayList<>();
        java.util.List<BigDecimal> diMinusValues = new java.util.ArrayList<>();

        for (int i = period; i < n; i++) {
            smoothedTr = smoothedTr.subtract(smoothedTr.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP))
                    .add(tr[i]);
            smoothedPlusDm = smoothedPlusDm.subtract(smoothedPlusDm.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP))
                    .add(plusDm[i]);
            smoothedMinusDm = smoothedMinusDm.subtract(smoothedMinusDm.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP))
                    .add(minusDm[i]);

            BigDecimal plusDi = BigDecimal.ZERO;
            BigDecimal minusDi = BigDecimal.ZERO;

            if (smoothedTr.compareTo(BigDecimal.ZERO) != 0) {
                plusDi = smoothedPlusDm.multiply(BigDecimal.valueOf(100))
                        .divide(smoothedTr, 4, RoundingMode.HALF_UP);
                minusDi = smoothedMinusDm.multiply(BigDecimal.valueOf(100))
                        .divide(smoothedTr, 4, RoundingMode.HALF_UP);
            }

            diPlusValues.add(plusDi);
            diMinusValues.add(minusDi);

            // DX = |+DI - -DI| / (+DI + -DI) × 100
            BigDecimal diSum = plusDi.add(minusDi);
            BigDecimal dx;
            if (diSum.compareTo(BigDecimal.ZERO) == 0) {
                dx = BigDecimal.ZERO;
            } else {
                dx = plusDi.subtract(minusDi).abs()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(diSum, 4, RoundingMode.HALF_UP);
            }
            dxValues.add(dx);
        }

        // Step 6: ADX = Wilder's smoothing of DX over 'period' periods
        BigDecimal adx;
        if (dxValues.size() < period) {
            // Not enough DX values, compute simple average
            BigDecimal sumDx = dxValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            adx = sumDx.divide(BigDecimal.valueOf(dxValues.size()), 2, RoundingMode.HALF_UP);
        } else {
            // Initial ADX = SMA of first 'period' DX values
            BigDecimal sumDx = BigDecimal.ZERO;
            for (int i = 0; i < period; i++) {
                sumDx = sumDx.add(dxValues.get(i));
            }
            adx = sumDx.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

            // Wilder's smoothing for remaining DX values
            for (int i = period; i < dxValues.size(); i++) {
                adx = adx.multiply(BigDecimal.valueOf(period - 1))
                        .add(dxValues.get(i))
                        .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
            }
            adx = adx.setScale(2, RoundingMode.HALF_UP);
        }

        // Latest +DI and -DI
        BigDecimal latestPlusDi = diPlusValues.isEmpty() ? BigDecimal.ZERO :
                diPlusValues.get(diPlusValues.size() - 1).setScale(2, RoundingMode.HALF_UP);
        BigDecimal latestMinusDi = diMinusValues.isEmpty() ? BigDecimal.ZERO :
                diMinusValues.get(diMinusValues.size() - 1).setScale(2, RoundingMode.HALF_UP);

        return new AdxResult(adx, latestPlusDi, latestMinusDi);
    }

    static AdxResult computeAdxResult(List<DailyPrice> prices) {
        return computeAdxResult(prices, 14);
    }

    /**
     * Holds the three ADX-related values computed together.
     */
    public static class AdxResult {
        public final BigDecimal adx;
        public final BigDecimal plusDi;
        public final BigDecimal minusDi;

        AdxResult(BigDecimal adx, BigDecimal plusDi, BigDecimal minusDi) {
            this.adx = adx;
            this.plusDi = plusDi;
            this.minusDi = minusDi;
        }
    }
}
