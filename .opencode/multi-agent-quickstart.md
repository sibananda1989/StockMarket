# Multi-Agent Workflow Quick Start

## Lifecycle Phases

The workflow follows six sequential phases:

```
Define → Plan → Build → Verify → Review → Ship
```

Each phase has a dedicated coordinator agent that delegates to sub-agents.

## When to Use Which Route

| Request Type | Lifecycle Route |
|-------------|-----------------|
| **Simple Question** | Spawn `stock-analyzer` or `thinker` directly |
| **Simple Bug Fix** | `build` → `verify` → Done |
| **Small Feature** | `plan` → `build` → `verify` → `review` → `ship` |
| **Complex Feature** | `define` → `plan` → `build` → `verify` → `review` → `ship` |
| **Architecture Change** | `define` → `plan` → `build` → `verify` → `review` → `ship` |
| **Stock Analysis** | `stock-analyzer` directly |

## Phase Coordinator Agents

| Phase | Agent | What It Does |
|-------|-------|-------------|
| **Define** | `define-agent` | Spec-driven: clarifies requirements, writes spec, validated by user |
| **Plan** | `plan-agent` | Planning: impact analysis, feasibility, implementation plan |
| **Build** | `build-agent` | Implementation: vertical slicing, delegate to implementation-agent |
| **Verify** | `verify-agent` | Testing + security hardening + performance checks |
| **Review** | `review-agent` | Code review + documentation review + plan adherence |
| **Ship** | `ship-agent` | Changelog + release summary + deployment notes |

## Subagents

| Agent | Used By | Purpose |
|-------|---------|---------|
| `planning-agent` | Plan | Requirements analysis and implementation planning |
| `impact-agent` | Plan | Impact analysis for code changes |
| `feasibility-agent` | Plan | Technical validation and feasibility checking |
| `implementation-agent` | Build | Code implementation following approved plans |
| `code-review-agent` | Review | Critical code review and defect finding |
| `testing-agent` | Verify | Comprehensive testing and coverage verification |

## Helper Agents

| Agent | Tool Access | Use When |
|-------|-------------|----------|
| `basher` | bash | Run terminal commands |
| `code-searcher` | grep, glob, read | Search for code patterns |
| `file-picker` | glob, grep, read | Find relevant files |
| `browser-use` | browser | Verify UI in browser |
| `researcher-web` | websearch, webfetch | External research |
| `researcher-docs` | websearch, webfetch | Framework/library docs |
| `thinker` | read, grep, websearch | Deep analysis |
| `code-reviewer` | read, grep, bash | Standalone code review |

## Project-Specific Skills

```
/skill add-endpoint        — New REST API endpoint
/skill add-indicator       — New technical indicator
/skill add-strategy        — New trading strategy
/skill add-frontend-page   — New frontend page
/skill run-tests           — Run/debug/write tests
/skill trace-signal        — 16-factor signal walkthrough
```

## Quality Gates

1. **Define** — Spec approved by user
2. **Plan** — Impact analyzed, feasibility validated
3. **Build** — Code compiles, plan followed
4. **Verify** — Tests pass, security clean, perf ok
5. **Review** — Code reviewed, docs updated, plan adhered to
6. **Ship** — Changelog generated, all phases complete

## Agent Files

- `.opencode/agent/team-lead.md` — Team Lead coordinator
- `.opencode/agent/define-agent.md` — Define phase
- `.opencode/agent/plan.md` — Plan phase
- `.opencode/agent/build.md` — Build phase
- `.opencode/agent/verify-agent.md` — Verify phase
- `.opencode/agent/review-agent.md` — Review phase
- `.opencode/agent/ship-agent.md` — Ship phase
- `.opencode/agent/stock-analyzer.md` — Stock market analysis
