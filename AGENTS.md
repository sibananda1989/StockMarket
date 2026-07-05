# AGENTS.md — Stock Market Analysis Platform

## Strict Focus & Anti-Distraction Rules

To avoid waste of time, scope creep, and scanning of unwanted files, ALL agents MUST strictly follow these rules:
1. **Zero Scouting / Precise Access Only**: Never run open-ended `glob` or `grep` searches unless absolutely necessary to locate a specific requested class. If the user tells you the file path or class name, go straight to it.
2. **Stay Inside the Requested Module**: Do not read files or explore packages outside of the target code requested by the user. "Checking for context" in unrelated files is strictly prohibited.
3. **No Unsolicited Refactoring or Cleanup**: Never "clean up", optimize, format, or refactor surrounding code unless the user explicitly requested it. Fix *only* the specific bug/feature requested.
4. **Minimal Tool Calls**: Minimize reads and searches. Read only the specific section of the file needed (use targeted offsets/limits if files are large).
5. **No Speculative Dependency Checks**: Never spend time checking dependencies, imports, or project build configurations unless a build error explicitly requires you to do so.

## Quick Start

```bash
# Run app (port 8080, MySQL required)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run all tests (with progress bar)
./run-tests.sh

# Run specific test class
mvn test -Dtest=SignalServiceTest

# Run specific test method
mvn test -Dtest=SignalServiceTest#testComputeWeightedScore
```

## Project Skills (`.opencode/skills/`)

Specialized workflows for this codebase. Load with `/skill <name>` or invoke when the task matches.

### Multi-Agent Workflow Skills

| Skill | Use For |
|-------|---------|
| `team-lead` | Coordinate multi-agent workflow — orchestrate planning, feasibility, implementation, review, and testing stages |
| `planning-agent` | Analyze requirements — identify affected modules, produce implementation plan, document risks and assumptions |
| `feasibility-agent` | Validate implementation plans — check technical feasibility, architecture conflicts, backward compatibility |
| `implementation-agent` | Implement approved features — write code following plan exactly, follow project conventions |
| `code-review-agent` | Critical code review — find defects, verify quality, approve or reject code |
| `testing-agent` | Comprehensive testing — verify functionality, add/update tests, produce test coverage report |

### Existing Project Skills

| Skill | Use For |
|-------|---------|
| `add-endpoint` | New REST API endpoint — controller + DTO + service + repository wiring |
| `add-indicator` | New technical indicator — calculator + IndicatorType enum + signal scoring + tests |
| `add-strategy` | New trading strategy in the multi-strategy engine — TradingStrategy + aggregator + config + tests |
| `add-frontend-page` | New frontend page/feature — HTML + IIFE JS module + api.js wrapper + nav registration |
| `run-tests` | Running/debugging/writing tests — backend JUnit + Playwright E2E |
| `trace-signal` | Debugging why a stock got a particular buy/sell/hold signal — 16-factor scoring walkthrough |

## Stack

