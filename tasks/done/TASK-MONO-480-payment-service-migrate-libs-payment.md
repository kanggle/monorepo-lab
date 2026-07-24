# Task ID

TASK-MONO-480

# Title

Migrate ecommerce `payment-service` onto `libs/payment` (ADR-MONO-056 Phase 1, consumer 2)

# Status

done

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

---

# Goal

[ADR-MONO-056](../../docs/adr/ADR-MONO-056-payment-gateway-abstraction.md) Phase 1,
consumer migration. Replace `payment-service`'s private `TossPaymentsAdapter`
+ `PaymentGatewayPort` with a dependency on `libs/payment-core` +
`libs/payment-toss` (created by TASK-MONO-478).

**Blocked on TASK-MONO-478** — promote to `ready/` only when 478 has landed.
Behavior-preserving refactor: the confirm/cancel/refund/status flows + R4j
Category-B config + the money-safety integration tests (stranded-refund,
reconciliation, partial-refund idempotency) must stay green.

---

# Scope

## In Scope

- Depend on `libs/payment-core` + `libs/payment-toss`; delete the in-service
  duplicate adapter + port + R4j scaffolding now living in the shared module.
- Keep the Toss confirm=capture semantics (the shared `verify` wraps `/confirm`).
- Preserve every existing payment IT (PaymentConfirm/Refund/Reconcile/Stranded)
  and the R4j instance config (`toss-payments`).

## Out of Scope

- membership-service migration (TASK-MONO-479).
- Any change to the payment saga / order coupling / settlement events.

---

# Acceptance Criteria

- [ ] payment-service uses the shared Toss adapter; no in-service adapter/port remains.
- [ ] R4j `toss-payments` CB/Retry/Bulkhead behavior preserved (config still applies).
- [ ] All payment ITs + Integration (ecommerce shard) green — assertions unchanged.
- [ ] One atomic PR.

---

# Related Specs

- `docs/adr/ADR-MONO-056-payment-gateway-abstraction.md`
- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md`

# Related Contracts

- Toss `/v1/payments/confirm|cancel|{paymentKey}` — unchanged (adapter-internal).

---

# Edge Cases

- The moved adapter must keep `saveAndFlush` / assigned-@Id semantics and the
  cross-tenant existence masking (those live in the service, not the adapter — verify
  the migration does not disturb them).

# Failure Scenarios

- A refund leg losing its `PgGatewayUnavailableException` → stays-COMPLETED behavior
  would drop money-safety. Preserve the fail path exactly.

---

# Definition of Done

- [ ] Migrated onto libs/payment; duplicate adapter/port deleted
- [ ] Payment ITs + ecommerce Integration green, assertions unchanged
- [ ] Ready for review

Analysis + implementation model: **Opus**.
