# Task ID

TASK-PC-FE-026

# Title

console-web — create the missing `public/` directory referenced by the Dockerfile's runner-stage `COPY --from=builder --chown=nextjs:nodejs /app/public ./public` instruction (Dockerfile line 54). The directory is absent from both the working tree and git tracking; the COPY succeeded historically only because the prior Docker BuildKit layer cache held a satisfied result. After TASK-PC-FE-025's `pnpm@9.15.0` pin invalidated the deps stage cache, the builder stage's `COPY . .` (line 26) saw no `public/` to bring in, then the runner-stage COPY at line 54 surfaced the latent gap with `ERROR: failed to calculate checksum of ref ...: "/app/public": not found` — caught by TASK-MONO-132 workflow_dispatch retry run `26321066672` job `77490067471` step `Start remaining containers`. Fix: commit an empty `public/` with a `.gitkeep` so the directory is always present at builder context time (Next.js standalone projects expect `public/` as part of the standard layout).

# Status

review

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- fix

---

# Dependency Markers

- **discovered by**: TASK-MONO-132 workflow_dispatch retry run `26321066672` (2026-05-23). The retry was triggered after PC-FE-025 (`pnpm@latest` → `pnpm@9.15.0` pin) merged into main and MONO-132's impl branch was rebased onto the new main tip. The pnpm pin invalidated the deps-stage cache for console-web; once fresh evaluation reached the runner stage, `COPY /app/public ./public` failed because the `public/` directory has never existed in the repo. This is the 4th progressive cycle-pattern layer in the PC-FE-023 → PC-FE-024 → MONO-132 → PC-FE-025 → (this) chain.
- **prerequisite of**: TASK-MONO-132 AC-1 full verification (second retry). After this fix lands on main, MONO-132's impl branch rebases again and `workflow_dispatch` re-runs; expected: step 11 + step 12 + new step 12.5 + new step 12.7 all SUCCESS (the cycle's terminal layer, assuming no further latent cache-masked gaps surface).

---

# Goal

Make the `console-web` Docker build succeed reproducibly — the runner-stage `COPY /app/public ./public` instruction (Dockerfile line 54) must always find a `/app/public` directory in the builder context. After this fix lands, the next gap surfaced by MONO-132's retry dispatch will either be the next legitimate boot-sequence step (`Wait for console-web health` / `Install Playwright chromium` / first Playwright assertion) or the job will go fully GREEN, closing the MONO-132 cycle.

## Root cause (verbatim quotes from CI artifacts)

