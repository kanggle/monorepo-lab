# TASK-FAN-FE-005 — membership history page

Status: ready
Type: frontend (TASK-FAN-FE)
Project: fan-platform
App: fan-platform-web (`projects/fan-platform/web/fan-platform-web`)

---

## Goal

Surface the **whole membership lifecycle** in one place. Subscribe / cancel /
expire (FAN-BE-014) / renew (FAN-BE-015) all mutate the caller's membership rows,
but the `/membership` page only shows the *current* one. This adds a read-only
**`/membership/history`** page listing every membership the caller has held — with
a per-row lifecycle badge (이용 중 / 만료됨 / 해지됨) — giving the lifecycle work a
visible payoff. Backend-free: it consumes the existing `GET /api/v1/memberships`.

## Scope

**In scope (fan-platform-web only):**

1. `features/membership/ui/historyStatus.ts` — a pure helper mapping a
   `MembershipListItem` to a display status: `CANCELED` → `canceled`; read-time
   `active` → `active`; otherwise (stored ACTIVE, past window) → `expired`. Plus a
   `HISTORY_LABEL` / `HISTORY_BADGE` map (이용 중 / 만료됨 / 해지됨).
2. `features/membership/ui/MembershipHistoryList.tsx` — a **presentational**
   component (no client directive — pure render in the RSC) taking
   `memberships: MembershipListItem[]`; one row per membership: tier, the lifecycle
   badge, the window (`validFrom ~ validTo`), `planMonths`, and the join date.
3. `app/(main)/membership/history/page.tsx` — Server Component:
   `getFanSession()` → `getMemberships()` → render `MembershipHistoryList` (newest
   first, as the API already orders) or an `EmptyState`; a "← 멤버십" back link.
4. `app/(main)/membership/page.tsx` — add an "이력 보기" link to
   `/membership/history`.
5. Tests (vitest): `historyStatus` mapping (active / expired / canceled);
   `MembershipHistoryList` renders a row per membership with the right badges +
   an empty render. `tsc --noEmit` + `next lint --dir src` + `vitest run` +
   `next build` green.

**Out of scope:** pagination of the history (the list API returns the full set in
this slice; a windowed history is a later follow-up); any write action (history is
read-only); a per-membership detail drill-in.

## Acceptance Criteria

- **AC-1** `/membership/history` lists every membership the caller holds, newest
  window first, each with its tier, window, plan length, and join date.
- **AC-2** Each row shows the correct lifecycle badge: a currently-active membership
  → "이용 중"; a stored-ACTIVE row past its window → "만료됨"; a CANCELED row →
  "해지됨".
- **AC-3** A caller with no memberships sees an `EmptyState` (not an error), with a
  link back to `/membership` to subscribe.
- **AC-4** The `/membership` page links to `/membership/history`.
- **AC-5** The access token never reaches the client bundle — the page reads via the
  `server-only` `getMemberships`; the list/rows are presentational (no client
  component, no action).
- **AC-6** `tsc --noEmit`, `next lint --dir src`, `vitest run`, `next build` green.

## Related Specs

- `specs/contracts/http/membership-api.md` § List (`GET /api/fan/memberships` — the
  caller's memberships, newest first, with read-time `active`).
- `specs/services/fan-platform-web/architecture.md` (FSD lite, server boundary).

## Related Contracts

- `membership-api.md` § List — no change (consumer only).

## Edge Cases

- No memberships → EmptyState (AC-3).
- A stored-ACTIVE row past its window reads `active=false` → badge "만료됨" even
  though `status` is ACTIVE (read-time expiry; consistent with the rest of the UI).
- A renewed pair (an active future-window row stacked behind the currently-active
  one) both appear in the list; the future one reads `active=false` until the
  current window ends — labeled "만료됨" would be wrong, so the helper treats a
  not-yet-started future window (now < validFrom, status ACTIVE) the same as
  "이용 중"? No — read-time `active` is false before the window starts, so it would
  read "만료됨". Mitigation: the helper distinguishes **future** (now < validFrom)
  from **past** (now > validTo) — a not-yet-started window is labeled "예정" rather
  than "만료됨".

## Failure Scenarios

- **Mislabeling a future (renewed) window as expired** — a renewed membership whose
  window has not started yet reads `active=false`; naively that is "만료됨".
  Mitigation: `historyStatus` checks `now < validFrom` → `scheduled` ("예정").
- **History read failure breaking the page** — `getMemberships` already degrades to
  `[]` on error, so an outage shows the EmptyState rather than an error boundary.
