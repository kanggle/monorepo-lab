# TASK-FAN-FE-004 — notification bell + inbox UI

Status: done
Type: frontend (TASK-FAN-FE)
Project: fan-platform
App: fan-platform-web (`projects/fan-platform/web/fan-platform-web`)

---

## Goal

Make the notification-service (FAN-BE-013) **user-visible**. The consumer +
inbox read API already ship (`GET /api/fan/notifications`, `POST /{id}/read` via
gateway `/api/v1/notifications/**`), but nothing in the web app reads them — a fan
who subscribes/cancels gets a WELCOME/CANCELLATION notification they can never see.
This task adds a **header notification bell** (unread badge + recent dropdown +
mark-as-read) and a full **`/notifications` inbox page**, closing the notification
vertical slice's user-visible surface (backend leg = FAN-BE-012 spec / FAN-BE-013
impl), exactly as FAN-FE-003 closed the membership slice.

## Scope

**In scope (fan-platform-web only):**

1. `entities/notification` — `NotificationType` (`WELCOME|CANCELLATION`),
   `NotificationStatus` (`UNREAD|READ`), `Notification` (`id, type, title, body,
   status, read, membershipId?, createdAt, readAt?`). Mirrors the
   notification-service `NotificationResponse` (`@JsonInclude(NON_NULL)` →
   `membershipId`/`readAt` optional).
2. `features/notification/api/getNotifications.ts` (`server-only`):
   - `getNotifications(accessToken, status?)` — `GET /api/v1/notifications`
     (optional `status` filter), reads `res.data` (a **bare array**, not
     `{ content }`). Degrades to `[]` on any error (the bell/page stay useful).
   - `getRecentNotifications(accessToken, limit)` — first page for the dropdown.
   - `getUnreadCount(accessToken)` — `status=UNREAD&size=1`, reads
     `meta.totalElements` (accurate full unread count, not the page length).
3. `features/notification/api/actions.ts` (`'use server'`):
   - `markNotificationRead(id)` — `POST /api/v1/notifications/{id}/read`;
     `revalidatePath('/notifications')`. 404 (foreign/unknown id) is an idempotent
     UI no-op. Auth/transport errors still throw.
   - `markAllNotificationsRead(ids)` — fan-out of `markNotificationRead` over the
     supplied unread ids (no bulk endpoint exists; 404s tolerated), then revalidate.
4. `features/notification/ui/NotificationBell.tsx` (`'use client'`) — bell icon +
   unread badge (hidden at 0, "9+" cap) + dropdown of recent items + "모두 읽음" +
   "전체 보기" link to `/notifications`. **Optimistic local state** (decrement
   count / flip to READ on click) so the bell updates instantly without revalidating
   the shared layout; `useTransition` drives the server action; revert on reject.
5. `features/notification/ui/NotificationList.tsx` (`'use client'`) — full inbox
   rows (type label, title, body, relative time, unread dot, per-row mark-read) +
   "모두 읽음". Used by the `/notifications` page.
6. `features/notification/ui/labels.ts` — `TYPE_LABEL` map + `formatRelative()`
   (pure, unit-tested).
7. `widgets/header/Header.tsx` — when authed, server-fetch
   `getRecentNotifications()` + `getUnreadCount()` (in parallel) and render
   `<NotificationBell>` in the right-hand group (before "내 정보"). Token stays on
   the server; the bell receives only plain data.
8. `app/(main)/notifications/page.tsx` — Server Component inbox: `getFanSession()`
   → `getNotifications(token, statusFilter)` with a `?status=UNREAD|READ` filter
   (전체/안읽음/읽음 tab Links) → `NotificationList` or `EmptyState`.
9. Tests (vitest): `NotificationBell` (badge count + "9+" cap + empty dropdown +
   optimistic mark-read calls the action); `NotificationList` (renders rows +
   per-row + "모두 읽음"); `formatRelative` round-trip; entity type sanity.
   `tsc --noEmit` + `next lint --dir src` + `vitest run` green.

**Out of scope:** real push channels (FCM/APNs/SES — backend mock stays); a
WebSocket/SSE live feed (the bell is server-fetched per navigation + optimistic);
`fan.membership.expired.v1` and its `EXPIRY_REMINDER` type (not yet emitted);
pagination UI on the inbox page (first page only this increment); membership
history page (separate follow-up).

## Acceptance Criteria

- **AC-1** When the authed fan has unread notifications, the header bell shows a
  count badge; 0 unread → no badge; >9 unread → "9+".
- **AC-2** Opening the bell shows the most recent notifications (newest first) with
  WELCOME/CANCELLATION labelled and unread ones visually marked; no items →
  "새 알림이 없습니다".
