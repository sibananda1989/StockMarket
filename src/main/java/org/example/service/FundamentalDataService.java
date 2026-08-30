package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.FundamentalDataDTO;
import org.example.dto.FundamentalScreenRequest;
import org.example.entity.FundamentalData;
import org.example.entity.Stock;
import org.example.repository.FundamentalDataRepository;
import org.example.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FundamentalDataService {

    private static final String PYTHON_SCRIPT = "fetch_fundamentals.py";
    private static final String PYTHON_BATCH_SCRIPT = "fetch_fundamentals_batch.py";

    private final FundamentalDataRepository fundamentalDataRepository;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;

    public FundamentalDataService(FundamentalDataRepository fundamentalDataRepository,
                                   StockRepository stockRepository,
                                   ObjectMapper objectMapper) {
        this.fundamentalDataRepository = fundamentalDataRepository;
        this.stockRepository = stockRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public FundamentalDataDTO getFundamentals(Long stockId) {
        return fundamentalDataRepository.findByStockId(stockId)
                .map(this::toDTOWithMarketCap)
                .orElse(null);
    }

    @Transactional
    public FundamentalDataDTO fetchAndSave(Long stockId) {
        Stock stock = stockRepository.findById(stockId).orElse(null);
        if (stock == null) return null;

        String symbol = stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol();

        Map<String, Object> rawData = callPythonScript(symbol);
        if (rawData == null && !symbol.contains(".")) {
            rawData = callPythonScript(symbol + ".NS");
        }
        if (rawData == null || rawData.containsKey("error")) {
            log.warn("No fundamental data found for stock {} ({})", stock.getSymbol(), symbol);
            return null;
        }

        FundamentalDataDTO dto = mapRawToDTO(rawData, stock);

        FundamentalData entity = fundamentalDataRepository.findByStockId(stockId)
                .orElseGet(() -> {
                    FundamentalData fd = new FundamentalData();
                    fd.setStock(stock);
                    return fd;
                });

        entity.setPeRatio(dto.getPeRatio());
        entity.setForwardPe(dto.getForwardPe());
        entity.setEpsTtm(dto.getEpsTtm());
        entity.setEpsForward(dto.getEpsForward());
        entity.setBookValue(dto.getBookValue());
        entity.setPriceToBook(dto.getPriceToBook());
        entity.setDividendYield(dto.getDividendYield());
        entity.setRoe(dto.getRoe());
        entity.setDebtToEquity(dto.getDebtToEquity());
        entity.setProfitMargin(dto.getProfitMargin());
        entity.setRevenueTtm(dto.getRevenueTtm());
        entity.setSector(dto.getSector());
        entity.setIndustry(dto.getIndustry());
        entity.setBusinessSummary(dto.getBusinessSummary());
        entity.setSharesOutstanding(dto.getSharesOutstanding());
        entity.setBeta(dto.getBeta());
        entity.setFiftyTwoWeekHigh(dto.getFiftyTwoWeekHigh());
        entity.setFiftyTwoWeekLow(dto.getFiftyTwoWeekLow());
        entity.setFetchedDate(LocalDate.now());

        fundamentalDataRepository.save(entity);

        if (dto.getSector() != null && !dto.getSector().equals(stock.getSector())) {
            stock.setSector(dto.getSector());
        }
        if (dto.getIndustry() != null && !dto.getIndustry().equals(stock.getIndustry())) {
            stock.setIndustry(dto.getIndustry());
        }
        if ((dto.getSector() != null && !dto.getSector().equals(stock.getSector()))
                || (dto.getIndustry() != null && !dto.getIndustry().equals(stock.getIndustry()))) {
            stockRepository.save(stock);
        }

        return toDTOWithMarketCap(entity);
    }

    @Transactional
    public int fetchForAllStocks() {
        List<Stock> stocks = stockRepository.findAll();
        int successCount = 0;
        for (Stock stock : stocks) {
            try {
                FundamentalDataDTO result = fetchAndSave(stock.getId());
                if (result != null) successCount++;
            } catch (Exception e) {
                log.warn("Failed to fetch fundamentals for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
        log.info("Fetched fundamentals for {}/{} stocks", successCount, stocks.size());
        return successCount;
    }

    /**
     * Fetches fundamentals only for stocks with stale data (older than maxAgeDays).
     * Used by startup catch-up task.
     */
    @Transactional
    public int fetchStaleStocks(int maxAgeDays) {
        List<Stock> stocks = stockRepository.findAll();
        LocalDate cutoff = LocalDate.now().minusDays(maxAgeDays);
        int fetchedCount = 0;

        for (Stock stock : stocks) {
            try {
                Optional<FundamentalData> existing = fundamentalDataRepository.findByStockId(stock.getId());
                if (existing.isPresent() && existing.get().getFetchedDate() != null
                        && !existing.get().getFetchedDate().isBefore(cutoff)) {
                    continue; // fresh enough, skip
                }
                FundamentalDataDTO result = fetchAndSave(stock.getId());
                if (result != null) fetchedCount++;
            } catch (Exception e) {
                log.warn("Startup fetch failed for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
        log.info("Startup fundamental catch-up: refreshed {}/{} stale stocks (cutoff={})", fetchedCount, stocks.size(), cutoff);
        return fetchedCount;
    }

    @Transactional(readOnly = true)
    public List<FundamentalDataDTO> screen(FundamentalScreenRequest req) {
        List<FundamentalData> all = fundamentalDataRepository.findAll();

        List<FundamentalData> filtered = all.stream()
                .filter(fd -> fd.getPeRatio() != null && fd.getPeRatio().compareTo(BigDecimal.ZERO) > 0)
                .filter(fd -> req.getPeMin() == null || fd.getPeRatio().compareTo(req.getPeMin()) >= 0)
                .filter(fd -> req.getPeMax() == null || fd.getPeRatio().compareTo(req.getPeMax()) <= 0)
                .filter(fd -> req.getRoeMin() == null || (fd.getRoe() != null && fd.getRoe().compareTo(req.getRoeMin()) >= 0))
                .filter(fd -> req.getDebtToEquityMax() == null || (fd.getDebtToEquity() != null && fd.getDebtToEquity().compareTo(req.getDebtToEquityMax()) <= 0))
                .filter(fd -> req.getDividendYieldMin() == null || (fd.getDividendYield() != null && fd.getDividendYield().compareTo(req.getDividendYieldMin()) >= 0))
                .filter(fd -> req.getSector() == null || req.getSector().isBlank() || req.getSector().equalsIgnoreCase(fd.getSector()))
                .collect(Collectors.toList());

        if (req.getMarketCapMin() != null || req.getMarketCapMax() != null) {
            filtered = filtered.stream()
                    .filter(fd -> {
                        Long mc = computeMarketCap(fd);
                        if (mc == null) return false;
                        if (req.getMarketCapMin() != null && BigDecimal.valueOf(mc).compareTo(req.getMarketCapMin().multiply(BigDecimal.valueOf(10000000))) < 0) return false;
                        if (req.getMarketCapMax() != null && BigDecimal.valueOf(mc).compareTo(req.getMarketCapMax().multiply(BigDecimal.valueOf(10000000))) > 0) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        String sortBy = req.getSortBy() != null ? req.getSortBy() : "marketCap";
        String sortOrder = req.getSortOrder() != null ? req.getSortOrder() : "desc";

        Comparator<FundamentalData> comparator = switch (sortBy) {
            case "peRatio" -> Comparator.comparing(FundamentalData::getPeRatio, Comparator.nullsLast(Comparator.naturalOrder()));
            case "epsTtm" -> Comparator.comparing(FundamentalData::getEpsTtm, Comparator.nullsLast(Comparator.naturalOrder()));
            case "roe" -> Comparator.comparing(FundamentalData::getRoe, Comparator.nullsLast(Comparator.naturalOrder()));
            case "debtToEquity" -> Comparator.comparing(FundamentalData::getDebtToEquity, Comparator.nullsLast(Comparator.naturalOrder()));
            case "dividendYield" -> Comparator.comparing(FundamentalData::getDividendYield, Comparator.nullsLast(Comparator.naturalOrder()));
            case "beta" -> Comparator.comparing(FundamentalData::getBeta, Comparator.nullsLast(Comparator.naturalOrder()));
            case "revenueTtm" -> Comparator.comparing(FundamentalData::getRevenueTtm, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.<FundamentalData, Long>comparing(fd -> {
                Long mc = computeMarketCap(fd);
                return mc != null ? mc : 0L;
            }, Comparator.nullsLast(Comparator.naturalOrder()));
        };

        if ("asc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }

        return filtered.stream()
                .sorted(comparator)
                .skip(req.getOffset())
                .limit(req.getLimit())
                .map(this::toDTOWithMarketCap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getDistinctSectors() {
        return fundamentalDataRepository.findDistinctSectors();
    }

    /**
     * Batch fetch fundamentals using a single Python process for all stale stocks.
     * Saves ~1-2s per stock by avoiding JVM/process startup overhead.
     * Still respects 1 req/sec Yahoo rate limit internally.
     */
    @Transactional
    public int fetchStaleStocksBatch(int maxAgeDays) {
        List<Stock> stocks = stockRepository.findAll();
        LocalDate cutoff = LocalDate.now().minusDays(maxAgeDays);

        List<String> staleSymbols = new ArrayList<>();
        List<Stock> staleStocks = new ArrayList<>();

        for (Stock stock : stocks) {
            Optional<FundamentalData> existing = fundamentalDataRepository.findByStockId(stock.getId());
            if (existing.isPresent() && existing.get().getFetchedDate() != null
                    && !existing.get().getFetchedDate().isBefore(cutoff)) {
                continue;
            }
            staleStocks.add(stock);
            staleSymbols.add(stock.getYahooSymbol() != null ? stock.getYahooSymbol() : stock.getSymbol());
        }

        if (staleSymbols.isEmpty()) {
            log.info("No stale fundamental data to fetch");
            return 0;
        }

        log.info("Batch fetching fundamentals for {} stale stocks...", staleSymbols.size());

        List<Map<String, Object>> results = callPythonBatchScript(staleSymbols);
        int successCount = 0;

        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> rawData = results.get(i);
            if (rawData == null || rawData.containsKey("error")) {
                log.warn("Failed to fetch fundamentals for {}: {}",
                        staleStocks.get(i).getSymbol(),
                        rawData != null ? rawData.get("error") : "null response");
                continue;
            }

            try {
                Stock stock = staleStocks.get(i);
                FundamentalDataDTO dto = mapRawToDTO(rawData, stock);

                FundamentalData entity = fundamentalDataRepository.findByStockId(stock.getId())
                        .orElseGet(() -> {
                            FundamentalData fd = new FundamentalData();
                            fd.setStock(stock);
                            return fd;
                        });

                entity.setPeRatio(dto.getPeRatio());
                entity.setForwardPe(dto.getForwardPe());
                entity.setEpsTtm(dto.getEpsTtm());
                entity.setEpsForward(dto.getEpsForward());
                entity.setBookValue(dto.getBookValue());
                entity.setPriceToBook(dto.getPriceToBook());
                entity.setDividendYield(dto.getDividendYield());
                entity.setRoe(dto.getRoe());
                entity.setDebtToEquity(dto.getDebtToEquity());
                entity.setProfitMargin(dto.getProfitMargin());
                entity.setRevenueTtm(dto.getRevenueTtm());
                entity.setSector(dto.getSector());
                entity.setIndustry(dto.getIndustry());
                entity.setBusinessSummary(dto.getBusinessSummary());
                entity.setSharesOutstanding(dto.getSharesOutstanding());
                entity.setBeta(dto.getBeta());
                entity.setFiftyTwoWeekHigh(dto.getFiftyTwoWeekHigh());
                entity.setFiftyTwoWeekLow(dto.getFiftyTwoWeekLow());
                entity.setFetchedDate(LocalDate.now());

                fundamentalDataRepository.save(entity);

                if (dto.getSector() != null && !dto.getSector().equals(stock.getSector())) {
                    stock.setSector(dto.getSector());
                }
                if (dto.getIndustry() != null && !dto.getIndustry().equals(stock.getIndustry())) {
                    stock.setIndustry(dto.getIndustry());
                }
                if ((dto.getSector() != null && !dto.getSector().equals(stock.getSector()))
                        || (dto.getIndustry() != null && !dto.getIndustry().equals(stock.getIndustry()))) {
                    stockRepository.save(stock);
                }

                successCount++;
            } catch (Exception e) {
                log.warn("Failed to save fundamentals for {}: {}",
                        staleStocks.get(i).getSymbol(), e.getMessage());
            }
        }

        log.info("Batch fundamental fetch complete: {}/{} stocks updated", successCount, staleSymbols.size());
        return successCount;
    }

    private FundamentalDataDTO toDTOWithMarketCap(FundamentalData fd) {
        Stock stock = fd.getStock();
        BigDecimal ltp = stock.getLastTradedPrice();
        Long marketCap = computeMarketCap(fd);

        return FundamentalDataDTO.builder()
                .id(fd.getId())
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .peRatio(fd.getPeRatio())
                .forwardPe(fd.getForwardPe())
                .epsTtm(fd.getEpsTtm())
                .epsForward(fd.getEpsForward())
                .bookValue(fd.getBookValue())
                .priceToBook(fd.getPriceToBook())
                .dividendYield(fd.getDividendYield())
                .roe(fd.getRoe())
                .debtToEquity(fd.getDebtToEquity())
                .profitMargin(fd.getProfitMargin())
                .marketCap(marketCap)
                .revenueTtm(fd.getRevenueTtm())
                .sharesOutstanding(fd.getSharesOutstanding())
                .sector(fd.getSector())
                .industry(fd.getIndustry())
                .businessSummary(fd.getBusinessSummary())
                .beta(fd.getBeta())
                .fiftyTwoWeekHigh(fd.getFiftyTwoWeekHigh())
                .fiftyTwoWeekLow(fd.getFiftyTwoWeekLow())
                .fetchedDate(fd.getFetchedDate())
                .build();
    }

    private Long computeMarketCap(FundamentalData fd) {
        if (fd.getSharesOutstanding() == null) return null;
        Stock stock = fd.getStock();
        BigDecimal ltp = stock.getLastTradedPrice();
        if (ltp == null) return null;
        return BigDecimal.valueOf(fd.getSharesOutstanding()).multiply(ltp).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callPythonScript(String symbol) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", PYTHON_SCRIPT, symbol);
            pb.directory(new java.io.File("."));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Python script exited with code {} for symbol {}", exitCode, symbol);
                return null;
            }

            return objectMapper.readValue(output.toString(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to call Python script for symbol {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callPythonBatchScript(List<String> symbols) {
        try {
            String[] cmd = new String[2 + symbols.size()];
            cmd[0] = "python3";
            cmd[1] = PYTHON_BATCH_SCRIPT;
            for (int i = 0; i < symbols.size(); i++) {
                cmd[2 + i] = symbols.get(i);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File("."));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Batch Python script exited with code {}", exitCode);
                return List.of();
            }

            return objectMapper.readValue(output.toString(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("Failed to call batch Python script: {}", e.getMessage());
            return List.of();
        }
    }

    private FundamentalDataDTO mapRawToDTO(Map<String, Object> raw, Stock stock) {
        return FundamentalDataDTO.builder()
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .peRatio(toBigDecimal(raw.get("peRatio")))
                .forwardPe(toBigDecimal(raw.get("forwardPe")))
                .epsTtm(toBigDecimal(raw.get("epsTtm")))
                .epsForward(toBigDecimal(raw.get("epsForward")))
                .bookValue(toBigDecimal(raw.get("bookValue")))
                .priceToBook(toBigDecimal(raw.get("priceToBook")))
                .dividendYield(toBigDecimal(raw.get("dividendYield")))
                .roe(toBigDecimal(raw.get("roe")))
                .debtToEquity(toBigDecimal(raw.get("debtToEquity")))
                .profitMargin(toBigDecimal(raw.get("profitMargin")))
                .marketCap(toLongObject(raw.get("marketCap")))
                .revenueTtm(toLongObject(raw.get("revenueTtm")))
                .sharesOutstanding(toLongObject(raw.get("sharesOutstanding")))
                .sector((String) raw.get("sector"))
                .industry((String) raw.get("industry"))
                .businessSummary((String) raw.get("businessSummary"))
                .beta(toBigDecimal(raw.get("beta")))
                .fiftyTwoWeekHigh(toBigDecimal(raw.get("fiftyTwoWeekHigh")))
                .fiftyTwoWeekLow(toBigDecimal(raw.get("fiftyTwoWeekLow")))
                .fetchedDate(LocalDate.now())
                .build();
    }

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        return null;
    }

    private static Long toLongObject(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return null;
    }
}
