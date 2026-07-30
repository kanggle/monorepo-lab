# Task ID

TASK-FAN-BE-039

# Title

ADR-MONO-058 D5 (fan-platform only) — promote the `PublicPaths` `isPublic()` mechanism to a shared
`PublicPathSet` value type in `libs/java-security-servlet`

# Status

done

# Owner

backend

# Task Tags

- code
- api
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

Close fan-platform's share of `ADR-MONO-058 § D5` (ACCEPTED 2026-07-30, § 6 rates this item "small,
low-risk, no auth-path behavior change" — the lowest-risk item in the whole ADR).

Measured against the tree (not the ADR's cross-project paraphrase): fan-platform's four servlet services —
`community-service`, `artist-service`, `membership-service`, `notification-service` — each carry a
byte-identical `PublicPaths` class (mechanism: `EXACT`/`PREFIXES` `Set<String>` fields +
`isPublic(String)`/`isPublic(HttpServletRequest)` static methods). Only the **data** differs:
`membership-service` carries one extra `EXACT` entry, `/webhooks/portone` (the PortOne V2 webhook,
TASK-FAN-BE-033), the other three carry the identical 3-entry `EXACT` + 1-entry `PREFIXES` set.

`ADR-MONO-058 § D5`: "The `EXACT`/`PREFIXES` set + `isPublic(String)`/`isPublic(HttpServletRequest)`
mechanism is identical everywhere; the actual path lists are service policy (each service decides what's
public) and must not move. Promote a `PublicPathSet`-shaped value object to `libs/java-security-servlet`;
each service continues to supply its own data to it."

`§ 6` forbids folding this into a cross-project mega-PR (scm/erp/wms also carry this pattern per the
audit table in `§ 1.1`) — this task is **fan-platform only**, following the same project-scoped-adoption
precedent as `TASK-FAN-BE-038` (D2, done).

---

# Scope

## In Scope

- `libs/java-security-servlet/src/main/java/com/example/security/servlet/PublicPathSet.java` — new
  **mechanism-only** value type:
  - `public static PublicPathSet of(Set<String> exact, Set<String> prefixes)` — defensively copies both
    sets (`Set.copyOf`) and validates every `prefixes` entry ends with `/` (`IllegalArgumentException`
    otherwise — the existing javadoc contract on every service copy, previously enforced only by comment).
  - `public Set<String> exact()` / `public Set<String> prefixes()` — read accessors.
  - `public boolean isPublic(String path)` and `public boolean isPublic(HttpServletRequest request)` —
    byte-identical matching logic to every current copy (null path → false; exact match; else any prefix
    match).
  - No service path data. No `@Component`/Spring wiring — a plain immutable value type, constructed by
    each service's own `PublicPaths` class (mirrors how `TenantClaimEnforcer.Builder.exempt(Predicate)`
    already keeps path data out of the shared filter — `libs/java-security-servlet/build.gradle`'s own
    module-header comment: "the library never has to reach into a project's `PublicPaths` class").
- `libs/java-security-servlet/src/test/java/com/example/security/servlet/PublicPathSetTest.java` — new
  unit test for the promoted mechanism (exact match, prefix match, null path, non-matching path, blank
  `exact`/`prefixes` sets, the `IllegalArgumentException` on a prefix not ending in `/`).
- Each of `community-service`, `artist-service`, `membership-service`, `notification-service`: rewrite the
  service's own `PublicPaths` class to keep its **existing public API unchanged**
  (`EXACT`, `PREFIXES`, `isPublic(String)`, `isPublic(HttpServletRequest)` — same field/method names,
  same signatures, same package, same visibility) but delegate the matching logic to a private
  `PublicPathSet` instance built from that service's own `EXACT`/`PREFIXES` data. Because the public
  surface is unchanged, **no other file in any of the four services needs to change** — `SecurityConfig`
  (which reads `PublicPaths.EXACT`/`PREFIXES` to build Spring Security request matchers),
  `ServiceLevelOAuth2Config` (which passes `PublicPaths::isPublic` as the `TenantClaimEnforcer` exemption
  predicate), and the four `FanTenantGatePolicyTest` classes (which call
  `PublicPaths.isPublic("/actuator/env")` directly) all keep compiling and passing unmodified. This is
  the intended "delete the duplicate mechanism, keep the service's own data and its own call sites" shape
  — not a call-site migration.
