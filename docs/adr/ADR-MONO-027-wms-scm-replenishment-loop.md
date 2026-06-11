# ADR-MONO-027 — wms → scm Stock-Replenishment Loop (low-stock → reorder suggestion)

**Status:** ACCEPTED
**Date:** 2026-06-11 (PROPOSED 2026-06-11 · ACCEPTED 2026-06-11, same-session user-explicit intent "진행" on the §2 decisions — see § 6)
**Decision driver:** User request (2026-06-11) — *"재고 부족이 자동으로 보충 발주를 트리거하게 만들고 싶다"* (when warehouse stock runs low, automatically drive a replenishment reorder). Via AskUserQuestion the user chose: **reorder *suggestion* (DRAFT) only — not auto-submit** (operator reviews before the supplier PO is dispatched); **SKU→supplier mapping in a demand-planning-owned minimal table** (not a full `supplier-service` v2 bootstrap); and scope **through federation E2E proof**.
**Supersedes:** none.
**Related:** [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) (the *forward* order-fulfillment loop ecommerce→wms — this ADR is the **replenishment** counter-loop that refills what fulfillment consumes), [ADR-MONO-004](ADR-MONO-004-shared-messaging-scaffolding.md) (shared `libs/java-messaging` outbox/consumer scaffolding — the transport this rides on), [ADR-MONO-005](ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) (saga category taxonomy), [`platform/service-boundaries.md`](../../platform/service-boundaries.md) §「Asynchronous (Events) — cross-project allowed」, the live precedent **scm `inventory-visibility-service` ← wms inventory events** (`projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md`), [scm `PROJECT.md`](../../projects/scm-platform/PROJECT.md) § Service Map (`demand-planning-service` v2 entry), memory `project_portfolio_7axis_architecture`.

> **Why an ADR, not just a task.** This introduces a **new cross-project runtime coupling direction** (wms → scm) between two independently-published portfolio axes, and it *activates* the `demand-planning-service` that scm's PROJECT.md has held as v2-deferred. The coupling direction, the auto-vs-suggest decision, the mapping-data ownership, and the demand-planning responsibility boundary are genuine architecture decisions that must be recorded before implementation tasks proceed. Authored PROPOSED so the user can review §2 before code; the §D8 implementation plan does not start until ACCEPTED.

---

## 1. Context

### 1.1 What already exists (no new domain invention on the wms side)

**wms-platform** `inventory-service` already detects low stock and **publishes a fact event today**:

```
any mutation reduces availableQty below the warehouse-configured threshold
   → inventory.low-stock-detected  (topic wms.inventory.alert.v1)
      payload: inventoryId, locationId, locationCode, skuId, skuCode,
               availableQty, threshold, triggeringEventType, triggeringEventId
   → debounced 1h per inventory row (no alert storm)
```

Authoritative schema: `projects/wms-platform/specs/contracts/events/inventory-events.md` §7. Current consumers are **wms-internal only** (`notification-service` operator alert, `admin-service` AlertLog). **No cross-project consumer subscribes to it yet** — scm consumes the `received/adjusted/transferred` topics but **not** the `alert` topic.

**scm-platform** already runs `procurement-service`, which owns the purchase-order (PO) lifecycle `DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED → … → RECEIVED`. A PO is authored **by a human** (operator/buyer) today; there is no programmatic "create a DRAFT PO from a recommendation" entry point.

`demand-planning-service` is declared in scm `PROJECT.md` § Service Map **v2 (deferred)**: *"batch-job + rest-api — 수요 예측 batch, 안전재고/재주문점 계산, 발주 추천."* This ADR is the trigger that **activates** it.

### 1.2 The permitting rule + the live precedent

`platform/service-boundaries.md` forbids cross-DB access but **explicitly designs cross-project async event consumption via published contracts**. This is already in production in the *visibility* direction: scm `inventory-visibility-service` consumes `wms.inventory.{received,adjusted,transferred}.v1` (group `scm-inventory-visibility-v1`, versioned, idempotent). This ADR follows the exact same pattern, consuming one more wms topic (`alert`) into a **different scm service** for a **different purpose** (replenishment decisioning, not read-model visibility).

