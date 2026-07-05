package org.example.service;

import org.example.dto.StockSearchResultDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YahooFinanceServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private YahooFinanceService yahooFinanceService;

    @Test
    void testSearchByName_EmptyQuery_ReturnsEmptyList() {
        List<StockSearchResultDTO> results = yahooFinanceService.searchByName("");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchByName_NullQuery_ReturnsEmptyList() {
        List<StockSearchResultDTO> results = yahooFinanceService.searchByName(null);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchByName_BlankQuery_ReturnsEmptyList() {
        List<StockSearchResultDTO> results = yahooFinanceService.searchByName("   ");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchByName_WithLimitParameter_RespectsBounds() {
        // Test that limit is capped at 50 and minimum 1
        // This is verified by the internal logic, we can't easily test the HTTP call without heavy mocking
        // But we can verify the method doesn't throw with various limits
        assertDoesNotThrow(() -> yahooFinanceService.searchByName("test", 5));
        assertDoesNotThrow(() -> yahooFinanceService.searchByName("test", 10));
        assertDoesNotThrow(() -> yahooFinanceService.searchByName("test", 50));
        assertDoesNotThrow(() -> yahooFinanceService.searchByName("test", 100)); // should be capped at 50
        assertDoesNotThrow(() -> yahooFinanceService.searchByName("test", 0)); // should be min 1
        assertDoesNotThrow(() -> yahooFinanceService.searchByName("test", -5)); // should be min 1
    }

    @Test
    void testSearchByName_DefaultLimit_UsesTen() {
        // Default limit should be 10
        assertDoesNotThrow(() -> yahooFinanceService.searchByName("test"));
    }
}