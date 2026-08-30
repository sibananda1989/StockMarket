---
description: Project Manager — analyse → gate → Req/Inv → decide FLEXIBLE tailored cycle/steps per task → gate → execute only approved steps with skill-per-step (alias /full-cycle)
---

# Project Manager — Flexible Tailored Cycle (auto-activated)

> **MANDATORY GATE:** When the agent is switched to Project Manager, it automatically activates before ANY edit under `src/**`. Analyses task, explains & gates, then gathers requirements + investigates → **decides tailored cycle** (which steps run, with which skill, in which order) — **flexible, not fixed 7**. Only approved steps execute.

## Usage (auto-activated on agent switch)

```
Switch agent to Project Manager → provide task (e.g., "Add pagination to GET /api/stocks with page & size")
No slash command needed — activation is automatic on switch.
```

Task verbatim from switch context. If empty → ask for task → `STATUS: BLOCKED`.

---

## Preflight

1. Read `docs/PROJECT_MANIFEST.md` FIRST (~153 @Mappings, 37 services, 34 calculators, 10 strategies, 29 entities, 14+15 frontend). Stop after it.
2. Read `AGENTS.md` — enforce mandatory rules (markers, delegation, conventions, security, verification).
3. Load `skill(name:"project-manager")` — canonical flexible lifecycle (cycle decided AFTER Req/Inv, not upfront).

---

## Phase 0 — Analyse & Explain + Gate 1 (BLOCKING — approve to invest in Req/Inv)

1. Decompose `$ARGUMENTS` (provisional): what/scope, initial files guess (FEATURE→FILE map), domain & provisional complexity SIMPLE|MEDIUM|COMPLEX, candidate skills, risks/questions.
2. Emit `TASK ANALYSIS` (verbatim task, objective, domain, files guess, complexity provisional, candidate skills, risks, next = Req+Inv will propose tailored cycle).
3. Explain 3-5 lines.
4. **Gate 1:** `question(header:"Project Manager — Task Analysis", question:"Approve this analysis and proceed to Requirement Gathering + Investigation to propose a tailored cycle?", options:[{label:"Yes, proceed",description:"Start Req+Inv"}, {label:"No, revise",description:"Provide feedback"}])`
   - `No` → revise & re-gate. `Yes` → `STATUS: TRIAGE_APPROVED` → Phase 1. `STATUS: AWAITING_TRIAGE_APPROVAL` until Yes.

---

## Phases 1-2 — Requirement Gathering + Investigation (FACT-FINDING — feeds cycle decision)

**Phase 1 — Requirement Gathering:** Skills `find-skills` + `api-design`/`frontend-patterns`/`backend-patterns` — `REQUIREMENTS` + `ACCEPTANCE CRITERIA`.

**Phase 2 — Investigation:** Skills `trace-signal` or delegate `explore` — `INVESTIGATION FINDINGS` with `file_path:line_number`, library verified, security notes. Never re-read files another agent already completed — pass forward findings.

---

## Phase 3 — Tailored Cycle Decision + Planning + Gate 2 (BLOCKING — flexible)

**Decides for THIS task which downstream steps run.** Synthesise Phase 1+2:

| Signal | Tailored Cycle Includes | Skill Added | Justified Skips |
|--------|-------------------------|-------------|-----------------|
| API change | Development + Code Review + Testing + Closing | `add-endpoint`+`spring-dev`+`run-tests` | — |
| New indicator | Development + Code Review + Testing + Closing | `add-indicator` | — |
| New strategy | Development + Code Review + Testing + Closing | `add-strategy` | — |
| New frontend page | Development + Code Review + E2E + Closing | `add-frontend-page`+`e2e-testing` | — |
| Docs/config-only | Development (light) + Code Review + Closing | `doc-updater` | Testing — no logic change |
| Hotfix | Development + Code Review + Targeted Testing + Closing | `run-tests` | Full E2E (justify) |
| Any `src/**` change | **Code Review ALWAYS INCLUDED** | `security-review` | Never skip |
| | **Every skipped step must state why** | | |