### 1.3 What this is NOT (the boundary the user already clarified)

This loop does **not** fulfill the customer's out-of-stock order. When a webstore order finds no stock, ecommerce auto-cancels + refunds (ADR-MONO-022 §D4 backorder path) — that customer order is closed. This ADR is a **separate replenishment loop**: it refills the warehouse for *future* demand. The two loops share no order identity and no synchronous path.

```
[ADR-022 forward]   webstore order → wms fulfillment → ship  (consumes stock)
[ADR-022 backorder] out-of-stock   → order cancel + refund   (closes THAT order)
[ADR-027 this]      low stock       → scm reorder SUGGESTION   (refills FUTURE stock)
```

### 1.4 The reconciliations that need an explicit decision

1. **wms speaks SKU + warehouse; scm procurement speaks supplier + PO.** A low-stock alert names `skuCode`/`warehouseId`; a PO needs `supplierId`, order quantity, and currency. An ACL/mapping layer is required. → **D3, D4**.
2. **Threshold ownership.** wms already has its *own* alert threshold (warehouse op concern). scm replenishment has its *own* reorder point / safety stock / reorder quantity (procurement concern). These are two distinct policies that must not be conflated. → **D4**.
3. **Auto-order risk.** Auto-submitting a PO commits real money/contract to a supplier. → **D2** (the user chose suggestion-only).
4. **Service activation.** demand-planning is a brand-new (4th) scm service with three service-type facets (event-consumer + batch-job + rest-api). Its responsibility boundary vs procurement must be drawn. → **D6, D7**.

---

## 2. Decision

### D1 — Transport: Kafka subscription to the existing wms alert topic (chosen)

scm **`demand-planning-service`** adds a **consumer** for the existing `wms.inventory.alert.v1` topic (`inventory.low-stock-detected`). **No wms producer change** — wms already publishes it. Mirrors the scm `inventory-visibility-service` ← wms precedent exactly.

- Consumer group: `scm-demand-planning-v1` (independent offsets from `scm-inventory-visibility-v1`).
- Idempotent on `eventId` (T8, UUID v7) via a `processed_events` table; at-least-once tolerated; retry 3× → `<topic>.DLT`.
- A cross-project subscription contract doc on the scm side (`replenishment-subscriptions.md`), per the scm precedent. The authoritative payload schema stays in the **producing** service (wms `inventory-events.md` §7); scm reproduces only the consumer-driven subset it reads.
- wms `inventory-events.md` §7 gains **one line** noting scm `demand-planning-service` as a sanctioned cross-project consumer (documentation parity; no schema change). This is the only wms-side edit.

> Rejected alternative: wms directly calls a scm REST endpoint. That inverts the dependency (wms would learn about scm), violates the async cross-project boundary, and couples wms's transaction to scm availability. The fact-event the user's warehouse already emits is the correct seam.

### D2 — Reorder action: SUGGESTION (DRAFT) only, operator-gated — **not** auto-submit (user decision)

On a low-stock signal that crosses scm's reorder policy, demand-planning creates a **reorder suggestion** (a `reorder_suggestion` row, status `SUGGESTED`). It does **not** auto-dispatch a PO to the supplier.

| Option | Verdict |
|---|---|
| **D2-a (chosen)** Suggestion → operator reviews → operator triggers the existing procurement DRAFT→SUBMITTED path. | Matches PROJECT.md "발주 추천"; prevents erroneous/duplicate auto-orders committing real supplier cost; reuses procurement's existing review flow. |
| D2-b Auto-submit PO to supplier on threshold. | Rejected for v1 — a wrong demand signal becomes a real financial/contractual commitment; would also require auto-driving the supplier-ack loop. Revisit only with mature forecasting + supplier SLAs. |

The materialization of a suggestion into a **DRAFT PO** (still pre-dispatch, still operator-reviewable) is the integration point with procurement — see **D5**.

### D3 — SKU→supplier / lead-time mapping: demand-planning-owned minimal table (user decision)

demand-planning bootstraps with its **own** minimal mapping table `sku_supplier_map`:

| Column | Purpose |
|---|---|
| `sku_code` | wms SKU code carried in the alert payload (the join key) |
| `supplier_id` | the scm supplier to reorder from (FK-free cross-service reference, as procurement already does) |
| `default_order_qty` | v1 fixed reorder quantity (EA) |
| `lead_time_days` | informational (expected arrival horizon) |
| `currency` | ISO 4217 for the PO |

- Unmapped `sku_code` → suggestion is **rejected to DLT + ops alert**, never silently dropped (scm S2 fail-closed posture; mirrors ADR-022 D3 unmapped-SKU rule).
- A full `supplier-service` (master/contract/catalog sync) stays **v2-deferred** — explicitly out of scope here. demand-planning's table is the minimal seam to prove the loop; it migrates to `supplier-service` when that bootstraps (forward-compatible: same `supplier_id` reference).

### D4 — Reorder policy lives in scm, distinct from the wms alert threshold

Two thresholds, two owners, deliberately separate:

- **wms alert threshold** — a warehouse operations concern ("tell someone stock is low"). Already exists; unchanged.
- **scm reorder policy** (`reorder_policy`: `reorder_point`, `safety_stock`, `reorder_qty` per sku) — a procurement concern ("should we reorder, and how much"). demand-planning owns it.

demand-planning treats the wms alert as a **trigger to evaluate**, not as the reorder decision itself. v1 evaluation = simple rule: `alert.availableQty <= reorder_policy.reorder_point` → suggest `reorder_qty` (default from `sku_supplier_map.default_order_qty` if no per-sku policy row). Demand forecasting (moving-average / seasonality) is a **v2** refinement inside the same service — the policy table is the extension point.

### D5 — demand-planning → procurement materialization: in-project, suggestion → DRAFT PO

demand-planning and procurement are **same-project** services, so this leg is intra-scm (not a cross-project event). Chosen shape:

- procurement gains an **additive** programmatic entry point to create a PO in `DRAFT` from a reorder suggestion (`supplierId`, lines `[{skuCode, qty, unitPriceRef}]`, `currency`, `origin=DEMAND_PLANNING`, `sourceSuggestionId`). It reuses the **existing** `DRAFT` state and lifecycle — no new PO state, no auto-SUBMIT.
- Transport between the two scm services: v1 = demand-planning calls procurement's internal REST entry (same gateway-internal trust other scm services already use), OR an intra-scm event `scm.replenishment.suggestion.raised.v1`. **Recommendation: synchronous internal REST** for v1 (simpler, both services co-deployed; the DRAFT PO is immediately visible to the operator). The event option is noted for v2 if the two services need temporal decoupling.
- The operator then reviews the DRAFT PO and uses procurement's existing `DRAFT → SUBMITTED` path to actually dispatch to the supplier. **demand-planning never submits.**

### D6 — Idempotency & duplicate-suppression (two layers)

1. **Event dedup** — `eventId` (T8) in `processed_events`; redelivery is a no-op.
2. **Open-suggestion guard** — even across distinct alert eventIds, demand-planning must not pile up suggestions for the same `(sku_code, warehouse)` while one is still open (`SUGGESTED` or already materialized into a non-terminal DRAFT PO). wms's 1h alert debounce reduces but does not eliminate this (a drop-then-redrop across the debounce window re-fires). The open-suggestion guard is the authoritative dedupe for *business* duplicates.

### D7 — demand-planning responsibility boundary (3 service-type facets)

| Facet | v1 responsibility |
|---|---|
| **event-consumer** | consume `wms.inventory.alert.v1` → evaluate reorder policy → raise `reorder_suggestion` |
| **rest-api** | list/inspect suggestions; (operator) approve a suggestion → triggers D5 DRAFT-PO creation; CRUD the `reorder_policy` / `sku_supplier_map` seed |
| **batch-job** (batch-heavy trait — first non-IVS use) | nightly re-evaluation sweep over the inventory-visibility read-model (catch SKUs that sit below reorder point without a fresh alert); ShedLock-guarded, mirrors inventory-visibility's StalenessDetectionScheduler |

