# Task ID

TASK-PC-FE-035

# Title

Optimize the `console-web` Docker image build — BuildKit cache mounts for the Next.js incremental compile cache (`.next/cache`) and the pnpm store, plus a `.dockerignore` test-source exclusion, so source-only rebuilds are incremental instead of cold (measured 229s → 91s, ~60% off the dev rebuild loop). Build-infra only; no app code, no API/event/composition contract, no producer/console-bff change.

# Status

done

# Owner

frontend-engineer (build-infra — `console-web` Dockerfile/.dockerignore only; no application source, no BE/producer change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- deploy
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **precedent / same category**: [TASK-PC-FE-032](../done/TASK-PC-FE-032-console-web-dockerignore-windows-build-fix.md) — the prior `console-web` build-infra fix (added the missing `.dockerignore`, bundled spec+impl per `feedback_pr_bundling`). This task continues the same Dockerfile/.dockerignore surface and the same "build-infra, no app code, no contract" posture.
- **surfaced by**: [TASK-PC-FE-034](../done/TASK-PC-FE-034-overview-consolidation-bff-home-and-gap-drilldown.md) close work — repeated full `console-web` image rebuilds (to pick up merged changes) exposed that every source-only rebuild recompiles cold because `.next/cache` is not persisted across builds.
- **no dependency on**: any spec/ADR/contract change, any producer/`console-bff` change, any app source change. The build output (the running image) is behaviourally identical — only the build *path* is faster and the build *context* slimmer.
- **pnpm-version coupling (must NOT break)**: the Dockerfile pins `pnpm@9.15.0` to match `.github/workflows/nightly-e2e.yml` (TASK-PC-FE-025). This task does **not** bump pnpm and does **not** touch any workflow — it stays project-internal. A future pnpm bump remains a separate, cross-cutting change (Dockerfile + workflow together).

---

# Goal

Make the `console-web` production Docker image build fast on the common dev/CI path — a source change followed by a rebuild — without changing the image's runtime behaviour or size.

Today the `deps` stage (`pnpm install`) is already layer-cached, so it is skipped on a source-only change. The remaining cost is `pnpm build` (the Next.js compile), and because the `COPY . .` layer is invalidated on every source change, the build always recompiles **cold** — Next.js's incremental webpack/SWC cache (`.next/cache`) is discarded between builds. Persisting that cache via a BuildKit cache mount turns the cold recompile into an incremental one.

Two secondary wins ride along: a pnpm-store cache mount (helps only when the lockfile changes and the `deps` layer misses) and a `.dockerignore` exclusion of test sources / test-only configs (so a test-file edit no longer invalidates the `COPY . .` layer and triggers a needless recompile).

# Scope

## In Scope

Build-infra only — a single bundled PR (spec-less; no ADR/contract impact, per the `feedback_pr_bundling` precedent set by TASK-PC-FE-032):

1. **`apps/console-web/Dockerfile`**
   - Add the `# syntax=docker/dockerfile:1` directive (line 1) to enable `RUN --mount` cache mounts.
   - `deps` stage: `RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile` (pnpm content-addressable store persisted across builds).
   - `builder` stage: `RUN --mount=type=cache,target=/app/.next/cache pnpm build` (Next.js incremental compile cache persisted across builds).
   - The `runner` stage is **unchanged** — it copies only `.next/standalone` + `.next/static` + `public`, never `.next/cache`, so the mounted cache never enters the image (size unchanged).
2. **`apps/console-web/.dockerignore`**
   - Exclude test sources + test-only configs that `next build` never imports: `tests`, `vitest.config.ts`, `vitest.setup.ts`, `playwright.config.ts`, `playwright.smoke.config.ts`, `**/*.test.ts(x)`, `**/*.spec.ts(x)`, `.github`.
   - **`.eslintrc.json` is intentionally NOT excluded** — `next build` runs ESLint and relies on it.
3. **Task md + `INDEX.md`** ready entry (this file).

Optional / documented-not-committed:

4. **provenance/attestation off** — `--provenance=false --sbom=false` (or `BUILDX_NO_DEFAULT_ATTESTATIONS=1`) is a **build-command flag**, not a Dockerfile directive. Documented here as the recommended local/CI build invocation; not encoded in the Dockerfile. (Whether to wire it into compose/CI is deferred — it changes the published manifest shape.)

## Out of Scope

- **Any application source change** (`src/**`). The image's runtime behaviour is byte-identical.
- **Any API / event / composition-contract change.** No `console-integration-contract.md`, no producer, no `console-bff` touch.
- **pnpm version bump** — would couple to `.github/workflows/nightly-e2e.yml` (cross-cutting; separate task).
- **Wiring `--provenance=false` into compose or CI** — documented only; encoding it is a separate decision (manifest-shape change).
- **The other domain images** (auth/admin/console-bff/wms/scm/finance/erp) — out of scope; this is `console-web` only.
- **A shared/base frontend Dockerfile** abstraction across `console-web` + the ecommerce/fan Next.js apps — a reasonable future consolidation, but a separate (likely root `TASK-MONO`) task because it would touch shared scaffolding.

# Acceptance Criteria

- [ ] **AC-1** `apps/console-web/Dockerfile` carries `# syntax=docker/dockerfile:1` (line 1) and the two `RUN --mount=type=cache,...` directives (pnpm store on install, `.next/cache` on build). The `runner` stage COPY set is unchanged.
- [ ] **AC-2** `apps/console-web/.dockerignore` excludes the test sources / test-only configs listed in Scope §2; `.eslintrc.json` is present (NOT excluded).
- [ ] **AC-3** `docker buildx build` of `console-web` succeeds (rc=0) from the optimized Dockerfile — proving the `.dockerignore` exclusions do not remove a file `next build` needs (lint included).
- [ ] **AC-4** A source-only rebuild (one `src/**` line changed) is measurably faster than the cold build: cold (empty `.next/cache`) vs warm (populated mount) is recorded. **Measured 2026-06-02: cold 229s → warm 91s (~60% reduction); identical-source rebuild 8s (full layer cache).**
- [ ] **AC-5** Image size is unchanged (`.next/cache` is not copied into the `runner` stage). The recreated container serves: `/api/health` 200, `/` 307 → `/dashboards/overview`, `/login` 200.
- [ ] **AC-6** Diff scope is build-infra only: `git diff` touches exactly `apps/console-web/Dockerfile` + `apps/console-web/.dockerignore` + this task md + `INDEX.md`. No `src/**`, no producer, no `console-bff`, no contract/ADR.

# Related Specs

- [`specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md) — `console-web` is the Next.js standalone-output service; the build is its Dockerfile multi-stage (deps → builder → runner). No architecture decision changes (Service Type, hexagonal posture, edge routing all unchanged); this task only speeds the build path.
- [TASK-PC-FE-025] (Dockerfile pnpm pin `9.15.0` ↔ nightly-e2e workflow coupling) — the constraint this task must not break.

# Related Contracts

- **None.** Build-infra only — no HTTP API, no domain event, no `console-integration-contract.md` composition route is touched. The built artifact (the `console-web` image) is behaviourally identical; only its build is optimized.

# Edge Cases

- **BuildKit unavailable / cache mounts unsupported** — `# syntax=docker/dockerfile:1` requires BuildKit (default in modern Docker / `docker buildx`). On a legacy `DOCKER_BUILDKIT=0` builder the `RUN --mount` lines would error. Mitigation: the repo's compose/buildx path is BuildKit-default (the federation-e2e build used buildx); document the BuildKit requirement.
- **First build after a cache prune** — `docker builder prune` empties the cache mount; the next build is a cold 229s (the worst case, == today's every-build cost). Expected; the optimization is for the steady-state rebuild loop, not the first/post-prune build.
- **Stale `.next/cache` after a Next.js major upgrade** — a cache-format change across Next versions could in theory produce a stale-cache miss; Next invalidates its own cache on version change, so the mount simply repopulates. No correctness risk (the cache is an optimization, never a source of truth).
- **`.dockerignore` over-exclusion** — if a future file `next build` needs matched a test glob (e.g. a non-test file named `*.spec.ts` that is imported by app code), the build would fail. Mitigation: AC-3 (the build must succeed) is the guard; the excluded globs are test-only by convention.

# Failure Scenarios

- **`.dockerignore` excludes a build-required file → build fails** — e.g. excluding `.eslintrc.json` would change/lint-break `next build`. Mitigation: `.eslintrc.json` is explicitly kept (AC-2); AC-3 verifies the build still succeeds with the exclusions applied. (Verified 2026-06-02: optimized build rc=0.)
- **Cache mount silently not applied (no speedup)** — a typo in the mount target or a missing syntax directive would make the mount a no-op (build still correct, just not faster). Mitigation: AC-4 records the before/after timing; a missing speedup is the detect signal.
- **Image bloat from the cache leaking into the runner** — if the `runner` stage ever copied `.next/cache`, the image would balloon. Mitigation: AC-1 freezes the `runner` COPY set (standalone/static/public only); AC-5 asserts size unchanged.
- **provenance-flag drift** — documenting `--provenance=false` but compose/CI not using it means local and CI manifests differ in shape (not in app behaviour). Mitigation: kept out of scope/uncommitted here; flagged as a separate decision so it is consciously adopted, not silently assumed.

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (단일 Dockerfile + .dockerignore build-infra 변경, 이미 구현+실측 60% 검증 완료 — 남은 것은 형식 정리 + bundled PR + 3-dim merge-verify). TASK-PC-FE-032 선례대로 spec-less bundled PR.
