# TASK-BE-456 — Order by-id read paths detach-safe + un-quarantine 3 orphaned integration tests

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (contained tx-boundary fix extending the TASK-BE-439 pattern + lift 3 orphaned quarantines; well-specified by the @Disabled root-cause)

**Service:** order-service (ecommerce)

> **Origin.** Discovery sweep (2026-06-28). TASK-MONO-307 (the ecommerce integration CI lane) quarantined several order-service `@Tag("integration")` tests with `@Disabled("TASK-BE-439: …LazyInitializationException…")`. TASK-BE-439 fixed the **two operational sweep queries** and (per its AC-2) lifted only `OrderStuckRecoveryIT` + `ConfirmPaidStaleIT`. Three other tests named on the **same** quarantine — `OrderPlacementIntegrationTest`, `OrderOptimisticLockIntegrationTest`, `OrderEventPublishIntegrationTest.placeOrder_commitSuccess_dbAndOutboxConsistent` — fell outside BE-439's declared scope and stayed `@Disabled` against a now-**closed** task. They remain unrun on the live CI lane.

---

## Goal

The three quarantined tests fail for the **same** detached-mapping root cause BE-439 diagnosed, but on a path BE-439 did not fix: `OrderRepositoryImpl.findById(...)` (and the other by-id reads) do `jpaRepository.…().map(mapper::toDomain)` with **no** `@Transactional` boundary, and `OrderJpaMapper.toDomain` eagerly reads the **LAZY** `OrderJpaEntity.items`. In a request these run inside the service-layer `@Transactional` so the session is open; but the three ITs call `orderRepository.findById(orderId)` **directly** (outside any tx — `OrderPlacementIntegrationTest:90`, `OrderEventPublishIntegrationTest:185`, `OrderOptimisticLockIntegrationTest:137,185`), so the entity is detached and `getItems()` throws `LazyInitializationException`. (`OrderOptimisticLockIntegrationTest`'s third method `optimisticLockConflict_returns409` does no read-back — that's the "2 of 3 concurrency tests" in the quarantine note; the class-level `@Disabled` collaterally disabled it too.)

Make the by-id read paths detach-safe and lift the three quarantines so the saga-critical invariants they assert (placement, outbox atomicity, optimistic-lock concurrency) actually run on the ecommerce integration lane.

## Scope

**In scope (order-service only):**

1. `OrderRepositoryImpl` — add `@Transactional(readOnly = true)` to the by-id / direct-list reads that `.map(mapper::toDomain)`: `findById`, `findByUserIdAndIdempotencyKey`, `findByIdForAdmin`, `findByIdAcrossTenants`, `findByUserIdAndStatusIn`, `findAllByUserIdAcrossTenants`. A single-row by-id read has **no** `Pageable`, so (unlike the BE-439 sweeps) no two-step id-then-fetch / `LEFT JOIN FETCH` is needed to avoid the HHH90003004 in-memory-pagination warning — the read-only tx spanning query + `toDomain` is the minimal, uniform fix. Propagation `REQUIRED` joins the request tx when present and opens a read session when called standalone (defense-in-depth; the production request paths were already safe).
2. Lift the orphaned TASK-MONO-307 quarantine: remove `@Disabled` (+ the now-unused `import org.junit.jupiter.api.Disabled`) from `OrderPlacementIntegrationTest` (class), `OrderOptimisticLockIntegrationTest` (class), and `OrderEventPublishIntegrationTest.placeOrder_commitSuccess_dbAndOutboxConsistent` (method).

**Out of scope:** the paged reads (`findByUserId`/`findAll`/`findByStatus`/`findAllWithItems`/`findByStatusWithItems`) — they map inside `toPageResult` and are only ever called from request-scoped `@Transactional` service methods; the paged `WithItems` queries already fetch-join. The other 11 ecommerce services' IT phases (TASK-MONO-307 follow-ups). Any behaviour change to the success-path request reads.

## Acceptance Criteria

- **AC-1** — `orderRepository.findById(...)` called outside a request tx loads + maps an order (incl. its `items`) **without** `LazyInitializationException`.
- **AC-2** — `OrderPlacementIntegrationTest`, `OrderOptimisticLockIntegrationTest` (all 3 methods), and `OrderEventPublishIntegrationTest.placeOrder_commitSuccess_dbAndOutboxConsistent` are un-`@Disabled` and GREEN on the ecommerce integration lane (Integration (ecommerce, Testcontainers)).
- **AC-3** — Docker-free `:order-service:compileJava` + `:compileTestJava` clean (verified locally); unit baseline unchanged.
- **AC-4** — no `@Disabled("TASK-BE-439 …")` remains in order-service test sources (the BE-439 quarantine is fully lifted across BE-439 + BE-456).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` — persistence / saga section.
- TASK-BE-439 — the sibling fix for the sweep queries; this task extends its detached-mapping pattern to the by-id reads.

## Related Contracts

- None (internal persistence/tx fix; no event or API contract change).

## Dependencies / Prior Work

- **TASK-BE-439** — fixed the two sweep queries + lifted `OrderStuckRecoveryIT`/`ConfirmPaidStaleIT`; this task lifts the remaining three it left quarantined.
- **TASK-MONO-307** — the ecommerce integration lane that surfaced + holds the quarantine.

## Edge Cases / Failure Scenarios

- **Order with items** — the ITs place a real order (items present), so the fix must initialise the collection inside the read session (the read-only tx does).
- **Nested tx** — the by-id reads are also called from service methods already in `@Transactional`; propagation `REQUIRED` joins the existing tx (no new tx, no behaviour change on the request path).
- **Tenant resolution** — `findById` is tenant-scoped; the ITs place + read in the same ambient tenant, so the row resolves (the documented failure was LazyInit, not a tenant miss). If CI reveals a tenant mismatch instead, STOP — the quarantine masked a second cause.

## Definition of Done

- [x] `OrderRepositoryImpl` by-id/direct-list reads `@Transactional(readOnly = true)`; 3 tests un-quarantined + imports cleaned.
- [x] Local `:order-service:compileJava`/`:compileTestJava` clean.
- [ ] commit + push (branch `task/be-456-order-it-unquarantine-byid-detach-safe`) + PR + CI GREEN (esp. **Integration (ecommerce, Testcontainers)**) + merge (3-dim verify).
