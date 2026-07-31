# Task ID

TASK-ERP-BE-040

# Title

ADR-MONO-058 D5 (erp-platform, all four servlet services) — adopt the already-shared
`PublicPathSet` matching mechanism from `libs/java-security-servlet`, keeping each service's own
`EXACT`/`PREFIXES` path data local

# Status

review

# Owner

backend

# Task Tags

- code
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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Close erp-platform's share of `ADR-MONO-058 § D5` (ACCEPTED 2026-07-30). `PublicPathSet` — the
`EXACT`/`PREFIXES` matching **mechanism** every project's `PublicPaths` class was found to
duplicate — is already promoted to `libs/java-security-servlet`
(`com.example.security.servlet.PublicPathSet`, landed by fan-platform's `TASK-FAN-BE-039`). This
task is **adoption only**: no new library code, and the module's own javadoc already documents the
exact usage pattern this task applies.

## Measured against the tree — what erp actually has (not the ADR's cross-project paraphrase)

All four erp servlet services (`approval-service`, `masterdata-service`, `notification-service`,
`read-model-service`) each declare their own `presentation.security.PublicPaths` final class.
`git diff --no-index` across all six adjacent pairs shows they are **effectively identical**: the
same `EXACT = Set.of("/actuator/health", "/actuator/info", "/actuator/prometheus")`, the same
`PREFIXES = Set.of("/actuator/health/")`, the same `isPublic(String)`/`isPublic(HttpServletRequest)`
mechanism body, word-for-word. Differences are confined to the javadoc: `notification-service` and
`read-model-service` carry one extra sentence noting the Prometheus scrape is network-isolated,
which `approval-service`/`masterdata-service` lack. **Both the mechanism and the actual path data
are identical across all four** — unlike D1's per-service `ActorContext` policy divergence, D5 has
no policy divergence to preserve beyond the (currently-identical) data itself, though the adopted
shape must still keep each service's own `EXACT`/`PREFIXES` constants as the service-owned source
of truth per `PublicPathSet`'s own design (the mechanism must not "notice" the four services happen
to agree today and quietly merge the data into the shared module — a future service-specific
webhook path, for instance, must still be addable to exactly one service's own class without
touching the shared mechanism or any sibling).

`gateway-service` is reactive (Spring Cloud Gateway) and has no `PublicPaths` class of this shape —
its equivalent exemption mechanism, if any, lives in its own `GatewayIdentityConfig`/
`OAuth2ResourceServerConfig` and is out of scope (`libs:java-security-servlet` must never reach a
reactive classpath, `ADR-MONO-048 § D1`).

