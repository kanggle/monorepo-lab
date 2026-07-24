# Task ID

TASK-FAN-FE-010

# Title

fan-platform-web: PortOne browser SDK checkout — real payment window on subscribe

# Status

ready

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

Replace the demo "결제 토큰" text field in `SubscribePanel` with a real PG
**payment window**: clicking "카드로 결제" loads the PortOne V2 browser SDK
(`@portone/browser-sdk`), calls `PortOne.requestPayment({ storeId, channelKey,
paymentId, orderName, totalAmount, currency, payMethod })` — which opens the PG
payment window (iframe/popup) — and on completion passes the returned `paymentId`
to the `subscribe` server action. The backend (TASK-FAN-BE-031) then verifies that
`paymentId` server-side before creating the membership. Per
[ADR-001](../../docs/adr/ADR-001-real-pg-portone-verification-boundary.md).

This is what makes the payment-flow window actually appear — the current flow calls
the subscribe action directly with no PG UI (see the answer that motivated
ADR-001).

> **Blocked on live-verify only:** the SDK wiring + vitest (SDK mocked) are CI-safe
> and mergeable without keys. The **live** acceptance (real payment window renders,
> user pays, membership created) needs the PortOne test `storeId` + `channelKey`
> (`NEXT_PUBLIC_PORTONE_*`, build-time inlined) — kanggle provisions. Merge
> atomically with TASK-FAN-BE-031.

---

# Scope

## In Scope

- Add `@portone/browser-sdk` dependency to `fan-platform-web`.
- `features/membership/ui/SubscribePanel.tsx`: remove the free-text `paymentToken`
  input; the tier "구독하기"/"카드로 결제" button now (1) generates a unique
  `paymentId` (uuid), (2) calls `PortOne.requestPayment(...)` → payment window,
  (3) on success calls the `subscribe` action with the returned `paymentId`, (4)
  renders SDK cancel/failure and the backend 422 `PAYMENT_DECLINED` inline (no
  error boundary). Keep the plan-months selector.
- `features/membership/api/actions.ts`: `subscribe(tier, planMonths, paymentId)` —
  send `paymentId` in the POST body (contract already updated in Phase 0).
- Config: `NEXT_PUBLIC_PORTONE_STORE_ID`, `NEXT_PUBLIC_PORTONE_CHANNEL_KEY` via
  `shared/config/env` (build-time inlined — NEXT_PUBLIC_*; see
  `env_next_public_inlined_at_build_time`). No secret in the browser bundle (the
  API secret is backend-only).
- Tests: `subscribe-panel.test.tsx` — mock `@portone/browser-sdk` `requestPayment`
  (success → action called with paymentId; user-cancel → no action, inline notice;
  SDK failure → inline notice). Preserve the FE-009 tier-superset assertions.

## Out of Scope

- Backend verification / PortOne REST call — TASK-FAN-BE-031.
- The mock demo path: when the backend runs the default (mock) profile, the SDK
  still runs but the test-channel window is PortOne's test UI; document how to force
  a decline in test mode (or keep a hidden dev affordance) — do NOT reintroduce the
  public token text field.
- Membership history / status UI — unchanged.

---

# Acceptance Criteria

- [ ] Clicking the tier CTA opens the PortOne payment window (SDK `requestPayment`
      invoked with `storeId`/`channelKey`/generated `paymentId`/amount).
- [ ] On SDK success, `subscribe` is called with the returned `paymentId`; a 201 →
      the tier shows "이용 중인 멤버십"; a 422 `PAYMENT_DECLINED` → inline decline
      notice.
- [ ] User-cancel / SDK error → inline notice, `subscribe` NOT called, no throw.
- [ ] The public bundle contains NO secret (only `NEXT_PUBLIC_PORTONE_*`); grep-clean
      for the API secret.
- [ ] `tsc --noEmit`, `next lint`, `vitest` pass (SDK mocked); production `next
      build` succeeds. FE-009 tier-superset tests still pass.
- [ ] **(Live, needs keys — do with BE-031):** the real test payment window renders
      and a successful test payment creates the membership.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 (read
> `PROJECT.md` + rule layers).

- `docs/adr/ADR-001-real-pg-portone-verification-boundary.md`
- `specs/services/fan-platform-web/architecture.md` (server-only token boundary — the
  PG API secret stays server-side; only public keys reach the browser)

# Related Contracts

- `specs/contracts/http/membership-api.md` (§ Subscribe — `paymentId`)

---

# Target App

- `fan-platform-web`

---

# Edge Cases

- User closes the payment window → SDK returns a cancel/failure result → inline
  notice, no subscribe call.
- SDK script fails to load (network) → graceful inline error, not a crashed page.
- Double-click / re-entrancy → disable the CTA while a payment is in flight
  (reuse `isPending`).
- `paymentId` must be unique per attempt (generate fresh each click) — a reused id
  would collide with the backend's replay guard.

---

# Failure Scenarios

- Reintroducing a client-typed token as the trust source → defeats the whole
  server-verify model (explicitly rejected; the client only relays a PortOne
  `paymentId`, which the backend independently verifies).
- Putting the PortOne API secret in `NEXT_PUBLIC_*` → leaks it into the browser
  bundle (explicitly rejected; secret is backend-only, guarded by the grep AC).
- Shipping FE without BE-031 (or vice-versa) → contract/behavior split on `main`
  (guarded by the atomic-merge requirement).

---

# Test Requirements

- Unit (`subscribe-panel.test.tsx`, `@portone/browser-sdk` mocked): success →
  `subscribe` called with the returned `paymentId`; cancel → not called + inline
  notice; SDK error → inline notice. FE-009 assertions (`['PREMIUM']` suppresses
  MEMBERS_ONLY, `['MEMBERS_ONLY']` still offers PREMIUM) preserved.
- Live smoke (needs keys): payment window renders; successful test payment →
  membership ACTIVE; canceled/failed → 422 inline.

---

# Definition of Done

- [ ] Implementation completed (SDK checkout replacing the token field)
- [ ] vitest (SDK mocked) + tsc + lint + build green (CI-safe)
- [ ] Live-verify done jointly with TASK-FAN-BE-031 (needs keys)
- [ ] Ready for review
