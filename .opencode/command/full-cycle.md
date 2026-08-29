---
description: Alias for Project Manager — auto-activated on switch to Project Manager agent. Flexible tailored cycle.
---

# /full-cycle — Deprecated Alias (auto-activated)

> **DEPRECATED:** Project Manager now auto-activates when the agent is switched to Project Manager — no slash command needed. This file kept for backward compatibility.

**Canonical:** Switch agent to Project Manager → see `.opencode/skill/project-manager/SKILL.md` for flexible lifecycle (Analyse → gate → Req → Inv → decide tailored cycle → gate → execute only approved steps).

Delegates to: `skill(name:"project-manager")` — same analysis, same gates, same markers (`STATUS: AWAITING_TRIAGE_APPROVAL` / `STATUS: DONE`).
