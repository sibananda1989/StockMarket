> **ARCHIVED**: The multi-agent workflow described here has been consolidated into the global full-cycle orchestrator (`~/.config/opencode/scripts/full-cycle.sh`). This doc is kept for reference only.

# Multi-Agent Software Engineering Workflow

This document defines the lifecycle-driven multi-agent workflow for the Stock Market Analysis Platform.

## Core Philosophy

**Strict lifecycle delegation.** The Team Lead routes work through six sequential lifecycle phases:

```
Define → Plan → Build → Verify → Review → Ship
```

Each phase has a dedicated coordinator agent that delegates work to sub-agents. The Team Lead never implements, searches, or tests directly.

## Lifecycle Architecture

```
User Request
    ↓
[Team Lead] — Strict coordinator, routes to lifecycle phases
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Lifecycle Phase Coordinators:                                │
│                                                              │
│ define-agent — Spec-driven development                       │
│   → Uses helpers: file-picker, code-searcher, researcher-web │
│   → Output: Specification document (user-approved)           │
│                                                              │
│ plan-agent — Planning & analysis                             │
│   → Subagents: planning-agent, impact-agent, feasibility-agent│
│   → Output: Implementation plan                              │
│                                                              │
│ build-agent — Implementation with vertical slicing           │
│   → Subagents: implementation-agent                         │
│   → Output: Implemented, compilable code                     │
│                                                              │
│ verify-agent — Testing + Security + Performance              │
│   → Subagents: testing-agent                                │
│   → Checks: Tests, security hardening, performance review    │
│   → Output: Verification report                              │
│                                                              │
│ review-agent — Code review + Documentation review            │
│   → Subagents: code-review-agent                            │
│   → Checks: Correctness, conventions, plan adherence         │
│   → Output: Review report                                    │
│                                                              │
│ ship-agent — Changelog + Release                             │
│   → Uses helpers: basher, code-searcher                     │
│   → Output: Changelog, release summary, deployment notes     │
│                                                              │
│ stock-analyzer — Domain expert (stock market analysis)       │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Helper Agents (delegated directly by any phase coordinator): │
│ basher, code-searcher, file-picker, browser-use,            │
│ researcher-web, researcher-docs, thinker, code-reviewer     │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Subagents (delegated by phase coordinators):                 │
│ planning-agent, impact-agent, feasibility-agent,            │
│ implementation-agent, code-review-agent, testing-agent       │
└─────────────────────────────────────────────────────────────┘
    ↓
Quality Gates Validation
    ↓
Final Result
```

## How Lifecycle Phases Work

### Team Lead

The Team Lead is the **only agent the user interacts with**. It:
1. Understands the request
2. Classifies the request type (question, simple fix, standard feature, complex feature)
3. Routes to the appropriate lifecycle phases
4. Reviews outputs and validates quality gates
5. Never implements, searches, or tests directly

### Define Phase (`define-agent`)

Used for **complex or vague requirements**. Produces a formal specification:
- Clarifies requirements by asking the user
- Surfaces assumptions explicitly
- Researches context (existing code, patterns, constraints)
- Produces spec document with objective, requirements, architecture notes, edge cases
- **Must be approved by user** before proceeding to Plan phase

### Plan Phase (`plan-agent`)

Used for **any implementation that needs planning**. Produces an implementation plan:
- Takes approved spec (or direct request) as input
- Delegates impact analysis to `impact-agent`
- Delegates implementation planning to `planning-agent`
- Delegates feasibility validation to `feasibility-agent`
- Output: detailed step-by-step implementation plan

### Build Phase (`build-agent`)

Used for **any code implementation**. Delegates all code work:
- Breaks work into **thin vertical slices** (one complete feature path per slice)
- Delegates each slice to `implementation-agent`
- Verifies compilation after each slice
- Output: implemented, compilable code

### Verify Phase (`verify-agent`)

