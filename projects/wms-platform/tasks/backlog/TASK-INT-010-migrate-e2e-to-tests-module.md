# Task ID

TASK-INT-010

# Title

Migrate wms e2e from gateway-service sourceSet to standard projects/wms-platform/tests/e2e module

# Status

backlog

# Owner

integration

# Task Tags

- test
- refactor
- code

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

# Goal

wms is the sole layout exception among the five projects' backend e2e suites.
fan / scm / iam / ecommerce all place cross-service e2e in a standalone
`projects/<platform>/tests/e2e/` Gradle module; wms instead embeds it in
`projects/wms-platform/apps/gateway-service/src/e2eTest/` via a sourceSet
(documented at `gateway-service/build.gradle:122-126` and ADR-MONO-010 §1.1).

This task migrates wms onto the standard `tests/e2e/` module so the five
projects share one layout. It is intended as a **structure-only** change: no
test scenario, tag, assertion, or CI semantics change — only the physical home
of the code and the Gradle task's owning module.

**This task is deliberately parked in backlog, not ready.** Per the analysis
recorded when it was filed (2026-07-23), pure-consistency migration of a green,
documented, still-justified exception is not itself sufficient warrant — and the
verification below surfaced a concrete added cost (shared test-support helpers,
see Scope §"Discovered constraint"). Promote to `ready/` ONLY when at least one
ready-gate below holds.

## Ready-gate (promote to ready/ only if one holds)

1. wms e2e grows to boot wms services **beyond** the gateway↔master pair
   (i.e. becomes a project-wide harness), making single-app ownership
   structurally wrong.
2. The portfolio-extraction requirement for a standalone wms repo is dropped
   or changed such that co-location no longer buys anything.
3. A recorded, repeated incident where the sourceSet layout
   (`testClassesDirs`/`classpath` hand-wiring) actually misled a contributor.

Absent a gate, leave parked and keep ADR-MONO-010 §1.1 + build.gradle:122-126
accurate as the standing record of the exception.

---

# Scope

## Verification performed at draft time (2026-07-23)

- **No e2e→gateway production coupling.** The e2eTest sources import only
  `com.wms.gateway.testsupport.*` (JwtTestHelper, JwksMockServer,
  KafkaTestConsumer) — zero imports of gateway-service `main` classes. So the
  scenario + base classes are a genuine move, not a rewrite.
- **Discovered constraint — the test-support helpers are SHARED.** `JwtTestHelper`
  and `JwksMockServer` are consumed not only by the e2e suite but by
  gateway-service's **own** integration tests
  (`src/test/java/com/wms/gateway/integration/GatewayIntegrationBase.java`,
  `GatewayRoutingAuthIntegrationTest.java`). Today the sourceSet layout shares
  them for free via `sourceSets.test.output` on the e2eTest classpath
  (build.gradle:23). A standalone `tests/e2e/` module (scm/fan pattern) does NOT
  depend on gateway-service test output, so that sharing breaks. This forces a
  decision — one of:
    - (a) **Duplicate** JwtTestHelper + JwksMockServer into the e2e module
      (simplest; introduces a divergence-risk second copy — record it).
    - (b) **Extract** the shared helpers to a shared test-fixtures location both
      consumers depend on. NOTE: scm's e2e build.gradle deliberately does NOT
      depend on `libs:java-test-support` (build.gradle:67-71), so the shared
      home cannot naively be that lib without solving the same dependency-mgmt
      conflict; a dedicated test-fixtures module or `java-test-fixtures` plugin
      would be needed.
  `KafkaTestConsumer` appears e2e-only (no `src/test` non-testsupport consumer
  found) and can move outright with its self-test.
  **Re-verify this split before moving** — it drives (a) vs (b).

## In Scope

- New Gradle module `projects/wms-platform/tests/e2e/` (`plugins { id 'java' }`,
  group `com.wms.e2e`), modelled on `projects/scm-platform/tests/e2e/build.gradle`:
  - Own dependency block (Testcontainers/JUnit5/Awaitility/Nimbus/okhttp
    MockWebServer/kafka-clients) — do NOT depend on `libs:java-test-support`
    (same rationale documented in scm build.gradle:67-71).
  - `test` task no-op'd for e2e (`excludeTags 'e2e'`) so `check` stays Docker-free.
  - `baseE2eConfig` closure + `e2eTest` / `e2eSmokeTest` / `e2eFullTest` family,
    identical semantics to today — `includeTags` + descriptions unchanged.
  - `-Pobservability=on` wrapper preserved verbatim, including the SHARED
    `wms.e2e.observabilityNetwork` system-property protocol name (do not rename;
    it is the cross-suite contract per TASK-MONO-066).
  - `dependsOn :...:gateway-service:bootJar` + `:...:master-service:bootJar`.
