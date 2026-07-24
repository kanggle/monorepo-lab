# inventory-visibility-service — Architecture

## Identity

| Field | Value |
|---|---|
| Service Name | `inventory-visibility-service` |
| Service Type | `rest-api` + `event-consumer` |
| Architecture Style | **Hexagonal** |
| Domain | scm |
| Traits | transactional, integration-heavy, batch-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Inventory Visibility (cross-node read-model for SCM portfolio) |
| Deployable unit | `apps/inventory-visibility-service/` |
| Data store | PostgreSQL `scm_inventory_visibility` schema (Flyway) + Redis aggregation cache |
| Event publication | Kafka `scm.inventory.alert.v1` (best-effort, no outbox per ADR-MONO-005 Cat C) |
| Event consumption | Kafka 3 topics: `wms.inventory.received.v1` / `wms.inventory.adjusted.v1` / `wms.inventory.transferred.v1` (cross-project, EventDedupe idempotency) |

### Service Type Composition

`inventory-visibility-service` combines two service types in one deployable unit:

- `rest-api` for synchronous **read-only** queries (cross-node inventory snapshot, staleness metadata).
  4 read endpoints (S5 — "Not for procurement decisions"), plus two mutating
  endpoints: `POST /nodes` (TASK-SCM-BE-046) for **explicit** THIRD_PARTY_LOGISTICS
  node registration, and `POST /nodes/{nodeId}/observed-stock` (TASK-SCM-BE-047)
  for **ingesting** an operator-pushed observation of stock held at an
  already-registered 3PL node. Neither is a procurement-decision surface — the
  first is an onboarding action, the second records what we *observe* at a 3PL
  we do not operate — so neither conflicts with S5's read-only intent for the
  other four.
- `event-consumer` for asynchronous **inbound** event subscription:
  - `wms.inventory.received.v1` → upsert quantity snapshot
  - `wms.inventory.adjusted.v1` → delta apply on snapshot
  - `wms.inventory.transferred.v1` → dual-row update (source + destination nodes)

Both surfaces share the same domain model (`InventoryNode` + `InventorySnapshot` +
`NodeStaleness`) and persistence. Read both
`platform/service-types/rest-api.md` and `platform/service-types/event-consumer.md`
when implementing — documented exception to the "read exactly one service-type
file" rule, justified by the CQRS read-model role.

## Responsibilities

Cross-node inventory read-model. Consumes wms-platform inventory events (cross-project) and maintains a SKU × Node quantity snapshot. Exposes read-only REST API for operators and buyers (not for PO decisions — S5).

## Architecture Style Rationale

Hexagonal chosen because:
1. Multiple inbound adapters coexist naturally: Kafka consumers (3 topics) and REST controllers share the same domain core without coupling.
2. Domain logic (staleness evaluation, idempotency check) is framework-free and fully testable.
3. Outbound adapters for JPA, Redis, Kafka alert publisher are interchangeable — important for testing (H2 slice tests) and future migration.

## Layer Structure

```
domain/         ← Pure Java: InventoryNode, InventorySnapshot, NodeStaleness, EventDedupeRecord
application/    ← Use cases + port interfaces (no Spring annotations in domain)
adapter/
  inbound/
    web/        ← REST controllers (@RestController)
    messaging/  ← Kafka @KafkaListener consumers
  outbound/
    persistence/ ← JPA entities + Spring Data repositories + adapters
    messaging/  ← KafkaTemplate alert publisher
    cache/      ← Redis aggregation cache
    batch/      ← @Scheduled staleness detection (ShedLock)
config/         ← Spring @Configuration beans only
```

## Service Type Compliance

### rest-api
- Stateless JWT auth (OAuth2 RS, IAM JWKS)
- `tenant_id=scm` fail-closed at gateway + service level
- Read-only endpoints (no mutating REST)
- Standard error envelope `{ code, message }`
- Paginated list endpoints

### REST endpoints (v1)

