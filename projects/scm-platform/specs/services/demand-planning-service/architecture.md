# demand-planning-service — Architecture

Activated by **ADR-MONO-027** (wms → scm stock-replenishment loop). scm's 4th
domain service, lifted from PROJECT.md's v2-deferred Service Map.

## Identity

| Field | Value |
|---|---|
| Service Name | `demand-planning-service` |
| Service Type | `event-consumer` + `batch-job` + `rest-api` |
| Architecture Style | **Hexagonal** |
| Domain | scm |
| Traits | transactional, integration-heavy, batch-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Replenishment Planning (reorder decisioning for the SCM portfolio) |
| Deployable unit | `apps/demand-planning-service/` |
| Data store | PostgreSQL `scm_demand_planning` schema (Flyway) |
| Event consumption | Kafka `wms.inventory.alert.v1` (`inventory.low-stock-detected`, cross-project, `processed_events` idempotency — see `specs/contracts/events/replenishment-subscriptions.md`) |
| Event publication | none in v1 — the suggestion → procurement DRAFT-PO leg is **synchronous intra-scm REST** (ADR-027 D5). No outbox (justified below). |
| Outbound sync calls | procurement-service internal REST (DRAFT-PO-from-suggestion), inventory-visibility-service REST (nightly batch read) |

### Service Type Composition

`demand-planning-service` combines three service types in one deployable unit,
all sharing one domain core (`ReorderPolicy` + `SkuSupplierMapping` +
`ReorderSuggestion`):

- **`event-consumer`** — the primary trigger. Subscribes to the wms low-stock
  alert (`wms.inventory.alert.v1`) → evaluates the scm reorder policy → raises a
  `reorder_suggestion`. Idempotent (eventId T8) + business-dedup (open-suggestion
  guard, D6).
