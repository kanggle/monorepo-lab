# logistics-service — Architecture

Activated by **[ADR-MONO-053](../../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md)** (logistics-service goes multimodal — carrier dispatch now, 3PL fulfillment designed-in), which fires **[ADR-MONO-052](../../../../../docs/adr/ADR-MONO-052-transport-context-map.md)** §D8-2. scm's 5th domain service, lifted from PROJECT.md's v2-deferred Service Map. **This document covers Phase 1 (carrier dispatch)**; the 3PL fulfillment path (ADR-053 §D5) and tracking (§D6) are marked *Phase 2* / *Phase 3* throughout and are **not** built by this spec.

## Identity

| Field | Value |
|---|---|
| Service Name | `logistics-service` |
| Service Type | `event-consumer` + `rest-api` |
| Architecture Style | **Hexagonal** |
| Domain | scm |
| Traits | transactional, integration-heavy, batch-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Logistics Coordination (carrier dispatch of confirmed shipments; the road, not the dock — ADR-052 §D1) |
| Deployable unit | `apps/logistics-service/` |
| Data store | PostgreSQL `scm_logistics` schema (Flyway) |
| Event consumption | Kafka `wms.outbound.shipping.confirmed.v1` (`outbound.shipping.confirmed`, **cross-project seam**, `processed_events` idempotency — see `specs/contracts/events/logistics-dispatch-subscriptions.md`) |
| Event publication | none in Phase 1. In-transit / tracking events are Phase 2/3 (ADR-053 §D5/§D6). |
| Outbound sync calls | carrier-aggregator vendor REST (**EasyPost**, **굿스플로**) via `ShipmentDispatchPort`; no internal-service REST (the seam is a fact event, never a synchronous wms→logistics call — ADR-052 §D5/§A3). |

### Service Type Composition

`logistics-service` combines two service types in one deployable unit, sharing one
domain core (`Dispatch` aggregate + `Carrier` value objects):

- **`event-consumer`** — the primary trigger. Subscribes to the wms
  `outbound.shipping.confirmed.v1` fact event (a shipment left a wms warehouse) →
  routes a carrier → pushes the shipment to that vendor. Idempotent on envelope
  `eventId` (T8) + business-dedup on `shipment_id` (a shipment dispatches once).
- **`rest-api`** — operator surface: inspect a dispatch, and **re-drive** a failed
  dispatch (`POST /api/v1/logistics/dispatches/{id}:retry`) — the relocation target
  of the wms `:retry-tms-notify` endpoint (ADR-053 §D8).

Read `platform/service-types/event-consumer.md` and `rest-api.md` when
implementing — documented exception to the "read exactly one service-type file"
rule, justified by a consumer-driven service that also exposes a small operator
recovery surface. (`batch-job` is declared on the project but **not** used by this
service in Phase 1 — a dispatch sweep/reconciler is a Phase 2 candidate.)

## Responsibilities (ADR-053 §D2 — carrier dispatch only)

`logistics-service` takes a **confirmed shipment** (goods already left a wms
warehouse) and gets it onto a carrier. In Phase 1 it does **not**:

