# Task ID

TASK-SCM-BE-023

# Title

Author the `demand-planning-service` spec suite (architecture / data-model / reorder-policy / HTTP API) + the additive procurement "DRAFT-PO-from-suggestion" entry-point spec. Activates scm's v2-deferred 4th service per ADR-MONO-027. spec-only.

# Status

done

> **DONE (2026-06-11, spec-only self-review)**: ADR-MONO-027 service spec suite authored. NEW `demand-planning-service/` specs — `architecture.md` (3-facet event-consumer+batch-job+rest-api, Hexagonal, canonical Identity + Service Type Composition, D7 decisioning-only boundary, S-rule mapping, T8+open-guard idempotency, ADR-005 Cat C/D, justified no-outbox), `data-model.md` (4 tables: `reorder_policy`/`sku_supplier_map` FK-free supplier ref/`reorder_suggestion` + partial-unique open-guard/`processed_events`; suggestion status machine), `reorder-policy.md` (D4 rule distinct from wms threshold; v2 forecasting seam), `overview.md`. NEW contract `demand-planning-api.md` (suggestions list/approve/dismiss + policy/mapping seed, new error codes, route publicity). **Additive** procurement `procurement-api.md` § `POST /po/from-suggestion` (D5 — DRAFT factory, idempotent on `sourceSuggestionId`, no new PO state, no auto-SUBMIT, scope-guarded). PROJECT.md Service Map demand-planning **v2→v1-active** (+ supplier-service v2 노트 sku_supplier_map 이관). gateway-public-routes.md `/api/v1/demand-planning/**` route reserved (BE-024 activates). spec-only, markdown fast-lane. traits frontmatter byte-unchanged. 선행=BE-022 ✓. 후속=BE-024/025. 분석=Opus 4.8 / 구현=Opus(직접).

# Owner

backend

# Task Tags

- spec
- api
- event

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **선행 (prerequisite)**: [TASK-SCM-BE-022](TASK-SCM-BE-022-replenishment-subscriptions-contract.md) (subscription contract the service consumes) + ADR-MONO-027 ACCEPTED.
- **sibling pattern**: `inventory-visibility-service` spec suite (TASK-SCM-BE-003/014) — same Hexagonal + event-consumer + batch shape to mirror.
- **후속**: TASK-SCM-BE-024 (bootstrap impl), TASK-SCM-BE-025 (procurement materialization impl).

# Goal

Specify the new `demand-planning-service` end to end **before** code: its Hexagonal architecture across three service-type facets (event-consumer + batch-job + rest-api), its data model (reorder policy + sku→supplier mapping + reorder suggestion + dedup), its reorder-policy evaluation rule, and its REST surface. Also specify the **additive** procurement entry point that turns an approved suggestion into a DRAFT PO (ADR-027 D5) — without changing procurement's existing PO lifecycle.

# Scope

## In Scope

### 1. Service architecture spec

`specs/services/demand-planning-service/architecture.md` (NEW):