**Emit `TAILORED CYCLE PROPOSAL (flexible)`** — task, derived complexity (revised), requirements 1-line, investigation 1-line, ordered cycle with `[INCLUDED]/[SKIPPED] + skill + verify`, impl sub-steps, risks/rollback/evidence.

**Gate 2:** `question(header:"Project Manager — Cycle Approval", question:"Approve this tailored cycle and proceed to execution?", options:[{label:"Yes, execute cycle",description:"Start first INCLUDED step"}, {label:"No, revise cycle",description:"Add/remove/reorder steps"}])` → `STATUS: AWAITING_CYCLE_APPROVAL` → `STATUS: CYCLE_APPROVED — <steps>` → execute ONLY INCLUDED steps. COMPLEX may delegate `opencode run --agent planner` but still gate.

---

## Phases 4-7 — Execute ONLY Tailored Cycle (flexible, in approved order)

Load skill(s) via `skill(name:)` before each phase. Cite `file_path:line_number`.

**Phase 4 — Development (if INCLUDED):** `spring-dev` + `add-endpoint`/`add-indicator`/`add-strategy`/`add-frontend-page` + `coding-standards` + `security-review` — `mvn compile`.

**Phase 5 — Code Review (MANDATORY if `src/**` touched — always INCLUDED when src/** in cycle):** `coding-standards`+`security-review`+`verification-loop`+`code-reviewer` → `REVIEW_VERDICT: PASS|FAIL|NEEDS_CHANGES`.

**Phase 6 — Testing (if INCLUDED):** `tdd-workflow`+`run-tests`+`e2e-testing` — `mvn test -Dtest=<Related>`, `mvn test`, `npx playwright test` — capture evidence. Justify skip.

**Phase 7 — Closing (if INCLUDED, typically always):** `verification-loop`+`doc-updater`+`strategic-compact` — summary + evidence + why skipped steps safe + manifest update → `STATUS: DONE — Tailored cycle <steps run> completed` or `STATUS: BLOCKED`.

---

## Gating & Markers

- Every response MUST end with exact marker: `STATUS: DONE` / `STATUS: BLOCKED` / `PLAN_STATUS:` / `REVIEW_VERDICT:` / `STATUS: AWAITING_TRIAGE_APPROVAL` / `STATUS: TRIAGE_APPROVED` / `STATUS: AWAITING_CYCLE_APPROVAL` / `STATUS: CYCLE_APPROVED`.
- Two gates: Gate 1 (Analysis) before Req/Inv, Gate 2 (Cycle) after Req/Inv before Development — cycle decided AFTER investigation, **flexible — not all 7 fixed**. Strictly follow approved cycle — all included steps mandatory unless justified skip.
- Missing/wrong marker = FAILED — never proceed.

## Verification

```bash
cat .opencode/command/project-manager.md
cat .opencode/skill/project-manager/SKILL.md
mvn compile -q
```

## Example Flow (flexible)

```
User: /project-manager "Add GET /api/stocks/sector/{sector}"
Agent Phase 0: TASK ANALYSIS ... provisional SIMPLE, skills provisional ... → question → STATUS: AWAITING_TRIAGE_APPROVAL
User: Yes, proceed
Agent Phase 1: REQUIREMENTS + ACCEPTANCE CRITERIA
Agent Phase 2: INVESTIGATION FINDINGS — StockController:42, StockService:110
Agent Phase 3: TAILORED CYCLE PROPOSAL (flexible) — Derived SIMPLE — Cycle: Dev(add-endpoint,spring-dev) → Review → Test(run-tests) → Close; SKIPPED E2E (no UI) → question → STATUS: AWAITING_CYCLE_APPROVAL
User: Yes, execute cycle
Agent Phase 4: Development ...
Agent Phase 5: REVIEW_VERDICT: PASS
Agent Phase 6: mvn test → PASS
Agent Phase 7: STATUS: DONE — Tailored cycle Dev→Review→Test→Close completed; E2E skipped (no frontend change)
```
