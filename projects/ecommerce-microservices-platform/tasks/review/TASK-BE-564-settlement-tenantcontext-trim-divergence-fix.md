# Task ID

TASK-BE-564

# Title

Fix settlement-service `TenantContext` silent `.trim()` divergence from the other 9 ecommerce services

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

`TenantContext` is copied byte-identically across 10 ecommerce services (`order`,
`payment`, `product`, `promotion`, `review`, `search`, `settlement`, `shipping`,
`user`, `notification`). A monorepo-wide audit found that 9 of the 10 copies bind
the tenant verbatim — `CURRENT.set(tenantId)` — while `settlement-service`'s copy
silently diverged to `CURRENT.set(tenantId.trim())`
(`apps/settlement-service/src/main/java/com/example/settlement/domain/tenant/TenantContext.java`,
`set(String tenantId)`). A whitespace-padded `X-Tenant-Id` header therefore behaves
differently depending on which service handles the request — settlement silently
normalizes it, every sibling service carries the padding through verbatim into the
persistence-layer `WHERE tenant_id = :ctx.tenantId` filter.

After this task, `settlement-service`'s `TenantContext.set()` must bind the tenant
id **verbatim** (no `.trim()`), restoring byte-identical behavior with the other 9
copies.

## Behavior decision (this is not a mechanical revert)

Investigated whether the *other 9* services are the ones with the latent bug (i.e.
settlement's `.trim()` accidentally found a real defect) or whether settlement's
`.trim()` is the accidental divergence. Evidence gathered:

1. **No upstream layer normalizes or validates the `tenant_id` value before it
   reaches `TenantContext`.**
   - Gateway `JwtHeaderMapping.skipIfBlank("X-Tenant-Id", JwtClaims::tenantId)`
     (`libs/java-gateway/.../filter/JwtHeaderMapping.java`) only gates on
     `resolved != null && !resolved.isBlank()` — it forwards the raw JWT `tenant_id`
     claim value **verbatim**, un-trimmed, into the `X-Tenant-Id` header
     (confirmed by `JwtHeaderEnrichmentFilterTest`'s own
     `skipIfBlankAlsoSkipsBlank` case, which only asserts blank-skip, not trim).
   - Gateway `TenantClaimValidator`
     (`libs/java-security/.../oauth2/TenantClaimValidator.java`) only checks
     `tenantId.isBlank()` and exact-string `expectedTenantId.equals(tenantId)` (or
     `entitled_domains` list membership) — no trim, no format/regex validation.
     A padded claim that fails exact equality is rejected as `tenant_mismatch`
     (403) only in the strict legacy path; it is not sanitized anywhere.
   - `TenantContextFilter` (all 10 service copies, incl. settlement's
     `presentation/filter/TenantContextFilter.java`) does no validation of its
     own — it is a raw pass-through: `TenantContext.set(request.getHeader(...))`.
2. **No spec mandates trimming.** `specs/services/settlement-service/architecture.md`
   § Multi-Tenancy & Marketplace states read = `WHERE tenant_id = <요청 컨텍스트>`
   (gateway `X-Tenant-Id`) with no format-normalization clause.
   `specs/contracts/http/settlement-api.md` § Tenancy & Seller Scope and
   `rules/traits/multi-tenant.md` (M1-M7) likewise say nothing about trimming or
   accepting malformed tenant ids — M2's "service-level final guard" is a
   defense-in-depth *presence* check (does the request carry a valid, gateway-
   verified tenant), not a value-sanitization step.
3. **Conclusion: settlement's `.trim()` is the accidental divergence, not a fix.**
   Because nothing upstream guarantees a clean `tenant_id` and nothing downstream
   is supposed to sanitize one either, a whitespace-padded value is an
   **unvalidated/malformed input** everywhere in this pipeline today. The 9-copy
   majority behavior (verbatim passthrough) is consistent with today's
   fail-closed-by-non-match design: a padded value simply won't match any
   persisted `tenant_id` row, so it degrades safely to "no rows" rather than being
   silently coerced into a legitimate-looking tenant id. Settlement's `.trim()`
   instead **silently repairs** a malformed identity value before it reaches the
   persistence filter — masking exactly the kind of malformed-header case that a
   defense-in-depth layer should either pass through inertly (today's majority
   behavior) or explicitly reject, never quietly "fix". This task restores parity
   with the majority; it does **not** add explicit rejection (see Out of Scope).

## Separate finding (report-only, NOT this task's scope)

Neither the gateway nor any of the 10 service-level `TenantContextFilter` copies
**rejects** a malformed (e.g. whitespace-padded) `X-Tenant-Id`/`tenant_id` claim
outright — they either pass it through verbatim (9 services, post-fix: 10) or
(pre-fix) silently coerce it. Whether the fleet should add explicit format
validation (e.g. reject non-slug `tenant_id` with 400) is a **separate,
cross-cutting, 10-service-plus-gateway decision** that is out of scope here and is
flagged as a candidate backlog item, not silently implemented as part of this
single-service fix.

---

# Scope

## In Scope

- `apps/settlement-service/src/main/java/com/example/settlement/domain/tenant/TenantContext.java`
  — remove `.trim()` from `set(String tenantId)`, restoring verbatim binding
  (byte-identical to the other 9 service copies).
- A unit test in settlement-service asserting a whitespace-padded tenant id is
  bound **verbatim** (not trimmed) by `TenantContext.set()`/`currentTenant()`.

## Out of Scope

- Any change to the other 9 services' `TenantContext` copies (they are already
  correct / already the majority behavior).
