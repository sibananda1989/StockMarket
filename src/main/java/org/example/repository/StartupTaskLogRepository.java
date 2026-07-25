package org.example.repository;

import org.example.entity.StartupTaskLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for querying startup task execution logs.
 */
@Repository
public interface StartupTaskLogRepository extends CrudRepository<StartupTaskLog, Long> {

    List<StartupTaskLog> findByTaskIdAndRunDate(String taskId, LocalDate runDate);

    long countByRunDate(LocalDate runDate);

    @Query("SELECT DISTINCT l.taskId FROM StartupTaskLog l WHERE l.runDate = :runDate AND l.status = 'success'")
    List<String> findCompletedTaskIdsByRunDate(LocalDate runDate);

    StartupTaskLog findTopByTaskIdOrderByRunDateDescCompletedAtDesc(String taskId);
}
