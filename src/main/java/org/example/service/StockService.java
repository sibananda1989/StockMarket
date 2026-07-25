package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.CsvImportRequest;
import org.example.dto.RsiDTO;
import org.example.dto.StockDTO;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.TechnicalIndicator;
import org.example.entity.Stock;
import org.example.exception.StockNotFoundException;
import org.example.repository.BlockDealRepository;
import org.example.repository.BulkDealRepository;
import org.example.repository.DailyPriceRepository;
import org.example.repository.InstitutionalHoldingRepository;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioRepository;
import org.example.repository.PortfolioSnapshotRepository;
import org.example.repository.SignalRecordRepository;
import org.example.repository.StockRepository;
import org.example.repository.SupportResistanceLevelRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StockService {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final PortfolioRepository portfolioRepository;
    private final SupportResistanceLevelRepository supportResistanceLevelRepository;
    private final BlockDealRepository blockDealRepository;
    private final BulkDealRepository bulkDealRepository;
    private final InstitutionalHoldingRepository institutionalHoldingRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final SignalRecordRepository signalRecordRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;

    public Stock addStock(Stock stock) {
        if (stockRepository.existsBySymbol(stock.getSymbol())) {
            throw new IllegalArgumentException("Stock with symbol " + stock.getSymbol() + " already exists");
        }
        return stockRepository.save(stock);
    }

    public Stock saveStock(Stock stock) {
        return stockRepository.save(stock);
    }

    public Stock updateStock(Long id, Stock stock) {
        Stock existingStock = getStockById(id);

        if (!existingStock.getSymbol().equals(stock.getSymbol()) &&
            stockRepository.existsBySymbol(stock.getSymbol())) {
            throw new IllegalArgumentException("Stock with symbol " + stock.getSymbol() + " already exists");
        }

        existingStock.setSymbol(stock.getSymbol());
        existingStock.setName(stock.getName());
        existingStock.setSector(stock.getSector());
        existingStock.setIndustry(stock.getIndustry());
        existingStock.setYahooSymbol(stock.getYahooSymbol());

        return stockRepository.save(existingStock);
    }

    public void deleteStock(Long id) {
        Stock stock = getStockById(id);

        log.info("Deleting stock {} (id={}) — removing all dependent FK rows first",
                stock.getSymbol(), id);

        deleteDependents("portfolio_holdings", id, stock,
                portfolioHoldingRepository.deleteByStockId(id));
        deleteDependents("portfolio_snapshots", id, stock,
                (int) portfolioSnapshotRepository.deleteByStockId(id));
        deleteDependents("support_resistance_levels", id, stock,
                supportResistanceLevelRepository.deleteByStockId(id));
        deleteDependents("block_deals", id, stock,
                blockDealRepository.deleteByStockId(id));
        deleteDependents("bulk_deals", id, stock,
                bulkDealRepository.deleteByStockId(id));
        deleteDependents("institutional_holdings", id, stock,
                institutionalHoldingRepository.deleteByStockId(id));
        deleteDependents("watchlist_items", id, stock,
                watchlistItemRepository.deleteByStockId(id));
        deleteDependents("signal_records", id, stock,
                signalRecordRepository.deleteByStockId(id));

        // daily_prices and technical_indicators are cascade-deleted via
        // Stock.@OneToMany(cascade = ALL, orphanRemoval = true).
        stockRepository.delete(stock);
    }

    /**
     * Deletes all stocks whose symbol starts with the given prefix (e.g. "TESTMQ"),
     * cleaning up all dependent FK rows first.
     *
     * @return number of stocks deleted
     */
    public int deleteStocksBySymbolPrefix(String prefix) {
        List<Stock> matches = stockRepository.findBySymbolStartingWith(prefix);
        if (matches.isEmpty()) {
            log.info("No stocks found with symbol prefix '{}'", prefix);
            return 0;
        }
        log.info("Found {} stock(s) matching prefix '{}': {}", matches.size(), prefix,
                matches.stream().map(Stock::getSymbol).toList());
        for (Stock stock : matches) {
            deleteStock(stock.getId());
        }
        return matches.size();
    }

    private void deleteDependents(String table, Long stockId, Stock stock, int count) {
        if (count > 0) {
            log.info("Removed {} rows from {} for stock {} (id={})",
                    count, table, stock.getSymbol(), stockId);
        }
    }

    @Transactional(readOnly = true)
    public Stock getStockById(Long id) {
        return stockRepository.findById(id)
                .orElseThrow(() -> new StockNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Stock getStockBySymbol(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new StockNotFoundException(symbol));
    }

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Stock> getStocksByIds(List<Long> ids) {
        return stockRepository.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public StockDTO getStockDTO(Stock stock) {
        return convertToDTO(stock);
    }

    @Transactional(readOnly = true)
    public List<StockDTO> getAllStockDTOs() {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) return List.of();

        List<Long> stockIds = stocks.stream().map(Stock::getId).collect(Collectors.toList());

        Map<Long, BigDecimal> latestPriceMap = new HashMap<>();
        for (DailyPrice dp : dailyPriceRepository.findLatestPriceForStockIds(stockIds)) {
            latestPriceMap.put(dp.getStock().getId(), dp.getClosingPrice());
        }

        Map<Long, BigDecimal> latestRsiMap = new HashMap<>();
        List<TechnicalIndicator> rsiIndicators = technicalIndicatorRepository
                .findLatestByStockIdsAndTypes(stockIds, List.of(IndicatorType.RSI));
        for (TechnicalIndicator ti : rsiIndicators) {
            latestRsiMap.put(ti.getStock().getId(), ti.getValue());
        }

        // Batch percent change: fetch 3-month prices for all stocks in one query
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        Map<Long, BigDecimal> percentChangeMap = new HashMap<>();
        List<DailyPrice> recentPrices = dailyPriceRepository.findPricesForStockIdsSince(stockIds, threeMonthsAgo);
        Map<Long, List<DailyPrice>> pricesByStock = new HashMap<>();
        for (DailyPrice dp : recentPrices) {
            pricesByStock.computeIfAbsent(dp.getStock().getId(), k -> new ArrayList<>()).add(dp);
        }
        for (Map.Entry<Long, List<DailyPrice>> entry : pricesByStock.entrySet()) {
            List<DailyPrice> prices = entry.getValue();
            if (prices.size() >= 2) {
                BigDecimal latest = prices.get(0).getClosingPrice();
                BigDecimal oldest = prices.get(prices.size() - 1).getClosingPrice();
                if (latest != null && oldest != null && oldest.compareTo(BigDecimal.ZERO) > 0) {
                    percentChangeMap.put(entry.getKey(),
                            latest.subtract(oldest).multiply(BigDecimal.valueOf(100))
                                    .divide(oldest, 2, RoundingMode.HALF_UP));
                }
            }
        }

        Map<Long, BigDecimal> finalPercentChangeMap = percentChangeMap;
        return stocks.stream()
                .map(stock -> {
                    StockDTO dto = convertToDTO(stock, latestPriceMap.get(stock.getId()), latestRsiMap.get(stock.getId()));
                    dto.setPercentChange(finalPercentChangeMap.get(stock.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RsiDTO> getRecentRsiValues(Long stockId, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        List<TechnicalIndicator> indicators = technicalIndicatorRepository
                .findByStockIdAndIndicatorTypeOrderByCalculationDateDesc(stockId, IndicatorType.RSI);
        return indicators.stream()
                .filter(ti -> !ti.getCalculationDate().isBefore(since))
                .map(ti -> new RsiDTO(ti.getId(), stockId, ti.getValue(), ti.getCalculationDate(), ti.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public Stock getOrCreateStock(String symbol, String name, String sector, String industry) {
        String trimmedSymbol = symbol != null ? symbol.trim() : null;
        String trimmedName = name != null ? name.trim() : null;

        return stockRepository.findBySymbol(trimmedSymbol)
                .map(existingStock -> {
                    boolean updated = false;
                    if (sector != null && (existingStock.getSector() == null || !existingStock.getSector().equals(sector))) {
                        existingStock.setSector(sector);
                        updated = true;
                    }
                    if (industry != null && (existingStock.getIndustry() == null || !existingStock.getIndustry().equals(industry))) {
                        existingStock.setIndustry(industry);
                        updated = true;
                    }
                    if (updated) {
                        return stockRepository.save(existingStock);
                    }
                    return existingStock;
                })
                .orElseGet(() -> {
                    if (trimmedSymbol == null || trimmedSymbol.isBlank()) {
                        throw new IllegalArgumentException("Symbol is required");
                    }
                    if (trimmedName == null || trimmedName.isBlank()) {
                        throw new IllegalArgumentException("Name is required");
                    }
                    if (trimmedSymbol.length() > 20) {
                        throw new IllegalArgumentException("Symbol must be at most 20 characters");
                    }
                    return stockRepository.save(new Stock(trimmedSymbol, trimmedName, sector, industry));
                });
    }

    public Stock getOrCreateStock(String symbol, String name, String sector) {
        return getOrCreateStock(symbol, name, sector, null);
    }

    public Stock updatePortfolioData(Long id, CsvImportRequest req) {
        Stock stock = getStockById(id);
        stock.setQuantity(req.getQuantity());
        stock.setAvgPrice(req.getAvgPrice());
        Stock result = recalculatePortfolio(stock);
        syncPortfolioHoldings(stock);
        return result;
    }

    public Stock recalculatePortfolio(Long stockId) {
        return recalculatePortfolio(getStockById(stockId));
    }

    public Stock recalculatePortfolio(Stock stock) {
        if (stock.getQuantity() != null && stock.getAvgPrice() != null) {
            BigDecimal qty = BigDecimal.valueOf(stock.getQuantity());
            stock.setInvestment(stock.getAvgPrice().multiply(qty));
        } else {
            stock.setInvestment(null);
        }

        dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(stock.getId())
                .ifPresentOrElse(price -> {
                    stock.setLastTradedPrice(price.getClosingPrice());
                    if (stock.getQuantity() != null && stock.getInvestment() != null) {
                        BigDecimal qty = BigDecimal.valueOf(stock.getQuantity());
                        stock.setCurrentValue(price.getClosingPrice().multiply(qty));
                        stock.setPnl(stock.getCurrentValue().subtract(stock.getInvestment()));
                        if (stock.getInvestment().compareTo(BigDecimal.ZERO) > 0) {
                            stock.setPnlPercent(stock.getPnl()
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(stock.getInvestment(), 2, RoundingMode.HALF_UP));
                        } else {
                            stock.setPnlPercent(null);
                        }
                    } else {
                        stock.setCurrentValue(null);
                        stock.setPnl(null);
                        stock.setPnlPercent(null);
                    }
                }, () -> {
                    stock.setLastTradedPrice(null);
                    stock.setCurrentValue(null);
                    stock.setPnl(null);
                    stock.setPnlPercent(null);
                });

        return stock;
    }

    @Transactional
    public int recalculateAllPortfolios() {
        List<Stock> stocks = getAllStocks();
        for (Stock stock : stocks) {
            recalculatePortfolio(stock);
        }
        return stocks.size();
    }

    /**
     * Syncs the Stock entity's quantity/avgPrice to all PortfolioHolding records
     * for this stock. This keeps the stocks table and portfolio_holdings table
     * in sync when portfolio data is updated via the stock management page.
     */
    public void syncPortfolioHoldings(Stock stock) {
        // Only sync to the default portfolio — prevents overwriting
        // portfolio-specific holdings in custom portfolios.
        Portfolio defaultPort = portfolioRepository.findByIsDefaultTrue().orElse(null);
        if (defaultPort == null) return;

        PortfolioHolding holding = portfolioHoldingRepository
                .findByPortfolioIdAndStockId(defaultPort.getId(), stock.getId())
                .orElseGet(() -> {
                    PortfolioHolding h = new PortfolioHolding();
                    h.setPortfolio(defaultPort);
                    h.setStock(stock);
                    return h;
                });

        holding.setQuantity(stock.getQuantity());
        holding.setAvgPrice(stock.getAvgPrice());
        portfolioHoldingRepository.save(holding);
        log.debug("Synced holding for stock {} in default portfolio (id={})",
                stock.getSymbol(), defaultPort.getId());
    }

    /**
     * Resets a stock's portfolio data to watched-only (quantity = 0).
     * Called automatically when a stock is added to a watchlist.
     */
    @Transactional
    public Stock resetPortfolioToWatched(Long stockId) {
        Stock stock = getStockById(stockId);
        if (stock.getQuantity() != null && stock.getQuantity() > 0) {
            stock.setQuantity(0);
            stock.setAvgPrice(null);
            stock.setInvestment(null);
            stock.setCurrentValue(null);
            stock.setPnl(null);
            stock.setPnlPercent(null);
            stock = stockRepository.save(stock);
            syncPortfolioHoldings(stock);
        }
        return stock;
    }

    private StockDTO convertToDTO(Stock stock) {
        Long stockId = stock.getId();
        BigDecimal latestClosingPrice = dailyPriceRepository
                .findFirstByStockIdOrderByPriceDateDesc(stockId)
                .map(DailyPrice::getClosingPrice)
                .orElse(null);
        BigDecimal rsi = technicalIndicatorRepository
                .findFirstByStockIdAndIndicatorTypeOrderByCalculationDateDesc(stockId, IndicatorType.RSI)
                .map(TechnicalIndicator::getValue)
                .orElse(null);
        return convertToDTO(stock, latestClosingPrice, rsi);
    }

    private StockDTO convertToDTO(Stock stock, BigDecimal latestClosingPrice, BigDecimal latestRsi) {
        StockDTO dto = new StockDTO();
        dto.setId(stock.getId());
        dto.setSymbol(stock.getSymbol());
        dto.setName(stock.getName());
        dto.setSector(stock.getSector());
        dto.setIndustry(stock.getIndustry());
        dto.setYahooSymbol(stock.getYahooSymbol());
        dto.setCreatedAt(stock.getCreatedAt());
        dto.setUpdatedAt(stock.getUpdatedAt());
        dto.setQuantity(stock.getQuantity());
        dto.setAvgPrice(stock.getAvgPrice());
        dto.setLastTradedPrice(latestClosingPrice != null ? latestClosingPrice : stock.getLastTradedPrice());
        dto.setLatestPrice(latestClosingPrice);
        dto.setLatestRsi(latestRsi);

        BigDecimal qty = stock.getQuantity() != null ? BigDecimal.valueOf(stock.getQuantity()) : null;
        BigDecimal avgPrice = stock.getAvgPrice();
        if (qty != null && avgPrice != null) {
            BigDecimal investment = avgPrice.multiply(qty);
            dto.setInvestment(investment);
            if (latestClosingPrice != null) {
                BigDecimal currentValue = latestClosingPrice.multiply(qty);
                BigDecimal pnl = currentValue.subtract(investment);
                BigDecimal pnlPercent = investment.compareTo(BigDecimal.ZERO) > 0
                        ? pnl.multiply(BigDecimal.valueOf(100)).divide(investment, 2, RoundingMode.HALF_UP)
                        : null;
                dto.setCurrentValue(currentValue);
                dto.setPnl(pnl);
                dto.setPnlPercent(pnlPercent);
            }
        }

        return dto;
    }
}
