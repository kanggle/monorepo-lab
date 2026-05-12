# ADR-MONO-010 — E2E Test Tag Taxonomy (`smoke` / `full`) and Gradle / CI Job Split

**Status:** ACCEPTED
**Date:** 2026-05-13
**History:** PROPOSED 2026-05-13 (TASK-MONO-076 spec PR #427, squash commit `75872289`) → ACCEPTED 2026-05-13 (TASK-MONO-076 impl PR 1차 — fan + scm bundle, D5 step 1+2). Phase 2 of the e2e 3단계 전략 (skip / light / full). Phase 1 (TASK-MONO-074 `*.md`-only PR skip + `contracts/**` force-full) landed 2026-05-13 (PR #422) and was stabilised the same day by TASK-MONO-075 (PR #425, paths-filter `@v3` negation quirk fix). Phase 3 (full e2e nightly 이전, 4 backend services + extension of ecommerce frontend-e2e-fullstack 패턴) is a separate downstream ADR/task — it depends on the `smoke` / `full` partition this ADR defines.
**Decision driver:** Phase 1 cut the *false-positive* cost (markdown-only PRs no longer trigger e2e) and added the *cross-service safety net* (any contract change forces all 4 e2e jobs). The remaining cost is in the e2e jobs themselves: the wms `e2e-tests` job alone has a 60-minute timeout; `fan-platform-e2e` and `scm-platform-e2e` carry 20-minute timeouts. Even after Phase 1, a PR that legitimately touches one project's code still pays the full 4-suite price for *every* push. The 15 e2e test classes/methods across the 4 services are not all of equal value: a happy-path cross-service contract check ("the service can issue a JWT, the gateway can route, the downstream returns 200") delivers most of the regression signal at a fraction of the cost; edge cases (DLQ routing, circuit-breaker state transitions, refresh-reuse detection, multi-step lifecycles) deliver the long-tail safety. Splitting these two cost classes is the single biggest e2e-pipeline win still on the table.
**Supersedes:** none — first ADR that takes a position on e2e suite *partitioning* (prior ADRs and tasks treat e2e as a single all-or-nothing bucket).
**Related:** [tasks/done/TASK-MONO-074](../../tasks/done/TASK-MONO-074-ci-e2e-skip-and-force-full-flags.md) (Phase 1 markdown-skip + contracts force-full), [tasks/done/TASK-MONO-075](../../tasks/done/TASK-MONO-075-fix-mono-074-markdown-skip-paths-filter-quirk.md) (Phase 1 fix — paths-filter `@v3` negation quirk), [tasks/done/TASK-MONO-045](../../tasks/done/TASK-MONO-045-ci-path-filter-and-nightly-e2e.md) (baseline path-filter + nightly-e2e split — Phase 3 prior art), [`platform/testing-strategy.md`](../../platform/testing-strategy.md) (existing Test Pyramid — to be amended at ACCEPTED), [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) §§ `e2e-tests` / `fan-platform-e2e` / `scm-platform-e2e`, [`.github/workflows/nightly-e2e.yml`](../../.github/workflows/nightly-e2e.yml) (Phase 3 reference — currently only ecommerce frontend-e2e-fullstack lives here).

---

## 1. Context

### 1.1 The 4 backend e2e suites — current state

The monorepo runs 4 backend e2e suites, all Testcontainers-backed, plus 1 frontend e2e suite already split (smoke vs fullstack — TASK-MONO-014 / -045 prior art). The backend suites are:

| Service | Module path | Boot pattern | Class-level `@Tag` today | Tests | PR-time CI job (`.github/workflows/ci.yml`) | Trigger |
|---|---|---|---|---|---|---|
| **wms gateway-service** | `projects/wms-platform/apps/gateway-service/src/e2eTest/` (sourceSets-split) | `ImageFromDockerfile` + pre-built image via `-Dwms.e2e.*Image=...` | none (no `@Tag` on test class or base `E2EBase.java`) | 1 class, 5 `@Nested` scenarios | `e2e-tests` (timeout 60 min) | wms / libs / workflows / contracts |
| **gap** | `projects/global-account-platform/tests/e2e/` (`tests/` style) | docker-compose direct (`ComposeFixture`) | none (only `@Tag("integration")` exists in tests/integration; e2e tests carry no tag) | 5 classes | (no PR-time job; only docker-compose driven, runs in `gap-integration-tests` which is integration-tagged, not e2e) | (n/a) |
| **fan-platform** | `projects/fan-platform/tests/e2e/` | Testcontainers Network, pre-built image | `@Tag("e2e")` on `FanPlatformE2ETestBase` | 3 classes | `fan-platform-e2e` (timeout 20 min) | fan / libs / workflows / contracts |
| **scm-platform** | `projects/scm-platform/tests/e2e/` | Testcontainers Network, pre-built image | `@Tag("e2e")` on `ScmPlatformE2ETestBase` | 6 classes | `scm-platform-e2e` (timeout 20 min) | scm / libs / workflows / contracts |

Total: **15 e2e test classes / methods** across 4 services (and 5 nested scenarios in the wms case, inside 1 class).

### 1.2 The 15 test units — initial smoke / full classification

The audit below is the input to D5 (per-service rollout). Each row is one test class; the wms row is one class with five `@Nested` scenarios that need method-level classification. The "Why" column quotes the cost / value trade-off; final partition is recorded in D1.

| # | Service | Test (class or nested) | Cost shape | Initial bucket | Why |
|---|---|---|---|---|---|
| 1 | wms gateway-service | `GatewayMasterE2ETest$HappyPath.getWarehousesListWithValidJwt` (Nested 1) | Single GET through gateway → master | **smoke** | Cross-service happy path; catches gateway↔master routing or JWKS regression instantly. |
| 2 | wms | `GatewayMasterE2ETest$HappyPath.postWarehouseWithValidJwt` (Nested 1, second method) | Single POST + outbox→Kafka assertion | **smoke** | Validates write path; outbox→Kafka is the single most-broken-in-history integration. |
| 3 | wms | `GatewayMasterE2ETest$UnauthenticatedRejection` (Nested 2, 401 without JWT) | Single request | **smoke** | Cheap; gates the entire auth surface. |
| 4 | wms | `GatewayMasterE2ETest$RateLimit` (Nested 3, 800-request burst) | Burst of 800 sequential HTTP requests | **full** | Stress-shaped; not regression-shaped. Catches Redis rate-limiter regression rarely — nightly cadence sufficient. |
| 5 | wms | `GatewayMasterE2ETest$MasterDownPropagation` (Nested 4, pause master container) | Container manipulation mid-test | **full** | Container-pause is slow + flaky-prone; failure mode is "503 with envelope" which is verifiable with a non-e2e cheaper test. |
| 6 | gap | `GoldenPathE2ETest.golden_path_full_flow` | 9-step JWKS → enrollment → TOTP → refresh → logout | **smoke** | The flagship cross-service flow; if this breaks, security-service / admin-service are de-facto down. |
| 7 | gap | `TenantProvisioningE2ETest` | tenant provisioning → JWT → gateway X-Tenant-Id + cross-tenant reject | **smoke** | Multi-tenant invariant; the single most-broken-in-history GAP regression class. |
| 8 | gap | `RefreshReuseDetectionE2ETest` | refresh→rotate→reuse→chain-invalidate | **full** | Security edge case; valuable but slow + not a happy-path. |
| 9 | gap | `DlqHandlingE2ETest` | Produce malformed JSON → assert DLQ + observability endpoints | **full** | Operations-shaped; verifies DLQ routing + actuator surface. |
| 10 | gap | `CrossServiceBulkLockE2ETest` | bulk-lock admin → account_db + security_db propagation | **full** | Cross-service projection assertion with awaitility poll; valuable but slow. |
| 11 | fan-platform | `ArtistAndPostFlowE2ETest` | 5-step artist→follow→post→feed→Kafka | **smoke** | The flagship fan-platform v1 happy path. |
| 12 | fan-platform | `MultiTenantIsolationE2ETest` | 3 sub-cases (cross-tenant reject / unauth / valid) | **smoke** | Tenant gate at the gateway boundary — same invariant class as #7. |
| 13 | fan-platform | `VisibilityTierE2ETest` | Visibility tier (PUBLIC / MEMBERS_ONLY / PREMIUM) | **full** | Membership checker bean wiring + tier policy; deep but narrow. |
| 14 | scm-platform | `ProcurementHappyPathE2ETest` | PO draft → submit → supplier mock 200 → outbox → Kafka | **smoke** | The flagship scm-platform v1 happy path. |
| 15 | scm-platform | `CrossTenantIsolationE2ETest` | tenant gate at resource server (cheap) | **smoke** | Same invariant class as #7 + #12. |
| 16 | scm-platform | `AsnReceiveE2ETest` | 5-step DRAFT→SUBMITTED→ACK→CONFIRMED→RECEIVED lifecycle | **full** | Long flow with multiple intermediate state assertions. |
| 17 | scm-platform | `SupplierAckWebhookE2ETest` | webhook → ack → state transition + Kafka | **full** | Webhook path; edge-case-shaped. |
| 18 | scm-platform | `SupplierCircuitBreakerE2ETest` | CB state transitions end-to-end | **full** | Resilience4j stateful behavior — high coverage value but slow + flaky-prone. |
| 19 | scm-platform | `WmsInventoryAdjustedConsumedE2ETest` | Cross-project event consumed | **full** | Cross-project bridge — most valuable as a *nightly* contract gate, not a per-PR check. |

Counts:
- **smoke** = 8 units (wms 3 methods + gap 2 classes + fan 2 classes + scm 2 classes) — fast cross-service happy-path coverage.
- **full** = 11 units (wms 2 methods + gap 3 classes + fan 1 class + scm 5 classes) — edge / resilience / long-flow / cross-project.

The wms case is the only one that needs **method-level** classification (one class, mixed buckets). All other services classify at **class level**.

### 1.3 Why a tag taxonomy, not a directory split

Three alternatives were considered (recorded under § 3):

1. **Directory split** — move `full` tests under `src/e2eTestFull/` etc.: rejected because shared base classes (`FanPlatformE2ETestBase`, `ScmPlatformE2ETestBase`, wms `E2EBase`) and test fixtures (`E2ETestFixtures`, `JwksMockServer`, `KafkaTestConsumer`) are *intentionally* shared across both buckets. Splitting directories would either duplicate the base class or require a third "shared support" source set — more complex than a tag.
2. **Suite-runner annotation** (`@Suite` / `@SelectClasses`) — rejected because JUnit 5's `includeTags` / `excludeTags` in Gradle is already the canonical mechanism, and Spring's existing `@Tag` precedent (`integration`, `e2e`) is in use across the monorepo.
3. **Manual `if(System.getenv(...))` skips** — rejected; pushes selection into the test runtime where it's invisible to Gradle and CI.

The tag taxonomy keeps:
- A single source set per service (the existing `e2eTest` / `tests/e2e/`).
- Shared base classes / fixtures unchanged.
- The CI selection mechanism (`includeTags` in Gradle `Test` task) is a single-line filter, not a structural change.
- Backward compatibility: any test left un-tagged keeps the existing `@Tag("e2e")` umbrella semantics and is treated as `full` by default (conservative; never silently demoted to smoke).

---

## 2. Decision

### D1 — `@Tag` taxonomy

Every e2e test class (or method, where method-level granularity is required) MUST carry exactly one of the following two specialised tags **in addition to** the existing `@Tag("e2e")` umbrella tag:

| Specialised tag | Semantics | Cost budget (per-test, on a CI Linux runner) | Frequency |
|---|---|---|---|
| `@Tag("smoke")` | Happy-path cross-service contract assertion. Passes when the boot succeeds + JWT issuance + routing + first-hop persistence + (where present) outbox → Kafka emission. | ≤ 30 s per test method on a warmed runner (cold-start of containers excluded — those amortise across the suite). | **Every PR**, on every push to a feature branch + main. |
| `@Tag("full")` | Edge case / resilience / long flow / cross-project consumer / security exhaustion / DLQ / circuit breaker / multi-step state lifecycle. | No upper bound — these tests intentionally exercise slow / stateful / failure-injection paths. | **Nightly cron** + push to main. (Phase 3 wiring; this ADR only authorises the tagging.) |

**Granularity rules**:

1. **Class-level by default.** Apply `@Tag("smoke")` or `@Tag("full")` on the test class, alongside the existing `@Tag("e2e")` from the base class.
2. **Method-level where the class is mixed.** When a single class contains scenarios that span both buckets (the wms `GatewayMasterE2ETest` is the only known case), apply method-level `@Tag` on each `@Test` / `@Nested` class, and OMIT the class-level `smoke` / `full` (the umbrella `@Tag("e2e")` from the base class remains).
3. **Un-tagged = `full`.** Any e2e test that carries `@Tag("e2e")` but no `smoke` / `full` is treated as `full`. This is the conservative migration default: a new test introduced before the author has decided gets pulled into the nightly suite, never silently into the per-PR fast lane. The lint at D4 step 4 surfaces such tests for explicit classification.
4. **`@Tag("e2e")` remains the umbrella.** It is NOT replaced. The base classes (`FanPlatformE2ETestBase`, `ScmPlatformE2ETestBase`) continue to carry it; concrete subclasses add `smoke` or `full` on top.

**Classification criteria** (the rubric D5 rollout PRs MUST apply):

A test is `smoke` IFF all of the following hold:
- (S1) It exercises the **happy path** of a primary cross-service flow (the kind a developer would write first if asked "does the service work?").
- (S2) It uses **deterministic** inputs — no burst counts > ~20, no container pauses, no `Thread.sleep` > 5 s, no awaitility timeout > 30 s.
- (S3) Its failure mode is **regression-shaped**, not stress-shaped — i.e. failure means "the wiring broke", not "the system is overloaded".
- (S4) It can complete (excluding cold-start) within ~30 s on a warmed runner.

Otherwise the test is `full`. Specifically, any of the following pulls a test into `full`:
- (F1) Burst / rate-limit / load assertion (e.g. wms rate-limit 429 with 800 requests).
- (F2) Container-pause or other lifecycle-injection assertion (e.g. wms 503-when-master-down).
- (F3) Multi-step state lifecycle (≥ 3 state transitions, e.g. scm DRAFT→SUBMITTED→ACK→CONFIRMED→RECEIVED).
- (F4) Cross-project event consumption (e.g. scm consuming `wms.inventory.*`).
- (F5) DLQ / error-routing / refresh-reuse detection / circuit-breaker state transitions.
- (F6) Membership / authorization / visibility-tier edge cases that require bean-wiring acrobatics.

### D2 — Gradle task naming + sourceSets

Two new Gradle `Test` tasks MUST be registered in each of the 4 e2e modules:

```groovy
tasks.register('e2eSmokeTest', Test) {
    description = '@Tag("smoke") subset of the e2e suite — runs every PR.'
    group = 'verification'
    useJUnitPlatform {
        includeTags 'smoke'
    }
    // sourceSets identical to existing `e2eTest`. CI image-name properties, observability=on wrapper,
    // bootJar dependsOn list, timeout, and testLogging block — all copied verbatim from `e2eTest`.
}

tasks.register('e2eFullTest', Test) {
    description = '@Tag("full") subset of the e2e suite — runs nightly + push to main.'
    group = 'verification'
    useJUnitPlatform {
        includeTags 'full'
        // No exclude; a class can in principle be both smoke and full if a future case demands it,
        // but D1 forbids that. The lint at D4 step 4 surfaces such drift.
    }
    // Identical inheritance as e2eSmokeTest above.
}
```

**SourceSets policy**:

- **No new source set.** Both new tasks reuse the existing `e2eTest` source set (wms gateway-service) or the existing single `test` source set (the 3 standalone `tests/e2e/` modules).
- **Shared support classes / fixtures unchanged.** `E2ETestFixtures`, `JwksMockServer`, `KafkaTestConsumer`, base classes — all live in the same source set as both `smoke` and `full` tests.
- **Existing `e2eTest` task retained for back-compat.** It continues to run the entire `@Tag("e2e")` umbrella (i.e. smoke + full). After Phase 2 lands and CI is migrated, the existing task becomes the "run everything locally" convenience target. No removal in this ADR.

The 4 build scripts to amend:
- `projects/wms-platform/apps/gateway-service/build.gradle`
- `projects/fan-platform/tests/e2e/build.gradle`
- `projects/scm-platform/tests/e2e/build.gradle`
- `projects/global-account-platform/tests/e2e/build.gradle` (special case — see below)

**gap special case**: the gap `tests/e2e/build.gradle` currently maps `test` to the e2e suite (no separate `e2eTest` task). Phase 2 introduces `e2eSmokeTest` / `e2eFullTest` as new tasks; the default `test` task remains intact (the e2e runner the existing CI uses) and gets `useJUnitPlatform { excludeTags 'full' }` so the default `test` becomes smoke-equivalent for CI; the full set runs via the new `e2eFullTest` task only on nightly. This preserves backward compatibility with any existing local-dev habit.

### D3 — Workflow job split policy

Phase 2 reaches up to but does NOT cross the workflow boundary. The build scripts in D2 are amended; the workflow YAML is amended in the *same* impl PR but only minimally:

**PR-time CI jobs** (`ci.yml`) — change `./gradlew :…:e2eTest` to `./gradlew :…:e2eSmokeTest`:
- `e2e-tests` (wms): `./gradlew :projects:wms-platform:apps:gateway-service:e2eSmokeTest`; timeout-minutes reduced 60 → 20.
- `fan-platform-e2e`: `./gradlew :projects:fan-platform:tests:e2e:e2eSmokeTest`; timeout-minutes 20 → 10.
- `scm-platform-e2e`: `./gradlew :projects:scm-platform:tests:e2e:e2eSmokeTest`; timeout-minutes 20 → 10.
- (gap currently has no PR-time `e2eTest` job; status quo preserved.)

**Nightly CI jobs** (`nightly-e2e.yml`) — add 4 new jobs (one per e2e module) running the corresponding `e2eFullTest` task:
- `wms-platform-e2e-full` (runs `:projects:wms-platform:apps:gateway-service:e2eFullTest`).
- `gap-e2e-full` (runs `:projects:global-account-platform:tests:e2e:e2eFullTest` — this is the only e2e mention of gap in CI, since gap has no PR-time job).
- `fan-platform-e2e-full` (runs `:projects:fan-platform:tests:e2e:e2eFullTest`).
- `scm-platform-e2e-full` (runs `:projects:scm-platform:tests:e2e:e2eFullTest`).

Each `nightly-e2e.yml` job MUST mirror the corresponding `ci.yml` job's image-prebuild pattern, JDK setup, boot-jar artifact upload/download (or its replacement with `./gradlew :…:bootJar` directly inside the job — Phase 3 picks the canonical pattern), and `if: github.repository == 'kanggle/monorepo-lab'` repo-gate.

**Failure handling at Phase 2 ACCEPTED**: smoke breakage blocks PRs immediately (per-PR cadence). full breakage shows red on the nightly badge but does NOT block PRs to main (same policy as the existing `frontend-e2e-fullstack` job). Phase 3 may later promote `full` to push-to-main gating, but that's a separate ADR.

### D4 — `platform/testing-strategy.md` amendment scope

The impl PR (D5) MUST amend `platform/testing-strategy.md` with the following inserts, in this order:

1. **Test Pyramid block** (top): the existing `[E2E / Contract]` row is split visually into `[E2E (full) / Contract]` over `[E2E (smoke)]`, with a one-line note: "Smoke runs every PR; full runs nightly + push to main."
2. **Test Types section**: insert a new subsection "E2E Smoke vs Full" between the current "Integration Tests" and "Event Consumer / Producer Tests" subsections. The subsection MUST include:
   - The S1–S4 + F1–F6 rubric verbatim (or paraphrased to fit document tone) from § D1.
   - The class-level vs method-level granularity rule (D1.1, D1.2).
   - The un-tagged = full default (D1.3).
   - A pointer to ADR-MONO-010 (this file) as the canonical decision record.
3. **Naming Conventions table**: append two rows: `*SmokeE2ETest` (recommended class-name suffix when the class is exclusively smoke) and `*FullE2ETest` (recommended suffix when exclusively full). Suffixes are RECOMMENDED, not REQUIRED — class-level `@Tag` remains the authoritative classifier. Method-level tagging (the wms case) does NOT use these suffixes.
4. **Rules section**: append one rule: "Every test class extending an e2e base class (`*E2ETestBase` or equivalent) MUST carry either `@Tag(\"smoke\")` or `@Tag(\"full\")` directly on the class, OR carry method-level `@Tag(\"smoke\")` / `@Tag(\"full\")` on each `@Test` / `@Nested` method. Tests that carry only `@Tag(\"e2e\")` are treated as `full` (conservative default) and SHOULD be classified explicitly in a follow-up PR." A `validate-rules` / `audit-memory` pass MAY enforce this lint; out-of-scope for the impl PR — Phase 3 candidate.

### D5 — 4-service application order

The impl PR(s) MUST roll out in this order (lowest risk → highest risk, matching the granularity escalation):

| # | Service | Reason for order | Granularity | Tags to add |
|---|---|---|---|---|
| 1 | **fan-platform** | Smallest test count (3); base class already carries `@Tag("e2e")`; clean class-level partition (2 smoke + 1 full); no method-level needed. | class-level | 2 × `@Tag("smoke")` + 1 × `@Tag("full")` |
| 2 | **scm-platform** | Same pattern as fan (base class `@Tag("e2e")`, class-level granularity). 6 tests → 1 smoke + 5 full. Validates the partition rubric scales. | class-level | 1 × `@Tag("smoke")` + 5 × `@Tag("full")` |
| 3 | **wms gateway-service** | First method-level case (1 class, 5 nested → 3 smoke + 2 full). Tests the granularity-rule D1.2. | method-level (5 `@Test` methods inside `@Nested` blocks) | 3 × `@Tag("smoke")` + 2 × `@Tag("full")` |
| 4 | **gap** | No `@Tag("e2e")` precedent → two-step migration: (a) add `@Tag("e2e")` at the base or per-class, then (b) add `@Tag("smoke")` / `@Tag("full")`. Also the only service with no PR-time job — only the nightly side wires up. Highest churn relative to test count, hence last. | class-level | 5 tests → 2 smoke + 3 full; AND `@Tag("e2e")` precedent introduced |

**Bundling policy**: a single impl PR MAY bundle steps 1 + 2 (fan + scm — same pattern, low churn). Steps 3 (wms method-level) and 4 (gap bootstrap) SHOULD ship as separate PRs to keep each granularity escalation reviewable independently. Three impl PRs total is the recommended shape; one bundled PR is allowed if the diff stays under ~400 LOC.

**Optional lint follow-up**: a `validate-rules`-style scanner that grep-asserts every `*E2ETest.java` carries `smoke|full` at class or method level. Out of scope for the PROPOSED → ACCEPTED transition; recorded as § 6 outstanding follow-up.

---

## 3. Alternatives Considered

### 3.1 Directory split (`src/e2eTest/` vs `src/e2eTestFull/`)

Move smoke and full tests into two distinct source sets, each with its own Gradle `Test` task. Rejected: shared base classes + fixtures (`*E2ETestBase`, `JwksMockServer`, `E2ETestFixtures`, `KafkaTestConsumer`) would either need to be duplicated or hoisted to a third source set. The structural complexity is disproportionate to the gain — tag-based filtering achieves the same partition at the cost of one annotation per class.

### 3.2 JUnit Platform Suite-runner annotation

Use `@Suite` + `@SelectClasses` / `@IncludePackages` to define `SmokeE2ESuite.java` and `FullE2ESuite.java` runners. Rejected: a) `@Suite` runners require JUnit Platform Suite as an additional dependency on each module's classpath (cost), b) the existing `useJUnitPlatform { includeTags '...' }` mechanism in Gradle is already in use across the monorepo (`@Tag("e2e")` filtering precedent — `scm-platform/tests/e2e/build.gradle:79`), and matches JUnit 5 idiom directly.