- Move sources `apps/gateway-service/src/e2eTest/java/com/wms/gateway/e2e/**`
  (`E2EBase`, `GatewayMasterE2ETest`) → `tests/e2e/src/test/java/com/wms/e2e/**`
  (package rename `com.wms.gateway.e2e` → `com.wms.e2e`).
- Resolve the shared-helper constraint per Scope §"Discovered constraint"
  ((a) duplicate or (b) extract) — pick, implement, and record the choice.
  Move `KafkaTestConsumer` (+ `KafkaTestConsumerTest`) into the e2e module.
- Delete the `sourceSets { e2eTest }` block, `e2eTest*` configurations, and the
  e2e task family from `gateway-service/build.gradle`; leave the fast `test`
  task and all production deps untouched. If option (a), leave JwtTestHelper +
  JwksMockServer (+ their self-tests) where they are for the gateway integration
  tests.
- `settings.gradle`: `include ':projects:wms-platform:tests:e2e'`.
- Update CI task references from
  `:projects:wms-platform:apps:gateway-service:{e2eSmokeTest,e2eFullTest}` →
  `:projects:wms-platform:tests:e2e:{...}` in every referencing workflow
  (`.github/workflows/ci.yml`, `nightly-e2e.yml`, and any reusable
  `_platform-e2e.yml` / `_integration.yml` caller). Enumerate exhaustively via
  grep before editing.
- Preserve the CI pre-built-image handoff: the `wms.e2e.masterImage` /
  `wms.e2e.gatewayImage` system properties and their `-x bootJar` skip must keep
  working from the new module path.

## Out of Scope

- Any change to test scenarios, `@Tag` classification, assertions, or the
  smoke/full split.
- Renaming the shared `wms.e2e.observabilityNetwork` protocol property.
- Touching sibling projects' e2e modules.
- The `E2EBase` container-wiring logic itself (only its file location/package
  move; the path-walking logic stays as-is — see AC-5).

---

# Acceptance Criteria

- [ ] AC-1 `./gradlew :projects:wms-platform:tests:e2e:e2eSmokeTest` and
      `:e2eFullTest` both pass on a Linux Docker runner (CI), running the exact
      same test methods as before the move (assert method count + names unchanged
      via the test report).
- [ ] AC-2 `gateway-service/build.gradle` no longer declares an `e2eTest`
      sourceSet, `e2eTest*` configurations, or e2e tasks; `./gradlew
      :...:gateway-service:check` stays GREEN and Docker-free — including
      gateway-service's own integration tests that still consume JwtTestHelper /
      JwksMockServer.
