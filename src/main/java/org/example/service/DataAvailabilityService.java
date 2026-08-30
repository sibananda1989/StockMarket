package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.DataAvailabilitySummaryDTO;
import org.example.repository.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataAvailabilityService {
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final SignalRecordRepository signalRecordRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final SupportResistanceLevelRepository supportResistanceLevelRepository;
    private final FiiDiiDataRepository fiiDiiDataRepository;
    private final FundamentalDataRepository fundamentalDataRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;

    public DataAvailabilitySummaryDTO getSummary() {
        long fiidii = fiiDiiDataRepository.count();
        return DataAvailabilitySummaryDTO.builder()
                .totalStocks(stockRepository.count())
                .stocksWithPriceHistory(dailyPriceRepository.countDistinctStocks())
                .stocksWithSignals(signalRecordRepository.countDistinctStocks())
                .stocksWithIndicatorsAndSr(technicalIndicatorRepository.countDistinctStocksWithIndicatorOrSr())
                .fiidiiRecords(fiidii)
                .fiidiiAvailable(fiidii > 0)
                .stocksWithFundamentals(fundamentalDataRepository.countDistinctStocks())
                .portfolioCount(portfolioRepository.count())
                .holdingsCount(portfolioHoldingRepository.count())
                .stocksWithHoldings(portfolioHoldingRepository.countDistinctStocks())
                .build();
    }
}
