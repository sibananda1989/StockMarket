## Delegation Efficiency

- When a prior agent has already investigated a codebase area, include those findings in subsequent delegation prompts to avoid redundant file reads.
- Pass forward relevant context (file paths, key lines, findings) so the next agent can work from the existing analysis instead of re-reading the same files.
- This cuts response time and avoids duplicating work.

### Context Reuse Protocol

**Step 1 – First Agent Call**  
Load the full chart investigation (stock-detail.js, Lightweight Charts, S/R lines, toggle logic, etc.).

**Step 2 – Immediate Reuse**  
When you ask a follow-up question (like "S/R labels still show even when toggle is off"), I will:
1. Reference the first agent's findings by ID/name (`ses_01b9957cdffeea1Vyge4hE2RkZ`)
2. Pull the relevant chunks directly from the prior result
3. Answer in one shot instead of delegating again

**Example response structure** (this is what I'll use):
> **Using prior context from first investigation (ses_01b9957cdffeea1Vyge4hE2RkZ)**  
> The S/R price lines are created with `axisLabelVisible: true` (line 1375). The toggle only sets `color: 'transparent'`, never disabling the axis label. This is a code omission. Minimal fix: add `axisLabelVisible: checked` to the `applyOptions` call.