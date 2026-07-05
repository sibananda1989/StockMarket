package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.FiiDiiData;
import org.example.repository.FiiDiiDataRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@Slf4j
public class FiiDiiService {

    private static final long NSE_RATE_LIMIT_MS = 2000L; // 1 request per 2 seconds

    private final FiiDiiDataRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ApiRateLimiter rateLimiter;

    private static final String NSE_BASE = "https://www.nseindia.com";
    private static final String FII_DII_API = "https://www.nseindia.com/api/fiidiiTradeReact";
    private static final DateTimeFormatter NSE_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    public FiiDiiService(FiiDiiDataRepository repository, ObjectMapper objectMapper, ApiRateLimiter rateLimiter) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public FiiDiiData getLatest() {
        return repository.findTopByOrderByDateDesc().orElse(null);
    }

    public String getSentimentSummary() {
        FiiDiiData latest = getLatest();
        if (latest == null) return "No FII/DII data available";

        BigDecimal combined = BigDecimal.ZERO;
        if (latest.getFiiNet() != null) combined = combined.add(latest.getFiiNet());
        if (latest.getDiiNet() != null) combined = combined.add(latest.getDiiNet());

        double val = combined.doubleValue();
        if (val > 500) return "Bullish";
        if (val > 200) return "Mildly Bullish";
        if (val < -500) return "Bearish";
        if (val < -200) return "Mildly Bearish";
        return "Neutral";
    }

    public int getScoreAdjustment() {
        FiiDiiData latest = getLatest();
        if (latest == null) return 0;

        BigDecimal combined = BigDecimal.ZERO;
        if (latest.getFiiNet() != null) combined = combined.add(latest.getFiiNet());
        if (latest.getDiiNet() != null) combined = combined.add(latest.getDiiNet());

        double val = combined.doubleValue();
        if (val > 500) return 1;
        if (val < -500) return -1;
        return 0;
    }

    public FiiDiiData fetchAndSave() {
        try {
            rateLimiter.acquire("nse-fiidii", NSE_RATE_LIMIT_MS);
            HttpRequest homeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(NSE_BASE))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();
            httpClient.send(homeRequest, HttpResponse.BodyHandlers.discarding());

            Thread.sleep(1500);

            HttpRequest dataRequest = HttpRequest.newBuilder()
                    .uri(URI.create(FII_DII_API))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://www.nseindia.com/reports/fii-dii")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(dataRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("NSE FII/DII API returned HTTP {}", response.statusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.size() < 2) {
                log.warn("Unexpected FII/DII response format");
                return null;
            }

            JsonNode fiiNode = null;
            JsonNode diiNode = null;
            for (JsonNode node : root) {
                String category = node.get("category").asText();
                if ("FII/FPI".equals(category)) fiiNode = node;
                else if ("DII".equals(category)) diiNode = node;
            }

            if (fiiNode == null || diiNode == null) {
                log.warn("Could not find FII and DII entries in response");
                return null;
            }

            LocalDate date = LocalDate.parse(fiiNode.get("date").asText(), NSE_DATE_FMT);

            FiiDiiData data = repository.findByDate(date).orElse(new FiiDiiData());
            data.setDate(date);
            data.setFiiBuy(parseBigDecimal(fiiNode.get("buyValue")));
            data.setFiiSell(parseBigDecimal(fiiNode.get("sellValue")));
            data.setFiiNet(parseBigDecimal(fiiNode.get("netValue")));
            data.setDiiBuy(parseBigDecimal(diiNode.get("buyValue")));
            data.setDiiSell(parseBigDecimal(diiNode.get("sellValue")));
            data.setDiiNet(parseBigDecimal(diiNode.get("netValue")));

            repository.save(data);
            log.info("FII/DII data saved for date: {}", date);
            return data;

        } catch (Exception e) {
            log.error("Failed to fetch FII/DII data from NSE: {}", e.getMessage(), e);
            return null;
        }
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "UTC")
    public void scheduledFetch() {
        log.info("Scheduled FII/DII data fetch from NSE");
        fetchAndSave();
    }

    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) return BigDecimal.ZERO;
        return new BigDecimal(node.asText());
    }
}
