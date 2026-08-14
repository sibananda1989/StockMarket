package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.BacktestResultDTO;
import org.example.dto.SignalDTO;
import org.example.dto.TradeRecordDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StockRepository;
import org.example.service.calculator.ATRCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

 private final DailyPriceRepository dailyPriceRepository;
 private final SignalService signalService;
 private final CorporateEventService corporateEventService;
 private final StockRepository stockRepository;

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000.00");
    private static final int ATR_PERIOD = 14;

    // Indian market transaction costs
    private static final BigDecimal BROKERAGE_PCT = new BigDecimal("0.0003");       // 0.03%
    private static final BigDecimal STT_SELL_PCT = new BigDecimal("0.001");         // 0.1% on sell
    private static final BigDecimal GST_PCT = new BigDecimal("0.18");               // 18% on brokerage
    private static final BigDecimal STAMP_DUTY_BUY_PCT = new BigDecimal("0.00015"); // 0.015% on buy
    private static final BigDecimal SLIPPAGE_PCT = new BigDecimal("0.001");         // 0.1% per trade

    private BigDecimal calculateTransactionCosts(BigDecimal tradeValue, boolean isSell) {
        BigDecimal brokerage = tradeValue.multiply(BROKERAGE_PCT);
        BigDecimal gst = brokerage.multiply(GST_PCT);
        BigDecimal slippage = tradeValue.multiply(SLIPPAGE_PCT);
        BigDecimal costs = brokerage.add(gst).add(slippage);

        if (isSell) {
            costs = costs.add(tradeValue.multiply(STT_SELL_PCT));
        } else {
            costs = costs.add(tradeValue.multiply(STAMP_DUTY_BUY_PCT));
        }
        return costs;
    }

    public BacktestResultDTO runBacktest(Long stockId) {
        return runBacktest(stockId, true, BigDecimal.valueOf(0.02), 0, 2.0, 3.0);
    }

    public BacktestResultDTO runBacktest(Long stockId, boolean useStopLoss, BigDecimal positionSizePct) {
        return runBacktest(stockId, useStopLoss, positionSizePct, 0, 2.0, 3.0);
    }

    public BacktestResultDTO runBacktest(Long stockId, boolean useStopLoss, BigDecimal positionSizePct, int days) {
        return runBacktest(stockId, useStopLoss, positionSizePct, days, 2.0, 3.0);
    }

    public BacktestResultDTO runBacktest(Long stockId, boolean useStopLoss, BigDecimal positionSizePct, int days,
            double riskFreeRatePct, double trailingStopMultiplier) {
        return runBacktestCore(stockId, useStopLoss, positionSizePct, days, riskFreeRatePct, trailingStopMultiplier);
    }

    private BacktestResultDTO runBacktestCore(Long stockId, boolean useStopLoss, BigDecimal positionSizePct, int days,
            double riskFreeRatePct, double trailingStopMultiplier) {
Stock stock = stockRepository.findById(stockId).orElse(null);
if (stock == null) {
    log.warn("Stock not found for backtest: ID {}", stockId);
    return BacktestResultDTO.builder()
            .symbol("UNKNOWN")
            .finalPortfolioValue(INITIAL_CAPITAL)
            .build();
}

String symbol = stock.getSymbol();

        List<DailyPrice> allPrices = dailyPriceRepository.findAllByStockIdOrderByPriceDateAsc(stockId);
        if (allPrices.size() < 50) {
            log.warn("Insufficient data for backtesting stock ID: {}", stockId);
            return BacktestResultDTO.builder()
                    .symbol(symbol)
                    .finalPortfolioValue(INITIAL_CAPITAL)
                    .build();
        }

        if (days > 0 && allPrices.size() > days) {
            allPrices = allPrices.subList(allPrices.size() - days, allPrices.size());
        }

        BigDecimal capital = INITIAL_CAPITAL;
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal entryPrice = BigDecimal.ZERO;
        BigDecimal stopPrice = BigDecimal.ZERO;

        int totalTrades = 0;
        int winningTrades = 0;
        int losingTrades = 0;
        int stoppedOutTrades = 0;
        int signalExits = 0;
        int eventsSkipped = 0;

        double maxDrawdown = 0;
        double peakValue = INITIAL_CAPITAL.doubleValue();

        ATRCalculator atrCalculator = new ATRCalculator(ATR_PERIOD);

        // Advanced metrics tracking
        List<TradeRecordDTO> tradeRecords = new ArrayList<>();
        List<Double> dailyPortfolioValues = new ArrayList<>();
        LocalDate tradeEntryDate = null;

        for (int i = 20; i < allPrices.size(); i++) {
            List<DailyPrice> historicalData = allPrices.subList(0, i + 1);
            SignalDTO signal = signalService.computeSignalFromPrices(stock, historicalData);
            if (signal == null) continue;

            DailyPrice today = allPrices.get(i);
            BigDecimal currentPrice = today.getClosingPrice();
            String recommendation = signal.getRecommendation();

            boolean isHolding = shares.compareTo(BigDecimal.ZERO) > 0;

            if (!isHolding && corporateEventService.hasEventRisk(symbol, today.getPriceDate())) {
                eventsSkipped++;
                continue;
            }

            if (isHolding) {
                if (useStopLoss) {
                    // Use previous day's high to update trailing stop (avoid look-ahead bias)
                    DailyPrice yesterday = i > 0 ? allPrices.get(i - 1) : today;
                    BigDecimal atr = atrCalculator.calculate(historicalData);
                    BigDecimal trailDistance = atr.multiply(BigDecimal.valueOf(trailingStopMultiplier));
                    BigDecimal newStop = yesterday.getHighPrice().subtract(trailDistance);
                    stopPrice = newStop.compareTo(stopPrice) > 0 ? newStop : stopPrice;
                    if (TrailingStopService.isStopped(today.getLowPrice(), stopPrice)) {
                        BigDecimal sellValue = shares.multiply(currentPrice);
                        if (sellValue.compareTo(entryPrice.multiply(shares)) > 0) {
                            winningTrades++;
                        } else {
                            losingTrades++;
                        }
                        BigDecimal sellCosts = calculateTransactionCosts(sellValue, true);
                        BigDecimal pnl = sellValue.subtract(sellCosts).subtract(entryPrice.multiply(shares));
                        
                        tradeRecords.add(createTradeRecord(tradeEntryDate, today.getPriceDate(), "SELL", 
                            entryPrice, currentPrice, shares, pnl, "STOP_LOSS", true));
                        
                        capital = sellValue.subtract(sellCosts);
                        shares = BigDecimal.ZERO;
                        stoppedOutTrades++;
                        totalTrades++;
                        double pv = capital.doubleValue();
                        dailyPortfolioValues.add(pv);
                        if (pv > peakValue) peakValue = pv;
                        double drawdown = (peakValue - pv) / peakValue;
                        if (drawdown > maxDrawdown) maxDrawdown = drawdown;
                        continue;
                    }
                }

                if ("SELL".equals(recommendation) || "STRONG SELL".equals(recommendation)) {
                    BigDecimal sellValue = shares.multiply(currentPrice);
                    if (sellValue.compareTo(entryPrice.multiply(shares)) > 0) {
                        winningTrades++;
                    } else {
                        losingTrades++;
                    }
                    BigDecimal signalSellCosts = calculateTransactionCosts(sellValue, true);
                    BigDecimal pnl = sellValue.subtract(signalSellCosts).subtract(entryPrice.multiply(shares));
                    
                    tradeRecords.add(createTradeRecord(tradeEntryDate, today.getPriceDate(), "SELL",
                        entryPrice, currentPrice, shares, pnl, "SIGNAL", false));
                    
                    capital = sellValue.subtract(signalSellCosts);
                    shares = BigDecimal.ZERO;
                    signalExits++;
                    totalTrades++;
                }
            } else {
                if ("BUY".equals(recommendation) || "STRONG BUY".equals(recommendation)) {
                    List<DailyPrice> dataWithToday = allPrices.subList(0, i + 1);
                    BigDecimal atr = atrCalculator.calculate(dataWithToday);
                    BigDecimal rawStop = TrailingStopService.calcInitialStop(currentPrice, atr);

                    BigDecimal usedCapital;
                    if (useStopLoss) {
                        BigDecimal riskPct = positionSizePct;
                        PositionSizingService.PositionResult pos = PositionSizingService.suggestPosition(
                                capital, currentPrice, rawStop, riskPct);
                        shares = pos.shares();
                        usedCapital = pos.capitalUsed();
                        stopPrice = rawStop;
                    } else {
                        shares = capital.divide(currentPrice, 0, RoundingMode.DOWN);
                        usedCapital = shares.multiply(currentPrice);
                    }

                    capital = capital.subtract(usedCapital);
                    BigDecimal buyCosts = calculateTransactionCosts(usedCapital, false);
                    capital = capital.subtract(buyCosts);
                    entryPrice = currentPrice;
                    tradeEntryDate = today.getPriceDate();
                    totalTrades++;
                }
            }

            double currentPortfolioValue = shares.compareTo(BigDecimal.ZERO) > 0
                    ? shares.multiply(currentPrice).doubleValue() + capital.doubleValue()
                    : capital.doubleValue();
            dailyPortfolioValues.add(currentPortfolioValue);

            if (currentPortfolioValue > peakValue) {
                peakValue = currentPortfolioValue;
            }

            double drawdown = (peakValue - currentPortfolioValue) / peakValue;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        // Close open position at end
        if (shares.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal finalPrice = allPrices.get(allPrices.size() - 1).getClosingPrice();
            BigDecimal sellValue = shares.multiply(finalPrice);
            BigDecimal sellCosts = calculateTransactionCosts(sellValue, true);
            BigDecimal pnl = sellValue.subtract(sellCosts).subtract(entryPrice.multiply(shares));
            tradeRecords.add(createTradeRecord(tradeEntryDate, allPrices.get(allPrices.size() - 1).getPriceDate(),
                "SELL", entryPrice, finalPrice, shares, pnl, "END_OF_DATA", false));
            totalTrades++;
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
            } else {
                losingTrades++;
            }
            capital = sellValue.subtract(sellCosts);
            dailyPortfolioValues.add(capital.doubleValue());
            shares = BigDecimal.ZERO;
        }

        BigDecimal finalValue = capital;

        BigDecimal totalReturn = BigDecimal.ZERO;
        if (finalValue != null && INITIAL_CAPITAL != null) {
            totalReturn = finalValue.subtract(INITIAL_CAPITAL)
                    .divide(INITIAL_CAPITAL, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        int completedTrades = winningTrades + losingTrades;
        double winRate = completedTrades > 0 ? ((double) winningTrades / completedTrades) * 100 : 0;

        BigDecimal avgReturnPerTrade = completedTrades > 0
                ? totalReturn.divide(BigDecimal.valueOf(completedTrades), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Calculate advanced metrics
        Double sharpeRatio = calcSharpe(dailyPortfolioValues, riskFreeRatePct);
        Double calmarRatio = maxDrawdown > 0 ? totalReturn.doubleValue() / (maxDrawdown * 100) : null;
        Double sortinoRatio = calcSortino(dailyPortfolioValues, riskFreeRatePct);
        Double profitFactor = calcProfitFactor(tradeRecords);
        BigDecimal avgWinning = calcAvgWin(tradeRecords);
        BigDecimal avgLosing = calcAvgLoss(tradeRecords);
        int largestWinCt = countLargestTrade(tradeRecords, true);
        int largestLossCt = countLargestTrade(tradeRecords, false);
        int longestDrawdownDays = calcLongestDrawdownDays(dailyPortfolioValues, maxDrawdown);

        return BacktestResultDTO.builder()
                .symbol(symbol)
                .totalTrades(totalTrades)
                .winningTrades(winningTrades)
                .losingTrades(losingTrades)
                .stoppedOutTrades(stoppedOutTrades)
                .signalExits(signalExits)
                .eventsSkipped(eventsSkipped)
                .winRate(winRate)
                .totalReturn(totalReturn)
                .averageReturnPerTrade(avgReturnPerTrade)
                .maxDrawdown(BigDecimal.valueOf(maxDrawdown * 100))
                .finalPortfolioValue(finalValue)
                .sharpeRatio(sharpeRatio)
                .calmarRatio(calmarRatio)
                .sortinoRatio(sortinoRatio)
                .longestDrawdownDays(longestDrawdownDays)
                .profitFactor(profitFactor)
                .avgWinningTrade(avgWinning)
                .avgLosingTrade(avgLosing)
                .largestWinnerCount(largestWinCt)
                .largestLoserCount(largestLossCt)
                .tradeHistory(tradeRecords)
                .equityCurve(dailyPortfolioValues)
                .build();
    }

    private TradeRecordDTO createTradeRecord(LocalDate entryDate, LocalDate exitDate, String action,
            BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal quantity,
            BigDecimal pnl, String exitReason, boolean stopLossHit) {
        TradeRecordDTO t = new TradeRecordDTO();
        t.setEntryDate(entryDate);
        t.setExitDate(exitDate);
        t.setAction(action);
        t.setEntryPrice(entryPrice);
        t.setExitPrice(exitPrice);
        t.setQuantity(quantity);
        t.setPnl(pnl);
        t.setExitReason(exitReason);
        t.setStopLossHit(stopLossHit);
        return t;
    }

    private Double calcSharpe(List<Double> values, double riskFreeRatePct) {
        if (values.size() < 2) return 0.0;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double rfDaily = riskFreeRatePct / 100.0 / 252.0;
        double var = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double std = Math.sqrt(var);
        return std > 0 ? ((mean - rfDaily) * 252) / std : 0.0;
    }

    private Double calcSortino(List<Double> values, double riskFreeRatePct) {
        if (values.size() < 2) return 0.0;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double rfDaily = riskFreeRatePct / 100.0 / 252.0;
        double downVar = values.stream().mapToDouble(v -> {
            double excess = v - rfDaily;
            return excess < 0 ? excess * excess : 0;
        }).average().orElse(0);
        double downStd = Math.sqrt(downVar);
        return downStd > 0 ? ((mean - rfDaily) * 252) / downStd : 0.0;
    }

    private Double calcProfitFactor(List<TradeRecordDTO> trades) {
        if (trades == null || trades.isEmpty()) return 0.0;
        double grossProfit = trades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0)
                .mapToDouble(t -> t.getPnl().doubleValue()).sum();
        double grossLoss = trades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) < 0)
                .mapToDouble(t -> Math.abs(t.getPnl().doubleValue())).sum();
        return grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 999.0 : 0.0);
    }

    private BigDecimal calcAvgWin(List<TradeRecordDTO> trades) {
        return calcAvg(trades, t -> t.getPnl().compareTo(BigDecimal.ZERO) > 0);
    }

    private BigDecimal calcAvgLoss(List<TradeRecordDTO> trades) {
        return calcAvg(trades, t -> t.getPnl().compareTo(BigDecimal.ZERO) < 0);
    }

    private BigDecimal calcAvg(List<TradeRecordDTO> trades, java.util.function.Predicate<TradeRecordDTO> filter) {
        List<BigDecimal> values = trades.stream()
                .filter(filter)
                .map(TradeRecordDTO::getPnl)
                .toList();
        if (values.isEmpty()) return BigDecimal.ZERO;
        double avg = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        return BigDecimal.valueOf(avg);
    }

    private int countLargestTrade(List<TradeRecordDTO> trades, boolean findWinner) {
        if (trades == null || trades.isEmpty()) return 0;
        BigDecimal largest = null;
        for (TradeRecordDTO t : trades) {
            if (t.getPnl() == null) continue;
            if (findWinner) {
                if (largest == null || t.getPnl().compareTo(largest) > 0) largest = t.getPnl();
            } else {
                if (largest == null || t.getPnl().compareTo(largest) < 0) largest = t.getPnl();
            }
        }
        if (largest == null) return 0;
        // Use explicit loop instead of lambda to avoid effectively-final issue
        int count = 0;
        for (TradeRecordDTO t : trades) {
            if (t.getPnl() != null && t.getPnl().compareTo(largest) == 0) count++;
        }
        return count;
    }

    private int calcLongestDrawdownDays(List<Double> values, double maxDrawdown) {
        if (values.size() < 2 || maxDrawdown == 0) return 0;
        double peak = values.get(0);
        int maxSpan = 0;
        int currentStart = 0;
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) > peak) {
                peak = values.get(i);
                currentStart = i;
            } else {
                double dd = (peak - values.get(i)) / peak;
                if (dd >= maxDrawdown * 0.9) {
                    maxSpan = Math.max(maxSpan, i - currentStart);
                }
            }
        }
        return maxSpan;
    }
}