All 4 read-only endpoints share the `/api/inventory-visibility` base path
(rewritten from `/api/v1/inventory-visibility/**` by gateway-service). All
require a JWT that satisfies the entitlement-trust dual-accept gate
(`tenant_id ∈ {scm, *}` ∪ signed `entitled_domains ∋ scm`, § Multi-tenancy);
no per-endpoint role/scope differentiation in v1 (defense-in-depth via
`TenantClaimEnforcer` filter).
All are **gateway-routed (public)** and listed in
[`gateway-public-routes.md`](../../contracts/http/gateway-public-routes.md).
Formal request / response shapes live in
[`inventory-visibility-api.md`](../../contracts/http/inventory-visibility-api.md).

| Method | Path | Public/Internal | Controller | Purpose |
|---|---|---|---|---|
| GET | `/api/inventory-visibility/snapshot` | public | `InventoryVisibilityController#getSnapshot` | cross-node paginated snapshot list (or single-node when `?nodeId=` passed) |
| GET | `/api/inventory-visibility/sku/{sku}` | public | `InventoryVisibilityController#getSkuSnapshot` | per-SKU cross-node breakdown with Redis cache (`X-Cache: HIT/MISS/UNAVAILABLE`) |
| GET | `/api/inventory-visibility/staleness` | public | `NodeStalenessController#getStaleness` | node-by-node staleness status (FRESH / STALE / UNREACHABLE) |
| GET | `/api/inventory-visibility/nodes` | public | `InventoryVisibilityController#getNodes` | node list with status (id, externalId, type, name, status) |
| POST | `/api/inventory-visibility/nodes` | public | `NodeRegistrationController#registerNode` | explicitly register a THIRD_PARTY_LOGISTICS node (ADR-MONO-054 §D2, TASK-SCM-BE-046) — 201 new / 200 idempotent repeat / 409 `NODE_TYPE_CONFLICT` |
| POST | `/api/inventory-visibility/nodes/{nodeId}/observed-stock` | public | `NodeRegistrationController#observeStock` | record an absolute observed-stock reading for an existing THIRD_PARTY_LOGISTICS node (ADR-MONO-054 §D4, TASK-SCM-BE-047) — 200 recorded / 404 `NODE_NOT_FOUND` / 409 `NODE_TYPE_CONFLICT` / 422 `VALIDATION_ERROR` |

`/nodes` exposure decision (TASK-SCM-BE-008): **public**. Rationale:
1. The controller method has no `@PreAuthorize` and no role guard — code-level
   security is identical to the other 3 endpoints.
2. `inventory-visibility-api.md` already enumerates `/nodes` with the same
   response envelope shape as the other endpoints, treating it as part of the
   public contract.
3. Use case = ops dashboards that need to render the node list before letting
   an operator drill into per-node `/snapshot?nodeId=` or `/staleness` —
   public access is required for the dashboard flow to work end-to-end.
4. No PII or credential exposure (the response only includes `id`,
   `nodeExternalId`, `nodeType`, `name`, `status`).

All 4 endpoints carry the **S5 warning** (`meta.warning: "Not for procurement
decisions (S5)"`) so that consumers cannot mistake the eventually-consistent
read-model for an authoritative source.

### Local management endpoints

| Path | Auth | Description |
|---|---|---|
| `GET /actuator/health` | none | liveness/readiness probe |
| `GET /actuator/info` | none | build info |
| `GET /actuator/prometheus` | network-isolated | metrics scrape (internal docker network only) |

### event-consumer
- Consumer group: `scm-inventory-visibility-v1`
- 3 topics from wms-platform (cross-project): `wms.inventory.received.v1`, `wms.inventory.adjusted.v1`, `wms.inventory.transferred.v1`
- Manual ACK mode
- Retry: 3 attempts + DLT
- Idempotency: `event_dedupe` table keyed on `eventId` (UUID v7)

## Node Registration Model (ADR-MONO-054 §D2 / TASK-SCM-BE-046)

`inventory_nodes` has two structurally different birth paths, by `NodeType`:

