---
name: orchestrate
description: >
  Full development cycle workflow — orchestrates Explore, Plan, coder,
  test-engineer, and code-reviewer agents across the complete lifecycle
  with automatic context flow between phases.
---
export const meta = {
  name: "orchestrate",
  description: "Full dev cycle: research → plan → code → test → review",
  phases: [
    { title: "Research / Bug Discovery" },
    { title: "Planning" },
    { title: "Implementation" },
    { title: "Testing" },
    { title: "Code Review" },
  ],
}

const TASK = args.task || args;
const NEEDS_RESEARCH = args.needsResearch !== false;
const NEEDS_PLAN = args.needsPlan !== false;
const NEEDS_TEST = args.needsTest !== false;
const NEEDS_REVIEW = args.needsReview !== false;

let researchResult = "";
let planResult = "";
let codeResult = "";
let testResult = "";
let reviewResult = "";

// Phase 1: Research & Bug Discovery
if (NEEDS_RESEARCH) {
  phase("Research / Bug Discovery");
  researchResult = await agent(
    `Research the following task thoroughly:
     Task: ${TASK}
     
     1. Find relevant existing code in the codebase
     2. Understand current architecture patterns
     3. Identify any existing bugs or issues related to this area
     4. Gather constraints, dependencies, and requirements
     
     Return a comprehensive research summary with file references.`,
    { agentType: "Explore", label: "Researching codebase & discovering bugs", phase: "Research / Bug Discovery" }
  );
}

// Phase 2: Planning
if (NEEDS_PLAN) {
  phase("Planning");
  const planContext = researchResult ? `Research context:\n${researchResult}\n\n` : "";
  planResult = await agent(
    `${planContext}Create a detailed implementation plan for:
     Task: ${TASK}
     
     Include:
     1. Files to create or modify
     2. Architecture decisions
     3. Implementation steps in order
     4. Edge cases to handle
     5. Any risks or considerations
     
     Return a clear, actionable plan.`,
    { agentType: "Plan", label: "Creating implementation plan", phase: "Planning" }
  );
}

// Phase 3: Implementation
phase("Implementation");
const codeContext = [
  researchResult ? `## Research Context\n${researchResult}` : "",
  planResult ? `## Implementation Plan\n${planResult}` : "",
].filter(Boolean).join("\n\n");

codeResult = await agent(
  `${codeContext}
   
   ## Task
   ${TASK}
   
   Implement the above plan. Write clean, production-ready code following existing patterns in the codebase.
   Handle edge cases and add appropriate error handling.
   
   Return the files created/modified and a summary of what was implemented.`,
  { agentType: "coder", label: "Implementing code", phase: "Implementation" }
);

// Phase 4: Testing
if (NEEDS_TEST) {
  phase("Testing");
  testResult = await agent(
    `## Implemented Code
${codeResult}

## Task
${TASK}

Write comprehensive tests for the implementation above.
Include unit tests, edge cases, and verify the existing tests still pass.

Return the test files created and coverage summary.`,
    { agentType: "test-engineer", label: "Writing tests", phase: "Testing" }
  );
}

// Phase 5: Code Review
if (NEEDS_REVIEW) {
  phase("Code Review");
  const reviewContext = [
    `## Implementation\n${codeResult}`,
    testResult ? `## Tests\n${testResult}` : "",
  ].filter(Boolean).join("\n\n");

  reviewResult = await agent(
    `${reviewContext}
     
     ## Task
     ${TASK}
     
     Perform a thorough code review:
     1. Check for bugs and logic errors
     2. Verify code follows project patterns
     3. Check test coverage and quality
     4. Validate error handling
     5. Suggest improvements if any
     
     Return the review verdict and any required changes.`,
    { agentType: "code-reviewer", label: "Reviewing code quality", phase: "Code Review" }
  );
}

// Return final report
return {
  task: TASK,
  research: researchResult ? "✅ Completed" : "⏭️ Skipped",
  plan: planResult ? "✅ Completed" : "⏭️ Skipped",
  implementation: "✅ Completed",
  tests: testResult ? "✅ Completed" : "⏭️ Skipped",
  review: reviewResult ? "✅ Completed" : "⏭️ Skipped",
  review_verdict: reviewResult || "No review requested",
};
