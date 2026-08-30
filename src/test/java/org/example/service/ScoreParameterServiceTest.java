package org.example.service;

import org.example.entity.ScoreParameterConfig;
import org.example.repository.ScoreParameterConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the "score parameter toggle" feature in {@link ScoreParameterService}.
 *
 * The repository is backed by an in-memory fake store so the service's real
 * logic (seeding, disable/enable, reset, disabled-factor queries) is exercised
 * deterministically without requiring a live MySQL database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreParameterServiceTest {

    @Mock
    private ScoreParameterConfigRepository repository;

    @InjectMocks
    private ScoreParameterService service;

    // In-memory fake store simulating repository persistence.
    // Reassigned (not just cleared) in setUp so no state leaks across test methods
    // regardless of the JUnit test-instance lifecycle.
    private Map<String, ScoreParameterConfig> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        // Fresh store per test so each test starts from a clean, seeded state.
        store = new ConcurrentHashMap<>();

        // Wire the repository mock to an in-memory store so the service behaves realistically.
        when(repository.count()).thenAnswer(inv -> (long) store.size());
        when(repository.findAll()).thenAnswer(inv -> new ArrayList<>(store.values()));
        when(repository.saveAll(anyList())).thenAnswer(inv -> {
            List<ScoreParameterConfig> list = inv.getArgument(0);
            for (ScoreParameterConfig c : list) {
                store.put(c.getParamKey(), copy(c));
            }
            return list;
        });
        when(repository.findByParamKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return Optional.ofNullable(store.get(key));
        });
        when(repository.findByEnabledFalse()).thenAnswer(inv ->
                store.values().stream().filter(c -> !c.isEnabled()).collect(Collectors.toList()));
        when(repository.save(any(ScoreParameterConfig.class))).thenAnswer(inv -> {
            ScoreParameterConfig c = inv.getArgument(0);
            store.put(c.getParamKey(), copy(c));
            return c;
        });

        // Seed the 35-row SEED_LIST so every test starts from a populated store.
        service.seedIfEmpty();
    }

    @Test
    void seedIsIdempotent() {
        // The seed already ran once in setUp; calling it again must NOT duplicate rows.
        service.seedIfEmpty();

        assertEquals(35, repository.count(), "store should contain exactly the 35 seeded rows");
        verify(repository, times(1)).saveAll(anyList());

        // No duplicate paramKeys.
        assertEquals(35, store.keySet().size());
        assertEquals(35, store.values().stream()
                .map(ScoreParameterConfig::getParamKey)
                .distinct()
                .count());
    }

    @Test
    void getDisabledLegacyFactors_emptyByDefault() {
        assertTrue(service.getDisabledLegacyFactors().isEmpty(),
                "no legacy factor should be disabled right after seeding");
    }

    @Test
    void getDisabledLegacyFactors_afterDisable() {
        service.setEnabled("MOM_RSI14", false);

        Set<String> disabled = service.getDisabledLegacyFactors();
        assertEquals(1, disabled.size());
        assertTrue(disabled.contains("MOM_RSI14"));
    }

    @Test
    void setEnabled_unknownKey_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setEnabled("NOPE", true),
                "setEnabled must reject unknown paramKeys");
    }

    @Test
    void resetToDefaults_reEnablesAll() {
        service.setEnabled("MOM_RSI14", false);
        service.setEnabled("MOD_ADX_MULTIPLIER", false);
        service.setEnabled("POST_FII_DII", false);
        assertFalse(service.getDisabledLegacyFactors().isEmpty(),
                "three factors should be disabled before reset");

        service.resetToDefaults();

        assertTrue(service.getDisabledLegacyFactors().isEmpty(),
                "reset must re-enable every factor");
        for (ScoreParameterConfig c : service.getAll()) {
            assertTrue(c.isEnabled(), "all params should be enabled after reset: " + c.getParamKey());
        }
    }

    /**
     * Returns an independent copy so mutations performed by the service (e.g. via
     * {@code setEnabled}) never leak back into the static {@code SEED_LIST} held by
     * {@link ScoreParameterService} — important because the same JVM runs many tests.
     */
    private ScoreParameterConfig copy(ScoreParameterConfig c) {
        return ScoreParameterConfig.builder()
                .id(c.getId())
                .paramKey(c.getParamKey())
                .category(c.getCategory())
                .displayName(c.getDisplayName())
                .description(c.getDescription())
                .enabled(c.isEnabled())
                .scoreSystem(c.getScoreSystem())
                .build();
    }
}
