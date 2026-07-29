# Task ID

TASK-FAN-BE-036

# Title

Remove the unreferenced TenantContext class straggling in notification/community/artist-service

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

Delete the unreferenced `TenantContext` class (`domain/tenant/TenantContext.java`, 15 lines / 3 constants) from `notification-service`, `community-service`, and `artist-service`, and update each affected `architecture.md` § Package Layout so its diagram no longer advertises a class that does not exist. An earlier dead-code removal (`TASK-MONO-479`-adjacent sweep, PR #2840 / "M1") already deleted the identical class from `membership-service` for the same reason; these three copies are stragglers of that removal that was started and never finished. Measured, not inferred: repo-wide grep for `TenantContext` returns zero references outside the class's own declaration and the four `architecture.md` package-layout diagrams. The live tenant gate uses different sources entirely — `ActorContextJwtAuthenticationConverter` reads `TenantClaimValidator.CLAIM_TENANT_ID` (from `libs:java-security`), and `ServiceLevelOAuth2Config` uses `@Value("${fanplatform.oauth2.required-tenant-id:fan-platform}")`.

---

# Scope

## In Scope

- Delete `domain/tenant/TenantContext.java` from `notification-service`, `community-service`, and `artist-service`.
- One-line edit to each affected service's `specs/services/<service>/architecture.md` § Package Layout removing the `domain/tenant/TenantContext.java` line from the diagram.
- Before deleting each file, run and record (in the PR body) a grep across the whole repo for `TenantContext`, `CLAIM_TENANT_ID`, `DEFAULT_TENANT_ID`, and `WILDCARD_TENANT`, covering both `src/main` and `src/test`, per `platform/refactoring-policy.md § Rules #6` — split explicitly which symbols are genuinely unreferenced (the class) vs. load-bearing (the `tenant_id` claim string itself, used elsewhere).

## Out of Scope

- `TenantClaimValidator` / `TenantClaimEnforcer` / `ServiceLevelOAuth2Config` — live, load-bearing; not touched.
- The `tenant_id` JWT claim name string or the `fanplatform.oauth2.required-tenant-id` property — unchanged; only the unused class is deleted, not the concept it modeled.
- Any other dead-code sweep beyond this one class.
- `libs/` — out of a fan-platform-only task's boundary.
- `membership-service` — already cleaned by the earlier removal; nothing to do there.

---

# Acceptance Criteria

- [ ] Before deleting each file, a recorded grep across the whole repo for `TenantContext`, `CLAIM_TENANT_ID`, `DEFAULT_TENANT_ID`, and `WILDCARD_TENANT` (covering `src/main` and `src/test`) shows zero live consumers per service; the result is pasted into the PR body.
- [ ] The decision is split explicitly per symbol: which survivors are genuinely unreferenced (the class) vs. load-bearing (the claim string) — no blanket "unused" claim without the grep evidence.
- [ ] Every touched service's `architecture.md` § Package Layout no longer lists `domain/tenant/TenantContext.java`.
- [ ] `./gradlew :projects:fan-platform:apps:notification-service:check`, `:community-service:check`, `:artist-service:check` GREEN with the same test count before and after; no test file edited.
- [ ] No behaviour change: no security config, no JWT claim name, no property key modified.
- [ ] A constant referenced only from a test class would make that service's class live — verified not to be the case (grep covers `src/test`).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` (§ Rules #6 — "unused" is a measurement, not an inference; § Prioritization item 3 — dead-code removal)
- `projects/fan-platform/specs/services/notification-service/architecture.md` § Package Layout
- `projects/fan-platform/specs/services/community-service/architecture.md` § Package Layout
- `projects/fan-platform/specs/services/artist-service/architecture.md` § Package Layout

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

None — this class is not referenced by any HTTP or event contract.

---

# Target Service

- `notification-service`, `community-service`, `artist-service`

---

# Architecture

Follow:

- Each target service's own `architecture.md` (package-layout section only; no structural change).

---

# Implementation Notes

- This is the identical straggler-removal pattern already applied once to `membership-service` (PR #2840) — same class, same rationale, same verification method (repo-wide grep before delete).

---

# Edge Cases

- A string-literal `"tenant_id"` used elsewhere is not a reference to this class and must not be rewritten to use it.
- If any of the three services' `TenantContext` copies has actually diverged (extra constants, different values) from the deleted `membership-service` version, verify it is still genuinely unreferenced before deleting — do not assume identical-name implies identical-status across services.

---

# Failure Scenarios

- Deleting only one or two of the three sibling copies leaves stragglers and a partially-true spec — the same drift this task exists to close; delete all three genuinely-unreferenced copies together, or document precisely why one is being deferred.
- Claiming "orphaned" without the grep is the exact error class `platform/refactoring-policy.md`'s worked incident records — CI cannot catch a false justification because the artifact still compiles either way.
- If the grep surfaces even one live consumer in one service, delete only in the services confirmed dead and stop — do not delete the referenced copy.

---

# Test Requirements

- No new tests required (dead-code removal). Existing suites for all three services must pass unmodified with identical test counts to their pre-change baselines.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing unmodified (same count as baseline) for all three services
- [ ] Contracts unchanged (verified — none applicable)
- [ ] Specs updated (§ Package Layout in all touched services)
- [ ] Ready for review
