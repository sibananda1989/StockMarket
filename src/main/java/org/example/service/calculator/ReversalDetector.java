package org.example.service.calculator;

import org.example.entity.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class ReversalDetector {

    public record Result(int score, int flagBitmask, String label) {
        public static final int FLAG_RSI_RISING = 1;
        public static final int FLAG_MACD_IMPROVING = 2;
        public static final int FLAG_BULLISH_DIVERGENCE = 4;
        public static final int FLAG_OBV_RISING = 8;
        public static final int FLAG_HIGHER_LOW = 16;
        public static final int FLAG_RECLAIMING_SMA20 = 32;

        public String flagSummary() {
            List<String> flags = new ArrayList<>();
            if ((flagBitmask & FLAG_RSI_RISING) != 0) flags.add("RSIrising");
            if ((flagBitmask & FLAG_MACD_IMPROVING) != 0) flags.add("MACDimpr");
            if ((flagBitmask & FLAG_BULLISH_DIVERGENCE) != 0) flags.add("BullDiv");
            if ((flagBitmask & FLAG_OBV_RISING) != 0) flags.add("OBVrising");
            if ((flagBitmask & FLAG_HIGHER_LOW) != 0) flags.add("HigherLow");
            if ((flagBitmask & FLAG_RECLAIMING_SMA20) != 0) flags.add("SMA20cross");
            return String.join(",", flags);
        }
    }

    public Result detect(List<DailyPrice> prices, List<BigDecimal> rsiValues, BigDecimal sma20) {
        if (prices == null || prices.size() < 15) {
            return new Result(0, 0, null);
        }

        int flags = 0;
        int totalFlags = 0;

        // Flag 1: RSI rising 3+ consecutive periods (last 5 bars)
        if (rsiValues != null && rsiValues.size() >= 5) {
            int risingCount = 0;
            for (int i = rsiValues.size() - 3; i < rsiValues.size(); i++) {
                if (i > 0 && rsiValues.get(i) != null && rsiValues.get(i - 1) != null
                        && rsiValues.get(i).compareTo(rsiValues.get(i - 1)) > 0) {
                    risingCount++;
                }
            }
            if (risingCount >= 3) {
                flags |= Result.FLAG_RSI_RISING;
                totalFlags++;
            }
        }

        // Flag 2: MACD histogram improving 3 consecutive periods
        if (prices.size() >= 20) {
            int macdImproving = 0;
            for (int i = prices.size() - 5; i < prices.size(); i++) {
                if (i >= 2) {
                    DailyPrice p0 = prices.get(i - 2);
                    DailyPrice p1 = prices.get(i - 1);
                    DailyPrice p2 = prices.get(i);
                    BigDecimal ema12_0 = computeEma(prices, 12, i - 1);
                    BigDecimal ema26_0 = computeEma(prices, 26, i - 1);
                    BigDecimal macd0 = ema12_0 != null && ema26_0 != null ? ema12_0.subtract(ema26_0) : null;
                    BigDecimal ema12_1 = computeEma(prices, 12, i);
                    BigDecimal ema26_1 = computeEma(prices, 26, i);
                    BigDecimal macd1 = ema12_1 != null && ema26_1 != null ? ema12_1.subtract(ema26_1) : null;
                    if (macd0 != null && macd1 != null && macd1.compareTo(macd0) > 0) {
                        macdImproving++;
                    }
                }
            }
            if (macdImproving >= 3) {
                flags |= Result.FLAG_MACD_IMPROVING;
                totalFlags++;
            }
        }

        // Flag 3: Bullish divergence (using simple price-RSI comparison)
        if (prices.size() >= 20 && rsiValues != null && rsiValues.size() >= 20) {
            int trough1 = -1, trough2 = -1;
            int n = prices.size();
            for (int i = n - 2; i >= 0; i--) {
                if (i == 0) break;
                if (prices.get(i).getClosingPrice().compareTo(prices.get(i - 1).getClosingPrice()) < 0 &&
                    prices.get(i).getClosingPrice().compareTo(prices.get(i + 1).getClosingPrice()) < 0) {
                    if (trough1 == -1) trough1 = i;
                    else { trough2 = i; break; }
                }
            }
            if (trough1 != -1 && trough2 != -1 && trough1 < rsiValues.size() && trough2 < rsiValues.size()
                    && rsiValues.get(trough1) != null && rsiValues.get(trough2) != null) {
                boolean priceLowerLow = prices.get(trough1).getClosingPrice()
                        .compareTo(prices.get(trough2).getClosingPrice()) < 0;
                boolean rsiHigherLow = rsiValues.get(trough1).compareTo(rsiValues.get(trough2)) > 0;
                if (priceLowerLow && rsiHigherLow) {
                    flags |= Result.FLAG_BULLISH_DIVERGENCE;
                    totalFlags++;
                }
            }
        }

        // Flag 4: OBV rising with price (5-day)
        if (prices.size() >= 6) {
            long obv5dAgo = 0;
            long obvNow = 0;
            for (int i = Math.max(0, prices.size() - 6); i < prices.size(); i++) {
                if (i > 0) {
                    long vol = prices.get(i).getVolume() != null ? prices.get(i).getVolume() : 0L;
                    if (prices.get(i).getClosingPrice().compareTo(prices.get(i - 1).getClosingPrice()) > 0) {
                        if (i <= prices.size() - 3) obv5dAgo += vol;
                        obvNow += vol;
                    } else if (prices.get(i).getClosingPrice().compareTo(prices.get(i - 1).getClosingPrice()) < 0) {
                        if (i <= prices.size() - 3) obv5dAgo -= vol;
                        obvNow -= vol;
                    }
                }
            }
            BigDecimal price5dAgo = prices.get(Math.max(0, prices.size() - 6)).getClosingPrice();
            BigDecimal priceNow = prices.get(prices.size() - 1).getClosingPrice();
            if (obvNow > obv5dAgo && priceNow.compareTo(price5dAgo) > 0) {
                flags |= Result.FLAG_OBV_RISING;
                totalFlags++;
            }
        }

        // Flag 5: Higher low formed (last trough > previous trough in last 10 bars)
        if (prices.size() >= 10) {
            BigDecimal low1 = null, low2 = null;
            int firstLowIdx = -1;
            for (int i = prices.size() - 2; i >= prices.size() - 10 && i >= 1; i--) {
                if (prices.get(i).getClosingPrice().compareTo(prices.get(i - 1).getClosingPrice()) < 0 &&
                    prices.get(i).getClosingPrice().compareTo(prices.get(i + 1).getClosingPrice()) < 0) {
                    if (low1 == null) {
                        low1 = prices.get(i).getClosingPrice();
                        firstLowIdx = i;
                    } else if (i < firstLowIdx) {
                        low2 = prices.get(i).getClosingPrice();
                        break;
                    }
                }
            }
            if (low1 != null && low2 != null && low1.compareTo(low2) > 0) {
                flags |= Result.FLAG_HIGHER_LOW;
                totalFlags++;
            }
        }

        // Flag 6: Price reclaiming SMA20 (crossed above within last 3 bars)
        if (sma20 != null && prices.size() >= 4) {
            for (int i = prices.size() - 3; i < prices.size(); i++) {
                BigDecimal prevClose = prices.get(i - 1).getClosingPrice();
                BigDecimal currClose = prices.get(i).getClosingPrice();
                if (prevClose.compareTo(sma20) <= 0 && currClose.compareTo(sma20) > 0) {
                    flags |= Result.FLAG_RECLAIMING_SMA20;
                    totalFlags++;
                    break;
                }
            }
        }

        int score = 0;
        if (totalFlags >= 4) score = 3;
        else if (totalFlags >= 3) score = 2;
        else if (totalFlags >= 2) score = 1;

        String label = score > 0 ? totalFlags + "/6 flags" : null;
        return new Result(score, flags, label);
    }

    private BigDecimal computeEma(List<DailyPrice> prices, int period, int upToIndex) {
        if (upToIndex < period) return null;
        BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal ema = BigDecimal.ZERO;
        for (int i = 0; i <= upToIndex; i++) {
            BigDecimal price = prices.get(i).getClosingPrice();
            if (i < period) {
                ema = ema.add(price);
                if (i == period - 1) ema = ema.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
            } else {
                ema = price.multiply(k).add(ema.multiply(BigDecimal.ONE.subtract(k)));
            }
        }
        return ema;
    }
}