| Node type | Birth path | Trigger |
|---|---|---|
| `WMS_WAREHOUSE` | **Auto-registered** | First `wms.inventory.{received,adjusted,transferred}.v1` event referencing an unknown `nodeExternalId` (Edge Case 3, TASK-SCM-BE-003). Name/contactInfo start empty, enriched later; `warehouseCode` learned set-if-present (ADR-MONO-050 §D9). |
| `THIRD_PARTY_LOGISTICS` | **Explicitly registered** | `POST /api/inventory-visibility/nodes` (operator/onboarding action). A 3PL relationship has no upstream event stream to be born from — it is an onboarding fact, not an event side-effect. Named **at** registration (unlike the empty-then-enriched warehouse name); `warehouseCode` stays `null` (wms-only business code, not applicable). Idempotent on `(tenant_id, node_external_id)` — a repeat registration of the same external id is a no-op returning the existing node, not a duplicate. |
| `SUPPLIER` / `IN_TRANSIT` | Declared, no active registration path in v1 | Reserved for a future task. |

A `THIRD_PARTY_LOGISTICS` node's stock is **observed read-only, never operated**
(ADR-054 §D4 / ADR-050 §D4 — a 3PL is operated by its own WMS, exactly as
`wms-platform` is authoritative for its own four walls). This service never
picks, packs, adjusts, or transfers 3PL stock — TASK-SCM-BE-046 registers an
**empty** node (no stock ingestion); read-only observation (node staleness +
snapshot ingestion for a 3PL node) is TASK-SCM-BE-047. The wms-platform
inbound-expectation whitelist gate (`CreateScmInboundExpectationService`)
stays untouched by node registration — routing a 3PL-destined inbound
expectation away from wms is TASK-SCM-BE-048, a separate producer-side
concern from this node's existence.

### 3PL stock observation ingestion (ADR-MONO-054 §D4 / TASK-SCM-BE-047)