- **Backend**: Spring Boot 3.2.5, Java 17, MySQL (localhost:3306, user: root/root)
- **Frontend**: Static HTML + vanilla JS served from `src/main/resources/static/`
- **DB**: `spring.jpa.hibernate.ddl-auto=update` — Hibernate creates/alters tables automatically. No manual SQL migrations for new tables. Manual migrations in `src/main/resources/migrations/` only for column width changes (ddl-auto doesn't widen existing MySQL columns).

## Architecture

```
src/main/java/org/example/
  entity/          — JPA entities (22 tables)
  repository/      — Spring Data repos
  service/         — Business logic
    calculator/    — Technical indicator calculators (run sequentially per stock)
  strategy/
    base/          — Base classes (TradingStrategy)
    model/         — Strategy result models (StrategyResult, AggregatedSignalResult)
    impl/          — Strategy implementations (RSI, MACD, MA_CROSSOVER, BOLLINGER, VOLUME, CANDLESTICK)
    engine/        — Multi-strategy signal engine (MultiStrategySignalEngine)
    aggregator/    — Signal aggregation (StrategySignalAggregator)
    config/        — Strategy configuration
  controller/      — REST endpoints (returns ApiResponse<T> wrapper)
  dto/             — Request/response DTOs
  startup/         — @EventListener(ApplicationReadyEvent.class) for seeding
src/main/resources/static/
  js/api.js        — All API call wrappers
  strategy-manager.js — IIFE module pattern (no globals)
  *.html           — One page per feature
```

## Critical Gotchas

### MySQL Reserved Words
`signal` is a MySQL reserved word. Entity fields must use `@Column(name = "signal_type")` and map to/from DTO's `signal` field in the service layer.

### @Transactional on Derived Queries
Any method calling Spring Data derived `deleteBy*` queries (e.g., `deleteByStrategyName`) **must** have `@Transactional` or the call throws 500. CrudRepository's `deleteAll()`/`saveAll()` work without explicit `@Transactional`.

### Static Resources
Spring Boot serves `src/main/resources/static/` directly. Edit files there — no build step. Browser may cache; add `?v=<timestamp>` query param or restart app to clear.

### Frontend JS Conventions
- All frontend JS uses IIFE pattern: `(function(){ ... })();`
- No global variables. State lives inside the IIFE closure.
- API wrappers live in `js/api.js`. Import via function name (loaded by HTML script tags).
- Toggle switches: avoid `pointer-events-none` on parent containers — it blocks clicks on child elements.

### Seed / DDL Timing
`@PostConstruct` runs before Hibernate DDL. Use `@EventListener(ApplicationReadyEvent.class)` for seeding that depends on tables existing.

## Signal Engine

**Scoring thresholds**:
| Signal | Score |
|--------|-------|
| STRONG BUY | ≥ 7 |
| BUY | ≥ 5 |
| HOLD | -4 to +4 |
| SELL | ≤ -5 |
| STRONG SELL | ≤ -7 |

**ADX multiplier**: < 15 → 0.3, 15–25 → linear 0.5–0.7, 25–35 → linear 0.7–1.0, ≥ 35 → 1.0, counter-trend → ×0.7

**Config flags**: `signal.gate.high-confidence.enabled` (default: false)

**Cache**: `@Cacheable("signals")` — takes effect only after app restart.

## User Context

- Stock **buyer** (not trader) — signals should be buyer-oriented
- Multiple portfolios (Dhan, Ammu Groww, Groww Siba) with overlapping holdings
- CSV import is primary stock addition method

## Key Files to Read Before Modifying

| Area | Files |
|------|-------|
| Signal scoring | `SignalService.java`, `SignalDTO.java` |
| Multi-strategy aggregation | `StrategySignalAggregator.java`, `MultiStrategySignalEngine.java`, `StrategyResult.java`, `AggregatedSignalResult.java` |
| Strategy implementations | `src/main/java/org/example/strategy/impl/` |
| Strategy frontend | `strategy-manager.js`, `strategy.html`, `js/api.js` |
| Indicators | `src/main/java/org/example/service/calculator/` |
| DB entities | `src/main/java/org/example/entity/` |
| REST API | `src/main/java/org/example/controller/` |

## Test Count

~37 strategy tests, ~99 total (15 integration + 84 unit). Run `mvn test` to verify.

## Multi-Agent Workflow

### Overview

This project uses a **dynamic multi-agent software engineering workflow** with 4 main agents and specialized subagents. The Team Lead intelligently determines which agents are needed for each task.

### Architecture

```
User Request
    ↓
[Team Lead] - Dynamic workflow coordinator
    ↓
┌─────────────────────────────────────────────────────┐
│ Main Agents (4):                                    │
│ • team-lead - Workflow coordinator                 │
│ • plan - Planning & analysis coordinator             │
│ • build - Implementation & testing coordinator       │
│ • stock-analyzer - Stock market analysis            │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ Subagents (6):                                     │
│ • planning-agent - Requirements analysis             │
│ • impact-agent - Impact analysis                     │
│ • feasibility-agent - Technical validation           │
│ • implementation-agent - Code implementation         │
│ • code-review-agent - Critical code review           │
│ • testing-agent - Comprehensive testing              │
└─────────────────────────────────────────────────────┘
    ↓
Quality Gates Validation
    ↓
Final Result
```

### Main Agents

| Agent | Purpose | When to Use |
|-------|---------|-------------|
| `team-lead` | Workflow coordinator | Always - coordinates all work |
| `plan` | Planning coordinator | New features, major refactoring, architecture changes |
| `build` | Build coordinator | Code implementation, bug fixes, feature additions |
| `stock-analyzer` | Stock market analysis | Stock analysis, signal debugging, trading decisions |

### Subagents

**Plan Agent Subagents:**
- `planning-agent` - Requirements analysis and implementation planning
- `impact-agent` - Impact analysis for code changes
- `feasibility-agent` - Technical validation and feasibility checking

**Build Agent Subagents:**
- `implementation-agent` - Code implementation following approved plans
- `code-review-agent` - Critical code review and defect finding
- `testing-agent` - Comprehensive testing and coverage verification

### Dynamic Routing

The Team Lead decides which main agents are required for each request:

| Request Type | Agents to Invoke |
|--------------|------------------|
| **Stock Analysis/Question** | `stock-analyzer` → Return explanation |
| **Simple Bug Fix** | `build` → Return fix summary |
| **Documentation Update** | `build` → Return success message |
| **New Feature** | `plan` → `build` → Return implementation summary |
| **Major Refactoring** | `plan` → `build` → Return implementation summary |
| **Architecture Change** | `plan` → `build` → Return implementation summary |

### Quality Gates

Before returning results, all quality gates must pass:

1. **Implementation Quality Gates**
   - Implementation matches requirements exactly
   - All impacted files have been updated
   - Configuration is updated if required
   - Documentation is updated if required
   - Tests are updated if required

2. **Code Review Quality Gates**
   - Code review passes (no critical defects)
   - No regressions introduced
   - No edge cases missed
   - Code follows project conventions
   - Maintainability is acceptable

3. **Testing Quality Gates**
   - All existing tests still pass
   - New tests added for new functionality
   - Regression tests added
   - Backward compatibility verified
   - Test coverage is adequate

### When to Use Multi-Agent Workflow

Use the multi-agent workflow for:
- New features
- Major enhancements
- Architecture changes
- Refactoring affecting multiple modules
- Database schema changes
- API endpoint additions
- Security-sensitive changes

### Quick Start

For simple tasks (bug fixes, small changes), use existing project skills directly. For complex work requiring multiple stages, invoke the Team Lead agent.

### Agent Files

**Main Agent Files (`.opencode/agent/`):**
- `team-lead.md` - Team Lead coordination
- `plan.md` - Planning coordinator
- `build.md` - Build coordinator
- `stock-analyzer.md` - Stock market analysis

**Subagent Files (`.opencode/agent/subagents/`):**
- `planning-agent.md` - Requirements analysis
- `impact-agent.md` - Impact analysis
- `feasibility-agent.md` - Technical validation
- `implementation-agent.md` - Code implementation
- `code-review-agent.md` - Code review
- `testing-agent.md` - Testing and verification

**Skill Files (`.opencode/skills/`):**
- `team-lead/SKILL.md` - Team Lead coordination
- `plan/SKILL.md` - Planning coordinator
- `build/SKILL.md` - Build coordinator

**Subagent Skill Files (`.opencode/skills/subagents/`):**
- `planning-agent/SKILL.md` - Requirements analysis
- `impact-agent/SKILL.md` - Impact analysis
- `feasibility-agent/SKILL.md` - Technical validation
- `implementation-agent/SKILL.md` - Code implementation
- `code-review-agent/SKILL.md` - Code review
- `testing-agent/SKILL.md` - Testing and verification

### Dynamic Workflow Examples

**Example 1: Question about Provider Fallback**
```
Team Lead
→ stock-analyzer (understand provider fallback logic)
→ Return explanation to user
```

**Example 2: Fix NullPointerException**
```
Team Lead
→ build (delegates to implementation-agent → code-review-agent → testing-agent)
→ Return fix summary to user
```

**Example 3: Add Timeout Support**
```
Team Lead
→ plan (delegates to impact-agent → planning-agent → feasibility-agent)
→ build (delegates to implementation-agent → code-review-agent → testing-agent)
→ Return implementation summary to user
```

**Example 4: Update README**
```
Team Lead
→ build (delegates to implementation-agent)
→ Return success message to user
```

### Team Lead Responsibilities

1. **Understand the user's intent** - Read the request carefully
2. **Classify the request** - Determine the request type
3. **Decide which main agents are required** - Select appropriate agents
4. **Delegate work only to those agents** - Don't invoke unnecessary agents
5. **Collect results from each agent** - Aggregate outputs
6. **Validate the outputs** - Check quality gates
7. **If an agent's output is insufficient, send the task back for refinement** - Don't proceed with incomplete work
8. **Continue until the work satisfies all quality gates** - Ensure quality
9. **Return the final response to the user** - Deliver results

### Team Lead Never

- Blindly executes every agent in a fixed sequence
- Implements code directly (unless explicitly instructed)
- Skips quality gates
- Accepts incomplete work
- Sends vague feedback

### Team Lead Always

- Delegates only when beneficial
- Optimizes for correctness first, efficiency second
- Preserves existing functionality
- Minimizes regressions
- Treats every implementation as potentially incomplete until validated
- Behaves like an experienced engineering manager

### Extensibility

New specialist agents can be added without changing the Team Lead's overall orchestration logic. The Team Lead discovers available agents from configuration rather than hard-coding them.

### Agent Configuration

Agents are configured in `.opencode/opencode.json`:

```json
{
  "agent": {
    "team-lead": {
      "model": "naraya/claude-sonnet-4.5"
    },
    "plan": {
      "model": "naraya/mistral-large"
    },
    "build": {
      "model": "naraya/mimo-v2.5-pro-free"
    },
    "stock-analyzer": {
      "model": "naraya/mimo-v2.5-pro-free"
    }
  }
}
```
