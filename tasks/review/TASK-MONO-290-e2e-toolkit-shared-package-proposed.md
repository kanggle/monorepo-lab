# TASK-MONO-290 — Decide + (if accepted) bootstrap a shared `e2e-toolkit` package for cross-suite Playwright helpers

**Status:** review

**Type:** TASK-MONO (monorepo-level — architecture decision; ADR authoring)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (package-boundary + cross-project posture; ADR authoring)

---

## Decision (2026-06-16)

Authored as **[ADR-MONO-039](../../docs/adr/ADR-MONO-039-e2e-cross-suite-shared-toolkit-decision.md)** (PROPOSED). Direction **CHOSEN-PROPOSED = Option C** (user direction-question): **do NOT build a shared `e2e-toolkit` package** — keep the three suites independent and govern the small overlap by a documented login convention (C1–C4). The ADR awaits the explicit ACCEPT gate (self-ACCEPT prohibited). On ACCEPT there is **no bootstrap** (Option A/B not taken); the deliverable is the decision record + the documented convention. The Goal/Scope below is retained for provenance — the chosen outcome is "no package."

---

## Goal

The cross-suite e2e diagnosis (TASK-MONO-280 family) found the **same OIDC-PKCE login flow, the `devpassword123!` credential + Argon2id seed hash, the `waitForURL` landing predicate, and Playwright config presets duplicated across three sibling suites that are three separate pnpm packages**:

- `tests/federation-hardening-e2e/` (root, shared)
- `projects/platform-console/apps/console-web/tests/e2e/`
- `projects/ecommerce-microservices-platform/apps/web-store/e2e/`

Because they are independent packages, **no `import` can share code between them today** — only copy-paste stays "in sync" (and it already drifted: `input[name="username"]` vs `#username`; positive `startsWith('/dashboards')` vs negative `!startsWith('/login')` landing predicates; `devpassword123!` in 4 files). MONO-280 removed the *intra*-suite duplication in federation-e2e; this task addresses the *inter*-suite layer, which needs a shared package first.

This is the **gate** for any cross-suite consolidation (#3/#4/#5 of the diagnosis partially depend on the canonical login/credential shape this would define).

## Scope

**This task is a DECISION first (ADR), then a thin bootstrap if accepted.** Do not bootstrap the package before the ADR is ACCEPTED.

**Decision to make (ADR-MONO-0xx):**

- **Option A — `libs/e2e-toolkit` pnpm workspace package** exporting `driveOidcPkceLogin`, the credential/host constants, the canonical `waitForURL` landing predicate, and a `definePlaywrightPreset()` config factory. Each suite adds `@repo/e2e-toolkit` as a devDependency and imports. *Pro:* real code sharing, single drift-proof source. *Con:* the three suites must join a pnpm workspace (federation-e2e + web-store + console-web currently install independently); a new shared build/publish surface; Windows junction/`node_modules` hygiene cost (see project memory `env_worktree_node_modules_junction_cleanup_hazard`).
- **Option B — a source-only shared dir** (e.g. `tests/_shared/`) consumed via TS path mapping / relative import, no package. *Pro:* lighter, no workspace/publish. *Con:* relative-path imports across `projects/**` ↔ root break the shared/project boundary (CLAUDE.md strict boundary) and complicate each suite's `tsconfig`/`testDir`.
- **Option C — keep duplicated, lint-enforce parity** (a test asserting the four copies are byte-equal). *Pro:* zero structural change. *Con:* does not actually de-duplicate; perpetuates drift risk.

**If ACCEPTED (Option A assumed):**

1. Create `libs/e2e-toolkit` (package-agnostic: zero service names / domain entities — only Playwright + OIDC-PKCE mechanics, so it satisfies `platform/shared-library-policy.md` / HARDSTOP-03).
2. Move the canonical `driveOidcPkceLogin` + constants there; re-point each suite's `fixtures/login.ts` / `helpers/auth.ts` to thin wrappers.
3. One atomic PR per cross-project boundary rule (lib + every adopting suite).

## Acceptance Criteria

- **AC-1 (decision)** — an ADR (`docs/adr/ADR-MONO-0xx-…md`) records the chosen option with Context / Decision / Consequences / Alternatives, cross-referencing the MONO-280 diagnosis. Status ACCEPTED before any code.
- **AC-2 (boundary)** — if a package is created, it contains **no project-specific content** (HARDSTOP-03); the canonical login selector + landing predicate are chosen explicitly (one form, documented).
- **AC-3 (net-zero adoption)** — each adopting suite's `playwright test --list` is unchanged (same tests) and its login still reaches the same landing URL; no spec assertion changes. Adoption is behavior-preserving like MONO-280.
- **AC-4** — `devpassword123!` + the Argon2id hash exist in exactly one TS source after adoption (seed SQL copies are a separate, DB-side concern, noted not silently merged).

## Related Specs

- `platform/shared-library-policy.md` — the project-agnostic test a new `libs/` package must pass.
- `docs/adr/` — destination for the decision record.
- TASK-MONO-280 (`tasks/done/` once merged) — the intra-suite precedent + the duplication inventory this builds on.

## Related Contracts

None — test-harness + tooling only.

## Edge Cases

- **Workspace join** — federation-e2e, web-store, console-web each have their own `pnpm-lock.yaml`; folding into one workspace changes install topology and CI cache keys (see `project_frontend_docker_build_cache_optimization`).
- **Windows `node_modules` junctions** — a shared workspace package consumed by worktree-isolated sessions must respect the junction-cleanup hazard (memory `env_worktree_node_modules_junction_cleanup_hazard`).

## Failure Scenarios

- Bootstrapping before ADR acceptance → HARDSTOP-09 (architecture decision not in specs).
- A relative-path shared dir (Option B) crossing the `projects/**` ↔ root boundary → CLAUDE.md shared/project boundary violation.
