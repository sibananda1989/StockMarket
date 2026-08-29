---
name: full-cycle
description: "DEPRECATED alias — use project-manager skill. Kept for backward compatibility with AGENTS.md and old commands."
---

# Full-Cycle — Deprecated Alias for Project Manager

> **DEPRECATED:** Use `skill(name:"project-manager")` — this file is kept only for backward compatibility. Delegates to **Project Manager**.

Canonical: `.opencode/skill/project-manager/SKILL.md` (and `.agents/skills/project-manager/SKILL.md`).

All lifecycle logic, Gate 1 analysis approval + Req/Inv → flexible tailored cycle decision + skill-per-step routing lives in `project-manager`. Auto-activates when the agent is switched to Project Manager — no slash command needed.