- **Service Type**: `event-consumer` + `batch-job` + `rest-api` (PROJECT.md Service Map). Canonical Identity table + `### Service Type Composition` H3 per ADR-MONO-012.
- **Architecture Style**: Hexagonal (consumer, scheduler, REST share one domain core).
- **Responsibility boundary (ADR-027 D7)**: decisioning only — trigger in (low-stock alert), reorder *suggestion* out, DRAFT PO handed to procurement. Does NOT own inventory (wms), PO lifecycle/dispatch (procurement), or order fulfillment (ecommerce/wms).
- **Domain core**: `ReorderPolicy`, `SkuSupplierMapping`, `ReorderSuggestion` (aggregate; status `SUGGESTED → APPROVED → MATERIALIZED → DISMISSED`), `ProcessedEvent` (T8).
- **Flows**: (a) consumer — alert → evaluate policy → raise/skip suggestion; (b) batch — nightly sweep over inventory-visibility read-model for below-reorder-point SKUs without a fresh alert (ShedLock-guarded, mirrors StalenessDetectionScheduler); (c) REST — list/inspect suggestions, operator approve (→ D5 materialize), policy/mapping CRUD seed.
- Mandatory section mapping (S-rules), Idempotency (T8 + open-suggestion guard D6), Multi-tenancy (`tenant_id=scm` fail-closed + entitlement-trust dual-accept per SCM-BE-019 blueprint), Saga/Long-running (ADR-MONO-005 Cat C consumer + Cat D batch sweep), Observability, Failure Modes, Testing.
- **Outbox decision**: documented either way — if demand-planning emits an intra-scm event for D5 it uses the transactional outbox (T3); if D5 is synchronous REST (recommended v1) no outbox needed and the absence is justified (mirror IVS's deliberate no-outbox rationale).

### 2. Data model spec

`specs/services/demand-planning-service/data-model.md` (NEW):

- `reorder_policy` (sku_code, reorder_point, safety_stock, reorder_qty, tenant_id, version, updated_at; UNIQUE (tenant_id, sku_code))
- `sku_supplier_map` (sku_code, supplier_id, default_order_qty, lead_time_days, currency, tenant_id; UNIQUE (tenant_id, sku_code)) — ADR-027 D3 minimal mapping (supplier_id = FK-free cross-service ref, as procurement does)
- `reorder_suggestion` (id UUID v7 PK, sku_code, warehouse_id, supplier_id, suggested_qty, trigger_event_id, trigger_available_qty, status, source `ALERT|BATCH`, materialized_po_id NULL, tenant_id, created_at, updated_at; INDEX (tenant_id, status), partial-unique open-suggestion guard on (tenant_id, sku_code, warehouse_id) WHERE status IN ('SUGGESTED','APPROVED'))
- `processed_events` (event_id UUID PK, tenant_id, processed_at, source_topic) — T8
- All tables `tenant_id` + index prefix.

### 3. Reorder-policy spec

`specs/services/demand-planning-service/reorder-policy.md` (NEW): the v1 evaluation rule (ADR-027 D4) — `alert.availableQty <= reorder_policy.reorder_point` → suggest `reorder_qty` (fallback `sku_supplier_map.default_order_qty`). Explicitly distinct from the wms alert threshold. v2 forecasting noted as the extension point (moving-average/seasonality), not implemented.

### 4. HTTP API contract

`specs/contracts/http/demand-planning-api.md` (NEW):

- `GET /api/demand-planning/suggestions` (filter by status) — paginated
- `GET /api/demand-planning/suggestions/{id}`
- `POST /api/demand-planning/suggestions/{id}/approve` — operator approve → triggers D5 DRAFT-PO materialization (idempotent; returns the created/linked `poId`)
- `POST /api/demand-planning/suggestions/{id}/dismiss`
- `GET|PUT /api/demand-planning/policies/{skuCode}` + `GET|PUT /api/demand-planning/sku-supplier-map/{skuCode}` (seed/admin)
- Envelope `{ data, meta }`; error codes from `rules/domains/scm.md` (+ `SKU_SUPPLIER_UNMAPPED`, `SUGGESTION_ALREADY_MATERIALIZED`). Which routes are gateway-public vs internal documented (approve/PUT = operator-authenticated; consumer-internal mapping seed may be internal-only).

### 5. procurement additive entry (ADR-027 D5)

`specs/services/procurement-service/architecture.md` + `specs/contracts/http/procurement-api.md` — **additive** "create DRAFT PO from reorder suggestion" entry (`supplierId`, lines `[{skuCode, qty, unitPriceRef}]`, `currency`, `origin=DEMAND_PLANNING`, `sourceSuggestionId`). Reuses existing `DRAFT` state + lifecycle; **no new PO state, no auto-SUBMIT**. Transport = intra-scm internal REST (v1 recommendation) documented; the operator then uses the existing `DRAFT → SUBMITTED` path. Idempotent on `sourceSuggestionId` (re-approve = no duplicate PO).

### 6. gateway + PROJECT.md + INDEX

- `specs/services/gateway-service/*` — note the new `/api/v1/demand-planning/**` route (placeholder; activated by BE-024 impl).
- `PROJECT.md` § Service Map — move `demand-planning-service` from v2 to v1-active (ADR-027 reference). **traits unchanged** (batch-heavy already declared).

## Out of Scope

- Any `apps/` code, Flyway SQL, consumer wiring — TASK-SCM-BE-024.
- procurement materialization *implementation* — TASK-SCM-BE-025.
- Demand *forecasting* (moving-average/seasonality) — v2 (policy table is the seam).
- `supplier-service` full master/contract/catalog — v2 deferred (ADR-027 D3).
- E2E — TASK-SCM-INT-002.

# Acceptance Criteria

- **AC-1** `demand-planning-service/architecture.md` exists: Service Type `event-consumer`+`batch-job`+`rest-api`, Hexagonal, canonical Identity table + Service Type Composition H3, D7 boundary stated.
- **AC-2** `data-model.md` defines the 4 tables incl. the partial-unique open-suggestion guard (D6) and the FK-free `supplier_id` ref (D3).
- **AC-3** `reorder-policy.md` states the v1 rule and its distinctness from the wms threshold (D4); v2 forecasting marked as deferred extension.
- **AC-4** `demand-planning-api.md` defines the suggestion list/approve/dismiss + policy/mapping seed routes with `{data,meta}` envelope and the new error codes; route publicity (public vs internal) is explicit.
- **AC-5** procurement gains an **additive** DRAFT-PO-from-suggestion entry — existing PO lifecycle/states byte-unchanged; idempotent on `sourceSuggestionId`; no auto-SUBMIT.
- **AC-6** `PROJECT.md` moves demand-planning v2→v1-active; traits frontmatter byte-unchanged.
- **AC-7** spec-only diff (no `apps/`, no migration).

# Related Specs

- [ADR-MONO-027](../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) (D2 suggestion-only, D3 mapping, D4 policy, D5 materialization, D7 boundary)
- [TASK-SCM-BE-022](TASK-SCM-BE-022-replenishment-subscriptions-contract.md) (the subscription it consumes)
- `specs/services/inventory-visibility-service/architecture.md` (Hexagonal + event-consumer + batch sibling to mirror; the read-model the batch sweep reads)
- `specs/services/procurement-service/architecture.md` (PO lifecycle the materialization plugs into at DRAFT)
- [rules/domains/scm.md](../../../../rules/domains/scm.md) S1 (idempotent multi-leg), S2, S5
- `rules/traits/batch-heavy.md` (nightly sweep chunking/restartability/ShedLock), [rules/traits/transactional.md](../../../../rules/traits/transactional.md) (T3/T8)
- [platform/architecture-decision-rule.md](../../../../platform/architecture-decision-rule.md), [platform/service-types/event-consumer.md](../../../../platform/service-types/event-consumer.md) / `batch-job.md` / `rest-api.md`

# Related Contracts

- NEW: `specs/contracts/http/demand-planning-api.md`
- Edited (additive): `specs/contracts/http/procurement-api.md` (DRAFT-PO-from-suggestion)
- Referenced: `specs/contracts/events/replenishment-subscriptions.md` (BE-022), wms `inventory-events.md` §7

# Edge Cases

- **Suggestion vs PO ownership**: the `reorder_suggestion` is demand-planning's aggregate; the DRAFT PO is procurement's. `materialized_po_id` is a soft cross-service link, not an FK. Document the lifecycle hand-off (APPROVED→MATERIALIZED on successful DRAFT-PO create).
- **Open-suggestion guard semantics**: the partial-unique index covers only non-terminal states — a DISMISSED/MATERIALIZED suggestion must not block a future re-suggestion when stock drops again. Specify exactly which states the guard spans.
- **Batch vs consumer double-raise**: nightly batch and a live alert could both target the same SKU in the same window — the open-suggestion guard (D6) is the single arbiter; both paths must funnel through it.
- **unitPriceRef**: a DRAFT PO needs a price. v1 = reference/placeholder (operator fills actual price at review) OR last-known supplier price — decide and document; do not invent pricing logic in demand-planning.

# Failure Scenarios

- If the procurement entry adds a new PO state or auto-SUBMITs → violates ADR-027 D2/D5 (operator-gated, lifecycle-unchanged). Materialization stops at DRAFT.
- If demand-planning is specced to read inventory-visibility *synchronously for the suggestion decision* in a way that couples to IVS availability → violates S5 eventual-consistency; the batch sweep reads IVS asynchronously, the live path uses the alert.
- If `supplier_id` is modeled as an FK to a (nonexistent) supplier table → breaks the FK-free cross-service convention; it is a reference value resolved by mapping seed.

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (multi-facet service spec + cross-service materialization design).
- Largest task of the set (spec for a 3-facet service + procurement additive). Sibling size ≈ TASK-SCM-BE-003 spec.
- PR Separation: this spec PR precedes BE-024/025 impl PRs.
