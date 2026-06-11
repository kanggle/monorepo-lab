# Task ID

TASK-SCM-BE-024

# Title

Bootstrap `demand-planning-service` — Spring Boot Hexagonal service: low-stock alert consumer → reorder-policy evaluation → reorder suggestion. scm's 4th domain service (ADR-MONO-027 Phase 1). impl.

# Status

backlog

# Owner

backend

# Task Tags

- code
- event
- deploy

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

- **선행 (prerequisite)**: [TASK-SCM-BE-022](../ready/TASK-SCM-BE-022-replenishment-subscriptions-contract.md) (subscription contract) + [TASK-SCM-BE-023](../ready/TASK-SCM-BE-023-demand-planning-service-spec.md) (service spec). **backlog → ready only after both specs merge.**
- **후속**: TASK-SCM-BE-025 (procurement materialization), TASK-SCM-INT-002 (E2E).
- **sibling blueprint**: `inventory-visibility-service` impl (TASK-SCM-BE-003) — Hexagonal + Kafka consumer + ShedLock batch + OAuth2 RS to reuse.

# Goal

Stand up `demand-planning-service` so a wms `inventory.low-stock-detected` event becomes a scm `reorder_suggestion` against the reorder policy — the consumer + batch + read half of ADR-027. The procurement DRAFT-PO leg is BE-025; this task ends at a persisted SUGGESTED row + the REST surface to inspect it.

# Scope

## In Scope

- `projects/scm-platform/apps/demand-planning-service/` Spring Boot module (build.gradle, Dockerfile, settings.gradle include): Web, Data JPA, Flyway, Spring Kafka, Spring Data Redis (optional), Security OAuth2 RS, libs:java-common/web/security/observability/messaging.
- **Consumer** `WmsLowStockAlertConsumer` (`@KafkaListener` `wms.inventory.alert.v1`, group `scm-demand-planning-v1`) → envelope parse → T8 dedup (`processed_events`) → `EvaluateReorderUseCase`. Retry 3× → DLT; null envelope / unmapped SKU → non-retryable DLT + ops alert (BE-022 contract).
- **Domain + application**: `ReorderPolicy`, `SkuSupplierMapping`, `ReorderSuggestion` (status machine `SUGGESTED→APPROVED→MATERIALIZED→DISMISSED`), `EvaluateReorderUseCase` (rule: `availableQty <= reorder_point` → raise), open-suggestion guard (D6).
- **Batch** `ReorderSweepScheduler` (`@Scheduled`, ShedLock single-instance) — nightly sweep over inventory-visibility read-model (via IVS REST or shared read) for below-reorder-point SKUs without a fresh alert → funnel through the same guard.
- **REST** (BE-023 contract): `GET /suggestions`, `GET /suggestions/{id}`, `POST /suggestions/{id}/dismiss`, policy/mapping seed `GET|PUT`. (`approve` → BE-025.) `{data,meta}` envelope + GlobalExceptionHandler.
- **Flyway** `db/migration/demand-planning/V1__init.sql`: `reorder_policy`, `sku_supplier_map`, `reorder_suggestion` (+ partial-unique open-suggestion index), `processed_events`. tenant_id prefix.
- **Security**: OAuth2 RS (RS256, GAP JWKS), `tenant_id=scm` fail-closed + entitlement-trust dual-accept (SCM-BE-019 blueprint, local isEntitled helper).
- **gateway**: activate `/api/v1/demand-planning/**` route (BE-023 placeholder → live).
- **docker-compose**: `demand-planning-service` (`expose: 8080`, depends postgres/kafka, `POSTGRES_DB=scm_demand_planning`, `WMS_KAFKA_BOOTSTRAP`, OIDC env). `infra/postgres` DB add. CI Build&Test list + scm Integration job.
- **Tests**: unit (policy eval, suggestion state machine, open-guard, dedup), slice (controllers), Testcontainers IT (alert→suggestion upsert + dedup idempotency, unmapped-SKU→DLT, ShedLock batch sweep, tenant fail-closed).

## Out of Scope

- procurement DRAFT-PO materialization + `approve` endpoint behavior — TASK-SCM-BE-025.
- Demand forecasting — v2.
- `supplier-service` — v2.
- federation live proof — TASK-SCM-INT-002 (this task's IT simulates the wms alert).

# Acceptance Criteria

- **AC-1** `:projects:scm-platform:apps:demand-planning-service:check` + `:integrationTest` pass; root CI Build&Test + scm Integration job include the module and pass.
- **AC-2** Publishing a wms-shaped `inventory.low-stock-detected` to `wms.inventory.alert.v1` upserts one `reorder_suggestion (SUGGESTED)` + a `processed_events` row.
- **AC-3** Same `eventId` twice → suggestion raised once (T8). Same SKU+warehouse while a suggestion is open → no second row (D6 open-guard).
- **AC-4** Unmapped `skuCode` → non-retryable DLT + ops alert; no silent drop.
- **AC-5** `tenant_id=wms` token → gateway 403; service-level fail-closed verified in unit test.
- **AC-6** ShedLock batch sweep detects a below-reorder-point SKU lacking a fresh alert and raises via the same guard.
- **AC-7** `GET /api/v1/demand-planning/suggestions` returns the SUGGESTED row through the gateway with a valid scm token.

# Related Specs

- [ADR-MONO-027](../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md)
- [TASK-SCM-BE-023](../ready/TASK-SCM-BE-023-demand-planning-service-spec.md) (architecture/data-model/policy/API authored there)
- `specs/services/inventory-visibility-service/architecture.md` (blueprint + read-model the batch reads)
- [rules/domains/scm.md](../../../../rules/domains/scm.md) S1/S2/S5, `rules/traits/batch-heavy.md`, [rules/traits/transactional.md](../../../../rules/traits/transactional.md) T8

# Related Contracts

- [`replenishment-subscriptions.md`](../../specs/contracts/events/replenishment-subscriptions.md) (BE-022), `demand-planning-api.md` (BE-023), wms [`inventory-events.md`](../../../wms-platform/specs/contracts/events/inventory-events.md) §7

# Edge Cases

- **transferred/adjusted vs alert**: only the `alert` topic triggers reorder; demand-planning does NOT consume the raw mutation topics (IVS does). Keep the consumer scoped to one topic.
- **availableQty already recovered**: an alert may arrive after stock was replenished by another path — re-check current policy state before raising (avoid stale-alert suggestions); document the read source for the recheck.
- **multi-instance batch**: ShedLock is mandatory (first batch-heavy code outside IVS) — without it the sweep double-raises.
- **warehouse dimension**: suggestion key is (sku_code, warehouse) — a SKU low in WH-A but fine in WH-B should suggest only for WH-A.

# Failure Scenarios

- **wms Kafka down** → no alerts; nightly batch over IVS still catches below-reorder SKUs (degraded but not blind). D8.
- **Postgres down** → consumer retry→DLT; REST 503; DLT replay on recovery (v2).
- **IVS unavailable for batch** → sweep skips that run + metric; live alert path unaffected (decoupled).
- **alert/ops publish fail** → best-effort; next batch re-detects. No outbox for alerts (justified per IVS precedent).

# Notes

- 분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (consumer + read-model + 1 batch = IVS pattern reuse; not the BE-002 6-way stress). Escalate to Opus only if the open-suggestion guard concurrency proves subtle.
- First code activating PROJECT.md's deferred 4th scm service. Memory note on merge: "demand-planning-service live = 2nd batch-heavy code site after IVS".
