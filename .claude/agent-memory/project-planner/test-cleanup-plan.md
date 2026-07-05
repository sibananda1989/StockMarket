# Test Cleanup Plan for Watchlist-to-Portfolio Flow

## Project Overview
Clean up test-created watchlists and stocks after each Playwright test to maintain database integrity and prevent test interference.

## Objectives & Success Criteria
- Ensure each test starts with a clean slate regarding watchlist and stock data
- Prevent foreign key constraint violations during cleanup
- Apply consistent cleanup pattern used elsewhere in the codebase (like SignalControllerIntegrationTest)
- All watchlist/portfolio tests pass after cleanup implementation

## Stakeholder Mapping
- **Primary**: Test Engineer (implements cleanup)
- **Secondary**: Backend Engineer (validates DB schema and cleanup order)
- **QA**: Verifies test suite stability

## Timeline
- Day 1: Analyze schema and design cleanup method
- Day 2: Implement cleanup utility
- Day 3: Integrate into test suite and verify

## Resource Allocation
- Test Engineer: 70% (implementation and integration)
- Backend Engineer: 30% (schema validation and review)

## Risk Register
| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Missing child tables in cleanup order | Medium | High (FK violations) | Schema analysis with backend engineer |
| Cleanup too slow affecting test runtime | Low | Medium | Optimize SQL statements, batch deletes |
| Inconsistent cleanup causing flaky tests | Medium | High | Run tests multiple times to verify |

## Communication Plan
- Daily standup updates on progress
- PR review with backend engineer before merging
- Post-implementation demo to QA team

## Assumptions & Constraints
- MySQL database is available for testing (localhost:3306/stockmarket)
- Follows existing pattern: delete child tables first, then parents
- Cleanup method will be placed in test utility package
- Playwright tests use JUnit 5 or similar framework supporting @AfterEach