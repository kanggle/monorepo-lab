# Task ID

TASK-FAN-FE-012

# Title

fan-platform-web: forward buyer identity (email/name) to the PortOne payment window (KG이니시스 V2 requires buyer email)

# Status

review

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

The real-PG checkout (FE-010/FE-011) calls PortOne `requestPayment` WITHOUT a
`customer` block. With the live demo channel bound to **KG이니시스 V2 일반결제**,
the SDK rejects the request before opening the window:

> 결제 창을 여는 중 오류가 발생했습니다: 결제 창 호출에 실패하였습니다. 이니시스 V2
> 일반 결제의 경우 구매자 이메일은 필수 입력입니다.

Buyer email is mandatory for Inicis-class PGs. Forward the signed-in fan's
identity (email + display name, already available from the OIDC `email`/`profile`
scopes) to the payment window so the checkout succeeds. This is a real gap — it
reproduces for any Inicis-class channel — not merely a demo-config issue.

---

# Scope

## In Scope

- `shared/auth/session.ts`: expose `email` + `displayName` on `FanSession`
  (from `session.user.email` / `session.user.name` — non-sensitive identity,
  safe to hand to a client component).
- `app/(main)/membership/page.tsx`: pass `buyerEmail` / `buyerName` to
  `SubscribePanel` and `RenewPanel`.
- `features/membership/lib/portone-checkout.ts`: add an optional `buyer`
  parameter and always send `customer: { email, fullName, phoneNumber }` to
  `requestPayment`. Prefer the authenticated buyer; fall back to demo-safe
  values so a missing IAM email claim never hard-blocks checkout (the buyer
  email is NOT part of the server-side verification — ADR-001 re-checks
  status/amount/currency only). Also surface the PG's own error message on a
  thrown SDK error (it is the actionable signal).
- `SubscribePanel` / `RenewPanel`: accept `buyerEmail` / `buyerName` props and
  forward them to the checkout helper.
- Tests: `subscribe-panel.test.tsx` + `renew-panel.test.tsx` — assert the buyer
  email/name reach `requestPortOnePayment`; existing amount assertions updated
  for the new 3rd arg.

## Out of Scope

- Collecting a real phone number (not in the session) — a neutral placeholder is
  sent; test-mode Inicis validates format only.
- Backend changes — the server verification boundary is unchanged (ADR-001).
- Any PG channel/provider switch.

---

# Acceptance Criteria

- [ ] `requestPortOnePayment` sends `customer.email` = the signed-in fan's email
      (fallback demo-safe value when absent); KG이니시스 V2 no longer rejects the
      window for a missing buyer email.
- [ ] Subscribe, upgrade, and renew all forward the buyer identity.
- [ ] `tsc` + `next lint` + `vitest` + `next build` green (SDK mocked).

---

# Related Specs

- `specs/services/fan-platform-web/architecture.md`
- `docs/adr/ADR-001-real-pg-portone-verification-boundary.md` (verification
  boundary unchanged — buyer email is out of the server re-check)

# Related Contracts

- `specs/contracts/http/membership-api.md` (unchanged — buyer identity is
  client→PG only, not part of our subscribe/renew request)

---

# Target App

- `fan-platform-web`

---

# Edge Cases

- IAM omits the `email` claim → fall back to a well-formed demo email so checkout
  still opens (backend re-verifies the payment regardless of buyer email).
- Anonymous/expired session → the page already redirects to `/login`; the panels
  never render for an unauthenticated fan.

---

# Failure Scenarios

- Omitting `customer` entirely → KG이니시스 V2 rejects the window up-front
  ("구매자 이메일은 필수") → the fan cannot pay (the bug this task fixes).
- Sending a malformed email → PG validation rejects → same failure; the fallback
  is a well-formed address.

---

# Test Requirements

- Unit `subscribe-panel.test.tsx`: buyer email/name forwarded to the checkout;
  amount assertions (7,900 / 17,900 / prorated 13,950) preserved with the new
  buyer arg.
- Unit `renew-panel.test.tsx`: buyer email/name forwarded on renew.

---

# Definition of Done

- [ ] Implementation completed (session email → panels → checkout customer)
- [ ] vitest + tsc + lint + build green (mocked)
- [ ] Ready for review
