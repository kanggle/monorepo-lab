# Task ID

TASK-FAN-BE-033

# Title

membership-service: BillingKeyEnrollment + auto-renewal scheduler + PortOne webhook endpoint (ADR-002)

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

---

# Goal

[ADR-002](../../docs/adr/ADR-002-billing-key-auto-renewal.md) (Accepted
2026-07-25). Give membership-service real recurring billing: a fan enrolls a
billing key once, and a server scheduler auto-charges + auto-renews on the
membership's `validTo` date — reusing the existing, already-tested
`RenewMembershipUseCase` unchanged (D2).

**Blocked on [`TASK-MONO-482`](../../../../tasks/ready/TASK-MONO-482-libs-payment-recurring-billing.md)**
(`libs/payment-core`'s `RecurringBillingGateway` + `libs/payment-portone`'s
webhook verifier) — promote this task to `ready/` only when 482 lands on `main`.

---

# Scope

## In Scope

- **`BillingKeyEnrollment`** entity (new table): `accountId, tenantId, tier,
  billingKey (encrypted at rest), active, createdAt`. Decoupled from `Membership`/
  `MembershipStatus` — **no state-machine change** (ADR-002 §D1). At most one
  active enrollment per account+tier.
- **Issuance endpoint**: accepts the `billingKey` the frontend obtained from
  `PortOne.requestIssueBillingKey(...)` and persists it (Phase 1 trusts the SDK's
  return value as-is per ADR-MONO-057 §7 — no server-side issuance verification).
- **Daily scheduler** (`@Scheduled`): finds ACTIVE memberships with `validTo`
  approaching (D-0) that have an active `BillingKeyEnrollment` for their tier.
  For each: server-generates a `paymentId`, calls
  `RecurringBillingGateway.chargeBillingKey(billingKey, paymentId, amountMinor,
  "KRW", orderName)`. On approved → drive the **existing**
  `RenewMembershipUseCase.execute(...)` with that `paymentId` (unchanged use
  case — ADR-002 §D2). On declined → no renewal, one retry at D+1, then give up
  (ADR-002 §D4, fail-closed, no new grace state). On an ambiguous/lost response →
  reconcile via the **existing** `PaymentGatewayPort.verify(paymentId, ...)`
  before deciding (ADR-MONO-057 §1.3/§D3) — never retry blindly.
- **Webhook endpoint** `POST /webhooks/portone`: verifies the signature via
  `libs/payment-portone`'s verifier (invalid → 401, reject before touching the
  payload); on a valid signature, treats the payload as a **trigger only** — calls
  `verify(paymentId, ...)` to get the truth, never acts on the webhook's own
  amount/status fields directly (ADR-002 §D3). Duplicate/at-least-once delivery is
  absorbed by `RenewMembershipUseCase`'s existing idempotency-key check — no new
  dedupe table (ADR-MONO-057 §7).
- Enrollment cancel (soft `active=false`).

## Out of Scope

- Billing-key issuance **UI** — `TASK-FAN-FE-013`.
- Any `MembershipStatus`/state-machine change (explicitly ruled out, ADR-002 §D1/§D4).
- Fan-facing notification on auto-renewal failure (deferred, ADR-002 §D4).
- `libs/payment` changes — frozen, consume only (`TASK-MONO-482`).

---

# Acceptance Criteria

- [ ] `BillingKeyEnrollment` persisted, encrypted at rest, never logged/returned
      in any response DTO.
- [ ] Scheduler drives the unmodified `RenewMembershipUseCase` on a successful
      charge — its existing payment-verification/idempotency/event-publish tests
      remain green, unchanged.
- [ ] A charge decline does not renew; D+1 retry; second failure leaves the
      membership to expire via existing read-time expiry (no new status).
- [ ] Webhook endpoint: invalid signature → 401 before any payload processing;
      valid signature → reconciles via `verify(paymentId, ...)`, never trusts the
      payload's amount/status directly.
- [ ] `./gradlew :projects:fan-platform:apps:membership-service:check` green.

---

# Related Specs

- `docs/adr/ADR-002-billing-key-auto-renewal.md`
- `docs/adr/ADR-MONO-057-recurring-billing-key-capability.md`
- `docs/adr/ADR-001-real-pg-portone-verification-boundary.md`
- `specs/services/membership-service/architecture.md` § State Machine

# Related Contracts

- New: `POST /webhooks/portone` (internal, PG-facing — not a public API contract
  in the usual sense; document its shape in `specs/contracts/` if the project
  convention requires webhook endpoints there).
- New: billing-key issuance endpoint (accepts the client-obtained `billingKey`).

---

# Target App

- `membership-service`

---

# Edge Cases

- Two enrollments racing for the same account+tier → the "at most one active"
  constraint must reject/replace, not create two chargeable enrollments.
- Scheduler runs twice for the same day (deploy overlap, clock skew) → the
  server-generated `paymentId` + `RenewMembershipUseCase`'s idempotency-key reuse
  must make the second run a no-op, not a double charge.
- Webhook arrives before the scheduler's own synchronous response — the
  reconcile-via-`verify` path must be safe to run concurrently with the
  scheduler's own handling of the same `paymentId` (idempotent by construction,
  since `verify` is a read and `RenewMembershipUseCase` is idempotency-keyed).

---

# Failure Scenarios

- Trusting the webhook payload directly for the charge amount/status → repeats
  the exact class of defect ADR-001 exists to prevent, now server-to-server.
- Retrying a timed-out charge with the same billing key without first
  reconciling via `verify` → double-charge risk (the money-safety failure this
  task exists to avoid, mirroring `TASK-BE-438`'s stranded-refund lesson).

---

# Test Requirements

- Scheduler unit tests: successful charge → renew driven; declined → no renew,
  retry scheduled; ambiguous response → reconcile path exercised (mocked
  `RecurringBillingGateway` + `PaymentGatewayPort`).
- Webhook endpoint IT: invalid signature rejected; valid signature triggers
  reconcile; duplicate delivery is a no-op.
- `RenewMembershipUseCase`'s existing test suite passes unchanged (proof the
  reuse didn't require touching it).

---

# Definition of Done

- [ ] Implementation completed (entity + scheduler + webhook + issuance endpoint)
- [ ] Tests green; `RenewMembershipUseCase` untouched and its suite still passes
- [ ] Live verification: one real billing-key issuance → one real auto-charge
      confirmed (per ADR-002's Phase 2 completion criterion — separate from ADR
      ACCEPT, which already authorised implementation)
- [ ] Ready for review

---

# Notes

Analysis + implementation model: **Opus** (new trust model — server-initiated
charge, webhook attack surface, money-safety reconciliation). Depends on
`TASK-MONO-482`; promote `backlog/` → `ready/` on its merge. Pairs with
`TASK-FAN-FE-013` (issuance UI) — consider an atomic PR if both are picked up
together, per this project's precedent (BE-031/FE-010, BE-032/FE-011).
