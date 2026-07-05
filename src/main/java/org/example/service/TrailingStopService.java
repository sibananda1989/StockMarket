package org.example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TrailingStopService {

    public static final BigDecimal STOP_MULTIPLIER = BigDecimal.valueOf(2);
    public static final BigDecimal TRAIL_MULTIPLIER = BigDecimal.valueOf(3);

    public static BigDecimal calcInitialStop(BigDecimal entryPrice, BigDecimal atr) {
        BigDecimal stopDistance = atr.multiply(STOP_MULTIPLIER);
        return entryPrice.subtract(stopDistance);
    }

    public static BigDecimal updateTrailingStop(BigDecimal currentStop, BigDecimal currentHigh, BigDecimal atr) {
        BigDecimal trailDistance = atr.multiply(TRAIL_MULTIPLIER);
        BigDecimal newStop = currentHigh.subtract(trailDistance);
        return newStop.compareTo(currentStop) > 0 ? newStop : currentStop;
    }

    public static boolean isStopped(BigDecimal lowPrice, BigDecimal stopPrice) {
        return lowPrice.compareTo(stopPrice) <= 0;
    }
}