- Any change to `TenantContextFilter` (any service) to add explicit
  malformed-header rejection — flagged above as a separate cross-cutting finding,
  not implemented here.
- Any change to gateway `JwtHeaderMapping` / `TenantClaimValidator` (shared
  library, out of this project-internal task's boundary regardless).
- Any change to persistence-layer tenant filtering behavior.

---

# Acceptance Criteria

- [ ] `settlement-service`'s `TenantContext.set(String tenantId)` binds the tenant
      id verbatim (no `.trim()`), matching the other 9 service copies
      byte-for-byte in behavior.
- [ ] A new/updated unit test asserts: `TenantContext.set(" tenant-a ")` (or
      equivalent leading/trailing-whitespace-padded value) followed by
      `TenantContext.currentTenant()` returns the value **unchanged** (with the
      whitespace preserved), not a trimmed value.
- [ ] Existing settlement-service tests (`CommissionRateAdminServiceTest` and any
      other consumer of `TenantContext.set`) remain green — none of them depend on
      the removed `.trim()` behavior (verified by inspection: all existing
      call-sites pass already-clean literals like `"tenantA"`).
- [ ] `./gradlew :projects:ecommerce-microservices-platform:apps:settlement-service:test`
      is GREEN.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `rules/traits/multi-tenant.md` (M1 — row-level `tenant_id`; M2 — 3-layer
  isolation; M3 — 404-over-403)
- `specs/services/settlement-service/architecture.md` § Multi-Tenancy & Marketplace
- `specs/features/multi-tenancy-and-marketplace.md`

# Related Skills

- `.claude/skills/backend/...` (standard backend implementation + unit-test skills)

---

# Related Contracts

- `specs/contracts/http/settlement-api.md` § Tenancy & Seller Scope

---

# Target Service

- `settlement-service`

---

# Architecture

Follow:

- `specs/services/settlement-service/architecture.md`

---

# Implementation Notes

- Single-file domain-layer change: delete `.trim()` in
  `TenantContext.set(String tenantId)`'s else-branch (`CURRENT.set(tenantId.trim());`
  → `CURRENT.set(tenantId);`). No other line changes required — the blank/null
  clear-to-default branch and `currentTenant()` are already byte-identical to the
  other 9 copies.
- Do not touch `TenantContextFilter` or any other class in this task — scope is the
  one divergent line in `TenantContext.java`.
- Place the new/updated unit test alongside the existing
  `apps/settlement-service/src/test/java/com/example/settlement/domain/...` test
  layout (mirror `payment-service`'s
  `domain/tenant/TenantContextTest.java` shape — settlement currently has no
  `TenantContextTest`, so this task adds one modeled on that sibling).

---

# Edge Cases

- Whitespace-only tenant id (e.g. `"   "`) — unaffected, already handled by the
  `isBlank()` clear-to-default branch (unchanged).
- Leading-only or trailing-only whitespace padding (e.g. `"tenant-a "`,
  `" tenant-a"`) — must now be preserved verbatim, not trimmed.
- Internal whitespace inside an otherwise valid-looking tenant id (e.g.
  `"tenant a"`) — out of this task's scope (not blank, passes through verbatim
  either way; no change in behavior).

---

# Failure Scenarios

- If `.trim()` is left in place: settlement-service continues to silently accept
  a padded `X-Tenant-Id` that every sibling service would treat as a
  non-matching (fail-closed) value — the exact cross-service behavioral
  divergence this task closes.
- If the fix is applied without the verbatim-preservation unit test: a future
  refactor could silently reintroduce `.trim()` (or an equivalent normalization)
  with no regression signal.

---

# Test Requirements

- unit test: `TenantContext` `set()`/`currentTenant()` for a whitespace-padded
  tenant id — assert verbatim (untrimmed) storage/retrieval.
- No integration or contract test change required — this is a pure domain-layer
  in-memory `ThreadLocal` holder with no persistence/HTTP surface of its own.

---

# Definition of Done

- [ ] Implementation completed (`.trim()` removed)
- [ ] Tests added (verbatim-preservation unit test)
- [ ] Tests passing (`./gradlew :projects:ecommerce-microservices-platform:apps:settlement-service:test` GREEN)
- [ ] Contracts updated if needed — N/A, no contract change
- [ ] Specs updated first if required — N/A, no architecture/behavior-contract change (aligns to already-documented majority behavior)
- [ ] Ready for review
