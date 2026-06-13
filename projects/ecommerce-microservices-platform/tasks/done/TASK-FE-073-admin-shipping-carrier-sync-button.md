# Task ID

TASK-FE-073

# Title

Add an operator-facing **"택배사 동기화" (carrier sync / refresh-tracking) button** to the admin-dashboard shipping management surface so an operator can force an on-demand pull of the live aggregator status (Delivery Tracker, ADR-007) instead of waiting for the unattended auto-collect scheduler (TASK-BE-360). The backend endpoint `POST /api/shippings/{shippingId}/refresh-tracking` (admin-only, TASK-BE-293) already exists and is published; this task is the missing **admin UI trigger** for it. Frontend-only (admin-dashboard + the shared `@repo/api-client`); no backend / contract change.

# Status

done

# Owner

frontend-engineer (admin-dashboard `features/shipping-management` + `packages/api-client`). NOT backend — the `refresh-tracking` endpoint + the Delivery Tracker pull adapter (BE-293/360/362/364) are already live; this only wires the existing endpoint to an operator button.

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

- **depends on**: [TASK-BE-293](../done/) (admin `refresh-tracking` endpoint), [TASK-BE-360](../done/TASK-BE-360-shipping-auto-collect-tracking-scheduler.md) (auto-collect scheduler — the unattended counterpart this button complements), [TASK-BE-364](../done/TASK-BE-364-shipping-delivery-tracker-graphql-adapter.md) (Delivery Tracker GraphQL pull adapter the endpoint drives).
- **context**: this closes the one operator-UI gap noted after the ADR-007 aggregator series — the carrier-status pull was reachable only via the scheduler (unattended) or the raw endpoint; the operator console had no button. Correctness was already covered by the scheduler (so this is a UX/latency affordance, not a correctness fix — mirrors the BE-365 thin-ping "polling already converges" rationale).

---

# Goal

On the admin-dashboard `/shippings` shipping management list, add a per-row **"택배사 동기화"** action that calls the existing admin endpoint `POST /api/shippings/{shippingId}/refresh-tracking`. The endpoint is best-effort (TASK-BE-293): it pulls the aggregator's unified last-event status (Delivery Tracker GraphQL `track`, TASK-BE-364), maps it via `CarrierStatusMapper`, and forward-advances the shipment; a carrier hiccup is a no-op (the shipment is unchanged). On success the list re-queries so any advanced status (e.g. `IN_TRANSIT → DELIVERED`) is reflected immediately.

The button is offered ONLY when there is a waybill to pull and the shipment is not terminal — i.e. `status ∈ {SHIPPED, IN_TRANSIT}` **and** a non-blank `trackingNumber`. `PREPARING` (no waybill yet) and `DELIVERED` (terminal, forward-only) show no sync button.

# Scope

## In Scope

- `packages/api-client/src/services/admin-shipping-api.ts` — add `refreshTracking(shippingId)` → `POST /api/shippings/{shippingId}/refresh-tracking` (no body), returning `UpdateShippingStatusResponse` (same envelope the endpoint returns).
- `apps/admin-dashboard/src/features/shipping-management/`:
  - `api/shipping-api.ts` — `refreshTracking(shippingId)` wrapper (mirrors `updateShippingStatus`).
  - `hooks/use-refresh-tracking.ts` — `useInvalidatingMutation` (invalidate `shippingKeys.all`, Korean error message), mirroring `use-update-shipping-status.ts`.
  - `components/RefreshTrackingButton.tsx` — per-row button, gated on `{SHIPPED, IN_TRANSIT}` + non-blank `trackingNumber`; mirrors `StatusActionButton` styling/`stopPropagation`.
  - `components/ShippingList.tsx` — wire the new mutation + button into the actions column (combined `isPending` across both mutations).
- Tests (vitest): `use-refresh-tracking` (success + failure-alert) mirroring the existing hook test; `RefreshTrackingButton` gating (shown/hidden per status + trackingNumber).
- Task md + `INDEX.md` lifecycle.

## Out of Scope

