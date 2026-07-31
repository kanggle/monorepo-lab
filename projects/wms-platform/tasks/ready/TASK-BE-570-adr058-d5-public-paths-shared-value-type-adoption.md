# Task ID

TASK-BE-570

# Title

ADR-MONO-058 D5 (wms-platform) — introduce a per-service `PublicPaths` class backed by the already-shared
`libs/java-security-servlet.PublicPathSet`, replacing the inline `PUBLIC_PATHS` string array duplicated
across the 5 wms servlet services' `SecurityConfig`

# Status

ready

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

# Dependency Markers

- **선행 없음** — standalone; `PublicPathSet` is **already promoted** to `libs/java-security-servlet`
  (landed via fan-platform's `TASK-FAN-BE-038`/`039`, confirmed present at
  `libs/java-security-servlet/src/main/java/com/example/security/servlet/PublicPathSet.java`). Unlike
  `TASK-BE-569` (D4), this task does **not** wait on any not-yet-landed promotion task.
- **관련 (비차단)**: `TASK-BE-569` (D4, filed alongside this task) will eventually wire whatever
  `PublicPaths` shape this task produces into the D4 security-chain builder as a constructor/builder
  parameter (per `TASK-MONO-500`'s own Scope). Landing this task first makes `TASK-BE-569` cleaner but is
  not required — `TASK-BE-569` can consume the current inline array form if this task hasn't landed yet.

---

# Goal

Close wms-platform's share of `ADR-MONO-058 § D5` (ACCEPTED 2026-07-30, § 6 rates this item "small,
low-risk, no auth-path behavior change" — the lowest-risk item in the whole ADR).

**Measured against the tree** (not the ADR's cross-project paraphrase, which lists wms among scm/erp/fan
carrying "~16 copies" of a `PublicPaths` class): **wms-platform has no standalone `PublicPaths` class at
all** — repo-wide grep for `class PublicPaths` under `projects/wms-platform/apps` returns zero hits. What
the ADR's audit actually found is a **structurally simpler but still duplicated** pattern: each of the 5
wms servlet services' `SecurityConfig.java` (`master`, `inventory`, `outbound`, `inbound`, `admin`)
declares an identical inline array —

```java
private static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/actuator/prometheus"
};
```

— passed directly into Spring Security's `.requestMatchers(PUBLIC_PATHS).permitAll()`. All 5 copies are
**byte-identical** (confirmed by reading `master-service`, `inventory-service`, and `admin-service` in
full; `outbound-service`/`inbound-service` share the same `SecurityConfig` skeleton per this task's
broader D4 investigation). There is no `isPublic(String)`/`isPublic(HttpServletRequest)` predicate method
anywhere in wms today — the array is consumed exactly once, by Spring's own `requestMatchers(...)`, using
Ant-style wildcard syntax (`/actuator/health/**`), not the `EXACT`/`PREFIXES`-split shape
`PublicPathSet` expects.

**This means wms's D5 adoption is not a pure "delegate instead of duplicate" swap the way fan-platform's
was** (fan already had a standalone `PublicPaths` class with the `EXACT`/`PREFIXES` split; this task's
wms equivalent starts from an inline Ant-pattern array with no such split and no predicate method). The
adoption here is: (1) introduce a per-service `PublicPaths` class, mirroring fan's landed shape
(`EXACT`/`PREFIXES` constants + `isPublic(String)`/`isPublic(HttpServletRequest)`, backed by
`PublicPathSet`); (2) split the 4-entry Ant-pattern array into `EXACT = {"/actuator/health",
"/actuator/info", "/actuator/prometheus"}` + `PREFIXES = {"/actuator/health/"}` (the wildcard entry
`/actuator/health/**` becomes the prefix `/actuator/health/`, matching `PublicPathSet`'s "prefix must end
in `/`" contract — this is a semantics-preserving split: `PublicPathSet.isPublic` on a prefix means
`path.startsWith(prefix)`, equivalent to what Ant's `/**` suffix matches); (3) `SecurityConfig` continues
to call `.requestMatchers(...)` with an Ant-pattern-shaped array for Spring's own matcher construction
(this is unavoidable — `PublicPathSet` has no method that emits Ant patterns, it only answers
`isPublic(...)`), so the new `PublicPaths` class needs **both** a Spring-matcher-shaped accessor (e.g. a
method returning `EXACT` plus each `PREFIXES` entry suffixed with `**`) **and** the `isPublic(...)`
predicate — the predicate is the genuinely new capability this task adds to wms (useful to
`TASK-BE-569`/D4's future builder wiring, which composes `PublicPathSet`-shaped exemption predicates the
way fan's `ServiceLevelOAuth2Config` already does), not a capability wms is replacing an existing use of.

---

# Scope

## In Scope

- New per-service `PublicPaths` class in each of `master-service`, `inventory-service`, `outbound-service`,
  `inbound-service`, `admin-service`, package-adjacent to that service's `SecurityConfig` (e.g.
  `config.security` or `config`, matching each service's existing package layout):
  - `public static final Set<String> EXACT` — `{"/actuator/health", "/actuator/info",
    "/actuator/prometheus"}` (identical across all 5, confirmed).
  - `public static final Set<String> PREFIXES` — `{"/actuator/health/"}` (identical across all 5).
  - A private `PublicPathSet` instance built via `PublicPathSet.of(EXACT, PREFIXES)`.
  - `public static boolean isPublic(String path)` / `public static boolean isPublic(HttpServletRequest
    request)` — new capability, delegating to the `PublicPathSet` instance.
  - A Spring-matcher-shaped accessor (name at implementer's discretion, e.g. `public static String[]
    asAntPatterns()`) returning `EXACT` plus each `PREFIXES` entry with `**` appended — used by
    `SecurityConfig` in place of the current inline array.
- Each service's `SecurityConfig.java`: delete the inline `PUBLIC_PATHS` array; call
  `PublicPaths.asAntPatterns()` (or equivalent) at the `.requestMatchers(...)` call site.
- A unit test per service (or one shared-shape test run per service if the implementer finds a way to
  avoid 5x duplication without violating the Ownership Rule) asserting: `isPublic` returns true for every
  `EXACT` entry and a representative sub-path under `/actuator/health/`; false for `/actuator/env`,
  `/actuator/heapdump`, and a representative authenticated `/api/...` route; `asAntPatterns()` (or
  equivalent) produces a matcher array that, when driven through the actual `SecurityFilterChain`, still
  permits exactly the same set of paths the current inline array permits (classification-parity check).

## Out of Scope

- **Every other project.** `scm`, `erp` carry the same underlying pattern per the ADR's audit table
  (§ 1.1); `fan` is already done (`TASK-FAN-BE-039`). Their D5 adoption (if `scm`/`erp` turn out, on
  investigation, to have the same inline-array-not-standalone-class shape wms does, or a different shape)
  is separate future work. One project, one PR (`ADR-MONO-058 § 6`).
- **The path lists themselves.** No `EXACT`/`PREFIXES`/Ant-pattern entry is added, removed, or renamed —
  this is a mechanism promotion, not a policy change. All 5 services keep the identical 4-actuator-path
  allow-list they have today.
- **`SecurityConfig`'s non-`PUBLIC_PATHS` content** — the JWT converter, entry points, access-denied
  handler, and the `.oauth2ResourceServer(...)` wiring are untouched here; that is `TASK-BE-569`/D4's
  scope, filed separately.
- **`gateway-service`** — reactive (Spring Cloud Gateway); confirmed zero `PUBLIC_PATHS`/`PublicPaths`
  usage under `gateway-service` (its own routing config handles public-path exemption differently, at the
  gateway layer). `libs:java-security-servlet` must never reach a reactive classpath.
- Any new error code, contract field, or HTTP behavior change.
- ADR-MONO-058 D1 / D2 / D3 / D4 / D6 / D7 / D8 — separate tasks (`D2`/`D3`/`D4`/`D7` filed alongside this
  one as `TASK-BE-567`/`568`/`569`/`571`).

---

# Acceptance Criteria

- [ ] **AC-1 (mechanism reused, not re-implemented)** — none of the 5 wms `PublicPaths` classes contains
      its own `startsWith`/exact-match loop; each delegates to a `PublicPathSet` instance from
      `libs/java-security-servlet`.
- [ ] **AC-2 (Spring matcher construction unaffected in behavior)** — for each service, drive a request to
      every current `PUBLIC_PATHS` entry (including a representative `/actuator/health/<sub-path>` under
      the `/**` wildcard) through the real `SecurityFilterChain` before and after this change and confirm
      identical `permitAll` classification. This is the direct check against `ADR-MONO-058 § 6`'s "no
      auth-path behavior change" promise for D5 — do not rely on a unit test of `PublicPaths.isPublic(...)`
      alone, since that method is new and was not what `SecurityConfig` used before; the actual regression
      surface is the `.requestMatchers(...)` call, which changes from a literal array to a generated one.
- [ ] **AC-3 (data preserved exactly, per service)** — `EXACT`/`PREFIXES` contain the exact same
      4-path allow-list, confirmed identical for all 5 services (this task's investigation found no
      per-service divergence, unlike fan-platform's `membership-service` extra `/webhooks/portone` entry —
      re-verify this holds for all 5 at implementation time, not just the 3 sampled during investigation).
- [ ] **AC-4 (new `isPublic()` predicate correctly classifies)** — `/actuator/env`, `/actuator/heapdump`,
      and at least one representative authenticated `/api/...` route return `false`; all `EXACT` entries
      and a `/actuator/health/` sub-path return `true`.
- [ ] **AC-5 (shared type has no service knowledge)** — no service-specific path string is added to
      `PublicPathSet` itself; `libs/java-security-servlet`'s `assertClasspathNeutrality` (or equivalent)
      guard, if present, stays green unmodified.
- [ ] **AC-6 (baseline parity)** — record each of the 5 services' test count before/after. No test may
      disappear. All 5 `:check`/`:test` tasks green; wms CI `Integration`/`E2E` lanes (Testcontainers)
      green.
- [ ] **AC-7 (no contract or spec edit required)** — `specs/contracts/http/*.md` need no edit (D5 is
      purely internal). If any `architecture.md` package-layout diagram is found to reference the old
      inline-array location in a way that goes stale, correct it in the same PR.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D5, § 4, § 6 (ACCEPTED)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — the tracking task this splits from.
- `platform/shared-library-policy.md` § Decision Rule, § Dependency Rule, § Ownership Rule
- `specs/services/{master,inventory,outbound,inbound,admin}-service/architecture.md` § Security
- `libs/java-security-servlet/src/main/java/com/example/security/servlet/PublicPathSet.java` — the
  already-shared type being consumed; read in full (its javadoc includes a worked usage example matching
  the shape this task should produce).
- `projects/fan-platform/tasks/done/TASK-FAN-BE-039-adr058-d5-public-paths-shared-value-type.md` — prior
  art for the promotion PR itself (this task only needs the *consumption* pattern from its "Usage" javadoc
  example, not its adoption-task narrative — fan already had a standalone class to convert; wms is
  building one for the first time from an inline array, a materially different starting point).

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

None. D5 is an internal security-mechanism refactor with no wire-visible surface.

---

# Target Service

- `master-service`, `inventory-service`, `outbound-service`, `inbound-service`, `admin-service`
  (wms-platform)
- Consumes `libs/java-security-servlet` (already shared, already a dependency; no new promotion needed)

---

# Architecture

Follow each target service's own `architecture.md`. The new `PublicPaths` class lives alongside
`SecurityConfig` in each service's existing security-configuration package (4 of 5 Hexagonal:
`config`/`config.security`; `admin-service` Layered per `PROJECT.md § Overrides`: `config`/`infra.security`
— match whichever package `OAuth2ResourceServerConfig` already sits in for that service, for consistency).

---

# Implementation Notes

- Order of work that keeps the diff reviewable: (1) `PublicPaths` + its test in one service —
  `inventory-service` (already read in full during this task's investigation) — verify the
  classification-parity test (AC-2) genuinely exercises the real filter chain before replicating; (2)
  confirm the identical 4-path allow-list holds in the other 4 before copying the class verbatim into
  each (do not assume — re-grep each service's current `PUBLIC_PATHS` literal at implementation time); (3)
  the remaining 4 services.
- The Ant-pattern-vs-`EXACT`/`PREFIXES` conversion is the one place this task's design differs from every
  other project's D5 adoption to date (fan's `TASK-FAN-BE-039` started from an already-split
  `EXACT`/`PREFIXES` class and only needed to swap the matching *logic*). Verify the semantic equivalence
  explicitly: Spring's `/actuator/health/**` Ant pattern matches `/actuator/health/` itself and any
  sub-path; `PublicPathSet`'s prefix matching (`path.startsWith(prefix)`) on `/actuator/health/` matches
  the same set. `/actuator/health` (no trailing slash) matches via the separate `EXACT` entry in both the
  old array and the new split — confirm this exact-vs-prefix boundary case is covered by AC-2's
  before/after classification-parity test, since it is the one case where a naive re-implementation could
  drift (e.g. if `EXACT` were dropped and only `/actuator/health/**` were kept, `/actuator/health` itself,
  with no trailing slash, would stop matching).
- `PublicPathSet.of(...)` throws `IllegalArgumentException` if any `PREFIXES` entry doesn't end with `/`
  — `/actuator/health/` already satisfies this, no adjustment needed there.

---

# Edge Cases

- `/actuator/health` (exact, no trailing slash) vs `/actuator/health/` (prefix boundary) vs
  `/actuator/health/liveness` (sub-path under the prefix) — three distinct cases the old single Ant
  pattern `/actuator/health/**` plus the separate `EXACT` entry `/actuator/health` already handled
  correctly; the new split must preserve all three. `PublicPathSetTest`-style coverage in
  `libs/java-security-servlet` already probes this general shape (per `TASK-FAN-BE-039`'s Edge Cases) —
  this task's per-service test additionally proves it holds through wms's actual `SecurityFilterChain`,
  not just the isolated `PublicPathSet`.
- `admin-service`'s `RoleHierarchy`-aware `SecurityFilterChain` bean signature
  (`securityFilterChain(HttpSecurity, ObjectMapper, RoleHierarchy)`) differs from the other 4
  (`securityFilterChain(HttpSecurity, ObjectMapper)`) — the `PublicPaths`/matcher-array swap must not
  interact with this difference; only the `.requestMatchers(...)` argument source changes.
- `inventory-service`'s `SecurityConfig` carries `@ConditionalOnWebApplication(type = SERVLET)` — the new
  `PublicPaths` class itself has no such condition (it is a plain value-holding class with no Spring
  annotations), so this is a non-issue, but confirm the class does not accidentally become a `@Component`
  that would need the same conditional guard.

---

# Failure Scenarios

- **Silent security regression at the exact/prefix boundary.** Dropping the `EXACT` entry for
  `/actuator/health` while keeping only the prefix would deny bare `/actuator/health` requests (health
  checks probe both forms in different tools) — AC-2's real-filter-chain classification-parity test exists
  specifically to catch this rather than trusting a unit-level `isPublic()` assertion alone.
- **Widening the shared type "just a little."** Adding an Ant-pattern-emitting method to `PublicPathSet`
  itself (instead of to each service's own `PublicPaths` wrapper) would put wms-specific Spring-matcher
  convenience into a type shared by 4+ other projects that don't need it — keep `asAntPatterns()` (or
  equivalent) on the per-service `PublicPaths` class, not on `PublicPathSet`.
- **Data drift disguised as a cleanup.** Removing `/actuator/prometheus` from one service "because it
  looks unused" or adding an entry "for consistency" is a policy change, out of scope — AC-3 exists to
  catch it.
- **Scope leak into `TASK-BE-569`/D4.** This task must not touch `SecurityConfig`'s JWT/validator-chain
  content — that is `TASK-BE-569`'s scope, filed separately.
- **Scope leak into `scm`/`erp`.** `ADR-MONO-058 § 1.1`'s audit table lists the same pattern there; fixing
  it outside wms-platform here is explicitly forbidden by `§ 6`.

---

# Test Requirements

- Unit/slice per service: `isPublic(...)` classification test (AC-4), data-preservation test (AC-3).
- Real-filter-chain classification-parity test per service (AC-2) — the load-bearing regression net for
  the "no auth-path behavior change" promise; must exercise the actual `SecurityFilterChain` bean, not
  call `PublicPaths.isPublic(...)` directly (that method is new and never gated the filter chain before
  this task).
- All 5 services' existing `SecurityConfig`-adjacent tests (`WmsTenantGatePolicyTest`, any existing
  actuator-reachability test) pass unmodified in assertion content.
- 5 services' `:check`/`:test` green. wms CI `Integration`/`E2E` (Testcontainers) green, authoritative
  over local Windows Docker.

---

# Definition of Done

- [ ] Implementation completed (5 new `PublicPaths` classes + 5 `SecurityConfig` call-site swaps)
- [ ] Tests passing; per-service before/after counts recorded; no test lost
- [ ] Real-filter-chain classification-parity confirmed for all 5 services (AC-2)
- [ ] `EXACT`/`PREFIXES` data confirmed byte-identical to the pre-existing inline array's semantics for
      all 5 services
- [ ] No `SecurityConfig` content beyond the `PUBLIC_PATHS`-array-to-`PublicPaths`-class swap touched
- [ ] Ready for review
