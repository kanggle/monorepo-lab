# CLAUDE.md — ecommerce-microservices-platform

This project is a member of the `monorepo-lab` monorepo. AI agents and developers must follow the monorepo root `CLAUDE.md`, not this file.

---

## Do not read this file as a source of truth

The operating rules, source-of-truth priority, workflow, and Hard Stop rules all live at the **repo root** `CLAUDE.md`. Path references there use repo-root-relative paths (e.g. `rules/`, `platform/`, `libs/`) and project-relative paths (e.g. `PROJECT.md`, `specs/`, `tasks/ready/`) that resolve inside this directory.

## Migration note (2026-04-21)

This project was imported into `monorepo-lab` via `git subtree` and now participates in the monorepo's shared rule and library layers. The following nested content is **legacy from the standalone-repo era** and is still present but **must not be used** when working inside the monorepo:

- `specs/rules/` — superseded by root `rules/` (shared). Delete after reconciliation.
- `specs/platform/` — superseded by root `platform/` (shared). Delete after reconciliation.
- `.claude/` — a future merge with root `.claude/` is planned. Until then, root `.claude/` wins for agent/skill resolution.

If you find yourself reading any of the legacy directories above, stop and consult root `CLAUDE.md` instead.

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
