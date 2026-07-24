# Task ID

TASK-MONO-478

# Title

Extract `libs/payment-core` + `libs/payment-portone` + `libs/payment-toss` — project-agnostic PG port with config-selectable vendor adapters (ADR-MONO-056 Phase 1)

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

Phase 1 of [ADR-MONO-056](../../docs/adr/ADR-MONO-056-payment-gateway-abstraction.md)
(ACCEPTED 2026-07-24). Create the shared, project-agnostic payment-gateway
library so future sites integrate a PG by config, not by rewrite, and so the two
existing integrations (ecommerce/Toss, fan/PortOne) can later migrate onto it.

**This task creates the library only — NO consumer migration** (that is
TASK-MONO-479 membership + TASK-MONO-480 payment, which stay in `backlog/` until
this lands).

Per the ACCEPT resolution (ADR §7):
- **Canonical model = verify (single-op).** The port's contract is "prove this
  payment is real for this amount + currency". A confirm-model vendor (Toss)
  performs its `POST /v1/payments/confirm` **inside** its adapter and returns a
  verified result — the port exposes no separate capture op. The Toss adapter
  javadoc MUST state its `confirm` is a money-**capture**, not a read.
- **Module split** — `payment-core` (SDK-free) + one module per vendor.

---

# Scope

## In Scope

- **`libs/payment-core`** (new Gradle module, no vendor SDK on its classpath):
  - `PaymentGatewayPort` — canonical verify port
    (`PaymentVerification verify(PaymentVerificationRequest)` returning
    `{ status, paidAmountMinor, currency, vendorPaymentRef }`).
  - Canonical value types — **domain-free** (no `Membership`/`Order`; only
    money/currency/reference primitives).
  - Category-B resilience + observability scaffolding (Resilience4j
    CircuitBreaker/Retry/Bulkhead + fail-closed fallback + metric hooks),
    generalised from the existing `TossPaymentsAdapter` (ADR-MONO-005 Category B).
- **`libs/payment-portone`** — `PortOnePaymentAdapter` moved out of
  `membership-service`, implementing `PaymentGatewayPort`, config/profile-selectable,
  domain-free.
- **`libs/payment-toss`** — `TossPaymentsAdapter` moved out of `payment-service`,
  implementing `PaymentGatewayPort` (its `confirm` capture wrapped inside `verify`),
  config-selectable, domain-free.
- `settings.gradle` + module `build.gradle` wiring; unit tests moved/adapted with
  each adapter (MockWebServer / WireMock as today). `./gradlew check` green.

## Out of Scope

- **Consumer migration** — `membership-service` / `payment-service` keep their
  current private adapters until TASK-MONO-479 / 480. This task does not delete or
  rewire the consumers (the new lib modules coexist, unused, until migration).
- Standalone payment service (ADR §D4), shared FE package (ADR §C), recurring /
  billing-key modeling — all future ADRs.
- Any domain entity in `libs/*` — HARDSTOP-03.

---

# Acceptance Criteria

- [ ] `libs/payment-core` compiles with **no** Toss/PortOne SDK on its classpath
      (`./gradlew :libs:payment-core:dependencies` shows neither vendor SDK).
- [ ] `PaymentGatewayPort` + canonical value types contain **no** project domain
      type (grep: no `Membership`, `Order`, service names).
- [ ] `libs/payment-portone` + `libs/payment-toss` each implement the port; their
      moved adapter unit tests pass unchanged in behavior (fail-closed cases +
      auth header + amount/currency verification preserved).
- [ ] Toss adapter's `verify` wraps `POST /v1/payments/confirm`; javadoc documents
      it as a capture (not a read).
- [ ] `./gradlew check` green across the repo (consumers still build on their
      current adapters — nothing migrated yet).

---

# Related Specs

- `docs/adr/ADR-MONO-056-payment-gateway-abstraction.md` (§2 D2, §7 resolutions)
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` (Category B)
- `platform/shared-library-policy.md` (HARDSTOP-03 domain-free)

# Related Contracts

- None new. The port is internal infrastructure; existing HTTP/PG contracts
  (Toss `/v1/payments/*`, PortOne `GET /payments/{id}`) are unchanged.

---

# Edge Cases

- A vendor SDK transitively leaking onto `payment-core`'s classpath → AC fails;
  keep SDK deps in the vendor modules only (`libs/java-gateway` cross-framework-leak
  lesson, ADR-MONO-049 D1).
- Toss `confirm` is non-idempotent capture — the moved adapter must preserve its
  existing idempotency/duplicate handling, not just the happy path.

---

# Failure Scenarios

- Modeling the port as read-only `verify` while a consumer assumes it does not
  charge → a consumer wired to Toss would double-capture. Mitigation: javadoc +
  the single-op contract explicitly names Toss `verify` as capture-inclusive.
- Moving the adapters but silently changing a fail-closed path to fail-open →
  a PG outage would then approve unverified payments. Preserve each adapter's
  existing fail-closed semantics exactly (money-safety).

---

# Definition of Done

- [ ] Three lib modules created; both adapters moved in, domain-free, config-selectable
- [ ] `./gradlew check` green; no vendor SDK on `payment-core`
- [ ] Consumers unchanged (migration is 479/480)
- [ ] Ready for review

---

# Notes

Analysis + implementation model: **Opus** (complex cross-project shared-library
extraction + money-safety-preserving refactor). Dispatch per CLAUDE.md § Recommending
Tasks. Follow-ons: TASK-MONO-479 (membership migration), TASK-MONO-480 (payment
migration), TASK-MONO-481 (checkout-pattern skill) — in `backlog/`, promote on merge.
