package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.ScoreParameterConfigDTO;
import org.example.service.ScoreParameterService;
import org.example.service.StrategyConfigService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/score-parameters")
@RequiredArgsConstructor
public class ScoreParameterController {

    private final ScoreParameterService scoreParameterService;
    private final StrategyConfigService strategyConfigService;

    private static final List<String> ENGINE_STRATEGIES = List.of(
            "RSI", "MACD", "MA_CROSSOVER", "BOLLINGER", "VOLUME",
            "CANDLESTICK_AT_SUPPORT", "CANDLESTICK_AT_RESISTANCE", "CANDLESTICK_PATTERN",
            "BREAKOUT"
    );

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScoreParameterConfigDTO>>> getAll() {
        Set<String> activeStrategies = strategyConfigService.getActiveStrategyNames();

        List<ScoreParameterConfigDTO> dtos = scoreParameterService.getAll().stream()
                .map(c -> ScoreParameterConfigDTO.builder()
                        .paramKey(c.getParamKey())
                        .category(c.getCategory())
                        .displayName(c.getDisplayName())
                        .description(c.getDescription())
                        .enabled(c.isEnabled())
                        .scoreSystem(c.getScoreSystem())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        ENGINE_STRATEGIES.forEach(name -> dtos.add(ScoreParameterConfigDTO.builder()
                .paramKey(name)
                .category("ENGINE")
                .displayName(humanName(name))
                .description("Multi-strategy engine strategy")
                .enabled(activeStrategies.contains(name))
                .scoreSystem("ENGINE")
                .build()));

        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @CacheEvict(value = "signals", allEntries = true)
    @PutMapping("/{paramKey}")
    public ResponseEntity<ApiResponse<String>> setEnabled(
            @PathVariable String paramKey,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        if (ENGINE_STRATEGIES.contains(paramKey)) {
            strategyConfigService.toggleStrategy(paramKey, enabled);
        } else {
            scoreParameterService.setEnabled(paramKey, enabled);
        }
        return ResponseEntity.ok(ApiResponse.success("updated", "updated"));
    }

    @CacheEvict(value = "signals", allEntries = true)
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<String>> reset() {
        scoreParameterService.resetToDefaults();
        return ResponseEntity.ok(ApiResponse.success("reset", "reset"));
    }

    private String humanName(String name) {
        return switch (name) {
            case "RSI" -> "RSI Strategy";
            case "MACD" -> "MACD Strategy";
            case "MA_CROSSOVER" -> "MA Crossover";
            case "BOLLINGER" -> "Bollinger Band";
            case "VOLUME" -> "Volume Strategy";
            case "CANDLESTICK_AT_SUPPORT" -> "Candlestick at Support";
            case "CANDLESTICK_AT_RESISTANCE" -> "Candlestick at Resistance";
            case "CANDLESTICK_PATTERN" -> "Candlestick Patterns";
            case "BREAKOUT" -> "Breakout Strategy";
            default -> name + " Strategy";
        };
    }
}
