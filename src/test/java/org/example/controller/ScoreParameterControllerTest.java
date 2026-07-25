package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.CacheConfig;
import org.example.entity.ScoreParameterConfig;
import org.example.service.ScoreParameterService;
import org.example.service.StrategyConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests for {@link ScoreParameterController}.
 *
 * LEGACY parameters are served by {@link ScoreParameterService#getAll()}; ENGINE
 * parameters (multi-strategy engine strategies) are appended by the controller and
 * their enable/disable is routed to {@link StrategyConfigService#toggleStrategy}.
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(ScoreParameterController.class)
@Import(CacheConfig.class)
class ScoreParameterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScoreParameterService scoreParameterService;

    @MockBean
    private StrategyConfigService strategyConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void get_returnsAllParams() throws Exception {
        ScoreParameterConfig legacy1 = ScoreParameterConfig.builder()
                .paramKey("MOM_RSI14").category("MOMENTUM").displayName("RSI(14)")
                .description("Relative Strength Index").enabled(true).scoreSystem("LEGACY").build();
        ScoreParameterConfig legacy2 = ScoreParameterConfig.builder()
                .paramKey("TREND_DIVERGENCE").category("TREND").displayName("Divergence")
                .description("RSI bull/bear divergence").enabled(true).scoreSystem("LEGACY").build();

        when(scoreParameterService.getAll()).thenReturn(List.of(legacy1, legacy2));
        when(strategyConfigService.getActiveStrategyNames()).thenReturn(Set.of("RSI", "MACD"));

        mockMvc.perform(get("/api/score-parameters"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"scoreSystem\":\"LEGACY\"")))
                .andExpect(content().string(containsString("\"scoreSystem\":\"ENGINE\"")))
                .andExpect(content().string(containsString("MOM_RSI14")))
                .andExpect(content().string(containsString("\"RSI\"")));
    }

    @Test
    void put_legacyKey_updatesService() throws Exception {
        doNothing().when(scoreParameterService).setEnabled("MOM_RSI14", false);

        mockMvc.perform(put("/api/score-parameters/MOM_RSI14")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        verify(scoreParameterService).setEnabled("MOM_RSI14", false);
        verify(strategyConfigService, never()).toggleStrategy(anyString(), anyBoolean());
    }

    @Test
    void put_engineKey_routesToStrategyToggle() throws Exception {
        when(strategyConfigService.toggleStrategy("RSI", false))
                .thenReturn(new org.example.entity.StrategyConfig("RSI", false, "RSI Strategy", 7));

        mockMvc.perform(put("/api/score-parameters/RSI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        verify(strategyConfigService).toggleStrategy("RSI", false);
        verify(scoreParameterService, never()).setEnabled(eq("RSI"), anyBoolean());
    }

    @Test
    void post_reset_callsService() throws Exception {
        doNothing().when(scoreParameterService).resetToDefaults();

        mockMvc.perform(post("/api/score-parameters/reset"))
                .andExpect(status().isOk());

        verify(scoreParameterService).resetToDefaults();
    }
}
