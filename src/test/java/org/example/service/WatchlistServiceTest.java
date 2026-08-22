package org.example.service;

import org.example.dto.StockDTO;
import org.example.dto.WatchlistDTO;
import org.example.dto.WatchlistDetailDTO;
import org.example.entity.DailyPrice;
import org.example.entity.IndicatorType;
import org.example.entity.Stock;
import org.example.entity.TechnicalIndicator;
import org.example.entity.Watchlist;
import org.example.entity.WatchlistItem;
import org.example.repository.DailyPriceRepository;
import org.example.repository.TechnicalIndicatorRepository;
import org.example.repository.WatchlistItemRepository;
import org.example.repository.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private WatchlistItemRepository watchlistItemRepository;

    @Mock
    private StockService stockService;

    @Mock
    private StockSyncService stockSyncService;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private TechnicalIndicatorRepository technicalIndicatorRepository;

    @Mock
    private Executor syncExecutor;

    @InjectMocks
    private WatchlistService watchlistService;

    @BeforeEach
    void setUp() {
        watchlistService.setSyncExecutor(syncExecutor);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Watchlist createWatchlist(Long id, String name, String description) {
        Watchlist wl = new Watchlist(name, description);
        wl.setId(id);
        return wl;
    }

    private Stock createStock(Long id, String symbol) {
        Stock stock = new Stock(symbol, symbol + " Inc.", "Technology", null);
        stock.setId(id);
        stock.setQuantity(0);
        return stock;
    }

    private StockDTO createStockDTO(Long id, String symbol) {
        StockDTO dto = new StockDTO();
        dto.setId(id);
        dto.setSymbol(symbol);
        dto.setName(symbol + " Inc.");
        dto.setSector("Technology");
        dto.setIndustry(null);
        return dto;
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    void testCreateWatchlist() {
        // Given
        String name = "Tech Stocks";
        String description = "Technology companies to watch";
        Watchlist saved = createWatchlist(1L, name, description);
        when(watchlistRepository.existsByName(name)).thenReturn(false);
        when(watchlistRepository.save(any(Watchlist.class))).thenReturn(saved);
        when(watchlistItemRepository.countByWatchlistId(1L)).thenReturn(0L);

        // When
        WatchlistDTO result = watchlistService.createWatchlist(name, description);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(name, result.getName());
        assertEquals(description, result.getDescription());
        assertEquals(0, result.getItemCount());
        verify(watchlistRepository, times(1)).existsByName(name);
        verify(watchlistRepository, times(1)).save(any(Watchlist.class));
    }

    @Test
    void testCreateWatchlistDuplicateName() {
        // Given
        String name = "Tech Stocks";
        when(watchlistRepository.existsByName(name)).thenReturn(true);

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.createWatchlist(name, "desc"));
        verify(watchlistRepository, never()).save(any());
    }

    @Test
    void testRenameWatchlist() {
        // Given
        Watchlist existing = createWatchlist(1L, "Old Name", "Desc");
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(watchlistRepository.save(any(Watchlist.class))).thenReturn(existing);
        when(watchlistItemRepository.countByWatchlistId(1L)).thenReturn(2L);

        // When
        WatchlistDTO result = watchlistService.renameWatchlist(1L, "New Name", null);

        // Then
        assertNotNull(result);
        assertEquals("New Name", existing.getName());
        assertEquals(2, result.getItemCount());
        verify(watchlistRepository, times(1)).save(existing);
    }

    @Test
    void testDeleteWatchlist() {
        // Given
        Watchlist existing = createWatchlist(1L, "Tech", "desc");
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(existing));

        // When
        watchlistService.deleteWatchlist(1L);

        // Then
        verify(watchlistRepository, times(1)).delete(existing);
    }

    @Test
    void testGetAllWatchlists() {
        // Given
        List<Watchlist> watchlists = List.of(
                createWatchlist(1L, "A", null),
                createWatchlist(2L, "B", null));
        when(watchlistRepository.findAllByOrderByNameAsc()).thenReturn(watchlists);
        when(watchlistItemRepository.countByWatchlistId(1L)).thenReturn(3L);
        when(watchlistItemRepository.countByWatchlistId(2L)).thenReturn(5L);

        // When
        List<WatchlistDTO> results = watchlistService.getAllWatchlists();

        // Then
        assertEquals(2, results.size());
        assertEquals(3, results.get(0).getItemCount());
        assertEquals(5, results.get(1).getItemCount());
        verify(watchlistRepository, times(1)).findAllByOrderByNameAsc();
    }

    @Test
    void testGetWatchlistDetail_UsesBatchQueries() {
        Watchlist watchlist = createWatchlist(1L, "Tech", "Tech stocks");
        Stock stock1 = createStock(10L, "AAPL");
        Stock stock2 = createStock(11L, "GOOGL");
        WatchlistItem item1 = new WatchlistItem(watchlist, stock1);
        WatchlistItem item2 = new WatchlistItem(watchlist, stock2);

        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));
        when(watchlistItemRepository.findByWatchlistIdOrderByAddedAtAsc(1L))
                .thenReturn(List.of(item1, item2));

        // Batch mocks
        DailyPrice dp1 = new DailyPrice(); dp1.setStock(stock1); dp1.setClosingPrice(new BigDecimal("150.00"));
        DailyPrice dp2 = new DailyPrice(); dp2.setStock(stock2); dp2.setClosingPrice(new BigDecimal("280.00"));
        when(dailyPriceRepository.findLatestPriceForStockIds(List.of(10L, 11L)))
                .thenReturn(List.of(dp1, dp2));

        TechnicalIndicator rsi1 = new TechnicalIndicator(); rsi1.setStock(stock1); rsi1.setValue(new BigDecimal("62.5"));
        TechnicalIndicator rsi2 = new TechnicalIndicator(); rsi2.setStock(stock2); rsi2.setValue(new BigDecimal("45.0"));
        when(technicalIndicatorRepository.findLatestByStockIdsAndTypes(
                List.of(10L, 11L), List.of(IndicatorType.RSI)))
                .thenReturn(List.of(rsi1, rsi2));

        when(dailyPriceRepository.countByStockIds(List.of(10L, 11L)))
                .thenReturn(List.of(new Object[]{10L, 45L}, new Object[]{11L, 30L}));

        when(stockService.getStockDTO(eq(stock1), eq(new BigDecimal("150.00")), eq(new BigDecimal("62.5"))))
                .thenReturn(createStockDTO(10L, "AAPL"));
        when(stockService.getStockDTO(eq(stock2), eq(new BigDecimal("280.00")), eq(new BigDecimal("45.0"))))
                .thenReturn(createStockDTO(11L, "GOOGL"));

        WatchlistDetailDTO result = watchlistService.getWatchlistDetail(1L);

        assertNotNull(result);
        assertEquals("Tech", result.getName());
        assertEquals(2, result.getItemCount());
        assertEquals(2, result.getStocks().size());
        assertEquals("AAPL", result.getStocks().get(0).getSymbol());
        assertEquals("GOOGL", result.getStocks().get(1).getSymbol());
        verify(dailyPriceRepository, never()).countByStockId(anyLong());
        verify(stockService, never()).getStockDTO(any(Stock.class));
    }

    @Test
    void testAddStockToWatchlist() {
        // Given
        Watchlist watchlist = createWatchlist(1L, "Tech", null);
        Stock stock = createStock(10L, "AAPL");
        when(watchlistItemRepository.existsByWatchlistIdAndStockId(1L, 10L)).thenReturn(false);
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));
        when(stockService.getStockById(10L)).thenReturn(stock);
        when(watchlistItemRepository.save(any(WatchlistItem.class)))
                .thenReturn(new WatchlistItem(watchlist, stock));
        // Mock syncExecutor to run the Runnable synchronously
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(syncExecutor).execute(any(Runnable.class));

        // When
        WatchlistItem item = watchlistService.addStockToWatchlist(1L, 10L);

        // Then
        assertNotNull(item);
        assertEquals(watchlist, item.getWatchlist());
        assertEquals(stock, item.getStock());
        verify(watchlistItemRepository, times(1)).save(any(WatchlistItem.class));
    }

    @Test
    void testAddDuplicateStockToWatchlist() {
        // Given
        when(watchlistItemRepository.existsByWatchlistIdAndStockId(1L, 10L)).thenReturn(true);

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.addStockToWatchlist(1L, 10L));
        verify(watchlistItemRepository, never()).save(any());
    }

    @Test
    void testRemoveStockFromWatchlist() {
        // Given
        Watchlist watchlist = createWatchlist(1L, "Tech", null);
        Stock stock = createStock(10L, "AAPL");
        WatchlistItem item = new WatchlistItem(watchlist, stock);
        when(watchlistItemRepository.findByWatchlistIdAndStockId(1L, 10L))
                .thenReturn(Optional.of(item));

        // When
        watchlistService.removeStockFromWatchlist(1L, 10L);

        // Then
        verify(watchlistItemRepository, times(1)).delete(item);
    }

    @Test
    void testRemoveNonexistentStockFromWatchlist() {
        // Given
        when(watchlistItemRepository.findByWatchlistIdAndStockId(1L, 10L))
                .thenReturn(Optional.empty());

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.removeStockFromWatchlist(1L, 10L));
        verify(watchlistItemRepository, never()).delete(any());
    }

    @Test
    void testGetWatchlistsForStock() {
        // Given
        Watchlist wl1 = createWatchlist(1L, "Tech", null);
        Watchlist wl2 = createWatchlist(2L, "Favorites", "desc");
        Stock stock = createStock(10L, "AAPL");
        WatchlistItem item1 = new WatchlistItem(wl1, stock);
        WatchlistItem item2 = new WatchlistItem(wl2, stock);
        when(watchlistItemRepository.findAllByStockId(10L))
                .thenReturn(List.of(item1, item2));
        when(watchlistItemRepository.countByWatchlistId(1L)).thenReturn(1L);
        when(watchlistItemRepository.countByWatchlistId(2L)).thenReturn(1L);

        // When
        List<WatchlistDTO> results = watchlistService.getWatchlistsForStock(10L);

        // Then
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(w -> w.getName().equals("Tech")));
        assertTrue(results.stream().anyMatch(w -> w.getName().equals("Favorites")));
    }
}
