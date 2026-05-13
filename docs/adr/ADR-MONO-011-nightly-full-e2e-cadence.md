# ADR-MONO-011 — Nightly + Push-to-main Cadence for `@Tag("full")` E2E Suites

**Status:** ACCEPTED
**Date:** 2026-05-13
**History:** PROPOSED 2026-05-13 (TASK-MONO-079 spec PR #434, squash commit `886c14a1`) → ACCEPTED 2026-05-13 (TASK-MONO-079 impl PR — 4 backend full job 신설). Phase 3 of the e2e 3단계 전략 (skip / light / full). Phase 2 (ADR-MONO-010, PROPOSED+ACCEPTED 2026-05-13, PR #427/#428/#430/#432) authorised the partition (`@Tag("smoke")` PR-time / `@Tag("full")` nightly) and wired the 4 service-side `:e2eFullTest` Gradle tasks. Phase 3 wires the CI side: the cron + push-to-main triggers, the 4 backend jobs, and the failure-handling policy.
**Decision driver:** Phase 2 landed `:e2eFullTest` Gradle tasks on 4 services (fan + scm + wms + gap) but no CI cadence currently invokes them — `@Tag("full")` tests are dead code from main's perspective until Phase 3. With 10 full units (fan 1 + scm 4 + wms 2 + gap 3) carrying high-cost regression coverage (refresh-reuse detection, DLQ routing, rate-limit burst, container-pause resilience, cross-project event consumption, supplier circuit-breaker state transitions, multi-step PO lifecycle), the gap costs a full day of main-HEAD drift per missed regression. Phase 2 explicitly deferred the cadence side to a separate ADR (ADR-MONO-010 § 6.1) precisely so the workflow-structure decisions (file layout, trigger windows, failure escalation, per-job pre-build pattern) could be debated independently from the partition rubric.
**Supersedes:** none — first ADR that takes a position on backend e2e nightly cadence. The prior `nightly-e2e.yml` workflow (TASK-MONO-014 / -045) covers only the ecommerce `frontend-e2e-fullstack` Playwright suite; the backend e2e fullbuckets are net-new.
**Related:** [ADR-MONO-010](ADR-MONO-010-e2e-tag-taxonomy.md) (Phase 2 partition — direct parent), [tasks/done/TASK-MONO-076](../../tasks/done/TASK-MONO-076-e2e-tag-taxonomy-impl.md) (Phase 2 impl 1차 fan+scm), [tasks/done/TASK-MONO-077](../../tasks/done/TASK-MONO-077-e2e-tag-impl-wms.md) (Phase 2 impl 2차 wms method-level), [tasks/done/TASK-MONO-078](../../tasks/done/TASK-MONO-078-e2e-tag-impl-gap.md) (Phase 2 impl 3차 gap), [tasks/done/TASK-MONO-014](../../tasks/done/TASK-MONO-014-frontend-e2e-fullstack-nightly.md) (ecommerce frontend-e2e-fullstack nightly reference), [tasks/done/TASK-MONO-045](../../tasks/done/TASK-MONO-045-ci-path-filter-and-nightly-e2e.md) (path-filter baseline + nightly split reference), [`.github/workflows/nightly-e2e.yml`](../../.github/workflows/nightly-e2e.yml) (existing workflow, to be extended), [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) §§ `e2e-tests` / `fan-platform-e2e` / `scm-platform-e2e` / `gap-integration-tests` (PR-time smoke jobs — reference for boot-jars artifact + image-build pattern).

---

## 1. Context

### 1.1 Phase 2 end-state (input to Phase 3)

After PR #428/#430/#432 the 4 service e2e modules each carry the `:e2eSmokeTest` (PR-time) + `:e2eFullTest` (nightly target) + `:e2eTest` (umbrella) task family. The final partition is recorded in ADR-MONO-010 § 1.2; in summary:

| Service | smoke | full | full units (the targets of Phase 3) |
|---|---|---|---|
| fan-platform | 2 | 1 | `VisibilityTierE2ETest` |
| scm-platform | 2 | 4 | `AsnReceiveE2ETest`, `SupplierAckWebhookE2ETest`, `SupplierCircuitBreakerE2ETest`, `WmsInventoryAdjustedConsumedE2ETest` |
| wms-platform | 4 | 2 | `GatewayMasterE2ETest.RateLimit.burstFrom800RequestsTripsRateLimiter`, `GatewayMasterE2ETest.MasterOutage.pausedMasterReturns503AndRecoversAfterUnpause` (method-level) |
| gap | 2 | 3 | `RefreshReuseDetectionE2ETest`, `DlqHandlingE2ETest`, `CrossServiceBulkLockE2ETest` |
| **total** | **10** | **10** | wms 의 method-level granularity 로 별도 카운트 |

10 full units, all running in zero CI cadence today.

### 1.2 The 4 backend e2e job patterns (PR-time today, Phase 3 input)

The `.github/workflows/ci.yml` PR-time jobs that Phase 3 mirrors:

| Job | Boot-jar pre-build | Image build | Gradle task (post-Phase 2) | PR-time timeout |
|---|---|---|---|---|
| `e2e-tests` (wms gateway-master) | upstream `boot-jars` job (`wms-boot-jars` artifact) | Docker CLI build inside the e2e job | `:projects:wms-platform:apps:gateway-service:e2eSmokeTest` | 20 min |
| `fan-platform-e2e` | upstream `fan-platform-boot-jars` job | Docker CLI build inside the e2e job | `:projects:fan-platform:tests:e2e:e2eSmokeTest` | 10 min |
| `scm-platform-e2e` | upstream `scm-platform-boot-jars` job | Docker CLI build inside the e2e job | `:projects:scm-platform:tests:e2e:e2eSmokeTest` | 12 min |
| `gap-integration-tests` | none — gap uses `ComposeFixture` (docker-compose builds services internally) | docker-compose inside the test JVM | default `test` (carries `excludeTags 'full'` per ADR-MONO-010 D5 step 4) | n/a (different lifecycle) |

The first three share a uniform pattern (Spring Boot bootJar artifact → Docker CLI image build → Testcontainers Network e2eSmokeTest). gap is structurally different — its `tests/e2e/build.gradle` boots services via `ComposeFixture` (docker-compose in `@BeforeAll`), so the nightly job uses `:projects:global-account-platform:tests:e2e:e2eFullTest` directly (no boot-jars artifact, no Docker CLI image build step — `docker-compose` handles both).

### 1.3 The existing `nightly-e2e.yml` (reference)

The `nightly-e2e.yml` workflow (TASK-MONO-014, hardened by TASK-MONO-045) currently carries:

- `ecommerce-boot-jars-nightly` — packages 11 ecommerce bootJars.
- `frontend-e2e-fullstack` — docker-compose stack of 12 backends + Postgres ×10 + Kafka + Redis + Elasticsearch + MinIO + web-store Next.js, then Playwright (4 specs: golden-flow, cart-management, auth-redirect, wishlist).

Trigger: `schedule: '0 18 * * *'` (UTC 18:00 = KST 03:00 — low-traffic window), `push: branches: [main]`, `workflow_dispatch`. Failure handling: nightly badge red, no auto-Slack/issue creation. Repo-gated to `kanggle/monorepo-lab` (extracted standalone repos skip).

This is the canonical pattern the backend full jobs SHOULD mirror.

### 1.4 Why a separate ADR (vs in-line in ADR-MONO-010)

ADR-MONO-010 § 3.6 (Phase 2+3 bundle alternative) rejected combining the two: the partition rubric and the cadence are orthogonal axes, and bundling produces an oversized ADR that mixes (a) per-test classification decisions with (b) cron-cadence + failure-handling policy. This ADR isolates the cadence axis.

---

## 2. Decision

### D1 — Workflow location

Extend the existing `.github/workflows/nightly-e2e.yml` rather than create a new workflow file.

**Rationale**: a single nightly workflow file is easier to manage (one badge, one schedule, one set of repo-gates, one workflow_dispatch surface). The TASK-MONO-014 precedent + `nightly-e2e.yml` naming already convey "everything that runs nightly lives here". A separate `nightly-e2e-backend.yml` would require duplicating concurrency / permissions / env / repo-gate boilerplate per file with no offsetting benefit.

**Alternatives rejected** (§ 3): separate `nightly-e2e-backend.yml` (file proliferation), per-service workflow files (4× duplication).

### D2 — Trigger policy

Reuse the exact `on:` block from the existing `nightly-e2e.yml`:

```yaml
on:
  schedule:
    - cron: '0 18 * * *'  # UTC 18:00 = KST 03:00, low-traffic window
  push:
    branches: [main]
  workflow_dispatch:
```

**Rationale**: KST 03:00 is the proven low-traffic window (TASK-MONO-014 chose it precisely to avoid colliding with weekday dev hours). `push: branches: [main]` provides the post-merge regression catch — every merge to main triggers a full-suite run so a regression introduced by a smoke-misclassified test is caught within minutes of merge, not on the next cron tick. `workflow_dispatch` enables manual one-shot runs (`gh workflow run nightly-e2e.yml --ref main`) for ad-hoc verification.

**No PR-level trigger.** Full suites do NOT run on PRs — Phase 2 already runs smoke on every PR; full is intentionally deferred to post-merge / nightly cadence. This is the entire point of the partition.

### D3 — Per-job structure (4 backend full jobs)

Four new jobs MUST be added to `nightly-e2e.yml`, named:

- `wms-platform-e2e-full` — runs `:projects:wms-platform:apps:gateway-service:e2eFullTest`.
- `fan-platform-e2e-full` — runs `:projects:fan-platform:tests:e2e:e2eFullTest`.
- `scm-platform-e2e-full` — runs `:projects:scm-platform:tests:e2e:e2eFullTest`.
- `gap-e2e-full` — runs `:projects:global-account-platform:tests:e2e:e2eFullTest`.

Each job MUST mirror the corresponding PR-time smoke job's structure verbatim, with the following diffs:

1. **Task target**: `:e2eSmokeTest` → `:e2eFullTest`.
2. **Timeout**: shorter PR-time budgets (10/12/20 min) → 60 min (matching the old pre-partition wms `e2e-tests` budget; gives headroom for cold-start + long-running edge cases like `SupplierCircuitBreaker` + container-pause).
3. **Repo gate**: `if: github.repository == 'kanggle/monorepo-lab'` (extracted standalone repos skip — same precedent as `frontend-e2e-fullstack`).
4. **No path-filter `if:`**: the cron + push-to-main triggers are unconditional; path-filter only applies on PR events.
5. **Boot-jars dependency** (wms / fan / scm):
   - **Option A**: reuse the existing PR-time boot-jars jobs (`boot-jars`, `fan-platform-boot-jars`, `scm-platform-boot-jars`) defined in `ci.yml`. These do NOT run on the `nightly-e2e.yml` workflow trigger (different workflow), so the boot-jars MUST be built in-job for the full e2e jobs.
   - **Option B (chosen)**: each full e2e job builds its own bootJars directly via `./gradlew :…:bootJar` as a step before the image build. Simpler than artifact upload/download between workflows; nightly is not latency-sensitive.
6. **Image build**: same Docker CLI BuildKit pattern as PR-time (Docker 28 `ImageFromDockerfile` hang workaround per `incidents/2026-05-05-ci-regression.md`).
7. **gap special case**: no image build step (docker-compose handles it inside the test JVM via `ComposeFixture`). The job just runs `./gradlew :…:e2eFullTest`.
8. **Concurrency**: shared `concurrency` group with `frontend-e2e-fullstack` (one group per workflow): `nightly-e2e-${{ github.ref }}` (already defined at workflow level — TASK-MONO-014). `cancel-in-progress: true` means a new push to main during a nightly run cancels the in-flight run; acceptable per TASK-MONO-014 precedent.

The resulting workflow has **5 jobs total**: the existing `ecommerce-boot-jars-nightly` + `frontend-e2e-fullstack` + the 4 new backend full jobs. No `needs:` dependencies between the backend full jobs and the ecommerce ones — they run in parallel.

### D4 — Failure handling

Mirror the existing `frontend-e2e-fullstack` policy exactly (TASK-MONO-014 § Failure handling):

1. **Nightly badge red** — visible on the `nightly-e2e.yml` action badge in the GitHub Actions UI. The repo README and `docs/project-overview.md` SHOULD link the nightly badge prominently.
2. **No auto-issue / Slack** — out of scope for v1 (matches TASK-MONO-014's explicit deferral). v2 (when the project has a real on-call rotation) would add `actions/github-script` to file an issue on failure or `slackapi/slack-github-action` to post to a channel.
3. **PR blocking**: full-suite failure does NOT block PRs to main. The PR-time smoke job is the PR gate; nightly full is the safety net + early-warning system for misclassified smoke / unmaintained edge tests.
4. **Investigation cadence**: a failed nightly is triaged within 1 business day. The triage decides one of: (a) genuine regression → file a fix task; (b) flaky test → file a stabilisation task (with `@Disabled` if 3+ consecutive flakes); (c) infrastructure flake (Docker / runner) → re-run and observe one cycle.

### D5 — gap-specific structure

gap's docker-compose pattern requires explicit treatment in the workflow:

- **No boot-jars build step**: `ComposeFixture` boots services via `docker-compose build` in `@BeforeAll`. The job's only Gradle invocation is `:projects:global-account-platform:tests:e2e:e2eFullTest`.
- **Compose-project teardown**: the job MUST clean up `gap-e2e_*` docker-compose projects on completion. ComposeFixture's JVM shutdown hook handles this in normal exit; on timeout / forced cancel, the runner is destroyed anyway. No extra `docker compose down` step needed.
- **gap-e2e_default network**: created lazily by ComposeFixture; no pre-create step required (unlike the `traefik-net` workaround for the ecommerce frontend-e2e-fullstack job).
- **`-Pobservability=on`**: the workflow does NOT pass this flag. Observability stack is opt-in per `ADR-MONO-007 § D3`; nightly explicitly omitted.
- **Smoke job uncovered**: gap currently has no PR-time smoke job (gap-integration-tests calls the default `test` which carries `excludeTags 'full'` per ADR-MONO-010 D5 step 4). A dedicated `gap-platform-e2e-smoke` PR-time job is recorded as outstanding (ADR-MONO-010 § 6.2). Phase 3 does NOT add it — the gap nightly full job assumes PR-time coverage via the default `test` invocation; if gaps emerge they are filed separately.

---

## 3. Alternatives Considered

### 3.1 Separate `nightly-e2e-backend.yml`

Move the 4 backend full jobs to a new workflow file, leaving `nightly-e2e.yml` for ecommerce only. Rejected: file proliferation without offsetting benefit. One workflow per cadence (nightly) is cleaner than one workflow per concern (frontend vs backend). The concurrency group + repo-gate boilerplate would duplicate.

### 3.2 Reusable workflow (`.github/workflows/e2e-reusable.yml`)

Define a single reusable workflow that PR-time and nightly both call, parameterised by `inputs.includeTag` (smoke or full). Rejected for Phase 3: a workflow-level abstraction at this point trades current readability for future flexibility we do not yet need. A 4-service × 2-tag matrix (8 invocations) is still small enough that explicit per-job declarations win on debuggability. Reusable workflow is a Phase 4 candidate if/when full-job count grows beyond ~6 (e.g. adding finance / erp / mes services per `project_portfolio_7axis_architecture`).

### 3.3 GitHub Actions matrix strategy

Single `strategy.matrix.service: [fan, scm, wms, gap]` job that runs `:e2eFullTest` for each. Rejected for Phase 3: matrix requires uniform job shape, but gap's docker-compose pattern + wms's sourceSets-split bootJar list + per-service environment variable injection (`Dwms.e2e.*Image`, `Dfan.e2e.*Image`, `Dscm.e2e.*Image`) diverge enough that matrix produces complex conditional steps. Phase 3 v1 = simple explicit jobs; matrix consolidation is a Phase 4 candidate (after observation of which divergence dimensions are stable enough to consolidate).

### 3.4 Skip push-to-main, cron only

Only run nightly on `cron: '0 18 * * *'` — no `push: branches: [main]` trigger. Rejected: full-suite regressions introduced by a smoke-misclassified test (or by a refactor that changed full-test behavior) would lurk for up to 24 hours before the next cron tick. The cost of `push to main` is one extra full-suite run per merge (typically 0–3 per day), well under the runner-minute budget. Catching regressions within minutes of merge is worth the cost.

### 3.5 Skip nightly cron, push-to-main only

Trigger only on push to main, no nightly cron. Rejected: the cron catches drift in long-running flake patterns (intermittent failures that surface only over time). Without the cron, a test that passes on every individual merge but fails 1-in-5 times due to a race or resource leak goes undetected until someone happens to re-run a workflow. The cron is the long-tail flake gate.

### 3.6 Phase 3 + Phase 4 consolidation in this ADR

Bundle the matrix-consolidation / reusable-workflow / observability-on-nightly / cost-budget-telemetry decisions into this ADR. Rejected: each is a non-trivial trade-off and Phase 3's job is the *cadence* + the *first wiring*. Once Phase 3 is operating for ~30 days, observed pain points (which divergence dimensions cause friction, how often nightly catches a real regression vs flakes) inform Phase 4 better than upfront speculation.

### 3.7 Reuse PR-time boot-jars artifact via cross-workflow download

Phase 3 jobs download the `wms-boot-jars` / `fan-platform-boot-jars` / `scm-platform-boot-jars` artifacts produced by `ci.yml` PR-time jobs on the same commit. Rejected: GitHub Actions does NOT support cross-workflow artifact reuse without resorting to `actions/github-script` or workflow chaining. The cost of rebuilding boot jars in-job is small (~30–60s per service per nightly run, amortised across the runner pool), and the simplicity gain (no cross-workflow plumbing) is significant.

---

## 4. Consequences

### 4.1 At PROPOSED merge (this PR)

- ADR file lands. `docs/adr/INDEX.md` gains the row. `tasks/INDEX.md` gains TASK-MONO-079 in `## ready`.
- No workflow YAML changes. No service / Gradle / spec changes.
- CI: markdown-only spec PR — Phase 1 markdown-skip rule → all e2e + build-and-test jobs SKIP, `changes` job PASS. **TASK-MONO-074 markdown-skip deferred 자연 검증 5번째 사례** 적립.

### 4.2 At ACCEPTED transition

ACCEPTED requires (a) user-explicit intent ("transition ADR-MONO-011 to ACCEPTED"), and (b) the readiness of TASK-MONO-079 to begin execution. ACCEPTED moves the ADR into operational status and unblocks TASK-MONO-079 from ready → in-progress.

### 4.3 Post-impl (TASK-MONO-079 done)

- **`nightly-e2e.yml` carries 6 jobs**: `ecommerce-boot-jars-nightly` + `frontend-e2e-fullstack` + `wms-platform-e2e-full` + `fan-platform-e2e-full` + `scm-platform-e2e-full` + `gap-e2e-full`.
- **First nightly run** (≤ 24h after merge or via `workflow_dispatch`) executes 10 full units across the 4 services + the existing frontend-e2e-fullstack. Total nightly runner-minutes ~ 60–120 min depending on cold-start variance. Within GitHub's 2,000 free min/month budget.
- **Push-to-main coverage**: every merge to main triggers all 6 jobs (in parallel where possible — backend jobs do not depend on ecommerce-boot-jars-nightly). Regression catch latency = minutes, not hours.
- **`@Tag("full")` tests stop being dead code from main's perspective** — the entire purpose of Phase 2 + 3 combined.
- **Nightly badge stability**: expected ~95% green initially; SupplierCircuitBreaker + 800-request-burst + container-pause are flake-prone categories. Phase 4 may add per-test retry-on-flake (out of scope here).

### 4.4 Cost analysis

GitHub Actions Linux runner free-tier budget = 2,000 min/month for public repos. Estimated nightly burn:

| Job | Cold wall-clock estimate | Daily runs | Monthly minutes |
|---|---|---|---|
| `ecommerce-boot-jars-nightly` | 15 min | 1 cron + ~3 push/day = 4 | 1,800 |
| `frontend-e2e-fullstack` | 30 min | 4 | 3,600 |
| `wms-platform-e2e-full` | 5–10 min | 4 | 600 |
| `fan-platform-e2e-full` | 5–10 min | 4 | 600 |
| `scm-platform-e2e-full` | 10–15 min | 4 | 900 |
| `gap-e2e-full` | 15–25 min | 4 | 1,500 |
| **total** | | | **~9,000 min/month** |

**Already over the 2,000 free-tier budget**, but the existing ecommerce nightly is already burning ~5,400 min/month and that is observed and accepted. The marginal cost of Phase 3 is ~3,600 min/month. Public-repo overage cost = $0 (GitHub Actions is free for public repos at any min budget). Private-repo migration would re-evaluate.

### 4.5 D4 OVERRIDE applicability

This ADR introduces a new CI workflow extension that, per ADR-MONO-003a § D1.3, is a *cross-cutting test policy* — adjacent to ADR-MONO-010 (Phase 2) which carried the same OVERRIDE classification. It does NOT touch `libs/`, `rules/`, `.claude/`, `tasks/templates/`, or `CLAUDE.md`. The PROPOSED PR is structurally identical to PR #427 (TASK-MONO-076 spec) — pure spec authoring under `docs/adr/`. D4 OVERRIDE applies narrowly to the PROPOSED authoring; the ACCEPTED-transition impl PR (TASK-MONO-079) reuses the same OVERRIDE for the `.github/workflows/nightly-e2e.yml` extension.

---

## 5. Verification

The PROPOSED PR (this ADR + INDEX + TASK-MONO-079 ready) lands with no functional code change. The ACCEPTED-transition impl PR (TASK-MONO-079) MUST verify:

1. **4 new backend full jobs in `nightly-e2e.yml`** with the names + Gradle targets per D3.
2. **Cron + push + workflow_dispatch triggers** unchanged from the existing block.
3. **First nightly run** (post-merge or `workflow_dispatch`) executes all 10 full units across 4 services. Test report row count = 10 (fan 1 + scm 4 + wms 2 + gap 3). Workflow badge = green within 24h.
4. **PR-time impact = zero**: smoke jobs in `ci.yml` unchanged; new full jobs do NOT trigger on PR events. A markdown-only or contracts-only PR after this merge MUST still observe the Phase 1 skip-or-force behavior.
5. **First failure handled per D4**: nightly badge red → triage within 1 business day. (Not blocking; observed in the natural cadence.)
6. **Repo gate honored**: extracted standalone repos do NOT run the new jobs (the `if: github.repository == 'kanggle/monorepo-lab'` check).

---

## 6. Outstanding follow-ups

Recorded for future filing; not gating ACCEPTED:

1. **Auto-issue / Slack on nightly failure** — v2 of D4. Filed when on-call rotation exists.
2. **Reusable workflow consolidation** (Phase 4) — when full-job count grows ≥ 6 services (e.g. finance / erp bootstrapped).
3. **Matrix strategy** for the 3 Testcontainers-Network services (fan / scm / wms) — gap stays explicit. Phase 4 candidate.
4. **Cost-budget telemetry** — capture actual cold-start wall-clock per job over the first 30 days, compare to § 4.4 estimates. Inform Phase 4 sizing.
5. **PR-time gap smoke job** (ADR-MONO-010 § 6.2 outstanding inherited) — separate file from this ADR; same docker-compose plumbing question.
6. **Observability stack on nightly** (`ADR-MONO-007a` deferred + ADR-MONO-007 § D3 explicit CI exclusion) — Phase 4 candidate if a nightly flake forensics gap surfaces.
7. **Retry-on-flake**: GitHub Actions `nick-fields/retry@v3` for the 3 flake-prone full categories (SupplierCircuitBreaker / RateLimit burst / MasterOutage container-pause). Apply only after observing first 2 weeks of nightly stability.

---

## Status transition history

| Row | Date | From | To | PR | Note |
|---|---|---|---|---|---|
| 1 | 2026-05-13 | — | PROPOSED | #434 (squash `886c14a1`) | Initial publication (TASK-MONO-079 spec PR). markdown-only → Phase 1 markdown-skip 의도된 동작 15 SKIP + 1 changes PASS 자기 검증 통과 (TASK-MONO-074 deferred 자연 검증 5번째 사례). |
| 2 | 2026-05-13 | PROPOSED | ACCEPTED | TBD (this PR — TASK-MONO-079 impl) | User-explicit intent 2026-05-13 ("어" → ACCEPTED 전환 + impl 진행). `.github/workflows/nightly-e2e.yml` 에 4 backend full job (`wms-platform-e2e-full` / `fan-platform-e2e-full` / `scm-platform-e2e-full` / `gap-e2e-full`) 신설 + header comment 갱신. 10 full unit (fan 1 + scm 4 + wms 2 + gap 3) 의 첫 CI cadence 시작점. |
