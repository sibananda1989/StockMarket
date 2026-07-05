package org.example.service;

import org.example.entity.Stock;
import org.example.repository.StockRepository;
import org.example.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockService stockService;

    @Test
    void testGetAllStocks() {
        // Given
        Stock stock1 = new Stock("RELIANCE", "Reliance Industries", "Energy");
        stock1.setId(1L);
        Stock stock2 = new Stock("TCS", "Tata Consultancy Services", "IT");
        stock2.setId(2L);
        when(stockRepository.findAll()).thenReturn(Arrays.asList(stock1, stock2));

        // When
        List<Stock> stocks = stockService.getAllStocks();

        // Then
        assertEquals(2, stocks.size());
        assertEquals("RELIANCE", stocks.get(0).getSymbol());
        assertEquals("TCS", stocks.get(1).getSymbol());
        verify(stockRepository, times(1)).findAll();
    }

     @Test
    void testGetStockById() {
        // Given
        Stock stock = new Stock("RELIANCE", "Reliance Industries", "Energy");
        stock.setId(1L);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));

        // When
        Stock result = stockService.getStockById(1L);

        // Then
        assertNotNull(result);
        assertEquals("RELIANCE", result.getSymbol());
        verify(stockRepository, times(1)).findById(1L);
    }

    @Test
    void testGetStockBySymbol() {
        // Given
        Stock stock = new Stock("RELIANCE", "Reliance Industries", "Energy");
        stock.setId(1L);
        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(stock));

        // When
        Stock result = stockService.getStockBySymbol("RELIANCE");

        // Then
        assertNotNull(result);
        assertEquals("RELIANCE", result.getSymbol());
        verify(stockRepository, times(1)).findBySymbol("RELIANCE");
    }

    @Test
    void testAddStock() {
        // Given
        Stock stock = new Stock("RELIANCE", "Reliance Industries", "Energy");
        Stock savedStock = new Stock("RELIANCE", "Reliance Industries", "Energy");
        savedStock.setId(1L);
        when(stockRepository.save(any(Stock.class))).thenReturn(savedStock);

        // When
        Stock result = stockService.addStock(stock);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("RELIANCE", result.getSymbol());
        verify(stockRepository, times(1)).save(any(Stock.class));
    }
}