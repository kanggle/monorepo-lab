# TASK-BE-439 — Order sweeper/read paths map detached entities outside a transaction → LazyInitializationException

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (contained tx-boundary / fetch-strategy fix + un-quarantine ITs; well-specified by the surfacing run)

**Service:** order-service

> **Origin.** Surfaced by **TASK-MONO-307** (ecommerce integration CI lane) — the first-ever CI execution of the order `@Tag("integration")` suite. The stuck-detector and confirm-paid-stale ITs failed with `LazyInitializationException`. They are **quarantined** with `@Disabled("TASK-BE-439: …")` in `OrderStuckRecoveryIT` (class) + `ConfirmPaidStaleIT` (`noBearer_returns401`, `validBearer_confirmsOnlyPaidUnconfirmed`); this task fixes the bug and lifts the quarantine.

---

## Goal

`OrderRepositoryImpl.findStuckPaymentPending(...)` and `findStalePaidUnconfirmed(...)` run their Spring-Data JPA query, then `.stream().map(mapper::toDomain)` the results. `OrderJpaMapper.toDomain` (`OrderJpaMapper.java:15`) eagerly reads the **lazy** `entity.getItems()`. Neither `OrderRepositoryImpl` (no class-level `@Transactional`) nor the callers — `OrderStuckDetector.sweep()` and the confirm-paid-stale endpoint's sweep — hold an open persistence session when `.map(toDomain)` runs, so the entities are **detached** and `getItems()` throws `org.hibernate.LazyInitializationException: failed to lazily initialize a collection of role: OrderJpaEntity.items: could not initialize proxy - no Session`.

This is a **latent production defect**: `OrderStuckDetector.sweep()` wraps `findStuckPaymentPending` in `try { … } catch (Exception e) { log.error("…findStuck_failed…"); return; }` — so in production the sweeper would **silently swallow the exception on every tick and never recover any stuck order** (no alert, no metric beyond the error log). The confirm-paid-stale internal endpoint is similarly broken. The bug was never caught because the unit tests mock the repository (returning domain `Order`s directly), and the ITs that exercise the real Postgres path never ran until TASK-MONO-307.

## Scope

**In scope (order-service only):**

1. Fix the detached-entity lazy mapping for the two operational sweep queries. Choose the cleanest correct option (decide + document):
   - a **fetch join** (`LEFT JOIN FETCH o.items`) on `findStuckPaymentPending` / `findStalePaidUnconfirmed` so `items` is initialised inside the query, **or**
   - a read transaction boundary (`@Transactional(readOnly = true)`) spanning the query + mapping (on the repository method or the sweep/endpoint service method) so the session stays open through `toDomain`, **or**
   - have `toDomain` not touch `items` for these projection paths (a lighter mapping) if items are unused downstream.
   Prefer the option that is correct for **both** call sites (the sweeper recovery loop and the confirm-paid-stale endpoint) and consistent with the codebase's existing tx conventions.
2. Audit the other `OrderRepositoryImpl` methods that `.map(mapper::toDomain)` for the same detached-mapping hazard (e.g. `toPageResult`, list queries) — they currently work only because they run inside a request `@Transactional`; document why each is safe or fix it.
3. **Lift the TASK-MONO-307 quarantine**: remove `@Disabled` from `OrderStuckRecoveryIT` (class) and the two `ConfirmPaidStaleIT` methods; they must pass on the ecommerce integration lane.

**Out of scope:** the payment-side OrderCancelled-consumer transaction bug (**TASK-BE-440**); the other 11 ecommerce services' IT phases (TASK-MONO-307 follow-ups); singleton-container harness tuning unless a resource issue blocks verification.

## Acceptance Criteria

- **AC-1** — `OrderStuckDetector.sweep()` (via `findStuckPaymentPending`) and the confirm-paid-stale sweep (via `findStalePaidUnconfirmed`) load + map orders **without** `LazyInitializationException`, proven by the previously-quarantined ITs passing on the ecommerce integration lane (TASK-MONO-307). A stuck PENDING order with **no items** (the IT's direct-INSERT seed) maps cleanly.
- **AC-2** — `OrderStuckRecoveryIT` (all 4 tests) + `ConfirmPaidStaleIT` `noBearer_returns401` + `validBearer_confirmsOnlyPaidUnconfirmed` are un-`@Disabled` and GREEN on the integration lane.
- **AC-3** — Docker-free `:order-service:check` stays green (unit baseline unchanged); the fix does not alter the success-path behaviour of the request-scoped repository reads.
- **AC-4** — A regression note or test asserts the sweeper recovers a stuck order end-to-end (the bug previously made every recovery a silent no-op).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` — stuck-detector / saga section (ADR-MONO-005 § D3).
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` § 2.6 D6 (ecommerce order saga).

## Related Contracts

- None (internal persistence/tx fix; no event or API contract change).

## Dependencies / Prior Work

- **TASK-MONO-307** — the integration lane that surfaced this; holds the quarantine. This task lifts it.
- **TASK-BE-138** — authored `OrderStuckDetector` + `findStuckPaymentPending`.
- **TASK-BE-412** — authored the confirm-paid-stale endpoint + `findStalePaidUnconfirmed`.

## Edge Cases

- **Order with no items** — the IT seeds orders via direct `INSERT` with an empty `items` collection; the lazy proxy still throws on access. The fix must handle empty collections (a fetch join returns the order with an empty list; a tx boundary keeps the session open).
- **Batch size / N+1** — a `JOIN FETCH` with `Pageable` can trigger in-memory pagination warnings; if chosen, verify Hibernate does not warn/regress (consider `@EntityGraph` or a two-step id-then-fetch). Document the choice.
- **Tenant-agnostic sweep** — the operational queries are intentionally cross-tenant; the fix must not introduce a tenant filter.

## Failure Scenarios

- **F1 — silent non-recovery (the production impact)** — the current `catch(Exception){return;}` hides the lazy-init so the sweeper appears healthy while recovering nothing. Mitigation: AC-1/AC-4 prove real recovery; consider tightening the catch to not swallow programming errors.
- **F2 — fixing only one call site** — the bug is in the shared `toDomain` mapping; both `findStuckPaymentPending` and `findStalePaidUnconfirmed` (and any other non-tx mapping path) must be addressed (AC-1, scope item 2).