- **`batch-job`** — nightly `ReorderSweepScheduler` (ShedLock single-instance)
  reads the inventory-visibility read-model for SKUs sitting below their reorder
  point **without** a fresh alert, and raises suggestions through the same guard.
  (`batch-heavy` trait, 2nd code site after inventory-visibility's staleness batch.)
- **`rest-api`** — operator surface: list/inspect suggestions, approve (→ D5
  DRAFT-PO materialization), dismiss, and CRUD the `reorder_policy` /
  `sku_supplier_map` seed.

Read `platform/service-types/event-consumer.md`, `batch-job.md`, and
`rest-api.md` when implementing — documented exception to the "read exactly one
service-type file" rule, justified by the decisioning role that fans one domain
core across a consumer, a scheduler, and a REST surface.

## Responsibilities (ADR-027 D7 — decisioning only)

`demand-planning-service` decides **whether and how much to reorder**, and hands
a DRAFT purchase order to procurement. It does **not**:

- own physical inventory (that is wms `inventory-service`),
- own the PO lifecycle or supplier dispatch (that is scm `procurement-service` —
  this service only creates the **DRAFT**; the operator submits),
- fulfill customer orders (that is ecommerce/wms, ADR-022).

Trigger in (low-stock alert) → reorder suggestion out → DRAFT PO handed off. The
customer's out-of-stock order is **not** this service's concern (it is already
cancelled+refunded by ADR-022's backorder path); this loop refills *future* stock.

## Architecture Style Rationale

Hexagonal chosen because:
1. Three inbound adapters (Kafka consumer, `@Scheduled` batch, REST controllers)
   share one domain core without coupling.
2. The reorder-policy evaluation and the open-suggestion guard are framework-free
   and fully unit-testable.
3. Outbound adapters (JPA, procurement REST client, inventory-visibility REST
   client) are interchangeable — the procurement leg can move from sync REST to
   an intra-scm event in v2 (D5) by swapping one outbound adapter.

## Layer Structure

```
domain/         ← Pure Java: ReorderPolicy, SkuSupplierMapping, ReorderSuggestion
                  (status machine), ProcessedEvent, value objects (SkuCode, Quantity)
application/    ← Use cases (EvaluateReorderUseCase, ApproveSuggestionUseCase,
                  SweepReorderUseCase) + outbound ports
adapter/
  inbound/
    web/        ← REST controllers (@RestController)
    messaging/  ← WmsLowStockAlertConsumer (@KafkaListener)
  outbound/
    persistence/ ← JPA entities + Spring Data repositories + adapters
    procurement/ ← ProcurementDraftPoClient (internal REST, D5)
    visibility/  ← InventoryVisibilityClient (batch read)
    batch/       ← ReorderSweepScheduler (@Scheduled + ShedLock)
config/         ← Spring @Configuration beans only
```

## Service Type Compliance

### event-consumer
- Group `scm-demand-planning-v1`; subscribes only to `wms.inventory.alert.v1`
  (NOT the raw mutation topics — those are inventory-visibility's, S5).
- Idempotent on envelope `eventId` (T8) via `processed_events`.
- Manual ack; retry 3× → DLT (`wms.inventory.alert.v1.DLT`). null-envelope /
  unmapped-SKU = non-retryable → immediate DLT + ops alert (fail-closed).

### batch-job
- `ReorderSweepScheduler`, nightly, ShedLock single-instance (mandatory — without
  it, multi-replica double-raises).
- Reads inventory-visibility read-model asynchronously (S5 — never couples the
  suggestion decision to IVS availability synchronously in the live path).
- IVS read uses `InventoryVisibilityRestAdapter` → IVS internal endpoint
  `GET /internal/inventory-visibility/snapshot` (no JWT — the batch is unattended
  with no operator token; network-trusted, gateway-blocked; ADR-MONO-027 §D7.1).
  On any transport/IVS failure the sweep skips the run + increments
  `reorder_sweep_ivs_unavailable_total`; the live alert path is unaffected.
- Restartable / idempotent: re-running the sweep funnels through the same
  open-suggestion guard, so a re-run raises no duplicate.

### rest-api
- Stateless JWT auth (OAuth2 RS, IAM JWKS).
- `tenant_id=scm` fail-closed at gateway + service level, **entitlement-trust
  dual-accept** (legacy `tenant_id ∈ {scm,*}` ∪ signed `entitled_domains ∋ scm`;
  reject = `!legacyOk && !entitled`, fail-closed) per the SCM-BE-019 blueprint
  (local `isEntitled` helper — module-boundary, not shared).
- Standard error envelope `{ code, message }`; codes from `rules/domains/scm.md`
  plus `SKU_SUPPLIER_UNMAPPED`, `SUGGESTION_ALREADY_MATERIALIZED`.

## Mandatory Section Mapping (scm S-rules)

| Rule | Application |
|---|---|
| S1 (multi-leg transitions idempotent + Tx-protected) | `ReorderSuggestion` status machine `SUGGESTED→APPROVED→MATERIALIZED→DISMISSED`; each transition Tx-guarded + idempotent. |
| S2 (idempotency keys on outbound) | consumer dedup on `eventId`; D5 procurement call idempotent on `sourceSuggestionId`. |
| S5 (eventual consistency) | batch reads IVS read-model async; live path uses the alert. Never blocks on IVS. |
| S7 (audit trail) | suggestion lifecycle transitions recorded (created/approved/dismissed/materialized) for operator audit. |

## Idempotency

Two layers (ADR-027 D6):
1. **Event dedup** — `eventId` (T8) in `processed_events`. Redelivery = no-op.
2. **Open-suggestion guard** — a partial-unique index on `reorder_suggestion`
   `(tenant_id, sku_code, warehouse_id) WHERE status IN ('SUGGESTED','APPROVED')`
   prevents piling up suggestions for the same SKU+warehouse while one is open.
   This is the authoritative *business*-duplicate guard (wms's 1h alert debounce
   does not cover a drop-then-redrop across the window — that re-fires a new
   eventId). Both the consumer and the batch funnel through it.

## Saga / Long-running (ADR-MONO-005)

- **Cat C** (best-effort consumer) — the alert→suggestion consumer; self-healing
  via the nightly batch if an alert is missed.
- **Cat D** (sweep) — the nightly `ReorderSweepScheduler`.
- No Cat A/B saga — the suggestion→DRAFT-PO leg is a single synchronous,
  idempotent, compensation-free call (a failed call leaves the suggestion
  `APPROVED`, retry-safe; no partial PO).

## Outbox

**Not used (justified).** v1 emits no domain event — the procurement leg is
synchronous intra-scm REST (D5). The suggestion is a local aggregate; procurement
owns its own outbox for the PO it creates. If v2 moves D5 to an intra-scm event,
that emission adopts the transactional outbox (T3) and this decision is revisited.
Mirrors inventory-visibility's deliberate no-outbox rationale (Cat C).

## Observability

Metrics: `reorder_suggestions_raised_total{source}`, `reorder_alert_dedup_hits_total`,
`reorder_suggestion_unmapped_sku_total`, `reorder_batch_sweep_duration_seconds`,
`reorder_draft_po_created_total`, `reorder_draft_po_failures_total`. Kafka consumer
trace propagation from the wms envelope. Logs on every suggestion transition.

## Failure Modes

| Failure | Behaviour |
|---|---|
| wms Kafka down | no alerts; nightly batch over IVS still catches below-reorder SKUs (degraded, not blind). D8. |
| Postgres down | consumer retry→DLT; REST 503. |
| IVS unavailable (batch) | sweep skips that run + metric; live alert path unaffected (decoupled, S5). |
| procurement down (at approve) | approve 5xx, suggestion stays `APPROVED`, operator retries (idempotent on `sourceSuggestionId`); no orphan PO. |
| unmapped SKU | non-retryable DLT + ops alert; no suggestion (fail-closed). |

## Testing

Unit (policy eval, suggestion state machine, open-guard, dedup), slice
(`@WebMvcTest` controllers), Testcontainers IT (alert→suggestion upsert + dedup,
unmapped-SKU→DLT, ShedLock batch sweep, approve→procurement DRAFT PO, tenant
fail-closed). E2E = TASK-SCM-INT-002.

## Dependencies

PostgreSQL `scm_demand_planning`, Kafka (wms alert source), IAM IdP (JWKS),
gateway-service (`/api/v1/demand-planning/**` route), procurement-service (D5
DRAFT-PO), inventory-visibility-service (batch read). No external vendor / supplier
API in v1 (supplier-service deferred — ADR-027 D3).
