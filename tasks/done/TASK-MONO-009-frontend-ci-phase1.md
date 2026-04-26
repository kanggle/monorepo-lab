# TASK-MONO-009 — Frontend CI Phase 1: lint + build (ecommerce)

**Status**: done  
**Completed**: 2026-04-26

---

## Goal

Add a `frontend-checks` CI job to `.github/workflows/ci.yml` that runs lint and build for the
ecommerce-microservices-platform frontend (web-store + admin-dashboard + shared packages).
Phase 1 covers the two gating checks that are Docker-free and fast: lint and Next.js build.

---

## Scope

- `.github/workflows/ci.yml` — new `frontend-checks` job
- `projects/ecommerce-microservices-platform/packages/*/eslint.config.mjs` — flat-config shims for
  packages that ran `eslint src/` directly (types, ui, utils, api-client)
- `apps/admin-dashboard` — two unused-variable fixes (use-templates.ts, EditProduct.test.tsx)
- `apps/web-store` — source-file fixes (unused var, any types) + test-file fixes (unused imports,
  missing displayName on createWrapper components)
- `packages/api-client` — one unused variable fix in test

No changes to shared libs, wms-platform, or ecommerce backend services.

---

## Acceptance Criteria

- [x] `pnpm lint` exits 0 for all packages (10/10 turbo tasks green)
- [x] `pnpm build` exits 0 on Linux/CI (both Next.js apps compile and generate pages; Windows-only
      EPERM on `output: 'standalone'` symlinks is a local-only limitation unrelated to TypeScript
      correctness)
- [x] `frontend-checks` job added to ci.yml: Node 20, pnpm 9.15, frozen install, lint, build
- [x] Job is independent (no `needs`) — runs in parallel with `build-and-test`

---

## Related Specs

- `projects/ecommerce-microservices-platform/package.json` — `packageManager: pnpm@9.15.0`,
  `engines: { node: ">=20.0.0" }`
- `turbo.json` — `lint` depends on `^build`; `build` outputs `.next/**`, `dist/**`

---

## Related Contracts

None.

---

## Edge Cases

- **ESLint 9 flat config**: Packages running `eslint src/` directly need `eslint.config.mjs` because
  ESLint 9 no longer reads `.eslintrc.*` by default. Apps using `next lint` are unaffected (Next.js
  internally sets legacy-config mode). Fix: add `eslint.config.mjs` using `FlatCompat` to wrap the
  existing `@repo/eslint-config/library` legacy config.
- **Windows symlink EPERM on standalone output**: `output: 'standalone'` in next.config.ts requires
  symlink creation which fails on Windows without Developer Mode. This is not a CI concern (Ubuntu
  runner), and not a correctness concern (compilation succeeds before the symlink step).

---

## Failure Scenarios

- pnpm cache miss on CI adds ~30s to install; `cache-dependency-path` pins the lockfile correctly.
- `--frozen-lockfile` will fail if pnpm-lock.yaml is out of sync — intentional gating.
