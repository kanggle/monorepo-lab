# Task ID

TASK-MONO-482

# Title

`libs/payment`: add `RecurringBillingGateway` capability + PortOne billing-key adapter + webhook signature-verification utility (ADR-MONO-057)

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

[ADR-MONO-057](../../docs/adr/ADR-MONO-057-recurring-billing-key-capability.md)
(ACCEPTED 2026-07-25). Extend `libs/payment` with one optional capability for
server-initiated recurring (billing-key) charges, and give `libs/payment-portone`
a PortOne implementation + a reusable webhook signature-verification utility.

**This task is library-only — NO consumer wiring** (fan-platform's actual
BillingKeyEnrollment/scheduler/webhook-endpoint consumption is
`TASK-FAN-BE-033`/`TASK-FAN-FE-013`, currently `backlog/`, blocked on this task
landing — mirrors `TASK-MONO-478`'s scope discipline).

---

# Scope

## In Scope

- **`libs/payment-core`**: add
  ```java
  public interface RecurringBillingGateway {
      PaymentAuthorization chargeBillingKey(
          String billingKey, String paymentId, long amountMinor, String currency, String orderName);
  }
  ```
  Domain-free (no `Membership`/`Order`/service names — HARDSTOP-03). Reuses the
  existing `PaymentAuthorization` return type from ADR-MONO-056; adds **no** new
  read/verify op — reconciliation of a lost/ambiguous charge response uses the
  **existing** `PaymentGatewayPort.verify(paymentId, ...)` (ADR-MONO-057 §1.3, the
  same shape as `payment-service`'s stranded-refund reconciler, `TASK-BE-438`).
- **`libs/payment-portone`**: implement `RecurringBillingGateway` on (or alongside)
  the existing `PortOnePaymentAdapter`, calling PortOne's billing-key charge API.
  Follow the existing adapter's fail-closed posture (a failed/ambiguous charge
  never surfaces as approved) — do not invent a new failure shape beyond what
  ADR-MONO-056's FAILURE CONTRACT already documents for this adapter (declined-return).
  Verify the exact PortOne V2 billing-key charge endpoint/request shape against
  current PortOne API docs at implementation time (this ADR fixes the *port
  contract*, not the wire format).
- **`libs/payment-portone`**: a `PortOneWebhookVerifier` (or similarly named)
  utility — verifies a PortOne webhook's HMAC signature against a configured
  webhook secret + raw request body, returning a parsed event or rejecting an
  invalid signature. Vendor-specific, reusable by any future consumer.
- Unit tests: `chargeBillingKey` happy/decline/error paths (MockWebServer, mirrors
  `PortOnePaymentAdapterTest`); webhook verifier valid/invalid-signature/tampered-body
  cases.

## Out of Scope

- Any fan-platform consumption: `BillingKeyEnrollment` entity, the scheduler, the
  webhook HTTP endpoint, billing-key issuance UI, `RenewMembershipUseCase` wiring
  — all `TASK-FAN-BE-033`/`TASK-FAN-FE-013` (blocked on this task).
- `libs/payment-toss` recurring capability — no real consumer today (ecommerce is
  one-time orders); out of scope until one exists.
- Billing-key **issuance** server-side verification (ADR-MONO-057 §7 resolved:
  deferred, not this task).

---

# Acceptance Criteria

- [ ] `RecurringBillingGateway.chargeBillingKey(...)` exists in `libs/payment-core`,
      domain-free.
- [ ] `libs/payment-portone`'s adapter implements it; a failed/ambiguous charge
      never returns `approved()`.
- [ ] `PortOneWebhookVerifier` (or equivalent) rejects an invalid/missing signature
      and a tampered body; accepts a validly-signed payload.
- [ ] `./gradlew :libs:payment-core:build :libs:payment-portone:build` green; no
      vendor SDK added to `payment-core`'s classpath.
- [ ] No consumer (`membership-service`, `payment-service`) modified by this task.

---

# Related Specs

- `docs/adr/ADR-MONO-057-recurring-billing-key-capability.md`
- `docs/adr/ADR-MONO-056-payment-gateway-abstraction.md` (the port this extends)
- `projects/fan-platform/docs/adr/ADR-002-billing-key-auto-renewal.md` (the
  consumer this unblocks — read for context, not implemented here)

# Related Contracts

- None new — this is an internal `libs/` port addition, not an HTTP/event contract.

---

# Target App

- `libs/payment-core`, `libs/payment-portone`

---

# Edge Cases

- A charge whose synchronous response times out/errors → the adapter must NOT
  retry internally with the same `paymentId` (that risks a double-charge if the
  first attempt actually succeeded at the PG) — surface the ambiguous outcome so
  the caller reconciles via `verify(paymentId, ...)` (the consumer's job, per
  ADR-MONO-057 §D3 — this task just needs the adapter to not silently swallow or
  auto-retry the ambiguity).
- A webhook with a valid signature but an unexpected/unknown event type → reject
  or ignore per the vendor SDK's own event taxonomy; do not crash the verifier.

---

# Failure Scenarios

- Skipping the fail-closed posture (returning `approved()` on an ambiguous/timeout
  response) → the same class of money-safety defect ADR-001/ADR-056 already guard
  against, now in the recurring path.
- A webhook verifier that accepts an unsigned or badly-signed payload → an
  attacker could forge a "charge succeeded" event; the verifier's ONE job is to
  make that impossible before any payload is trusted.

---

# Test Requirements

- `RecurringBillingGateway` adapter unit test: happy/decline/timeout-ambiguous
  paths (MockWebServer).
- Webhook verifier unit test: valid signature accepted; missing/invalid/tampered
  rejected.

---

# Definition of Done

- [ ] `RecurringBillingGateway` + PortOne implementation + webhook verifier landed
      in `libs/`, domain-free, tested
- [ ] No consumer touched
- [ ] Ready for review

---

# Notes

Analysis + implementation model: **Opus** (money-safety-adjacent library
extension, mirrors TASK-MONO-478's dispatch). Follow-ons `TASK-FAN-BE-033` /
`TASK-FAN-FE-013` (fan-platform `backlog/`) promote to `ready/` on this task's merge.
