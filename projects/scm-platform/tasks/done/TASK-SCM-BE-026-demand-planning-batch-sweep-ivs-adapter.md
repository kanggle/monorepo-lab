# Task ID

TASK-SCM-BE-026

# Title

demand-planning batch sweep — wire the real IVS read adapter (replace the Phase-1 stub) via an internal network-trusted IVS endpoint, completing the ADR-MONO-027 §D7 batch-job facet. impl/test.

# Status

done

# Owner

backend

# Task Tags

- backend
- batch
- cross-service

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

- **선행 (prerequisite)**: [TASK-SCM-BE-024](../done/TASK-SCM-BE-024-demand-planning-service-bootstrap.md) (sweep scaffolding: `ReorderSweepScheduler` + `SweepReorderUseCase` + `InventoryVisibilityPort` + `InventoryVisibilityStubAdapter`) + [TASK-SCM-BE-025](../done/TASK-SCM-BE-025-demand-planning-procurement-materialization.md) (intra-scm RestClient pattern `ProcurementDraftPoClient`). Both merged.
- **decision**: [ADR-MONO-027 §D7.1](../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) — sweep reads IVS via an **internal network-trusted endpoint** (user-directed 2026-06-12). No workload-identity infra; v1 intra-scm trust extended to the unattended batch.

# Goal

Complete the demand-planning **batch-job** facet (ADR-MONO-027 §D7): the nightly `ReorderSweepScheduler` must read the **live** inventory-visibility (IVS) read-model — not the Phase-1 `InventoryVisibilityStubAdapter` (returns empty) — so SKUs that sit below their reorder point **without a fresh wms alert** are caught and raise reorder suggestions through the same open-suggestion guard as the live path. All `SweepReorderUseCase` logic is already implemented; only the IVS read adapter is stubbed.

# Scope

## In Scope

### 1. IVS internal endpoint (inventory-visibility-service)

- `GET /internal/inventory-visibility/snapshot` — `permitAll` (no JWT), returns the current snapshot **across all tenants** as a flat list `[{ sku, nodeId, availableQty }]`. NOT routed by scm-gateway; reachable only on the intra-scm network (ADR-MONO-027 §D7.1).
- A cross-tenant snapshot query (the existing repository methods are tenant-scoped; the batch is tenant-agnostic).
- `SecurityConfig`: permit `/internal/inventory-visibility/**` (before `anyRequest().denyAll()`).

### 2. demand-planning real IVS adapter (demand-planning-service)

- `InventoryVisibilityRestAdapter implements InventoryVisibilityPort` — `ResilienceClientFactory.buildRestClient` (mirrors `ProcurementDraftPoClient`), **no bearer** (internal endpoint), calls `GET /internal/inventory-visibility/snapshot`, maps `{ sku, nodeId, availableQty }` → `SkuWarehouseQty(skuCode, warehouseId, availableQty)`.
- Remove `InventoryVisibilityStubAdapter`.
- Config `scmplatform.demand-planning.inventory-visibility.base-url` (+ connect/read timeouts), env `INVENTORY_VISIBILITY_BASE_URL`.
- Compose: demand-planning gets `INVENTORY_VISIBILITY_BASE_URL` pointing at the IVS service (federation replenishment overlay).

### 3. Tests

- IVS: the internal endpoint returns a cross-tenant snapshot **without** a token (permitAll), and is denied/absent through the authenticated `/api/**` surface.
- demand-planning: adapter mapping unit (MockWebServer IVS) + a sweep IT — seed policy+mapping, MockWebServer IVS internal returns a below-reorder SKU → `SweepReorderUseCase.sweep()` raises a `BATCH`-source suggestion; open-guard re-run raises no duplicate; IVS-unavailable → sweep skips (0 raised), no throw.

## Out of Scope

- Demand forecasting / safety-stock math beyond the existing simple rule — v2.
- A service/workload JWT issuer or static service secret (ADR-MONO-027 §D7.1 rejected for v1).
- Triggering the cron in e2e (the deterministic sweep proof is the demand-planning IT).
- supplier-service v2, multi-warehouse routing.

