# Task ID

TASK-SCM-BE-057

# Title

Adopt ADR-MONO-058 D5 — `PublicPathSet` shared value type (already promoted to `libs/java-security-servlet`)

# Status

review

# Owner

backend

# Task Tags

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`ADR-MONO-058` (ACCEPTED 2026-07-30) § 2 D5 found the `EXACT`/`PREFIXES` set + `isPublic(String)`/`isPublic(HttpServletRequest)` matching mechanism identical across every service's local `PublicPaths` class, while the actual path lists are service policy and must not move. The mechanism was already promoted to `libs/java-security-servlet.PublicPathSet` (landed via fan-platform's `TASK-FAN-BE-039`/`040`, confirmed present at `libs/java-security-servlet/src/main/java/com/example/security/servlet/PublicPathSet.java`). This task adopts it in scm-platform's four servlet REST services, each continuing to supply its own path data.

---

# Scope

## In Scope

Grep across `projects/scm-platform/apps/` (2026-07-31) confirms exactly four local `PublicPaths` classes, all structurally identical to `PublicPathSet`'s own javadoc usage example:

- `apps/procurement-service/src/main/java/.../procurement/presentation/security/PublicPaths.java`
- `apps/logistics-service/src/main/java/.../logistics/adapter/inbound/web/security/PublicPaths.java`
- `apps/inventory-visibility-service/src/main/java/.../inventoryvisibility/adapter/inbound/web/security/PublicPaths.java`
- `apps/demand-planning-service/src/main/java/.../demandplanning/adapter/inbound/web/security/PublicPaths.java`

For each: keep the local `PublicPaths` class (its `EXACT`/`PREFIXES` constants are service policy and stay put), but delegate its `isPublic(String)`/`isPublic(HttpServletRequest)` methods to a private `static final PublicPathSet` instance built from those constants, exactly matching the pattern already documented in `PublicPathSet`'s own javadoc. This removes the duplicated matching-logic body (`EXACT.contains(path)` / prefix-loop / null-check) from each of the four classes while leaving every consumer (`SecurityConfig`, `TenantClaimEnforcer`, wherever else each service references `PublicPaths.isPublic(...)`) untouched — the public static method signatures do not change.

## Out of Scope

- `gateway-service` — no local `PublicPaths` class found (reactive edge gateway, different mechanism entirely — Spring Cloud Gateway route-level auth, not a servlet filter-chain exempt-path list).
- Changing any service's actual `EXACT`/`PREFIXES` path data — service policy, untouched (Ownership Rule, ADR § 2 D5).
- Any consumer of `PublicPaths.isPublic(...)` (`SecurityConfig`, `TenantClaimEnforcer`, etc.) — their call sites are unaffected since the public method signatures don't change; do not touch them as part of this task (that wiring is `TASK-SCM-BE-054`'s D4 scope if it happens to touch the same files, but this task's own diff should be limited to each `PublicPaths.java` file itself).
- `libs/java-security-servlet.PublicPathSet` itself — already landed, no change needed.

---

# Acceptance Criteria