### 3.3 Manual environment-variable skips

Each test reads `System.getenv("E2E_SCOPE")` and `Assumptions.assumeTrue(...)` based on value. Rejected: pushes selection into test runtime (invisible to Gradle / CI), couples test code to env-var name (rot-prone), and "skipped" tests appear in CI reports as noise.

### 3.4 Single shared `@Tag("smoke")` only, no `@Tag("full")` (orphan-tag scheme)

Tag only smoke; everything else (un-tagged within `@Tag("e2e")`) is implicitly full. Considered. Rejected (narrowly): the lint at D4 step 4 needs a positive signal to verify intent, otherwise a forgotten `@Tag` is silently full. Requiring both tags is a small cost (one annotation per test) and gives the lint a concrete missing-tag failure mode.

### 3.5 Method-level only, no class-level

Force every classification at method level. Rejected: 14 of the 15 test units are exclusively one bucket at class level. Method-level on those would be repetitive annotation noise. The hybrid rule (D1.1 + D1.2) keeps the common case clean and falls back to method-level only where mixed-bucket classes exist (wms only).

### 3.6 Phase 2 + Phase 3 in one ADR

Bundle the workflow job split (Phase 3) into this ADR. Rejected: Phase 3 requires deciding nightly-cron cadence + push-to-main gating policy + 4 new workflow jobs' boot-jar pre-build pattern (artifact upload vs in-job build) — each is a non-trivial trade-off that benefits from its own decision record. This ADR's job is to authorise the *partitioning*; Phase 3's job is to authorise the *cadence*.

