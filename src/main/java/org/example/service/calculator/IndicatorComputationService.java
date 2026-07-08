package org.example.service.calculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.Stock;
import org.example.entity.TechnicalIndicator;
import org.example.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes all technical indicators on-the-fly from price data.
 * Used by the multi-strategy signal engine when DB indicators are
 * missing or insufficient for historical signal calculation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndicatorComputationService {

    private final StockRepository stockRepository;

    public List<TechnicalIndicator> computeIndicators(Long stockId, LocalDate date, List<DailyPrice> prices) {
        Stock stock = stockRepository.getReferenceById(stockId);
        List<TechnicalIndicator> result = new ArrayList<>();

        tryAdd(result, stock, IndicatorType.RSI, new RsiCalculator(12).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.SMA_20, new SmaCalculator(20).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.SMA_50, new SmaCalculator(50).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.EMA_20, new EmaCalculator(20).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.MACD_LINE, new MacdLineCalculator().calculate(prices), date);
        tryAdd(result, stock, IndicatorType.MACD_SIGNAL, new MacdSignalCalculator().calculate(prices), date);
        tryAdd(result, stock, IndicatorType.STOCH_K, new StochKCalculator(14).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.STOCH_D, new StochDCalculator(14, 3).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.WILLIAMS_R, new WilliamsRCalculator(14).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.CCI, new CCICalculator(14).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.STOCH_RSI, new StochRsiCalculator(14, 14).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.PLUS_DI, new PlusDiCalculator().calculate(prices), date);
        tryAdd(result, stock, IndicatorType.MINUS_DI, new MinusDiCalculator().calculate(prices), date);
        tryAdd(result, stock, IndicatorType.ULTIMATE_OSC, new UltimateOscillatorCalculator().calculate(prices), date);
        tryAdd(result, stock, IndicatorType.ROC_12, new RocCalculator(12).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.OBV, new ObvCalculator().calculate(prices), date);
        tryAdd(result, stock, IndicatorType.VWAP, new VwapCalculator().calculate(prices), date);
        tryAdd(result, stock, IndicatorType.BOLLINGER_UPPER, new BollingerUpperCalculator(20, 2.5).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.BOLLINGER_LOWER, new BollingerLowerCalculator(20, 2.5).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.ATR, new ATRCalculator(14).calculate(prices), date);
        tryAdd(result, stock, IndicatorType.ADX, new AdxCalculator(14).calculate(prices), date);

        if (prices.size() >= 200) {
            tryAdd(result, stock, IndicatorType.SMA_200, new SmaCalculator(200).calculate(prices), date);
        }

        log.debug("Computed {} indicators for stock {} on {}", result.size(), stockId, date);
        return result;
    }

    private void tryAdd(List<TechnicalIndicator> list, Stock stock, IndicatorType type, BigDecimal value, LocalDate date) {
        if (value != null) {
            list.add(new TechnicalIndicator(stock, type, value, date));
        }
    }
}
