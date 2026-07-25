package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.ConditionStatDTO;
import org.example.dto.StrategyConditionGroupDTO;
import org.example.dto.StrategyFullDTO;
import org.example.entity.StrategyConfig;
import org.example.entity.StrategyConditionGroup;
import org.example.service.StrategyConditionService;
import org.example.service.StrategyConfigService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/strategy-config")
@RequiredArgsConstructor
public class StrategyConfigController {

    private final StrategyConfigService strategyConfigService;
    private final StrategyConditionService strategyConditionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StrategyConfig>>> getAllConfigs() {
        List<StrategyConfig> configs = strategyConfigService.getAllConfigs();
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @PutMapping("/{strategyName}")
    public ResponseEntity<ApiResponse<StrategyConfig>> toggleStrategy(
            @PathVariable String strategyName,
            @RequestBody ToggleRequest request) {
        StrategyConfig config = strategyConfigService.toggleStrategy(strategyName, request.active());
        return ResponseEntity.ok(ApiResponse.success("Strategy toggled", config));
    }

    @GetMapping("/full")
    public ResponseEntity<ApiResponse<List<StrategyFullDTO>>> getFullConfig() {
        List<StrategyFullDTO> fullConfigs = strategyConditionService.getFullStrategyConfig();
        return ResponseEntity.ok(ApiResponse.success(fullConfigs));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, List<ConditionStatDTO>>>> getStats() {
        Map<String, List<ConditionStatDTO>> stats = strategyConditionService.getCachedStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @CacheEvict(value = "signals", allEntries = true)
    @PutMapping("/conditions/{strategyName}")
    public ResponseEntity<ApiResponse<String>> saveConditions(
            @PathVariable String strategyName,
            @RequestBody List<StrategyConditionGroupDTO> conditions) {
        strategyConditionService.saveConditions(strategyName, conditions);
        strategyConditionService.computeAndCacheStats();
        return ResponseEntity.ok(ApiResponse.success("Conditions saved", "Conditions saved"));
    }

    @CacheEvict(value = "signals", allEntries = true)
    @PostMapping("/conditions/reset")
    public ResponseEntity<ApiResponse<String>> resetConditions() {
        strategyConditionService.resetToDefaults();
        strategyConditionService.computeAndCacheStats();
        return ResponseEntity.ok(ApiResponse.success("Reset to defaults", "Reset to defaults"));
    }

    @PutMapping("/{strategyName}/priority")
    public ResponseEntity<ApiResponse<StrategyConfig>> updatePriority(
            @PathVariable String strategyName,
            @RequestParam int priority) {
        StrategyConfig config = strategyConfigService.updatePriority(strategyName, priority);
        return ResponseEntity.ok(ApiResponse.success("Priority updated", config));
    }

    @CacheEvict(value = "signals", allEntries = true)
    @PatchMapping("/conditions/{strategyName}/{conditionId}")
    public ResponseEntity<ApiResponse<StrategyConditionGroup>> toggleCondition(
            @PathVariable String strategyName,
            @PathVariable String conditionId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        StrategyConditionGroup updated = strategyConditionService.toggleCondition(strategyName, conditionId, enabled);
        return ResponseEntity.ok(ApiResponse.success("Condition toggled", updated));
    }

    @GetMapping("/volume/spike-factor")
    public ResponseEntity<ApiResponse<Double>> getVolumeSpikeFactor() {
        double factor = strategyConfigService.getVolumeSpikeFactor();
        return ResponseEntity.ok(ApiResponse.success(factor));
    }

    @PutMapping("/volume/spike-factor")
    public ResponseEntity<ApiResponse<String>> updateVolumeSpikeFactor(@RequestParam double factor) {
        if (factor <= 0 || factor > 10) {
            throw new IllegalArgumentException("Spike factor must be between 0 and 10");
        }
        strategyConfigService.updateVolumeSpikeFactor(factor);
        return ResponseEntity.ok(ApiResponse.success("Spike factor updated to " + factor, "Spike factor updated to " + factor));
    }

    record ToggleRequest(boolean active) {}
}
