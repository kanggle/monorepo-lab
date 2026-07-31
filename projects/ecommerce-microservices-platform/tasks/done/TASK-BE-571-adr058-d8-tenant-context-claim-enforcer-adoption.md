# Task ID

TASK-BE-571

# Title

ADR-MONO-058 D8 — reconcile ecommerce's 10 `TenantContext`/`TenantContextFilter` copies against the already-shared `libs/java-security-servlet.TenantClaimEnforcer`

# Status

done

# Owner

backend

# Task Tags

- code
- security
- design-decision

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

`ADR-MONO-058` § 2 D8 states ecommerce is the one project that never adopted
`libs/java-security-servlet.TenantClaimEnforcer` (already shared fleet-wide per
`ADR-MONO-049`, "replacing thirteen hand-maintained copies") and instead grew 10 local
`TenantContext`/`TenantContextFilter` copies (`order`, `payment`, `product`,
`promotion`, `review`, `search`, `settlement`, `shipping`, `user`, `notification` —
count confirmed by grep, matches the ADR's audit exactly). The ADR frames this as
"not a new decision — closing ecommerce's gap against a decision already made."

**This task's own investigation found that framing does not fully hold, and the
mismatch must be resolved as an explicit design decision before any code moves — not
worked around silently.** See "Why this is not a mechanical swap" below.

## TASK-BE-557 status (required check per this task's filing instructions)

The ADR's own D8 text names `TASK-BE-557` as "a separate live-defect fix, filed
alongside the ADR" for `settlement-service`'s `.trim()` divergence, and says it
"should land before or alongside this migration so it doesn't have to reconcile the
divergence mid-flight." **`TASK-BE-557` does not exist as a task file in this
project** (checked `ready/`, `review/`, `done/`, and all other states — zero matches
for the literal ID). However, `tasks/done/TASK-BE-564-settlement-tenantcontext-trim-divergence-fix.md`
(DONE, 2026-07-30, 3-dim verified, impl PR #3081) is — almost word for word — the
same live defect the ADR describes: *"settlement-service's copy has ALREADY DIVERGED
(silently `.trim()`s the tenant id where the other 9 don't)."* This is evidently the
same piece of work the ADR's author intended when writing "`TASK-BE-557`," filed and
merged under a different ID. **Treat `TASK-BE-564` as satisfying the ADR's stated
prerequisite** — the divergence-fix has already landed, so this migration is not
blocked on it. Reference `TASK-BE-564` (not the phantom `TASK-BE-557`) in any future
document that needs to cite this precedent.

## Why this is not a mechanical swap

`TenantClaimEnforcer` (`libs/java-security-servlet/src/main/java/com/example/security/servlet/TenantClaimEnforcer.java`)
is a **request-rejection filter**: it reads the `tenant_id` claim from a
`JwtAuthenticationToken` already sitting in `SecurityContextHolder` (i.e. it assumes
the service has *already* run its own Spring Security OAuth2 Resource Server JWT
decode/authentication for the request) and either lets the request through or
responds 401/403. It does **not** expose the resolved tenant id to callers, and it
does **not** provide any propagation mechanism (no `ThreadLocal`, no
`currentTenant()`-equivalent) for the persistence/event layers to consume.

ecommerce's `TenantContext`/`TenantContextFilter` pair does two different jobs that
`TenantClaimEnforcer` does not combine:

1. **Binding/propagation** — `TenantContextFilter` reads the gateway-forwarded
   `X-Tenant-Id` **header** (not a locally-decoded JWT claim) into a plain
   `ThreadLocal` (`TenantContext`), which the persistence layer's
   `WHERE tenant_id = ...` filtering and the event-publishing layer both consume via
   `TenantContext.currentTenant()`.
2. **Net-zero default** — `TenantContext.currentTenant()` falls back to
   `DEFAULT_TENANT_ID` ("ecommerce") when no context is bound (standalone deployment,
   background thread, unit test) — this is `ADR-MONO-030` D8's own net-zero
   requirement, preserving pre-multi-tenant single-store behavior. `TenantClaimEnforcer`
   has no equivalent fallback concept; it is purely a gate.

Critically, `order-service`'s own `TenantContextFilter` javadoc states explicitly:
*"order-service trusts gateway-derived identity headers (`X-User-Id`, `X-User-Role`);
it runs **no Spring Security filter chain**."* This is not an order-service-specific
detail — it reflects ecommerce's whole current security model (per `PROJECT.md`'s
Service Map, only `gateway-service` does RS256 JWT verification; the 10 downstream
services trust gateway-enriched headers, matching `ADR-MONO-058`'s own D4 table, which
does **not** list ecommerce among D4's "security-chain assembly" adopters — `scm, erp,
wms, fan` only). `TenantClaimEnforcer` requires the *opposite* model: local JWT
decoding via a Resource Server filter chain feeding `SecurityContextHolder`. Ecommerce
services do not have that today, and standing one up is D1/D4-shaped work that this
ADR's own § 1.1 table did **not** confirm as applicable to ecommerce.

Adopting `TenantClaimEnforcer` as literally written would therefore require either:

- **(a)** introducing a local Spring Security OAuth2 Resource Server JWT chain into
  all 10 ecommerce services first (a materially larger, higher-risk architecture
  change than "swap a filter," and out of this ADR's confirmed scope for ecommerce —
  D4 was not confirmed applicable here), or
- **(b)** `TenantClaimEnforcer` gaining a header-based (not JWT-claim-based) mode,
  which the shared class does not offer today and would itself be a `libs/`-level
  change requiring its own review under `platform/shared-library-policy.md`
  (out of a project-internal task's authority to decide unilaterally), or
- **(c)** treating D8 for ecommerce as deferred until an equivalent to D4 lands for
  this project, keeping `TenantContext`/`TenantContextFilter` as-is in the meantime.

**This task's Acceptance Criteria require the implementer to make and document this
choice explicitly** (most likely (c), given (a) and (b) are each their own
significant undertaking outside this task's confirmed D8 scope) — not to silently
no-op the filter swap while reporting "done," and not to force (a) or (b) through
without a review this task alone cannot authorize.

---

# Scope

## In Scope

- Investigate and **document** (in this task's implementation notes / a short design
  note referenced from here) which of options (a)/(b)/(c) above — or another option
  the implementer identifies — is the right resolution for ecommerce, with the
  reasoning that led there.
- If, after investigation, a genuine like-for-like adoption path is found that this
  task's author did not identify (e.g. `TenantClaimEnforcer` already has or gains a
  header-trust mode by the time this task is picked up — re-check current code, not
  this task's snapshot), implement it across all 10 services.
- If the resolution is "defer" (option (c), the most likely outcome per this task's
  own analysis): explicitly record that decision (a short note in
  `specs/services/*/architecture.md`'s Multi-Tenancy section, or a dedicated
  `docs/adr/` addendum if the scale of the finding warrants one — judgment call for
  the implementer) so this gap does not silently re-surface as "still not done" in a
  future audit without context on *why*.
- Regardless of outcome: verify `TASK-BE-564`'s fix (settlement's `.trim()` removal)
  is still in place and has not regressed (quick sanity check, not a re-implementation).

## Out of Scope

- Implementing option (a) (standing up local JWT Resource Server chains in all 10
  ecommerce services) as part of this task — that is D1/D4-shaped work, not
  confirmed in ADR-MONO-058's scope for ecommerce, and would need its own ADR-level
  review given its security-boundary blast radius (`ADR-MONO-058 § 4`: "D1 and D4
  both touch authentication/authorization-adjacent code across every servlet service
  in the fleet — the highest-risk category of change in this repo").
- Implementing option (b) (extending `TenantClaimEnforcer`'s shared-library API) as
  part of this task — a `libs/java-security-servlet` change requires its own
  `platform/shared-library-policy.md § Review Rule` pass and is a monorepo-level
  task per `CLAUDE.md`'s shared-vs-project boundary, not a project-internal one.
- Any change to `TASK-BE-564`'s already-landed fix.
- Any change to the gateway's own `TenantClaimValidator`/`JwtHeaderMapping` (already
  investigated and left alone by `TASK-BE-564`'s own scope).

---

# Acceptance Criteria

- [ ] The (a)/(b)/(c) (or alternative) resolution is explicitly investigated and
      decided, with the reasoning documented — not defaulted to silently.
- [ ] If (c) "defer" is chosen: a clear, discoverable note exists (spec addendum or
      short ADR) stating that ecommerce's `TenantContext`/`TenantContextFilter`
      remain the project's tenant-propagation mechanism, why `TenantClaimEnforcer`
      does not fit today, and what would need to change (D4-equivalent work) before
      it could.
- [ ] `TASK-BE-564`'s settlement `.trim()` fix is confirmed still in place (verbatim
      binding, no regression) — a quick read of
      `settlement-service/.../TenantContext.java`'s `set()` method, not a
      re-implementation.
- [ ] If a genuine adoption path is found and implemented instead: all 10 services'
      `TenantContext`/`TenantContextFilter` behavior (including the
      `DEFAULT_TENANT_ID` net-zero fallback) is preserved end-to-end, and
      `./gradlew :projects:ecommerce-microservices-platform:apps:<service>:test` is
      GREEN for every touched service.
- [ ] This task does not close as "done, adopted" if the actual outcome is "deferred,
      documented why" — the task's own Definition of Done must accurately reflect
      which outcome occurred (do not mis-report a deferral as a completed adoption).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read
> `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and
> `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a
> Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2
  D8, § 6 item 3
- `docs/adr/ADR-MONO-049-*` (the prior fleet-wide `TenantClaimEnforcer`
  consolidation this ADR extends — read to understand what the shared class assumes
  about its adopters' security chains)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)
- `tasks/done/TASK-BE-564-settlement-tenantcontext-trim-divergence-fix.md` (the
  live-defect fix this task's Goal reconciles against the ADR's phantom
  `TASK-BE-557` reference)
- `specs/features/multi-tenancy-and-marketplace.md` § 2 (M1-M7 — the multi-tenant
  trait's authoritative source of truth for ecommerce's current tenant-isolation
  design, which `TenantContext`/`TenantContextFilter` implement)
- `rules/traits/multi-tenant.md`

---

# Related Contracts

- None directly — `TenantContext`/`TenantContextFilter` are internal request-scoped
  plumbing, not a published wire contract. If the resolution changes any observable
  error-response shape (e.g. `TenantClaimEnforcer`'s 401/403 body vs the current
  gateway-only enforcement), that would require a contract review — not expected
  under the most likely "defer" outcome.

---

# Target Service

- All 10: `order-service`, `payment-service`, `product-service`,
  `promotion-service`, `review-service`, `search-service`, `settlement-service`,
  `shipping-service`, `user-service`, `notification-service` — investigation scope;
  implementation scope depends entirely on which resolution option is chosen.

---

# Architecture

Follow, per touched service (if any code changes result):

- `specs/services/order-service/architecture.md`
- `specs/services/payment-service/architecture.md`
- `specs/services/product-service/architecture.md`
- `specs/services/promotion-service/architecture.md`
- `specs/services/review-service/architecture.md`
- `specs/services/search-service/architecture.md`
- `specs/services/settlement-service/architecture.md`
- `specs/services/shipping-service/architecture.md`
- `specs/services/user-service/architecture.md`
- `specs/services/notification-service/architecture.md`

---

# Implementation Notes

- Start by re-reading `TenantClaimEnforcer`'s class javadoc in full
  (`libs/java-security-servlet/.../TenantClaimEnforcer.java`) — it documents its own
  "every switch defaults closed" design and explicitly reads from
  `SecurityContextHolder.getContext().getAuthentication()` as a `JwtAuthenticationToken`.
  Confirm this task's finding (that ecommerce services don't populate that context
  today) against current code before proceeding — architecture can have shifted since
  this task was filed.
- If, on re-investigation, ecommerce's gateway model has changed (e.g. some service
  now does run a local Resource Server chain for another reason), that specific
  service might be a legitimate partial-adoption candidate even while the other 9
  are not — do not assume it is all-or-nothing across the 10 without checking each.
- `TASK-BE-564`'s own Goal section (read the full done task) contains a thorough
  analysis of ecommerce's current tenant-header trust chain (gateway
  `JwtHeaderMapping` → `X-Tenant-Id` header → service `TenantContextFilter`) that is
  directly relevant background for this task — do not re-derive it from scratch.

---

# Edge Cases

- If the "separate finding (report-only)" flagged in `TASK-BE-564` — "no service
  explicitly rejects a malformed `X-Tenant-Id`" — is still unaddressed, note whether
  this task's chosen resolution makes that gap better, worse, or unchanged (a
  `TenantClaimEnforcer`-based approach, if ever adopted, would add fail-closed
  rejection at the service layer that today's header-trust model lacks entirely —
  worth recording as a reason *for* eventually pursuing option (a)/(b), even while
  deferring it now).
- A future ADR that confirms D4-equivalent security-chain assembly for ecommerce
  would directly unblock this task's option (a) — if such an ADR exists or is filed
  before this task starts, re-evaluate the resolution against it rather than
  defaulting to (c) reflexively.

---

# Failure Scenarios

- Silently swapping `TenantContextFilter` for `TenantClaimEnforcer` without
  addressing the missing local JWT chain would mean the filter's
  `SecurityContextHolder` read always sees no `JwtAuthenticationToken`, degenerating
  to "chain.doFilter and continue" for every request per the class's own
  `if (!(auth instanceof JwtAuthenticationToken jwtAuth))` early-return — i.e. **the
  enforcement would silently no-op for 100% of ecommerce traffic** while looking
  adopted. This is the single most important failure mode this task must not produce.
- Reporting this task as "done, D8 adopted" when the actual outcome was "deferred,
  documented" would misrepresent the ADR's own tracking and could cause a future
  audit to believe ecommerce's D8 gap is closed when it is not.

---

# Test Requirements

- If code changes result (options (a)/(b) pursued, unlikely per this task's own
  analysis): full regression coverage per touched service, plus an explicit test
  proving `TenantClaimEnforcer` actually rejects a cross-tenant request in
  ecommerce's deployment topology (not just that it compiles) — directly guards
  against the silent-no-op failure scenario above.
- If deferred (option (c), most likely): no code test required; the "test" is that
  the documented resolution note exists and is discoverable (verify the file/section
  was actually added, not just described in this task).

---

# Definition of Done

- [ ] Resolution investigated and explicitly decided ((a)/(b)/(c)/other)
- [ ] Decision documented and discoverable (spec note or ADR addendum)
- [ ] `TASK-BE-564`'s fix confirmed intact
- [ ] If code changed: all 10 services' tests GREEN, net-zero default preserved,
      enforcement verified non-no-op
- [ ] Task's own Status/summary accurately reflects "adopted" vs "deferred" — no
      over-claiming
- [ ] Ready for review
