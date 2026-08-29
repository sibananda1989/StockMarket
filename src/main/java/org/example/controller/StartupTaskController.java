package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.ApiResponse;
import org.example.dto.StartupTaskDTO;
import org.example.entity.StartupTaskLog;
import org.example.repository.StartupTaskLogRepository;
import org.example.startup.StartupTask;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Controller for managing startup tasks that run on the landing page popup.
 * Tasks are discovered dynamically from Spring beans implementing {@link StartupTask}.
 *
 * Execution strategy:
 * - Yahoo-dependent tasks (data-sync, fundamental-data) run sequentially (rate-limited to 1 req/sec)
 * - DB-only tasks run in parallel for maximum throughput
 */
@RestController
@RequestMapping("/api/startup-tasks")
@RequiredArgsConstructor
@Slf4j
public class StartupTaskController {

    private final ApplicationContext applicationContext;
    private final StartupTaskLogRepository logRepository;
    private final Executor startupTaskExecutor;

    /**
     * Execution order for startup tasks (dependency-aware).
     * Tasks execute sequentially in this order regardless of selection.
     */
    private static final List<String> EXECUTION_ORDER = List.of(
            "portfolio-migration",
            "strategy-conditions",
            "score-params",
            "data-sync",
            "fundamental-data",
            "fiidii-data",
            "signal-accuracy",
            "indicator-calc",
            "signal-performance"
    );

    /**
     * Tasks in this group depend on rate-limited external APIs (Yahoo Finance, NSE).
     * They run sequentially to respect rate limits.
     */
    private static final Set<String> SEQUENTIAL_GROUP = Set.of(
            "data-sync",
            "fundamental-data",
            "fiidii-data"
    );

    /**
     * Categories to exclude from the popup (auto-run tasks).
     */
    private static final Set<String> EXCLUDED_CATEGORIES = Set.of("Config");

    @GetMapping
    public ResponseEntity<ApiResponse<List<StartupTaskDTO>>> getStartupTasks() {
        Map<String, StartupTask> taskMap = getTaskMap();
        LocalDate today = LocalDate.now();
        Set<String> completedToday = new HashSet<>(logRepository.findCompletedTaskIdsByRunDate(today));

        List<StartupTaskDTO> tasks = EXECUTION_ORDER.stream()
                .filter(taskMap::containsKey)
                .map(taskMap::get)
                .filter(task -> !EXCLUDED_CATEGORIES.contains(task.getCategory()))
                .filter(task -> !completedToday.contains(task.getId()))
                .map(task -> {
                    var builder = StartupTaskDTO.builder()
                            .id(task.getId())
                            .name(task.getName())
                            .description(task.getDescription())
                            .category(task.getCategory())
                            .defaultEnabled(task.defaultEnabled())
                            .required(task.isRequired());
                    
                    StartupTaskLog latestLog = logRepository.findTopByTaskIdOrderByRunDateDescCompletedAtDesc(task.getId());
                    if (latestLog != null) {
                        builder.lastRunDate(latestLog.getCompletedAt() != null ? latestLog.getCompletedAt().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")) : latestLog.getRunDate().toString());
                        builder.lastRunStatus(latestLog.getStatus());
                    }
                    
                    return builder.build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<Void>> runStartupTasks(@RequestBody List<String> taskIds) {
        Map<String, StartupTask> taskMap = getTaskMap();
        LocalDate today = LocalDate.now();

        log.info("Running {} startup tasks: {}", taskIds.size(), taskIds);

        // Split into rate-limited (sequential) and DB-only (parallel) groups
        List<String> sequentialTasks = taskIds.stream()
                .filter(SEQUENTIAL_GROUP::contains)
                .collect(Collectors.toList());
        List<String> parallelTasks = taskIds.stream()
                .filter(id -> !SEQUENTIAL_GROUP.contains(id))
                .collect(Collectors.toList());

        // Run sequential group as ONE async unit (preserves ordering)
        if (!sequentialTasks.isEmpty()) {
            CompletableFuture.runAsync(() ->
                    sequentialTasks.forEach(id -> runSingleTask(id, taskMap, today)),
                    startupTaskExecutor);
        }

        // Run each parallel task async (fire-and-forget)
        parallelTasks.forEach(id ->
                CompletableFuture.runAsync(() -> runSingleTask(id, taskMap, today), startupTaskExecutor));

        log.info("Startup tasks queued (async): {}", taskIds.size());
        return ResponseEntity.accepted().body(ApiResponse.success("Startup tasks queued", null));
    }

    @PostMapping("/run-one/{taskId}")
    public ResponseEntity<ApiResponse<String>> runOneTask(@PathVariable String taskId) {
        Map<String, StartupTask> taskMap = getTaskMap();
        LocalDate today = LocalDate.now();

        if (taskMap.get(taskId) == null) {
            return ResponseEntity.ok(ApiResponse.error("Unknown task: " + taskId));
        }

        CompletableFuture.runAsync(() -> runSingleTask(taskId, taskMap, today), startupTaskExecutor);
        return ResponseEntity.accepted().body(ApiResponse.success("Task queued", taskId));
    }

    private String runSingleTask(String taskId, Map<String, StartupTask> taskMap, LocalDate today) {
        StartupTask task = taskMap.get(taskId);
        if (task == null) {
            log.warn("Unknown task ID: {}", taskId);
            return "skipped";
        }

        if (!logRepository.findByTaskIdAndRunDate(taskId, today).isEmpty()) {
            log.info("Task {} already completed today, skipping", taskId);
            return "already-completed";
        }

        try {
            log.info("Executing task: {} ({})", task.getName(), taskId);
            task.execute();

            logRepository.save(StartupTaskLog.builder()
                    .taskId(taskId)
                    .runDate(today)
                    .status("success")
                    .completedAt(LocalDateTime.now())
                    .build());

            log.info("Task completed successfully: {}", taskId);
            return "success";
        } catch (Exception e) {
            log.error("Task failed: {} - {}", taskId, e.getMessage(), e);

            logRepository.save(StartupTaskLog.builder()
                    .taskId(taskId)
                    .runDate(today)
                    .status("failed")
                    .completedAt(LocalDateTime.now())
                    .build());
            return "failed";
        }
    }

    /**
     * Discovers all StartupTask beans from the application context.
     */
    private Map<String, StartupTask> getTaskMap() {
        return applicationContext.getBeansOfType(StartupTask.class).values().stream()
                .collect(Collectors.toMap(StartupTask::getId, task -> task));
    }
}