Used for **quality verification after build**. Checks three areas:
- **Testing**: Runs all tests via `testing-agent`, verifies backward compatibility
- **Security**: Checks input validation, parameterized queries, output encoding, HTTPS, dependencies
- **Performance**: Checks N+1 queries, excessive loops, caching opportunities
- Output: verification report with pass/fail status

### Review Phase (`review-agent`)

Used for **human-quality review after verification**. Covers:
- **Code review**: Delegates to `code-review-agent` for 5-axis review (correctness, readability, architecture, security, performance)
- **Documentation review**: README, inline comments, API docs, changelog
- **Plan adherence**: Implementation matches approved spec, no scope creep
- Output: review report with approve/request-changes/reject status

### Ship Phase (`ship-agent`)

Used for **release documentation after review**. Produces:
- **Changelog**: Categorized entries (features, fixes, perf, security, docs)
- **Release summary**: User-friendly description of what changed
- **Deployment notes**: Config changes, migrations, rollback plan
- Output: ship report confirming all phases completed

### Stock Analyzer (`stock-analyzer`)

Used for **analysis only** — signal debugging, strategy questions, indicator explanations. Not part of the lifecycle pipeline.

## Lifecycle Routing

The Team Lead routes requests based on complexity. **Always use the shortest path that works.**

```
User Request
    ↓
Team Lead evaluates:
    ↓
┌── Simple question/analysis → stock-analyzer or answer directly → Done
│
├── Bug fix (clear scope) → build-agent → Done
│
├── Simple feature (clear scope) → build-agent → Done
│
├── Medium feature → plan-agent → build-agent → Done
│
├── Complex feature (vague) → define-agent → plan-agent → build-agent → Done
└── Architecture change → define-agent → plan-agent → build-agent → Done
```

**Key principle: Skip phases that don't add value.**
- Don't plan what's obvious
- Don't verify what you just tested
- Don't review what's a one-line fix
- Don't ship-document a bug fix

Only add Verify/Review/Ship phases when:
- The change is large (10+ files)
- The change affects production systems
- The user explicitly asks for it

## Quality Gates (Mandatory)

Every phase enforces quality gates before passing to the next:

1. **Define** — Spec approved by user, all assumptions surfaced, edge cases documented
2. **Plan** — Plan complete with impact analysis, risks, dependencies, test requirements
3. **Build** — Code compiles, follows plan, all files updated
4. **Verify** — All tests pass, security checklist clean, no performance issues
5. **Review** — Code approved, documentation updated, plan adhered to
6. **Ship** — Changelog generated, release summary written, all prior phases completed

## Key Principles

1. **Delegate everything** — Phase coordinators never implement, search, review, or test directly
2. **Follow the lifecycle** — Phases execute in order, never skip a phase without justification
3. **Parallelize helpers** — Spawn independent helper agents simultaneously
4. **Ask the user** — When unclear, ask rather than guess
5. **Loop on failure** — Send incomplete work back with specific feedback
6. **Be concise** — Output useful summaries, not filled-in templates

## File Organization

```
.opencode/
  opencode.json                — Agent model configuration
  multi-agent-workflow.md      — This file
  multi-agent-quickstart.md    — Quick reference
  agents/
    team-lead.md               — Team Lead coordinator
    define-agent.md            — Define phase (spec-driven)
    plan-agent.md              — Plan phase coordinator
    build-agent.md             — Build phase coordinator
    verify-agent.md            — Verify phase (test + security + perf)
    review-agent.md            — Review phase (code review)
    ship-agent.md              — Ship phase (changelog + release)
    stock-analyzer.md          — Domain expert
    basher.md, code-searcher.md, ... — Helper agents
  skills/
    team-lead/SKILL.md         — Team Lead skill
    define/SKILL.md            — Define phase skill
    plan/SKILL.md              — Plan phase skill
    build/SKILL.md             — Build phase skill
    verify/SKILL.md            — Verify phase skill
    review/SKILL.md            — Review phase skill
    ship/SKILL.md              — Ship phase skill
    subagents/                 — Subagent skills
    add-endpoint/, ...         — Project-specific skills
```
