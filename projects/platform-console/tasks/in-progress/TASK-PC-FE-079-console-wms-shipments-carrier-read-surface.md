# Task ID

TASK-PC-FE-079

# Title

console-web — wms shipments / 택배(carrier) read surface (carrier code · tracking no · shipped-at on the WMS operations screen)

# Status

in-progress

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

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

# Dependency Markers

- **reuses (do NOT re-derive)**: `TASK-PC-FE-007` (`features/wms-ops`) — the wms `admin-service` read client (`wms-api.ts`), the NESTED error-envelope parser (`parseWmsError`), the `X-Read-Model-Lag-Seconds` lag-hint surfacing, the eventual-consistency "surface-not-poll" query posture, and the **per-domain credential rule** (console-integration-contract § 2.4.5: wms = IAM `platform-console-web` OIDC **access token** via `getDomainFacingToken()`, **never** the IAM operator-token exchange). This task only **surfaces an already-contracted, already-clientized read** — it adds no new credential, no new mutation, no contract change.
- **already contracted (no spec/contract change)**: the shipments read is **row 5** of console-integration-contract § 2.4.5's read table (`GET /api/v1/admin/dashboard/shipments`, kind = read) and the client `listShipments()` + `ShipmentPageSchema` already exist in `features/wms-ops` (built in the FE-007 era) but were **never surfaced in the UI**. This task closes that gap: it wires the existing read into a visible operations section. Because the binding already exists, **§ 2.4.5 is consumed unchanged — there is no contract edit**.
- **read-only — no mutation discipline**: unlike FE-057 (wms outbound `confirmShipping`, a mutation) and FE-077 (scm approve/dismiss), this surface is a **pure read**. No `Idempotency-Key`, no confirm dialog, no `X-Operator-Reason` — surfacing any of those would be header-matrix drift. The WMS shipment record (incl. `carrierCode` / `trackingNo`) is **projected** from `outbound.shipping.confirmed` (admin-service `ShipmentSummary`, domain-model.md § 9); the console only **reads** it.
- **scope boundary (read what the user actually asked, not the ecommerce carrier aggregator)**: this surfaces the **wms** outbound shipment carrier fields (carrier code / tracking no, projected into the wms admin-service read-model). It is **distinct** from the ecommerce ADR-007 carrier aggregator (shipping-service, surfaced in ecommerce's own admin-dashboard, TASK-FE-073) — that is a separate domain/app and out of scope here.
- **routine read-surfacing → Sonnet** (분석=Opus 4.8 / 구현 권장=Sonnet 4.6). No contract extension, no new auth model, established feature-local pattern (mirrors the inventory read end-to-end).

# Goal

Surface the **wms shipment / 택배(carrier) read** on the console's WMS operations screen (`/wms`). Today the operator can see inventory + alerts there, and can *confirm* a shipment on `/wms/outbound` (which assigns a `carrierCode`), but there is **no screen anywhere in the console to VIEW shipment records** — carrier code, tracking number, shipped-at, shipment no. The read is already contracted (§ 2.4.5 row 5) and the client (`listShipments`) already exists; only the UI is missing.

Add a **"택배 / 출고 (Shipments)"** read-only section to `WmsOpsScreen`, server-seeded page-0 (like inventory), with a filter (warehouse id · carrier code) and pagination, rendering the `ShipmentSummary` fields the operator needs: 출고번호(`shipmentNo`), 주문번호(`orderNo`), 택배사(`carrierCode`), 운송장번호(`trackingNo`), 출고시각(`shippedAt`), 수량(`totalQty`).

# Scope

## In Scope

- `features/wms-ops/api/types.ts` — replace the generic `ShipmentPageSchema` (currently `wmsPage(GenericRowSchema)`) with a **typed, tolerant** `ShipmentRowSchema` (`shipmentId` required; `orderId`/`orderNo`/`warehouseId`/`shipmentNo`/`carrierCode`/`trackingNo`/`shippedAt`/`totalQty` optional + `.passthrough()` per the § 2.4.5 unknown/future-field tolerance invariant). Add `ShipmentQueryParams` (`warehouseId?`, `carrierCode?`, `page?`, `size?`).
- `features/wms-ops/api/wms-api.ts` — `listShipments()` **already exists**; no change beyond the typed schema it now returns.
- `features/wms-ops/api/wms-state.ts` — add `shipments` to the server-seeded reads (`getWmsSectionState` parallel fan-out alongside inventory/alerts), fold its `lagSeconds` into the max, add `shipments: ShipmentPage | null` to `WmsSectionState`.
- `features/wms-ops/hooks/use-wms-ops.ts` — add `shipmentsKey()`, `buildShipmentsQs()`, `fetchShipments()`, `useWmsShipments()` mirroring the inventory read hook (seeded page-0, `staleTime` 30s when seeded, NO refetch interval / window-focus — surface-not-poll).
- `app/api/wms/shipments/route.ts` — new same-origin **GET-only** read proxy (mirror `app/api/wms/inventory/route.ts`): parse `warehouseId`/`carrierCode`/`page`/`size`, call `listShipments`, `mapWmsError`. `runtime = 'nodejs'`. No mutation branch, no Idempotency-Key.
- `features/wms-ops/components/WmsOpsScreen.tsx` — add the read-only **"택배 / 출고"** section (between inventory and alerts): warehouse-id + carrier-code filter form, results table (the 6 fields above), prev/next pagination, and the same resilience states as inventory (403 forbidden inline / 503 degraded inline / empty). `data-testid` parity with the inventory section (`wms-ship-*`).
- `features/wms-ops/index.ts` — export `ShipmentPage` / `ShipmentRow` / `ShipmentQueryParams` types.
- `app/(console)/wms/page.tsx` — pass `shipments={state.shipments}` into `WmsOpsScreen`; include `!state.shipments` in the degraded guard.
- Tests (`tests/unit/WmsOpsScreen.test.tsx` + a hooks/proxy test as the existing structure dictates) — shipments section renders seeded rows; carrier-code filter triggers a new query key; 403 → inline forbidden; 503 → inline degraded; empty → empty state; unknown/future field passthrough does not throw.

## Out of Scope

- Any **mutation** on shipments (the wms shipment is read-model-only on this surface; confirm-shipping stays on `/wms/outbound`, FE-057).
- The **ecommerce ADR-007 carrier aggregator** (shipping-service tracking refresh / webhook) — separate domain + separate app (ecommerce admin-dashboard, TASK-FE-073). Not federated into the console by this task.
- A dedicated per-shipment detail drill-in (only the list + filter in this slice; detail can follow if needed).
- Any change to wms specs/contracts or `console-integration-contract.md` (§ 2.4.5 row 5 already binds this read — consumed unchanged).

# Acceptance Criteria

1. `/wms` renders a **"택배 / 출고"** section showing server-seeded shipment rows with 택배사(carrierCode) + 운송장번호(trackingNo) + 출고시각 + 출고번호 + 주문번호 + 수량.
2. The warehouse-id / carrier-code filter submits a new query (new React Query key) and re-renders; pagination prev/next works and shows `n / total` page info.
3. Resilience parity with inventory: 403 → inline "권한 없음" (no crash, no re-login loop); 503/timeout → this section degrades inline only (shell + other sections intact); 401 still forces whole-session re-login via the existing server boundary.
4. Read-only: no Idempotency-Key, no confirm dialog, no `X-Operator-Reason` anywhere in the shipments path; the proxy route is GET-only.
5. Tolerant parse: an unknown/future field on a shipment row passes through and never throws (§ 2.4.5 tolerance invariant).
6. `pnpm lint` + `tsc --noEmit` + `vitest` all green in `console-web` (CI's two frontend jobs gate on lint/tsc; vitest covers the unit behavior).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` — Layered-by-Feature; `features/wms-ops` already mapped (this adds a read + proxy within the existing feature, no new module).
- `projects/wms-platform/specs/services/admin-service/domain-model.md` § 9 `ShipmentSummary` (the projected read-model row this surfaces).

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5 row 5 (shipments read — **consumed unchanged**, no edit).
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.3 `GET /api/v1/admin/dashboard/shipments` (authoritative producer — unchanged).

# Edge Cases

- `carrierCode` / `trackingNo` are **nullable** (domain-model § 9) — render `—` when absent (a shipment confirmed without a carrier assigned yet).
- Empty shipments page → friendly empty state (`wms-ship-empty`), not a degraded notice.
- Unknown/future enum or extra field on a row → tolerant passthrough (no throw).
- Operator not wms-eligible → the existing `notEligible` page guard already blocks before any wms call (no shipments call fabricated).
- Read-model lag → the existing `lagSeconds` banner already covers the whole wms section (fold shipments lag into the max).

# Failure Scenarios

- wms admin-service 503 / timeout / network → `WmsUnavailableError` → shipments section inline degraded only (shell + inventory + alerts intact).
- 403 (role-insufficient, e.g. below `WMS_VIEWER`) → inline "권한 없음".
- 401 (IAM OIDC session expired) → whole-session re-login (handled by the existing server boundary / api client — not a per-section state).
- Malformed/flat error body from wms → `parseWmsError` degrades to a synthetic code rather than crashing (existing behavior, reused).
