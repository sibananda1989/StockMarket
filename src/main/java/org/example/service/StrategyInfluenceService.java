package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.StrategyConditionGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyInfluenceService {

    private final StrategyConfigService strategyConfigService;
    private final StrategyConditionService strategyConditionService;

    @Value("${strategy.influence.weight:0.5}")
    private double influenceWeight;

    public record StrategyInfluence(double multiplier, String dominantSignal, String reason) {}

    public StrategyInfluence computeInfluence(Long stockId) {
        Set<String> activeNames = strategyConfigService.getActiveStrategyNames();
        if (activeNames.isEmpty()) {
            return new StrategyInfluence(0, "HOLD", "No active strategies");
        }

        double buyScore = 0;
        double sellScore = 0;
        StringBuilder reason = new StringBuilder();

        for (String strategyName : activeNames) {
            List<StrategyConditionGroup> conditions = strategyConditionService.getConditions(strategyName);
            int priority = strategyConfigService.getPriority(strategyName);

            for (StrategyConditionGroup cond : conditions) {
                if (!cond.isEnabled()) continue;
                boolean matches = strategyConditionService.evaluateConditionForStock(stockId, cond);
                if (!matches) continue;

                double weight = priority * switch (cond.getConfidence()) {
                    case "high" -> 1.0;
                    case "mid" -> 0.6;
                    default -> 0.3;
                };

                if ("BUY".equals(cond.getSignalType())) {
                    buyScore += weight;
                } else if ("SELL".equals(cond.getSignalType())) {
                    sellScore -= weight;
                }

                if (reason.isEmpty()) {
                    reason.append(strategyName).append(": ").append(cond.getConditionId());
                }
            }
        }

        double net = buyScore + sellScore;
        double totalMagnitude = Math.abs(buyScore) + Math.abs(sellScore);
        double normalized = totalMagnitude == 0 ? 0 : Math.tanh(net / 10.0);
        String signal = net > 0 ? "BUY" : net < 0 ? "SELL" : "HOLD";

        return new StrategyInfluence(normalized * influenceWeight, signal, reason.toString());
    }
}