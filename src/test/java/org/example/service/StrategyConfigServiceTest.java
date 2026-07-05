package org.example.service;

import org.example.entity.StrategyConfig;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.StrategyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
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
        when(repository.count()).thenReturn(0L);

        service.init();

        verify(repository).saveAll(any());
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
    void testSeedIfEmpty_AlreadyPopulated() {
        when(repository.count()).thenReturn(5L);

        service.init();

        verify(repository, never()).saveAll(any());
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
