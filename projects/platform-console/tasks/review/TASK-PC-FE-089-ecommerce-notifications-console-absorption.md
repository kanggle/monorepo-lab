# TASK-PC-FE-089 — console ecommerce **notification template** operator surface (ADR-031 Phase 5b)

**Status:** review
**Area:** platform-console / console-web · **Feature:** `features/ecommerce-ops` (notifications slice)
**Parent:** ADR-MONO-031 Phase 5 (absorb ecommerce admin-dashboard notification surface into the unified console)
**Precondition:** TASK-BE-373 (notification-service row-level `tenant_id` + `GET /templates/{id}` gap-fill) — the §2.4.10 tenant-isolation gate.

## Goal

Absorb the ecommerce admin-dashboard **notification template management** operator surface into platform-console
`features/ecommerce-ops`, mirroring the **promotions** slice (list + create + edit mutation template). This is the
**last operator area** before ADR-031 Phase 6 (admin-dashboard app deletion). After this, an operator manages
notification templates entirely from the console.

## Authoritative producer surface (4 endpoints — admin-dashboard parity)

All under the ecommerce gateway, **`ECOMMERCE_PUBLIC_BASE_URL` (`http://ecommerce.local/api`) + `/notifications/templates`**
(i.e. `/api/notifications/**`, the **non-admin** path — promotions/shippings model, NOT `ECOMMERCE_ADMIN_BASE_URL`).
All admin-guarded (`X-User-Role=ADMIN`, gateway-injected from the domain-facing OIDC token):

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `GET` | `/api/notifications/templates?page=&size=` | paginated template list (summary: templateId, type, channel, subject, createdAt) |
| 2 | `GET` | `/api/notifications/templates/{templateId}` | template **detail** (full, incl. `body`) — **the new BE-373 endpoint**; backs the edit page |
| 3 | `POST` | `/api/notifications/templates` | create; body `{type, channel, subject, body}` → 201 `{templateId}` |
| 4 | `PUT` | `/api/notifications/templates/{templateId}` | update; body `{subject, body}` — **type/channel are immutable after creation** (producer accepts only subject+body) |

**No delete** (producer defines none). **No consumer/preference/notification-log surface** (those are shopper-plane `/me` endpoints — out of scope).

## Enums (mirror in UI)
- **TemplateType**: `ORDER_PLACED` (주문 완료), `PAYMENT_COMPLETED` (결제 완료), `SHIPPING_STATUS_CHANGED` (배송 상태 변경), `WELCOME` (회원 가입).
- **NotificationChannel**: `EMAIL`, `SMS`, `PUSH`.
- On **create**, type + channel are selectable; on **edit**, they are read-only (display only) — only subject + body editable.

## Scope — mirror the promotions slice

Reference (read first): the `features/ecommerce-ops` **promotions** slice (PromotionsScreen, PromotionForm,
PromotionDetail, promotions-api, promotion Zod types, promotions-state, use-ecommerce-promotions + route handlers
+ pages) and the **shippings** slice (latest landed). Notifications = list + create + edit (no delete, no coupon-style action).

Create under `projects/platform-console/apps/console-web/src/features/ecommerce-ops/`:
- `api/notification-types.ts` — Zod: `NotificationTemplateSummary` (list row), `NotificationTemplateDetail`
  (full, incl. body, createdAt, updatedAt), `TEMPLATE_TYPE_VALUES`, `NOTIFICATION_CHANNEL_VALUES`,
  create/update body schemas.
- `api/notifications-api.ts` — `listTemplates(params)`, `getTemplate(id)`, `createTemplate(body)`,
  `updateTemplate(id, body)`. `getDomainFacingToken()` (**never** `getOperatorToken()`),
  `ECOMMERCE_PUBLIC_BASE_URL + /notifications/templates`, flat error envelope `{code,message,timestamp}`,
  **no** `X-Tenant-Id`, **no** `Idempotency-Key`. Mirror `promotions-api.ts` resilience/inline-error handling.
- `api/notifications-state.ts` — server-side section state loader (mirror `promotions-state.ts`).
- `hooks/use-ecommerce-notifications.ts` — list query + getTemplate query (for edit) + create + update mutations
  (invalidate the list key on success).
