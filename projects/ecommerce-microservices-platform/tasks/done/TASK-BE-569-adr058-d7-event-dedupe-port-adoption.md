# Task ID

TASK-BE-569

# Title

ADR-MONO-058 D7 (EventDedupePort) — adopt `libs/java-messaging.EventDedupePort` in place of hand-rolled consumer-side dedupe (order, product, shipping, settlement)

# Status

done

# Owner

backend

# Task Tags

- code
- refactor
- idempotency

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

`ADR-MONO-058` § 2 D7 found `libs/java-messaging.EventDedupePort` already exists and
`.claude/skills/messaging/idempotent-consumer/SKILL.md` already instructs "use the
shared port — do not hand-roll," ignored by roughly 9 services fleet-wide across wms,
erp, ecommerce, fan. Grepping ecommerce's consumer-facing services confirms multiple
hand-rolled, non-conforming dedupe implementations:

| Service | Hand-rolled class(es) | Mechanism |
|---|---|---|
| `product-service` | `ReservationEventDedupe`, `WmsReconciliationDedupe` | `ReservationProcessedEventJpaRepository`/`WmsProcessedEventEntity`, `isDuplicate(UUID, String)` returning `boolean` |
| `order-service` | `ProcessedEventJpaEntity` + ad-hoc consumer-level checks | own `ProcessedEventJpaEntity` shape |
| `shipping-service` | `ProcessedEventJpaEntity` + consumer-level checks (`WmsShippingConfirmedConsumer`, `WmsOutboundCancelledConsumer`) | own `ProcessedEventJpaEntity` shape |
| `settlement-service` | `ProcessedEventStoreImpl` implementing its own `ProcessedEventStore` domain port + `ProcessedEventJpaEntity` | closest in *shape* to `EventDedupePort` (already has an interface/impl split) but the interface is service-local, not the shared one |

None of the four implements `com.example.messaging.dedupe.EventDedupePort` — each
defines its own method signature and semantics instead of the shared
`Outcome process(UUID eventId, String eventType, Runnable work)` contract.
`wms-platform` already has three live adopters (`inventory-service`,
`outbound-service`, `inbound-service`, each with an `EventDedupeRepositoryImpl`) —
use those as the concrete adoption reference.

