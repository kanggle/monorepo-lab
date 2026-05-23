# Task ID

TASK-PC-FE-025

# Title

console-web Dockerfile — pin `corepack prepare pnpm@latest --activate` to `pnpm@9.15.0` (host workflow's pinned version). `pnpm@latest` resolves to upstream's newest release at build time; on 2026-05-23 that became `pnpm@11.2.2`, which requires `Node.js v22.13+` for its `node:sqlite` built-in module dependency. The console-web Dockerfile uses `node:20-alpine` (v20.20.2), so the corepack install crashes inside the docker build with `Error [ERR_UNKNOWN_BUILTIN_MODULE]: No such built-in module: node:sqlite` and exits 1, killing the entire `pnpm install --frozen-lockfile` step — surfaced by dispatch run `26320586292` job `77488762541` (workflow_dispatch on TASK-MONO-132's impl branch) step `Start remaining containers (finance + console-bff + console-web)`. Yesterday's nightly (`26319887335`) had the same Dockerfile but corepack pulled an older pnpm that still ran on Node 20; the newly-pinned host runner action (`pnpm/action-setup@v4 with version: '9.15.0'`) is the canonical version this project is built and tested against, so the Dockerfile should match.

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- fix

---

# Dependency Markers

- **discovered by**: TASK-MONO-132 (workflow_dispatch verification run `26320586292`, 2026-05-23). MONO-132's AC-1 verification surfaced this horizontal regression — MONO-132's own fix (seed.sql section 6 split) was verified working (step 11 `Apply seed.sql` SUCCESS), but the next workflow step (step 12 `Start remaining containers`) failed during console-web image build due to this Dockerfile gap.
- **prerequisite of**: TASK-MONO-132 AC-1 full verification. After this fix lands on main, MONO-132's impl branch needs to be rebased and `workflow_dispatch` re-run to verify step 12.5 `Wait for finance-account-service health` + step 12.7 `Apply seed-finance.sql` both SUCCESS.
- **discovered by (root reference)**: any future console-web docker build (PR-time CI smoke `platform-console-e2e-smoke` job, nightly `platform-console-e2e-fullstack` job, local `docker compose -f docker-compose.e2e.yml up -d --build console-web`). Until pinned, every fresh `corepack prepare` invocation pulls the current upstream `pnpm@latest`, so any breaking pnpm release re-introduces this failure.

---

# Goal

Make the `console-web` docker image build reproducible against Node.js v20 (the project's chosen runtime). Specifically: the two `RUN corepack enable && corepack prepare pnpm@latest --activate` lines in `projects/platform-console/apps/console-web/Dockerfile` (deps stage line 15, builder stage line 22) should pin a specific pnpm version that is known compatible with Node v20 and consistent with the host workflow's `pnpm/action-setup@v4 with version: '9.15.0'`. After this fix lands, `docker compose -f docker-compose.e2e.yml up -d --build console-web` should succeed in CI and locally regardless of upstream pnpm releases.

## Root cause (verbatim quotes from CI artifacts)

- Dispatch run `26320586292` (workflow_dispatch on `task/mono-132-impl-pc-e2e-seed-finance-phase-split`, 2026-05-23T02:05:Z) job `Platform Console E2E full-stack (Playwright + docker compose)` (job id `77488762541`):
  - Step 11 `Apply seed.sql (GAP operators + OIDC tweaks + finance_db preamble)` — **success** (MONO-132 fix held).
  - Step 12 `Start remaining containers (finance + console-bff + console-web)` — **failure**. Console-bff + finance-account-service image builds completed; console-web build failed at `#65 [console-web deps 6/6] RUN pnpm install --frozen-lockfile`:
    ```
    #65 0.222 warn: This version of pnpm requires at least Node.js v22.13
    #65 0.222 warn: The current version of Node.js is v20.20.2
    #65 0.222 warn: Visit https://r.pnpm.io/comp to see the list of past pnpm versions with respective Node.js version support.
    #65 0.485 node:internal/modules/cjs/loader:1031
    #65 0.485       throw new ERR_UNKNOWN_BUILTIN_MODULE(request);
    #65 0.485             ^
    #65 0.485 Error [ERR_UNKNOWN_BUILTIN_MODULE]: No such built-in module: node:sqlite
    #65 0.485     at Module._load (node:internal/modules/cjs/loader:1031:13)
    #65 0.485     at Module.require (node:internal/modules/cjs/loader:1289:19)
    #65 0.485     at require (node:internal/modules/helpers:182:18)
    #65 0.485     at ../store/index/lib/index.js (file:///root/.cache/node/corepack/v1/pnpm/11.2.2/dist/pnpm.mjs:16125:25)
    ...
    #65 ERROR: process "/bin/sh -c pnpm install --frozen-lockfile" did not complete successfully: exit code: 1
    target console-web: failed to solve: process "/bin/sh -c pnpm install --frozen-lockfile" did not complete successfully: exit code: 1
    ##[error]Process completed with exit code 1.
    ```
- Dockerfile source [projects/platform-console/apps/console-web/Dockerfile:14-15, 21-22](../../apps/console-web/Dockerfile):
  ```
  # Install pnpm
  RUN corepack enable && corepack prepare pnpm@latest --activate
  ...
  FROM node:20-alpine AS builder
  RUN corepack enable && corepack prepare pnpm@latest --activate
  ```
- Workflow source [.github/workflows/nightly-e2e.yml:661-664](../../../../.github/workflows/nightly-e2e.yml#L661-L664) — host workflow installs pnpm via the action with explicit pin:
  ```yaml
  - name: Set up pnpm
    uses: pnpm/action-setup@v4
    with:
      version: '9.15.0'
  ```
  The host runner uses `9.15.0`; the Docker image used `corepack prepare pnpm@latest` which on 2026-05-23 resolved to `11.2.2`. This is the version drift that surfaced — host pnpm and container pnpm should be the same to avoid this kind of latent breakage.
- Comparison: `projects/ecommerce-microservices-platform/apps/web-store/Dockerfile:6` and `projects/ecommerce-microservices-platform/apps/admin-dashboard/Dockerfile:6` both use `RUN corepack enable pnpm` (without `prepare pnpm@latest`), which honors the `packageManager` field in their `package.json`. console-web is the only Dockerfile in the portfolio with the `pnpm@latest` pattern.

## Decision authority — Option A (`pnpm@9.15.0` literal pin)

Three real options were considered:

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A — pin to `pnpm@9.15.0` literal** | Replace both `pnpm@latest` occurrences with `pnpm@9.15.0` (the version `.github/workflows/nightly-e2e.yml` installs on the host runner via `pnpm/action-setup@v4 with version: '9.15.0'`). | Container pnpm matches host pnpm → no drift between local dev (host pnpm) and CI build (container pnpm). Reproducible builds against any base image — the version is hard-coded in the source tree. Trivial 2-line edit. | Requires occasional bumps when the project intentionally upgrades pnpm — but those bumps are intentional and would land via a coordinated change to both Dockerfile and `pnpm/action-setup@v4` version. |
| **B — switch to `corepack enable pnpm` (ecommerce pattern)** | Drop `corepack prepare pnpm@latest --activate` entirely; use only `corepack enable pnpm` and rely on `package.json` `packageManager` field. | Single source of truth (`packageManager` in package.json). Matches the ecommerce + admin-dashboard Dockerfile pattern. | Requires `package.json` to declare `packageManager` (likely not present in console-web's current `package.json`). Adds a second file change (Dockerfile + package.json). Slightly more invasive given this task's tight scope (the immediate fix is the regression, not a Dockerfile pattern refactor). Could be a separate cleanup task after this fix lands. |
| **C — bump base image to `node:22-alpine`** | Update `FROM node:20-alpine` to `FROM node:22-alpine` (3 occurrences: deps + builder + runner stages). | Unblocks pnpm 11.x; future-proof for pnpm releases. | Project hasn't validated Node 22 compatibility for the Next.js app + dependencies. Larger surface change. Not what surfaced — pnpm 11 is the immediate cause; the project deliberately chose Node 20. |

**Chosen — Option A.** Rationale:

1. **Consistency with host pinning.** `.github/workflows/nightly-e2e.yml` already pins pnpm `9.15.0` on the host. Pinning the container to the same version eliminates host-vs-container drift — both runtimes use the same pnpm, so the same `pnpm-lock.yaml` is interpreted identically.

2. **Minimal change, immediate fix.** 2-line edit (line 15 + line 22). No `package.json` change. No base image upgrade. Reverts to the *intent* of having a known pnpm version, not the accidental "whatever pnpm latest happens to be at build time" outcome that bit this run.

3. **Reproducible across upstream pnpm releases.** Future pnpm 12.x / 13.x releases (which may have additional Node.js requirements) cannot break this Dockerfile until the project consciously bumps the pin.

4. **Option B is the right refactor for a different task.** Switching to `corepack enable pnpm` + `packageManager` field is a portfolio-wide convention question — should all web Dockerfiles align on this pattern? — and deserves its own focused task or ADR.

5. **Option C couples two concerns.** Bumping to Node 22 unblocks newer pnpm but introduces Next.js runtime compatibility risk; the project should bump Node 22 deliberately, not as a side-effect of a regression fix.

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/Dockerfile` line 15 + line 22 — replace `corepack prepare pnpm@latest --activate` with `corepack prepare pnpm@9.15.0 --activate` (twice). Optionally add an inline comment near the first occurrence cross-ref'ing this task + the host workflow pin.
- This task md + project `projects/platform-console/tasks/INDEX.md` ready entry.

## Out of Scope

- `projects/platform-console/apps/console-web/package.json` — no `packageManager` field added (Option B's territory; deferred).
- Base image upgrade to `node:22-alpine` (Option C's territory; deferred).
- Other web Dockerfiles (`web-store`, `admin-dashboard`, `admin-portal` if any) — they already use `corepack enable pnpm` and do not hit this regression.
- `pnpm-lock.yaml` regeneration — `9.15.0` is the version the lockfile was generated with (verified by host action pin); no lockfile change needed.
- Workflow change (`.github/workflows/nightly-e2e.yml` byte-unchanged — workflow already pins the host pnpm correctly).
- TASK-MONO-132 dispatch run retry — that's a follow-up action after this PR merges (post-merge, MONO-132's impl branch rebases and dispatches `workflow_dispatch` again).

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — After this fix lands on main, MONO-132's impl branch rebased + `workflow_dispatch` re-run shows step 12 `Start remaining containers` SUCCESS (console-web image build completes without `ERR_UNKNOWN_BUILTIN_MODULE`). Or: a fresh PR-time CI smoke (`platform-console-e2e-smoke` job) shows console-web image build green.
- [ ] **AC-2 (functional, local)** — From `projects/platform-console/`:
  ```bash
  docker compose -f docker-compose.e2e.yml build console-web
  # No `ERR_UNKNOWN_BUILTIN_MODULE` warning; `RUN pnpm install --frozen-lockfile` completes without exit 1.
  docker compose -f docker-compose.e2e.yml run --rm console-web pnpm --version
  # Prints 9.15.0
  ```
- [ ] **AC-3 (hard invariant — scope)** — `git diff --stat origin/main` shows exactly 2 file modifications: the Dockerfile + this task md (the INDEX move is part of the close-chore PR). No `pnpm-lock.yaml` change, no `package.json` change, no other Dockerfile change.
- [ ] **AC-4 (literal grep, post-fix)** — `git grep -n 'pnpm@latest' projects/platform-console/apps/console-web/Dockerfile` returns 0 matches; `git grep -n 'pnpm@9.15.0' projects/platform-console/apps/console-web/Dockerfile` returns 2 matches.
- [ ] **AC-5 (host workflow consistency)** — `git grep -n "version: '9.15.0'" .github/workflows/nightly-e2e.yml` returns ≥ 1 match (the host pin is unchanged and the Dockerfile pin equals it).
- [ ] **AC-6 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes (PR `state=MERGED` + `mergeCommit` matches `git log origin/main` tip + pre-merge `gh pr checks` snapshot `failing=0`). Same protocol PC-FE-024 + MONO-131 just used.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md`.

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/apps/console-web/Dockerfile`](../../apps/console-web/Dockerfile) — the file modified by this task.
- [`.github/workflows/nightly-e2e.yml`](../../../../.github/workflows/nightly-e2e.yml) — `platform-console-e2e-fullstack` job, `Set up pnpm` step (line ~661-664) shows the host pnpm version this Dockerfile aligns to.
- [`projects/ecommerce-microservices-platform/apps/web-store/Dockerfile`](../../../ecommerce-microservices-platform/apps/web-store/Dockerfile) — read-only reference; ecommerce uses `corepack enable pnpm` without `prepare pnpm@latest` (Option B's pattern, deferred as separate task).
- [`tasks/ready/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md`](../../../../tasks/ready/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md) — the discovering task; this fix unblocks its AC-1 verification.

# Related Contracts

- None. This task does not touch any HTTP or event contract, parity matrix, ADR, or domain spec.

# Related Skills

- None additional. CLAUDE.md standard workflow applies (spec PR → impl PR → close-chore PR with BE-303 3-dim verification).

---

# Edge Cases

- **Future intentional pnpm bump** — when the project decides to upgrade pnpm (e.g. to 10.x or 11.x), both the Dockerfile pin and the `pnpm/action-setup@v4 with version` need to be updated in the same PR. The inline comment near the Dockerfile pin will say so.
- **`pnpm-lock.yaml` lockfile-format version** — `pnpm-lock.yaml` was generated with the v9 series (verified by host action pin); pinning the container to `9.15.0` honors the lockfile format. If the project ever ships a v10/v11 generated lockfile, the host pin would be bumped first (PR-time CI smoke catches lockfile-version mismatches).
- **`corepack` itself failing** — `corepack` is bundled with Node.js since v16; `node:20-alpine` ships it. No additional install needed.

# Failure Scenarios

- **`pnpm@9.15.0` removed from npm registry** — extremely unlikely (npm preserves historic versions). If it ever happens, this task's fix would need to revisit the version pin choice.
- **A future Node 20 patch releases removes corepack support** — `corepack` is bundled; removal is not on the Node 20 LTS roadmap.

---

# Test Requirements

- AC-2 local one-shot reproduction (`docker compose build console-web` succeeds).
- CI verification = the next PR-time CI on this task's impl PR (the path-filter triggers a console-web build via the `platform-console-e2e-smoke` job which uses the same Dockerfile).
- No new automated test needed; existing CI catches future drift.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + project `tasks/INDEX.md` ready entry), (2) impl PR (single-file 2-edit to Dockerfile), (3) close-chore PR (`git mv ready/ → done/` + Status flip + INDEX move + BE-303 3-dim verification documented).
- [ ] AC-1 through AC-6 all checked off in the close-chore PR description.
- [ ] TASK-MONO-132 INDEX entry (root `tasks/INDEX.md`) updated with one cross-ref line at its review entry: "**MONO-132 AC-1 unblocked by TASK-PC-FE-025** (2026-05-23, PR #N — console-web Dockerfile pnpm@latest → pnpm@9.15.0 pin)".
- [ ] After this PC-FE-025 close PR merges, dispatcher rebases MONO-132 impl branch and re-runs `workflow_dispatch` to verify MONO-132 AC-1 (steps 12.5 + 12.7 both SUCCESS in addition to step 11).

---

# 메타 (intended)

① **Horizontal regression vs. cycle layer** — PC-FE-023/024/MONO-132 are TASK-MONO-014 cycle-pattern fixes (each closes a *progressively-revealed* nightly gap on the same boot sequence). PC-FE-025 is *orthogonal* — an upstream version drift that happened to surface during MONO-132's verification window but exists independently of the boot-sequence chain. The cycle pattern still works as designed; this is just a separate horizontal regression to clear so the cycle can continue verifying.

② **`pnpm@latest` is the bug, not the version** — pinning anything (whether 9.15.0 or 10.x) eliminates the regression. The choice of 9.15.0 specifically is for *consistency with host runner pinning*, which is a separate value the project deliberately chose. Future bumps require coordinated changes to both the Dockerfile and the host action pin.

③ **Surfaced by MONO-132 verification, not by MONO-132 itself** — MONO-132's fix (seed.sql section 6 split) works correctly. AC-1 step 11 SUCCESS proves it. The MONO-132 impl branch needed a `workflow_dispatch` to verify its own AC-1; the verification dispatch happened to occur during the upstream pnpm 11.2.2 release window. Without this dispatch, the regression would have first surfaced on the post-merge nightly cron — same cause, same fix.

④ **Portfolio scope check confirms single-file regression** — `git grep 'pnpm@latest'` over all `projects/*/apps/*/Dockerfile` returns matches only in console-web. ecommerce web-store + admin-dashboard use the `corepack enable pnpm` (read `packageManager`) pattern instead. So this fix is genuinely surgical.

⑤ **Sequence relative to MONO-132** — MONO-132 impl PR #766 should NOT be merged before this PR lands and MONO-132's `workflow_dispatch` re-verification passes. BE-303's "main GREEN restored before close chore" spirit applies — even though MONO-132's PR-time push CI is fully green, its functional AC-1 cannot be verified until console-web image builds again. Wait for the verification, then merge MONO-132. This is the same posture MONO-131/MONO-130 used: ensure objective verification before close chore.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (surgical 2-line Dockerfile pin — mechanical execution; Option A choice already made in spec; no decision authority remaining for impl).
