package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.DailyPrice;
import org.example.entity.Portfolio;
import org.example.entity.PortfolioHolding;
import org.example.entity.Stock;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioRepository;
import org.example.repository.StockRepository;
import org.example.service.PortfolioSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final PortfolioSnapshotService snapshotService;

    // ═══════════════════════════════════════════════════════════════════
    // Portfolio CRUD
    // ═══════════════════════════════════════════════════════════════════

    public PortfolioDTO createPortfolio(String name, String description) {
        if (portfolioRepository.existsByName(name)) {
            throw new IllegalArgumentException("Portfolio with name '" + name + "' already exists");
        }
        Portfolio portfolio = new Portfolio(name, description, false);
        portfolio = portfolioRepository.save(portfolio);
        return toPortfolioDTO(portfolio);
    }

    @Transactional(readOnly = true)
    public List<PortfolioDTO> getAllPortfolios() {
        return portfolioRepository.findAll().stream()
                .map(this::toPortfolioDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PortfolioDTO getPortfolio(Long id) {
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + id));
        PortfolioDTO dto = toPortfolioDTO(portfolio);
        dto.setHoldings(getHoldingsInternal(id));
        return dto;
    }

    public void deletePortfolio(Long id) {
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + id));
        if (portfolio.isDefault()) {
            throw new IllegalArgumentException("Cannot delete the default portfolio");
        }
        holdingRepository.deleteByPortfolioId(id);
        portfolioRepository.delete(portfolio);
    }

    public PortfolioDTO updatePortfolio(Long id, String name, String description) {
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + id));
        if (name != null && !name.isBlank()) {
            // Check uniqueness if name changed
            if (!portfolio.getName().equals(name) && portfolioRepository.existsByName(name)) {
                throw new IllegalArgumentException("Portfolio with name '" + name + "' already exists");
            }
            portfolio.setName(name);
        }
        if (description != null) {
            portfolio.setDescription(description);
        }
        portfolio = portfolioRepository.save(portfolio);
        return toPortfolioDTO(portfolio);
    }

    @Transactional(readOnly = true)
    public Portfolio getDefaultPortfolio() {
        return portfolioRepository.findByIsDefaultTrue()
                .orElseGet(() -> {
                    Portfolio defaultPort = new Portfolio("Default Portfolio",
                            "Auto-created default portfolio", true);
                    return portfolioRepository.save(defaultPort);
                });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Holding Management
    // ═══════════════════════════════════════════════════════════════════

    public HoldingDTO addHolding(Long portfolioId, Long stockId, Integer quantity, BigDecimal avgPrice) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + stockId));

        if (holdingRepository.existsByPortfolioIdAndStockId(portfolioId, stockId)) {
            throw new IllegalArgumentException("Stock " + stock.getSymbol() + " is already in portfolio '" + portfolio.getName() + "'");
        }

        PortfolioHolding holding = new PortfolioHolding();
        holding.setPortfolio(portfolio);
        holding.setStock(stock);
        holding.setQuantity(quantity);
        holding.setAvgPrice(avgPrice);
        holding = holdingRepository.save(holding);

        syncStockFromHolding(stock, quantity, avgPrice);
        snapshotService.saveOrUpdate(stock, LocalDate.now(), portfolio);
        return computeHoldingDTO(holding);
    }

    public HoldingDTO updateHolding(Long portfolioId, Long holdingId, Integer quantity, BigDecimal avgPrice) {
        PortfolioHolding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding not found: " + holdingId));

        if (!holding.getPortfolio().getId().equals(portfolioId)) {
            throw new IllegalArgumentException("Holding does not belong to this portfolio");
        }

        if (quantity != null) holding.setQuantity(quantity);
        if (avgPrice != null) holding.setAvgPrice(avgPrice);
        holding = holdingRepository.save(holding);

        Stock stock = holding.getStock();
        syncStockFromHolding(stock, holding.getQuantity(), holding.getAvgPrice());
        snapshotService.saveOrUpdate(stock, LocalDate.now(), holding.getPortfolio());
        return computeHoldingDTO(holding);
    }

    public void removeHolding(Long portfolioId, Long holdingId) {
        PortfolioHolding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding not found: " + holdingId));
        if (!holding.getPortfolio().getId().equals(portfolioId)) {
            throw new IllegalArgumentException("Holding does not belong to this portfolio");
        }
        Long stockId = holding.getStock().getId();
        holdingRepository.delete(holding);
        snapshotService.deleteSnapshot(portfolioId, stockId);
    }

    public void removeHoldingByStockId(Long portfolioId, Long stockId) {
        PortfolioHolding holding = holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Holding not found for portfolio " + portfolioId + " and stock " + stockId));
        holdingRepository.delete(holding);
        snapshotService.deleteSnapshot(portfolioId, stockId);
    }

    @Transactional(readOnly = true)
    public HoldingDTO getHolding(Long portfolioId, Long stockId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        PortfolioHolding holding = holdingRepository.findByPortfolioIdAndStockId(portfolioId, stockId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Holding not found for portfolio " + portfolioId + " and stock " + stockId));
        return computeHoldingDTO(holding);
    }

    @Transactional(readOnly = true)
    public HoldingDTO getAggregateHolding(Long stockId) {
        List<PortfolioHolding> holdings = holdingRepository.findByStockId(stockId);
        if (holdings.isEmpty()) {
            throw new ResourceNotFoundException("No holdings found for stock " + stockId);
        }
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + stockId));
        int totalQty = holdings.stream()
                .filter(h -> h.getQuantity() != null)
                .mapToInt(PortfolioHolding::getQuantity)
                .sum();
        BigDecimal weightedAvg = BigDecimal.ZERO;
        if (totalQty > 0) {
            BigDecimal weightedSum = BigDecimal.ZERO;
            for (PortfolioHolding h : holdings) {
                if (h.getQuantity() != null && h.getAvgPrice() != null) {
                    weightedSum = weightedSum.add(
                            h.getAvgPrice().multiply(BigDecimal.valueOf(h.getQuantity())));
                }
            }
            weightedAvg = weightedSum.divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
        }
        BigDecimal investment = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal pnl = BigDecimal.ZERO;
        BigDecimal lastPrice = null;
        for (PortfolioHolding h : holdings) {
            PortfolioHolding computed = computeAndCache(h);
            if (computed.getInvestment() != null) investment = investment.add(computed.getInvestment());
            if (computed.getCurrentValue() != null) currentValue = currentValue.add(computed.getCurrentValue());
            if (computed.getPnl() != null) pnl = pnl.add(computed.getPnl());
            if (computed.getLastTradedPrice() != null) lastPrice = computed.getLastTradedPrice();
        }
        BigDecimal pnlPercent = investment.compareTo(BigDecimal.ZERO) > 0
                ? pnl.multiply(BigDecimal.valueOf(100)).divide(investment, 2, RoundingMode.HALF_UP)
                : null;
        return HoldingDTO.builder()
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .sector(stock.getSector())
                .yahooSymbol(stock.getYahooSymbol())
                .quantity(totalQty > 0 ? totalQty : null)
                .avgPrice(weightedAvg.compareTo(BigDecimal.ZERO) > 0 ? weightedAvg : null)
                .lastTradedPrice(lastPrice)
                .investment(investment)
                .currentValue(currentValue)
                .pnl(pnl)
                .pnlPercent(pnlPercent)
                .build();
    }

    @Transactional(readOnly = true)
    public List<HoldingDTO> getHoldings(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        return getHoldingsInternal(portfolioId);
    }

    private List<HoldingDTO> getHoldingsInternal(Long portfolioId) {
        return holdingRepository.findHoldingsOrderBySymbol(portfolioId).stream()
                .map(this::computeHoldingDTO)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════
    // P&L Recalculation
    // ═══════════════════════════════════════════════════════════════════

    public int recalculatePortfolio(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        for (PortfolioHolding holding : holdings) {
            computeAndCache(holding);
        }
        return holdings.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal Helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes P&L for a holding and caches the computed values on the transient fields.
     * Same logic as StockService.recalculatePortfolio() but operates on PortfolioHolding.
     */
    private PortfolioHolding computeAndCache(PortfolioHolding holding) {
        Stock stock = holding.getStock();
        Integer qty = holding.getQuantity();
        BigDecimal avgPrice = holding.getAvgPrice();
        holding.setInvestment(null);
        holding.setCurrentValue(null);
        holding.setPnl(null);
        holding.setPnlPercent(null);
        holding.setLastTradedPrice(null);

        if (qty != null && avgPrice != null) {
            BigDecimal qtyBD = BigDecimal.valueOf(qty);
            holding.setInvestment(avgPrice.multiply(qtyBD));
        }

        dailyPriceRepository.findFirstByStockIdOrderByPriceDateDesc(stock.getId())
                .ifPresent(price -> {
                    holding.setLastTradedPrice(price.getClosingPrice());
                    if (qty != null && holding.getInvestment() != null) {
                        BigDecimal qtyBD = BigDecimal.valueOf(qty);
                        BigDecimal currentValue = price.getClosingPrice().multiply(qtyBD);
                        BigDecimal pnl = currentValue.subtract(holding.getInvestment());
                        BigDecimal pnlPercent = holding.getInvestment().compareTo(BigDecimal.ZERO) > 0
                                ? pnl.multiply(BigDecimal.valueOf(100))
                                        .divide(holding.getInvestment(), 2, RoundingMode.HALF_UP)
                                : null;
                        holding.setCurrentValue(currentValue);
                        holding.setPnl(pnl);
                        holding.setPnlPercent(pnlPercent);
                    }
                });

        return holding;
    }

    /**
     * Computes P&L fields for a holding and returns a DTO.
     */
    private HoldingDTO computeHoldingDTO(PortfolioHolding holding) {
        computeAndCache(holding);
        Stock stock = holding.getStock();
        return HoldingDTO.builder()
                .id(holding.getId())
                .portfolioId(holding.getPortfolio().getId())
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .sector(stock.getSector())
                .yahooSymbol(stock.getYahooSymbol())
                .quantity(holding.getQuantity())
                .avgPrice(holding.getAvgPrice())
                .lastTradedPrice(holding.getLastTradedPrice())
                .investment(holding.getInvestment())
                .currentValue(holding.getCurrentValue())
                .pnl(holding.getPnl())
                .pnlPercent(holding.getPnlPercent())
                .build();
    }

    /**
     * Syncs PortfolioHolding quantity/avgPrice back to the Stock entity.
     * Keeps the stocks table in sync when holdings are added/updated via portfolio management.
     */
    private void syncStockFromHolding(Stock stock, Integer quantity, BigDecimal avgPrice) {
        stock.setQuantity(quantity);
        stock.setAvgPrice(avgPrice);
        stockRepository.save(stock);
    }

    private PortfolioDTO toPortfolioDTO(Portfolio portfolio) {
        long count = holdingRepository.countByPortfolioId(portfolio.getId());
        return PortfolioDTO.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .description(portfolio.getDescription())
                .isDefault(portfolio.isDefault())
                .holdingsCount(count)
                .createdAt(portfolio.getCreatedAt())
                .updatedAt(portfolio.getUpdatedAt())
                .build();
    }
}
