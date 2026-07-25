package org.example.scheduler;

import org.example.service.StrategyDailyWeightService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyDailyWeightSchedulerTest {

    @Mock
    private StrategyDailyWeightService weightService;

    @InjectMocks
    private StrategyDailyWeightScheduler scheduler;

    @Test
    void testComputeDailyWeights() {
        scheduler.computeDailyWeights();

        verify(weightService).computeForAllStocks(LocalDate.now());
    }
}
