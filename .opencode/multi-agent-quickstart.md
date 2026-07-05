# Multi-Agent Workflow Quick Start

## When to Use Multi-Agent Workflow

Use the multi-agent workflow for:
- ✅ New features
- ✅ Major enhancements
- ✅ Architecture changes
- ✅ Refactoring affecting multiple modules
- ✅ Database schema changes
- ✅ API endpoint additions
- ✅ Security-sensitive changes

Use existing project skills for:
- ✅ Bug fixes (simple)
- ✅ Small changes
- ✅ Documentation updates
- ✅ Test-only changes

## Workflow Stages

```
User Request
    ↓
[Team Lead] Coordinates workflow
    ↓
[Planning Agent] Analyzes & produces plan
    ↓
[Feasibility Agent] Validates plan
    ↓
[Implementation Agent] Writes code
    ↓
[Code Review Agent] Reviews code
    ↓
[Testing Agent] Verifies tests
    ↓
[Team Lead] Produces final summary
```

## Agent Responsibilities

### Team Lead (Coordinator)
- Orchestrates all agents
- Never implements code directly
- Validates each stage
- Produces final summary

### Planning Agent
- Analyzes requirements
- Identifies affected modules
- Produces implementation plan
- Documents risks and assumptions

### Feasibility Agent
- Validates technical feasibility
- Checks architecture conflicts
- Confirms backward compatibility
- Suggests improvements

### Implementation Agent
- Implements approved plan
- Follows project conventions
- Makes minimal changes
- Updates all affected files

### Code Review Agent
- Critical code review
- Finds defects
- Verifies quality
- Approves or rejects

### Testing Agent
- Identifies affected tests
- Verifies backward compatibility
- Adds/updates tests
- Produces test plan

## Quick Start Commands

### Start Multi-Agent Workflow
```
/skill team-lead
```

### Use Specific Agent
```
/skill planning-agent
/skill feasibility-agent
/skill implementation-agent
/skill code-review-agent
/skill testing-agent
```

### Use Existing Project Skills
```
/skill add-endpoint
/skill add-indicator
/skill add-strategy
/skill add-frontend-page
/skill run-tests
/skill trace-signal
```

## Quality Gates

1. **Plan Completeness** - All modules identified, risks documented
2. **Feasibility Approval** - Technical feasibility confirmed
3. **Implementation Fidelity** - Plan followed exactly
4. **Code Quality** - No critical defects
5. **Test Coverage** - All tests pass

## Output Examples

### Planning Agent Output
- Implementation plan
- Affected modules list
- Risks and assumptions
- Test requirements

### Feasibility Agent Output
- Feasibility assessment
- Architecture review
- Performance analysis
- Security review

### Implementation Agent Output
- Code changes
- File modifications
- Test updates
- Configuration changes

### Code Review Agent Output
- Defects found
- Quality metrics
- Approval status
- Recommendations

### Testing Agent Output
- Test results
- Coverage metrics
- Defects found
- Performance data

### Team Lead Output
- Final summary
- Files modified
- Tests added/updated
- Known risks
- Follow-up recommendations

## Troubleshooting

### Agent Stuck or Incomplete
- Team Lead will reject incomplete outputs
- Send back to responsible agent with specific feedback

### Quality Not Met
- Team Lead will reject and specify issues
- Agent must fix issues before proceeding

### Workflow Blocked
- Team Lead identifies bottleneck
- Coordinates resolution
- May escalate issues

## Best Practices

1. **Use multi-agent for complex work** - Don't use for simple fixes
2. **Follow agent instructions** - Each agent has specific responsibilities
3. **Provide complete context** - Include all necessary information
4. **Review feedback carefully** - Address all identified issues
5. **Test thoroughly** - Ensure tests cover all cases

## Agent Files

- `multi-agent-workflow.md` - Comprehensive documentation
- `.opencode/skills/team-lead/SKILL.md` - Team Lead coordination
- `.opencode/skills/planning-agent/SKILL.md` - Requirements analysis
- `.opencode/skills/feasibility-agent/SKILL.md` - Technical validation
- `.opencode/skills/implementation-agent/SKILL.md` - Code implementation
- `.opencode/skills/code-review-agent/SKILL.md` - Code review
- `.opencode/skills/testing-agent/SKILL.md` - Testing and verification

## Support

For questions or issues:
1. Check agent skill files for detailed documentation
2. Review multi-agent-workflow.md for workflow details
3. Contact Team Lead for coordination issues
