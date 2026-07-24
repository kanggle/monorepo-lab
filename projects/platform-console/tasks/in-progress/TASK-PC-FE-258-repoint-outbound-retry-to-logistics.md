# TASK-PC-FE-258 — repoint operator outbound-retry from wms TMS to logistics dispatch (D8 console half)

**Status:** in-progress
**Type:** TASK-PC-FE
**Depends on / 전제:** [TASK-SCM-BE-045](../../../scm-platform/tasks/done/TASK-SCM-BE-045-dispatch-lookup-by-shipment.md) **done** (the logistics `GET /api/v1/logistics/dispatches/by-shipment/{shipmentId}` lookup this repoint needs — already on main) · [TASK-PC-FE-087](../done/TASK-PC-FE-087-console-wms-outbound-tms-retry-action.md) **done** (the action being repointed) · [ADR-MONO-053](../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) **ACCEPTED** §D8.
**후속 / blocks:** the wms-internal **TMS side-channel retirement** task (retires wms `:retry-tms-notify` + `TmsClientAdapter` + `SHIPPED_NOT_NOTIFIED` + `tms_request_dedupe`). That task is unblocked **only after** this repoint lands — once the console stops calling the wms endpoint, wms can retire it with **zero** cross-project coupling. **Order: this task first, then the wms retirement.**

> **The console half of D8, made a standalone console-internal task by BE-045.** Carrier dispatch moved to `logistics-service` (ADR-053 Phase 1, live). The operator "TMS 재시도" action still calls the wms placeholder `:retry-tms-notify`; this repoints it to the real dispatch recovery, logistics `POST /dispatches/{id}:retry`. **No wms change** — the console already reaches the scm gateway (`SCM_GATEWAY_BASE_URL`, used today for demand-planning mutations), so this needs no new credential/entitlement.

---

## Why this is console-internal (not cross-project)

`logistics-service` is on the **scm gateway** (`/api/v1/logistics/**`, `SCM_GATEWAY_BASE_URL` default `http://scm.local`) — the **same gateway + IAM domain credential** the console already uses to call scm demand-planning `approve`/`dismiss` mutations (`console-integration-contract.md` §2.4.6 / the `/api/scm/demand-planning/**` BFF routes). So the console can call logistics with its **existing** proven credential; nothing on the wms or IAM side changes. BE-045 already added the `shipmentId → dispatch` lookup logistics was missing. Both halves are now decoupled → this is a `projects/platform-console/`-only change.

## Current flow (to repoint)

`OutboundDrillActions` "TMS 재시도" → BFF `POST /api/wms/outbound/{orderId}/retry-tms`
→ `resolveShipmentIdForOrder(orderId)` (wms admin read-model `GET /dashboard/shipments?orderId=`)
→ `retryTmsNotify(shipmentId)` = wms outbound `POST /shipments/{shipmentId}:retry-tms-notify`.

## Target flow

BFF `POST /api/wms/outbound/{orderId}/retry-dispatch` (renamed for honesty)
→ `resolveShipmentIdForOrder(orderId)` — **unchanged** (wms admin still projects the shipment)
→ **NEW** `resolveDispatchIdForShipment(shipmentId)` = logistics (scm gateway) `GET /api/v1/logistics/dispatches/by-shipment/{shipmentId}` → dispatchId; `404 DISPATCH_NOT_FOUND` → no dispatch yet
→ **NEW** `retryDispatch(dispatchId, idempotencyKey)` = logistics `POST /api/v1/logistics/dispatches/{id}:retry`.

## Scope (all under `projects/platform-console/`)

1. **API layer** (`apps/console-web/src/features/wms-outbound-ops/api/`):
   - Keep `resolveShipmentIdForOrder` (wms admin, unchanged).
   - Add `resolveDispatchIdForShipment(shipmentId)` and `retryDispatch(dispatchId, idempotencyKey)` calling the **scm gateway** via the same client pattern the demand-planning mutations use (`SCM_GATEWAY_BASE_URL` + the IAM domain credential). Reuse the existing scm-gateway call helper rather than the wms `callOutbound` client (distinct gateway/base).
   - Remove `retryTmsNotify` from the action path (delete it — the console no longer calls the wms TMS endpoint; do not leave it dead-wired).
2. **BFF proxy route**: rename `apps/console-web/src/app/api/wms/outbound/[orderId]/retry-tms/route.ts` → `.../retry-dispatch/route.ts`; new chain (orderId → shipmentId → dispatchId → `:retry`). Error mapping: `SHIPMENT_NOT_FOUND` (no shipment projected) **and** `DISPATCH_NOT_FOUND` (shipment exists but no dispatch yet — the seam event has not been consumed) both → inline actionable states (no crash). Reason-free (empty body + `Idempotency-Key`), same as today.
3. **Types** (`.../api/types.ts`): `DispatchRefSchema` / `DispatchRetryResult` for the logistics `DispatchResponse` envelope (id, shipmentId, status, trackingNo, carrierCode). Retire the `TmsRetryResult` shape from the action path.
4. **Hook + components**: `hooks/use-outbound-ops.ts`, `components/OutboundDrillActions.tsx`, `components/OutboundOpsScreen.tsx` — repoint the mutation to the new route; relabel **"TMS 재시도" → "발송 재시도"** (dispatch retry). The action stays reason-free and does not pre-gate on role (producer-enforced), as today. The **retry trigger/affordance** should reflect the **logistics dispatch status** (`DISPATCH_FAILED` → retry is meaningful) rather than the wms `SHIPPED_NOT_NOTIFIED` saga state — surface the resolved dispatch's status so the operator sees whether a retry applies. Any vestigial wms `tms_status` / `SHIPPED_NOT_NOTIFIED` display that the retire-wms task will drop may remain harmless, but the **action** must no longer depend on it.
5. **Tests**: `tests/unit/outbound-proxy.test.ts` + `tests/unit/OutboundOpsScreen.test.tsx` — update to the logistics flow (shipment→dispatch resolve, `:retry`, DISPATCH_NOT_FOUND inline).
6. **Contract**: `specs/contracts/console-integration-contract.md` §2.4.5.1 op 10 — repoint the operator-action description from wms `:retry-tms-notify` to logistics `dispatches/{id}:retry` (via the by-shipment resolve), noting the scm-gateway credential reuse and the `DISPATCH_NOT_FOUND` inline state.

