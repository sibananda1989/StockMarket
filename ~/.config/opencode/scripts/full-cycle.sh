#!/usr/bin/env bash
# full-cycle.sh — Smart orchestrator: assess → route → execute
#
# Usage: full-cycle.sh <task description>
#
# Philosophy: The full-cycle agent UNDERSTANDS the task first, then decides:
#   - SIMPLE task → do it directly (build + verify, minimal overhead)
#   - COMPLEX task → find appropriate specialized agents and delegate
#
# No rigid phases. No mandatory planning for obvious fixes.
# The agent is the decision-maker, not a pipeline follower.

set -euo pipefail

TASK="$*"
if [[ -z "$TASK" ]]; then
  echo "Usage: full-cycle.sh <task description>" >&2
  exit 1
fi

WORKDIR="${FULL_CYCLE_DIR:-$(pwd)}"
STATE_DIR="$WORKDIR/.full-cycle"
ASSESS_FILE="$STATE_DIR/assess.md"
RESULT_FILE="$STATE_DIR/result.md"
mkdir -p "$STATE_DIR"

# Load AGENTS.md constraints
CONSTRAINTS=""
AGENTS_FILE="$WORKDIR/AGENTS.md"
if [[ -f "$AGENTS_FILE" ]]; then
  CONSTRAINTS="$(printf 'CONSTRAINTS (mandatory, from AGENTS.md):\n%s\n' "$(cat "$AGENTS_FILE")")"
fi

# gate_check <file> <pattern> <phase-name>
gate_check() {
  local file="$1" pattern="$2" phase="$3"
  if grep -qE "$pattern" "$file"; then
    return 0
  fi
  echo "$phase GATE FAILED: no line matching '$pattern' in $file." >&2
  echo "Last 15 lines:" >&2
  tail -15 "$file" >&2
  return 1
}

# ═══════════════════════════════════════════════════════════════════
# PHASE 1: ASSESS — Understand the task and classify it
# ═══════════════════════════════════════════════════════════════════
echo "==> Phase 1: ASSESS"

ASSESS_PROMPT="You are a senior engineer assessing a task. Your job is to UNDERSTAND the task and classify its complexity.

TASK: $TASK