- Because this touches a shared path, the lib addition and all four fan-platform adaptations land in
  **ONE atomic PR** (`CLAUDE.md § Cross-Project Changes`), commit scope `refactor(lib)` (same governance
  shape as `TASK-FAN-BE-038`).

## Out of Scope

- **Every other project.** `scm`, `erp`, `wms` carry the same `PublicPaths` pattern per the ADR's audit
  table (§ 1.1) — their D5 adoption is separate future work. One project, one PR (`ADR-MONO-058 § 6`).
- **The path lists themselves.** No `EXACT`/`PREFIXES` entry is added, removed, or renamed in any of the
  four services — this is a mechanism promotion, not a policy change. `membership-service` keeps its extra
  `/webhooks/portone` entry; the other three keep their 3+1 entries, unchanged.
- **`SecurityConfig`, `ServiceLevelOAuth2Config`, `TenantClaimEnforcer`, `TenantClaimValidator`.** D5 is
  the value-type promotion only. `ADR-MONO-058 § D4` (security-chain assembly) and `§ D1` (actor/JWT
  cluster) are separate, later, higher-risk tasks per `§ 6`'s suggested sequence — this task must not
  reach into either.
- `gateway-service` — reactive (Spring Cloud Gateway); it has no `PublicPaths` class (confirmed: the
  fan-platform grep for `PublicPaths` returns zero hits under `gateway-service`) and `libs:java-web-servlet`
  /`libs:java-security-servlet` must never reach a reactive classpath
  (`libs/java-security-servlet/build.gradle`'s `assertClasspathNeutrality` task guards this). Untouched.
- Any new error code, contract field, or HTTP behaviour change.
- ADR-MONO-058 D1 / D2 (done, `TASK-FAN-BE-038`) / D3 / D4 / D6 / D7 / D8 — separate tasks.

---

# Acceptance Criteria

- [x] **AC-1 (mechanism promoted, not duplicated)** — `libs/java-security-servlet` gains exactly one new
      class, `PublicPathSet`, holding the `EXACT`/`PREFIXES` container shape + both `isPublic` overloads.
      Repo-wide grep for the matching-logic body (`path.startsWith(prefix)` inside a `PublicPaths` class
      under `projects/fan-platform/apps/*/src/main`) shows it now appears **zero** times outside
      `PublicPathSet` — each service's `PublicPaths` delegates instead of re-implementing.
- [x] **AC-2 (public API byte-identical, zero external call-site change)** — each service's `PublicPaths`
      keeps the exact field names (`EXACT`, `PREFIXES`), method names/signatures
      (`isPublic(String)`, `isPublic(HttpServletRequest)`), package, and visibility it had before. A diff
      of `SecurityConfig.java`, `ServiceLevelOAuth2Config.java`, and every `FanTenantGatePolicyTest.java`
      (community/artist/membership/notification) shows **zero changes** — these files are proof the
      promotion is call-site-transparent, not evidence to be updated to match a new shape.
- [x] **AC-3 (data preserved exactly, per service — the core security-adjacent guarantee)** — for each of
      the four services, `EXACT` and `PREFIXES` contain the exact same string literals before and after
      this change (community/artist/notification: `{"/actuator/health","/actuator/info",
      "/actuator/prometheus"}` + `{"/actuator/health/"}`; membership: the same three plus
      `"/webhooks/portone"` in `EXACT`). Proven by a test per service that asserts the literal set
      contents (not just membership of a few probe paths), so a future accidental edit of the data is
      caught even if every existing probe-path test still passes.
- [x] **AC-4 (no path changes classification — before/after parity)** — for each service, a test enumerates
      a fixed probe list (every current `EXACT` entry, every current `PREFIXES` entry plus one sub-path
      under it, `/actuator/env` and `/actuator/heapdump` as known-non-public actuator paths, `/api/...`
      representative authenticated routes, and — membership only — `/internal/membership/access-check`)
      and asserts `PublicPaths.isPublic(...)` returns the **same boolean it returned before this task**
      for every one of them. This is the guard against the exact regression `ADR-MONO-058 § 6` calls out
      by name ("no auth-path behavior change") — run once as a pre-change baseline capture (see
      Verification Record) and re-assert unchanged after.
- [x] **AC-5 (shared type has no service knowledge)** — `PublicPathSet` contains no hardcoded path string
      and no import from any `projects/` module. `libs/java-security-servlet`'s existing
      `assertClasspathNeutrality` guard task stays GREEN unmodified (no new dependency added).
- [x] **AC-6 (`PublicPathSet`'s own contract is tested directly)** — `PublicPathSetTest` proves: exact
      match, prefix match (including a path exactly equal to the prefix minus trailing `/`, which must
      NOT match — `"/actuator/health"` is in `EXACT` already, so this specifically probes a prefix-only
      case), null path → false, non-matching path → false, and a `prefixes` entry not ending in `/`
      throws `IllegalArgumentException` at construction (fail fast, not a silent no-match at query time).
- [x] **AC-7 (baseline parity — no test lost, no test broken)** — record each of the four services' and
      the lib's test count **before** and **after**. No test may disappear. All four `:check` tasks and
      `:libs:java-security-servlet:check` are GREEN, and CI's `Integration (fan-platform, Testcontainers)`
      lane is GREEN (authoritative — local Windows Docker is not, per
      `project_testcontainers_docker_desktop_blocker`).
- [x] **AC-8 (no contract or spec edit required)** — `specs/contracts/http/*.md` need **no** edit (D5 is
      purely internal mechanism, no wire-visible change). `specs/services/*/architecture.md` package-layout
      diagrams that mention `PublicPaths` (e.g. `artist-service/architecture.md`) remain accurate without
      edit — the class stays at the same package path with the same public shape, only its internal
      implementation changes. If implementation finds this claim false for any file, stop and correct that
      file in the same PR rather than leaving stale doc behind.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D5, § 4, § 6 (ACCEPTED)
- `platform/shared-library-policy.md` § Decision Rule, § Dependency Rule, § Ownership Rule, § Change Rule
- `platform/service-types/rest-api.md` § Error Handling (unaffected surface, read for context only)
- `projects/fan-platform/specs/services/{community,artist,membership,notification}-service/architecture.md`
  § Package Layout (where each mentions `PublicPaths`/security package contents)
- `libs/java-security-servlet/build.gradle` — the module header comment stating the Dependency Rule
  reasoning this task must not violate ("the library never has to reach into a project's `PublicPaths`
  class")
- `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`
  — **prior art, read before starting.** Established, for this project, the governance pattern for an
  `ADR-MONO-058` sub-task that touches shared `libs/`: one atomic PR (lib + all four fan services),
  `refactor(lib)` commit scope, before/after test-count table in the Verification Record, explicit
  statement of what did and did not change. This task follows the same shape for a much smaller diff.

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

None. D5 is an internal security-mechanism refactor with no wire-visible surface — no HTTP contract in
`projects/fan-platform/specs/contracts/http/` documents `PublicPaths`' internal shape (the one contract
reference, `membership-api.md` § the PortOne webhook section, describes the `/webhooks/portone` path being
public — a fact about the **data**, which this task does not change — not about the mechanism).

---

# Target Service

- `community-service`, `artist-service`, `membership-service`, `notification-service` (fan-platform)
- `libs/java-security-servlet` (shared — atomic, same PR)

---

# Architecture

Follow each target service's own `architecture.md`. `PublicPaths` stays in its current package
(`community`/`membership`/`notification`: `presentation.security`; `artist`:
`adapter.in.web.security` — the four services never agreed on this package name, and this task does not
change that either) and keeps its current class name — both are referenced by the four
`SecurityConfig`/`ServiceLevelOAuth2Config`/`FanTenantGatePolicyTest` files by import, so renaming or
moving it would be an unrelated, unnecessary diff.

---

# Implementation Notes

- Order of work that keeps the diff reviewable: (1) `PublicPathSet` + its own test in
  `libs/java-security-servlet`; (2) one service end-to-end (`notification-service` — smallest, no
  divergent data) to prove the delegation compiles and the existing `SecurityConfig`/
  `ServiceLevelOAuth2Config`/`FanTenantGatePolicyTest` files genuinely need zero changes; (3) replicate to
  the other three, `membership-service` last (it carries the one data divergence — confirm the extra
  `/webhooks/portone` entry survives byte-for-byte).
- `PublicPathSet.of(...)` should defensively copy (`Set.copyOf`) rather than store the caller's set by
  reference — the service's own `EXACT`/`PREFIXES` constants stay the public, directly-referenced fields
  (`SecurityConfig` reads them directly today and must keep doing so per AC-2), so `PublicPathSet` holding
  its own defensive copy avoids any aliasing surprise between the two.
- Each service's rewritten `PublicPaths` should keep its existing class-level javadoc (the "both
  `SecurityConfig` and `TenantClaimEnforcer` reference this list" explanation is still true and still
  useful) and may add one sentence noting the matching mechanism now delegates to
  `com.example.security.servlet.PublicPathSet` (ADR-MONO-058 § D5) — documentation-only addition, not
  required by any AC but keeps the class's own javadoc honest about what changed.
- Do not add a `PublicPathSet` `equals`/`hashCode` unless a test actually needs value equality — none of
  the four services compare `PublicPathSet` instances, only call `isPublic(...)` or read `exact()`/
  `prefixes()`.

---

# Edge Cases

- A path exactly equal to a `PREFIXES` entry with its trailing `/` stripped (e.g. `/actuator/health`
  against the `/actuator/health/` prefix) must still match — but it already matches via the `EXACT` set
  in all four services (`/actuator/health` is itself an `EXACT` entry), so this is not a new edge case the
  promotion introduces; `PublicPathSetTest` (AC-6) probes the prefix mechanism in isolation with a
  synthetic prefix that has no corresponding `EXACT` entry, so the prefix-vs-exact interaction is actually
  exercised rather than accidentally passing through the `EXACT` branch.
- `membership-service`'s `/internal/**` exemption (used by `ServiceLevelOAuth2Config.tenantClaimEnforcer()`
  alongside `PublicPaths::isPublic`) is a **separate** predicate composed at the wiring site
  (`.exempt(request -> PublicPaths.isPublic(request) || request.getRequestURI().startsWith("/internal/"))`)
  — it is NOT part of `PublicPaths`/`PublicPathSet` today and must not become part of it; folding it in
  would silently change what `PublicPaths.isPublic()` means for the other three services that import the
  same shared value type shape (each service's own instance, so there's no real cross-service leakage
  risk — but the class's own contract should not gain a member no service asked for).
- `notification-service` is declared `event-consumer` but exposes a REST inbox; its `PublicPaths` usage is
  in scope exactly like the three `rest-api` services (matches the precedent set by `TASK-FAN-BE-038`'s
  Edge Cases section for the same service).

---

# Failure Scenarios

- **Silent security regression via the value type's copy semantics.** If `PublicPathSet.of(...)` stored
  the passed-in `Set` by reference instead of defensively copying, and any future code mutated a service's
  `EXACT`/`PREFIXES` "constant" via reflection or a non-`Set.of` mutable collection, the shared type's
  behaviour would silently drift from what the service's own fields say. AC-6 requires validating the
  isolation is real, not assumed.
- **Widening the shared type "just a little."** Adding the `/internal/**` prefix logic (or any other
  service-specific predicate) into `PublicPathSet` itself would put policy into the mechanism-only shared
  type — exactly what `ADR-MONO-058 § D5` and `libs/java-security-servlet/build.gradle`'s own header
  comment forbid. If an implementer reaches for it, stop and re-read Scope/Edge Cases.
- **Call-site migration disguised as a promotion.** Rewriting `SecurityConfig`/`ServiceLevelOAuth2Config`
  to call `PublicPathSet` directly instead of through each service's own `PublicPaths` facade would still
  "work," but it breaks AC-2's zero-external-diff guarantee and turns a small, low-risk mechanical
  promotion into a wider security-surface diff the ADR explicitly did not ask for at this step (that
  reshaping, if ever wanted, belongs with `§ D4`, not `§ D5`).
- **Data drift disguised as a "cleanup."** Deleting `membership-service`'s `/webhooks/portone` entry (or
  adding it to the other three "for consistency") is a policy change, not a mechanism promotion — Out of
  Scope, and AC-3/AC-4 exist specifically to catch it.
