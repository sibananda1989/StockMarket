# Multi-Agent Software Engineering Workflow

This document defines the multi-agent workflow for the Stock Market Analysis Platform.

## Core Philosophy

**Flexible, adaptive, and practical.** Unlike rigid stage-gate processes, this workflow adapts to the task:

- **Simple tasks** → bypass planning & formal review, implement directly
- **Complex tasks** → use planning, formal review, and thorough testing
- **Always** → gather context first, act second

## Architecture

```
User Request
    ↓
[Team Lead] - Dynamic workflow coordinator
    ↓
┌─────────────────────────────────────────────────────┐
│ Main Agents (4):                                    │
│ • team-lead - Workflow coordinator (FREE MODEL)    │
│ • plan - Planning & analysis (FREE MODEL)           │
│ • build - Implementation & testing (FREE MODEL)     │
│ • stock-analyzer - Stock market analysis (FREE)     │
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
```

## How Agents Are Used

### Team Lead

The Team Lead is the **only agent the user interacts with**. It:
1. Understands the request
2. Gathers context (reads files, searches codebase)
3. Decides which main agents to invoke (if any — simple questions need no agents)
4. Spawns agents in parallel where possible
5. Reviews results and returns a concise summary

### Plan Agent

Used for **complex or uncertain work**. Not used for simple bugs or questions.

May invoke subagents:
- `impact-agent` — for multi-module changes
- `planning-agent` — for detailed implementation planning
- `feasibility-agent` — for architecture-level decisions

### Build Agent

Used for **any code change**. Adapts its approach:
- Simple fix → implements directly, self-review
- Feature → implements, then spawns review + testing in parallel
- Complex → may use `implementation-agent` for careful coding

### Stock Analyzer

Used for **analysis only** — signal debugging, strategy questions, indicator explanations.

## Workflow Decision Tree

```
User Request
    ↓
Team Lead evaluates:
    ↓
┌── Is it a simple question? ──→ Answer directly or use stock-analyzer ──→ Done
│
├── Is it a simple bug fix? ──→ Gather context → Spawn build → Verify → Done
│
├── Is it a small feature? ──→ Gather context → Spawn build → Review → Done
│
├── Is it a complex feature? ──→ Spawn plan → Review plan → Spawn build → Done
│
└── Is it architecture change? ──→ Spawn plan → Get user approval → Spawn build → Done
```

## Quality Philosophy

- **Simple changes** → common sense review, verify it compiles
- **Meaningful changes** → run relevant tests, quick diff review
- **Critical changes** → formal code review + full test suite + manual verification

No mandatory quality gates. No template-based output requirements. The Team Lead uses judgment.

## Key Principles

1. **Context first** — read relevant files before making changes
2. **Parallelize** — spawn agents together, not in sequence
3. **Adapt** — the workflow depends on the task, not a fixed process
4. **Ask the user** — when unclear, ask rather than guess
5. **Be concise** — output useful summaries, not filled-in templates
6. **Free models** — all agents use free models (mimo-v2.5-pro-free, deepseek-v4-flash-free, etc.)

## Model Configuration

All agents are configured in `opencode.json` to use free models:
- `team-lead` → `naraya/mimo-v2.5-pro-free`
- `plan` → `naraya/mimo-v2.5-free`
- `build` → `naraya/mimo-v2.5-pro-free`
- `stock-analyzer` → `naraya/mimo-v2.5-pro-free`

Fallback chains in `fallback.json` prioritize free/cheap models first.
