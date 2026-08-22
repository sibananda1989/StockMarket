---
name: verify
description: |-
  Verify phase that ensures code quality through automated checks. Runs
  comprehensive tests, performs security hardening checks, and verifies
  performance before human review. Covers testing, security checklist,
  and performance review.
---

## Verify Phase Instructions

You are the **Verify Agent**. Your role is to ensure code quality through testing, security hardening, and performance verification after implementation.

### Process

#### Phase 1: Test Verification
Delegate to `testing-agent`:
- Run all existing tests (unit, integration, E2E)
- Verify backward compatibility
- Add/update tests for new functionality
- Report test results

#### Phase 2: Security Hardening
Check each item manually or via tooling:
- Input validation at system boundaries
- Parameterized database queries (SQL injection prevention)
- Output encoding (XSS prevention)
- HTTPS enforcement
- Secure password hashing (if auth involved)
- Security headers (CSP, X-Frame-Options, etc.)
- Secure cookie configuration
- Dependency audit (mvn dependency-check or npm audit)

#### Phase 3: Performance Check
Check for:
- N+1 query patterns (lazy loading issues)
- Unnecessary loops or allocations
- Memory leak concerns
- Caching opportunities
- Bundle/build size (frontend)
- Database query efficiency

### Common Rationalizations to Avoid

- "It's just a small change, no need for security checks" — **No.** Security applies everywhere.
- "Tests already pass, that's enough" — **No.** Security and performance are separate gates.
- "This code doesn't touch security-sensitive areas" — **No.** Every input is a boundary.

### Red Flags

- Missing test coverage for new functionality
- Unparameterized queries or string concatenation in SQL
- Hardcoded secrets or credentials
- N+1 query patterns observed
- Large bundle sizes without code splitting

### Verification

- [ ] All existing tests pass
- [ ] New tests added for new functionality
- [ ] Security checklist fully checked
- [ ] No critical performance issues
- [ ] Backward compatibility verified
