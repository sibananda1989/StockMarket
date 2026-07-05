package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.BacktestResultDTO;
import org.example.dto.SignalDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StockRepository;
import org.example.service.calculator.ATRCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        return runBacktest(stockId, true, BigDecimal.valueOf(0.02));
    }

    public BacktestResultDTO runBacktest(Long stockId, boolean useStopLoss, BigDecimal positionSizePct) {
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
                    stopPrice = TrailingStopService.updateTrailingStop(stopPrice, yesterday.getHighPrice(),
                            atrCalculator.calculate(historicalData));
                    if (TrailingStopService.isStopped(today.getLowPrice(), stopPrice)) {
                        BigDecimal sellValue = shares.multiply(currentPrice);
                        if (sellValue.compareTo(entryPrice.multiply(shares)) > 0) {
                            winningTrades++;
                        } else {
                            losingTrades++;
                        }
                        BigDecimal sellCosts = calculateTransactionCosts(sellValue, true);
                        capital = sellValue.subtract(sellCosts);
                        shares = BigDecimal.ZERO;
                        stoppedOutTrades++;
                        totalTrades++;
                        double currentPortfolioValue = capital.doubleValue();
                        if (currentPortfolioValue > peakValue) peakValue = currentPortfolioValue;
                        double drawdown = (peakValue - currentPortfolioValue) / peakValue;
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
                    totalTrades++;
                }
            }

            double currentPortfolioValue = shares.compareTo(BigDecimal.ZERO) > 0
                    ? shares.multiply(currentPrice).doubleValue() + capital.doubleValue()
                    : capital.doubleValue();

            if (currentPortfolioValue > peakValue) {
                peakValue = currentPortfolioValue;
            }

            double drawdown = (peakValue - currentPortfolioValue) / peakValue;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        BigDecimal finalValue = shares.compareTo(BigDecimal.ZERO) > 0
                ? shares.multiply(allPrices.get(allPrices.size() - 1).getClosingPrice()).add(capital)
                : capital;

        BigDecimal totalReturn = finalValue.subtract(INITIAL_CAPITAL)
                .divide(INITIAL_CAPITAL, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        int completedTrades = totalTrades / 2;
        double winRate = completedTrades > 0 ? ((double) winningTrades / completedTrades) * 100 : 0;

        BigDecimal avgReturnPerTrade = completedTrades > 0
                ? totalReturn.divide(BigDecimal.valueOf(completedTrades), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

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
                .build();
    }
}
