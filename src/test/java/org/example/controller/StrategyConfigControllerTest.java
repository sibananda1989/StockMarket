package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.ConditionStatDTO;
import org.example.dto.StrategyConditionGroupDTO;
import org.example.dto.StrategyFullDTO;
import org.example.entity.StrategyConfig;
import org.example.service.StrategyConditionService;
import org.example.service.StrategyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrategyConfigControllerTest {

    @Mock
    private StrategyConfigService strategyConfigService;
    @Mock
    private StrategyConditionService strategyConditionService;

    @InjectMocks
    private StrategyConfigController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllConfigs() {
        List<StrategyConfig> configs = List.of(
                new StrategyConfig("RSI", true, "RSI Strategy", 7)
        );
        when(strategyConfigService.getAllConfigs()).thenReturn(configs);

        ResponseEntity<ApiResponse<List<StrategyConfig>>> response = controller.getAllConfigs();

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(configs, response.getBody().getData());
    }

    @Test
    void testToggleStrategy_Activate() {
        StrategyConfig config = new StrategyConfig("MACD", true, "MACD Strategy", 7);
        when(strategyConfigService.toggleStrategy("MACD", true)).thenReturn(config);

        ResponseEntity<ApiResponse<StrategyConfig>> response = controller.toggleStrategy("MACD", new StrategyConfigController.ToggleRequest(true));

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().getData().isActive());
        verify(strategyConfigService).toggleStrategy("MACD", true);
    }

    @Test
    void testToggleStrategy_Deactivate() {
        StrategyConfig config = new StrategyConfig("RSI", false, "RSI Strategy", 7);
        when(strategyConfigService.toggleStrategy("RSI", false)).thenReturn(config);

        ResponseEntity<ApiResponse<StrategyConfig>> response = controller.toggleStrategy("RSI", new StrategyConfigController.ToggleRequest(false));

        assertEquals(200, response.getStatusCodeValue());
        assertFalse(response.getBody().getData().isActive());
        verify(strategyConfigService).toggleStrategy("RSI", false);
    }

    @Test
    void testToggleStrategy_ResponseHasSuccessMessage() {
        StrategyConfig config = new StrategyConfig("VOLUME", false, "Volume Strategy", 5);
        when(strategyConfigService.toggleStrategy("VOLUME", false)).thenReturn(config);

        ResponseEntity<ApiResponse<StrategyConfig>> response = controller.toggleStrategy("VOLUME", new StrategyConfigController.ToggleRequest(false));

        assertEquals("success", response.getBody().getStatus());
        assertEquals("Strategy toggled", response.getBody().getMessage());
    }

    // ── New tests for strategy condition endpoints ──────────────────────

    @Test
    void testGetFullConfig_Returns200() {
        List<StrategyFullDTO> dtos = List.of(
                StrategyFullDTO.builder().strategyName("RSI").displayName("RSI Strategy").build()
        );
        when(strategyConditionService.getFullStrategyConfig()).thenReturn(dtos);

        ResponseEntity<ApiResponse<List<StrategyFullDTO>>> response = controller.getFullConfig();

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("RSI", response.getBody().getData().get(0).getStrategyName());
    }

    @Test
    void testGetStats_Returns200() {
        Map<String, List<ConditionStatDTO>> stats = Map.of(
                "RSI", List.of(ConditionStatDTO.builder().conditionId("rsi_strong_buy").stockCount(5).build())
        );
        when(strategyConditionService.getCachedStats()).thenReturn(stats);

        ResponseEntity<ApiResponse<Map<String, List<ConditionStatDTO>>>> response = controller.getStats();

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().getData().containsKey("RSI"));
    }

    @Test
    void testSaveConditions_Returns200() {
        List<StrategyConditionGroupDTO> dtos = List.of(
                StrategyConditionGroupDTO.builder()
                        .conditionId("c1").signal("BUY").fieldLabel("RSI")
                        .operator("<").confidence("high").build()
        );

        ResponseEntity<ApiResponse<String>> response = controller.saveConditions("RSI", dtos);

        assertEquals(200, response.getStatusCodeValue());
        verify(strategyConditionService).saveConditions("RSI", dtos);
        verify(strategyConditionService).computeAndCacheStats();
    }

    @Test
    void testResetConditions_Returns200() {
        ResponseEntity<ApiResponse<String>> response = controller.resetConditions();

        assertEquals(200, response.getStatusCodeValue());
        verify(strategyConditionService).resetToDefaults();
        verify(strategyConditionService).computeAndCacheStats();
    }

    @Test
    void testUpdatePriority_Returns200() {
        StrategyConfig config = new StrategyConfig("RSI", true, "RSI Strategy", 8);
        when(strategyConfigService.updatePriority("RSI", 8)).thenReturn(config);

        ResponseEntity<ApiResponse<StrategyConfig>> response = controller.updatePriority("RSI", 8);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(8, response.getBody().getData().getPriority());
        verify(strategyConfigService).updatePriority("RSI", 8);
    }
}
