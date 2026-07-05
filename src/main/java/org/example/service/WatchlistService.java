package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.StockDTO;
import org.example.dto.WatchlistDTO;
import org.example.dto.WatchlistDetailDTO;
import org.example.entity.Stock;
import org.example.entity.Watchlist;
import org.example.entity.WatchlistItem;
import org.example.exception.StockNotFoundException;
import org.example.repository.DailyPriceRepository;
import org.example.repository.WatchlistItemRepository;
import org.example.repository.WatchlistRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final StockService stockService;
    private final StockSyncService stockSyncService;
    private final DailyPriceRepository dailyPriceRepository;

    private Executor syncExecutor;

    @Autowired
    @Qualifier("syncExecutor")
    public void setSyncExecutor(Executor syncExecutor) {
        this.syncExecutor = syncExecutor;
    }

    // ─── Watchlist CRUD ─────────────────────────────────────────────────────

    public WatchlistDTO createWatchlist(String name, String description) {
        if (watchlistRepository.existsByName(name)) {
            throw new IllegalArgumentException("Watchlist with name '" + name + "' already exists");
        }
        Watchlist watchlist = new Watchlist(name, description);
        watchlist = watchlistRepository.save(watchlist);
        return toDTO(watchlist);
    }

    public WatchlistDTO renameWatchlist(Long id, String newName, String description) {
        Watchlist watchlist = getWatchlistById(id);
        if (!watchlist.getName().equals(newName) && watchlistRepository.existsByName(newName)) {
            throw new IllegalArgumentException("Watchlist with name '" + newName + "' already exists");
        }
        watchlist.setName(newName);
        if (description != null) {
            watchlist.setDescription(description);
        }
        watchlist = watchlistRepository.save(watchlist);
        return toDTO(watchlist);
    }

    public void deleteWatchlist(Long id) {
        Watchlist watchlist = getWatchlistById(id);
        watchlistRepository.delete(watchlist);
    }

    @Transactional(readOnly = true)
    public List<WatchlistDTO> getAllWatchlists() {
        return watchlistRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WatchlistDetailDTO getWatchlistDetail(Long id) {
        Watchlist watchlist = getWatchlistById(id);
        List<WatchlistItem> items = watchlistItemRepository.findByWatchlistIdOrderByAddedAtAsc(id);
        List<StockDTO> stockDTOs = items.stream()
                .map(item -> {
                    StockDTO dto = stockService.getStockDTO(item.getStock());
                    dto.setAddedAt(item.getAddedAt());
                    dto.setRecordCount(dailyPriceRepository.countByStockId(item.getStock().getId()));
                    return dto;
                })
                .collect(Collectors.toList());
        return toDetailDTO(watchlist, stockDTOs);
    }

    // ─── Watchlist Items ────────────────────────────────────────────────────

    public WatchlistItem addStockToWatchlist(Long watchlistId, Long stockId) {
        if (watchlistItemRepository.existsByWatchlistIdAndStockId(watchlistId, stockId)) {
            throw new IllegalArgumentException("Stock is already in this watchlist");
        }
        Watchlist watchlist = getWatchlistById(watchlistId);
        Stock stock = stockService.getStockById(stockId);
        // Reset to watched-only if it was a holding
        stockService.resetPortfolioToWatched(stockId);
        WatchlistItem item = new WatchlistItem(watchlist, stock);
        WatchlistItem saved = watchlistItemRepository.save(item);

        // Trigger background history sync
        triggerBackgroundSync(stockId);

        return saved;
    }

    public void removeStockFromWatchlist(Long watchlistId, Long stockId) {
        WatchlistItem item = watchlistItemRepository.findByWatchlistIdAndStockId(watchlistId, stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock is not in this watchlist"));
        watchlistItemRepository.delete(item);
    }

    @Transactional
    public List<WatchlistItem> addBatchStocks(Long watchlistId, List<Long> stockIds) {
        Watchlist watchlist = getWatchlistById(watchlistId);
        List<Stock> stocks = stockService.getStocksByIds(stockIds);
        // Reset each stock from holding to watched-only
        stocks.forEach(stock -> stockService.resetPortfolioToWatched(stock.getId()));
        List<WatchlistItem> saved = stocks.stream()
                .filter(stock -> !watchlistItemRepository.existsByWatchlistIdAndStockId(watchlistId, stock.getId()))
                .map(stock -> watchlistItemRepository.save(new WatchlistItem(watchlist, stock)))
                .collect(Collectors.toList());

        // Trigger background history sync for each added stock
        saved.forEach(item -> triggerBackgroundSync(item.getStock().getId()));

        return saved;
    }

    // ─── Queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WatchlistDTO> getWatchlistsForStock(Long stockId) {
        return watchlistItemRepository.findAllByStockId(stockId).stream()
                .map(item -> toDTO(item.getWatchlist()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getStockIdsInWatchlist(Long watchlistId) {
        return watchlistItemRepository.findByWatchlistIdOrderByAddedAtAsc(watchlistId).stream()
                .map(item -> item.getStock().getId())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getWatchlistItemCount(Long watchlistId) {
        return watchlistItemRepository.countByWatchlistId(watchlistId);
    }

    // ─── Background Sync ────────────────────────────────────────────────────

    private void triggerBackgroundSync(Long stockId) {
        syncExecutor.execute(() -> {
            try {
                log.info("Starting background history sync for stock {}", stockId);
                stockSyncService.syncStockHistory(stockId, 90);
                log.info("Background history sync completed for stock {}", stockId);
            } catch (Exception e) {
                log.warn("Background sync failed for stock {}: {}", stockId, e.getMessage());
            }
        });
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Watchlist getWatchlistById(Long id) {
        return watchlistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found with id: " + id));
    }

    private WatchlistDTO toDTO(Watchlist watchlist) {
        WatchlistDTO dto = new WatchlistDTO();
        dto.setId(watchlist.getId());
        dto.setName(watchlist.getName());
        dto.setDescription(watchlist.getDescription());
        dto.setCreatedAt(watchlist.getCreatedAt());
        dto.setUpdatedAt(watchlist.getUpdatedAt());
        dto.setItemCount(watchlistItemRepository.countByWatchlistId(watchlist.getId()));
        return dto;
    }

    private WatchlistDetailDTO toDetailDTO(Watchlist watchlist, List<StockDTO> stocks) {
        WatchlistDetailDTO dto = new WatchlistDetailDTO();
        dto.setId(watchlist.getId());
        dto.setName(watchlist.getName());
        dto.setDescription(watchlist.getDescription());
        dto.setCreatedAt(watchlist.getCreatedAt());
        dto.setUpdatedAt(watchlist.getUpdatedAt());
        dto.setItemCount(stocks.size());
        dto.setStocks(stocks);
        return dto;
    }
}