demand-planning **does not**: own physical inventory (wms), own the PO lifecycle/supplier dispatch (procurement), or fulfill customer orders (ecommerce/wms). It is a **decisioning** service: trigger in (alert), suggestion out, DRAFT PO handed to procurement.

#### D7.1 — batch sweep reads IVS via an internal network-trusted endpoint (TASK-SCM-BE-026)

The nightly `ReorderSweepScheduler` runs **unattended** (`@Scheduled`, no inbound request) — there is no operator JWT in context to present to the inventory-visibility-service (IVS) read API, and scm has no workload-identity infrastructure (the same v1 gap noted in D5, where the live approve leg propagates the *operator's* bearer). Decision (user-directed, 2026-06-12): IVS exposes a dedicated **internal endpoint** `GET /internal/inventory-visibility/snapshot` that is `permitAll` (no JWT) and is **NOT routed by scm-gateway** (the gateway only routes `/api/v1/**`; `/internal/**` is reachable only on the intra-scm container network). The demand-planning batch calls it directly with no token. This extends the **v1 intra-scm trust** posture from D5 (operator-bearer propagation) to the unattended batch case.

- The endpoint returns the current snapshot **across all tenants** (the batch is tenant-agnostic — demand-planning raises suggestions under the static `scm` domain slug, exactly as the live alert path does; the alert envelope itself carries no tenant). The sweep then filters each row against demand-planning's own `reorder_policy`.
- **Trust boundary**: network isolation only. The endpoint must never be exposed through the gateway or a public host route. Production deployments must keep IVS un-routed externally (only the gateway is a registered hostname).
- **Rejected for v1**: a service/workload JWT issuer (proper, but a new auth subsystem — its own ADR) and a static shared secret (secret-management + rotation burden, redundant with network isolation). Both remain open upgrades if IVS is ever externally reachable.
- The live alert path is unchanged and remains fully decoupled from IVS (S5): if the internal endpoint is unavailable, the sweep skips the run (metric `reorder_sweep_ivs_unavailable_total`) and the alert path is unaffected.

### D8 — Standalone-publish degradation (no hard dependency)

Without wms present (scm published standalone), the `wms.inventory.alert.v1` topic never arrives; demand-planning simply holds an empty suggestion list. The nightly batch over inventory-visibility still runs (and is also empty without wms data). No hard dependency — same posture as the scm `inventory-visibility-service` precedent and ADR-022 §D8.

---

## 3. Implementation plan (tasks — start only after ACCEPTED)

Numbered against the live queue (origin/main: ADR-MONO-026, TASK-MONO-218, SCM-BE-021, SCM-INT-001 are the latest used).

**Phase 0 — decision + contracts (spec-only)**

| Task | Queue | Content |
|---|---|---|
| TASK-MONO-219 | root | Author this ADR PROPOSED (this file). |
| TASK-MONO-220 | root | ADR-MONO-027 PROPOSED → ACCEPTED transition (after user review). |
| TASK-SCM-BE-022 | scm | `replenishment-subscriptions.md` — scm consumes `wms.inventory.alert.v1` (consumer-driven contract); +1 line in wms `inventory-events.md` §7 cross-project consumer note (same atomic PR). |
| TASK-SCM-BE-023 | scm | `demand-planning-service` spec suite — `architecture.md` (Hexagonal, service-type=event-consumer+batch-job+rest-api), `data-model.md` (`reorder_policy` / `sku_supplier_map` / `reorder_suggestion` / `processed_events`), reorder-policy spec, HTTP API contract, + procurement `architecture.md`/api additive DRAFT-PO-from-suggestion entry (D5). |

**Phase 1 — service bootstrap + materialization (impl)**

| Task | Queue | Content |
|---|---|---|
| TASK-SCM-BE-024 | scm | Bootstrap `demand-planning-service` — low-stock consumer (T8 dedup) → reorder-policy eval → `reorder_suggestion`. gateway route. Flyway V1. OAuth2 RS (`tenant_id=scm` gate + entitlement-trust dual-accept, per SCM-BE-019 blueprint). |
| TASK-SCM-BE-025 | scm | demand-planning → procurement DRAFT-PO materialization (D5) + `sku_supplier_map` apply + open-suggestion guard (D6). |

**Phase 2 — proof**

| Task | Queue | Content |
|---|---|---|
| TASK-SCM-INT-002 | scm | Testcontainers cross-service E2E: simulated `wms.inventory.alert.v1` → demand-planning suggestion → procurement DRAFT PO. Then **federation-stack live proof** (wms emits a real alert → scm DRAFT PO appears) — wired into `tests/federation-hardening-e2e` (root) as the live leg. |

PR shape per scm `tasks/INDEX.md`: each task = spec PR ↔ impl PR ↔ chore PR separation.

---

## 4. Alternatives considered

- **Auto-submit PO on threshold** (D2-b) — rejected: real financial commitment from a possibly-wrong signal.
- **Bootstrap `supplier-service` first** — deferred: full supplier master/contract/catalog is v2; demand-planning's minimal `sku_supplier_map` is the cheapest seam that proves the loop and migrates cleanly.
- **wms → scm via synchronous REST** — rejected: inverts the cross-project dependency and couples wms's TX to scm uptime; the existing fact-event is the right boundary.
- **Reuse `inventory-visibility-service` as the consumer** — rejected: visibility is a read-model with eventual-consistency semantics (S5); replenishment decisioning + policy + PO materialization is a distinct responsibility that PROJECT.md already names as its own service. Overloading IVS would blur S5.
- **Overload the existing `inventory.adjusted` topic instead of `alert`** — rejected: `alert` is the purpose-built threshold-crossing signal (debounced, threshold-aware); `adjusted` is a raw mutation stream scm IVS already consumes for a different purpose.

---

## 5. Consequences

**Positive**
- Activates the long-declared `demand-planning-service` with a concrete, user-driven use case; exercises the `batch-heavy` trait a second time (first non-IVS).
- Closes the portfolio's supply-chain story end-to-end: webstore sells → wms ships → stock drops → scm proposes replenishment. A second, complementary cross-project loop to ADR-022.
- Zero wms producer change (one doc line); the whole cost is scm-side + one new consumer.
- Operator-gated suggestion keeps the human in the loop where money is committed.

**Negative / risk**
- A 4th scm service to operate (gateway route, DB, Flyway, federation wiring).
- `sku_supplier_map` is a deliberate minimal stand-in for `supplier-service`; must be tracked as tech-debt to migrate at v2 (recorded in D3).
- Duplicate-suggestion correctness depends on the open-suggestion guard (D6), not just event dedup — a known subtlety to test explicitly.
- federation-e2e is nightly (not PR-gated) — the live leg can regress silently; INT-002 must add a deterministic Testcontainers leg as the PR-gated guard (per memory `project_adr023_plane_separation_fed_e2e` fed-e2e trap #1).

**Neutral**
- scm `PROJECT.md` traits unchanged (`transactional` + `integration-heavy` + `batch-heavy` already cover it; this is the first code exercising batch-heavy outside IVS). No new domain/trait declaration.
- No change to the ecommerce side or to ADR-022's loops.

---

## 6. Status history

- **2026-06-11 PROPOSED** — authored for user review of §2 (D1 Kafka subscribe, D2 suggestion-only, D3 demand-planning-owned mapping, D4 scm-owned reorder policy, D5 intra-scm DRAFT-PO materialization). User pre-selected D2-a / D3-minimal-table / scope-through-federation-E2E via AskUserQuestion before authoring.
- **2026-06-11 ACCEPTED** (TASK-MONO-220) — user reviewed the PROPOSED document (PR #1292) and gave explicit affirmative intent ("진행") accepting the §2 decisions as recommended. **NOT self-ACCEPT**: the user directed the transition. Cross-project runtime coupling between two independently-published portfolio axes (wms ↔ scm) is a genuine architecture decision, recorded here before the §3 implementation tasks proceed. Phase 0 spec tasks (SCM-BE-022/023) are now unblocked; Phase 1 impl (BE-024/025/INT-002) follows as their specs merge.
