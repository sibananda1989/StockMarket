- [Stock Detail Page Overview](stock-detail-overview.md) — Structure and file usage for stock detail page

## Audit 2026-08-21
- **Controllers**: 27, **Services**: 37, **Calculators**: 34, **Entities**: 29, **Repos**: 26, **DTOs**: 59, **HTML**: 14, **JS**: 15, **Strategies**: 10, **Tests**: 81
- **SignalThresholds**: SBUY≥7, BUY≥3, SELL≤-4, SSELL≤-7 — synchronized across `docs/PROJECT_MANIFEST.md`, `README.md`, `docs/SIGNAL_ENGINE_SPEC.md`, `docs/PROJECT_CONTEXT.md`, `docs/API_SPEC.md`
- **IndicatorType**: 31 values — updated in manifest + README + DATABASE_DESIGN
- **Cache drift noted**: `CacheConfig.java` (7) vs `application.properties` (5) — documented in manifest/README
- Docs refreshed: `PROJECT_MANIFEST`, `PROJECT_CONTEXT`, `API_SPEC`, `DATABASE_DESIGN`, `SIGNAL_ENGINE_SPEC`, `README`, `MEMORY`
- Remaining root docs (`BRD.md`, `CHANGELOG.md`, `CLAUDE.md`, `AGENTS.md`) verified — no stale counts requiring patch