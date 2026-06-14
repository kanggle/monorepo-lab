# Task ID

TASK-PC-FE-087

# Title

console-web — wms outbound **manual TMS retry** operator action (admin-gated, shipment-id resolved from the admin read-model, saga-`SHIPPED_NOT_NOTIFIED`-aware)

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
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

- **reuses (do NOT re-derive)**: `TASK-PC-FE-057` (`features/wms-outbound-ops`) — the wms `outbound-service` client + the **per-domain credential rule** (console-integration-contract § 2.4.5.1: wms = IAM **domain-facing** OIDC access token via `getDomainFacingToken()`, **never** `getOperatorToken()`; no `X-Tenant-Id`; nested wms error envelope) + the **mutation discipline** (`Idempotency-Key` per confirmed action, confirm-gated dialog). And `TASK-PC-FE-085` (`cancel`) — the **first non-forward** admin action this builds the second of; reuse its 403→inline / confirm-dialog / fresh-key posture.
- **contract-promotion (not a redefinition)**: console-integration-contract § 2.4.5.1 currently lists wms outbound **TMS retry** (§ 4.3) as **"out of v1 console scope — deferred, not silently dropped"** (alongside manual create). This task **promotes TMS retry into the binding** (new op 10). Authoritative producer = wms [`outbound-service-api.md` § 4.3](../../../wms-platform/specs/contracts/http/outbound-service-api.md) — **consumed unchanged**.
- **the data-discovery divergence (the net-new part — record what is actually required, do NOT cargo-cult cancel)**: TMS retry operates on a **`shipmentId`** (`POST /shipments/{id}:retry-tms-notify`), but the outbound-service **order-centric reads carry no `shipmentId`** (§ 1.2 order detail = create-response shape — no shipment block; there is no `GET /orders/{id}/shipments`). The `shipmentId` is resolved server-side from the **admin read-model** `GET /api/v1/admin/dashboard/shipments?orderId={id}` (`WMS_ADMIN_BASE_URL` — already consumed by `features/wms-ops`; the `orderId` filter is contracted). **Same wms gateway + same IAM-OIDC domain-facing credential, different path prefix** (§ 2.4.5 / § 2.4.5.1) — so a single proxy route legitimately reads admin + mutates outbound. The retry-needed **signal** is the order saga state `SHIPPED_NOT_NOTIFIED` (already read in the drill via § 5.1), NOT a `tmsStatus` field (the admin `ShipmentSummary` read-model does not project `tmsStatus`).
- **admin-only, reason-free**: § 4.3 requires `OUTBOUND_ADMIN` (unlike cancel's WRITE-or-ADMIN escalation) and is **reason-free** (re-notify only; stock already consumed — unlike cancel's required reason). Empty/`{}` body + `Idempotency-Key`. The console does **not** pre-gate on role — it attempts and maps a `403 FORBIDDEN` to an inline message + a pre-emptive "관리자(OUTBOUND_ADMIN) 권한 필요" hint.
- **contract-extension + cross-read mutation → Opus** (분석=Opus 4.8 / 구현 권장=Opus 4.8). Adds a § 2.4.5.1 op row and a mutation with a real cross-base-URL data-discovery step. Per ADR-MONO-013 § D6 ("contract ext → Opus").

# Goal

Add the **manual TMS retry** operator action to the console's `/wms/outbound` surface. When an order ships but TMS (carrier) notification fails, the producer marks the shipment `tmsStatus = NOTIFY_FAILED` and the order saga goes `SHIPPED_NOT_NOTIFIED` (an alert fires). Today the console can **see** the stuck saga state on the order drill but offers **no way to recover it** — the operator cannot re-trigger the carrier notification, even though the producer endpoint (`POST /api/v1/outbound/shipments/{id}:retry-tms-notify`, § 4.3) already exists and the console contract explicitly **deferred** it. TMS retry is the natural **recovery** sibling to the cancel action (PC-FE-085) — together they complete the outbound non-forward admin action set.

This is a **single admin mutation** (retry), reason-free, surfaced on the order drill-in only for `status=SHIPPED` + saga `SHIPPED_NOT_NOTIFIED`. Manual order **create** (§ 1.1) stays deferred (see Out of Scope).

# Scope

## In Scope

### Spec-first (console-side, lands with code, same PR)