A 3PL has **no `wms.*` Kafka event stream** — its own WMS is authoritative for
its four walls (ADR-050 §D4), so the three `wms.inventory.*` consumers never
fire for a 3PL node. `POST /api/inventory-visibility/nodes/{nodeId}/observed-stock`
is the minimal ingestion path this gap needs (ADR-054 §D4's "read APIs or
periodic snapshots"; Phase 2a picks the push form): an operator/adapter pushes
a **full, absolute** reading of the 3PL node's stock, which
`InventoryVisibilityApplicationService#applyThirdPartyObservedStock` records via
`InventorySnapshot.applyQuantity` (**set**, never `applyDelta` — the same
method the three wms consumers use is deliberately not reused here, because a
3PL observation is a point-in-time reading, not an incremental event). We still
never *operate* the 3PL's stock (no pick/pack/adjust/transfer) — this is us
recording what the 3PL reports, nothing more, keeping the "read-only toward
the 3PL" posture even though the write lands in our own read-model.

The endpoint resolves the target node via `InventoryNodeRepository#findById`
and rejects (never auto-registers) when the node is absent, not
`THIRD_PARTY_LOGISTICS`, or belongs to another tenant — `resolveOrCreateNode`
(which auto-registers a `WMS_WAREHOUSE`) is never called from this path. An
observation whose `observedAt` is older than a SKU's stored `lastEventAt` is
skipped line-by-line (stale/replayed reading), while the node's `NodeStaleness`
row is still seeded/refreshed for the push as a whole — this is the **only**
path that creates a `NodeStaleness` row for a 3PL node (registration does not),
so a 3PL node only joins the FRESH/STALE/UNREACHABLE lifecycle once first
observed.

## batch-heavy First Code

`StalenessDetectionScheduler` — `@Scheduled` fixedDelay 5 minutes, ShedLock-protected.
Evaluates all node staleness records, updates status, publishes SNAPSHOT_STALE alerts.
This is the first `batch-heavy` trait code in scm-platform (TASK-SCM-BE-003).

## Security

- OAuth2 Resource Server (RS256)
- JWKS: `${OIDC_ISSUER_URL}/oauth2/jwks`
- Validators (`ServiceLevelOAuth2Config`): JwtTimestampValidator + AllowedIssuersValidator + decode-time `tenantClaimValidator`
- **Decode-time** `tenantClaimValidator` (`ServiceLevelOAuth2Config`) — entitlement-trust dual-accept gate at JWT decode (`tenant_id ∈ {scm, *}` ∪ signed `entitled_domains ∋ scm`); carries a local `isEntitled` helper. **Both** authz layers (decode validator + filter) dual-accept; a domain-entitled cross-tenant token must survive decode before the filter runs (TASK-MONO-162)
- Service-level `TenantClaimEnforcer` filter (defense-in-depth) — entitlement-trust dual-accept gate (`tenant_id ∈ {scm, *}` ∪ signed `entitled_domains ∋ scm`); carries a local `isEntitled` helper
- Public paths: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`

## Dependencies

| Direction | Target | Protocol | Notes |
|---|---|---|---|
| In | scm-platform gateway-service | HTTP `/api/v1/inventory-visibility/**` | tenant-validated JWT 통과 후 라우팅 |
| In | wms-platform Kafka | Consumer subscribed to `wms.inventory.{received,adjusted,transferred}.v1` | EventDedupe 멱등; cross-project 첫 사례 |
| Out | PostgreSQL (inventory-visibility schema) | JDBC | InventoryNode / InventorySnapshot / NodeStaleness / EventDedupe |
| Out | Redis | TCP | read-model cache (fail-OPEN) |
| Out | IAM `/oauth2/jwks` | HTTPS | JWT 서명 검증 (libs/java-security) |

## Saga / Long-running Flow (ADR-MONO-005)

Per [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md). `inventory-visibility-service` is a **read-model**; it owns no aggregate state machine and makes no outbound synchronous business call, so it has **no Category A (multi-step saga) and no Category B (synchronous external) flow**. Two ADR-MONO-005 categories do apply:

| Flow | Category | Resilience config | Fail behavior | Metrics | Status |
|---|---|---|---|---|---|
| wms inventory event consumption (`wms.inventory.{received,adjusted,transferred}.v1`) | **C** (single-step idempotent consume, retry + DLT, no saga row) | manual ACK; 3 retries exponential backoff (1s, 2s); invalid envelope (null `eventId`/`payload`) → immediate DLT, no retry | duplicate `eventId` skipped via `event_dedupe`; retry exhaustion → `<topic>.DLT` (no silent discard) | consumer lag, DLT route count, dedupe-skip count | Compliant |
| staleness detection sweep (`StalenessDetectionScheduler`) | **D** (periodic TTL-style sweep, cluster singleton) | `@Scheduled(fixedDelay = 5 min)` + ShedLock; each run recomputes node status from `last_event_at` (deterministic, rerun-safe) | ShedLock not acquired → silent skip + metric (not an error, B5) | run/lag/failure metrics (B6) | Compliant |

The outbound `scm.inventory.alert.v1` publish is **at-most-once, no outbox** (Category C best-effort) — see § Outbox + audit_log invariants for the deliberate-deviation rationale.

## Outbox + audit_log invariants

### Transactional outbox

**N/A — deliberate ADR-MONO-005 Category C deviation.** `inventory-visibility-service` does **not** run a transactional outbox. The only published event, `scm.inventory.alert.v1` (SNAPSHOT_STALE / NODE_UNREACHABLE), is emitted **best-effort at-most-once** by `KafkaAlertPublisherAdapter`. Justification: the alert is a non-authoritative notification, not a state-of-record change — the authoritative state is the `NodeStaleness` table maintained by the 5-minute sweep. A dropped alert self-heals: the next sweep re-evaluates and re-publishes for any node still STALE/UNREACHABLE. Paying transactional-outbox cost for a self-healing notification is unjustified (ADR-MONO-005 Cat C). The envelope still uses the platform standard shape (`source = "scm-platform-inventory-visibility-service"`, `schemaVersion = 1`) per [`inventory-visibility-subscriptions.md`](../../contracts/events/inventory-visibility-subscriptions.md).

### Audit log (S7)

**N/A — no domain state machine.** scm S7 (state-transition audit trail) targets aggregates with auditable business transitions (e.g. PO lifecycle in `procurement-service`). `inventory-visibility-service` is a projection: snapshot mutations are idempotent re-applications of external `wms-platform` events, not operator-driven state transitions, and there are no mutating REST endpoints. Processing provenance is instead captured by the **`event_dedupe`** table (`eventId` + processed marker) — every applied wms event is traceable to its source envelope. The project is not `audit-heavy` (PROJECT.md § Out of Scope); no immutable external-retention audit store is in v1 scope.

## Idempotency (T8)

`inventory-visibility-service` has **no mutating REST endpoints**, so the `Idempotency-Key` header pattern (T1, used by `procurement-service`) does **not** apply here. Idempotency lives entirely on the **event-consumer** side (T8):

- **Dedupe store**: `event_dedupe` table keyed on the wms envelope `eventId` (UUID v7). A duplicate `eventId` is skipped without mutation — re-delivering the same wms event leaves the snapshot byte-identical.
- **Idempotent projection**: snapshot application is itself rerun-safe — `received` = upsert by `(skuId, nodeId)`, `adjusted` = delta apply guarded by the dedupe check, `transferred` = dual-row (source + destination) update in one unit. Replaying an already-applied `eventId` is a no-op.
- **Sweep idempotency** (batch-heavy B1): `StalenessDetectionScheduler` recomputes each node's status from `last_event_at`; running it twice in succession yields the same `NodeStaleness` rows (no accumulating side-effect).

```
event_dedupe(event_id UUID PRIMARY KEY, topic, processed_at TIMESTAMPTZ)
```

Invalid envelopes (null `eventId` or null `payload`) bypass dedupe and route straight to DLT (cannot key the dedupe table).

## Multi-tenancy

**N/A as SaaS row-level isolation — single-tenant by project classification.** `scm-platform` does **not** declare the `multi-tenant` trait (PROJECT.md § Out of Scope: it receives a IAM `tenant_id=scm` claim but does not isolate multiple organisations internally — it is one organisation's supply chain). All persisted rows belong to the `scm` tenant; there is no per-tenant partitioning column and cross-tenant reads are not a structural concern (there is only one tenant).

The domain claim is still **fail-closed enforced** at the gate via
**entitlement-trust dual-accept** (ADR-MONO-019 § D5, single-tenant gate,
defense-in-depth — consistent with `procurement-service`). A token is accepted
when **either** the legacy slug `tenant_id ∈ {scm, *}` (`*` = SUPER_ADMIN
platform-scope) **or** the IAM-signed `entitled_domains` claim (a list of domain
keys) contains `scm`; rejection (403 `TENANT_FORBIDDEN`) requires **both**
branches to fail (fail-closed; entitlement only *widens*). `entitled_domains` is
read only from an RS256/JWKS-verified token, so it is unforgeable — **IAM is the
entitlement authority**; a non-list / null / empty / non-string-element claim
degrades to "not entitled". While IAM has not yet populated `entitled_domains`
the claim is absent → only the legacy path applies → **production net-zero**
(ADR-MONO-019 **dual-accept window**; the legacy slug branch is removed in step
4 once IAM populates the claim — separate follow-up):

1. **Gateway** — `TenantClaimValidator` applies the dual-accept gate at JWT decode time.
2. **Service JWT validator chain (decode-time)** — `AllowedIssuersValidator` + the decode-time `tenantClaimValidator` (`ServiceLevelOAuth2Config`) re-run during local decode (the gateway forwards the bearer; the service decodes again). The decode-time validator applies the **entitlement-trust dual-accept** itself (TASK-MONO-162) via a local `isEntitled` helper — without it a domain-entitled cross-tenant token (e.g. globex `entitled_domains ∋ scm`) is rejected at decode before the filter's dual-accept can run.
3. **Service filter** — `TenantClaimEnforcer` servlet filter applies the dual-accept gate and rejects with 403 `TENANT_FORBIDDEN` (public actuator paths skipped). It carries its own **local** `isEntitled` helper (the entitlement check cannot be shared across modules); the decode validator and the filter are independent gates and **both** dual-accept.

This dual-accept gate is independent of row-level isolation: the
`adapter/inbound/web/TenantClaimExtractor` (which extracts `tenant_id` for
row scoping, defaulting to `scm`) is **not** an enforcement gate and is
unchanged.

The published `scm.inventory.alert.v1` payload carries a constant `tenantId: "scm"`. Consumed `wms-platform` events are cross-project but are projected into the single `scm` tenant scope.

## Mandatory Rule mapping (rules/domains/scm.md)

| Rule | Status | Mechanism |
|---|---|---|
| **S1** Multi-leg state transitions idempotent + tx-protected | N/A | No aggregate state machine — read-model only. The idempotent event projection (`event_dedupe` + upsert) is the analogue. |
| **S2** Supplier external calls carry idempotency key | N/A | No outbound supplier/external business call (only DB / Redis / JWKS). |
| **S3** Settlement period lock immutability | N/A | No settlement domain (deferred to v2 `settlement-service`). |
| **S4** Demand forecast reproducibility | N/A | No forecasting (deferred to v2 `demand-planning-service`). |
| **S5** Cross-node inventory visibility eventual consistency | ✅ Applied (primary subject) | **This service is S5's reference implementation**: eventual-consistency read-model; all 4 read endpoints emit `meta.warning: "Not for procurement decisions (S5)"`; `procurement-service` has a structural Forbidden Dependency on it. |
| **S6** Supplier credentials encryption | N/A | No supplier credentials stored (no supplier integration surface). |
| **S7** State transition audit trail | N/A | No state machine; processing provenance via `event_dedupe` (see § Outbox + audit_log invariants). |
| **S8** Reconciliation discrepancy auto-close forbidden | N/A | No reconciliation (deferred to v2 `settlement-service`). |

> S5 is **positive primary** compliance here (contrast `procurement-service`, where S5 is *negative* structural compliance — it must not depend on this service).

## Trait Rule mapping (rules/traits/)

| Trait Rule | Status | Mechanism |
|---|---|---|
| **T1** Idempotency on mutating endpoints | N/A | No mutating REST endpoints (read-only API). Idempotency is event-side (T8). |
| **T8** Idempotent event consumption | ✅ | `event_dedupe` table keyed on wms `eventId` (UUID v7); duplicate → skip without mutation. |
| **T2 / T3** Atomic state-change + transactional outbox / polling relay | N/A | No transactional outbox — alert is best-effort Cat C (deliberate, see § Outbox + audit_log invariants). |
| **T4** State machine via dedicated module | N/A | Read-model; `NodeStaleness` status (FRESH/STALE/UNREACHABLE) is a derived classification, not a domain state machine. |
| **T7** Optimistic locking on aggregates | ✅ (ordering-based) | `wms-platform` is the sole write source; the single consumer group consumes per-node-partition-ordered events, serialising snapshot writes per node. Read API and sweep do not mutate snapshots. No concurrent multi-writer to guard. |
| **I2 / I3** Circuit breaker / retry+jitter on external calls | N/A (no sync external) | No outbound synchronous business call. Consumer resilience = manual ACK + 3-retry + DLT (Cat C). |
| **I7 / I8** Vendor SDK isolation / vendor types never reach domain | N/A | No external vendor SDK — inbound is intra-platform Kafka; outbound is infra (PG/Redis/Kafka). |
| **batch-heavy B1** Idempotent + rerunnable batch | ✅ | `StalenessDetectionScheduler` recomputes status from `last_event_at` — rerun-safe, no accumulating side-effect. |
| **batch-heavy B2** Checkpoint + partial-failure recovery | ✅ (minimal, justified) | Single-pass over a bounded node set (no chunking needed); a killed run is fully recovered by the next 5-min tick (B1 rerun-safety substitutes for checkpointing). |
| **batch-heavy B3** Retry policy | ✅ | Transient failure → next scheduled tick re-runs (self-healing); the consumer path uses explicit 3-retry + DLT. |
| **batch-heavy B5** Distributed lock for cluster singleton | ✅ | **ShedLock** on `StalenessDetectionScheduler` — the first `batch-heavy` compliance in scm-platform; lock-not-acquired → silent skip + metric. |
| **batch-heavy B6** Observation + alerting | ✅ | Scheduler run/lag/failure metrics (see § Observability); `lag.seconds > 2 × interval` SLO alert. |
| **batch-heavy B7** Resource isolation (batch ↔ OLTP) | ✅ (low-risk) | Bounded read-mostly node-set sweep; no large UPDATE/INSERT, no OLTP pool contention. |
| **batch-heavy B8** Reproducibility | N/A | Project is not `data-intensive`; no model/input-hash retention requirement. |

## Observability

- Logback pattern includes `traceId`, `requestId`, `tenantId` (= `scm`), `accountId` MDC keys (set by `libs/java-observability`).
- Custom Micrometer metrics:
  - **Event consumer**: dedupe-skip count, DLT-route count, consumer lag per topic.
  - **Staleness scheduler** (batch-heavy B6): `staleness_sweep_run_count{result=success|failed|skipped}`, `staleness_sweep_duration_seconds` (histogram), `staleness_sweep_lag_seconds` (gauge — SLO alert when `> 2 × 5 min`), `staleness_sweep_failure_count`.
  - **Alert publish**: `scm_inventory_alert_publish_total{result=success|failed}` (best-effort; failure is non-fatal, re-published next sweep).
  - **Redis cache**: hit / miss / unavailable counters (mirrors the `X-Cache: HIT/MISS/UNAVAILABLE` response header).
- Tracing: OTLP via `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`; dev sampling 100%.
- Prometheus scrape on `/actuator/prometheus`, **internal docker network only** — never gateway-routed externally.

## Failure Modes

| # | Situation | Behavior |
|---|---|---|
| 1 | Duplicate wms `eventId` | skipped, no mutation (`event_dedupe`, T8) |
| 2 | Invalid envelope (null `eventId` / `payload`) | immediate `<topic>.DLT`, no retry |
| 3 | Transient consumer processing error | 3 retries (1s, 2s exponential) → `<topic>.DLT` on exhaustion |
| 4 | Cross-tenant JWT — `tenant_id ∉ {scm, *}` **and** signed `entitled_domains ∌ scm` (dual-accept both branches fail) | 403 `TENANT_FORBIDDEN` (validator chain or `TenantClaimEnforcer`) |
| 5 | Redis cache miss / Redis outage | **fail-OPEN** → fall back to PostgreSQL; response header `X-Cache: UNAVAILABLE`; never 5xx (invariant #6) |
| 6 | wms topic silent / consumer lag grows | node `last_event_at` ages → sweep flips node to STALE / UNREACHABLE → `SNAPSHOT_STALE` alert |
| 7 | `StalenessDetectionScheduler` ShedLock held by another replica | silent skip + `staleness_sweep_run_count{result=skipped}` (not an error, B5) |
| 8 | Alert Kafka publish fails | at-most-once → dropped; re-published on next 5-min sweep if node still stale (no outbox, Cat C — deliberate) |
| 9 | Node has never reported any event | classified `UNREACHABLE` → `NODE_UNREACHABLE` alert |
| 10 | Read endpoint serves eventually-stale data | by design — response carries `meta.warning: "Not for procurement decisions (S5)"` (not a failure) |
| 11 | Snapshot query for unknown SKU / node | empty result (read-model absence is normal, not 404) |
| 12 | `POST /nodes` registers an externalId already registered under a **different** `NodeType` (e.g. wms auto-registered warehouse) | 409 `NODE_TYPE_CONFLICT` — a 3PL registration must not silently overwrite an unrelated node (TASK-SCM-BE-046) |
| 13 | `POST /nodes` concurrent registration race on `uq_inventory_nodes_tenant_external` | find-or-register: the losing writer re-reads and returns the existing node, not a 500 (TASK-SCM-BE-046 Edge Case) |
| 14 | `POST /nodes/{nodeId}/observed-stock` — `nodeId` does not exist | 404 `NODE_NOT_FOUND` (TASK-SCM-BE-047) |
| 15 | `POST /nodes/{nodeId}/observed-stock` — `nodeId` resolves to a non-`THIRD_PARTY_LOGISTICS` node (e.g. a wms warehouse), or a node belonging to a different tenant | 409 `NODE_TYPE_CONFLICT` — never silently mutates a wms node's snapshot, never auto-registers (TASK-SCM-BE-047 Failure Scenario B) |
| 16 | `POST /nodes/{nodeId}/observed-stock` — a line's `observedAt` is older than the SKU's stored `lastEventAt` | that line is skipped (last-observation-wins by time); the request still returns 200 and staleness still refreshes (TASK-SCM-BE-047 Edge Case) |

## Testing Strategy

- **Unit** (`./gradlew :apps:inventory-visibility-service:test`):
  - Domain: `InventorySnapshot` (received upsert / adjusted delta / transferred dual-row apply), `NodeStaleness` FRESH/STALE/UNREACHABLE classification, `InventoryNode`, `EventDedupeRecord`.
  - Application: use-case services with mocked ports.
  - Adapters: 3 Kafka consumer mappers, validator units (`TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`), `TenantClaimEnforcerTest`.
- **Slice**: JPA adapter slices (H2), Redis cache adapter (fail-open path), REST controller slices (S5 `meta.warning` assertion), error-handler slice.
- **Integration** (`./gradlew :apps:inventory-visibility-service:integrationTest`, `@Tag("integration")`, Testcontainers PostgreSQL + Redis + Kafka):
  - Consume `wms.inventory.{received,adjusted,transferred}.v1` → snapshot upsert / delta / dual-row.
  - Duplicate `eventId` → idempotent skip (snapshot unchanged).
  - Poison envelope → DLT; transient error → 3-retry then DLT.
  - `StalenessDetectionScheduler` ShedLock — 2-instance singleton (only one runs); STALE/UNREACHABLE → `scm.inventory.alert.v1` published.
  - Redis outage → fail-open fallback, `X-Cache: UNAVAILABLE`, no 5xx.
  - Cross-tenant JWT → 403; missing tenant claim → 401.
  - All 4 read endpoints carry `meta.warning: "Not for procurement decisions (S5)"`.
  - `applyThirdPartyObservedStock` against a real registered `THIRD_PARTY_LOGISTICS`
    node (TASK-SCM-BE-047): snapshot rows persisted; visible via
    `getSnapshotByNode` and `getCrossNodeSnapshot`; `NodeStaleness` row seeded;
    unknown/wrong-type node rejected; an older `observedAt` line is skipped
    without overwriting the stored quantity.

`integrationTest` is excluded from `./gradlew check` so the fast feedback loop stays Docker-free (same convention as `procurement-service`).

## References

- `platform/architecture-decision-rule.md`
- `platform/service-types/rest-api.md` + `platform/service-types/event-consumer.md` (dual-type — documented exception to "read exactly one service-type file", see § Service Type Composition)
- `platform/error-handling.md`
- `rules/domains/scm.md` (esp. **S5** — this service is S5's reference implementation)
- `rules/traits/transactional.md` (T8), `rules/traits/integration-heavy.md`, `rules/traits/batch-heavy.md` (B1/B3/B5/B6)
- [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) — Category C consumer + Category D sweep + no-outbox best-effort alert
- [`procurement-service/architecture.md`](../procurement-service/architecture.md) — sibling architecture (section structure reference)
- [`gateway-service/architecture.md`](../gateway-service/architecture.md)
- [`iam-integration.md`](../../integration/iam-integration.md)
- [`gateway-public-routes.md`](../../contracts/http/gateway-public-routes.md)
- [`inventory-visibility-api.md`](../../contracts/http/inventory-visibility-api.md)
- [`inventory-visibility-subscriptions.md`](../../contracts/events/inventory-visibility-subscriptions.md)
- [`data-model.md`](data-model.md) · [`staleness-monitoring.md`](staleness-monitoring.md) · [`overview.md`](overview.md)
- TASK-SCM-BE-003 — bootstrap (first `batch-heavy` code) · TASK-SCM-BE-008 — `/nodes` public decision · TASK-SCM-BE-014 — this section-completion task