- [ ] AC-3 `./gradlew check` at repo root does NOT pull the e2e module's
      Testcontainers tests into the fast lane (new module's `test` is a no-op).
- [ ] AC-4 Every CI workflow that referenced the old task path now references
      `:projects:wms-platform:tests:e2e:*`; a repo-wide grep for
      `gateway-service:e2e` returns zero hits in `.github/`.
- [ ] AC-5 **Extraction-portability regression** — `E2EBase.locateFile` /
      `locateJar` still resolve `apps/{master,gateway}-service/build/libs/*.jar`
      from the new cwd depth, in BOTH:
      (a) monorepo layout (cwd = `projects/wms-platform/tests/e2e`), and
      (b) extracted-standalone layout (cwd = `<wms-repo>/tests/e2e`).
      Prove (a) by a green CI run; prove (b) by running the portfolio-extraction
      tooling (TEMPLATE.md Discovery→Distribution) into a temp dir and executing
      the e2e task there, OR by a documented dry-run that shows the 8-level
      up-walk still reaches the wms root from the deeper `tests/e2e` cwd.
- [ ] AC-6 The extraction manifest / include-list (whatever selects wms files
      for standalone export) includes `tests/e2e/` and still excludes it from
      the extracted repo's fast `check` — confirm the extracted repo builds.
- [ ] AC-7 `-Pobservability=on` path still creates/attaches the named docker
      network and `E2EBase` still reads `wms.e2e.observabilityNetwork` (one local
      run with the flag, or a documented trace of the unchanged code path).
- [ ] AC-8 ADR-MONO-010 §1.1 updated: wms row changes from
      `src/e2eTest/ (sourceSets-split)` to `tests/e2e/ (tests/ style)`, and the
      "wms is the exception" prose in build.gradle is removed (there is no longer
      an exception to document).
- [ ] AC-9 If option (a) duplication was chosen, the two copies of JwtTestHelper /
      JwksMockServer are called out in the review note as a known divergence risk
      with a one-line rationale (why extraction was not done). If option (b),
      both consumers compile against the single shared source.
- [ ] AC-10 Single atomic PR — module add + source move + gateway build.gradle
      delta + settings.gradle + CI edits + ADR edit land together (no
      transiently-broken main; the CI task path and the module must appear in the
      same commit).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> read `PROJECT.md`, then load `rules/common.md` + `rules/domains/wms.md` +
> `rules/traits/integration-heavy.md`.

- `platform/testing-strategy.md` — E2E tiering, smoke/full taxonomy
- `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` — §1.1 layout table (to be edited)
- `TEMPLATE.md` — Discovery → Distribution (portfolio-extraction tooling, AC-5/6)
- `platform/git-workflow-policy.md` — atomic cross-cutting PR, CI path-filter

# Related Skills

- `.claude/skills/testing/e2e-test/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- None. No API/event contract changes — pure structural relocation.

---

# Target Service / Module

- `projects/wms-platform/tests/e2e/` (new owning module)
- `apps/gateway-service` (sourceSet removed; production untouched; own
  integration tests keep their helper dependency)
- `apps/master-service` (live upstream, bootJar dependency only)

---

# Architecture

No production architectural change. The migration is test-infrastructure
relocation. It does reverse a prior local decision (single-app ownership of the
e2e suite), so the review note must confirm the ready-gate that justified
promotion, and ADR-MONO-010 §1.1 must be updated in the same PR (AC-8).

---

# Edge Cases

- **Shared helper split (the load-bearing one).** JwtTestHelper + JwksMockServer
  are consumed by gateway-service's own integration tests AND the e2e suite.
  They cannot be moved outright without breaking `gateway-service:test`. Resolve
  via Scope §"Discovered constraint" (a) duplicate or (b) extract — re-grep both
  sourceSets to confirm the exact consumer set before deciding.
- Any e2e class importing a gateway-service `main` class would break the
  no-coupling assumption. Draft-time grep found none; re-confirm after the move.
- The 8-level up-walk in `locateFile` (E2EBase:286) is depth-sensitive. From
  `tests/e2e` the runtime cwd is one level deeper than from `apps/gateway-service`
  — confirm 8 levels still reaches the wms root in the extracted layout.

# Failure Scenarios

- CI green in monorepo but extraction breaks (AC-5b unverified) — the whole
  reason wms co-located e2e was standalone portability; a migration that passes
  CI but silently breaks `git`-extraction defeats the point. AC-5/6 are the
  gate; do not close on a monorepo-only green.
- A missed CI task-path reference leaves a workflow calling the deleted
  `gateway-service:e2eSmokeTest` → job errors at Gradle task resolution. AC-4's
  grep must be exhaustive across `.github/` including reusable workflows.
- Staggered landing (module in one PR, CI path in another) transiently reds main
  — AC-10 forbids it.
- `wms.e2e.observabilityNetwork` renamed by accident → breaks the shared
  cross-suite observability protocol. Out-of-scope + AC-7 guard it.
- Duplicated helpers (option a) silently diverge over time — AC-9 makes the
  divergence explicit so a future reader knows the two copies must stay in sync.

---

# Test Requirements

- No new test logic. The migrated suite must run byte-for-byte the same methods.
- Prove non-regression by comparing the pre/post test reports (same class/method
  set, same pass count) rather than trusting "it compiles".
- Mutation check for AC-4: temporarily point one workflow at the OLD task path
  in a scratch branch and confirm CI fails at task resolution (proves the grep
  found the live references), then revert.
- gateway-service's own integration tests must be run post-move to prove the
  helper split (AC-2) did not break them.
