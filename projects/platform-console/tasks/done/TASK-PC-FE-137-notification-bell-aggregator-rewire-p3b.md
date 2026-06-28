# TASK-PC-FE-137 — Rewire the console notification bell to the console-bff aggregator (ADR-MONO-043 P3b)

**Status:** done

**Type:** TASK-PC-FE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (the shell bell data-source cutover + the §1 aggregated shape/mark-read addressing must be exact; degrade posture preserved)

---

## Goal

ADR-MONO-043 **P3b** (final P3 piece): rewire the platform-console shared-shell **notification bell** from the erp-direct path (`/api/erp/notifications`) to the **console-bff notification aggregator** (`/api/console/notifications/inbox`, TASK-PC-BE-010 / P3a). The bell now reads one merged, per-domain-failure-isolated feed — closing the §1.2 incident (a single domain's outage no longer dictates the bell across every console page). Operator-facing scope = erp (Phase 1, from the aggregator config).

## Scope

`projects/platform-console/apps/console-web/`:

- **New proxy routes** (mirror the operator-overview BFF proxy):
  - `src/app/api/console/notifications/inbox/route.ts` (GET) — forwards `page/size/unread` to `${CONSOLE_BFF_URL}/api/console/notifications/inbox` with `Authorization: Bearer <domainFacingToken>` (+ `X-Operator-Token`/`X-Tenant-Id` when present). Does NOT require an active tenant (the aggregator doesn't; erp reads tenant from JWT). Passthrough 200 (the aggregator always returns 200 per D5); 401 → 401; other/network → 502.
  - `src/app/api/console/notifications/[sourceDomain]/[id]/read/route.ts` (POST) — forwards to the aggregator mark-read; passthrough 200; 401/404 mapped; other → 502.
- **`features/notifications/api/notification-types.ts`** — `NotificationSchema` gains `sourceDomain` (required) + `deepLink?` (§1); `sourceType`/`sourceId` made optional (cross-domain — non-erp items omit them). New `NotificationInboxResponseSchema` (`{ asOf?, items[], meta, degradedDomains[] }`). Legacy `NotificationListResponseSchema` retained (erp-direct client, now bell-unused — removal = follow-up close-chore).
- **`features/notifications/hooks/use-notifications.ts`** — `useNotificationInbox` fetches the aggregator inbox (returns `NotificationInboxResponse`); `useMarkNotificationRead` takes `{ sourceDomain, id }` and POSTs the aggregator mark-read.
- **`features/notifications/components/NotificationBell.tsx`** — reads `data.items` (was `data.data`); mark-read passes `{ sourceDomain, id }`; deep-link prefers `n.deepLink` then the erp `isApprovalSource`/`sourceId` fallback; row key `sourceDomain:id`; a quiet per-domain degrade hint when `degradedDomains` is non-empty (D5 UX).
- **`tests/unit/features/notifications/NotificationBell.test.tsx`** — fixtures → aggregator shape (`items`/`degradedDomains` + `sourceDomain`); mark-read URL assertions → `/api/console/notifications/erp/{id}/read`.

## Out of Scope

- Removing the erp-direct path (`notification-api.ts` + `/api/erp/notifications/**` proxy routes + `NotificationListResponseSchema`) — kept (bell-unused) for a follow-up close-chore (per the P3a Explore recommendation; avoids churning `erp-api.test.ts`).
- Phase-2 domains (fan/ecommerce) in the bell — gated by the aggregator's `consolebff.notifications.domains` config (P3a), zero console-web change.

## Acceptance Criteria

- [x] Bell reads `/api/console/notifications/inbox` (aggregator) via a new same-origin proxy; credential attached server-side (HttpOnly); browser never calls console-bff or a domain directly.
- [x] Mark-read addresses the owning domain (`/api/console/notifications/{sourceDomain}/{id}/read`).
- [x] `NotificationSchema` carries `sourceDomain`; the bell renders `data.items`, sorts/attributes per domain; deep-link prefers `deepLink` then the erp fallback.
- [x] **Degrade preserved + extended**: bell still degrades inert on transport error (`isError`); a non-empty `degradedDomains` shows a quiet per-domain hint (D5 UX) without blanking.
- [x] erp-direct route-count test (`erp-api.test.ts`) unaffected (scans `/api/erp` only; the new routes are under `/api/console`).
- [x] Unit test updated to the aggregator shape.

## Related Specs

- [ADR-MONO-043](../../../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — D2 (aggregator), D5 (failure isolation), D6 (credential dispatch).
- [platform/contracts/notification-inbox-contract.md](../../../../platform/contracts/notification-inbox-contract.md) §1/§4.
- TASK-PC-BE-010 (P3a) — the console-bff aggregator endpoint this bell consumes.

## Related Contracts

- Consumes the P3a aggregator (`GET /api/console/notifications/inbox`, `POST /api/console/notifications/{sourceDomain}/{id}/read`).

## Edge Cases / Failure Scenarios

- **console-bff unreachable / transport error** → proxy 502 → bell `isError` → inert degrade (no crash). Preserved.
- **One domain degraded (aggregator 200 + degradedDomains)** → bell shows the rest + a quiet hint (D5). New.
- **Mark-read failure** → silently ignored; never blocks navigation. Preserved.
- **Active tenant absent** → inbox proxy does NOT 400 (unlike operator-overview); erp reads tenant from JWT. The bell works tenant-agnostically.

## Definition of Done

- [x] Proxy routes + hook + bell + types + unit test rewired to the aggregator.
- [x] erp route-count test unaffected; imports clean (no unused).
- [ ] commit + push (branch `task/pc-fe-137-notification-bell-rewire-p3b`) + PR + CI (Frontend lint&build + Frontend unit + Frontend E2E smoke + Build&Test) GREEN + merge (3-dim verify).
- [ ] Follow-up close-chore: remove the bell-unused erp-direct notification path (`notification-api.ts` + `/api/erp/notifications/**` + `NotificationListResponseSchema`).
