package org.example.service;

import org.example.dto.PortfolioAggregateDTO;
import org.example.entity.PortfolioHolding;
import org.example.entity.PortfolioTransaction;
import org.example.entity.Stock;
import org.example.entity.TransactionType;
import org.example.repository.DailyPriceRepository;
import org.example.repository.PortfolioHoldingRepository;
import org.example.repository.PortfolioTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the in-memory price lookup used by the history sweep. The sweep runs
 * once per trading date per held stock, so a per-iteration DB fallback there
 * triggers a Hibernate auto-flush over the whole persistence context and turns
 * a save into a ~40s request.
 */
class PortfolioHistoryServicePriceLookupTest {

    private PortfolioTransactionRepository transactionRepository;
    private DailyPriceRepository dailyPriceRepository;
    private PortfolioHoldingRepository holdingRepository;
    private PortfolioPositionReplayer replayer;
    private PortfolioHistoryService service;

    private static final Long PORTFOLIO_ID = 1L;
    private static final Long STOCK_ID = 42L;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(PortfolioTransactionRepository.class);
        dailyPriceRepository = mock(DailyPriceRepository.class);
        holdingRepository = mock(PortfolioHoldingRepository.class);
        replayer = mock(PortfolioPositionReplayer.class);
        service = new PortfolioHistoryService(
                transactionRepository, dailyPriceRepository, holdingRepository, replayer);
    }

    @Test
    @DisplayName("loads price history from the beginning so early dates resolve from memory")
    void shouldLoadPricesFromEpochRatherThanAWindow() {
        stubSingleBuyTransaction();

        service.getAllHistory(PORTFOLIO_ID);

        // A bounded window would leave early sweep dates to hit the DB fallback.
        verify(dailyPriceRepository).findPricesForStockIdsSince(org.mockito.ArgumentMatchers.<Long>anyList(), any(LocalDate.class));
        org.mockito.ArgumentCaptor<LocalDate> since =
                org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(dailyPriceRepository).findPricesForStockIdsSince(org.mockito.ArgumentMatchers.<Long>anyList(), since.capture());
        assertThat(since.getValue())
                .as("prices must be loaded from the epoch, not a bounded window")
                .isEqualTo(LocalDate.of(1970, 1, 1));
    }

    @Test
    @DisplayName("never issues a per-date price query while sweeping dates")
    void shouldNotQueryPerDateDuringSweep() {
        stubSingleBuyTransaction();
        // Several distinct trading dates so the sweep iterates more than once.
        when(dailyPriceRepository.findDistinctTradingDatesAfter(any(LocalDate.class)))
                .thenReturn(List.of(LocalDate.of(2024, 1, 2),
                        LocalDate.of(2024, 1, 3),
                        LocalDate.of(2024, 1, 4),
                        LocalDate.of(2024, 1, 5)));

        service.getAllHistory(PORTFOLIO_ID);

        verify(dailyPriceRepository, never()).findClosingPriceOnOrBeforeDate(anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("prices a holding from the last close on or before the date")
    void shouldUseClosingPriceOnOrBeforeDate() {
        stubSingleBuyTransaction();
        LocalDate buyDate = LocalDate.of(2024, 1, 2);
        when(dailyPriceRepository.findDistinctTradingDatesAfter(any(LocalDate.class)))
                .thenReturn(List.of(buyDate, LocalDate.of(2024, 1, 3)));
        // Only one price exists, well after the buy date: the 1/3 row must reuse it.
        stubPriceFor(buyDate, new BigDecimal("100.00"));

        List<PortfolioAggregateDTO> history = service.getAllHistory(PORTFOLIO_ID);

        assertThat(history).isNotEmpty();
        // computeHistory always appends today, so the final row is dated now and
        // must carry forward the last known close.
        PortfolioAggregateDTO latest = history.get(history.size() - 1);
        assertThat(latest.getDate()).isEqualTo(LocalDate.now());
        assertThat(latest.getTotalCurrentValue())
                .as("carry-forward: 1 share valued at the most recent close of 100")
                .isEqualByComparingTo("100.00");
        assertThat(latest.getTotalInvestment())
                .as("1 share bought at 100")
                .isEqualByComparingTo("100.00");
        assertThat(latest.getTotalPnl()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("treats a holding as worth zero when no price exists on or before the date")
    void shouldValueHoldingAtZeroWhenNoPriceExists() {
        stubSingleBuyTransaction();
        // Price data exists, but only AFTER the buy date — so the buy date itself
        // has no close on or before it.
        stubPriceFor(LocalDate.of(2024, 6, 1), new BigDecimal("500.00"));
        when(dailyPriceRepository.findDistinctTradingDatesAfter(any(LocalDate.class)))
                .thenReturn(List.of(LocalDate.of(2024, 1, 2)));

        List<PortfolioAggregateDTO> history = service.getAllHistory(PORTFOLIO_ID);

        assertThat(history).isNotEmpty();
        PortfolioAggregateDTO only = history.get(0);
        assertThat(only.getDate()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(only.getTotalInvestment()).isEqualByComparingTo("100.00");
        assertThat(only.getTotalCurrentValue())
                .as("no price on or before the date contributes zero value")
                .isEqualByComparingTo("0.00");
    }

    private void stubSingleBuyTransaction() {
        Stock stock = new Stock();
        stock.setId(STOCK_ID);
        stock.setSymbol("TEST");

        PortfolioTransaction buy = new PortfolioTransaction();
        buy.setId(1L);
        buy.setStock(stock);
        buy.setQuantity(1);
        buy.setPrice(new BigDecimal("100.00"));
        buy.setType(TransactionType.BUY);
        buy.setTransactionDate(LocalDate.of(2024, 1, 2));

        when(transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(PORTFOLIO_ID))
                .thenReturn(new ArrayList<>(List.of(buy)));

        PortfolioHolding holding = new PortfolioHolding();
        holding.setStock(stock);
        holding.setQuantity(0);
        holding.setAvgPrice(BigDecimal.ZERO);
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(holding));
    }

    private void stubPriceFor(LocalDate priceDate, BigDecimal close) {
        Stock stock = new Stock();
        stock.setId(STOCK_ID);
        org.example.entity.DailyPrice dp = new org.example.entity.DailyPrice();
        dp.setStock(stock);
        dp.setPriceDate(priceDate);
        dp.setClosingPrice(close);
        when(dailyPriceRepository.findPricesForStockIdsSince(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(dp));
    }

}