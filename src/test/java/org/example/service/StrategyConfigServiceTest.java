package org.example.service;

import org.example.entity.StrategyConfig;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.StrategyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StrategyConfigServiceTest {

    @Mock
    private StrategyConfigRepository repository;

    @InjectMocks
    private StrategyConfigService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetActiveStrategyNames_AllActive() {
        when(repository.findAll()).thenReturn(List.of(
                new StrategyConfig("RSI", true, "RSI Strategy", 7),
                new StrategyConfig("MACD", true, "MACD Strategy", 7),
                new StrategyConfig("MA_CROSSOVER", true, "MA Crossover", 8),
                new StrategyConfig("BOLLINGER", true, "Bollinger Band", 6),
                new StrategyConfig("VOLUME", true, "Volume Strategy", 5)
        ));

        Set<String> active = service.getActiveStrategyNames();

        assertEquals(5, active.size());
        assertTrue(active.containsAll(Set.of("RSI", "MACD", "MA_CROSSOVER", "BOLLINGER", "VOLUME")));
    }

    @Test
    void testGetActiveStrategyNames_SomeInactive() {
        when(repository.findAll()).thenReturn(List.of(
                new StrategyConfig("RSI", true, "RSI Strategy", 7),
                new StrategyConfig("MACD", false, "MACD Strategy", 7),
                new StrategyConfig("MA_CROSSOVER", true, "MA Crossover", 8),
                new StrategyConfig("BOLLINGER", false, "Bollinger Band", 6),
                new StrategyConfig("VOLUME", true, "Volume Strategy", 5)
        ));

        Set<String> active = service.getActiveStrategyNames();

        assertEquals(3, active.size());
        assertTrue(active.containsAll(Set.of("RSI", "MA_CROSSOVER", "VOLUME")));
        assertFalse(active.contains("MACD"));
        assertFalse(active.contains("BOLLINGER"));
    }

    @Test
    void testGetActiveStrategyNames_AllInactive() {
        when(repository.findAll()).thenReturn(List.of(
                new StrategyConfig("RSI", false, "RSI Strategy", 7),
                new StrategyConfig("MACD", false, "MACD Strategy", 7),
                new StrategyConfig("MA_CROSSOVER", false, "MA Crossover", 8),
                new StrategyConfig("BOLLINGER", false, "Bollinger Band", 6),
                new StrategyConfig("VOLUME", false, "Volume Strategy", 5)
        ));

        Set<String> active = service.getActiveStrategyNames();

        assertTrue(active.isEmpty());
    }

    @Test
    void testGetActiveStrategyNames_EmptyRepo_SeedsFirst() {
        when(repository.findAll()).thenReturn(List.of());

        service.init();

        verify(repository).saveAll(anyList());
    }

    @Test
    void testToggleStrategy_ActivateInactive() {
        StrategyConfig config = new StrategyConfig("MACD", false, "MACD Strategy", 7);
        when(repository.findByStrategyName("MACD")).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyConfig result = service.toggleStrategy("MACD", true);

        assertTrue(result.isActive());
        verify(repository).save(config);
    }

    @Test
    void testToggleStrategy_DeactivateActive() {
        StrategyConfig config = new StrategyConfig("RSI", true, "RSI Strategy", 7);
        when(repository.findByStrategyName("RSI")).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyConfig result = service.toggleStrategy("RSI", false);

        assertFalse(result.isActive());
        verify(repository).save(config);
    }

    @Test
    void testToggleStrategy_NotFound_Throws() {
        when(repository.findByStrategyName("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.toggleStrategy("UNKNOWN", true));
    }

    @Test
    void testInit_FreshDb_SeedsAllTwelveStrategies() {
        when(repository.findAll()).thenReturn(List.of());

        service.init();

        ArgumentCaptor<List<StrategyConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<String> seededNames = captor.getValue().stream()
                .map(StrategyConfig::getStrategyName)
                .toList();
        assertEquals(12, seededNames.size());
        assertEquals(new HashSet<>(Set.of("RSI", "MACD", "MA_CROSSOVER", "BOLLINGER", "VOLUME",
                "CANDLESTICK_AT_SUPPORT", "CANDLESTICK_AT_RESISTANCE", "BREAKOUT",
                "CANDLESTICK_PATTERN", "LIQUIDITY", "SMA44_PULLBACK", "EMA_CROSSOVER")), new HashSet<>(seededNames));
    }

    @Test
    void testInit_LegacyRows_BackfillsMissingWithoutTouchingExisting() {
        StrategyConfig rsi = new StrategyConfig("RSI", true, "RSI Strategy", 7);
        StrategyConfig macd = new StrategyConfig("MACD", false, "MACD Strategy", 7); // deliberately disabled
        StrategyConfig maCrossover = new StrategyConfig("MA_CROSSOVER", true, "MA Crossover", 8);
        StrategyConfig bollinger = new StrategyConfig("BOLLINGER", true, "Bollinger Band", 6);
        StrategyConfig volume = new StrategyConfig("VOLUME", true, "Volume Strategy", 5);
        when(repository.findAll()).thenReturn(List.of(rsi, macd, maCrossover, bollinger, volume));

        service.init();

        ArgumentCaptor<List<StrategyConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<String> inserted = captor.getValue().stream()
                .map(StrategyConfig::getStrategyName)
                .toList();
        assertEquals(7, inserted.size());
        assertEquals(new HashSet<>(Set.of("CANDLESTICK_AT_SUPPORT", "CANDLESTICK_AT_RESISTANCE",
                "BREAKOUT", "CANDLESTICK_PATTERN", "LIQUIDITY", "SMA44_PULLBACK", "EMA_CROSSOVER")), new HashSet<>(inserted));
        // Existing rows untouched: never saved, never re-enabled.
        verify(repository, never()).save(rsi);
        verify(repository, never()).save(macd);
        verify(repository, never()).save(maCrossover);
        verify(repository, never()).save(bollinger);
        verify(repository, never()).save(volume);
        assertFalse(macd.isActive());
    }

    @Test
    void testInit_AllTwelvePresent_NoInserts() {
        when(repository.findAll()).thenReturn(allCanonicalConfigs());

        service.init();

        verify(repository, never()).saveAll(anyList());
    }

    private List<StrategyConfig> allCanonicalConfigs() {
        return List.of(
                new StrategyConfig("RSI", true, "RSI Strategy", 7),
                new StrategyConfig("MACD", true, "MACD Strategy", 7),
                new StrategyConfig("MA_CROSSOVER", true, "MA Crossover", 8),
                new StrategyConfig("BOLLINGER", true, "Bollinger Band", 6),
                new StrategyConfig("VOLUME", true, "Volume Strategy", 5),
                new StrategyConfig("CANDLESTICK_AT_SUPPORT", true, "Candlestick at Support", 4),
                new StrategyConfig("CANDLESTICK_AT_RESISTANCE", true, "Candlestick at Resistance", 4),
                new StrategyConfig("BREAKOUT", true, "Breakout Strategy", 5),
                new StrategyConfig("CANDLESTICK_PATTERN", true, "Candlestick Patterns", 4),
                new StrategyConfig("LIQUIDITY", true, "Liquidity Assessment", 4),
                new StrategyConfig("SMA44_PULLBACK", true, "SMA44 Pullback Bounce", 8),
                new StrategyConfig("EMA_CROSSOVER", true, "EMA Crossover", 7)
        );
    }

    @Test
    void testGetAllConfigs() {
        List<StrategyConfig> expected = List.of(
                new StrategyConfig("BOLLINGER", true, "Bollinger Band", 6),
                new StrategyConfig("MACD", true, "MACD Strategy", 7)
        );
        when(repository.findAllByOrderByStrategyNameAsc()).thenReturn(expected);

        List<StrategyConfig> configs = service.getAllConfigs();

        assertEquals(expected, configs);
    }
}