After this task, all four services' consumer-side dedupe goes through
`EventDedupePort`, with a per-service JPA-backed adapter implementing it (the
interface is shared, per-service persistence stays per-service, per the port's own
javadoc: "the persistence implementation lives in each service's adapter layer
because the dedupe table's retention policy and tenant scoping is service-specific").

---

# Scope

## In Scope

- `product-service`: implement `EventDedupePort` via an adapter wrapping the existing
  `ReservationProcessedEventJpaRepository`/`WmsProcessedEventEntity` persistence,
  replacing `ReservationEventDedupe`/`WmsReconciliationDedupe`'s bespoke
  `isDuplicate(UUID, String): boolean` call sites in `PaymentCompletedReservationConsumer`,
  `OrderPlacedReservationConsumer`, `OrderCancelledReservationConsumer`,
  `WmsMasterSkuConsumer`, `WmsInventoryReconciliationConsumer` with
  `EventDedupePort#process(eventId, eventType, work)`.
- `order-service`: implement `EventDedupePort` via an adapter over the existing
  `ProcessedEventJpaEntity` table; migrate `WmsOutboundCancelledConsumer`,
  `OrderReservationFailedConsumer` (and any other consumer currently doing its own
  processed-event check) to the shared port.
- `shipping-service`: implement `EventDedupePort` via an adapter over its
  `ProcessedEventJpaEntity` table; migrate `WmsShippingConfirmedConsumer`,
  `WmsOutboundCancelledConsumer`.
- `settlement-service`: replace the service-local `ProcessedEventStore` domain port +
  `ProcessedEventStoreImpl` with `EventDedupePort` directly (or have
  `ProcessedEventStore` become a thin service-specific facade delegating to
  `EventDedupePort` if `SettlementService`'s call sites rely on
  `ProcessedEventStore`'s specific method names — evaluate at implementation time
  which is less churn); migrate `PaymentRefundedReversalConsumer`,
  `PaymentCompletedAccrualConsumer`, `OrderPlacedSnapshotConsumer`.
- Add `libs/java-messaging` as a `build.gradle` dependency to each of the 4 services
  where not already present.
- Preserve each service's existing `@Transactional(propagation = Propagation.MANDATORY)`
  atomicity guarantee (dedupe-row insert + business-side-effect commit together) —
  this is the entire point of the pattern (see `product-service`'s
  `ReservationEventDedupe` javadoc for why `MANDATORY` matters here) and must not
  regress during the swap.

## Out of Scope

- `payment-service`, `notification-service`, `review-service`, `search-service`,
  `user-service`, `promotion-service` — grepped for `ProcessedEvent`/`Idempotency`/
  `dedup` patterns; none showed a confirmed hand-rolled consumer-side event dedupe
  table (payment-service's `IdempotencyKey*` classes are REST-request-level
  idempotency, a different concept from consumer-side event dedupe — out of scope).
  If a service in this "out of scope" list turns out to consume Kafka events with no
  dedupe at all, that is a **different, more serious gap** (missing dedupe entirely,
  not hand-rolled-instead-of-shared) — flag it as a new finding, do not silently fold
  it into this task's scope.
- `gateway-service`, `batch-worker`, `web-store` — not event consumers of this shape.
- Any change to `libs/java-messaging.EventDedupePort` itself, or to the interface's
  contract/method signature.
- `wms-platform`'s existing `EventDedupeRepositoryImpl` adopters — reference only, not
  touched by this task (different project).

---

# Acceptance Criteria

- [x] `product-service`, `order-service`, `shipping-service`, `settlement-service`
      each have at least one adapter class implementing
      `com.example.messaging.dedupe.EventDedupePort`.
      Evidence: `ReservationEventDedupe` + `WmsReconciliationDedupe` (product),
      `EventDeduplicationChecker` (order), `EventDeduplicationChecker` (shipping),
      `SettlementEventDedupe` (settlement, new class — replaces the deleted
      `ProcessedEventStoreImpl`).
- [x] All named consumer classes in Scope are migrated to call
      `EventDedupePort#process` instead of their prior bespoke dedupe check.
      Evidence: repo-wide grep for `.isDuplicate(` under the 4 services' `src/main`
      returns zero hits; product 5 consumers, order 8 consumers (all
      `EventDeduplicationChecker` call sites, not just the 2 named in Scope — it is
      one shared bean), shipping 3 consumers, settlement 3 consumers — 19 consumers
      total, all migrated.
- [x] `MANDATORY` transaction propagation (dedupe row + side effect committing
      atomically) is preserved for every migrated consumer — verify by inspection
      that no consumer's dedupe check moved outside the enclosing `@Transactional`
      boundary.
      Evidence: all 5 adapter `process(...)` methods keep
      `@Transactional(propagation = Propagation.MANDATORY)`; every consumer's
      post-dedupe business logic (including shipping-service's outbox-writing
      `publishFulfillmentRequested` call in `OrderConfirmedEventConsumer`) moved
      into the `Runnable` passed to `process(...)`, i.e. still inside the
      `@Transactional` method that was already there — no consumer's transaction
      boundary changed shape.
- [x] Existing consumer tests for all migrated classes remain GREEN
      (`WmsShippingConfirmedConsumerTest`, `WmsOutboundCancelledConsumerTest`,
      `ProductDuplicateRequestGuardIntegrationTest`,
      `ReservationConsumersTest`/`WmsReconciliationConsumersTest`, and
      `settlement-service`'s consumer tests) with duplicate-delivery scenarios still
      asserting exactly-once side effects.
      Evidence: local `:test` run per service — product-service 375/375, order-service
      387/387, shipping-service 195/195, settlement-service 145/145, 0 failures
      (Testcontainers-backed `ProductDuplicateRequestGuardIntegrationTest` did not run
      locally — Windows Docker Testcontainers flake per
      `project_testcontainers_docker_desktop_blocker`; the CI `Integration` lane is
      authoritative for that one, everything else above is a plain `:test` unit run).
- [x] `settlement-service`'s existing `ProcessedEventStore` domain-port abstraction is
      either removed in favor of direct `EventDedupePort` use, or reduced to a
      documented thin facade — not left as a second, now-redundant, parallel
      abstraction alongside `EventDedupePort`.
      Evidence: `ProcessedEventStore.java` and `ProcessedEventStoreImpl.java` deleted;
      all 3 consumers depend on `EventDedupePort` directly, backed by the new
      `SettlementEventDedupe` adapter over the same `processed_event` table.
- [x] `./gradlew :projects:ecommerce-microservices-platform:apps:<service>:test`
      GREEN for all 4 touched services.
      Evidence: see counts above — all 4 GREEN, 0 failures.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read
> `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and
> `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a
> Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2
  D7, § 6 item 2
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)
- `.claude/skills/messaging/idempotent-consumer/SKILL.md` (the existing instruction
  this task is closing the adoption gap against)
- `rules/traits/transactional.md` § T8 (authoritative reference cited by
  `EventDedupePort`'s own javadoc)

---

# Related Contracts

- None directly — this is internal consumer-side idempotency plumbing, not a
  published API/event contract change. If any `specs/contracts/events/*.md` document
  currently describes a service's dedupe mechanism by name (unlikely but check),
  update it to reflect the new adapter.

---

# Target Service

- `product-service`, `order-service`, `shipping-service`, `settlement-service`

---

# Architecture

Follow, per touched service:

- `specs/services/product-service/architecture.md`
- `specs/services/order-service/architecture.md`
- `specs/services/shipping-service/architecture.md`
- `specs/services/settlement-service/architecture.md`

---

# Implementation Notes

- **Re-verified at implementation time (correction to the Goal's framing above):**
  `wms-platform`'s `inventory-service`/`outbound-service`/`inbound-service`
  `EventDedupeRepositoryImpl` classes are **not yet** adopters of the *shared*
  `com.example.messaging.dedupe.EventDedupePort` — each still implements its own
  **service-local** `application/port/out/EventDedupePort` interface (byte-identical
  in contract shape, but a separate type). wms-platform's own migration to the shared
  port is tracked separately (`projects/wms-platform/tasks/ready/TASK-BE-571-...md`,
  still `ready` as of this task's implementation). The wms classes were still used as
  the concrete **adapter shape reference** (JPA entity + `INSERT ... ON CONFLICT
  (event_id) DO NOTHING` + `MANDATORY` propagation) — that persistence pattern is
  sound and reusable regardless of which interface the class implements — but this
  task's four adapters implement the shared interface directly (no intermediate
  service-local duplicate), since none of the four target services had a pre-existing
  local port to convert (unlike wms's TASK-BE-571 case, which is a pure interface
  swap over an already-identical local port).
  `wms-platform/apps/inventory-service/src/main/java/com/wms/inventory/adapter/out/persistence/dedupe/EventDedupeRepositoryImpl.java`
  (and its `outbound-service`/`inbound-service` siblings) — read one as the concrete
  adapter shape before implementing ecommerce's four, rather than designing the
  adapter from the interface alone.
- `EventDedupePort#process` takes a `Runnable work` and returns an `Outcome` enum
  (`APPLIED`/`IGNORED_DUPLICATE`/`FAILED`) — this is a different call shape from
  `product-service`'s current `isDuplicate(UUID, String): boolean` (check-then-act,
  two steps) or `order-service`/`shipping-service`'s ad-hoc inline checks. Migrating
  call sites means restructuring "check duplicate → if not, do work → save dedupe
  row" into "call `process(id, type, () -> { do work })`" — the side-effecting body
  moves inside the lambda. Audit each consumer's current control flow before
  mechanically wrapping, since some consumers may have post-work logic (e.g.
  emitting a follow-up event) that needs to stay outside the `Runnable` if it should
  run regardless of `APPLIED` vs `IGNORED_DUPLICATE` (unlikely given the pattern's
  intent, but verify per consumer).
- `product-service`'s `ReservationEventDedupe` javadoc explicitly documents why no
  `try/catch` sits around the repository `save()` call (Hibernate's assigned-`@Id`
  flush-timing behavior, TASK-BE-541) — carry this reasoning forward into the new
  adapter's implementation; do not reintroduce a dead `try/catch` that looks
  defensive but can never fire.

---

# Edge Cases

- `settlement-service`'s `ProcessedEventStore` may have call sites depending on a
  method signature that doesn't map 1:1 onto `EventDedupePort#process` (e.g. a
  query-only "has this been processed" check without also running work inline) — if
  so, either extend the call site to use the `Runnable`-based API or keep a thin
  query-only method on the local facade backed by the same underlying dedupe table
  (document the choice).
- A consumer whose "work" is itself transactional-boundary-sensitive (e.g. it
  publishes an outbox row as part of the same transaction) must have that outbox
  write happen *inside* the `Runnable` passed to `process`, not after it returns —
  otherwise the atomicity guarantee this task exists to preserve is silently broken.
- If any of the four services' existing dedupe table schema differs meaningfully from
  what a straightforward `EventDedupePort` adapter expects (e.g. a composite key
  instead of a single `eventId` UUID), that is a legitimate blocker requiring a
  design decision (extend the adapter, or a migration) — not something to paper over.

  **Resolved (implementation).** `order-service`'s `AccountDeletedConsumer` hit exactly
  this: the flat `account.deleted` wire (TASK-BE-422) carries no `eventId` at all, so
  the existing dedupe key was a service-defined composite
  `accountId + ":" + (anonymized ? "anon" : "grace")` — a `String`, not a UUID, and not
  the envelope's `event_id`. `EventDedupePort#process` requires a `UUID` and the
  interface's contract/signature is out of scope to change (see Scope), so the chosen
  resolution is "extend the adapter": the consumer deterministically derives a UUID
  from the composite string via `UUID.nameUUIDFromBytes(dedupKey.getBytes(UTF_8))`
  before calling `process(...)`. This is a storage-format detail only — the same
  composite key always maps to the same derived UUID, so the dedup/duplicate outcome
  is byte-identical to the pre-migration behavior; only the persisted table value
  changes shape (a UUID string instead of the raw composite string). No other
  consumer across the 4 services had a non-eventId dedupe key.

---

# Failure Scenarios

- Migrating a consumer's dedupe check without preserving `MANDATORY`-propagation
  atomicity would reintroduce exactly the double-processing risk this pattern exists
  to prevent — verify per consumer, not just per service.
- Leaving `settlement-service`'s `ProcessedEventStore` in place alongside the new
  `EventDedupePort` adapter (rather than consolidating) creates two competing
  abstractions doing the same job — a duplication-of-a-different-kind that this task
  should not introduce.

---

# Test Requirements

- Existing duplicate-delivery integration/unit tests for all migrated consumers must
  remain GREEN, asserting exactly-once side effects on redelivery.
- New unit test(s) for each service's `EventDedupePort` adapter: first call →
  `APPLIED` + work runs; duplicate `eventId` → `IGNORED_DUPLICATE` + work does not
  re-run.

---

# Definition of Done

- [x] All 4 services implement and adopt `EventDedupePort`
- [x] All named consumers migrated (plus the un-named order-service consumers sharing
      the single `EventDeduplicationChecker` bean, since it is one shared component —
      see Acceptance Criteria evidence)
- [x] `settlement-service`'s parallel `ProcessedEventStore` abstraction resolved (not
      left duplicated) — deleted, consumers use `EventDedupePort` directly
- [x] Atomicity preserved and verified (by inspection, see Acceptance Criteria)
- [x] Tests passing for all 4 services (375/387/195/145, 0 failures)
- [x] Ready for review
