package org.example.service;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public class TechnicalAnalysisUtils {

    public static BigDecimal calculateRsi14(List<DailyPrice> prices) {
        if (prices == null || prices.size() < 15) {
            return null;
        }

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
        for (int i = 0; i < 14; i++) {
            sumGain = sumGain.add(gains[i]);
            sumLoss = sumLoss.add(losses[i]);
        }
        BigDecimal avgGain = sumGain.divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = sumLoss.divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);

        for (int i = 14; i < gains.length; i++) {
            avgGain = avgGain.multiply(BigDecimal.valueOf(13))
                    .add(gains[i])
                    .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(13))
                    .add(losses[i])
                    .divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
        }

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(
                        BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
    }

    public static boolean hasBullishDivergence(List<DailyPrice> prices, List<BigDecimal> rsiValues) {
        if (prices.size() < 20 || rsiValues.size() < 20) return false;

        // Simple divergence: check the last two troughs
        // Price makes lower low, RSI makes higher low
 int n = prices.size();

 // Find last two troughs in price
 int trough1 = -1;
 int trough2 = -1;

 for (int i = n - 2; i >= 0; i--) {
    if (i == 0) break;
    if (prices.get(i).getClosingPrice().compareTo(prices.get(i-1).getClosingPrice()) < 0 &&
        prices.get(i).getClosingPrice().compareTo(prices.get(i+1).getClosingPrice()) < 0) {
                if (trough1 == -1) trough1 = i;
                else {
                    trough2 = i;
                    break;
                }
            }
        }

        if (trough1 == -1 || trough2 == -1) return false;
        if (trough1 >= rsiValues.size() || trough2 >= rsiValues.size()) return false;

        boolean priceLowerLow = prices.get(trough1).getClosingPrice().compareTo(prices.get(trough2).getClosingPrice()) < 0;
        boolean rsiHigherLow = rsiValues.get(trough1).compareTo(rsiValues.get(trough2)) > 0;

        return priceLowerLow && rsiHigherLow;
    }

    public static boolean hasBearishDivergence(List<DailyPrice> prices, List<BigDecimal> rsiValues) {
        if (prices.size() < 20 || rsiValues.size() < 20) return false;

        // Find last two peaks in price
        int peak1 = -1;
        int peak2 = -1;

 int n = prices.size();
 for (int i = n - 2; i >= 0; i--) {
    if (i == 0) break;
    if (prices.get(i).getClosingPrice().compareTo(prices.get(i-1).getClosingPrice()) > 0 &&
        prices.get(i).getClosingPrice().compareTo(prices.get(i+1).getClosingPrice()) > 0) {
                if (peak1 == -1) peak1 = i;
                else {
                    peak2 = i;
                    break;
                }
            }
        }

        if (peak1 == -1 || peak2 == -1) return false;
        if (peak1 >= rsiValues.size() || peak2 >= rsiValues.size()) return false;

        boolean priceHigherHigh = prices.get(peak1).getClosingPrice().compareTo(prices.get(peak2).getClosingPrice()) > 0;
        boolean rsiLowerHigh = rsiValues.get(peak1).compareTo(rsiValues.get(peak2)) < 0;

        return priceHigherHigh && rsiLowerHigh;
    }
}
