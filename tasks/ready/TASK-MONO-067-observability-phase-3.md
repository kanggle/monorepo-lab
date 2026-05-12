# Task ID

TASK-MONO-067

# Title

Observability stack coverage expansion + Rancher Desktop validation + CI footprint regression test (OpenAI Harness gap #3 Phase 3 — final)

# Status

ready

# Owner

monorepo

# Task Tags

- code
- infra
- ci
- harness

---

# Required Sections

- Goal
- Scope (In / Out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Close OpenAI Harness gap #3 entirely by completing the **Phase 3** deliverables enumerated in ADR-MONO-007 § 2.5 D5:

1. **Coverage expansion** — extend the `-Pobservability=on` opt-in wiring from `gateway-service:e2eTest` (Phase 2, gateway-master live-pair) to the three remaining e2e suites: `fan-platform/tests/e2e` (live-trio gateway + community + artist), `scm-platform/tests/e2e` (cross-service), `global-account-platform/tests/e2e`. Identical pattern: named docker network handoff via `Network.builder().createNetworkCmdModifier(...)` in each base class + `doFirst` / `doLast` lifecycle hooks in each build.gradle.
2. **Rancher Desktop compatibility validation** — document that the Phase 1 stack works against Rancher Desktop dockerd v29.1.3 (validated 2026-05-12 via TASK-MONO-065 footprint measurement: 11.1 s cold start, 26.88 MiB resident, all health checks green) and add a memory cross-reference in `infra/observability/README.md` to `project_testcontainers_docker_desktop_blocker.md` so future operators understand which docker engine is the reference baseline.
3. **CI footprint regression test** — new GitHub Actions workflow step (or extension of an existing job) that brings the stack up on a Linux CI runner, snapshots `docker stats` after 10 s of idle, and fails the build if total resident memory exceeds **100 MiB** (Linux baseline 62.84 MiB measured on first CI run × ~1.6 safety margin — Linux Vector resident ~3× larger than Windows/Rancher per § Implementation Notes below) or wall-clock cold start exceeds **30 s** (the ADR § 2.1 D1 hard cap).

This is the fourth and final phase of the gap #3 closure series. Phase 0 (TASK-MONO-064 ADR), Phase 1 (TASK-MONO-065 stack scaffolding), and Phase 2 (TASK-MONO-066 skill + Gradle e2eTest integration) are all in `done/`. On merge of this task's closure chore, memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" gap #3 row flips to **DELIVERED** (full closure annotation), matching the existing gap A and gap #2 entries.

---

# Scope

## In Scope

### A. e2e coverage expansion (3 services)

Each of the three e2e modules below receives a copy of the gateway-service Phase 2 patch — same code shape, project-name-substituted:

| Project | Build file | Base class |
|---|---|---|
| fan-platform | `projects/fan-platform/tests/e2e/build.gradle` | `projects/fan-platform/tests/e2e/src/test/java/com/example/fanplatform/e2e/testsupport/FanPlatformE2ETestBase.java` |
| scm-platform | `projects/scm-platform/tests/e2e/build.gradle` | `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/testsupport/ScmPlatformE2ETestBase.java` |
| global-account-platform | `projects/global-account-platform/tests/e2e/build.gradle` | `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/E2EBase.java` |

Per project:

- **`build.gradle` `tasks.register('e2eTest', Test) { ... }` extension** — `if (project.hasProperty('observability') && project.property('observability') == 'on') { ... }` block adds: worktreeHash derivation (`git rev-parse --show-toplevel | sha256sum | head -c 8`), `doFirst` (docker network create + `scripts/observability/up.sh --network <name>`), `systemProperty 'wms.e2e.observabilityNetwork', <name>` (Note: the system property name is preserved as `wms.e2e.observabilityNetwork` across all four projects so the same `E2EBase.java` constant resolves uniformly — the property is a *protocol* between the build script and the base class, not a service-naming concern), `doLast` (`scripts/observability/down.sh` + docker network rm). Both `||  true` on the docker network commands for idempotence.
- **Base class `@BeforeAll` patch** — same pattern as gateway-service's `E2EBase.java`: when `System.getProperty("wms.e2e.observabilityNetwork")` is set, use `Network.builder().createNetworkCmdModifier(cmd -> cmd.withName(<name>)).build()`; when absent, fall back to `Network.newNetwork()` (zero behaviour change on the default path).

The network name uses a per-project suffix to avoid cross-project collisions when two e2e suites run in parallel:

- gateway-service (Phase 2, already merged): `wms-observability-e2e-${worktreeHash}`
- fan-platform: `fan-observability-e2e-${worktreeHash}`
- scm-platform: `scm-observability-e2e-${worktreeHash}`
- global-account-platform: `gap-observability-e2e-${worktreeHash}`

The Phase 2 `gateway-service/build.gradle` network name pattern is preserved verbatim (no rename); the three new entries pick distinct prefixes per project name.

### B. Rancher Desktop compatibility validation

- **`infra/observability/README.md`** — append a new `## Docker engine compatibility` section noting:
  - Validated against Rancher Desktop dockerd v29.1.3 on Windows 11 (Phase 1 measurement environment, 2026-05-12).
  - Cross-reference to memory `project_testcontainers_docker_desktop_blocker.md` — observability stack uses docker-compose only (not Testcontainers' docker-java client), so the documented Rancher cold-start `MalformedChunkCodingException` regression does NOT affect this stack.
  - Linux CI runner (GitHub Actions `ubuntu-latest` with the bundled docker engine) is the second validated environment via the new footprint regression test (item C below).
  - Docker Desktop is **not yet validated** — Phase 3 ships without it; users on Docker Desktop should validate before merging Phase-3-dependent work, and any incompatibility surfaces as a follow-up.

### C. CI footprint regression test

- New job in `.github/workflows/ci.yml` titled `Observability stack footprint regression` (or extension of an existing job — design choice during implementation). Triggers on `infra/observability/**` or `scripts/observability/**` path changes per the existing `dorny/paths-filter` rules (TASK-MONO-045 + 058 precedent).
- Job body:
  1. Checkout repo.
  2. `docker network create wms-platform-bootrun_default || true` (dummy — Phase 1 footprint measurement protocol).
  3. `time bash scripts/observability/up.sh --network wms-platform-bootrun_default 2>&1 | tee up.log`
  4. Parse wall-clock from `time` output → fail if > 30 s.
  5. `docker stats --no-stream --format "{{.MemUsage}}" $(docker compose -f infra/observability/docker-compose.yml -p $PROJECT ps -q) | awk '{sum += $1} END { print sum }'` (parsing MiB / KiB / GiB units) → fail if > 100 MiB (Linux baseline 62.84 MiB × ~1.6 safety margin; first CI run discovered Linux Vector resident is ~3× larger than Windows/Rancher 14 MiB due to musl/glibc native build + cgroup accounting — cap pegged against the larger baseline to avoid false positives across environments).
  6. `bash scripts/observability/down.sh` (cleanup, run even on prior step failure via `if: always()` step).
  7. Upload `up.log` + `docker stats` raw output as artifact for diff inspection.
- Job is **not blocking** for the default PR pipeline (similar to the Integration job that runs only when `wms` paths change); the job runs only when `infra/observability/` or `scripts/observability/` paths are touched. Footprint creep on observability changes alone is the regression signal worth catching.

### D. README closure annotation

- **`infra/observability/README.md`** — update the `## Limitations of Phase 1` section: items previously marked "Phase 2" or "Phase 3" deferred are now updated to either DELIVERED (with cross-ref to MONO-066 / MONO-067) or to remain explicitly out of scope (idle teardown daemon, trace queries).

### E. memory annotation (closure note via the chore PR)

The closure chore PR (`ready` → `done`) updates memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" gap #3 row + § "우선순위 액션 후보" item #3:

- Before (Phase 2 closure annotation): "Phase 0/1/2 DELIVERED — Phase 3 outstanding".
- After (Phase 3 closure annotation): "DELIVERED 2026-05-13 — full closure. ADR-MONO-007 ACCEPTED + MONO-064/065/066/067 all DELIVERED. Trace layer remains deferred to ADR-MONO-007a (separate; not part of gap #3 surface)."

## Out of Scope

- **Idle teardown daemon** (5-min idle teardown promised by ADR § 2.3 D3). Not enumerated in ADR § 2.5 D5 Phase 3 deliverables; if a future operator finds the manual teardown burden too high, file a follow-up. Phase 3 closes gap #3 without it.
- **Docker Desktop validation**. Phase 1 measurement protocol ran against Rancher Desktop; Docker Desktop has not been validated. Documented as a known gap in the new README section; not a Phase 3 deliverable.
- **Trace layer / ADR-MONO-007a**. Separate ADR, not part of gap #3 surface.
- **gap #4 (Chrome DevTools MCP)**. Separate gap, untriggered.
- **Service-side telemetry changes**. Same as Phase 1 / 2.
- **Cross-project parallel e2e runs in CI** with concurrent observability stacks. Network names are distinct per project (per-project prefix), but CI does not currently run multiple project e2e suites concurrently within a single workflow run. If concurrent execution becomes a CI goal later, Phase 3 has already laid the network-naming groundwork.

---

# Acceptance Criteria

- [ ] fan-platform e2e build.gradle accepts `-Pobservability=on` and wires the named-network lifecycle (network name prefix `fan-observability-e2e-`).
- [ ] FanPlatformE2ETestBase.java reads `wms.e2e.observabilityNetwork` system property; falls back to anonymous `Network.newNetwork()` when absent.
- [ ] scm-platform e2e build.gradle + ScmPlatformE2ETestBase.java analogous (prefix `scm-observability-e2e-`).
- [ ] global-account-platform e2e build.gradle + E2EBase.java analogous (prefix `gap-observability-e2e-`).
- [ ] `infra/observability/README.md` includes a `## Docker engine compatibility` section with Rancher v29.1.3 baseline + memory cross-reference + CI Linux runner mention.
- [ ] `infra/observability/README.md` `## Limitations of Phase 1` section updated to reflect Phase 2 + Phase 3 deliveries (or renamed to `## Limitations`).
- [ ] `.github/workflows/ci.yml` includes a footprint regression job that fails on >40 MiB total resident or >30 s cold start. Triggered only on `infra/observability/**` or `scripts/observability/**` path changes.
- [ ] Default CI e2eTest paths (gateway-master + fan-platform + scm-platform + GAP) remain byte-identical when `-Pobservability=on` is absent — zero regression for the existing 3 E2E + 3 Integration jobs.
- [ ] Production code = 0 modifications. Touches: `infra/**`, `scripts/**`, `.github/workflows/ci.yml`, e2eTest source sets + build.gradle of the three e2e modules.
- [ ] CI green (15/15 SUCCESS or 16/16 if the new footprint regression job runs).

---

# Related Specs

- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` § 2.5 D5 Phase 3 deliverables (coverage + Rancher + CI footprint regression)
- `tasks/done/TASK-MONO-064-adr-mono-007-observability-stack.md` (ADR publication)
- `tasks/done/TASK-MONO-065-observability-stack-scaffolding.md` § Implementation Notes (Phase 1 footprint baseline 26.88 MiB / 11.1 s)
- `tasks/done/TASK-MONO-066-observability-query-skill.md` (Phase 2 skill + gateway-service Gradle pattern — replicated here per project)
- Memory `project_testcontainers_docker_desktop_blocker.md` (Rancher Desktop cold-start regression context)
- Memory `reference_openai_harness_engineering.md` (gap #3 row, closure annotation target)

# Related Skills

- `.claude/skills/cross-cutting/observability-query/SKILL.md` (Phase 2 skill — this task expands its applicable scope from gateway-master to all 4 e2e suites)

---

# Related Contracts

None — infrastructure + e2eTest build wiring + CI workflow. No HTTP / event contract surface.

---

# Target Service

N/A — monorepo-level infrastructure expansion. Targets:

- `projects/fan-platform/tests/e2e/build.gradle` + `FanPlatformE2ETestBase.java`
- `projects/scm-platform/tests/e2e/build.gradle` + `ScmPlatformE2ETestBase.java`
- `projects/global-account-platform/tests/e2e/build.gradle` + `E2EBase.java`
- `infra/observability/README.md`
- `.github/workflows/ci.yml`

---

# Architecture

The Phase 2 gateway-service pattern (TASK-MONO-066) is the template. Phase 3 replicates it for the remaining three e2e suites with two adaptations:

1. **Per-project network name prefix** — `fan-`, `scm-`, `gap-` instead of `wms-` — prevents collision when two project e2es run in parallel.
2. **System property name preservation** — `wms.e2e.observabilityNetwork` (not renamed per project) — the property is a protocol between build script and base class, and reusing the same name avoids adding a new property per project.

The CI footprint regression test is structurally similar to the existing Integration / E2E Testcontainers jobs — single Linux runner, docker-compose-driven, artifact upload on failure. The new job's trigger condition is **narrow** (only `infra/observability/**` or `scripts/observability/**` changes) so the default PR pipeline is unaffected.

---

# Implementation Notes

## Why no idle teardown daemon

ADR § 2.3 D3 promises a 5-min idle teardown but does not enumerate it under any specific phase in § 2.5 D5. The interpretation here is that the lifecycle property is "good behaviour the stack should have eventually" rather than "must ship in Phase N". Phase 3 closes the gap with the three deliverables ADR § 2.5 D5 explicitly names; the idle teardown can be filed as a follow-up if manual teardown burden becomes a recurring complaint.

## Footprint regression test thresholds

Per-environment baselines:

| Environment | Measured | Source |
|---|---|---|
| Windows 11 + Rancher Desktop dockerd v29.1.3 | 26.88 MiB total / 11.1 s cold start | TASK-MONO-065 Phase 1 |
| Linux GitHub Actions `ubuntu-latest` | **62.84 MiB total** (Vector 44 + VictoriaLogs 5.9 + VictoriaMetrics 12.9) | first CI run of this task's `observability-footprint` job, 2026-05-13 |

Vector alone consumes ~3× more memory on Linux than on Windows/Rancher (44 MiB vs 14 MiB) — driven by musl/glibc native build differences + cgroup memory accounting at the Linux container layer. VictoriaLogs and VictoriaMetrics show ~2× divergence each. The ADR § 2.1 D1 "200 MB resident" target was set conservatively in anticipation of this kind of cross-environment spread.

Phase 3 CI caps (set against the larger baseline so neither environment produces false positives):

- Memory: **100 MiB** (Linux baseline 62.84 MiB × ~1.6 safety margin)
- Cold start: **30 s** (the ADR § 2.1 D1 hard cap, unchanged — applies to both environments since cold start is dominated by image pull + container init, not language runtime memory)

If a future image upgrade pushes Linux total memory to 70-95 MiB, the CI test still passes — at 101 MiB+, it fails, and the operator decides whether to bump the cap (intentional growth) or pin to the prior image (unintended regression). Windows/Rancher operators see ample headroom (their measurements stay well under 30 MiB), which is acceptable — the cap exists for detection, not for forcing Linux down to the lower environment's baseline.

## CI job trigger gating

Path-filter rule: `infra/observability/**` OR `scripts/observability/**` changed in the PR diff. This matches the existing `dorny/paths-filter` config (TASK-MONO-045) — the rule should be a small addition to the existing `paths-ignore` / `paths` setup, not a new top-level workflow file.

## D4 churn-clock interaction

ADR-MONO-003a § D1.3 (Harness gap series) applies. Touches:

- 3 × `projects/<name>/tests/e2e/build.gradle` (project-internal, no relaxation)
- 3 × project-internal base class file
- `infra/observability/README.md` (additive section)
- `.github/workflows/ci.yml` (additive job)
- memory annotation (closure chore PR)

The `.github/workflows/ci.yml` touch is shared infrastructure — note that the existing CI workflow is shared by all five projects, so an additive job that fires only on `infra/observability/` path changes does not affect any project's default pipeline. Same shape as MONO-045 path-filter PR (also workflow-level additive).

## Commit shape

Single commit / single PR pattern. Conventional commit prefix:

```
feat(infra+ci)+task(mono-067): observability coverage expansion + CI footprint regression (OpenAI Harness gap #3 Phase 3 — final)
```

The closure chore (`ready` → `done`) lands in a separate small PR after this one merges and includes the memory annotation flipping gap #3 row to full DELIVERED.

---

# Edge Cases

- **Concurrent fan + scm e2e runs in the same worktree.** Network names differ by prefix, so concurrent runs produce distinct networks. Two same-project parallel runs collide on the network name and fail fast — same as Phase 2's same-project collision.
- **Footprint regression job hits flake on CI runner cold start.** Linux GitHub Actions runners are generally consistent, but a particularly slow image pull could push cold start past 30 s. If observed flake rate > 5 %, the job is rerun-eligible (already the default for GitHub Actions) and the threshold can be widened to 45 s. Phase 3 ships with 30 s as the conservative cap.
- **Stack started by one e2e crashes mid-test of another.** Each test's `doFirst` creates its own named network; `doLast` tears it down. A crashed test's `doLast` still runs (Gradle `Test` task lifecycle), so the named network is removed even on test failure.

---

# Failure Scenarios

- **Build.gradle edit breaks an e2e module's default path** (test failure when `-Pobservability=on` is absent). Mitigation: the `if (project.hasProperty('observability') && project.property('observability') == 'on')` guard makes the lifecycle block opt-in; when the flag is absent, no doFirst/doLast hook runs, no system property is set, and the base class falls back to `Network.newNetwork()` byte-identical to today. CI verifies this by running e2e jobs without the flag.
- **CI footprint regression job false positive on first run.** Phase 1 measured 26.88 MiB on Rancher Desktop; CI Linux runner may differ slightly. If first runs show 30+ MiB, that becomes the new baseline and the 40 MiB cap still holds. If runs show < 20 MiB, the cap is conservative — acceptable.
- **README section drift after Phase 3.** The README's "Phase 1 / 2 / 3 limitations" structure becomes obsolete once all three phases ship. Phase 3 renames the section to `## Known limitations` or `## Future work` to reflect that the phasing structure is no longer a forward-looking roadmap but historical context.

---

# Test Requirements

- **Manual verification (one project)**: run `./gradlew :projects:fan-platform:tests:e2e:e2eTest -Pobservability=on` against a worktree with the wms bootRun stack NOT running. Expect: fan-platform e2e suite passes, observability stack spins up briefly under network `fan-observability-e2e-${hash}`, queries via `.claude/skills/cross-cutting/observability-query/scripts/query-logs.sh '{service="community-service"}'` return data, stack tears down on completion. Repeat for scm-platform and GAP if local docker resources allow.
- **CI verification (default path)**: existing e2e jobs run without `-Pobservability=on`. Confirms zero regression for the 3 E2E + 3 Integration jobs.
- **CI verification (new footprint job)**: trigger by including an `infra/observability/` or `scripts/observability/` path change in the same PR. Expect job runs, passes thresholds, uploads artifact.
- **Memory annotation verification**: closure chore PR description quotes the gap #3 row update.

CI must remain at minimum 15/15 SUCCESS (existing) + optionally 16/16 when the new footprint job is in the matched-paths slice.

---

# Definition of Done

- [ ] All Acceptance Criteria pass.
- [ ] CI green at 15/15 (default path) or 16/16 (with footprint job).
- [ ] Closure chore PR (`ready` → `done`) opens after this PR merges.
- [ ] Memory `reference_openai_harness_engineering.md` gap #3 row flipped to full DELIVERED on closure chore merge.
- [ ] ADR-MONO-007 § 6 outstanding follow-up #3 (Phase 3) → close.

---

# Provenance

ADR-MONO-007 § 2.5 D5 Phase 3 gate met by TASK-MONO-066 (Phase 2) merge — PR #404 commit `7e77b083` (2026-05-13).

Memory `reference_openai_harness_engineering.md` § 우선순위 액션 후보 item #3 — gap #3, Phase 3 of the four-phase closure plan. On this task's closure chore merge, the gap series log entry is the third and final DELIVERED — gap A (2026-05-12) + gap #2 (2026-05-12) + gap #3 (this task closes 2026-05-13).

D4 OVERRIDE applies per ADR-MONO-003a § D1.3 — Harness gap series scope, user-acknowledged 2026-05-12.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (3 e2e mechanical replication of Phase 2 pattern + CI workflow extension + README polishing — judgment-light; direct authoring chosen for single-PR scope and Phase 2 context continuity).
