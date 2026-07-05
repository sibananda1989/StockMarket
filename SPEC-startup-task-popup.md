# Spec: Startup Task Popup

**Status:** Ready for Implementation
**Date:** 2026-06-30
**Author:** Siba

## Problem

On every app restart, 9 startup tasks auto-run via `@EventListener(ApplicationReadyEvent.class)`. This causes:
- Slow startup (tasks fetch data from external APIs: Yahoo Finance, NSE)
- Unnecessary API calls (tasks run even if data is already fresh)

## Solution

Replace auto-run with a **blocking popup on index.html**. User selects which tasks to run, clicks "Run Selected", tasks execute sequentially, then dashboard loads.

**If all tasks done today → no popup, dashboard loads immediately.**

## Scope

### Tasks Moving to Popup (9)

| ID | Task Class | Category | Required | Description |
|----|-----------|----------|----------|-------------|
| portfolio-migration | PortfolioMigrationStartupTask | Portfolio | Yes | Migrate legacy holdings to multi-portfolio schema |
| portfolio-snapshot | PortfolioSnapshotStartupTask | Portfolio | No | Ensure today's portfolio snapshots exist |
| data-sync | StartupDataSyncTask | Data Sync | No | Sync 7 days of price history for all stocks |
| fundamental-data | FundamentalStartupTask | Data Sync | No | Refresh fundamental data for stocks >7 days stale |
| fiidii-data | FiiDiiStartupTask | Data Sync | No | Fetch latest FII/DII data from NSE |
| signal-cache | SignalStartupTask | Signals | No | Clear signal cache and backfill records |
| signal-accuracy | SignalAccuracyStartupTask | Signals | No | Create historical signal records and mark accuracy |
| indicator-calc | IndicatorStartupTask | Indicators | No | Calculate technical indicators for all stocks |
| signal-performance | SignalPerformanceScheduler | Signals | No | Compute historical forward returns for signals |

### Tasks Staying Auto-Run (2)

| Task | Annotation | Why |
|------|------------|-----|
| StrategyConfigService | @PostConstruct | Seeds 5 strategy config rows |
| StrategyConditionService | @EventListener | Seeds 25 condition rules |

### SignalPerformanceScheduler Special Handling

- **Disable** `@Scheduled(cron = "0 30 2 * * *")` — no more daily auto-run
- **Keep** `@EventListener` → moves to popup as `execute()` method
- Task only runs when triggered via popup

## Technical Design

### New Files (5)

#### 1. `src/main/java/org/example/startup/StartupTask.java`

```java
package org.example.startup;

public interface StartupTask {
    String getId();
    String getName();
    String getDescription();
    String getCategory();
    boolean defaultEnabled();
    boolean isRequired();
    void execute();
}
```

#### 2. `src/main/java/org/example/entity/StartupTaskLog.java`

```java
package org.example.entity;

@Entity
@Table(name = "startup_task_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartupTaskLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "status", nullable = false)
    private String status; // "success", "failed"

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
```

#### 3. `src/main/java/org/example/repository/StartupTaskLogRepository.java`

```java
package org.example.repository;

@Repository
public interface StartupTaskLogRepository extends CrudRepository<StartupTaskLog, Long> {
    List<StartupTaskLog> findByTaskIdAndRunDate(String taskId, LocalDate runDate);
    long countByRunDate(LocalDate runDate);
}
```

#### 4. `src/main/java/org/example/dto/StartupTaskDTO.java`

```java
package org.example.dto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartupTaskDTO {
    private String id;
    private String name;
    private String description;
    private String category;
    private boolean defaultEnabled;
    private boolean required;
    private boolean completedToday;
}
```

#### 5. `src/main/java/org/example/controller/StartupTaskController.java`

```java
package org.example.controller;

@RestController
@RequestMapping("/api/startup-tasks")
@RequiredArgsConstructor
public class StartupTaskController {

    private final StartupTaskLogRepository logRepository;

    // GET /api/startup-tasks
    // Returns List<StartupTaskDTO> with completedToday flag
    // Filters out "Config" category tasks (StrategyConfigService, StrategyConditionService)

    // POST /api/startup-tasks/run
    // Accepts: List<String> taskIds
    // Executes tasks sequentially in dependency order
    // Returns: ApiResponse<Void>
}
```

### Modified Files (9)

Each startup task class needs:
1. Implement `StartupTask` interface
2. Remove `@EventListener(ApplicationReadyEvent.class)`
3. Remove `@Async("syncExecutor")` (controller handles async)
4. Add `@Override` methods: `getId()`, `getName()`, `getDescription()`, `getCategory()`, `defaultEnabled()`, `isRequired()`
5. Rename `onApplicationReady()` to `execute()`