`ServiceLevelOAuth2Config`'s `tenantClaimEnforcer()` bean in all four services already references
`PublicPaths::isPublic` as the `TenantClaimEnforcer` exemption predicate
(`.exempt(PublicPaths::isPublic)`) — this call site's *method reference target* does not change by
adopting `PublicPathSet` (the static `PublicPaths.isPublic(String)` method signature stays
identical per the module's own usage pattern), so this task should require **zero** change to
`ServiceLevelOAuth2Config`/`SecurityConfig` in any of the four services — confirm this at
implementation time rather than assuming it, since `TASK-ERP-BE-037` (D4) may already be adopting a
shared builder there in parallel or in sequence; either task order is valid, but this task's own
diff should not need to touch the security-chain-assembly file if the static method signature is
preserved exactly.

---

# Scope

## In Scope

- Each of `approval-service`, `masterdata-service`, `notification-service`, `read-model-service`'s
  `PublicPaths` class — rewrite the body to delegate to `com.example.security.servlet.PublicPathSet`
  per the module's own documented usage pattern:
  ```java
  public final class PublicPaths {
      public static final Set<String> EXACT = Set.of(...);      // unchanged, service-owned
      public static final Set<String> PREFIXES = Set.of(...);   // unchanged, service-owned
      private static final PublicPathSet MECHANISM = PublicPathSet.of(EXACT, PREFIXES);

      private PublicPaths() {}

      public static boolean isPublic(String path) { return MECHANISM.isPublic(path); }
      public static boolean isPublic(HttpServletRequest request) { return MECHANISM.isPublic(request); }
  }
  ```
  Keep the class name, package, `EXACT`/`PREFIXES` field names, and both static method signatures
  byte-identical — every consumer of `PublicPaths` (the four `SecurityConfig`/
  `ServiceLevelOAuth2Config` files, any filter, any test) must compile and behave unchanged with no
  edit of its own.
- `build.gradle` for all four services — verify `implementation project(':libs:java-security-servlet')`
  is already declared (all four already import `com.example.security.servlet.TenantClaimEnforcer`
  per `ADR-MONO-049`, so the dependency should already exist; confirm, do not assume).
- One atomic PR — all four services together, one commit shape, since the change is mechanically
  identical across all four and there is no per-service divergence to reconcile.

## Out of Scope

- **Every other project.** `§ 6` forbids a cross-project mega-PR; scm/wms's D5 adoption (per the
  ADR's audit table, `scm`, `erp`, `fan` [done], `wms` all carry this pattern) are separate tasks.
- **The `EXACT`/`PREFIXES` *data* itself** — stays exactly as each service already declares it;
  this task does not add, remove, or reconsider any path. If a future audit finds erp's four
  services' actuator exemption list should differ from each other, that is separate scope.
- **`ServiceLevelOAuth2Config`/`SecurityConfig`** — no edit expected (see Goal); if implementation
  finds one is required (e.g. the exemption predicate's call-site type changes), treat that as a
  finding to report, not something to route around silently, since it would mean the "zero
  downstream edit" claim in the Goal section was wrong.
- **`gateway-service`** — reactive; no `PublicPaths` class, no change.
- **ADR-MONO-058 D1 / D2 / D3 / D4** — separate tasks. (`TASK-ERP-BE-037`'s D4 half also touches
  `ServiceLevelOAuth2Config`, which references `PublicPaths::isPublic` — the two tasks may land in
  either order since this task's diff is confined to `PublicPaths.java` itself and does not change
  the static method signature D4's task consumes.)

---

# Acceptance Criteria

- [x] **AC-1 (mechanism adopted)** — all four services' `PublicPaths` classes delegate to
      `com.example.security.servlet.PublicPathSet` per the module's documented usage pattern;
      `EXACT`/`PREFIXES` fields and both `isPublic(...)` method signatures are unchanged. Verified:
      each of `approval-service`/`masterdata-service`/`notification-service`/`read-model-service`'s
      `PublicPaths.java` now declares `private static final PublicPathSet MECHANISM =
      PublicPathSet.of(EXACT, PREFIXES);` and both `isPublic` overloads are one-line delegations;
      the `path.startsWith(prefix)` loop body no longer appears in any of the four classes.
- [x] **AC-2 (zero downstream edits, verified not assumed)** — `git status --short` /
      `git diff --stat` after implementation shows only the four `PublicPaths.java` (modified) plus
      four new `PublicPathsTest.java` (added) changed. `build.gradle` in all four services already
      declared `implementation project(':libs:java-security-servlet')` before this task (confirmed
      by direct grep, not assumed) — zero `build.gradle` edits were needed.
      `git diff --stat -- "**/SecurityConfig.java" "**/ServiceLevelOAuth2Config.java"` returns empty
      — confirms the Goal section's "zero downstream edit" claim held; no finding to report.
- [x] **AC-3 (behavior identical, integration level)** — all four services' existing
      `ErpTenantGatePolicyTest` suites (which exercise the actuator health path as unauthenticated
      and `/actuator/env`/API paths as gated, via the real `ServiceLevelOAuth2Config`-built
      `TenantClaimEnforcer`) pass **unmodified**. Mutation check performed on
      `masterdata-service`: temporarily corrupted `EXACT`'s `/actuator/prometheus` entry to
      `/actuator/prometheusXXX`, re-ran `:masterdata-service:test` — 2 of the new `PublicPathsTest`
      cases (`exactSetUnchanged`, `classificationParityBeforeAndAfter`) went RED (99 tests
      completed, 2 failed); reverted, re-ran, back to 99/99 GREEN.
- [x] **AC-4 (baseline parity)** — before/after test counts recorded per module (see Verification
      Record below); no test lost, only additive. All four `:check` GREEN locally
      (`./gradlew :projects:erp-platform:apps:{masterdata,approval,notification,read-model}-service:check`).
      CI `Integration (erp-platform, Testcontainers)` GREEN is the authoritative gate — pending this
      PR's CI run.
- [x] **AC-5 (no contract or wire change)** — this is an internal mechanism swap with no
      HTTP-visible surface of its own; stated explicitly in the PR body — no
      `specs/contracts/` edit, no wire-format or status-code change, `PublicPaths.EXACT`/`PREFIXES`
      literal contents unchanged for all four services.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`,
> then load `rules/common.md` plus `rules/domains/erp.md` and `rules/traits/{internal-system,
> transactional,audit-heavy}.md`. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D5, § 6
  (ACCEPTED 2026-07-30)
- `docs/adr/ADR-MONO-049` — precedent this extends (`TenantClaimEnforcer`'s exemption predicate,
  which `PublicPaths::isPublic` already feeds)
- `platform/shared-library-policy.md` § Decision Rule, § Ownership Rule (mechanism vs. policy — the
  load-bearing boundary this task must not cross by centralizing the path data)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — origin tracking task
- `projects/fan-platform/tasks/done/TASK-FAN-BE-039-adr058-d5-public-paths-shared-value-type.md` —
  **prior art, read before starting.** Sets the governance shape for a D5 adoption in this repo
  (smallest of the ADR's D-items, mechanism-only, zero policy change) and, unlike D1/D2, fan and
  erp's `PublicPaths` classes are close enough in shape that this task's resolution should closely
  mirror it — confirm rather than assume the two projects' facts stayed aligned.
- `libs/java-security-servlet/src/main/java/com/example/security/servlet/PublicPathSet.java` — its
  own javadoc is the authoritative usage-pattern reference this task's adoption must match.
- `projects/erp-platform/specs/services/{approval,masterdata,notification,read-model}-service/architecture.md`
  § Security

---

# Related Contracts

None — internal mechanism swap, no wire-format or event contract.

---

# Target Service

- `approval-service`, `masterdata-service`, `notification-service`, `read-model-service`
  (erp-platform)
- Consumes `libs/java-security-servlet.PublicPathSet` (existing) — no shared-library code authored
  by this task.

---

# Architecture

Follow each target service's own `architecture.md` § Security. `PublicPaths` stays in its current
`presentation.security` package in all four services; no layer boundary moves.

---

# Implementation Notes

- This is the lowest-risk of the four erp ADR-058 adoption tasks in this filing batch — per
  `§ 6` item 5, D5 is meant to be done early/cheaply, and this task's own investigation confirms
  erp's four copies are already identical in both mechanism and data, so there is no divergence to
  reconcile.
- Do all four services in one pass rather than sequencing one-then-replicate — the change per
  service is mechanically identical (swap the method bodies for a one-line delegation, add a
  `private static final PublicPathSet MECHANISM` field), so there is no learning-curve reason to
  stage them.
- Confirm the `build.gradle` dependency is already present in all four before assuming no
  `build.gradle` edit is needed — do not skip this check just because `TenantClaimEnforcer`'s
  import implies it; verify directly.

---

# Edge Cases

- **A future service-specific public path.** If, after this adoption, one erp service needs to add
  a path its siblings should not have (e.g. a future webhook), it must be addable by editing only
  that service's own `EXACT`/`PREFIXES` constants — verify the adopted shape still supports this
  (it does, per `PublicPathSet`'s own design — each service still owns and passes in its own sets),
  but confirm no accidental centralization crept in during the swap.
- **`prefixes` validation.** `PublicPathSet.of(...)` throws `IllegalArgumentException` at
  construction if any prefix entry does not end with `/` — all four services' existing
  `PREFIXES = Set.of("/actuator/health/")` already satisfies this, but this is now an enforced
  invariant where before it was merely convention; confirm no other prefix entry violates it before
  shipping (there is only the one entry today in all four, but verify).

---

# Failure Scenarios

- **Silently changing the exemption set.** A copy-paste error while rewriting the class body could
  drop or add an `EXACT`/`PREFIXES` entry; AC-3's mutation check exists to catch exactly this class
  of defect before it ships.
- **Centralizing the path data into the shared module "since all four already agree."** Would
  violate `§ D5`'s explicit mechanism-vs-policy boundary and remove the seam a future
  service-specific path needs — reject this even though it looks like a harmless simplification
  today.
- **Scope leak into `ServiceLevelOAuth2Config`.** If the adoption turns out to require touching the
  security-chain-assembly file, that is D4's territory (`TASK-ERP-BE-037`) — report the finding
  rather than silently expanding this task's diff.
- **Scope leak into the other seven projects.** `§ 6` forbids a cross-project mega-PR; scm/wms's D5
  adoption remain separate future tasks.

---

# Test Requirements

- Unit/slice (per service, ×4): existing `PublicPaths`/security-chain tests exercising actuator
  paths as public and all other paths as authenticated pass unmodified; add or confirm a direct
  `PublicPaths.isPublic(...)` unit test per service if none exists today.
- Guard mutation-check: temporarily remove one `EXACT`/`PREFIXES` entry per service, confirm the
  corresponding test goes RED, then revert.
- `./gradlew :libs:java-security-servlet:test` (expected untouched — confirm) and the four erp
  `:check` tasks GREEN. CI `Integration (erp-platform, Testcontainers)` GREEN authoritative.

---

# Verification Record

## Test counts (local, Docker-free `:check` / `:test`)

| module | before | after | delta |
|---|---|---|---|
| `masterdata-service` | 94 | 99 | +5 |
| `approval-service` | 145 | 150 | +5 |
| `notification-service` | 103 | 108 | +5 |
| `read-model-service` | 143 | 148 | +5 |
| `libs:java-security-servlet` | 113 | 113 | 0 (untouched, confirmed) |

0 failures / 0 errors / 0 skipped in every module, before and after. No existing test was removed
or modified — each service gained exactly one new file (`PublicPathsTest`, 5 `@Test` methods).

## `build.gradle` dependency — confirmed, not assumed

`grep -rn "java-security-servlet"` across all four services' `build.gradle` shows
`implementation project(':libs:java-security-servlet')` already present in all four (pre-existing,
per `ADR-MONO-049` `TenantClaimEnforcer` adoption) — zero `build.gradle` edits required.

## AC-2 verified by diff, not assumed

`git status --short` / `git diff --stat` after implementation:
```
 M .../approval-service/.../presentation/security/PublicPaths.java
 M .../masterdata-service/.../presentation/security/PublicPaths.java
 M .../notification-service/.../presentation/security/PublicPaths.java
 M .../read-model-service/.../presentation/security/PublicPaths.java
?? .../approval-service/.../presentation/security/PublicPathsTest.java
?? .../masterdata-service/.../presentation/security/PublicPathsTest.java
?? .../notification-service/.../presentation/security/PublicPathsTest.java
?? .../read-model-service/.../presentation/security/PublicPathsTest.java
```
`git diff --stat -- "**/SecurityConfig.java" "**/ServiceLevelOAuth2Config.java"` returns empty —
zero lines changed in any of the eight files across the four services. The `.exempt(PublicPaths::isPublic)`
call site in each `ServiceLevelOAuth2Config` continues to compile against the unchanged
`isPublic(HttpServletRequest)` static method signature.

## Guard mutation-check (the new tests were verified to bite, not merely to pass)

Temporarily corrupted `masterdata-service`'s `PublicPaths.EXACT` (`/actuator/prometheus` →
`/actuator/prometheusXXX`) and re-ran `:masterdata-service:test`: **2 of the 5 new
`PublicPathsTest` cases went RED** (`exactSetUnchanged`, `classificationParityBeforeAndAfter`) — 99
tests completed, 2 failed. Reverted; re-ran the full suite, back to 99/99 GREEN.

## Observable behaviour deltas

None. `PublicPaths.EXACT`/`PREFIXES` literal contents and `isPublic(...)` classification are
unchanged for all four services (pinned by the new `PublicPathsTest` per service). No
`specs/contracts/` edit required.

---

# Definition of Done

- [x] All four `PublicPaths` classes delegate to the shared `PublicPathSet` mechanism; `EXACT`/
      `PREFIXES` data and both method signatures unchanged
- [x] Zero downstream file edits beyond the four `PublicPaths.java` (and `build.gradle` only if
      genuinely required); any exception stated as a finding in the PR body — none found, verified
      by diff
- [x] Tests passing; per-service before/after counts recorded; no test lost; mutation-check recorded
- [x] No contract or observable-behaviour change; PR body states this explicitly
- [x] Ready for review
