package org.example.service.institutional;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Slf4j
public class NseSessionManager {

    private static final String NSE_HOMEPAGE = "https://www.nseindia.com";
    private static final String NSE_OPTION_CHAIN = "https://www.nseindia.com/option-chain";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private volatile boolean sessionReady = false;

    public NseSessionManager() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * Ensures a valid NSE session exists by refreshing cookies if needed.
     * Protected by a circuit breaker to prevent repeated failures from hanging threads.
     */
    @CircuitBreaker(name = "nseSession", fallbackMethod = "sessionFallback")
    public synchronized void ensureSession() {
        if (sessionReady) {
            return; // Existing session is likely still valid
        }
        doRefreshSession();
    }

    /**
     * Forcefully refreshes the NSE session by hitting the homepage and option chain.
     */
    public synchronized void refreshSession() {
        try {
            doRefreshSession();
        } catch (Exception e) {
            log.warn("Failed to acquire NSE session: {}", e.getMessage());
        }
    }

    /**
     * Internal session refresh - not circuit-broken so refreshSession() is not affected.
     */
    private void doRefreshSession() {
        try {
            log.info("Acquiring new NSE session...");

            HttpRequest homeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(NSE_HOMEPAGE))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .GET()
                    .build();
            httpClient.send(homeRequest, HttpResponse.BodyHandlers.discarding());

            Thread.sleep(1500);

            HttpRequest optionChainRequest = HttpRequest.newBuilder()
                    .uri(URI.create(NSE_OPTION_CHAIN))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .GET()
                    .build();
            httpClient.send(optionChainRequest, HttpResponse.BodyHandlers.discarding());

            sessionReady = true;
            log.info("NSE session acquired successfully.");
        } catch (Exception e) {
            sessionReady = false;
            log.warn("Failed to acquire NSE session: {}", e.getMessage());
            throw new RuntimeException("NSE session acquisition failed", e);
        }
    }

    /**
     * Fallback for when the circuit breaker is open.
     */
    private void sessionFallback(Exception e) {
        sessionReady = false;
        log.warn("NSE session refresh blocked by circuit breaker: {}", e.getMessage());
    }

    /**
     * Returns the configured HttpClient with attached cookies and connect timeout.
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Creates a base HTTP GET request builder with standard browser headers
     * and a 30-second request timeout.
     */
    public HttpRequest.Builder createRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", "https://www.nseindia.com/get-quotes/equity?symbol=")
                .GET();
    }

    public boolean isSessionReady() {
        return sessionReady;
    }
}