### Frontend Changes

#### `src/main/resources/static/index.html`

Add blocking modal after portfolio modal:

```html
<!-- Startup Tasks Modal -->
<div id="startupModal" class="hidden fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <!-- Task list grouped by category -->
    <!-- Portfolio (required: migration), Data Sync, Signals, Indicators -->
    <!-- Buttons: Select All, Deselect All, Skip, Run Selected -->
</div>

<!-- Progress Overlay -->
<div id="startupProgressOverlay" class="hidden fixed inset-0 z-50 ...">
    <!-- Progress bar, task list, cancel button -->
</div>
```

**Modal behavior:**
- On page load: `GET /api/startup-tasks`
- If all `completedToday === true` → skip modal, load dashboard
- Otherwise → show modal, block dashboard
- User clicks "Run Selected" → `POST /api/startup-tasks/run` with selected IDs
- On success → hide modal, load dashboard
- "Skip" button → hide modal, load dashboard (tasks don't run)

#### `src/main/resources/static/js/api.js`

Add two functions:

```javascript
async function getStartupTasks() {
    return apiCall('/startup-tasks');
}

async function runStartupTasks(taskIds) {
    return apiCall('/startup-tasks/run', {
        method: 'POST',
        body: JSON.stringify(taskIds)
    });
}
```

### Execution Order

Tasks execute sequentially in this order (dependencies):

1. **Portfolio** (must run first — other tasks may reference holdings)
   - portfolio-migration (required, always runs if selected)
   - portfolio-snapshot
2. **Data Sync** (price data needed for signals/indicators)
   - data-sync
   - fundamental-data
   - fiidii-data
3. **Signals** (depends on price data)
   - signal-cache
   - signal-accuracy
4. **Indicators** (depends on price data)
   - indicator-calc
5. **Signal Performance** (depends on indicators)
   - signal-performance

### Database

**Table:** `startup_task_log` (auto-created by Hibernate `ddl-auto=update`)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT AUTO_INCREMENT | Primary key |
| task_id | VARCHAR(50) | Task identifier (e.g., "portfolio-migration") |
| run_date | DATE | Date task was run |
| status | VARCHAR(20) | "success" or "failed" |
| completed_at | TIMESTAMP | When task completed |

**Daily reset:** Query groups by `run_date = CURDATE()`. If count = 9 (all tasks done today), no popup.

### API Endpoints

#### `GET /api/startup-tasks`

**Response:**
```json
{
  "status": "success",
  "data": [
    {
      "id": "portfolio-migration",
      "name": "Portfolio Migration",
      "description": "Migrate legacy holdings to multi-portfolio schema",
      "category": "Portfolio",
      "defaultEnabled": true,
      "required": true,
      "completedToday": false
    },
    ...
  ]
}
```

**Logic:**
1. Discover all `StartupTask` beans
2. Filter out "Config" category (StrategyConfigService, StrategyConditionService)
3. Query `startup_task_log` for today's completions
4. Return list with `completedToday` flag

#### `POST /api/startup-tasks/run`

**Request:**
```json
["portfolio-migration", "data-sync", "signal-cache"]
```

**Response:**
```json
{
  "status": "success",
  "message": "Startup tasks completed"
}
```

**Logic:**
1. Validate task IDs exist
2. Execute sequentially in dependency order
3. For each task: wrap in try-catch, log result to `startup_task_log`
4. Return success even if some tasks fail (failures logged individually)

## Edge Cases

1. **Task already done today** — `completedToday: true`, checkbox disabled in UI
2. **Task fails** — Log as "failed" in `startup_task_log`, continue with next task
3. **User skips** — Modal closes, tasks don't run, dashboard loads
4. **App restart same day** — If all tasks done, no popup. If some done, show remaining.
5. **PortfolioMigration required** — Checkbox always checked, can't uncheck
6. **Concurrent access** — Only one user at a time (solo dev, not a concern)

## Testing

1. **Unit tests:** `StartupTaskController` tests for GET/POST endpoints
2. **Integration test:** Full flow — startup, modal appears, run tasks, verify logs
3. **Manual test:** Start app, verify popup, run tasks, restart app, verify no popup

## Success Criteria

- [ ] App starts without auto-running the 9 tasks
- [ ] Popup appears on first visit of the day
- [ ] User can select/deselect tasks
- [ ] Tasks run sequentially when "Run Selected" clicked
- [ ] Progress shown during execution
- [ ] Dashboard loads after completion
- [ ] Second visit same day: no popup
- [ ] StrategyConfigService and StrategyConditionService still auto-run
- [ ] SignalPerformanceScheduler daily cron disabled