- `components/NotificationsScreen.tsx` — template list table (type, channel, subject, createdAt), pagination,
  "템플릿 등록" link, row → edit.
- `components/TemplateForm.tsx` — create (type+channel selects + subject + body) and edit (type+channel read-only,
  subject + body editable) modes; confirm-gate on submit (mirror PromotionForm).
- (optional) `components/TemplateDetail.tsx` if a separate detail view is wanted (admin-dashboard goes list→edit directly).

Route handlers (Next.js, **direct to ecommerce gateway, no console-bff write leg** — ADR-017 D2.A) under
`src/app/api/ecommerce/notifications/templates/`:
- `route.ts` — `GET` (list) + `POST` (create).
- `[id]/route.ts` — `GET` (detail) + `PUT` (update).

Pages under `src/app/(console)/ecommerce/notifications/templates/`:
- `page.tsx` — list (eligibility waterfall: registryDegraded → notEligible → forbidden → degraded → happy; mirror promotions/page.tsx).
- `new/page.tsx` — create.
- `[id]/edit/page.tsx` — edit (loads detail via the BE-373 GET endpoint; 404 → notFound).

Sidebar: add an `알림` leaf to the ecommerce `NavParent` children in
`src/shared/ui/ConsoleSidebarNav.tsx` (after the `배송` leaf):
`{ href: '/ecommerce/notifications/templates', label: '알림', testid: 'nav-ecommerce-notifications' }`.

Contract: add **§2.4.10.4 (notifications)** to `projects/platform-console/specs/contracts/console-integration-contract.md`,
immediately after §2.4.10.3 (shippings). Follow the §2.4.10.2/.3 structure: opening (unblocked by TASK-BE-373
notification `tenant_id` + the GET /templates/{id} gap-fill, ADR-030 Step 4), "inherits §2.4.10 cross-cutting rules
verbatim", the 4-endpoint authoritative-producer table, immutable type/channel-on-edit note, no-delete note,
"Producer immutability", "Not a §3 parity row". This is the **final §2.4.10.x** area (note that all 6 operator
areas are now absorbed → Phase 6 app-deletion gate is unblocked).

## Out of scope
- No delete, no notification-log admin view, no preference admin (consumer-self-service only).
- No backend change (BE-373 is the precondition, separate task/PR).
- `getOperatorToken()`, `X-Tenant-Id` header, `Idempotency-Key`, console-bff write leg — forbidden (§2.4.10).

## Acceptance Criteria
- `tsc --noEmit` → 0 errors.
- `pnpm --filter console-web lint` → clean (no-unused-vars etc. — **mandatory**; CI fails the two frontend jobs otherwise).
- vitest → all green incl. new tests (mirror promotions slice coverage: credential pin = getDomainFacingToken not
  getOperatorToken; resilience 401/403/503; create requires type+channel; edit keeps type/channel read-only).
- Sidebar `알림` leaf present; pages render the eligibility waterfall; edit page loads detail + 404s missing.
- Contract §2.4.10.4 added (and notes all 6 areas absorbed).
- products/orders/users/promotions/image/shippings slices, console-bff, backend: **0-change**.

## Related Specs / Contracts
- `docs/adr/ADR-MONO-031-ecommerce-admin-console-consolidation.md` (Phase 5)
- `console-integration-contract.md` §2.4.10 (cross-cutting) + new §2.4.10.4
- Producer: `notification-service` `TemplateController` (`/api/notifications/templates`) — list / detail / create / update.

## Edge Cases
- Cross-tenant or missing `templateId` on detail/update → producer 404 (BE-373) → inline "not found" / notFound page.
- Create with duplicate (type, channel) within tenant → producer 409 (TemplateAlreadyExists) → inline.
- Edit must not send type/channel (producer ignores; UI keeps them read-only).
- 401 → whole-session IAM re-login; 403 → "not available to your role"; 503/timeout → only this section degrades.

## Failure Scenarios
- Using `ECOMMERCE_ADMIN_BASE_URL` (`/api/admin`) → 404 (no `/api/admin/notifications` route). Notifications live at `/api/notifications` (promotions/shippings model).
- Allowing type/channel edit → producer ignores them silently; keep them read-only on edit to avoid operator confusion.
- Skipping `pnpm lint` before push → CI "Frontend lint & build" + "Frontend unit tests" RED.
