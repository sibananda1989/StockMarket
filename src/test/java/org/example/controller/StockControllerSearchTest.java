package org.example.controller;

import org.example.dto.StockSearchResultDTO;
import org.example.service.YahooFinanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockControllerSearchTest {

    @Mock
    private YahooFinanceService yahooFinanceService;

    @InjectMocks
    private StockController stockController;

    @Test
    void testSearchStocksByName_ValidRequest_ReturnsSuccess() {
        // Given
        String query = "reli";
        List<StockSearchResultDTO> mockResults = Arrays.asList(
                createMockResult("RELIANCE.NS", "Reliance Industries Ltd.", "NSE", "Energy"),
                createMockResult("RELIANCE.BO", "Reliance Industries", "BSE", "Energy")
        );
        when(yahooFinanceService.searchByName(eq(query), eq(10))).thenReturn(mockResults);

        // When
        ResponseEntity<?> response = stockController.searchStocksByName(query, 10);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
    }

    @Test
    void testSearchStocksByName_CustomLimit_UsesProvidedLimit() {
        // Given
        String query = "tata";
        int customLimit = 5;
        when(yahooFinanceService.searchByName(eq(query), eq(customLimit))).thenReturn(List.of());

        // When
        stockController.searchStocksByName(query, customLimit);

        // Then
        verify(yahooFinanceService).searchByName(query, customLimit);
    }

    @Test
    void testSearchStocksByName_DefaultLimit_UsesTen() {
        // Given
        String query = "tech";
        when(yahooFinanceService.searchByName(eq(query), eq(10))).thenReturn(List.of());

        // When
        stockController.searchStocksByName(query, 10);

        // Then
        verify(yahooFinanceService).searchByName(query, 10);
    }

    @Test
    void testSearchStocksByName_EmptyName_ReturnsEmptyList() {
        // Given
        when(yahooFinanceService.searchByName(eq(""), eq(10))).thenReturn(List.of());

        // When
        ResponseEntity<?> response = stockController.searchStocksByName("", 10);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        verify(yahooFinanceService).searchByName("", 10);
    }

    @Test
    void testSearchStocksByName_NullResponseFromService_HandlesGracefully() {
        // Given
        when(yahooFinanceService.searchByName(anyString(), anyInt())).thenReturn(null);

        // When
        ResponseEntity<?> response = stockController.searchStocksByName("bank", 10);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
    }

    private StockSearchResultDTO createMockResult(String symbol, String name, String exchange, String sector) {
        StockSearchResultDTO dto = new StockSearchResultDTO();
        dto.setSymbol(symbol);
        dto.setName(name);
        dto.setExchange(exchange);
        dto.setSector(sector);
        dto.setIndustry("Industry");
        dto.setQuoteType("EQUITY");
        dto.setYahooFinance(true);
        return dto;
    }
}