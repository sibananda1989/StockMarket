# AI Team Guidelines — StockTracker

## Agent Roles

| Agent | Responsibility |
|-------|---------------|
| **project-manager** | Entry point for all tasks. Analyzes requirements, breaks into subtasks, assigns to specialized agents, tracks dependencies, verifies output |
| **business-analyst** | Converts business requirements → user stories, acceptance criteria, workflows |
| **solution-architect** | Designs module boundaries, API contracts, integration strategy |
| **stock-market-expert** | Price action, S/R, trends, chart patterns, swing trading logic |
| **quant-analyst** | Indicator math, signal scoring, ranking algorithms, statistical validation |
| **backend-engineer** | Java/Spring Boot API implementation, services, signal engine |
| **frontend-engineer** | HTML/JS/CSS dashboards, charts, UI components |
| **database-engineer** | Schema design, indexing, query optimization, data migration |
| **signal-validation-engineer** | Validates signal logic, stop/target calculation, edge cases |
| **qa-engineer** | Test plans, unit tests, integration tests, Playwright E2E |
| **security-engineer** | Auth, API security, secrets, vulnerability review |
| **devops-engineer** | CI/CD, Docker, deployment, monitoring |

## Task Routing Rules

### When to Route to project-manager
- ALL new requirements first go to project-manager
- Ambiguous or multi-step requests
- Tasks spanning multiple domains (e.g., "add new indicator" → backend + signal + frontend + tests)

### Domain-Specific Routing

| If the task involves... | Route to... |
|-------------------------|-------------|
| New API endpoint (controller + DTO + service) | backend-engineer |
| Adding a technical indicator (calculator + enum + scoring) | quant-analyst → backend-engineer |
| Debugging why a stock got a specific score | signal-validation-engineer |
| Running/writing tests | qa-engineer |
| Frontend dashboard, charts, UI layout | frontend-engineer |
| Database schema change, query perf | database-engineer |
| Architecture review, module restructuring | solution-architect |
| Trading logic, price action rules | stock-market-expert |

### When to Ask for Clarification
- Missing stock ID, portfolio ID, or date range
- Unclear which portfolio a signal should be computed for
- Ambiguous "fix this" without description of expected vs actual behavior
- Request to modify scoring without specifying desired outcome

## Code Conventions

### Java
- Java 17, Spring Boot 3.2.5
- Use Lombok (`@RequiredArgsConstructor`, `@Slf4j`, `@Data`) — do NOT write manual constructors/loggers
- Use `var` for local variables where type is obvious from RHS
- Services are `@Service`, controllers are `@RestController`, repos are Spring Data JPA interfaces
- DTOs never reference entities; use constructor/static factory for conversion
- All API responses wrapped in `ApiResponse<T>` (success/error pattern)

### JavaScript
- ES6+ syntax, `const`/`let` over `var`
- `async/await` for all API calls — no callback patterns
- API calls go through `api.js` helper, never raw `fetch()`
- Chart.js for all charts (registered via `_charts` map for cleanup)
- Errors caught locally with `try/catch` + `.catch()` fallbacks — never let a chart crash the page

### Database
- All schema changes via JPA entities + `ddl-auto=update` (no manual SQL migration)
- Composite unique keys for idempotent inserts
- FK constraints on all relationships

## Quality Gates
1. No JS syntax errors — run `node -e "new Function(require('fs').readFileSync('...','utf8'))"` to verify
2. No 500 errors on API endpoints — check `/tmp/app.log`
3. Tests pass: `mvn test`
4. Signal scores are explainable — document factor breakdown for any changed scoring
5. Frontend handles missing data gracefully — `.catch()` on every API call

## Verification
- After any backend change: verify with curl or browser
- After scoring change: check signal distribution (BUY/HOLD/SELL counts)
- After frontend change: check browser console for errors
- After DB schema change: verify entity matches table columns
