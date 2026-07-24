# Task ID

TASK-FAN-BE-031

# Title

membership-service: PortOne V2 payment adapter (profile-gated, server-side verification) + paymentToken → paymentId rename

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

Add a real PG adapter (`PortOnePaymentAdapter`) behind the existing
`PaymentGatewayPort`, per [ADR-001](../../docs/adr/ADR-001-real-pg-portone-verification-boundary.md).
The trust model is **client-initiated payment + server-side verification**: the
browser SDK (TASK-FAN-FE-010) obtains a `paymentId` from the PortOne payment
window; the backend verifies that `paymentId` via the PortOne REST API
(`status == PAID` AND paid amount == charged amount) before creating the
membership. The client's success signal is NEVER trusted on its own.

The mock adapter stays the **default** (`@Profile("!portone")`) so CI, integration
tests, and keyless local runs never call a real PG. PortOne is activated only with
`SPRING_PROFILES_ACTIVE=portone` + injected keys.

The domain / use-case layers (`SubscribeUseCase`, `Membership`, `AccessPolicy`)
are **unchanged** — this is an adapter swap plus a semantic rename of the
client-supplied string (`paymentToken` → `paymentReference`/`paymentId`).

> **Blocked on live-verify only:** the code + WireMock-stubbed integration tests
> are fully CI-safe and can be implemented/merged without keys. The **live**
> acceptance (real PortOne test payment → verified → membership) requires the
> PortOne test `storeId`/`channelKey`/`API secret` (kanggle provisions). Coordinate
> the atomic merge with TASK-FAN-FE-010 so `main` never carries a half-migrated
> `paymentToken`/`paymentId` split.

---

# Scope

## In Scope

- `domain/payment/PaymentGatewayPort.java`: rename param `paymentToken` →
  `paymentReference` (semantic neutralization; signature otherwise unchanged —
  `amountMinor` already present for amount verification).
- `infrastructure/payment/MockPaymentGatewayAdapter.java`: annotate
  `@Profile("!portone")`; keep the `tok_decline` sentinel behavior (param renamed).
- `infrastructure/payment/PortOnePaymentAdapter.java` (NEW): `@Profile("portone")`;
  `RestClient`/`WebClient` call to `GET {portone.api-base}/payments/{paymentId}`
  with `Authorization: PortOne <api-secret>`; verify `status == PAID` AND
  `amount.total == amountMinor` AND currency; approved → `paymentRef = paymentId`,
  else declined. Fail-closed on any lookup/parse error → declined.
- Config: `fan.payment.portone.api-base`, `fan.payment.portone.api-secret`
  (env-injected; NEVER committed). `application.yml` documents the keys as
  placeholders; a docker-compose overlay (infra) wires real values at demo time.
- Propagate the `paymentToken` → `paymentId` rename through `SubscribeRequest` DTO,
  `SubscribeCommand`, controller, and existing tests (mock path unchanged in
  behavior).
- Tests: `PortOnePaymentAdapterIntegrationTest` with **WireMock** stubbing PortOne
  (PAID / status≠PAID / amount-mismatch / 404 / 5xx) — CI-safe, no real keys.
  `MockPaymentGatewayAdapterTest` updated for the renamed param.

## Out of Scope

- Domain / use-case logic (`SubscribeUseCase` flow, idempotency, state machine) —
  unchanged.
- Frontend SDK / checkout UI — TASK-FAN-FE-010.
- Webhook-based async confirmation — v1 uses synchronous verify-on-subscribe; a
  webhook path is a later increment (note in ADR consequences).
- Refund / cancel-via-PG — cancel stays a local state transition (no PG call).

---

# Acceptance Criteria

- [ ] With NO `portone` profile (default): `MockPaymentGatewayAdapter` is the active
      `PaymentGatewayPort` bean; existing subscribe behavior + all current tests
      pass unchanged (param rename only).
- [ ] With `portone` profile: `PortOnePaymentAdapter` is active; a `paymentId` whose
      PortOne lookup returns `status=PAID` and matching amount → approved
      (`paymentRef = paymentId`); `status≠PAID`, amount mismatch, or lookup
      error → declined → 422 `PAYMENT_DECLINED`, no row (WireMock-verified).
- [ ] The PortOne `api-secret` is read from env only; no secret literal in the repo
      (grep-clean).
- [ ] `./gradlew :membership-service:check` green (WireMock tests, no real keys).
- [ ] **(Live, needs keys — do with FE-010):** a real PortOne test-mode payment →
      backend verifies → membership created; a canceled/failed test payment → 422.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read
> `PROJECT.md`, then load `rules/common.md` plus matching `rules/domains/` and
> `rules/traits/` files. fan-platform declares the `integration-heavy` trait —
> re-read its fail-closed / external-dependency guidance.

- `docs/adr/ADR-001-real-pg-portone-verification-boundary.md`
- `specs/services/membership-service/architecture.md` (§ PG Boundary — Mock + PortOne)

# Related Contracts

- `specs/contracts/http/membership-api.md` (§ Subscribe — `paymentId` + profile
  semantics)

---

# Target App

- `membership-service`

---

# Edge Cases

- Amount tampering: client obtains a `paymentId` for a cheaper charge → server-side
  `amount.total == amountMinor` check rejects it (the core security test).
- Replayed `paymentId` reused across two subscribes → the existing `Idempotency-Key`
  guard + a "one membership per verified paymentId" check (reject reuse) prevent a
  double grant. (Decide: rely on Idempotency-Key, or also uniqueness-constrain
  `paymentRef`; document the choice.)
- PortOne API slow/unreachable → fail-closed (declined, 422) within a bounded
  timeout; never hang the subscribe transaction.
- Currency mismatch (non-KRW) → declined.

---

# Failure Scenarios

- Trusting the client's `paymentId` without server-side verify → a forged success
  grants free membership (explicitly rejected; verify is mandatory — the WireMock
  `status≠PAID` and amount-mismatch tests guard this).
- Leaking the `api-secret` into the repo/CI → rotate + fail the grep-clean AC.
- Shipping the `paymentToken`→`paymentId` rename on `main` before the mock still
  works under the default profile → broken subscribe for keyless envs (guarded by
  the "default profile behavior unchanged" AC).
- Making the PortOne adapter the default bean → CI calls a real PG (explicitly
  rejected; mock is `@Profile("!portone")`, i.e. default).

---

# Test Requirements

- Integration (`PortOnePaymentAdapterIntegrationTest`, `portone` profile, WireMock):
  PAID+matching-amount → approved; status≠PAID → declined; amount mismatch →
  declined; PortOne 404/5xx → declined (fail-closed). No real keys.
- Existing mock/subscribe tests pass under the default profile after the rename.
- Grep guard: no `api-secret` literal committed.

---

# Definition of Done

- [ ] Implementation completed (mock default preserved + PortOne profile added)
- [ ] WireMock integration tests added + passing (CI-safe)
- [ ] Contract already updated (Phase 0) — verify code matches
- [ ] Live-verify done jointly with TASK-FAN-FE-010 (needs keys)
- [ ] Ready for review
