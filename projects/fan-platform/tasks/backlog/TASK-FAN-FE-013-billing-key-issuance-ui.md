# Task ID

TASK-FAN-FE-013

# Title

fan-platform-web: billing-key issuance UI (자동 갱신 등록) (ADR-002)

# Status

backlog

# Owner

frontend

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
2026-07-25). Let a fan register a card **once** for auto-renewal ("자동 갱신
등록") via PortOne's billing-key issuance SDK call, and send the resulting
`billingKey` to the backend's issuance endpoint (`TASK-FAN-BE-033`).

**Blocked on [`TASK-MONO-482`](../../../../tasks/ready/TASK-MONO-482-libs-payment-recurring-billing.md)**
landing (the backend issuance endpoint depends on it) — promote to `ready/`
alongside `TASK-FAN-BE-033`, ideally as an atomic PR (this project's precedent:
BE-031/FE-010, BE-032/FE-011).

---

# Scope

## In Scope

- `features/membership/lib/portone-billing-key.ts` (new, alongside the existing
  `portone-checkout.ts`): `requestIssueBillingKey(buyer: CheckoutBuyer):
  Promise<{ ok: true; billingKey: string } | { ok: false; message: string }>` —
  calls `PortOne.requestIssueBillingKey({ storeId, channelKey, billingKeyMethod:
  'CARD', customer: {...}, ... })`. Forward buyer identity the same way
  `requestPortOnePayment` already does (KG이니시스-class gotcha applies here too
  — `FE-012` precedent).
- A membership settings surface (e.g. an "자동 갱신" toggle/button in the
  membership page or a new settings panel) — CTA "자동 갱신 등록" opens the
  issuance flow; on success, calls a `'use server'` action that POSTs the
  `billingKey` to the backend issuance endpoint; shows "등록됨" state; an "해지"
  action calls the backend cancel endpoint.
- Tests: mock the PortOne SDK issuance call (cancel / decline / success paths,
  mirroring `subscribe-panel.test.tsx`'s SDK-mock pattern).

## Out of Scope

- The scheduler / webhook / auto-charge itself — `TASK-FAN-BE-033`.
- Showing auto-renewal charge **history** — separate follow-up if wanted.

---

# Acceptance Criteria

- [ ] Fan can issue a billing key (card registration, no charge) and see it
      reflected as "자동 갱신 등록됨".
- [ ] Buyer identity (email/name) forwarded to the issuance call, same as checkout.
- [ ] Cancel path sets the enrollment inactive; UI reflects "해지됨".
- [ ] `tsc` + `next lint` + `vitest` + `next build` green (SDK mocked).

---

# Related Specs

- `specs/services/fan-platform-web/architecture.md`
- `docs/adr/ADR-002-billing-key-auto-renewal.md`

# Related Contracts

- The backend issuance/cancel endpoints from `TASK-FAN-BE-033`.

---

# Target App

- `fan-platform-web`

---

# Edge Cases

- Fan issues a new billing key while an active one already exists for the same
  tier → backend enforces "at most one active" (`TASK-FAN-BE-033`); the UI
  should treat a successful re-issuance as replacing, not stacking.
- SDK issuance cancel (fan closes the window) → same inline-non-throw pattern as
  `requestPortOnePayment` (`{ ok: false, message }`, no crash).

---

# Failure Scenarios

- Sending the `billingKey` to the backend over a non-`'use server'` path that
  could leak it to a client bundle → treat it exactly as sensitive as an access
  token (server-only read/write, per this app's existing session boundary
  discipline).

---

# Test Requirements

- Unit: issuance success/decline/cancel paths (SDK mocked); buyer identity
  forwarded; server action never exposes `billingKey` to client state.

---

# Definition of Done

- [ ] Implementation completed (issuance UI + server action wiring)
- [ ] vitest + tsc + lint + build green (mocked)
- [ ] Ready for review

---

# Notes

Analysis + implementation model: **Opus** (new sensitive-data handling path,
mirrors the access-token boundary discipline already established for this app).
Depends on `TASK-MONO-482` (backend enabler) and pairs with `TASK-FAN-BE-033`.
