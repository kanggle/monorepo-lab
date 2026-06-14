# Task ID

TASK-PC-FE-085

# Title

console-web — wms outbound **order cancel** operator action (reason-required, role-escalating, async-saga-aware)

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

- **reuses (do NOT re-derive)**: `TASK-PC-FE-057` (`features/wms-outbound-ops`) — the wms `outbound-service` read client + the **per-domain credential rule** (console-integration-contract § 2.4.5.1: wms = IAM `platform-console-web` OIDC **domain-facing** access token via `getDomainFacingToken()`, **never** `getOperatorToken()`) + the forward pick→pack→ship **mutation discipline** (`Idempotency-Key` per confirmed action, order `version` optimistic-lock, confirm-gated dialog, nested wms error envelope). This task adds the **first non-forward** outbound operator action on top of that foundation.
- **contract-promotion (not a redefinition)**: console-integration-contract § 2.4.5.1 currently lists wms outbound **cancel** (§ 1.4) as **"out of v1 console scope — deferred, not silently dropped"** (alongside manual create + TMS retry). This task **promotes cancel into the binding** (new op 9). Authoritative producer = wms [`outbound-service-api.md` § 1.4](../../../wms-platform/specs/contracts/http/outbound-service-api.md) — **consumed unchanged**.
- **divergence from ship/alert-ack (the net-new parts — record what § 1.4 actually requires, do NOT cargo-cult)**:
  - **reason is REQUIRED** (3..500 chars) — unlike the **reason-free** wms alert-ack (no `X-Operator-Reason`) and ship-confirm. So the cancel confirm dialog **does** capture a reason (validated 3..500 client-side; it rides in the JSON body, not a header).
  - **role escalation**: `OUTBOUND_WRITE` for `PICKING` (pre-pick) but **`OUTBOUND_ADMIN` for `PICKED`/`PACKING`/`PACKED`** (post-pick). The console **cannot reliably pre-gate on role** (it does not hold the operator's wms role catalog) → it **attempts** the cancel and maps a `403 FORBIDDEN` to an actionable inline message, plus shows a pre-emptive hint for post-pick orders.
  - **async saga**: the response `sagaState` is `CANCELLATION_REQUESTED` (NOT yet terminal `CANCELLED`) when a reservation was held — it transitions to `CANCELLED` later, once `inventory.released` is consumed. The UI must reflect "취소 요청됨 · 재고 해제 대기" rather than asserting an immediate terminal cancel.
- **contract-extension + mutation discipline → Opus** (분석=Opus 4.8 / 구현 권장=Opus 4.8). Adds a console-side § 2.4.5.1 op row and a mutation with three real divergences (reason-required, role escalation, async saga). Per ADR-MONO-013 § D6 ("contract ext → Opus"; the PC-FE-077 precedent).

# Goal

Add the **cancel** operator action to the console's `/wms/outbound` surface. Today the console drives the order **forward only** (pick→pack→ship); there is no way to **cancel** a stuck / erroneous / customer-cancelled outbound order from the console, even though the producer endpoint (`POST /api/v1/outbound/orders/{id}:cancel`, § 1.4) already exists and the console contract explicitly **deferred** it. Cancel is the one **non-forward** action a warehouse operator genuinely needs — this binds it.

This is a **single mutation** (cancel), reason-required and role-escalating, surfaced on the order drill-in. Manual order **create** (§ 1.1) and **TMS retry** (§ 4.3) stay deferred (see Out of Scope).

# Scope

## In Scope

### Spec-first (console-side, lands before/with code, same PR)

- `projects/platform-console/specs/contracts/console-integration-contract.md` — **promote cancel into § 2.4.5.1**:
  - Add **op 9 | cancel | `POST /orders/{id}:cancel` (§ 1.4) | mutation | `OUTBOUND_WRITE` (PICKING) / `OUTBOUND_ADMIN` (post-pick)** to the op table; remove cancel from the "deferred, not silently dropped" sentence (leaving manual-create + TMS-retry still deferred).
  - Document the mutation shape (mirroring the op 5–8 mutation-discipline block): **`Idempotency-Key`** (UUID, stable per confirmed action / fresh per attempt — same posture as ship); request body `{ reason (3..500, REQUIRED), version (order optimistic-lock, from op 2) }`; allowed when order `status ∈ {PICKING,PICKED,PACKING,PACKED}`; `SHIPPED → 422 ORDER_ALREADY_SHIPPED`; re-cancel idempotent **only if the `Idempotency-Key` matches** else `STATE_TRANSITION_INVALID`; response carries `status`/`previousStatus`/`cancelledReason`/`cancelledAt`/`cancelledBy`/**`sagaState` (`CANCELLATION_REQUESTED` → eventual `CANCELLED`)**/`version`. The **role escalation** (WRITE pre-pick / ADMIN post-pick) is a producer-enforced gate the console surfaces via the 403 mapping, **not** a client-side pre-gate. Producer § 1.4 **consumed unchanged**; § 3 parity matrix **not** mutated (same as the rest of § 2.4.5.1).
- `projects/platform-console/specs/services/console-web/architecture.md` — extend the `features/wms-outbound-ops` map with the cancel proxy route (canonical Identity table + § Service Type Composition untouched; ADR-MONO-012 D3 form preserved).

### Implementation

- `features/wms-outbound-ops/api/outbound-api.ts` — `cancelOrder(orderId, { reason, version, idempotencyKey })` → `POST /orders/${id}:cancel` (note the `:cancel` action suffix — `encodeURIComponent(orderId)` then the literal `:cancel`). Carries `Idempotency-Key`; nested-envelope error parse reused.
- `app/api/wms/outbound/[orderId]/cancel/route.ts` — new **POST** same-origin proxy (mirror the ship route): body `{ reason, version, idempotencyKey }`, forwards `Idempotency-Key` server-side, attaches the domain-facing IAM OIDC token in `outbound-api.ts`. `runtime = 'nodejs'`.
- `features/wms-outbound-ops/hooks/*` — `useCancelOrder()` mutation (mirror `useConfirmShipping`): on success, invalidate the order list + the drilled order detail/saga queries.
- `OutboundOpsScreen.tsx` (drill-in) — a **`취소`** button:
  - **Visible** only when the drilled order `status ∈ {PICKING,PICKED,PACKING,PACKED}` (hidden for `SHIPPED`/`CANCELLED`/terminal). Distinct from the forward pick/pack/ship buttons.
  - Opens a **reason-required confirm dialog** (a `textarea`, client-validated 3..500 chars — submit disabled until valid; this is NOT the reason-free ship/ack dialog). On confirm → `cancelOrder` with the order `version` (from the detail read) + a **fresh `Idempotency-Key`** (`crypto.randomUUID()`, regenerated per a new confirmed attempt).
  - **Post-pick hint** (status ∈ {PICKED,PACKING,PACKED}): inline note "피킹 이후 취소는 관리자(OUTBOUND_ADMIN) 권한이 필요합니다." next to the button.
  - On success: reflect the returned `status` (`CANCELLED`) and, when `sagaState === 'CANCELLATION_REQUESTED'`, a non-blocking "취소 요청됨 · 재고 해제 대기" hint (do NOT assert immediate terminal cancel).
- **Error mapping** (inline actionable, no crash): `422 ORDER_ALREADY_SHIPPED` → "이미 출고된 주문은 취소할 수 없습니다"; `422 STATE_TRANSITION_INVALID` → "주문 상태가 변경되어 취소할 수 없습니다 (목록을 새로고침하세요)"; `403 FORBIDDEN` → "취소 권한이 없습니다 (피킹 이후 취소는 관리자 권한이 필요합니다)"; `409` (optimistic-lock) → stale-version notice + refetch; `404 NOT_FOUND`; `401` → whole-session re-login (existing boundary); `503`/timeout → outbound section degrades only.
- Tests (`tests/unit/` mirroring the existing outbound specs): cancel button visibility by status (shown for the 4 cancellable states, hidden for SHIPPED/CANCELLED); reason-required validation (empty / <3 / >500 → submit blocked, **no** producer call); confirm → POST body carries `reason` + `version` + a fresh `Idempotency-Key`; fresh attempt regenerates the key; `422 ORDER_ALREADY_SHIPPED` → inline (no crash, shell intact); `403` → inline permission message; `sagaState=CANCELLATION_REQUESTED` → async hint surfaced (not asserted as terminal); proxy route forwards `Idempotency-Key` + uses the domain-facing token (never the operator token).

## Out of Scope

- **Manual order create** (`POST /orders`, § 1.1) — stays deferred. Outbound orders are auto-created by the ecommerce fulfillment event (ADR-MONO-022); manual create is a niche exception path, not this task.
- **TMS retry** (§ 4.3, `OUTBOUND_ADMIN`) — separate admin op, stays deferred.
- No producer/backend change (§ 1.4 already implemented — `CancelOrderService`).
- No change to the forward pick→pack→ship lifecycle or the outbound read set.

# Acceptance Criteria

1. The order drill-in shows a **`취소`** action only for cancellable statuses ({PICKING,PICKED,PACKING,PACKED}); it is absent for SHIPPED/CANCELLED.
2. Cancelling requires a **reason (3..500)** — the confirm dialog blocks submission (and fires **no** producer call) for an empty/too-short/too-long reason.
3. A confirmed cancel POSTs `{ reason, version }` with a fresh `Idempotency-Key` to `POST /orders/{id}:cancel`; on success the order reflects `CANCELLED`, and a `CANCELLATION_REQUESTED` saga state shows the non-blocking "재고 해제 대기" hint.
4. Producer errors map to inline actionable states (no crash, shell + other sections intact): `422 ORDER_ALREADY_SHIPPED`, `403 FORBIDDEN` (post-pick admin), `409` stale-version, `404`; `401` → whole-session re-login; `503`/timeout → outbound section degrade only.
5. Post-pick orders show the "관리자 권한 필요" hint pre-emptively.
6. Idempotent re-cancel with the same key is a no-op success; a different key on an already-cancelled order surfaces `STATE_TRANSITION_INVALID` inline.
7. `console-integration-contract.md § 2.4.5.1` binds cancel as op 9 (no longer "deferred"); manual-create + TMS-retry remain deferred.
8. console-web `tsc --noEmit` + `next lint` + `vitest` all green (CI's two frontend jobs gate on lint/tsc).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` — `features/wms-outbound-ops` Layered-by-Feature map (adds the cancel proxy route).
- `projects/wms-platform/specs/services/outbound-service/...` — `CancelOrderService` AC-09 (the producer behaviour this surfaces; unchanged).

# Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` § 1.4 `POST /orders/{id}:cancel` (authoritative producer — **unchanged, consumed only**).
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5.1 (consumer binding — **extended**: cancel promoted to op 9).

# Edge Cases

- **Async saga**: response `sagaState=CANCELLATION_REQUESTED` (reservation held: RESERVED/PICKING_CONFIRMED/PACKING_CONFIRMED) → eventual `CANCELLED` via `inventory.released`. Pre-`RESERVED` (saga `REQUESTED`) may go terminal faster. UI surfaces the requested-state hint, never assumes synchronous terminal.
- **Role escalation**: a `WRITE`-only operator cancelling a post-pick (PICKED/PACKING/PACKED) order → `403 FORBIDDEN` → inline "관리자 권한 필요"; a `PICKING` order cancel by the same operator succeeds. Console attempts, never pre-gates.
- **Already SHIPPED**: `422 ORDER_ALREADY_SHIPPED` (cancel not allowed on terminal-shipped) → inline.
- **Reason bounds**: empty / <3 / >500 chars blocked client-side (mirrors the producer 3..500 validation) — no call fabricated.
- **Stale version**: order advanced (e.g. PICKING→PICKED) between read and cancel → `409` optimistic-lock → refetch + retry hint.
- **Idempotent re-cancel**: same `Idempotency-Key` → no-op success; different key on `CANCELLED` order → `STATE_TRANSITION_INVALID`.

# Failure Scenarios

- wms outbound-service `503` / timeout / network → `WmsOutboundUnavailableError` → outbound section degrades only (shell + `/wms` admin sections intact).
- `401` (IAM OIDC session expired) → whole-session re-login (existing server boundary / api client).
- Malformed/flat error body from wms → nested-envelope parser degrades to a synthetic code rather than crashing (reused behaviour).
- A `403` must NOT trigger a re-login loop (it is a role-insufficient inline state, distinct from 401).