- **AC-3** Clicking an unread item (bell or inbox) marks it READ via
  `POST /api/v1/notifications/{id}/read`; the unread badge/count decrements
  immediately (optimistic) and the row reflects READ.
- **AC-4** "모두 읽음" marks every currently-unread item READ (fan-out over unread
  ids); the badge clears.
- **AC-5** `/notifications` lists the caller's notifications with 전체/안읽음/읽음
  filter tabs (`?status=`), or an EmptyState when none.
- **AC-6** The access token never reaches the client bundle — all reads via
  `server-only` modules, all writes via `'use server'` actions; no `accessToken`
  prop on any client component.
- **AC-7** A read of an unknown/foreign notification id (404 NOTIFICATION_NOT_FOUND)
  is a silent UI no-op (no error boundary) — matches the inbox no-leak contract.
- **AC-8** `tsc --noEmit`, `next lint --dir src`, `vitest run` all green
  (MONO-166 frontend CI gate).

## Related Specs

- `specs/services/notification-service/architecture.md` § "Inbox Read API" (the
  authoritative surface: `GET /api/fan/notifications?status=&page=&size=`,
  `POST /{id}/read`, tenant+account scoping, 401/404, paginated `{ data, meta }`
  envelope). No separate `notification-api.md` HTTP contract exists — the inbox is
  a thin read surface defined in architecture.md; this task **consumes** it and
  changes no contract.
- `specs/services/fan-platform-web/architecture.md` (FSD lite, server boundary,
  HttpOnly cookie / server-only accessToken).
- `specs/contracts/events/fan-membership-events.md` (the events that produce the
  WELCOME/CANCELLATION notifications — context only).

## Related Contracts

- Inbox surface (architecture.md § Inbox Read API): list/read response =
  `{ data: NotificationResponse[], meta: { page, size, totalElements, timestamp } }`;
  read = `{ data: NotificationResponse, meta }`. `NotificationResponse` =
  `{ id, type, title, body, status, read, membershipId?, createdAt, readAt? }`.
- Gateway route `Path=/api/v1/notifications/**` → `/api/fan/notifications/**`
  (`gateway-service/application.yml`, added by FAN-BE-013).

## Edge Cases

- No notifications yet → no badge; bell dropdown + inbox show the empty state.
- Inbox read response `data` is a **bare array** (`NotificationResponse[]`), unlike
  membership list's `{ data: { content: [] } }` — `getNotifications` reads
  `res.data` directly.
- `membershipId` / `readAt` are **absent** (not null) on the wire for UNREAD /
  non-membership rows (`@JsonInclude(NON_NULL)`) → typed optional.
- Unread count > the dropdown window (e.g. 12 unread, dropdown shows 10) → badge
  shows the true count from `meta.totalElements`, capped "9+".
- Re-marking an already-READ notification → idempotent 200 (contract); optimistic
  state already READ so it is a no-op click.
- Mark-read of a foreign/unknown id → 404, swallowed as a UI no-op (AC-7).

## Failure Scenarios

- **Token leak** — an `accessToken` on a client component ships it in the browser
  bundle. Mitigation: AC-6; reads via `server-only` `getNotifications`, writes via
  `'use server'` actions; `NotificationBell`/`NotificationList` receive only plain
  data.
- **Stale badge after read** — optimistic local decrement keeps the bell correct
  without a layout revalidation; `revalidatePath('/notifications')` keeps the full
  page consistent on next visit.
- **Inbox read failure degrading the whole header** — `getNotifications`/
  `getUnreadCount` swallow errors to `[]`/`0`, so a notification-service outage
  degrades the bell to "no notifications" rather than breaking every authed page.
- **Throwing on the expected 404 no-op** — would replace the page with an error
  boundary; `markNotificationRead` treats 404 as a no-op (AC-7).

## Completion

Implemented + merged as **PR #1295** (squash `5e4add65`). Verified in an isolated
git worktree before merge: `tsc --noEmit` 0, `vitest run` 50/50 (17 new), `next
lint --dir src` 0, `next build` OK (`/notifications` dynamic route). CI all-green
(Frontend lint & build / unit / E2E smoke; observability footprint correctly
skipped — no workflow change). 3-dim merge verified: state=MERGED,
`origin/main` tip == `5e4add65`, pre-merge 0 failing required checks.

Closes the notification vertical slice's user-visible surface (backend leg =
FAN-BE-012 spec / FAN-BE-013 impl). Follow-ups (unchanged): membership history
page · real push channels (FCM/APNs/SES) · `fan.membership.expired.v1` sweeper +
its `EXPIRY_REMINDER` notification type · inbox pagination UI.