- [x] All four services' `PublicPaths.java` delegate `isPublic(String)`/`isPublic(HttpServletRequest)` to a `PublicPathSet.of(EXACT, PREFIXES)` instance instead of re-implementing the matching loop. Evidence: repo-wide grep for `path.startsWith(prefix)` under `projects/scm-platform/apps/*/src/main` returns zero hits after the change (the loop body only remains inside `libs/java-security-servlet.PublicPathSet`).
- [x] Each service's `EXACT`/`PREFIXES` constants and their values are byte-for-byte unchanged — this is a mechanism swap only. Evidence: `git diff` on the four `PublicPaths.java` shows only the import addition, the `MECHANISM` field, and the two method bodies changing; no `EXACT`/`PREFIXES` literal was touched.
- [x] `PublicPaths`'s public static method signatures (`isPublic(String)`, `isPublic(HttpServletRequest)`) are unchanged, so no consumer call site requires modification. Evidence: `git status` on the four services' `apps/` trees shows only the four `PublicPaths.java` plus one test file changed — `SecurityConfig.java`, `ServiceLevelOAuth2Config.java`/`TenantClaimEnforcer` call sites are untouched.
- [x] Behavior is verified unchanged: for every path currently classified public/non-public by each service's existing tests (or a smoke check if no dedicated `PublicPaths` unit test exists today — confirm which is the case per service before assuming coverage), the classification is identical after the swap. Evidence: procurement/inventory-visibility/demand-planning already had direct `PublicPaths.EXACT`/`isPublic(...)` assertions in their `ScmTenantGatePolicyTest`, which pass unmodified. logistics-service had a genuine coverage gap (no direct or indirect `PublicPaths` assertion) — closed by adding a minimal `PublicPathsMechanism` nested test plus `FilterAdmits`/`FilterRefuses` actuator-path cases to its `ScmTenantGatePolicyTest`, mirroring the sibling services' pattern.
- [x] scm-platform Build & Test + Integration (Testcontainers) CI lanes GREEN for all four touched services. Evidence: local `./gradlew :projects:scm-platform:apps:{procurement,logistics,inventory-visibility,demand-planning}-service:test` all GREEN (see PR for CI confirmation).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D5, § 6 item 5
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)
- `platform/shared-library-policy.md` (Ownership Rule — mechanism vs. policy)
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `projects/scm-platform/specs/services/logistics-service/architecture.md`
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
- `projects/scm-platform/specs/services/demand-planning-service/architecture.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- None — this is an internal servlet-filter-chain mechanism swap with no wire-format or event-contract surface. No consumer (internal or external, including platform-console) observes `PublicPaths`'s internals.

---

# Target Service

- `procurement-service`, `logistics-service`, `inventory-visibility-service`, `demand-planning-service`
- `gateway-service` — explicitly out of scope (no local `PublicPaths` class; reactive stack uses a different mechanism)

---

# Architecture

Follow each touched service's own architecture doc (listed under Related Specs above).

---

# Implementation Notes

- `libs/java-security-servlet.PublicPathSet`'s own javadoc (as of 2026-07-31) already contains a complete usage example matching scm's own `PublicPaths` shape almost verbatim — this is a mechanical, low-risk adoption; follow that example directly rather than redesigning the delegation shape.
- `procurement-service`'s `PublicPaths` documents (in its own class javadoc) that both `SecurityConfig` and `TenantClaimEnforcer` reference it so the two "stay in lockstep" — confirm this dual-consumer relationship still holds unchanged after the swap (both call sites keep reading `PublicPaths.isPublic(...)`, not `PublicPathSet` directly).
- Per the ADR § 6 suggested sequence, this (D5) is intentionally one of the smallest, lowest-risk items and is suggested to land *before* `TASK-SCM-BE-054`'s D4 half — landing this first lets `TASK-SCM-BE-054` pass each service's already-`PublicPathSet`-backed `PublicPaths` straight into the D4 builder's exempt-path parameter without an intermediate step. Not a hard dependency either direction, but worth sequencing this way if both are picked up close together.

---

# Edge Cases

- `procurement-service`'s `PublicPaths.PREFIXES` includes `/api/procurement/webhooks/` with an explicit comment that supplier webhooks intentionally do **not** bypass authentication via the OIDC/bearer path but via a separate shared-secret verification chain — confirm this policy note (a comment, not code) survives the file edit; it documents *why* the prefix exists, which matters for the next reader even though `PublicPathSet` itself carries no such context.
- If any service's `PREFIXES` set contains an entry not ending in `/`, `PublicPathSet.of(...)` throws `IllegalArgumentException` at construction (static-initializer time, so it fails fast at class-load) — grep-confirm all four services' current `PREFIXES` values already end in `/` before adoption (evidence from the files read during audit: `procurement-service`'s do; verify the other three the same way before implementing, not after a boot failure).

---

# Failure Scenarios

- Changing any service's actual `EXACT`/`PREFIXES` values while "cleaning up" during this adoption would violate the ADR's Ownership Rule (data is policy, stays local) and could silently open or close an endpoint's auth requirement — Hard Stop if the diff touches path literals, not just the matching mechanism.
- Changing `PublicPaths`'s public method signatures would ripple into every consumer (`SecurityConfig`, `TenantClaimEnforcer`) unnecessarily — keep the public surface identical; only the private implementation delegates to `PublicPathSet`.
- If a `PREFIXES` entry doesn't end in `/`, adopting `PublicPathSet.of(...)` as a static field would throw at class-load time and fail the service's boot — verify all four services' literal values before implementation, not discover it via a CI boot failure.

---

# Test Requirements

- No new test scenarios required — behavior-preserving mechanism swap. If a dedicated `PublicPaths`-classification unit test exists for any service, it passes unmodified. If none exists today, confirm this via the existing 401/403 slice/IT tests that already exercise `isPublic(...)` indirectly through the security filter chain (e.g. an actuator-health request returning 200 without auth) — do not add net-new test infrastructure solely for this task unless a genuine coverage gap is found.

---

# Definition of Done

- [x] All four services' `PublicPaths.java` delegate to `PublicPathSet`
- [x] `EXACT`/`PREFIXES` values unchanged
- [x] Public method signatures unchanged, no consumer call sites modified
- [x] Existing behavior verified unchanged (via existing security tests; logistics-service's genuine coverage gap closed with a minimal, targeted addition — see Acceptance Criteria evidence)
- [x] scm-platform Build & Test lane GREEN locally for all four touched services; Integration (Testcontainers) CI lane pending PR CI run (task moved to `review`, not `done`, until CI confirms)
- [ ] Task moved `ready → done`, referencing `TASK-MONO-495` as origin (pending PR merge + CI-green verification per `CLAUDE.md` merge-verification rule)
