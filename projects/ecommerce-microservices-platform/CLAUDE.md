# CLAUDE.md — ecommerce-microservices-platform

This project is a member of the `monorepo-lab` monorepo. AI agents and developers must follow the monorepo root `CLAUDE.md`, not this file.

---

## Do not read this file as a source of truth

The operating rules, source-of-truth priority, workflow, and Hard Stop rules all live at the **repo root** `CLAUDE.md`. Path references there use repo-root-relative paths (e.g. `rules/`, `platform/`, `libs/`) and project-relative paths (e.g. `PROJECT.md`, `specs/`, `tasks/ready/`) that resolve inside this directory.

## Migration note (2026-04-21)

This project was imported into `monorepo-lab` via `git subtree` and now participates in the monorepo's shared rule and library layers.

**Reconciliation status:**

- `specs/rules/`, `specs/platform/`, `tasks/templates/` — **deleted**. The legacy standalone-repo copies are gone; the authoritative sources are root `rules/`, root `platform/`, root `tasks/templates/`. Any promotable universal content was merged up (notably `platform/object-storage-policy.md` now lives at root as shared policy); ecommerce-specific content was absorbed into `rules/domains/ecommerce.md` (Ubiquitous Language, Standard Error Codes cross-ref) and `platform/error-handling.md` (domain error-code sections tagged `[domain: ecommerce]`).
- `.claude/` — nested directory still present; **root `.claude/` wins** for agent/skill resolution. A future merge into root is a pending follow-up.
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
