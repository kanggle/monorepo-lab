# TASK-FAN-FE-006 — Notification inbox pagination (fan-platform-web)

**Status:** done

**Type:** TASK-FAN-FE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (FE-only; the backend page API already exists)

---

## Goal

Add **page navigation** to the notification inbox (`/notifications`) on fan-platform-web. The backend already paginates — `GET /api/v1/notifications` accepts `page`/`size` and returns `meta.{page,size,totalElements}` (notification-service `NotificationInboxController`, default size 20, max 100). But the web inbox loads only the **first page** (`getNotifications` is called with no page, default 50) and renders a flat list with **no way to reach older notifications** — once a fan accumulates more than one page, the rest are unreachable.

This closes that gap with a server-rendered, searchParams-driven pager (`?page=N`), consistent with the inbox's existing `?status=` tab idiom. **FE-only — no backend / contract change** (the page API and its `meta` are already there).

## Scope

**In scope (fan-platform-web only):**
1. **Pure paging helpers** (`features/notification/ui/paging.ts`, no `server-only`, no `'use client'` — so they are unit-testable):
   - `computeTotalPages(totalElements, size)` → `max(1, ceil(total/size))`, `size<=0 ⇒ 1`.
   - `buildNotificationsHref(status, page)` → `/notifications` preserving the `status` filter and target `page` (omits `page=0` and absent status for clean URLs).
2. **`getNotificationPage(accessToken, status?, page=0, size=20)`** (`features/notification/api/getNotifications.ts`, `server-only`) → returns `{ items, page, size, totalElements, totalPages }` by reading the envelope `meta` defensively (`meta` is `Record<string, unknown>`; fall back to request values / `items.length`). Returns an empty page on error (same degrade-to-empty discipline as `getNotifications`). The existing `getNotifications` / `getRecentNotifications` / `getUnreadCount` (used by the header bell) are **unchanged**.
3. **`NotificationPagination`** (`features/notification/ui/NotificationPagination.tsx`, server component) — renders `이전` / `다음` as `<Link>`s (disabled spans at the ends) + a `현재 / 총` indicator; renders **nothing** when `totalPages <= 1`.
4. **`notifications/page.tsx`** — read `?page` (clamp `<0 → 0`) alongside `?status`; call `getNotificationPage(token, statusFilter, page, PAGE_SIZE=20)`; render `NotificationList` with the page's `items` + `NotificationPagination`. Keep the existing status tabs + `EmptyState` (shown only when `totalElements === 0`); when a page beyond the end has no rows but `totalElements > 0`, show a small inline notice + the pager (so the fan can navigate back).
5. **`features/notification/index.ts`** — export `getNotificationPage`, the `NotificationPage` type, `NotificationPagination`, and the pure helpers.
6. **Tests** — `computeTotalPages` + `buildNotificationsHref` (pure), and `NotificationPagination` rendering (hidden at 1 page; `이전` disabled on page 0; `다음` disabled on the last page; indicator text).

**Out of scope:**
- Any backend / contract / gateway change (the page API + `meta` already exist).
- Infinite-scroll / "더 보기" append (server-paged `?page=N` navigation is chosen for RSC consistency with the `?status=` tabs and zero client state).
- The header bell + its dropdown (still shows recent N via `getRecentNotifications`; unchanged).
- The membership history page pagination (separate deferred follow-up).

## Acceptance Criteria

- **AC-1** — With more notifications than one page (size 20), `/notifications` shows the first 20 + a pager; `/notifications?page=1` shows the next page; the `status` filter is preserved across page links (`/notifications?status=UNREAD&page=1`).
- **AC-2** — `이전` is disabled/absent on page 0; `다음` is disabled/absent on the last page; the indicator reads `현재페이지 / 총페이지` (1-based).
- **AC-3** — `totalPages <= 1` ⇒ `NotificationPagination` renders nothing (single-page inboxes look exactly as before).
- **AC-4** — `totalElements === 0` ⇒ the existing `EmptyState` (no pager). A notification-service error ⇒ `getNotificationPage` returns an empty page (degrade-to-empty), `EmptyState` shown, no thrown error (the authed page must not break).
- **AC-5** — `computeTotalPages` / `buildNotificationsHref` unit tests pass; `NotificationPagination` render test passes; `tsc --noEmit`, `vitest`, `next lint`, `next build` all GREEN. The bell (`getNotifications`/`getUnreadCount`) is unaffected.

## Related Specs

- `projects/fan-platform/specs/services/notification-service/architecture.md` (§ Inbox Read API — the paged `GET /api/v1/notifications`, already implemented)
- `projects/fan-platform/PROJECT.md` (trait `read-heavy` — "페이지네이션 최적화")

## Related Contracts

- None changed. The inbox API + `meta.{page,size,totalElements}` already exist; this consumes them.

## Edge Cases

- **`meta` shape variance** — `meta` is typed `Record<string, unknown>`; read `totalElements`/`size`/`page` with `typeof === 'number'` guards, falling back to the request `size`/`page` and `items.length`.
- **Page beyond range** (`?page` ≥ totalPages) — `items` is empty but `totalElements > 0`; show an inline "이 페이지에 알림이 없습니다" + the pager (`이전` works) rather than the global `EmptyState`.
- **Negative / non-numeric `?page`** — clamp to `0`.
- **`size=0` guard** — `computeTotalPages` returns `1` (never divide-by-zero / Infinity pages).
- **Clean URLs** — `buildNotificationsHref` omits `page=0` and an absent status so page-0/all-status links stay `/notifications`.

## Failure Scenarios

- **F1 — older notifications unreachable** — the bug this fixes: without a pager, >1 page of notifications is invisible. Guarded by AC-1.
- **F2 — pager shown for a single page** — visual noise / dead controls. Guarded by AC-3 (`totalPages<=1 ⇒ null`).
- **F3 — inbox breaks on a notification-service outage** — if `getNotificationPage` threw, the authed page would error. Guarded by AC-4 (degrade-to-empty try/catch, same as `getNotifications`).
- **F4 — bell regression** — if the shared `getNotifications`/`getUnreadCount` were altered, the header bell badge could break. Guarded by AC-5 (those are untouched; new `getNotificationPage` is additive).

---

## Closure

- **Impl PR**: #1351 — squash `7d3c6cba94192140a4e1eea6b6cd2692f719b903` (merged 2026-06-12). 3-dim verified: (a) state=MERGED; (b) `origin/main` tip = the squash commit; (c) pre-merge checks = 20 SUCCESS + 1 SKIPPED, **0 failing required**.
- **Delivered**: `paging.ts` (`computeTotalPages` + `buildNotificationsHref`, pure); `getNotificationPage` (server-only, meta-defensive, degrade-to-empty); `NotificationPagination` (server component, `이전`/`다음` Links + 1-based indicator, hidden at 1 page); `notifications/page.tsx` rewired to `?page` + `PAGE_SIZE=20`; `index.ts` exports; `notification-pagination.test.tsx` (11 tests). The header bell's `getNotifications`/`getRecentNotifications`/`getUnreadCount` unchanged.
- **Verification**: `tsc --noEmit` 0, `vitest` 71/71 (11 new + 60 existing unchanged), `next lint` clean, `next build` OK (`/notifications` 2.53 kB) — all local; CI "Frontend lint & build" + "Frontend unit tests" + "Frontend E2E smoke" + Build & Test GREEN.
- **AC**: AC-1…AC-5 all satisfied. **FE-only — no backend / contract / gateway change** (the page API + `meta` already existed).
- **Deferred (out of scope, follow-on)**: infinite-scroll/"더 보기"; membership-history page pagination.