---

## 4. Consequences

### 4.1 At PROPOSED merge (this PR)

- ADR file lands. `docs/adr/INDEX.md` gains the row. `tasks/INDEX.md` gains TASK-MONO-076 in `## ready`.
- No service code changes. No `build.gradle` changes. No workflow changes. No `platform/testing-strategy.md` changes.
- CI: path-filter `libs` (rules/docs/.claude) flag = true → standard build & test pass; e2e jobs SKIP per the Phase 1 markdown-skip rule (this PR's diff is markdown-only). Self-CI is regression-only.

### 4.2 At ACCEPTED transition

ACCEPTED requires: (a) user-explicit intent in a follow-up session ("transition ADR-MONO-010 to ACCEPTED"), and (b) the readiness of TASK-MONO-076 to begin execution (Phase 2 impl). ACCEPTED moves the ADR into operational status and unblocks TASK-MONO-076 from ready → in-progress.

### 4.3 Post-impl (TASK-MONO-076 done)

- **PR-time wall-clock cost drops** from `e2e-tests` (60 min budget, typically 12-18 min actual) to `e2eSmokeTest` only (≤ 20 min budget, projected 4-8 min actual). Comparable drops for `fan-platform-e2e` (20 → 10 min) and `scm-platform-e2e` (20 → 10 min).
- **Full coverage preserved** via nightly + push-to-main runs (Phase 3 wires the full-cadence side; Phase 2 only authorises the partitioning that Phase 3 builds on).
- **Regression risk**: smoke tests that should have been full (developer misclassification under the S1-S4 rubric) might miss a regression. The push-to-main full-cadence catches it within 1 day. Mitigation: a smoke breakage on main triggers a full-suite re-run in the next nightly cycle; full breakage on main shows red on the nightly badge.
- **Naming convention**: the `*SmokeE2ETest` / `*FullE2ETest` suffix is RECOMMENDED but not enforced. Existing class names (e.g. `GoldenPathE2ETest`) keep their semantic names; the `@Tag` is the authoritative classifier.

### 4.4 D4 OVERRIDE applicability

This ADR introduces a new e2e test classification taxonomy that, per § D4 of ADR-MONO-001 / § D1 of ADR-MONO-003a, is a *cross-cutting test policy* on the same axis as `platform/testing-strategy.md`. It does NOT touch `libs/`, `rules/`, `.claude/`, `tasks/templates/`, or `CLAUDE.md`. It DOES amend `platform/testing-strategy.md` (D4) at the ACCEPTED-transition impl PR. The PROPOSED PR itself is structurally identical to PR #414 (TASK-MONO-071, ADR-MONO-008 PROPOSED) — pure spec authoring under `docs/adr/`. D4 OVERRIDE applies narrowly to the PROPOSED authoring; the ACCEPTED-transition impl PR's amendment to `platform/testing-strategy.md` reuses the same OVERRIDE under ADR-MONO-003a § D1.3 (meta-policy / cross-cutting test policy).

---

## 5. Verification

The PROPOSED PR (this ADR + INDEX + TASK-MONO-076 ready) lands with no functional code change. The ACCEPTED-transition impl PR (TASK-MONO-076) MUST verify:

1. **Smoke / full partition matches § 1.2 audit.** 8 smoke + 11 full = 19 unique classifications (one wms class has 5 method-level decisions; counts as 5). Per-service breakdown:
   - fan-platform: 2 smoke + 1 full.
   - scm-platform: 1 smoke + 5 full.
   - wms gateway-service: 3 smoke + 2 full (method-level).
   - gap: 2 smoke + 3 full (after `@Tag("e2e")` precedent introduction).
2. **`./gradlew :…:e2eSmokeTest` runs only `@Tag("smoke")` tests** on each of the 4 modules; `./gradlew :…:e2eFullTest` runs only `@Tag("full")`. Verified by Gradle test report row count.
3. **`./gradlew :…:e2eTest` (legacy task) still runs both** (smoke + full) — back-compat preserved.
4. **CI smoke jobs (`e2e-tests`, `fan-platform-e2e`, `scm-platform-e2e`) green** with the new task name. Self-CI on the impl PR provides the regression gate.
5. **`platform/testing-strategy.md` carries the 4 inserts** (D4 list). Diff review verifies.
6. **No service code touched.** `git diff --stat projects/*/apps/` shows zero rows in the impl PR. Test code (under `tests/e2e/` or `src/e2eTest/`) annotated only.

---

## 6. Outstanding follow-ups

These are recorded for future filing, not gating ACCEPTED:

1. **Phase 3 — nightly cadence + push-to-main gating** for `e2eFullTest`. Separate ADR. Depends on Phase 2 ACCEPTED + TASK-MONO-076 DONE.
2. **gap PR-time smoke job** — currently gap has no PR-time e2e job. After Phase 2 introduces `e2eSmokeTest` on gap, a `gap-platform-e2e-smoke` job mirroring `fan-platform-e2e` should be filed as a separate task. Out of scope for both Phase 2 and Phase 3 (gap docker-compose pattern requires different boot-jar plumbing than the Testcontainers-Network pattern).
3. **Lint enforcement** — a `validate-rules` extension that grep-asserts every `*E2ETest.java` carries `smoke|full` at class or method level. Out of scope for Phase 2 impl PR; Phase 3 candidate.
4. **Naming suffix migration** — opt-in renames of existing classes to `*SmokeE2ETest` / `*FullE2ETest`. Recommended but not enforced. Out of scope; may surface as a small cleanup task after the impl PR lands.
5. **Cost-budget telemetry** — capture actual wall-clock cost per task on the first 3 nightly runs after Phase 3, compare to the ≤ 30 s smoke / no-upper-bound full budget. Out of scope for both Phase 2 and Phase 3 ADRs; an `audit-memory`-side follow-up.

---

## Status transition history

| Row | Date | From | To | PR | Note |
|---|---|---|---|---|---|
| 1 | 2026-05-13 | — | PROPOSED | #427 (squash `75872289`) | Initial publication (TASK-MONO-076 spec PR). markdown-only → Phase 1 markdown-skip 의도된 동작 16/16 (15 SKIP + 1 changes PASS) 자기 검증 통과. |
| 2 | 2026-05-13 | PROPOSED | ACCEPTED | TBD (this PR — TASK-MONO-076 impl 1차 fan+scm bundle) | User-explicit intent 2026-05-13 ("다음 분기 시작" → ACCEPTED + impl 1차). D5 step 1+2 (fan class-level 2 smoke + 1 full / scm class-level 1 smoke + 5 full) + D2 fan/scm build.gradle + D3 fan-platform-e2e/scm-platform-e2e job target/timeout + D4 testing-strategy.md 4 insert. wms (method-level, D5 step 3) + gap (`@Tag("e2e")` precedent, D5 step 4) = follow-up impl PR. |