- `projects/platform-console/specs/contracts/console-integration-contract.md` — **promote TMS retry into § 2.4.5.1**:
  - Add **op 10 | retry TMS notify | `POST /shipments/{id}:retry-tms-notify` (§ 4.3) | mutation | `OUTBOUND_ADMIN`** to the op table; remove TMS retry from the "deferred, not silently dropped" sentence (leaving **manual-create** still deferred).
  - Document the mutation shape: reason-free empty/`{}` body + **`Idempotency-Key`** (UUID, stable per confirmed retry / fresh per attempt); allowed only when the shipment `tmsStatus == NOTIFY_FAILED` (order saga `SHIPPED_NOT_NOTIFIED`) — else `422 STATE_TRANSITION_INVALID`; producer-enforced `OUTBOUND_ADMIN` (the console surfaces this via the 403 mapping, NOT a client-side pre-gate). Document the **shipment-id resolution**: server-side `GET /api/v1/admin/dashboard/shipments?orderId={id}` (`WMS_ADMIN_BASE_URL`, same IAM-OIDC credential) → first `shipmentId`; no shipment → `404 SHIPMENT_NOT_FOUND` inline. Producer § 4.3 + admin § 1.3 **consumed unchanged**; § 3 parity matrix **not** mutated.
- `projects/platform-console/specs/services/console-web/architecture.md` — extend the `features/wms-outbound-ops` map with the retry-tms proxy route + the admin shipment-id resolver.

### Implementation

- `features/wms-outbound-ops/api/types.ts` — `TmsRetryResultSchema` (tolerant: `shipmentId`/`tmsStatus`/`tmsNotifiedAt`/`trackingNo`/`sagaState`/`retriedAt`/`retriedBy` optional+passthrough) + a minimal tolerant `AdminShipmentRefPageSchema` (`{ content: [{ shipmentId }], ... }`) for the id resolver + `canRetryTms(status, saga)` gate (`status==='SHIPPED' && saga==='SHIPPED_NOT_NOTIFIED'`).
- `features/wms-outbound-ops/api/outbound-api.ts` — extend the single hardened `callOutbound` with optional `baseUrl`/`timeoutMs` overrides (default = outbound base/timeout) so the same token + abort + nested-envelope error mapping is reused for the admin read; add `resolveShipmentIdForOrder(orderId)` (admin `GET /dashboard/shipments?orderId&size=1` → first `shipmentId | null`) + `retryTmsNotify(shipmentId, idempotencyKey)` (`POST /shipments/{id}:retry-tms-notify`, note the `:retry-tms-notify` action suffix).
- `app/api/wms/outbound/[orderId]/retry-tms/route.ts` — new **POST** same-origin proxy (mirror cancel): body = `{ idempotencyKey }` (reuse `ActionBodySchema` — reason-free); resolve `shipmentId` → if null `404 SHIPMENT_NOT_FOUND`; else `retryTmsNotify`; `mapOutboundError`. `runtime = 'nodejs'`.
- `features/wms-outbound-ops/hooks/use-outbound-ops.ts` — `useRetryTms()` mutation (mirror `useCancelOrder`, reason-free): on success invalidate the order list + the drilled order detail/saga queries (saga should move `SHIPPED_NOT_NOTIFIED → COMPLETED` on success).
- `OutboundOpsScreen.tsx` (drill-in) — a **`TMS 재전송`** admin action:
  - **Visible** only when `canRetryTms(status, saga)` (status `SHIPPED` + saga `SHIPPED_NOT_NOTIFIED`); hidden otherwise.
  - Inline **"관리자(OUTBOUND_ADMIN) 권한 필요"** hint next to the button (pre-emptive).
  - Opens a **reason-free confirm dialog** (reuse `OutboundActionDialog`) — on confirm → `retryTms` with a **fresh `Idempotency-Key`** (`crypto.randomUUID()` per attempt).
  - On success: surface the returned `tmsStatus`/`sagaState` (e.g. `NOTIFIED`/`COMPLETED`) so the operator sees recovery; on a still-failed result the action remains available to retry again.
- **Error mapping** (inline actionable, no crash): `404 SHIPMENT_NOT_FOUND` → "출고 건을 찾을 수 없습니다 (TMS 재전송 대상 없음)"; `422 STATE_TRANSITION_INVALID` → "이미 정상 통보되었거나 재전송 대상 상태가 아닙니다"; `403 FORBIDDEN` → "TMS 재전송 권한이 없습니다 (관리자(OUTBOUND_ADMIN) 권한 필요)"; `409 DUPLICATE_REQUEST` → idempotent no-op notice; `401` → whole-session re-login (existing boundary); `503`/timeout → outbound section degrades only.
- Tests (`tests/unit/` mirroring the outbound specs): retry button visibility by status+saga (shown only for SHIPPED + SHIPPED_NOT_NOTIFIED; hidden for SHIPPED + COMPLETED and for non-SHIPPED); admin hint present; confirm → POST to the retry route with a fresh `Idempotency-Key`; `403` → inline permission message; `404 SHIPMENT_NOT_FOUND` → inline; proxy route resolves shipment-id via the admin read then retries, forwards `Idempotency-Key`, uses the domain-facing token (never the operator token); no-shipment → 404 (no outbound POST fired).

