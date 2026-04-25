# Migration Notes — ecommerce-microservices-platform

History of how this project was imported into and reconciled with the `monorepo-lab` monorepo. Operational rules live at the repo root (`CLAUDE.md`, `rules/`, `platform/`, `.claude/`); this file is audit trail only.

## Import (2026-04-21)

This project was imported into `monorepo-lab` via `git subtree` and now participates in the monorepo's shared rule and library layers.

**Reconciliation status:**

- `specs/rules/`, `specs/platform/`, `tasks/templates/` — **deleted**. The legacy standalone-repo copies are gone; the authoritative sources are root `rules/`, root `platform/`, root `tasks/templates/`. Any promotable universal content was merged up (notably `platform/object-storage-policy.md` now lives at root as shared policy); ecommerce-specific content was absorbed into `rules/domains/ecommerce.md` (Ubiquitous Language, Standard Error Codes cross-ref) and `platform/error-handling.md` (domain error-code sections tagged `[domain: ecommerce]`).
- `.claude/` — **fully removed**. All agent / skill / command / hook / template / workflow resolution now happens at root `.claude/` unconditionally. The three files Phase 4 kept as "project-specific overrides" were removed in Phase 5 after re-examination: `coordinator.md` and `process-tasks.md` differed from root only in example-string content (no functional value), and `frontend-engineer.md`'s `domains: [web-store, admin-dashboard]` override contradicted root `.claude/agents/domain/README.md` L18 which classifies `frontend-engineer` as domain-agnostic and forbids project-scoped overrides. Any future ecommerce-specific agent routing (which frontend apps, which services) must be captured in `PROJECT.md` or per-service `specs/services/<name>/architecture.md`, not in nested agent frontmatter.
- Historical task records in `tasks/done/` may contain broken `specs/rules/...` / `specs/platform/...` links — preserved as immutable history; rewrite is not planned.

## Library consolidation (2026-04-25)

Project-internal `libs/` and `settings.gradle` were removed; ecommerce now references the monorepo's shared `libs/` directly via root `settings.gradle` `include(...)`, matching the wms-platform pattern. The earlier composite-build isolation was an artifact of the import process, not a deliberate boundary — root libs is the single source.

## CI workflow consolidation (2026-04-25)

Project-internal `.github/workflows/` (`backend-ci.yml`, `frontend-ci.yml`, `docker-build.yml`) was removed. All three were inert in the monorepo: they triggered on `branches: [master]` (monorepo uses `main`), filtered on standalone-repo paths, and depended on the just-removed project-internal `settings.gradle`. Java backend CI now runs through root `.github/workflows/ci.yml` (`./gradlew check`). Frontend and Docker-build CI for ecommerce are not yet covered at root — to be designed as monorepo-wide jobs in a follow-up.

## Standalone-repo CI badges (2026-04-25)

Removed standalone-era CI badges from `README.md` (`backend-ci`, `frontend-ci`, `docker-build` pointing at `kanggle/ecommerce-microservices-platform/master`). The standalone repo still exists for portfolio dual-deployment and keeps its own README/badges; the monorepo copy should not advertise CI signals from a different build.
