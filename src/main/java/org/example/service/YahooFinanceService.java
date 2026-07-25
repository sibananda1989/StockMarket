package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.FundamentalDataDTO;
import org.example.dto.StockHistoryDTO;
import org.example.dto.SymbolValidationResult;
import org.example.dto.StockSearchResultDTO;
import org.example.exception.StockHistoryNotFoundException;
import org.slf4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class YahooFinanceService {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(YahooFinanceService.class);
    private static final String YAHOO_BASE = "https://query1.finance.yahoo.com";
    private static final long YAHOO_RATE_LIMIT_MS = 500L; // 2 requests per second (unofficial API, no published limit)

    private final WebClient webClient;
    private final ApiRateLimiter rateLimiter;

    private static final String YAHOO_QUOTE_URL = "https://query1.finance.yahoo.com/v7/finance/quote";

    public YahooFinanceService(ApiRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.webClient = WebClient.builder()
                .baseUrl(YAHOO_BASE)
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
    }

    /**
     * Validates whether a stock symbol exists on Yahoo Finance.
     * Returns the validated symbol (possibly with .NS suffix) and the company name if found.
     * @param symbol the stock symbol to validate (e.g. "RELIANCE", "AAPL")
     * @return SymbolValidationResult with validated=true if the symbol exists
     */
    @SuppressWarnings("unchecked")
    public SymbolValidationResult validateSymbol(String symbol) {
        // Try the symbol as-is first
        SymbolValidationResult result = tryValidateSymbol(symbol);
        if (result.isValid()) {
            return result;
        }
        // Fall back to .NS suffix for NSE stocks
        if (!symbol.contains(".")) {
            result = tryValidateSymbol(symbol + ".NS");
            if (result.isValid()) {
                return result;
            }
        }
        return new SymbolValidationResult(false, symbol, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private SymbolValidationResult tryValidateSymbol(String yahooSymbol) {
        try {
            rateLimiter.acquire("yahoo", YAHOO_RATE_LIMIT_MS);
            Map<?, ?> response = webClient.get()
                    .uri(YAHOO_QUOTE_URL + "?symbols=" + yahooSymbol)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (response == null) {
                return new SymbolValidationResult(false, yahooSymbol, null, null, null);
            }

            Map<?, ?> quoteResponse = (Map<?, ?>) response.get("quoteResponse");
            if (quoteResponse == null) {
                return new SymbolValidationResult(false, yahooSymbol, null, null, null);
            }

            List<?> result = (List<?>) quoteResponse.get("result");
            if (result == null || result.isEmpty()) {
                return new SymbolValidationResult(false, yahooSymbol, null, null, null);
            }

            Map<?, ?> quote = (Map<?, ?>) result.get(0);
            String longName = (String) quote.get("longName");
            if (longName == null) {
                longName = (String) quote.get("shortName");
            }
            if (longName == null) {
                longName = (String) quote.get("symbol");
            }

            String sector = (String) quote.get("sector");
            if (sector == null || sector.isBlank()) {
                sector = (String) quote.get("industry");
            }

            String industry = (String) quote.get("industry");

            return new SymbolValidationResult(true, yahooSymbol, longName, sector, industry);

        } catch (Exception e) {
            log.warn("Yahoo Finance symbol validation failed for {}: {}", yahooSymbol, e.getMessage());
            return new SymbolValidationResult(false, yahooSymbol, null, null, null);
        }
    }

    /**
     * Batch validate multiple symbols in a single API call.
     * Returns a map of original symbol -> validation result.
     * Falls back to .NS suffix for any symbol that fails as-is.
     */
    @SuppressWarnings("unchecked")
    public Map<String, SymbolValidationResult> validateSymbols(List<String> symbols) {
        Map<String, SymbolValidationResult> results = new java.util.HashMap<>();

        List<String> asIs = new ArrayList<>();
        for (String s : symbols) {
            asIs.add(s.trim().toUpperCase());
        }

        batchFetchQuoteResults(asIs, results, false);

        List<String> needsNs = new ArrayList<>();
        for (String s : symbols) {
            String upper = s.trim().toUpperCase();
            SymbolValidationResult r = results.get(upper);
            if (r == null || !r.isValid()) {
                needsNs.add(upper + ".NS");
            }
        }

        if (!needsNs.isEmpty()) {
            batchFetchQuoteResults(needsNs, results, true);
        }

        for (String s : symbols) {
            String upper = s.trim().toUpperCase();
            results.putIfAbsent(upper, new SymbolValidationResult(false, upper, null, null, null));
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private void batchFetchQuoteResults(List<String> yahooSymbols, Map<String, SymbolValidationResult> results, boolean isNsFallback) {
        if (yahooSymbols.isEmpty()) return;
        rateLimiter.acquire("yahoo", YAHOO_RATE_LIMIT_MS);
        try {
            String symbolsParam = String.join(",", yahooSymbols);
            Map<?, ?> response = webClient.get()
                    .uri(YAHOO_QUOTE_URL + "?symbols=" + symbolsParam)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (response == null) return;

            Map<?, ?> quoteResponse = (Map<?, ?>) response.get("quoteResponse");
            if (quoteResponse == null) return;

            List<?> result = (List<?>) quoteResponse.get("result");
            if (result == null) return;

            for (Object obj : result) {
                if (!(obj instanceof Map)) continue;
                Map<?, ?> quote = (Map<?, ?>) obj;
                String symbol = (String) quote.get("symbol");
                if (symbol == null) continue;

                String longName = (String) quote.get("longName");
                if (longName == null) longName = (String) quote.get("shortName");
                if (longName == null) longName = symbol;

                String sector = (String) quote.get("sector");
                if (sector == null || sector.isBlank()) sector = (String) quote.get("industry");

                String industry = (String) quote.get("industry");

                String rawKey = isNsFallback && symbol.endsWith(".NS")
                        ? symbol.substring(0, symbol.length() - 3)
                        : symbol;
                results.put(rawKey, new SymbolValidationResult(true, symbol, longName, sector, industry));
            }
        } catch (Exception e) {
            log.warn("Yahoo Finance batch symbol validation failed for {}: {}", yahooSymbols, e.getMessage());
        }
    }

    /**
     * Search for stocks by name using Yahoo Finance search API.
     * @param query the search query (partial or full stock name)
     * @param limit maximum number of results to return (default 10)
     * @return list of stock search results matching the query
     */
    @SuppressWarnings("unchecked")
    public List<StockSearchResultDTO> searchByName(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Yahoo Finance API requires minimum 3 characters for search
        if (query.trim().length() < 3) {
            return new ArrayList<>();
        }

        try {
            rateLimiter.acquire("yahoo", YAHOO_RATE_LIMIT_MS);
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString());
            Map<?, ?> response = webClient.get()
                    .uri("/v1/finance/search?q=" + encodedQuery +
                            "&quotesCount=" + Math.max(1, Math.min(limit, 50)) +
                            "&newsCount=0&enableFuzzyQuery=false&quotesQueryId=tss_match_phrase_query")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (response == null) {
                return new ArrayList<>();
            }

            List<?> quotesList = (List<?>) response.get("quotes");
            if (quotesList == null || quotesList.isEmpty()) {
                return new ArrayList<>();
            }

            List<StockSearchResultDTO> results = new ArrayList<>();
            for (Object quoteObj : quotesList) {
                if (!(quoteObj instanceof Map)) continue;
                Map<?, ?> quote = (Map<?, ?>) quoteObj;

                String symbol = (String) quote.get("symbol");
                // Yahoo Finance API uses lowercase keys: shortname, longname
                String name = (String) quote.get("shortname");
                if (name == null) name = (String) quote.get("longname");
                // Use display fields for better readability
                String exchange = (String) quote.get("exchDisp");
                if (exchange == null) exchange = (String) quote.get("exchange");
                String sector = (String) quote.get("sectorDisp");
                if (sector == null) sector = (String) quote.get("sector");
                String industry = (String) quote.get("industryDisp");
                if (industry == null) industry = (String) quote.get("industry");
                String quoteType = (String) quote.get("quoteType");
                Boolean isYahooFinanceObj = (Boolean) quote.get("isYahooFinance");
                boolean isYahooFinance = isYahooFinanceObj != null && isYahooFinanceObj;

                if (symbol != null && name != null) {
                    results.add(new StockSearchResultDTO(
                            symbol,
                            name,
                            exchange,
                            sector,
                            industry,
                            quoteType,
                            isYahooFinance
                    ));
                }
            }

            return results;

        } catch (Exception e) {
            log.warn("Yahoo Finance name search failed for '{}': {}", query, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Search for stocks by name using Yahoo Finance search API (default limit 10).
     * @param query the search query (partial or full stock name)
     * @return list of stock search results matching the query
     */
    public List<StockSearchResultDTO> searchByName(String query) {
        return searchByName(query, 10);
    }

    @Cacheable(value = "stockHistory", key = "#symbol + '_' + #days")
    public List<StockHistoryDTO> fetchHistory(String symbol, int days) {
        long toEpoch   = Instant.now().getEpochSecond();
        long fromEpoch = Instant.now().minus(Duration.ofDays(days)).getEpochSecond();

        log.info("Fetching Yahoo Finance history: symbol={} days={}", symbol, days);

        // Try the symbol as-is first
        try {
            return fetchWithSymbol(symbol, symbol, fromEpoch, toEpoch);
        } catch (StockHistoryNotFoundException e) {
            log.warn("Failed to fetch with symbol '{}', trying with .NS suffix", symbol);
            
            // If symbol doesn't already have a dot suffix, try with .NS
            if (!symbol.contains(".")) {
                String yahooSymbol = symbol + ".NS";
                try {
                    return fetchWithSymbol(symbol, yahooSymbol, fromEpoch, toEpoch);
                } catch (StockHistoryNotFoundException e2) {
                    log.warn("Failed to fetch with .NS suffix for '{}'", symbol);
                    throw new StockHistoryNotFoundException(
                            "No data found for symbol: " + symbol + ". Tried both '" + symbol + "' and '" + yahooSymbol + "'");
                }
            }
            throw e;
        }
    }

    private List<StockHistoryDTO> fetchWithSymbol(String originalSymbol, String yahooSymbol, long fromEpoch, long toEpoch) {
        String url = "/v8/finance/chart/" + yahooSymbol + "?period1=" + fromEpoch + "&period2=" + toEpoch + "&interval=1d&events=history";
        log.info("Yahoo Finance URL: {}{}", YAHOO_BASE, url);

        try {
            rateLimiter.acquire("yahoo", YAHOO_RATE_LIMIT_MS);
            Map<?, ?> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("period1", fromEpoch)
                            .queryParam("period2", toEpoch)
                            .queryParam("interval", "1d")
                            .queryParam("events", "history")
                            .build(yahooSymbol))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                    .block(Duration.ofSeconds(15));

            log.info("Yahoo Finance response received for symbol: {}", yahooSymbol);
            log.debug("Response: {}", response);

            return parseResponse(response, originalSymbol);

        } catch (WebClientResponseException e) {
            log.error("Yahoo Finance returned HTTP {} for symbol {} (yahoo: {}): {}", e.getStatusCode(), originalSymbol, yahooSymbol, e.getResponseBodyAsString());
            throw new StockHistoryNotFoundException(
                    "No data found for symbol: " + originalSymbol + " (HTTP " + e.getStatusCode() + ")");
        } catch (StockHistoryNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching history for {} (yahoo: {}): {}", originalSymbol, yahooSymbol, e.getMessage(), e);
            throw new StockHistoryNotFoundException("Failed to fetch history for: " + originalSymbol);
        }
    }

    @SuppressWarnings("unchecked")
    private List<StockHistoryDTO> parseResponse(Map<?, ?> response, String symbol) {
        try {
            Map<?, ?> chart   = (Map<?, ?>) response.get("chart");
            List<?>   result  = (List<?>) chart.get("result");

            if (result == null || result.isEmpty()) {
                throw new StockHistoryNotFoundException("No history data returned for: " + symbol);
            }

            Map<?, ?> data       = (Map<?, ?>) result.get(0);
            List<?>   timestamps = (List<?>) data.get("timestamp");
            Map<?, ?> indicators = (Map<?, ?>) data.get("indicators");
            List<?>   quoteList  = (List<?>) indicators.get("quote");
            Map<?, ?> quote      = (Map<?, ?>) quoteList.get(0);

            List<?>   opens   = (List<?>) quote.get("open");
            List<?>   highs   = (List<?>) quote.get("high");
            List<?>   lows    = (List<?>) quote.get("low");
            List<?>   closes  = (List<?>) quote.get("close");
            List<?>   volumes = (List<?>) quote.get("volume");

            List<StockHistoryDTO> history = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (closes.get(i) == null) continue;

                long epoch = ((Number) timestamps.get(i)).longValue();
                LocalDate date = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate();

                history.add(new StockHistoryDTO(
                        date,
                        toDouble(opens.get(i)),
                        toDouble(highs.get(i)),
                        toDouble(lows.get(i)),
                        toDouble(closes.get(i)),
                        toLong(volumes.get(i))
                ));
            }

            if (history.isEmpty()) {
                throw new StockHistoryNotFoundException("Empty price data for: " + symbol);
            }

            return history;

        } catch (StockHistoryNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Yahoo Finance response for {}: {}", symbol, e.getMessage());
            throw new StockHistoryNotFoundException("Could not parse data for: " + symbol);
        }
    }

    private double toDouble(Object val) {
        return val == null ? 0.0 : ((Number) val).doubleValue();
    }

    private long toLong(Object val) {
        return val == null ? 0L : ((Number) val).longValue();
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

    @SuppressWarnings("unchecked")
    public FundamentalDataDTO fetchFundamentals(String yahooSymbol) {
        try {
            rateLimiter.acquire("yahoo", YAHOO_RATE_LIMIT_MS);
            Map<?, ?> response = webClient.get()
                    .uri(YAHOO_QUOTE_URL + "?symbols=" + yahooSymbol)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (response == null) return null;

            Map<?, ?> quoteResponse = (Map<?, ?>) response.get("quoteResponse");
            if (quoteResponse == null) return null;

            List<?> result = (List<?>) quoteResponse.get("result");
            if (result == null || result.isEmpty()) return null;

            Map<?, ?> quote = (Map<?, ?>) result.get(0);

            String longName = (String) quote.get("longName");
            if (longName == null) longName = (String) quote.get("shortName");

            return FundamentalDataDTO.builder()
                    .symbol(yahooSymbol)
                    .name(longName)
                    .peRatio(toBigDecimal(quote.get("trailingPE")))
                    .forwardPe(toBigDecimal(quote.get("forwardPE")))
                    .epsTtm(toBigDecimal(quote.get("epsTrailingTwelveMonths")))
                    .epsForward(toBigDecimal(quote.get("epsForward")))
                    .bookValue(toBigDecimal(quote.get("bookValue")))
                    .priceToBook(toBigDecimal(quote.get("priceToBook")))
                    .dividendYield(toBigDecimal(quote.get("dividendYield")))
                    .roe(toBigDecimal(quote.get("returnOnEquity")))
                    .debtToEquity(toBigDecimal(quote.get("debtToEquity")))
                    .profitMargin(toBigDecimal(quote.get("profitMargins")))
                    .marketCap(toLongObject(quote.get("marketCap")))
                    .revenueTtm(toLongObject(quote.get("totalRevenue")))
                    .sector((String) quote.get("sector"))
                    .industry((String) quote.get("industry"))
                    .businessSummary((String) quote.get("longBusinessSummary"))
                    .sharesOutstanding(toLongObject(quote.get("sharesOutstanding")))
                    .beta(toBigDecimal(quote.get("beta")))
                    .fiftyTwoWeekHigh(toBigDecimal(quote.get("fiftyTwoWeekHigh")))
                    .fiftyTwoWeekLow(toBigDecimal(quote.get("fiftyTwoWeekLow")))
                    .fetchedDate(LocalDate.now())
                    .build();

        } catch (Exception e) {
            log.warn("Yahoo Finance fundamentals fetch failed for {}: {}", yahooSymbol, e.getMessage());
            return null;
        }
    }
}
