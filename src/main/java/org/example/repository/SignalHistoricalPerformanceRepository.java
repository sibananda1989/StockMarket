package org.example.repository;

import org.example.entity.SignalHistoricalPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SignalHistoricalPerformanceRepository extends JpaRepository<SignalHistoricalPerformance, Long> {

    List<SignalHistoricalPerformance> findByRecommendationAndDaysForwardIn(String recommendation, List<Integer> daysForward);

    Optional<SignalHistoricalPerformance> findByRecommendationAndDaysForward(String recommendation, Integer daysForward);
}
