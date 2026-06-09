# TASK-FAN-FE-003 — membership subscribe / status UI

Status: review
Type: frontend (TASK-FAN-FE)
Project: fan-platform
App: fan-platform-web (`projects/fan-platform/web/fan-platform-web`)

---

## Goal

Turn the static `/membership` info page into a working **subscribe / cancel /
status** experience backed by membership-service (FAN-BE-009) through the gateway
`/api/v1/memberships/**` routes. Completes the membership vertical slice's
**user-visible surface** (backend leg = FAN-BE-008/009/010; the post-gate 403
`MEMBERSHIP_REQUIRED` ErrorState + "/membership" deep-link already exists).

## Scope

**In scope (fan-platform-web only):**

1. `entities/membership` — `Membership`, `MembershipTier` (`MEMBERS_ONLY|PREMIUM`),
   `MembershipStatus` (`ACTIVE|CANCELED`), `MembershipListItem` (with read-time
   `active`). Matches `membership-api.md` payloads.
2. `features/membership/api/getMemberships.ts` (`server-only`) —
   `GET /api/v1/memberships` → the caller's memberships (newest first).
3. `features/membership/api/actions.ts` (`'use server'`):
   - `subscribe(tier, planMonths, paymentToken)` — `POST /api/v1/memberships`
     with a server-generated `Idempotency-Key` (`crypto.randomUUID()`).
     **Returns a discriminated result** (`{ ok: true; membership } | { ok: false;
     code; message }`) rather than throwing on the expected business decline
     (422 `PAYMENT_DECLINED` / `MEMBERSHIP_TIER_INVALID`), so the client renders
     the decline inline. Auth/transport errors still throw. `revalidatePath('/membership')`.
   - `cancelMembership(id)` — `POST /api/v1/memberships/{id}/cancel`,
     `revalidatePath('/membership')`. 404/already-canceled is an idempotent no-op
     at the UI level.
4. `features/membership/ui/SubscribePanel.tsx` (`'use client'`) — per-tier
   subscribe cards with a demo payment-token input (default `tok_visa_demo`;
   `tok_decline` forces a decline) + plan-months selector; `useTransition`; shows
   the decline message inline; disables a tier the caller already actively holds.
5. `features/membership/ui/MembershipStatusCard.tsx` — current active membership
   summary (tier / window / `active`) + cancel button (client, `useTransition`).
6. `app/(main)/membership/page.tsx` — rewrite as a Server Component:
   `getFanSession()` → `getMemberships()` → derive the current active membership →
   render `MembershipStatusCard` (if active) + `SubscribePanel` + the existing
   tier-perks info. Reads an optional `?tier=` searchParam (set by the gate
   deep-link) to highlight the required tier.
7. Post-gate deep-link: `posts/[id]` ErrorState "멤버십 알아보기" link carries the
   required tier (`/membership?tier=PREMIUM`).
8. Tests (vitest): `SubscribePanel` renders tiers + subscribe control + decline
   surface; `MembershipStatusCard` renders an active membership + cancel; entity
   type round-trip. `tsc --noEmit` + `next lint` + `vitest run` green.

**Out of scope:** real payment integration (PG is a deterministic mock); a
dedicated membership-history page; notification-bell for membership events
(notification-service v2); wiring `community-service:integrationTest` to CI
(FAN-BE-011).

## Acceptance Criteria

- **AC-1** `/membership` shows the caller's current active membership (if any) and
  tier subscribe cards; a tier already actively held is not offered again.
- **AC-2** Subscribe with `tok_visa_demo` (or empty) → creates an ACTIVE
  membership; the page reflects it after revalidation.
- **AC-3** Subscribe with `tok_decline` → 422 `PAYMENT_DECLINED` surfaced inline
  ("결제가 거절되었습니다"), NO membership created, no thrown error page.
- **AC-4** Cancel an active membership → status becomes CANCELED; the subscribe
  cards return.
- **AC-5** The access token never reaches the client bundle — all gateway calls go
  through `server-only` reads + `'use server'` actions (no `accessToken` in any
  client component).
- **AC-6** `Idempotency-Key` is generated server-side per subscribe attempt.
- **AC-7** The post-gate ErrorState links to `/membership?tier=<requiredTier>`.
- **AC-8** `tsc --noEmit`, `next lint --dir src`, `vitest run` all green
  (MONO-166 frontend CI gate).

## Related Specs

- `specs/contracts/http/membership-api.md` (public subscribe/cancel/list/detail +
  envelope + error codes).
- `specs/services/fan-platform-web/architecture.md` (FSD lite, server boundary,
  HttpOnly cookie / server-only accessToken).
- `specs/services/membership-service/architecture.md` (state machine, read-time
  `active`, PG mock `tok_decline`).

## Related Contracts

- `membership-api.md` § Subscribe / Cancel / List / Detail.
- Gateway route `Path=/api/v1/memberships/**` → `/api/fan/memberships/**`
  (`gateway-service/application.yml`).

## Edge Cases

- No memberships yet → status card hidden; all tiers offered.
- A stored-ACTIVE row past its window reads `active=false` (read-time) → treated as
  not-currently-active; the tier is offered again.
- PG decline (422) is a normal business outcome, not an error page.
- Idempotent replay (same key) — not exercised in the UI (a fresh key per attempt),
  but the server contract tolerates it.
- Cancel of an already-canceled / unknown membership → no-op (no error toast).

## Failure Scenarios

- **Token leak** — putting `accessToken` into a client component would ship it in
  the browser bundle. Mitigation: AC-5; reads via `server-only` `getMemberships`,
  writes via `'use server'` actions; `SubscribePanel`/`StatusCard` receive only
  plain data + call actions.
- **Throwing on decline** — would replace the page with an error boundary instead
  of an inline message. Mitigation: `subscribe()` returns a discriminated result
  for `PAYMENT_DECLINED`/`MEMBERSHIP_TIER_INVALID`.
- **Stale view after subscribe/cancel** — mitigated by `revalidatePath('/membership')`
  in both actions.
