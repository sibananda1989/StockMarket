package org.example.controller;

import org.example.CacheConfig;
import org.example.dto.BacktestResultDTO;
import org.example.service.BacktestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice tests for {@link BacktestController}.
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(BacktestController.class)
@Import(CacheConfig.class)
class BacktestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BacktestService backtestService;

    @Test
    void runBacktest_returns200AndExpectedFields() throws Exception {
        BacktestResultDTO dto = BacktestResultDTO.builder()
                .symbol("RELIANCE")
                .totalTrades(2)
                .winningTrades(1)
                .losingTrades(1)
                .winRate(50.0)
                .totalReturn(new BigDecimal("5.25"))
                .maxDrawdown(new BigDecimal("3.1"))
                .finalPortfolioValue(new BigDecimal("10525.00"))
                .sharpeRatio(1.2)
                .tradeHistory(List.of())
                .equityCurve(List.of())
                .build();

        when(backtestService.runBacktest(eq(1L), eq(true), eq(new BigDecimal("0.02")),
                eq(0), eq(2.0), eq(3.0))).thenReturn(dto);

        mockMvc.perform(get("/api/backtest/stock/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.data.totalTrades").value(2))
                .andExpect(jsonPath("$.data.winningTrades").value(1))
                .andExpect(jsonPath("$.data.losingTrades").value(1))
                .andExpect(jsonPath("$.data.winRate").value(50.0))
                .andExpect(jsonPath("$.data.totalReturn").value("5.25"))
                .andExpect(jsonPath("$.data.maxDrawdown").value(3.1))
                .andExpect(jsonPath("$.data.finalPortfolioValue").value(10525.0))
                .andExpect(jsonPath("$.data.sharpeRatio").value(1.2))
                .andExpect(jsonPath("$.data.tradeHistory").isArray())
                .andExpect(jsonPath("$.data.equityCurve").isArray());
    }

    @Test
    void runBacktest_customParams_passthrough() throws Exception {
        BacktestResultDTO dto = BacktestResultDTO.builder()
                .symbol("TCS")
                .totalTrades(0)
                .winningTrades(0)
                .losingTrades(0)
                .winRate(0.0)
                .totalReturn(new BigDecimal("0"))
                .finalPortfolioValue(new BigDecimal("10000.00"))
                .tradeHistory(List.of())
                .equityCurve(List.of())
                .build();

        when(backtestService.runBacktest(eq(2L), eq(false), eq(new BigDecimal("0.05")),
                eq(30), eq(1.5), eq(2.5))).thenReturn(dto);

        mockMvc.perform(get("/api/backtest/stock/2")
                        .param("stopLoss", "false")
                        .param("positionSizePct", "0.05")
                        .param("days", "30")
                        .param("riskFreeRatePct", "1.5")
                        .param("trailingStopMultiplier", "2.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("TCS"))
                .andExpect(jsonPath("$.data.totalTrades").value(0));

        verify(backtestService).runBacktest(eq(2L), eq(false), eq(new BigDecimal("0.05")),
                eq(30), eq(1.5), eq(2.5));
    }
}