**Out of scope:**
- **Any wms change** — the wms `:retry-tms-notify` endpoint + TMS subsystem retirement is the **follow-up wms-internal task** (this repoint unblocks it). If a `projects/wms-platform/` edit appears here, split it out.
- Any change to the wms **admin read-model** shipment resolve (`resolveShipmentIdForOrder`) — it stays.
- 3PL / Phase 2, tracking / Phase 3.

## Acceptance Criteria

- [ ] The operator "발송 재시도" action calls logistics `POST /api/v1/logistics/dispatches/{id}:retry` (scm gateway) — **no** call to wms `:retry-tms-notify` remains anywhere in the console (`grep retry-tms-notify` in `apps/console-web/src` returns nothing).
- [ ] BFF chain: orderId → shipmentId (wms admin) → dispatchId (logistics by-shipment) → `:retry`. A shipment with no dispatch → `DISPATCH_NOT_FOUND` inline actionable (no crash / no retry POST fired), analogous to the existing `SHIPMENT_NOT_FOUND` handling.
- [ ] Uses the existing scm-gateway credential/base (`SCM_GATEWAY_BASE_URL`) — no new env, no new IAM entitlement (verify the demand-planning mutation client pattern is reused).
- [ ] `console-integration-contract.md` §2.4.5.1 op 10 updated to the logistics target.
- [ ] `outbound-proxy.test.ts` + `OutboundOpsScreen.test.tsx` updated and green.
- [ ] `pnpm lint` + `tsc` + `vitest` green (CI Frontend lint & build + Frontend unit; **`pnpm lint` is required — tsc+vitest alone miss console CI-RED**). Frontend E2E smoke green.
- [ ] No wms-platform change; no new error code.

## Related Specs / Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.5.1 op 10 (the action) + §2.4.6 (the scm-gateway credential pattern to reuse) — **must be updated**
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` § logistics-service — the `by-shipment` + `:retry` endpoints being consumed (read-only reference)

## Edge Cases

- **Two-hop resolution, two 404s.** No shipment for the order → `SHIPMENT_NOT_FOUND` (as today). Shipment exists but no dispatch yet (seam event not consumed / logistics lagging) → `DISPATCH_NOT_FOUND`. Both are inline actionable, not errors — the operator sees "아직 발송 접수 전" vs "발송 정보 없음". Do NOT fire the `:retry` POST when either resolve returns null.
- **Different gateway, different client.** logistics is the scm gateway (`SCM_GATEWAY_BASE_URL`), not the wms outbound base. Reuse the demand-planning scm-gateway client + credential — do NOT route logistics calls through the wms `callOutbound` client.
- **Idempotency preserved.** The logistics `:retry` is naturally idempotent (already-`DISPATCHED` → cached ack, no vendor call); keep passing a stable `Idempotency-Key` as the action does today.
- **Label + semantics.** "발송 재시도" now retries a **carrier dispatch** (logistics), not a wms→TMS notify. The operator mental model ("re-send this shipment to the carrier") is preserved; only the backend it hits changes.
- **Host/verify.** Console FE verification needs `pnpm lint` (tsc+vitest miss lint-only CI-RED); the host may be memory-constrained → CI Frontend lanes are the authority.

## Failure Scenarios

- **A — wms edit leaks in.** Any `projects/wms-platform/` change here re-couples D8; the wms retirement is a separate wms-internal task. Split out.
- **B — dead wms call left behind.** If `retryTmsNotify` / the `retry-tms` route remains reachable, the repoint is half-done and the wms retirement will break the console. Remove the wms TMS call path entirely from the console action.
- **C — wrong gateway/credential.** Routing logistics through the wms client (or minting a new token) is wrong — reuse the proven scm-gateway demand-planning client.
- **D — DISPATCH_NOT_FOUND crashes instead of inline.** A missing dispatch must render an inline actionable state (like `SHIPMENT_NOT_FOUND`), never a 500 / unhandled throw.
- **E — lint-skipped green.** Declaring green off `tsc`+`vitest` without `pnpm lint` misses console CI-RED (project precedent). Run lint.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): a BFF-proxy repoint across two gateways + api/types/hooks/components/tests + a contract update, with a two-hop resolve and inline-error handling → **Opus** (frontend-engineer dispatch, `model=opus`). The gateway/credential reuse is the crux — not a mechanical relabel.