- own physical inventory or the pick/pack/ship floor (that is wms — the shipment
  arrives *after* wms's `SHIPPED`),
- decide *which node* fulfills an order (that is upstream — self-fulfillment routes
  through wms today; the 3PL branch is Phase 2, ADR-053 §D5),
- run the 3PL fulfillment path or observe 3PL inventory (Phase 2 — the
  `THIRD_PARTY_LOGISTICS` node factory lives in inventory-visibility-service),
- track parcels post-dispatch (Phase 3 — tracking is a separate axis, ADR-053 §D6).

Fact event in (`shipping.confirmed`) → carrier selected → vendor dispatched →
dispatch record `DISPATCHED` (or `DISPATCH_FAILED` + operator retry). That is the
whole Phase-1 loop.

## Architecture Style Rationale

Hexagonal chosen because:
1. The vendor is a swappable outbound detail — **multi-vendor** dispatch behind a
   single `ShipmentDispatchPort` is the core reason this service exists (one port,
   EasyPost + 굿스플로 + a standalone stub). The domain calls `port.dispatch(shipment)`
   without knowing which carrier answers (integration-heavy I7/I8).
2. The seam is an inbound Kafka adapter; the operator retry is an inbound REST
   adapter; both drive the same `DispatchShipmentUseCase` without coupling.
3. `CarrierRouter` and the `FulfillmentRouter` seam are framework-free domain
   services, fully unit-testable, and the 3PL branch (Phase 2) attaches to the
   `FulfillmentRouter` without touching the dispatch path (ADR-053 §D4).

## Layer Structure

```
domain/         ← Pure Java: Dispatch (status machine PENDING→DISPATCHED→
                  DISPATCH_FAILED; carries requestedCarrierCode = the routing input),
                  Carrier, value objects (ShipmentId, TrackingNo, CarrierCode),
                  ProcessedEvent
application/    ← Use cases (DispatchShipmentUseCase, RetryDispatchUseCase) +
                  CarrierRouter + FulfillmentRouter (self-branch) + outbound ports
                  (ShipmentDispatchPort, DispatchPersistencePort)
adapter/
  inbound/
    messaging/  ← ShippingConfirmedConsumer (@KafkaListener on the seam topic)
    web/        ← DispatchController (@RestController — inspect + :retry)
  outbound/
    dispatch/   ← ShipmentDispatchPort implementations:
                    EasyPostDispatchAdapter   @Profile("!standalone")
                    GoodsflowDispatchAdapter  @Profile("!standalone")
                    StandaloneDispatchAdapter @Profile("standalone")
                  + vendor DTOs / mappers (package-private, I8) + dispatch_request_dedupe
    persistence/ ← JPA entities + Spring Data repositories + adapters
config/         ← Spring @Configuration beans only (per-vendor RestClient + Resilience4j)
```

## Service Type Compliance

### event-consumer
- Group `scm-logistics-v1`; subscribes only to `wms.outbound.shipping.confirmed.v1`
  (NOT wms's other outbound topics — those are not the seam).
- Idempotent on envelope `eventId` (T8) via `processed_events`; **plus** a
  business guard on `shipment_id` (a shipment has exactly one dispatch record — a
  redelivered event finds the existing `DISPATCHED` record and no-ops).
- Manual ack after the dispatch TX commits. Retry 3× → DLT
  (`wms.outbound.shipping.confirmed.v1.DLT`). A **malformed envelope** is
  non-retryable → immediate DLT + ops alert. A **vendor failure** is NOT a consume
  failure: the event is ack'd, the dispatch record is persisted `DISPATCH_FAILED`,
  and recovery is the operator `:retry` endpoint (mirrors the wms post-commit shape
  being retired — ADR-052 §2.10).

### rest-api
- Stateless JWT auth (OAuth2 RS, IAM JWKS).
- `tenant_id=scm` fail-closed at gateway + service level, **entitlement-trust
  dual-accept** (legacy `tenant_id ∈ {scm,*}` ∪ signed `entitled_domains ∋ scm`;
  reject = `!legacyOk && !entitled`, fail-closed) per the SCM-BE-019 blueprint
  (local `isEntitled` helper — module-boundary, not shared).
- `POST /dispatches/{id}:retry` is naturally idempotent — re-invoking for an
  already-`DISPATCHED` shipment returns the cached vendor ack with no vendor call
  (the `dispatch_request_dedupe` short-circuit).
- Standard error envelope `{ code, message }`; codes from `rules/domains/scm.md`
  plus `DISPATCH_NOT_FOUND`, `CARRIER_UNROUTABLE`, `DISPATCH_ALREADY_COMPLETED`.

## Mandatory Section Mapping (scm S-rules)

| Rule | Application |
|---|---|
| S1 (multi-leg transitions idempotent + Tx-protected) | `Dispatch` status machine `PENDING→DISPATCHED` / `PENDING→DISPATCH_FAILED→DISPATCHED`; each transition Tx-guarded + idempotent; a re-consumed event or a repeated `:retry` never double-dispatches. |
| S2 (idempotency keys on outbound) | consumer dedup on `eventId`; the vendor call carries `Idempotency-Key = {shipment.id}` (stable across Resilience4j retry and operator re-drive), backed by the local `dispatch_request_dedupe` ground-truth (relocated from wms, ADR-052 §2.7). |
| S5 (eventual consistency) | dispatch happens **after** the wms shipping-confirm TX has committed and its event was published; wms never blocks on logistics (ADR-052 §D5). |
| S7 (audit trail) | every `Dispatch` transition (created / dispatched / failed / retried) recorded for operator audit. |

## Idempotency

Three layers:
1. **Event dedup** — `eventId` (T8) in `processed_events`. Redelivery = no-op.
2. **Shipment-dispatch uniqueness** — a unique constraint on `dispatch.shipment_id`
   (a shipment dispatches once). The consumer upserts idempotently.
3. **Idempotency toward the vendor** — `Idempotency-Key = {shipment.id}` on the
   dispatch call + `dispatch_request_dedupe(request_id PK, sent_at,
   response_snapshot)`. On a repeat send the adapter returns the cached ack without
   a network call (integration-heavy I4; relocated verbatim from the wms interim so
   dedupe semantics are byte-preserved).

## Saga / Long-running (ADR-MONO-005)

- **Cat C** (best-effort post-commit action) — the `shipping.confirmed` →
  dispatch step. Stock is already consumed upstream; a failed dispatch is a
  recorded `DISPATCH_FAILED` state, not a rollback. Recovery is the operator
  `:retry` endpoint (Phase 2 may add an automatic sweep — a Cat D reconciler).
- No Cat A/B saga — a failed dispatch compensates nothing; the shipment simply is
  not yet on a carrier.

This is the **receiver** ADR-052 §D7 named: once this consumer is live, wms stops
calling the vendor and its outbound saga completes at *shipped + event published*
(the paired wms simplification is a separate Phase-1 cross-project task, ADR-053 §D8).

## Outbox

**Not used in Phase 1 (justified).** The service consumes a fact event and calls
an external vendor; it publishes no domain event. If Phase 2/3 emits an
in-transit or tracking event (ADR-053 §D5/§D6), that emission adopts the
transactional outbox (T3) and this decision is revisited. Mirrors
demand-planning's and inventory-visibility's deliberate no-outbox rationale (Cat C).

## Multimodal seam (ADR-053 §D4 — Phase 1 shape)

- **`CarrierRouter`** selects the vendor for a self-fulfilled shipment by the
  shipment's **`carrierCode`** (the routing signal the seam actually carries — the
  seam carries **no** geographic region, the documented "known input gap"). A
  config-driven `carrierCode → vendor` registry models the split (domestic carriers
  → 굿스플로; international → EasyPost); a **null or unmapped** `carrierCode` → a
  documented default vendor with a `CARRIER_UNROUTABLE` degrade, never a silent drop
  — see Failure Modes. Exactly one vendor per shipment. (BE-043 amended this from an
  earlier "by Region" wording: the built router routes on `carrierCode`, and no
  `Region` value object exists — the domestic/international split is registry
  structure, per the subscriptions contract § CarrierRouter selection.)
- **`FulfillmentRouter`** is introduced with **only the self-fulfillment branch
  wired**: every input in Phase 1 arrives via `shipping.confirmed` (i.e. wms already
  fulfilled), so the router resolves to *carrier-dispatch*. The Phase-2 3PL branch
  (send a fulfillment request to a 3PL instead of dispatching a wms-shipped parcel)
  attaches here **without** modifying the dispatch path. Documented as an extension
  point, not active routing logic, in Phase 1.

## Observability

Metrics (per-vendor labels): `logistics_dispatch_total{vendor,result=success|4xx|5xx|timeout|circuit_open|bulkhead_full}`,
`logistics_dispatch_duration_seconds{vendor}`, `logistics_dispatch_retry_total{vendor,attempt}`,
`logistics_dispatch_circuit_state{vendor}` (0=closed,1=half,2=open),
`logistics_dispatch_failed_total` (alerts >0), `logistics_dispatch_dedupe_hit_total`,
`logistics_event_dedup_hits_total`. Kafka consumer trace propagation from the wms
envelope; the vendor call is a child span `logistics.dispatch`. Logs on every
`Dispatch` transition. Full per-vendor matrix in
[`external-integrations.md`](external-integrations.md).

## Failure Modes

| Failure | Behaviour |
|---|---|
| wms Kafka down | no shipments arrive; nothing to dispatch (upstream-degraded, not a logistics fault). Recovers on Kafka heal (consumer resumes from committed offset). |
| Postgres down | consumer retry → DLT; REST 503. |
| Vendor (EasyPost/굿스플로) 5xx / timeout | per-vendor retry+jitter → circuit; on exhaustion the event is ack'd, dispatch persisted `DISPATCH_FAILED`, alert fires; operator `:retry` recovers. Never a consume failure (S5). |
| Vendor 4xx (bad request) | non-retryable; `DISPATCH_FAILED` with reason; alert (config/domain bug, not vendor health). |
| `carrierCode` null or unmapped by `CarrierRouter` | route to the documented **default vendor** and emit a `CARRIER_UNROUTABLE` degrade (log + metric) — never silently drop the shipment. |
| Duplicate `shipping.confirmed` (same eventId or same shipmentId) | no-op via the two dedup layers; no double dispatch. |
| Vendor honours idempotency key with a 409/202 | adapter treats as "already accepted", returns cached ack (I4). |

## Testing

Unit (dispatch state machine, `CarrierRouter` carrierCode selection incl. null/unmapped
fallback, `FulfillmentRouter` self-branch, dedup). Slice (`@WebMvcTest`
DispatchController incl. `:retry` idempotency). **WireMock** per vendor — success,
timeout, 5xx, 4xx, circuit-open, bulkhead-full, idempotency-replay (the matrix in
`external-integrations.md`). Testcontainers IT — `shipping.confirmed` →
dispatch upsert + dedup, malformed-envelope → DLT, vendor-failure →
`DISPATCH_FAILED` + retry-endpoint recovery, tenant fail-closed. Standalone profile
(`StandaloneDispatchAdapter`) so local/CI bring-up needs no vendor credentials.
E2E = a Phase-1 cross-service task (BE-042+ series).

## Dependencies

PostgreSQL `scm_logistics`, Kafka (wms `outbound.shipping.confirmed.v1` source),
IAM IdP (JWKS), gateway-service (`/api/v1/logistics/**` route — reserved by this
spec, wired in BE-042), external carrier aggregators **EasyPost** + **굿스플로**
(see [`external-integrations.md`](external-integrations.md)). No internal-service
REST dependency (the seam is a fact event). Phase 2 will add
inventory-visibility-service (`THIRD_PARTY_LOGISTICS` node) and a 3PL vendor.
