package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.IndicatorType;
import org.example.entity.Portfolio;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistOpportunityService {

    private final SignalService signalService;
    private final PortfolioService portfolioService;

    private static final Map<String, Integer> SIGNAL_STRENGTH = Map.of(
            "STRONG BUY", 5,
            "BUY", 4,
            "HOLD", 3,
            "SELL", 2,
            "STRONG SELL", 1
    );

    public WatchlistOpportunityResponseDTO getOpportunities(Long portfolioId) {
        List<String> warnings = new ArrayList<>();

        // 1. Get holdings
        if (portfolioId == null) {
            Portfolio defaultPortfolio = portfolioService.getDefaultPortfolio();
            portfolioId = defaultPortfolio.getId();
        }
        List<HoldingDTO> holdings = portfolioService.getHoldings(portfolioId);

        if (holdings.isEmpty()) {
            return WatchlistOpportunityResponseDTO.builder()
                    .stocks(Collections.emptyList())
                    .summary(WatchlistSummaryDTO.builder()
                            .totalStocks(0)
                            .buyOpportunities(0)
                            .holdOpportunities(0)
                            .sellOpportunities(0)
                            .build())
                    .warnings(Collections.emptyList())
                    .build();
        }

        // 2. Compute opportunities for each holding
        List<WatchlistOpportunityDTO> opportunities = new ArrayList<>();
        for (HoldingDTO holding : holdings) {
            try {
                SignalDTO signal = signalService.computeSignal(holding.getStockId());
                if (signal.getCompositeScore() == 0 && signal.getRecommendation() == null) {
                    warnings.add(holding.getSymbol() + ": no signal data available");
                    continue;
                }
                opportunities.add(buildOpportunity(holding, signal));
            } catch (Exception e) {
                log.error("Failed to compute signal for {}: {}", holding.getSymbol(), e.getMessage());
                warnings.add(holding.getSymbol() + ": " + e.getMessage());
            }
        }

        // 3. Sort by signal strength, then composite score
        opportunities.sort((a, b) -> {
            int signalCompare = compareSignalStrength(b.getSignal(), a.getSignal());
            if (signalCompare != 0) return signalCompare;
            return b.getCompositeScore().compareTo(a.getCompositeScore());
        });

        // 4. Assign ranks
        IntStream.range(0, opportunities.size())
                .forEach(i -> opportunities.get(i).setRank(i + 1));

        // 5. Build summary
        WatchlistSummaryDTO summary = buildSummary(opportunities);

        return WatchlistOpportunityResponseDTO.builder()
                .stocks(opportunities)
                .summary(summary)
                .warnings(warnings)
                .build();
    }

    private WatchlistOpportunityDTO buildOpportunity(HoldingDTO holding, SignalDTO signal) {
        BigDecimal price = signal.getLatestPrice();
        BigDecimal atr = signal.getAtr();

        // Entry zone: price ± ATR
        EntryZoneDTO entryZone = EntryZoneDTO.builder()
                .low(atr != null && price != null ? price.subtract(atr) : null)
                .high(atr != null && price != null ? price.add(atr) : null)
                .build();

        // Risk-reward ratio
        BigDecimal riskRewardRatio = null;
        if (signal.getTargetPrice() != null && signal.getStopLoss() != null && price != null) {
            BigDecimal reward = signal.getTargetPrice().subtract(price);
            BigDecimal risk = price.subtract(signal.getStopLoss());
            if (risk.compareTo(BigDecimal.ZERO) > 0) {
                riskRewardRatio = reward.divide(risk, 2, RoundingMode.HALF_UP);
            }
        }

        // Indicator agreement
        Double indicatorAgreement = computeIndicatorAgreement(signal);

        // Reasons (extreme values)
        List<ReasonDTO> reasons = computeReasons(signal);

        // Last computed timestamp
        LocalDateTime lastComputed = signal.getSignalAge() != null
                ? LocalDateTime.now().minusDays(signal.getSignalAge())
                : null;

        return WatchlistOpportunityDTO.builder()
                .stockId(holding.getStockId())
                .symbol(holding.getSymbol())
                .companyName(holding.getName())
                .currentPrice(price)
                .compositeScore(BigDecimal.valueOf(signal.getCompositeScore()))
                .signal(signal.getRecommendation())
                .entryZone(entryZone)
                .stopLoss(signal.getStopLoss())
                .target(signal.getTargetPrice())
                .riskRewardRatio(riskRewardRatio)
                .indicatorAgreement(indicatorAgreement)
                .reasons(reasons)
                .lastComputed(lastComputed)
                .confidenceScore(signal.getConfidenceScore() != null ? signal.getConfidenceScore().intValue() : null)
                .build();
    }

    private Double computeIndicatorAgreement(SignalDTO signal) {
        int totalDirectional = 0;
        int agreeing = 0;

        int compositeDirection = signal.getCompositeScore() > 0 ? 1 : (signal.getCompositeScore() < 0 ? -1 : 0);

        // RSI
        if (signal.getRsi14() != null && IndicatorType.RSI.isDirectional()) {
            totalDirectional++;
            int rsiDirection = signal.getRsi14().doubleValue() < 40 ? -1 : (signal.getRsi14().doubleValue() > 60 ? 1 : 0);
            if (rsiDirection == compositeDirection || (compositeDirection == 0 && rsiDirection == 0)) {
                agreeing++;
            }
        }

        // MACD
        if (signal.getMacd() != null && signal.getMacdSignal() != null && IndicatorType.MACD_LINE.isDirectional()) {
            totalDirectional++;
            int macdDirection = signal.getMacd().compareTo(signal.getMacdSignal()) > 0 ? 1 : -1;
            if (macdDirection == compositeDirection) {
                agreeing++;
            }
        }

        // SMA 20
        if (signal.getSma20() != null && signal.getLatestPrice() != null && IndicatorType.SMA_20.isDirectional()) {
            totalDirectional++;
            int smaDirection = signal.getLatestPrice().compareTo(signal.getSma20()) > 0 ? 1 : -1;
            if (smaDirection == compositeDirection) {
                agreeing++;
            }
        }

        // SMA 50
        if (signal.getSma50() != null && signal.getLatestPrice() != null && IndicatorType.SMA_50.isDirectional()) {
            totalDirectional++;
            int smaDirection = signal.getLatestPrice().compareTo(signal.getSma50()) > 0 ? 1 : -1;
            if (smaDirection == compositeDirection) {
                agreeing++;
            }
        }

        // Stochastic
        if (signal.getStochK() != null && signal.getStochD() != null && IndicatorType.STOCH_K.isDirectional()) {
            totalDirectional++;
            int stochDirection = signal.getStochK().compareTo(signal.getStochD()) > 0 ? 1 : -1;
            if (stochDirection == compositeDirection) {
                agreeing++;
            }
        }

        // Williams %R
        if (signal.getWilliamsR() != null && IndicatorType.WILLIAMS_R.isDirectional()) {
            totalDirectional++;
            int wrDirection = signal.getWilliamsR().doubleValue() < -50 ? -1 : 1;
            if (wrDirection == compositeDirection) {
                agreeing++;
            }
        }

        // CCI
        if (signal.getCci() != null && IndicatorType.CCI.isDirectional()) {
            totalDirectional++;
            int cciDirection = signal.getCci().doubleValue() < 0 ? -1 : 1;
            if (cciDirection == compositeDirection) {
                agreeing++;
            }
        }

        if (totalDirectional == 0) {
            return null;
        }

        return (double) agreeing / totalDirectional * 100;
    }

    private List<ReasonDTO> computeReasons(SignalDTO signal) {
        List<ReasonDTO> reasons = new ArrayList<>();

        // RSI extreme
        if (signal.getRsi14() != null) {
            if (signal.getRsi14().doubleValue() < 30) {
                reasons.add(ReasonDTO.builder()
                        .factor("RSI")
                        .value(signal.getRsi14())
                        .interpretation("Oversold - potential reversal")
                        .build());
            } else if (signal.getRsi14().doubleValue() > 70) {
                reasons.add(ReasonDTO.builder()
                        .factor("RSI")
                        .value(signal.getRsi14())
                        .interpretation("Overbought - potential pullback")
                        .build());
            }
        }

        // MACD cross
        if (signal.getMacd() != null && signal.getMacdSignal() != null) {
            boolean bullishCross = signal.getMacd().compareTo(signal.getMacdSignal()) > 0
                    && signal.getMacdHistogram() != null && signal.getMacdHistogram().compareTo(BigDecimal.ZERO) > 0;
            boolean bearishCross = signal.getMacd().compareTo(signal.getMacdSignal()) < 0
                    && signal.getMacdHistogram() != null && signal.getMacdHistogram().compareTo(BigDecimal.ZERO) < 0;

            if (bullishCross) {
                reasons.add(ReasonDTO.builder()
                        .factor("MACD")
                        .value("bullish_cross")
                        .interpretation("MACD above signal line - bullish momentum")
                        .build());
            } else if (bearishCross) {
                reasons.add(ReasonDTO.builder()
                        .factor("MACD")
                        .value("bearish_cross")
                        .interpretation("MACD below signal line - bearish momentum")
                        .build());
            }
        }

        // Volume confirmed
        if (signal.isVolumeConfirmed()) {
            reasons.add(ReasonDTO.builder()
                    .factor("Volume")
                    .value(true)
                    .interpretation("Volume confirms signal direction")
                    .build());
        }

        // Volume breakout
        if (signal.isVolumeBreakout()) {
            reasons.add(ReasonDTO.builder()
                    .factor("Volume")
                    .value("breakout")
                    .interpretation("Volume breakout detected")
                    .build());
        }

        // Strong trend
        if (signal.getAdx() != null && signal.getAdx().doubleValue() > 25) {
            reasons.add(ReasonDTO.builder()
                    .factor("ADX")
                    .value(signal.getAdx())
                    .interpretation("Strong trend detected (ADX > 25)")
                    .build());
        }

        return reasons;
    }

    private int compareSignalStrength(String a, String b) {
        return Integer.compare(
                SIGNAL_STRENGTH.getOrDefault(a, 0),
                SIGNAL_STRENGTH.getOrDefault(b, 0)
        );
    }

    private WatchlistSummaryDTO buildSummary(List<WatchlistOpportunityDTO> opportunities) {
        int buy = 0, hold = 0, sell = 0;
        String strongest = null, weakest = null;

        for (WatchlistOpportunityDTO opp : opportunities) {
            String signal = opp.getSignal();
            if (signal != null) {
                if (signal.contains("BUY")) buy++;
                else if (signal.equals("HOLD")) hold++;
                else if (signal.contains("SELL")) sell++;
            }
        }

        if (!opportunities.isEmpty()) {
            strongest = opportunities.get(0).getSymbol();
            weakest = opportunities.get(opportunities.size() - 1).getSymbol();
        }

        return WatchlistSummaryDTO.builder()
                .totalStocks(opportunities.size())
                .buyOpportunities(buy)
                .holdOpportunities(hold)
                .sellOpportunities(sell)
                .strongestSignal(strongest)
                .weakestSignal(weakest)
                .build();
    }
}
