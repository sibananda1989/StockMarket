package org.example.repository;

import org.example.entity.FiiDiiData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FiiDiiDataRepository extends JpaRepository<FiiDiiData, Long> {
    Optional<FiiDiiData> findTopByOrderByDateDesc();
    Optional<FiiDiiData> findByDate(java.time.LocalDate date);
}