- **Scope leak into scm/erp/wms.** The same `PublicPaths` pattern exists in those projects (`ADR-MONO-058`
  § 1.1's audit table). Fixing it there is separate future work — `§ 6` forbids a cross-project mega-PR.

---

# Test Requirements

- Unit (lib): `PublicPathSetTest` — exact match, prefix match, null path, non-matching path, blank
  `exact`/`prefixes`, `IllegalArgumentException` on a malformed prefix (AC-6).
- Unit/slice (per service): a data-preservation test asserting `PublicPaths.EXACT`/`PublicPaths.PREFIXES`
  literal contents (AC-3), and a before/after classification-parity test over the fixed probe list (AC-4).
  These may live in the existing `FanTenantGatePolicyTest` (which already probes `PublicPaths.isPublic`
  directly) as additional `@Test` methods, or a new small `PublicPathsTest` per service — implementer's
  choice, but do not delete or weaken any existing assertion in `FanTenantGatePolicyTest`.
- All four services' existing `SecurityConfig`/`ServiceLevelOAuth2Config`/`FanTenantGatePolicyTest` files
  pass **unmodified** — this is the regression net for AC-2 (if the promotion required editing any of
  these files, that is itself a signal the public API changed, and the task should stop and reconsider the
  design rather than "fix" the call site).
- `./gradlew :libs:java-security-servlet:check`, the four fan `:check` tasks GREEN. CI
  `Integration (fan-platform, Testcontainers)` GREEN is authoritative.

---

# Verification Record

## Test counts (local, Docker-free `:check` / `:test`)

| module | before | after | delta |
|---|---|---|---|
| `community-service` | 127 | 130 | +3 |
| `artist-service` | 126 | 129 | +3 |
| `membership-service` | 127 | 130 | +3 |
| `notification-service` | 101 | 104 | +3 |
| `libs:java-security-servlet` | 24 | 35 | +11 |

0 failures / 0 errors / 0 skipped in every module, before and after. No existing test was removed or
modified — each service gained exactly one new file (`PublicPathsTest`, 3 `@Test` methods); the lib gained
`PublicPathSetTest` (11 `@Test` methods). Baseline matches `TASK-FAN-BE-038`'s recorded "after" counts,
confirming the tree was in the expected post-D2 state before D5 started.

## AC-2 verified by diff, not assumed

`git diff --stat -- "**/SecurityConfig.java" "**/ServiceLevelOAuth2Config.java" "**/FanTenantGatePolicyTest.java"`
returns **empty** — zero lines changed across all four services' `SecurityConfig.java`,
`ServiceLevelOAuth2Config.java`, and `FanTenantGatePolicyTest.java`. The only files touched are: the four
`PublicPaths.java` (rewritten to delegate), the four new `PublicPathsTest.java`, and the new
`libs/java-security-servlet` `PublicPathSet.java` + `PublicPathSetTest.java`.

## Guard mutation-check (the new tests were verified to bite, not merely to pass)

Temporarily corrupted `community-service`'s `PublicPaths.EXACT` (`/actuator/prometheus` →
`/actuator/prometheusXXX`) and re-ran `:community-service:test`: **2 of the 3 new `PublicPathsTest` cases
went RED** (`exactSetUnchanged`, `classificationParityBeforeAndAfter`) — 130 tests completed, 2 failed.
Reverted; re-ran the full suite, back to 130/130 GREEN. The guard bites a real data regression, not just a
vacuous assertion.

