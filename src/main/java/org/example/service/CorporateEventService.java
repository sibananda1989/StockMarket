package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.CorporateEvent;
import org.example.repository.CorporateEventRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class CorporateEventService {

    private static final long NSE_RATE_LIMIT_MS = 2000L; // 1 request per 2 seconds

    private final CorporateEventRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ApiRateLimiter rateLimiter;

    private static final String NSE_BASE = "https://www.nseindia.com";
    private static final String EVENT_API = "https://www.nseindia.com/api/event-calendar";
    private static final DateTimeFormatter NSE_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final int EVENT_WINDOW_DAYS = 5;

    public CorporateEventService(CorporateEventRepository repository, ObjectMapper objectMapper, ApiRateLimiter rateLimiter) {
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

    public List<CorporateEvent> getEventsForSymbol(String symbol) {
        LocalDate today = LocalDate.now();
        return repository.findBySymbolAndEventDateBetweenOrderByEventDateAsc(
                symbol.toUpperCase(), today, today.plusDays(EVENT_WINDOW_DAYS));
    }

    public boolean hasEventRisk(String symbol, LocalDate signalDate) {
        return repository.existsBySymbolAndEventDateBetween(
                symbol.toUpperCase(), signalDate, signalDate.plusDays(EVENT_WINDOW_DAYS));
    }

    @CacheEvict(cacheNames = "signals", allEntries = true)
    public int fetchAndSaveEvents() {
        int count = 0;
        try {
            rateLimiter.acquire("nse-events", NSE_RATE_LIMIT_MS);
            HttpRequest homeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(NSE_BASE))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();
            httpClient.send(homeRequest, HttpResponse.BodyHandlers.discarding());

            Thread.sleep(1500);

            HttpRequest dataRequest = HttpRequest.newBuilder()
                    .uri(URI.create(EVENT_API))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://www.nseindia.com")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(dataRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("NSE event calendar returned HTTP {}", response.statusCode());
                return 0;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                log.warn("Unexpected event calendar response format");
                return 0;
            }

            for (JsonNode node : root) {
                String symbol = node.get("symbol").asText();
                String purpose = node.get("purpose").asText();
                LocalDate eventDate = LocalDate.parse(node.get("date").asText(), NSE_DATE_FMT);

                if (repository.existsBySymbolAndEventDateAndPurpose(symbol, eventDate, purpose)) {
                    continue;
                }

                CorporateEvent event = new CorporateEvent();
                event.setSymbol(symbol);
                event.setCompanyName(node.has("company") ? node.get("company").asText() : null);
                event.setPurpose(purpose);
                event.setBmDesc(node.has("bm_desc") ? node.get("bm_desc").asText() : null);
                event.setEventDate(eventDate);
                repository.save(event);
                count++;
            }

            log.info("Saved {} new corporate events from NSE calendar", count);

        } catch (Exception e) {
            log.error("Failed to fetch events from NSE calendar: {}", e.getMessage(), e);
        }
        return count;
    }

    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "UTC")
    @CacheEvict(cacheNames = "signals", allEntries = true)
    public void scheduledRefresh() {
        log.info("Scheduled corporate events refresh from NSE calendar");
        fetchAndSaveEvents();
    }
}