- **Any backend / contract change.** `refresh-tracking` (BE-293) + the Delivery Tracker adapter (BE-364) are done and published; `shipping-api.md` already documents the endpoint. This task adds NO new endpoint and changes NO contract.
- **web-store (customer) UI** — the customer `ShippingTracker` already reflects the advanced status; no change.
- **The unattended scheduler** (BE-360) — unchanged; this button is the manual complement.
- **A detail-page sync** beyond the list-row action (could be a later affordance).

# Acceptance Criteria

- [ ] **AC-1** `createAdminShippingApi(...).refreshTracking(shippingId)` issues `POST /api/shippings/{shippingId}/refresh-tracking` with no body and resolves to `UpdateShippingStatusResponse`.
- [ ] **AC-2** The list row shows a **"택배사 동기화"** button **iff** `status ∈ {SHIPPED, IN_TRANSIT}` AND `trackingNumber` is non-blank; hidden for `PREPARING`, `DELIVERED`, or a missing tracking number.
- [ ] **AC-3** Clicking it calls `refreshTracking(shippingId)`; on success the shipping list query is invalidated/re-queried (an advanced status renders without a manual refresh). The button is `disabled` while either shipping mutation is pending.
- [ ] **AC-4** On endpoint failure a Korean error alert is shown (mirrors `use-update-shipping-status` error UX); the row/list is not corrupted (best-effort, no optimistic mutation).
- [ ] **AC-5** No backend/contract change; the diff is confined to `packages/api-client` + `apps/admin-dashboard` (+ task md/INDEX). `pnpm --filter admin-dashboard test` (vitest) + `tsc --noEmit` + `lint` + `build` all green.
- [ ] **AC-6** The existing shipping-management tests (`ShippingList`, `use-update-shipping-status`, `ShipFormDialog`) stay green — the new action is additive.

# Related Specs

- [`specs/services/shipping-service/external-integrations.md`](../../specs/services/shipping-service/external-integrations.md) § 1 — Delivery Tracker outbound pull (the wire this button ultimately triggers, via the admin endpoint).
- [`apps/admin-dashboard`](../../apps/admin-dashboard) architecture — Layered-by-Feature, React Query mutation + `useInvalidatingMutation` convention.

# Related Contracts

- [`specs/contracts/http/shipping-api.md`](../../specs/contracts/http/shipping-api.md) — `POST /api/shippings/{id}/refresh-tracking` (admin, best-effort) is already published here. This task consumes it byte-compatibly; **no contract edit**.

# Edge Cases

- **No trackingNumber on a SHIPPED row** (carrier set late / data gap): button hidden (nothing to pull) — AC-2 gates on a non-blank `trackingNumber`.
- **DELIVERED row**: terminal/forward-only; no sync button (a pull could only no-op).
- **Best-effort no-op**: the endpoint may return the shipment unchanged (carrier hiccup / `track:null` / unmapped status). The UI treats a 2xx as success and re-queries; an unchanged status is a valid, non-error outcome (no false "synced!" claim beyond the re-query).
- **Both mutations pending**: the row's status-transition button and the sync button share a combined `isPending` so a double-submit across the two is prevented.

# Failure Scenarios

- **403 (non-admin operator)**: the endpoint requires `userRole == ADMIN` (BE-293 `RefreshTrackingService`); a non-admin gets 403 → the generic error alert (no crash). The admin-dashboard is operator-gated, so this is a guard, not a normal path.
- **Aggregator/transport failure**: the endpoint absorbs it best-effort and returns the unchanged shipment (2xx) → UI re-query shows no change. A true 5xx from the endpoint surfaces the Korean error alert.
- **Optimistic-update temptation**: explicitly avoided — there is NO optimistic status mutation client-side (the carrier result is authoritative server-side); the UI only re-queries after the call.

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (routine admin-dashboard FE — mirrors the existing `StatusActionButton` + `use-update-shipping-status` + `createAdminShippingApi` patterns; the one non-trivial judgement is the button-visibility gate `{SHIPPED, IN_TRANSIT} ∧ trackingNumber`). 핵심 검증=`pnpm --filter admin-dashboard test` + tsc/lint/build green; 머지 후 회귀 없음(additive).
