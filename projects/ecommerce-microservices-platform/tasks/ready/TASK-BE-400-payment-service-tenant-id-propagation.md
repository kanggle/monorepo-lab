---
id: TASK-BE-400
title: "payment-service tenant_id propagation (ADR-MONO-030 Step 4, facet c)"
status: ready
service: payment-service
tags: [code, test, migration, multi-tenant, event-driven]
analysis_model: "Opus 4.8"
impl_model: "Sonnet 4.6"
created: 2026-06-18
---

# TASK-BE-400 — payment-service tenant_id propagation

## Goal

Bring `payment-service` into the multi-tenant marketplace SaaS model (ADR-MONO-030 Step 4,
facet c) so every payment row carries `tenant_id` and payment domain events thread it in
their envelope — matching what user-service (V4 / TASK-BE-367), notification-service (V5 /
TASK-BE-370), promotion-service (V6 / TASK-BE-368), and shipping-service (V7 / TASK-BE-369)
already did.

This change also resolves the settlement-service workaround documented in
`specs/features/marketplace-settlement.md §1` (the "ADR-030 통찰" note): once
`PaymentCompleted` and `PaymentRefunded` events carry `tenant_id` in their envelope,
settlement-service can read it directly instead of deriving it from the `OrderPlaced`
snapshot.

## Scope

1. **Flyway migration V5** — add `tenant_id VARCHAR(64) NOT NULL DEFAULT 'ecommerce'` to the
   `payments` table; backfill existing rows to `'ecommerce'` (three-step: ADD nullable →
   UPDATE → SET NOT NULL). Create `idx_payments_tenant_id` index.

2. **TenantContext** — port `TenantContext` (ThreadLocal, default `'ecommerce'`, net-zero D8)
   from user-service pattern into `payment-service` domain layer.

3. **TenantContextFilter** — servlet filter that reads `X-Tenant-Id` gateway header into
   `TenantContext` for the duration of each request.

4. **Domain model (`Payment`)** — add `tenantId` field; update `create(…)` factory and
   `reconstitute(…)` to include it.

5. **Persistence adapter** — stamp `tenant_id` on `PaymentJpaEntity`; repository read
   methods scope by `tenant_id` from `TenantContext`; `PaymentJpaRepository` gains a
   `findByOrderIdAndTenantId` method.

6. **Outbox event envelope** — add `tenant_id` top-level field to `PaymentCompletedEvent`
   and `PaymentRefundedEvent`; populate from the stored payment's `tenantId`. Additive
   change — existing consumers that do not read `tenant_id` are unaffected.

7. **Spec updates**:
   - ADD `## Multi-Tenancy` section to `specs/services/payment-service/architecture.md`
     (M-applicability table matching sibling services).
   - UPDATE `specs/features/multi-tenancy-and-marketplace.md` — remove `payment-service`
     from the `in-migration` table row; mark it done (TASK-BE-400).
   - UPDATE `specs/features/marketplace-settlement.md §1` — remove/update the "ADR-030
     통찰" note that payment events lacked `tenant_id`; note that from TASK-BE-400 onward
     payment events carry it directly.
   - UPDATE `specs/contracts/events/payment-events.md` — add `tenant_id` field to the
     event envelope (additive).

8. **Tests** — unit tests for:
   - `TenantContext` (set / currentTenant / clear / default)
   - `Payment` domain model with `tenantId` field
   - `PaymentJpaEntity` → `PaymentPersistenceMapper` with `tenantId`
   - `PaymentEventOutboxWriter` emits `tenant_id` in serialized envelope
   - `OrderPlacedEventConsumer` threads `tenant_id` into `processPayment`
   - `TenantContextFilter` reads header and clears

## Acceptance Criteria

- AC-1: `payments` table has `tenant_id VARCHAR(64) NOT NULL`; all pre-existing rows map to
  `'ecommerce'` (default-tenant D8 net-zero).
- AC-2: `./gradlew :projects:ecommerce-microservices-platform:apps:payment-service:test`
  passes with all new tests GREEN.
- AC-3: `PaymentCompletedEvent` and `PaymentRefundedEvent` serialized envelopes include
  `"tenant_id"` at the top level; existing fields unchanged.
- AC-4: `TenantContext.currentTenant()` returns `'ecommerce'` when no header is set
  (standalone/D8 degradation).
- AC-5: `payment-service` is removed from the "in-migration" list in
  `specs/features/multi-tenancy-and-marketplace.md`.
- AC-6: `specs/features/marketplace-settlement.md §1` ADR-030 note updated to reflect
  payment events now carry `tenant_id` directly.
- AC-7: `specs/services/payment-service/architecture.md` has a `## Multi-Tenancy` section.

## Related Specs

- `specs/features/multi-tenancy-and-marketplace.md` — M1-M7 definitions, in-migration table
- `specs/features/marketplace-settlement.md` — ADR-030 note on missing tenant_id in payment
  events
- `specs/services/payment-service/architecture.md` — service architecture (no MT section yet)
- `rules/traits/multi-tenant.md` — M1-M7 trait definitions

## Related Contracts

- `specs/contracts/events/payment-events.md` — PaymentCompleted / PaymentRefunded envelope
  (additive `tenant_id` field)

## Edge Cases

- **Standalone / no IAM**: `X-Tenant-Id` header absent → `TenantContext.currentTenant()`
  returns `'ecommerce'` (default tenant, D8 net-zero). Service behaves byte-identically to
  pre-multi-tenant single-store.
- **Event consumer threads (OrderPlacedEventConsumer, OrderCancelledEventConsumer)**: no
  HTTP request → no filter → `TenantContext` unset → default tenant used. For
  single-tenant-today this is correct; multi-tenant-ready means the tenant is derived from
  the inbound order event's `tenant_id` field once order events carry it. Annotate with a
  TODO for that future step.
- **Existing outbox rows**: already-persisted outbox rows without `tenant_id` in the
  serialized JSON will be delivered as-is — consumers must tolerate a missing `tenant_id`
  field (treat as `null` / default); this is consistent with the additive contract rule.
- **`findByOrderId` called without tenant context**: repository now queries
  `findByOrderIdAndTenantId`; event consumer threads default to `'ecommerce'`. Functionally
  equivalent for the current single-tenant deployment.

## Failure Scenarios

- **Migration V5 fails** (duplicate version): Flyway will refuse to start — roll back by
  removing the migration file; existing rows are unaffected.
- **`tenant_id` null on insert** (if `TenantContext.currentTenant()` returned null):
  mitigated by the `DEFAULT 'ecommerce'` in Flyway V5 and the invariant in `TenantContext`
  that `currentTenant()` never returns null.
- **Serialization test regression** (existing `PaymentEventOutboxWriterTest` detects
  unexpected envelope field): additive addition of `tenant_id` will not break
  `assertThat(envelope).contains(...)` pattern; no existing assertion checks for exact JSON
  equality.
