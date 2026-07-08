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

### Lifecycle Phase Skills

| Skill | Phase | Use For |
|-------|-------|---------|
| `team-lead` | Orchestration | Coordinate multi-agent workflow across all lifecycle phases |
| `define` | Define | Spec-driven development — clarify requirements, surface assumptions, write spec document |
| `plan` | Plan | Planning — impact analysis, feasibility validation, implementation plan |
| `build` | Build | Implementation — thin vertical slicing, delegate to implementation-agent |
| `verify` | Verify | Testing + security hardening + performance checks |
| `review` | Review | Critical code review + documentation review + plan adherence |
| `ship` | Ship | Changelog + release summary + deployment notes |

### Subagent Skills

| Skill | Used By | Use For |
|-------|---------|---------|
| `planning-agent` | Plan | Analyze requirements, produce implementation plan, document risks |
| `impact-agent` | Plan | Impact analysis for code changes |
| `feasibility-agent` | Plan | Technical validation and feasibility checking |
| `implementation-agent` | Build | Implement approved features following plan exactly |
| `code-review-agent` | Review | Critical code review, defect finding, quality verification |
| `testing-agent` | Verify | Comprehensive testing, coverage verification, backward compatibility |

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

## Lifecycle-Driven Multi-Agent Workflow

### Overview

This project uses a **lifecycle-driven multi-agent workflow** with 6 sequential phases. The Team Lead routes requests through the appropriate phases based on complexity.

### Architecture

```
User Request
    ↓
[Team Lead] — Routes to lifecycle phases
    ↓
┌─────────────────────────────────────────────────────────┐
│ Lifecycle Phase Coordinators (6):                       │
│ • define-agent — Spec-driven development                │
│ • plan-agent — Planning & analysis                      │
│ • build-agent — Implementation with vertical slicing    │
│ • verify-agent — Testing + Security + Performance       │
│ • review-agent — Code review + Documentation review     │
│ • ship-agent — Changelog + Release                      │
│ • stock-analyzer — Stock market analysis (domain expert) │
└─────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────┐
│ Subagents (6):                                          │
│ • planning-agent, impact-agent, feasibility-agent       │
│   → Used by Plan phase                                  │
│ • implementation-agent                                   │
│   → Used by Build phase                                 │
│ • code-review-agent                                      │
│   → Used by Review phase                                │
│ • testing-agent                                          │
│   → Used by Verify phase                                │
└─────────────────────────────────────────────────────────┘
    ↓
Quality Gates Validation
    ↓
Final Result
```

### Phase Coordinators

| Agent | Phase | Purpose |
|-------|-------|---------|
| `team-lead` | Orchestration | Routes requests to lifecycle phases, reviews outputs |
| `define-agent` | Define | Spec-driven: clarify requirements, write spec, validate with user |
| `plan-agent` | Plan | Impact analysis, feasibility, implementation plan |
| `build-agent` | Build | Vertical slicing, delegate to implementation-agent |
| `verify-agent` | Verify | Testing + security hardening + performance checks |
| `review-agent` | Review | Critical code review + doc review + plan adherence |
| `ship-agent` | Ship | Changelog + release summary + deployment notes |
| `stock-analyzer` | Domain | Stock market analysis, signal debugging |

### Lifecycle Routing

The Team Lead routes based on request complexity:

| Request Type | Route |
|--------------|-------|
| **Simple Question** | Spawn `stock-analyzer` or `thinker` → Return |
| **Simple Bug Fix** | `build-agent` → `verify-agent` → Return |
| **Standard Feature** | `plan-agent` → `build-agent` → `verify-agent` → `review-agent` → `ship-agent` |
| **Complex Feature** | `define-agent` → `plan-agent` → `build-agent` → `verify-agent` → `review-agent` → `ship-agent` |
| **Architecture Change** | `define-agent` → `plan-agent` → `build-agent` → `verify-agent` → `review-agent` → `ship-agent` |

### Quality Gates

1. **Define** — Spec approved by user
2. **Plan** — Impact analyzed, feasibility validated
3. **Build** — Code compiles, plan followed
4. **Verify** — Tests pass, security clean, perf ok
5. **Review** — Code approved, docs updated, plan adhered to
6. **Ship** — Changelog generated, all phases complete

### Agent Files

**Phase Agent Files (`.opencode/agent/`):**
- `team-lead.md` — Team Lead coordinator
- `define-agent.md` — Define phase
- `plan.md` — Plan phase
- `build.md` — Build phase
- `verify-agent.md` — Verify phase
- `review-agent.md` — Review phase
- `ship-agent.md` — Ship phase
- `stock-analyzer.md` — Stock market analysis

**Subagent Files (`.opencode/agent/`):**
- `planning-agent.md`, `impact-agent.md`, `feasibility-agent.md`
- `implementation-agent.md`, `code-review-agent.md`, `testing-agent.md`

**Skill Files (`.opencode/skills/`):**
- `team-lead/SKILL.md`, `define/SKILL.md`, `plan/SKILL.md`, `build/SKILL.md`
- `verify/SKILL.md`, `review/SKILL.md`, `ship/SKILL.md`
- `subagents/*/SKILL.md` (6 subagent skills)

**Project Skill Files (`.opencode/skills/`):**
- `add-endpoint/SKILL.md`, `add-indicator/SKILL.md`, `add-strategy/SKILL.md`
- `add-frontend-page/SKILL.md`, `run-tests/SKILL.md`, `trace-signal/SKILL.md`

### Team Lead Never

- ❌ Implements code directly — **never**, under any circumstances
- ❌ Runs bash commands
- ❌ Searches the codebase with grep/glob
- ❌ Reviews code changes
- ❌ Writes tests
- ❌ Skips lifecycle phases without justification
- ❌ Accepts incomplete work
- ❌ Sends vague feedback

### Team Lead Always

- ✅ Routes work through appropriate lifecycle phases
- ✅ Reads files for context (this is allowed)
- ✅ Spawns agents in parallel when possible
- ✅ Validates outputs before accepting them
- ✅ Sends incomplete work back for refinement
- ✅ Asks the user when unclear