## Out of Scope

- **Manual order create** (`POST /orders`, § 1.1) — stays deferred (auto-created by the ecommerce fulfillment event, ADR-MONO-022).
- **Adding `tmsStatus` to the admin `ShipmentSummary` read-model** (a wms producer change) — NOT needed; the retry-needed signal is the order saga `SHIPPED_NOT_NOTIFIED`, already read in the drill. (A future shipment-table retry surface in `features/wms-ops` would want it, but that is a separate task.)
- No producer/backend change (§ 4.3 + admin § 1.3 already implemented).
- No change to the forward pick→pack→ship lifecycle, the cancel action, or the outbound read set.

# Acceptance Criteria

1. The order drill-in shows a **`TMS 재전송`** action **only** when `status=SHIPPED` AND saga `SHIPPED_NOT_NOTIFIED`; it is absent for a healthy SHIPPED (`COMPLETED`) order and for any non-SHIPPED status.
2. A confirmed retry resolves the `shipmentId` server-side from `GET /api/v1/admin/dashboard/shipments?orderId={id}` then POSTs (reason-free, fresh `Idempotency-Key`) to `POST /shipments/{shipmentId}:retry-tms-notify`; on success the drill reflects the recovered `tmsStatus`/`sagaState`.
3. When no shipment resolves for the order, the proxy returns `404 SHIPMENT_NOT_FOUND` inline and fires **no** outbound retry POST.
4. Producer errors map to inline actionable states (no crash, shell + other sections intact): `404 SHIPMENT_NOT_FOUND`, `422 STATE_TRANSITION_INVALID`, `403 FORBIDDEN` (admin), `409 DUPLICATE_REQUEST`; `401` → whole-session re-login; `503`/timeout → outbound section degrade only.
5. The action shows a pre-emptive "관리자(OUTBOUND_ADMIN) 권한 필요" hint.
6. `console-integration-contract.md § 2.4.5.1` binds TMS retry as op 10 (no longer "deferred"); manual-create remains deferred.
7. console-web `tsc --noEmit` + `next lint` + `vitest` all green (CI's two frontend jobs gate on lint/tsc).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` — `features/wms-outbound-ops` Layered-by-Feature map (adds the retry-tms proxy route + admin shipment-id resolver).
- `projects/wms-platform/specs/services/outbound-service/...` — the `RetryTmsNotify` / TMS notification port behaviour this surfaces (unchanged).

# Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` § 4.3 `POST /shipments/{id}:retry-tms-notify` + § 5.1 saga (`SHIPPED_NOT_NOTIFIED`) — authoritative producer, **unchanged, consumed only**.
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.3 `GET /dashboard/shipments?orderId` — shipment-id resolver source, **unchanged, consumed only**.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5.1 (consumer binding — **extended**: TMS retry promoted to op 10).

# Edge Cases

- **No shipment for the order** (resolver returns empty) → `404 SHIPMENT_NOT_FOUND` inline; no outbound POST fired.
- **Not in NOTIFY_FAILED** (already `NOTIFIED`/`COMPLETED`, or someone retried concurrently) → `422 STATE_TRANSITION_INVALID` inline; the button is also gated off once the drill refetch shows the saga left `SHIPPED_NOT_NOTIFIED`.
- **Role**: a non-admin operator → `403 FORBIDDEN` → inline "관리자 권한 필요" (console attempts, never pre-gates). Pre-emptive hint shown regardless.
- **Idempotent re-retry**: same `Idempotency-Key` → `409 DUPLICATE_REQUEST` no-op success notice (no double carrier notification).
- **Retry itself fails again** (carrier still down): `tmsStatus` stays `NOTIFY_FAILED` / saga stays `SHIPPED_NOT_NOTIFIED` → the action remains available for another attempt.
- **Admin read degrades but outbound is up** (or vice-versa): the resolve step maps `503`/timeout to the outbound-section degrade (same `WmsOutboundUnavailableError` taxonomy) — never a hard crash.

# Failure Scenarios

- wms outbound-service or admin-service `503` / timeout / network → `WmsOutboundUnavailableError` → outbound section degrades only (shell + `/wms` admin sections intact).
- `401` (IAM OIDC session expired) → whole-session re-login (existing server boundary / api client).
- Malformed/flat error body from wms → nested-envelope parser degrades to a synthetic code rather than crashing (reused behaviour).
- A `403` must NOT trigger a re-login loop (it is a role-insufficient inline state, distinct from 401).