## Cross-project (shared-lib) blast radius

- `libs/java-security-servlet` gains exactly one new class (`PublicPathSet`) + its test. No existing class
  in the module was modified. `assertClasspathNeutrality` stays GREEN unmodified (no new dependency added
  — `PublicPathSet` uses only `jakarta.servlet.http.HttpServletRequest` and `java.util`, both already on
  the module's classpath).
- Other `libs:java-security-servlet` consumers outside fan-platform (`scm-platform`: procurement/
  logistics/inventory-visibility/demand-planning; `erp-platform`: read-model/notification/masterdata/
  approval; `finance-platform`: ledger/account) are unaffected by construction — purely additive lib
  change. Sanity-compiled one representative service per other project
  (`erp-platform:masterdata-service`, `scm-platform:procurement-service`,
  `finance-platform:account-service`) — all `compileJava` GREEN.
- `gateway-service` (reactive) untouched — confirmed zero `PublicPaths` usage in `gateway-service` and
  `libs:java-security-servlet` is not on its classpath.

## Observable behaviour deltas

None. `PublicPaths.EXACT`/`PREFIXES` literal contents and `isPublic(...)` classification are unchanged
for every one of the four services (AC-3/AC-4, pinned by the new `PublicPathsTest` per service).

---

# Definition of Done

- [x] Implementation completed (lib type + 4 service adoptions, one atomic PR)
- [x] Tests passing; per-service before/after counts recorded; no test lost
- [x] Zero changes to `SecurityConfig`/`ServiceLevelOAuth2Config`/`FanTenantGatePolicyTest` in any of the
      four services (verified by diff, not assumed)
- [x] Each service's exact `EXACT`/`PREFIXES` data confirmed byte-identical before/after
- [x] Contracts unchanged (verified); no spec edit required (verified `architecture.md` package-layout
      mentions of `PublicPaths` remain accurate — same package, same class name, same public shape)
- [x] Ready for review