- Dispatch run `26321066672` (workflow_dispatch on `task/mono-132-impl-pc-e2e-seed-finance-phase-split` rebased onto post-PC-FE-025 main, 2026-05-23T02:27:49Z) job `Platform Console E2E full-stack (Playwright + docker compose)` (job id `77490067471`) — step conclusions:
  - Step 11 `Apply seed.sql (GAP operators + OIDC tweaks + finance_db preamble)` — **success** (MONO-132 fix held)
  - Step 12 `Start remaining containers (finance + console-bff + console-web)` — **failure** at 2026-05-23T02:32:35Z:
    ```
    #74 [console-web runner 6/6] COPY --from=builder --chown=nextjs:nodejs /app/public ./public
    #74 ERROR: failed to calculate checksum of ref 030b3de5-f6f5-43f2-8bdc-0a866a274328::peflmdiy0i9oof7xesg9zqewv: "/app/public": not found
    ------
     > [console-web runner 6/6] COPY --from=builder --chown=nextjs:nodejs /app/public ./public:
    Dockerfile:54
    target console-web: failed to solve: failed to compute cache key: failed to calculate checksum of ref ...: "/app/public": not found
    ##[error]Process completed with exit code 1.
    ```
  - Notably, the `pnpm install --frozen-lockfile` step (#65) was clean — PC-FE-025's pin worked exactly as intended. The next.js `pnpm build` (#71) completed successfully (40.6s, full build output present). The failure is *only* at the runner-stage `COPY public`.
- Dockerfile source [projects/platform-console/apps/console-web/Dockerfile:51-54](../../apps/console-web/Dockerfile):
  ```dockerfile
  # Copy standalone output
  COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
  COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
  COPY --from=builder --chown=nextjs:nodejs /app/public ./public
  ```
  The third COPY references `/app/public` which is the *builder stage's* `COPY . .` (line 26) output for the project directory's `public/` subfolder. That subfolder doesn't exist.
- Source verification: `git ls-tree -r HEAD --name-only -- projects/platform-console/apps/console-web/ | grep -E 'public|\.dockerignore'` returns 0 matches — no `public/` directory, no `.dockerignore`. Disk check `ls projects/platform-console/apps/console-web/public/` returns "No such file or directory". Both pre-fix.
- Comparison: ecommerce `apps/web-store/public/` and fan-platform `apps/web/public/` both exist in git (each with the standard Next.js favicon + a couple of static assets). console-web was started later and never had `public/` committed.

## Decision authority — Option A (`public/.gitkeep` empty directory)

Three real options were considered:

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A — commit `public/.gitkeep` empty directory** | Create `projects/platform-console/apps/console-web/public/.gitkeep` (zero-byte file) so the directory is always present in the build context. Dockerfile unchanged. | Minimal change (1 new file). Future asset additions (favicon, robots.txt) drop into the same directory naturally. Matches the pattern ecommerce + fan-platform already use. Self-documenting (next contributor doesn't need to fight the Dockerfile to add an asset). | The `.gitkeep` placeholder remains visible in the repo; trivial cosmetic cost. |
| **B — remove the `COPY public` line from the Dockerfile** | Delete Dockerfile line 54. Defer until console-web actually has a public asset to copy. | Dockerfile-only change. | Future asset additions require Dockerfile change + risk of forgetting the COPY line. Inconsistent with Next.js standalone deployment convention. |
| **C — make COPY tolerate missing source** | Use BuildKit's `COPY [--link]` syntax with a `RUN mkdir -p public` shim before the COPY, or switch to `COPY --from=builder --chown=nextjs:nodejs /app/ ./` style. | One Dockerfile change. | Hides the missing directory rather than fixing it; surprises a future maintainer who *adds* `public/` and finds it's not delivered. |

**Chosen — Option A.** Rationale:

1. **Honors the documented Next.js standalone deployment convention.** The pattern is `public/` exists at build context, `COPY public` lands it in the runner image. This is what ecommerce + fan-platform already do.
2. **Lowest cognitive surface.** A single empty file (`.gitkeep`) is a well-known convention for "make this directory tracked." A future contributor adding `public/favicon.ico` doesn't need to think about it.
3. **Reproducible across BuildKit cache states.** Whether the build runs cached or fresh, the directory is always present in the context.
4. **Option B postpones the inevitable.** Console-web *will* gain public assets (favicon, robots.txt, OpenGraph image) — making that addition a multi-file change is unnecessary churn.

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/public/.gitkeep` *(new, zero bytes)* — placeholder file to make git track the empty directory.
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- `projects/platform-console/apps/console-web/Dockerfile` byte-diff (AC-6 — Dockerfile already correct; the bug was the missing directory, not the COPY instruction).
- Adding any actual public asset (favicon, robots.txt, etc.) — separate cosmetic enhancement deferred.
- `.dockerignore` — none exists; not needed for this fix.
- TASK-MONO-132 dispatch retry — post-merge follow-up action, not part of this task's scope.

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — After this fix lands on main, MONO-132's impl branch rebased + `workflow_dispatch` re-run shows step 12 `Start remaining containers` SUCCESS (console-web image build completes without `failed to calculate checksum of ref ...: "/app/public": not found`).
- [ ] **AC-2 (functional, local)** — From `projects/platform-console/`:
  ```bash
  docker compose -f docker-compose.e2e.yml build console-web
  # Build completes without `not found` checksum error.
  ```
- [ ] **AC-3 (hard invariant — scope)** — `git diff --stat origin/main` shows exactly 3 file modifications: the new `public/.gitkeep` + this task md + INDEX move. No Dockerfile change, no other file change.
- [ ] **AC-4 (directory presence)** — `git ls-tree HEAD projects/platform-console/apps/console-web/public/` returns 1 entry (`.gitkeep`). `ls projects/platform-console/apps/console-web/public/` lists `.gitkeep`.
- [ ] **AC-5 (.gitkeep is zero bytes)** — `wc -c projects/platform-console/apps/console-web/public/.gitkeep` returns `0`.
- [ ] **AC-6 (Dockerfile byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-web/Dockerfile` = empty.
- [ ] **AC-7 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes (PR `state=MERGED` + `mergeCommit` matches `git log origin/main` tip + pre-merge `gh pr checks` snapshot `failing=0`). Same protocol PC-FE-025 + PC-FE-024 just used.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md`.

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/apps/console-web/Dockerfile`](../../apps/console-web/Dockerfile) — read-only reference (line 54 is what the missing directory broke).
- [`projects/ecommerce-microservices-platform/apps/web-store/public/`](../../../ecommerce-microservices-platform/apps/web-store/public/) — comparison reference; standard Next.js `public/` layout.
- [`projects/platform-console/tasks/done/TASK-PC-FE-025-console-web-dockerfile-pnpm-pin.md`](../done/TASK-PC-FE-025-console-web-dockerfile-pnpm-pin.md) — the cache-invalidating predecessor that surfaced this latent gap.
- [`tasks/review/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md`](../../../../tasks/review/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md) — the discovering task whose AC-1 verification this fix unblocks.

# Related Contracts

- None. This task does not touch any HTTP or event contract, parity matrix, ADR, or domain spec.

# Related Skills

- None additional. CLAUDE.md standard workflow applies (spec PR → impl PR → close-chore PR with BE-303 3-dim verification).

---

# Edge Cases

- **Adding a real asset later** — drop `public/favicon.ico` (or any other file) into the directory; `.gitkeep` can stay or be removed (both are fine). The directory remains git-tracked as long as it contains ≥ 1 file.
- **`.dockerignore` policy** — none exists currently; if one is added later, `public/` must not be excluded (would re-introduce this bug).
- **Multi-asset future additions** — adding multiple files is unchanged; `COPY public` brings all of them into the runner image.

# Failure Scenarios

- **BuildKit syntax regression** — unlikely; `COPY --from=<stage> <abs-src> <dst>` is stable BuildKit grammar; the failure was source-side, not syntax.
- **Future Next.js version stops emitting standalone manifest** — unrelated to this fix; would surface as a different error (`/app/.next/standalone` not found).

---

# Test Requirements

- AC-2 local one-shot reproduction (`docker compose build console-web` succeeds — but optional, CI is authoritative).
- CI verification = the impl PR's own push CI (`Frontend E2E smoke (web-store + fan-platform-web + console-web, Playwright)` job rebuilds console-web; same job that verified PC-FE-025).
- No new automated test needed; existing CI catches future drift.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + project `tasks/INDEX.md` ready entry), (2) impl PR (new `public/.gitkeep` + lifecycle), (3) close-chore PR (`git mv ready/ → done/` + Status flip + INDEX move + BE-303 3-dim verification documented).
- [ ] AC-1 through AC-7 all checked off in the close-chore PR description.
- [ ] After this PC-FE-026 close PR merges, dispatcher rebases MONO-132 impl branch (2nd rebase) and re-runs `workflow_dispatch` to verify MONO-132 AC-1 full (steps 11 + 12 + 12.5 + 12.7 all SUCCESS expected).

