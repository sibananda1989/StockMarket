package org.example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PositionSizingService {

    public static PositionResult suggestPosition(BigDecimal capital, BigDecimal entryPrice, BigDecimal stopPrice, BigDecimal riskPct) {
        BigDecimal riskAmount = capital.multiply(riskPct);
        BigDecimal riskPerShare = entryPrice.subtract(stopPrice);
        if (riskPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return new PositionResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal shares = riskAmount.divide(riskPerShare, 0, RoundingMode.DOWN);
        BigDecimal capitalUsed = shares.multiply(entryPrice);
        if (capitalUsed.compareTo(capital) > 0) {
            shares = capital.divide(entryPrice, 0, RoundingMode.DOWN);
            capitalUsed = shares.multiply(entryPrice);
        }
        return new PositionResult(shares, capitalUsed);
    }

    public record PositionResult(BigDecimal shares, BigDecimal capitalUsed) {}
}
