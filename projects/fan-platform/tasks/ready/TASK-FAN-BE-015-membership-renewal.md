# TASK-FAN-BE-015 — membership renewal (seamless re-activation)

Status: ready
Type: backend (TASK-FAN-BE)
Project: fan-platform
Apps: membership-service (renew endpoint) + fan-platform-web (renew UI)

---

## Goal

Close the **user-facing follow-up of expiry** (FAN-BE-014). The expiry sweeper now
emits `fan.membership.expired.v1` → an `EXPIRY_REMINDER` notification whose body
says "Renew to keep your member benefits" — but there is no renew action. This task
adds a **renewal flow**: a `POST /api/fan/memberships/{id}/renew` endpoint that
continues a membership's tier with a **seamless window**, and the FE surfaces it on
the active membership (extend) and on a just-expired membership (re-activate).

## Design — seamless renewal, reuse `activated.v1`

`renew(priorId)` creates a **new** membership row (the model is one-window-per-row)
for the **same tier** as the prior, with:

- `validFrom = max(now, prior.validTo)` — renewing **early** stacks the new window
  onto the end of the current one (no lost days); renewing **after expiry** starts
  a fresh window from `now`.
- `validTo = validFrom + planMonths·30d`.

The renewed membership is just another activation, so it **reuses
`fan.membership.activated.v1`** (→ WELCOME notification). A distinct `renewed.v1`
event / `RENEWAL` notification type is intentionally **deferred** (would mirror the
whole FAN-BE-014 producer+consumer+migration footprint for marginal benefit).

A **CANCELED** prior is a deliberate opt-out → renewal is rejected (422
`MEMBERSHIP_NOT_RENEWABLE`); the user subscribes fresh instead. The prior row is
never mutated.

## Scope

**membership-service:**

1. `RenewCommand` (actor, priorMembershipId, planMonths, paymentToken, idempotencyKey).
2. `RenewMembershipUseCase` (`@Transactional`) — mirrors `SubscribeUseCase`
   (Idempotency-Key + PG mock authorize + activated outbox event in one TX) but:
   resolves the prior membership **scoped** (404 `MEMBERSHIP_NOT_FOUND` if
   missing/foreign), rejects a CANCELED prior (422 `MEMBERSHIP_NOT_RENEWABLE`),
   inherits `tier` from the prior, and computes the **seamless window**.
3. `MembershipNotRenewableException` + handler → 422 `MEMBERSHIP_NOT_RENEWABLE`.
4. `RenewRequest` DTO ({planMonths ≥ 1, paymentToken ≤ 80}).
5. `MembershipController.renew` — `POST /api/fan/memberships/{id}/renew`,
   `Idempotency-Key` required, 201 with the new membership.
6. Contract: `membership-api.md` += the renew endpoint.

**fan-platform-web:**

7. `features/membership/api/actions.ts` — `renewMembership(membershipId, planMonths,
   paymentToken)` `'use server'` action (server-generated `Idempotency-Key`;
   returns the discriminated `SubscribeResult`; PG decline rendered inline).
8. `MembershipStatusCard` — add a "연장하기" control (renew the active membership →
   seamless extension; shows the new window end inline on success).
9. `features/membership/ui/RenewPanel.tsx` — for a just-expired (status ACTIVE,
   read-time inactive, not canceled) newest membership: "만료됨 · 갱신하기".
10. `app/(main)/membership/page.tsx` — derive the renewable membership; render
    `RenewPanel` when the newest is expired and none is currently active.

**Tests:** unit (`RenewMembershipUseCaseTest`: seamless window for an active prior,
fresh-from-now for an expired prior, reject CANCELED, PG decline, idempotent replay)
+ Testcontainers IT (`RenewIntegrationTest`: renew an active membership → a new row
with a stacked window + an activated event) + FE vitest (StatusCard extend control,
RenewPanel). membership `:test` + FE `tsc`/`vitest`/`lint`/`build` local; Docker IT
on CI.

## Acceptance Criteria

- **AC-1** `POST /{id}/renew` on an **active** membership creates a new membership
  for the same tier with `validFrom = prior.validTo` (seamless), `validTo =
  validFrom + planMonths·30d`, status ACTIVE; returns 201; the prior is unchanged.
- **AC-2** Renew on an **expired** (status ACTIVE, past window) membership creates a
  new membership with `validFrom = now` (fresh window).
- **AC-3** Renew of a **CANCELED** membership → 422 `MEMBERSHIP_NOT_RENEWABLE`, no
  row created.
- **AC-4** Renew of an unknown / cross-account / cross-tenant id → 404
  `MEMBERSHIP_NOT_FOUND` (existence not leaked).
- **AC-5** Renew requires `Idempotency-Key`; a replay with the same key + payload
  returns the same renewed membership (no second row, no re-auth). PG `tok_decline`
  → 422 `PAYMENT_DECLINED`, no row.
- **AC-6** The renewed membership emits `fan.membership.activated.v1` (→ WELCOME);
  no new event topic / notification type is introduced.
- **AC-7** The FE shows a "연장" control on the active membership and a "갱신" prompt
  on a just-expired membership; the token never reaches the client bundle
  (`server-only` reads + `'use server'` renew action).
- **AC-8** membership `:test` (unit) green; FE `tsc --noEmit`, `next lint --dir
  src`, `vitest run`, `next build` green; Docker IT green on CI.

## Related Specs

- `specs/contracts/http/membership-api.md` (+ renew endpoint).
- `specs/services/membership-service/architecture.md` § State Machine / § PG Mock
  Boundary (renew reuses the subscribe activation path; no stored-state change).
- `specs/services/fan-platform-web/architecture.md` (server boundary).

## Related Contracts

- `membership-api.md` § Renew (new). Request `{planMonths, paymentToken}` +
  `Idempotency-Key`; response = the full membership payload (same shape as subscribe).
- Gateway route `Path=/api/v1/memberships/**` → `/api/fan/memberships/**` (existing).

## Edge Cases

- Early renewal (prior still active) → `validFrom = prior.validTo` (future); the new
  row reads `active=false` until the current window ends, then becomes active —
  seamless continuity. The currently-active row keeps granting access meanwhile.
- Renew after expiry → `validFrom = now`; immediately active.
- Multiple renewals stack (each `validFrom = max(now, latest.validTo)`).
- CANCELED prior → 422 (subscribe fresh instead); the FE offers `SubscribePanel`.
- Idempotent replay (same key) returns the first renewed membership.

## Failure Scenarios

- **Renewing a canceled membership** — would silently re-activate an opted-out
  subscription. Mitigation: AC-3 (422 `MEMBERSHIP_NOT_RENEWABLE`).
- **Lost days on early renewal** — a naive `validFrom = now` would discard the
  remaining paid window. Mitigation: `validFrom = max(now, prior.validTo)` (AC-1).
- **Double renewal** — a duplicate submit would create two rows. Mitigation: the
  `Idempotency-Key` replay guard (AC-5), same mechanism as subscribe.
- **Token leak** — `accessToken` on a client component. Mitigation: AC-7 (renew via
  a `'use server'` action; client components receive only plain data).
