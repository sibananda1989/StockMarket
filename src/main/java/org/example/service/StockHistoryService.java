package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.StockHistoryDTO;
import org.example.dto.StockSummaryDTO;
import org.example.entity.DailyPrice;
import org.example.entity.Stock;
import org.example.exception.StockHistoryNotFoundException;
import org.example.repository.DailyPriceRepository;
import org.example.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockHistoryService {

    private final YahooFinanceService yahooFinanceService;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;

    public StockSummaryDTO getSummary(String symbol, int days) {
        String yahooSymbol = resolveYahooSymbol(symbol);
        List<StockHistoryDTO> history = yahooFinanceService.fetchHistory(yahooSymbol, days);

        double currentPrice  = history.get(history.size() - 1).getClose();
        double firstClose    = history.get(0).getClose();
        double highestPrice  = history.stream().mapToDouble(StockHistoryDTO::getHigh).max().orElse(0);
        double lowestPrice   = history.stream().mapToDouble(StockHistoryDTO::getLow).min().orElse(0);
        double averageClose  = history.stream().mapToDouble(StockHistoryDTO::getClose).average().orElse(0);
        double percentChange = firstClose == 0 ? 0 : ((currentPrice - firstClose) / firstClose) * 100;

        return new StockSummaryDTO(
                yahooSymbol,
                round(currentPrice),
                round(highestPrice),
                round(lowestPrice),
                round(averageClose),
                round(percentChange),
                history.size(),
                history
        );
    }

    public List<StockHistoryDTO> getHistoryData(String symbol, int days) {
        String yahooSymbol = resolveYahooSymbol(symbol);
        return yahooFinanceService.fetchHistory(yahooSymbol, days);
    }

    public StockSummaryDTO getLocalSummary(Stock stock, int days) {
        LocalDate fromDate = LocalDate.now().minusDays(days);
        List<DailyPrice> prices = dailyPriceRepository
                .findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(stock.getId(), fromDate, LocalDate.now());

        if (prices.isEmpty()) {
            throw new StockHistoryNotFoundException("No local data found for symbol: " + stock.getSymbol());
        }

        double currentPrice  = prices.get(prices.size() - 1).getClosingPrice().doubleValue();
        double firstClose    = prices.get(0).getClosingPrice().doubleValue();
        double highestPrice  = prices.stream().mapToDouble(p -> p.getHighPrice().doubleValue()).max().orElse(0);
        double lowestPrice   = prices.stream().mapToDouble(p -> p.getLowPrice().doubleValue()).min().orElse(0);
        double averageClose  = prices.stream().mapToDouble(p -> p.getClosingPrice().doubleValue()).average().orElse(0);
        double percentChange = firstClose == 0 ? 0 : ((currentPrice - firstClose) / firstClose) * 100;

        String yahooSymbol = stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol();

        return new StockSummaryDTO(
                yahooSymbol,
                round(currentPrice),
                round(highestPrice),
                round(lowestPrice),
                round(averageClose),
                round(percentChange),
                prices.size(),
                List.of()
        );
    }

    private String resolveYahooSymbol(String symbol) {
        return stockRepository.findBySymbol(symbol.trim().toUpperCase())
                .map(s -> s.getYahooSymbol() != null ? s.getYahooSymbol().trim().toUpperCase() : null)
                .orElse(symbol.trim().toUpperCase());
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
