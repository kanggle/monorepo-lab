# CLAUDE.md — ecommerce-microservices-platform

This project is a member of the `monorepo-lab` monorepo. AI agents and developers must follow the monorepo root `CLAUDE.md`, not this file.

---

## Do not read this file as a source of truth

The operating rules, source-of-truth priority, workflow, and Hard Stop rules all live at the **repo root** `CLAUDE.md`. Path references there use repo-root-relative paths (e.g. `rules/`, `platform/`, `libs/`) and project-relative paths (e.g. `PROJECT.md`, `specs/`, `tasks/ready/`) that resolve inside this directory.

## Migration note (2026-04-21)

This project was imported into `monorepo-lab` via `git subtree` and now participates in the monorepo's shared rule and library layers.

**Reconciliation status:**

- `specs/rules/`, `specs/platform/`, `tasks/templates/` — **deleted**. The legacy standalone-repo copies are gone; the authoritative sources are root `rules/`, root `platform/`, root `tasks/templates/`. Any promotable universal content was merged up (notably `platform/object-storage-policy.md` now lives at root as shared policy); ecommerce-specific content was absorbed into `rules/domains/ecommerce.md` (Ubiquitous Language, Standard Error Codes cross-ref) and `platform/error-handling.md` (domain error-code sections tagged `[domain: ecommerce]`).
- `.claude/` — **reduced to a minimal project-specific overlay**. 126 duplicate files deleted (identical to root, or only differing in legacy `specs/rules/` → `rules/` path references). Only three genuine overrides remain: `agents/common/coordinator.md` and `commands/process-tasks.md` (use concrete ecommerce service names in examples), and `agents/common/frontend-engineer.md` (routes frontend work to `web-store` + `admin-dashboard` instead of the generic `frontend` domain). `settings.json` was deleted — its extra entries were stale after the composite-build move (referenced old standalone `apps:auth-service` Gradle paths and the pre-import standalone repo directory). Hooks and all other skills/agents/commands now load from root `.claude/`.
- Historical task records in `tasks/done/` may contain broken `specs/rules/...` / `specs/platform/...` links — preserved as immutable history; rewrite is not planned.

## What lives here

Only project-specific content (per monorepo boundary rule):

- `PROJECT.md` — domain/traits classification
- `apps/` — service implementations
- `libs/` — **project-internal** shared libraries (not to be confused with root `libs/`)
- `specs/contracts/`, `specs/services/`, `specs/features/`, `specs/use-cases/`
- `tasks/` — project task lifecycle (except `templates/` which is shared at root)
- `knowledge/`, `docs/` (excluding `docs/guides/` which is shared at root)
- `infra/`, `k8s/`, `load-tests/`, `scripts/`
- Frontend tooling: `package.json`, `pnpm-workspace.yaml`, `turbo.json`, `packages/`