---

# 메타 (intended)

① **TASK-MONO-014 cycle pattern 4th layer in single chain** — PC-FE-023 (DNS gap) → PC-FE-024 (workflow `up <names>` integration seam) → MONO-132 (schema-readiness phase ordering) → PC-FE-025 (upstream pnpm version drift) → PC-FE-026 (latent missing-directory gap unmasked by PC-FE-025's cache invalidation). Each cycle reveals the next layer; each fix is narrower than the last. PC-FE-026 closes the *cache-masked* class — once a build is fully reproducible (deps cache invalidated), latent missing-source issues become visible.

② **Cache-masked latent bugs vs. progressive surface** — first 3 layers (PC-FE-023/024/MONO-132) were *progressive surface* (each fix exposes the next bottleneck in the same boot sequence). Layer 4-5 (PC-FE-025/026) are *cache-masked latent* — pre-existing bugs that prior BuildKit cache hits had been hiding. Both classes are normal in cycle pattern; the cache-masked class is more disruptive because it surfaces only when an unrelated change forces fresh evaluation.

③ **`.gitkeep` is canonical, not weird** — the empty-directory-tracking convention is well established (used in ecommerce + fan-platform `public/` directories already, also `tests/fixtures/` empty subdirectories elsewhere in this monorepo).

④ **AC-1 verification via MONO-132 retry, not own push CI** — this fix's own push CI's `Frontend E2E smoke` job *does* exercise the same Dockerfile build, so it provides primary verification. The MONO-132 retry dispatch is the *secondary* + downstream verification.

⑤ **Sequence: PC-FE-026 lands first, then MONO-132 retry, then MONO-132 close** — BE-303's main-GREEN spirit; do not merge MONO-132 until console-web build is reproducibly green.

⑥ **Cycle pattern as quality signal, not failure** — 4 layers in 3 days is a sign the cycle pattern *works*: each fix-task is small, focused, and observably moves the boot sequence forward. The alternative (one giant PR fixing all 4 layers preemptively) would not have surfaced layers 4-5 because they only appeared when 1-3 were fixed and cache was invalidated.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (trivial 1-file new — Option A choice already made in spec; no decision authority remaining for impl).