Analyze the task:
1. What is the user asking for?
2. Which files are likely involved?
3. Is this a one-file change or multi-file?
4. Does it touch src/** (production code) or just config/docs/static?
5. What's the risk level?

Classify as one of:
- SIMPLE: Clear scope, 1-3 files, low risk, well-understood pattern (e.g. version bump, typo fix, adding an attribute, cache-bust)
- MEDIUM: Needs planning but is well-scoped (e.g. new feature in existing pattern, bug fix requiring investigation)
- COMPLEX: Multi-system, unclear requirements, architectural decisions needed, or touches many files

End your response with exactly one line:
CLASSIFICATION: SIMPLE
or
CLASSIFICATION: MEDIUM
or
CLASSIFICATION: COMPLEX

Then list the files you think are involved (if any), one per line after FILES:"

if ! opencode run --agent build "$ASSESS_PROMPT" >"$ASSESS_FILE" 2>&1; then
  echo "ASSESS FAILED — output below:" >&2
  tail -30 "$ASSESS_FILE" >&2
  exit 1
fi

gate_check "$ASSESS_FILE" 'CLASSIFICATION: (SIMPLE|MEDIUM|COMPLEX)' "ASSESS" \
  || { echo "Assessment did not produce a CLASSIFICATION — treating as failure." >&2; exit 1; }

# Extract classification
CLASS=$(grep -oE 'CLASSIFICATION: (SIMPLE|MEDIUM|COMPLEX)' "$ASSESS_FILE" | awk '{print $2}')
echo "    Task classified as: $CLASS"

# ═══════════════════════════════════════════════════════════════════
# ROUTE based on classification
# ═══════════════════════════════════════════════════════════════════

if [[ "$CLASS" == "SIMPLE" ]]; then
  # ─── SIMPLE PATH: Build agent does it directly ───
  echo "==> Route: SIMPLE → Build agent handles directly"

  SIMPLE_PROMPT="Implement this task directly. You are the builder — no delegation needed, just do it.

TASK: $TASK

ASSESSMENT:
$(cat "$ASSESS_FILE")

$CONSTRAINTS

Instructions:
- Make the changes directly. Don't plan, just implement.
- If it touches src/**, run the build after: mvn -q compile -DskipTests
- For JS files, run: node --check <file>
- Keep changes minimal — match existing conventions.
- Stage intended files only. Do NOT commit unless asked.

End your response with exactly one line:
STATUS: DONE"

  if ! opencode run --agent build "$SIMPLE_PROMPT" >"$RESULT_FILE" 2>&1; then
    echo "BUILD FAILED — output below:" >&2
    tail -30 "$RESULT_FILE" >&2
    exit 1
  fi

  gate_check "$RESULT_FILE" 'STATUS: DONE' "SIMPLE BUILD" \
    || { echo "Simple build did not produce STATUS: DONE — treating as failure." >&2; exit 1; }

  echo
  echo "================= SIMPLE TASK COMPLETE ================="
  echo "Assessment: $ASSESS_FILE"
  echo "Result:     $RESULT_FILE"

elif [[ "$CLASS" == "MEDIUM" ]]; then
  # ─── MEDIUM PATH: Build agent with context from assessment ───
  echo "==> Route: MEDIUM → Build agent with assessment context"

  MEDIUM_PROMPT="Implement this task. Use the assessment to guide your approach, but don't overthink it.

TASK: $TASK

ASSESSMENT:
$(cat "$ASSESS_FILE")

$CONSTRAINTS

Instructions:
- Read the assessment to understand scope and files involved.
- Make the changes. For src/** changes, run: mvn -q compile -DskipTests
- For JS files, run: node --check <file>
- Match existing conventions.
- Stage intended files only. Do NOT commit unless asked.

End your response with exactly one line:
STATUS: DONE"

  if ! opencode run --agent build "$MEDIUM_PROMPT" >"$RESULT_FILE" 2>&1; then
    echo "BUILD FAILED — output below:" >&2
    tail -30 "$RESULT_FILE" >&2
    exit 1
  fi

  gate_check "$RESULT_FILE" 'STATUS: DONE' "MEDIUM BUILD" \
    || { echo "Medium build did not produce STATUS: DONE — treating as failure." >&2; exit 1; }

  echo
  echo "================= MEDIUM TASK COMPLETE ================="
  echo "Assessment: $ASSESS_FILE"
  echo "Result:     $RESULT_FILE"

else
  # ─── COMPLEX PATH: Plan → Build → Review ───
  echo "==> Route: COMPLEX → Plan → Build → Review"

  # Phase 2a: Planning
  echo "    → Planning..."
  PLAN_FILE="$STATE_DIR/plan.md"

  PLAN_PROMPT="Create an implementation plan for this task. Focus on WHAT to change, not long prose.

TASK: $TASK

ASSESSMENT:
$(cat "$ASSESS_FILE")

$CONSTRAINTS

Requirements:
- List specific files to modify and what changes in each.
- Identify risks and dependencies.
- Keep it actionable — a build agent should be able to follow this.
- Under 200 lines.

End your response with exactly one line:
PLAN_STATUS: READY"

  if ! opencode run --agent planner "$PLAN_PROMPT" >"$PLAN_FILE" 2>&1; then
    echo "PLAN FAILED — output below:" >&2
    tail -30 "$PLAN_FILE" >&2
    exit 1
  fi

  gate_check "$PLAN_FILE" 'PLAN_STATUS: READY' "PLAN" \
    || { echo "Plan did not produce PLAN_STATUS: READY — treating as failure." >&2; exit 1; }

  # Phase 2b: Build with plan
  echo "    → Building..."
  RESULT_FILE="$STATE_DIR/result.md"

  BUILD_PROMPT="Implement this task following the plan below. Write production code.

TASK: $TASK

PLAN:
$(cat "$PLAN_FILE")

$CONSTRAINTS

Instructions:
- Follow the plan. Implement each file change.
- For src/** changes, run: mvn -q compile -DskipTests
- For JS files, run: node --check <file>
- Match existing conventions.
- Stage intended files only. Do NOT commit unless asked.

End your response with exactly one line:
STATUS: DONE"

  if ! opencode run --agent build "$BUILD_PROMPT" >"$RESULT_FILE" 2>&1; then
    echo "BUILD FAILED — output below:" >&2
    tail -30 "$RESULT_FILE" >&2
    exit 1
  fi

  gate_check "$RESULT_FILE" 'STATUS: DONE' "BUILD" \
    || { echo "Build did not produce STATUS: DONE — treating as failure." >&2; exit 1; }

  # Phase 2c: Review (only if complex — worth the extra call)
  echo "    → Reviewing..."
  REVIEW_FILE="$STATE_DIR/review.md"

  REVIEW_PROMPT="Review the implementation of this task. Report defects, security issues, and quality problems with file references.

TASK: $TASK

ASSESSMENT:
$(cat "$ASSESS_FILE")

PLAN:
$(cat "$PLAN_FILE")

$CONSTRAINTS

End your response with exactly one line:
REVIEW_VERDICT: CLEAN
or
REVIEW_VERDICT: ISSUES"

  if ! opencode run --command code-review "$REVIEW_PROMPT" >"$REVIEW_FILE" 2>&1; then
    echo "REVIEW FAILED — output below:" >&2
    tail -30 "$REVIEW_FILE" >&2
    exit 1
  fi

  gate_check "$REVIEW_FILE" 'REVIEW_VERDICT: (CLEAN|ISSUES)' "REVIEW" \
    || { echo "Review did not produce a verdict — treating as failure." >&2; exit 1; }

  if grep -q "REVIEW_VERDICT: ISSUES" "$REVIEW_FILE"; then
    echo "    ⚠ Review found issues (see $REVIEW_FILE). Continuing."
  fi

  echo
  echo "================= COMPLEX TASK COMPLETE ================="
  echo "Assessment: $ASSESS_FILE"
  echo "Plan:       $PLAN_FILE"
  echo "Result:     $RESULT_FILE"
  echo "Review:     $REVIEW_FILE"
fi