# Acceptance Criteria

- **AC-1** IVS `GET /internal/inventory-visibility/snapshot` returns the cross-tenant snapshot as `[{sku, nodeId, availableQty}]` with **no** Authorization header (permitAll), and is not reachable through scm-gateway.
- **AC-2** `InventoryVisibilityRestAdapter` replaces the stub: a real `SweepReorderUseCase.sweep()` reads IVS, evaluates each row against `reorder_policy`, and raises `BATCH`-source suggestions for below-reorder SKUs (open-guard + unmapped-skip honored).
- **AC-3** Idempotency: a re-run of the sweep raises no duplicate (open-suggestion guard).
- **AC-4** IVS unavailable → the sweep skips the run (`reorder_sweep_ivs_unavailable_total` incremented, 0 raised, no exception propagated); the live alert path is unaffected.
- **AC-5** `:check` + `integrationTest` green for both services.

# Related Specs

- [ADR-MONO-027 §D7 / §D7.1](../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md)
- [demand-planning-service architecture.md](../../specs/services/demand-planning-service/architecture.md) (batch-job facet)
- [inventory-visibility-service architecture.md](../../specs/services/inventory-visibility-service/architecture.md)

# Related Contracts

- [`inventory-visibility-api.md` § Internal endpoints](../../specs/contracts/http/inventory-visibility-api.md) (the new `/internal/inventory-visibility/snapshot`)

# Edge Cases

- **cross-tenant read**: IVS snapshots live under real customer tenants (globex-corp, …); the sweep is tenant-agnostic (`scm` slug, like the live alert path). The internal endpoint returns all tenants; demand-planning raises `scm` suggestions.
- **quantity type**: IVS `quantity` is `BigDecimal`; map to integer `availableQty` (whole units).
- **gateway-block invariant**: `/internal/**` must stay un-routed by scm-gateway and un-exposed on any public host route (trust = network isolation only).

# Failure Scenarios

- **IVS internal endpoint down / slow**: adapter error → sweep skips the run (metric) → live alert path unaffected (S5 decoupling). No partial suggestions.
- **unmapped / no-policy SKU in the snapshot**: skipped (logged + `reorder_sweep_skipped_unmapped_total`) / degraded fallback per the existing `SweepReorderUseCase` rules — no DLT (no event to route in batch).

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (cross-service batch + security surface [permitAll internal endpoint] + cross-tenant read semantics).
- All `SweepReorderUseCase` decision logic is already implemented (BE-024); this task is the IVS read seam + its security/contract. Both services are scm-platform → **one atomic PR**.

# Closure

- Merged PR #1330 squash `33450a875` (3-dim verified: state=MERGED · origin/main tip = `33450a875` · pre-merge `gh pr checks` 0 failing required, incl. `Integration (scm-platform, Testcontainers)` pass). Auth decision A (IVS internal network-trusted endpoint) recorded in ADR-MONO-027 §D7.1.
- **CI flake root-caused + fixed (2 iterations):** the scm Integration suite failed `sweep_idempotent_openGuard` (expected 1, was 0). True cause = **ShedLock**: `ReorderSweepScheduler.runSweep()` is `@SchedulerLock(lockAtLeastFor=PT5M)`, so the FIRST `runSweep` in the test JVM holds the lock 5 min and every later call (other tests + the idempotent test's 2nd sweep) is a silent no-op (the "no IVS-read log for 3 of 4 tests" tell). Fix: the IT calls `SweepReorderUseCase.sweep()` directly (the inner method has no `@SchedulerLock`) — deterministic by construction, and the open-guard re-run is genuinely exercised. Also hardened the IVS stub to a `Dispatcher` (request-count-independent) + unique SKU + filtered asserts (defends the shared Kafka consumer's earliest-offset re-processing of sibling-class alerts).
- AC-1..AC-5 satisfied. ADR-MONO-027 demand-planning 3-facet (event-consumer + rest-api + **batch-job**) now fully implemented; the Phase-1 IVS stub is retired.
