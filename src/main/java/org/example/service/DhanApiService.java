package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.DhanCandleResponseDTO;
import org.example.dto.DhanHoldingDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class DhanApiService {

    private static final String DHAN_API_BASE_URL = "https://api.dhan.co";
    private static final Set<String> ALLOWED_EXCHANGE_SEGMENTS = Set.of("NSE_EQ", "BSE_EQ");
    private static final int SECURITY_ID_MAX_LENGTH = 20;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private static final long DHAN_RATE_LIMIT_MS = 200L;

    private final WebClient webClient;
    private final ApiRateLimiter rateLimiter;

    public DhanApiService(@Value("${dhan.api.access-token}") String accessToken,
                          ApiRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Dhan API access token must not be null or empty");
        }
        this.webClient = WebClient.builder()
                .baseUrl(DHAN_API_BASE_URL)
                .defaultHeader("access-token", accessToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public List<DhanHoldingDTO> getHoldings() {
        rateLimiter.acquire("dhan", DHAN_RATE_LIMIT_MS);
        log.info("Fetching holdings from Dhan API");
        List<DhanHoldingDTO> holdings = webClient.get()
                .uri("/v2/holdings")
                .retrieve()
                .bodyToFlux(DhanHoldingDTO.class)
                .collectList()
                .block();
        log.info("Fetched {} holdings from Dhan", holdings != null ? holdings.size() : 0);
        return holdings;
    }

    public DhanCandleResponseDTO getDailyCandles(String securityId, String exchangeSegment, int days) {
        // CWE-20: validate inputs before use
        if (securityId == null || securityId.isBlank()) {
            throw new IllegalArgumentException("securityId must not be null or empty");
        }
        if (securityId.length() > SECURITY_ID_MAX_LENGTH || !securityId.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("securityId contains invalid characters or exceeds max length");
        }
        // CWE-918: validate exchangeSegment against allowlist to prevent SSRF via request body injection
        if (!ALLOWED_EXCHANGE_SEGMENTS.contains(exchangeSegment)) {
            throw new IllegalArgumentException("exchangeSegment not allowed: " + exchangeSegment);
        }

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(days);

        Map<String, Object> requestBody = Map.of(
                "securityId", securityId,
                "exchangeSegment", exchangeSegment,
                "instrument", "EQUITY",
                "expiryCode", 0,
                "oi", false,
                "fromDate", fromDate.format(DATE_FMT),
                "toDate", toDate.format(DATE_FMT)
        );

        log.info("Fetching candles for securityId={} from {} to {}", securityId, fromDate, toDate);

        rateLimiter.acquire("dhan", DHAN_RATE_LIMIT_MS);

        return webClient.post()
                .uri("/v2/charts/historical")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(DhanCandleResponseDTO.class)
                .block();
    }
}
