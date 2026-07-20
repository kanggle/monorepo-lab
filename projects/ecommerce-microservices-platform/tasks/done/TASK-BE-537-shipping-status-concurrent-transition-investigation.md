# TASK-BE-537 — Investigate whether concurrent `PUT /api/shippings/{id}/status` double-publishes and double-deducts WMS inventory

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (a reproduction test first; the fix, if warranted, is a small one)

> **This is an INVESTIGATION ticket, not a fix ticket.** The defect is not established. What is established is
> the *absence of one specific guard*. Do not open with "add `@Version`" — open by trying to make the failure
> happen. A negative result is a real deliverable here and should be recorded, not treated as a failed task.

---

## Goal

The ADR-002 D3 idempotency census (2026-07-20) classified `PUT /api/shippings/{shippingId}/status` as
NATURAL-KEY: a **sequential** replay is deterministically rejected by the domain state machine.
`ShippingStatus.java:13-21` defines `ALLOWED_TRANSITIONS` as a strictly linear
`PREPARING → SHIPPED → IN_TRANSIT → DELIVERED` map with **no self-transition entry**, so
`Shipping.transitionTo` (`Shipping.java:85-89`) throws `InvalidStatusTransitionException` on a repeated
`SHIPPED`. That classification is sound for sequential replay and was verified.

What was **not** verified is the concurrent case. Measured: `@Version` appears **zero** times anywhere in
`shipping-service`. That zero is real, not a broken pattern — the same search finds `@Version` in 10 files
elsewhere in the repo (`wms .../AsnJpaEntity.java`, `scm .../PurchaseOrder.java`,
`iam .../OperatorGroupJpaEntity.java`, among others).

So the handler is a read-modify-write inside one transaction with no optimistic lock. The *reasoned* failure
mode is: two simultaneous `SHIPPED` requests both read `PREPARING`, both pass `canTransitionTo`, both commit.
Because the WMS deduction publish is gated on `saved.getStatus()==SHIPPED && command.deductWmsInventory() &&
saved.isWmsRouted()` (`ShippingCommandService.java:105-113`), both would publish
`ManualShipConfirmRequested` — a double WMS inventory deduction.

**This has not been observed.** Whether the window is actually reachable depends on the transaction isolation
level in effect, lock behaviour of the surrounding reads, and commit timing — none of which were measured.
The purpose of this task is to find out, not to assume.

## Scope

**In scope:**

1. **Determine the effective isolation level and locking behaviour** on this path: what isolation the
   datasource/transaction actually runs at, and whether the entity read takes any lock (`PESSIMISTIC_WRITE`,
   a `SELECT … FOR UPDATE` behind the repository, a DB constraint) that would serialize the two writers.
2. **Attempt a reproduction**: drive two concurrent `PUT …/status` → `SHIPPED` against the same shipping and
   observe whether two `ManualShipConfirmRequested` messages are published. A Testcontainers-backed
   integration test against real Postgres is the authoritative surface — an H2/mock test can neither confirm
   nor refute isolation-dependent behaviour.
3. **If reproduced** — apply the smallest guard that closes it and prove the same test now fails to reproduce.
   `@Version` on `ShippingJpaEntity` is the obvious candidate, but a unique constraint on the published
   dedup key or a pessimistic read may fit better; choose with reasoning.
4. **If NOT reproduced** — record why (which mechanism actually serializes the writers) in the shipping-service
   `architecture.md` or the spec, so the next person auditing this does not re-run the same investigation. Then
   close the task as done-with-negative-result. **This is a success, not a failure.**

**Out of scope:**

- The six NONE-classified endpoints from the same census → `TASK-BE-535` (money) / `TASK-BE-536` (stock/coupon).
- The other 12 NATURAL-KEY endpoints. If this investigation shows the concurrent window is real, whether the
  *same* window exists on the other state-machine-guarded endpoints is a legitimate follow-up question — file
  it as a follow-up with its own population count rather than widening this task.
- Adding `@Version` broadly across shipping-service entities as a "consistency" sweep.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Do not inherit this ticket's reading. Re-verify at start:
  `@Version` still absent from `shipping-service` (sanity-check the pattern against `wms .../AsnJpaEntity.java`,
  a known positive, so a zero is proven to mean absence); `ALLOWED_TRANSITIONS` still has no self-transition;
  the WMS publish is still gated as described. If any has changed, **STOP and report**.
- **AC-1** — The effective transaction isolation level on this path is stated as a measured fact (where it is
  configured, what it resolves to at runtime), not inferred from defaults.
- **AC-2** — A concurrency test exists that drives two simultaneous `SHIPPED` transitions against one shipping,
  and its outcome is reported either way: reproduced (how many publishes observed) or not reproduced (and what
  serialized them).
- **AC-3** — The test runs against real Postgres via Testcontainers. Note that local Windows is not the
  authority for Testcontainers here (it is flaky on this host); **CI Linux is the authority**. If the test
  cannot run locally, say so and let CI decide.
- **AC-4** — If a guard is added, a test proves the double-publish no longer occurs AND a separate test proves
  a *legitimate* forward transition (`PREPARING → SHIPPED` once, then `SHIPPED → IN_TRANSIT`) still works.
- **AC-5** — If not reproduced, the finding is written down where the next auditor will encounter it, with the
  evidence for what serializes the writers.
- **AC-6** — `shipping-service` build + tests GREEN.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/shipping-service/architecture.md`
- `projects/ecommerce-microservices-platform/docs/adr/ADR-002-saga-over-distributed-transaction.md` § Decision-3
- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` (the fulfillment leg whose inventory this
  would double-deduct)

## Related Contracts

- `ManualShipConfirmRequested` — the message whose duplicate emission is the actual harm. The WMS-side consumer
  is the second place this could be defended; check whether it already dedupes before concluding the impact.
  **If the WMS consumer is idempotent, the blast radius is smaller than this ticket assumes — say so.**

## Edge Cases

- **The WMS consumer may already dedupe** — a duplicate publish that the consumer discards is a noise problem,
  not an inventory problem. Check the consumer before sizing the fix.
- **Retry-driven concurrency, not user-driven** — an at-least-once job or a gateway retry is a likelier source
  of two simultaneous requests than a double-clicking operator. That affects how tight the window needs to be
  to matter.
- **`@Version` changes failure mode, it does not remove the race** — an optimistic lock converts a silent
  double-write into an `OptimisticLockException` the caller must handle. Decide what the API returns then
  (409? retry?) rather than letting the exception escape as a 500.
- **Isolation level differences between local and CI** — a reproduction that only fails on one is still
  informative, but say which environment produced which result.

## Failure Scenarios

- **F1 — assuming the defect and fixing it without reproducing.** Adds a version column, a migration, and a new
  exception path to defend against something that may already be serialized. The guard against this is that
  AC-2 requires reporting the reproduction outcome before AC-4 permits a fix.
- **F2 — a green concurrency test that proves nothing.** Two "concurrent" requests that actually run
  sequentially (shared thread, transaction started before both, test container not really parallel) will pass
  and be read as "no race". State how genuine concurrency was achieved.
- **F3 — treating a negative result as a failed task.** If nothing reproduces, the deliverable is the recorded
  explanation. AC-5 exists so that outcome still lands something durable.
