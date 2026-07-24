# outbound-service — External Integrations

External vendor catalog for `outbound-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

This document declares **every** external system `outbound-service`
integrates with — direction, auth, timeouts, circuit-breaker policy, retry
policy, observability hooks. Implementation must match these declarations.
Changes here precede code changes (per `CLAUDE.md` Contract Rule).

The primary external integration in this service is the **inbound ERP order
webhook** (§1). The former outbound TMS push was retired in TASK-BE-560 —
carrier dispatch is owned by the scm `logistics-service` (ADR-MONO-053 §D8); see
§2. The remaining vendors mirror the inbound-service catalog.

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| **External ERP** | inbound (receive) | HTTPS webhook | HMAC-SHA256 | Order reception |
| **External ERP** | outbound (push ack) | — | — | **Not in v1** |
| ~~External TMS~~ | ~~outbound (push)~~ | — | — | **Retired** — carrier dispatch relocated to scm `logistics-service` (ADR-MONO-053 §D8, TASK-BE-560) |
| **Kafka cluster** | both | TCP / SASL | mTLS or SCRAM | event publish + saga reply / master snapshot consume |
| **PostgreSQL** | outbound (DB) | TCP | password | persistence |
| **Redis** | outbound (cache) | TCP | password | idempotency store |
| **Secret Manager** | outbound (config) | HTTPS | service-account / IAM | ERP webhook secret retrieval |

Internal services (`master-service`, `inventory-service`,
`gateway-service`, `admin-service`) are not "external" — they live in the
same project, share the same Kafka cluster, and follow internal-event
contracts in `specs/contracts/`. They are documented in
[`specs/services/outbound-service/architecture.md`](architecture.md) §
Dependencies.

---

## 1. External ERP — Inbound Webhook (Order Push)

The ERP system pushes order events to `outbound-service` via HTTPS
webhook. This is the primary integration path between ERP and the outbound
flow in v1; ERP does not poll any outbound REST endpoint, and we do not
push acks back to ERP.

### 1.1 Endpoint

```
POST {gateway-base}/webhooks/erp/order
```

Routed via `gateway-service` to `outbound-service:8084/webhooks/erp/order`.

Full wire-level contract:
[`specs/contracts/webhooks/erp-order-webhook.md`](../../contracts/webhooks/erp-order-webhook.md).

### 1.2 Authentication

- **HMAC-SHA256** signature over the raw request body.
- Secret: per-environment shared secret in Secret Manager
  (`erp-order-prod`, `erp-order-stg`, `erp-order-dr`). Distinct from the
  inbound-service ASN secret — separate secret per logical channel.
- Header: `X-Erp-Signature: sha256=<lowercase-hex>`.
- Verified before any other processing — failure returns 401 with no DB
  writes.

### 1.3 Anti-Replay

- `X-Erp-Timestamp` window: ±5 minutes (configurable
  `outbound.webhook.erp.timestamp-window-seconds`, max 600s).
- `X-Erp-Event-Id` dedupe via `erp_order_webhook_dedupe` table (7-day
  retention).

### 1.4 Inbound Side: Failure Modes (per I10)

| Scenario | Response | DB effect | Observable |
|---|---|---|---|
| Bad signature | 401 `WEBHOOK_SIGNATURE_INVALID` | none | metric `outbound.webhook.received.total{result=signature_invalid}` |
| Stale timestamp | 401 `WEBHOOK_TIMESTAMP_INVALID` | none | metric `..{result=timestamp_invalid}` |
| Unknown source | 401 `WEBHOOK_SIGNATURE_INVALID` | none | metric `..{result=signature_invalid}` (no secret available) |
| Schema invalid | 422 `VALIDATION_ERROR` | none | metric `..{result=schema_invalid}` |
| Duplicate event-id | 200 `ignored_duplicate` | dedupe row only (no inbox write) | metric `..{result=duplicate}` |
| Domain validation fails (master ref unknown, partner inactive) | 200 `accepted` synchronously; later inbox `status=FAILED` | inbox row + dedupe row + later FAILED status update | metric `outbound.webhook.processing.failure.total{reason}` |
| DB unavailable during ingest | 503 `SERVICE_UNAVAILABLE` | none | infra-level alert |
| Background processor backlog > 100 PENDING | 200 `accepted` (ingest unaffected) | inbox row queues | gauge `outbound.webhook.inbox.pending.count` alerts |

Tests covering each: see
[`specs/contracts/webhooks/erp-order-webhook.md`](../../contracts/webhooks/erp-order-webhook.md)
§ "Failure-mode Test Cases".

### 1.5 Backpressure

- Webhook accepts requests as fast as the DB can write
  `erp_order_webhook_inbox` + `erp_order_webhook_dedupe` rows (~1 ms each).
- The **background processor** — not the webhook — does the heavy domain
  work (creating Order + OutboundSaga + outbox rows). It runs every 1s,
  picks up `LIMIT 50` PENDING rows.
- If ERP saturates the webhook, the backlog grows (not a request failure).
- Gauge `outbound.webhook.inbox.pending.count > 100` is an alert; ops
  scales the processor cron interval down to 0.5s, or temporarily
  increases the batch size.

### 1.6 ERP-side Retry Expectations

We do not control ERP retry behavior, but document our expectations so the
integration team can compare with their ERP vendor's behavior:

- ERP retries on 5xx and connection failure with exponential backoff (we
  recommend min 30s, max 5 attempts).
- ERP retries on 503 with same `X-Erp-Event-Id`.
- ERP must NOT retry on 401 / 422 — those are caller-side fixes.
- After retries exhaust, ERP escalates to **its own** DLQ; ops investigates
  via the integration's runbook.

### 1.7 Outbound to ERP (NOT IN v1)

Pushing order acks back to ERP is out of scope for v1. ERP polls our
`GET /api/v1/outbound/orders?status=SHIPPED` (synchronous read) for
reconciliation. v2 may add an outbound webhook (`POST {erp-base}/wms/order-ack`).
When introduced:

- Outbound HTTP client: WebClient with `connectTimeout=5s`,
  `readTimeout=30s`
- Circuit breaker: Resilience4j `erpAckCircuit`,
  fail-rate-threshold=50%, sliding-window=60s
- Retry: 3 attempts, exponential backoff with jitter (1s → 2s → 4s ±20%)
- Idempotency: outbound payload includes our `eventId` (= the outbound
  outbox event id) so ERP can dedupe

These are placeholders — fully specified when v2 is scheduled.

---

## 2. Carrier Dispatch — Relocated to scm logistics-service (ADR-MONO-053 §D8)

The outbound-service TMS notification side-channel — a post-commit HTTPS push
of the confirmed shipment to a Transportation Management System, plus its
`SHIPPED_NOT_NOTIFIED` alert state, `tms_request_dedupe` idempotency table, and
`:retry-tms-notify` recovery endpoint — has been **retired** (TASK-BE-560).

Carrier dispatch (multimodal, 3PL-ready) is now owned by the scm
`logistics-service`, which consumes the `outbound.shipping.confirmed` event and
drives the carrier itself, per **ADR-MONO-053 §D8** (custody boundary in
ADR-MONO-052 §D7). outbound-service therefore holds **no** outbound HTTP vendor
integration, no TMS config/secret, and no TMS metrics. The only remaining
external integration is the inbound ERP order webhook (§1).

> The vendor wire spec (formerly `specs/contracts/http/tms-shipment-api.md`) is
> deleted; the carrier-side contract now lives with logistics-service.

---

## 3. Kafka Cluster

### 3.1 Direction

- **Outbound (publish)**: 7 topics for outbound events (see
  `specs/contracts/events/outbound-events.md` § Topic Layout — Open
  Item):
  `wms.outbound.order.received.v1`, `wms.outbound.order.cancelled.v1`,
  `wms.outbound.picking.requested.v1`,
  `wms.outbound.picking.cancelled.v1`,
  `wms.outbound.picking.completed.v1`,
  `wms.outbound.packing.completed.v1`,
  `wms.outbound.shipping.confirmed.v1`.
- **Inbound (consume)**: 4 inventory reply topics
  (`wms.inventory.{reserved,released,confirmed,adjusted}.v1`) +
  6 master-data topics
  (`wms.master.{warehouse,zone,location,sku,lot,partner}.v1`).

### 3.2 Connection

- Bootstrap: `kafka.brokers` (env-driven, 3+ brokers in prod).
- Auth: SASL/SCRAM-SHA-512 in dev/staging; mTLS in prod (per
  `platform/security-rules.md`).
- Client property `client.id = outbound-service-{instance-id}` for
  broker-side monitoring.

### 3.3 Producer Config (outbox publisher)

```yaml
spring.kafka.producer:
  acks: all                       # await all in-sync replicas
  retries: 5                      # broker-side retries (additive to outbox-level retry)
  enable-idempotence: true        # exactly-once-on-broker semantics
  max-in-flight-requests-per-connection: 5
  compression-type: lz4
  request-timeout-ms: 30000
  delivery-timeout-ms: 120000
  properties:
    linger.ms: 5
    batch.size: 16384
```

Outbox publisher (separate from broker retries):

- Reads `outbound_outbox` rows where `published_at IS NULL`.
- Publishes one row per `KafkaTemplate.send()` call.
- Partition key: `partition_key` column (set to `saga_id` for saga
  events; `order_id` for order lifecycle events). This guarantees per-saga
  in-order delivery for cross-service correlation.
- On broker ACK: sets `published_at = now()`.
- On broker NACK: leaves `published_at = null`. Next scheduled run
  retries.
- Backoff between retries: exponential 1s → 2s → 4s → 8s → 16s (capped).
- Metrics: `outbound.outbox.pending.count`, `outbound.outbox.lag.seconds`
  (now - oldest unpublished row's `created_at`),
  `outbound.outbox.publish.failure.total`.

### 3.4 Consumer Config

```yaml
spring.kafka.consumer:
  group-id: outbound-service
  auto-offset-reset: earliest
  enable-auto-commit: false
  isolation-level: read_committed
  max-poll-records: 50
  fetch-max-bytes: 5242880          # 5 MiB
  session-timeout-ms: 45000
  heartbeat-interval-ms: 10000
  properties:
    spring.json.trusted.packages: com.wms.outbound.adapter.in.messaging
```

Per-listener:
- `concurrency: 3` (matches partition count for inventory and master
  topics).
- Manual ACK after successful TX commit.
- `DefaultErrorHandler` with backoff `[1s, 2s, 4s]` then DLT routing.

### 3.5 Failure Modes

| Scenario | Behavior |
|---|---|
| Broker unreachable on publish | Outbox publisher retries; `outbound_outbox.pending.count` grows; alerts at >100 |
| Broker partition leader election | Producer waits up to `delivery-timeout-ms`; transparent in steady state |
| Consumer poll returns stale leader | Auto-recovery on next rebalance; observable via `KafkaConsumer` metrics |
| Consumer hits unparseable record | Routed to DLT immediately; alert on `kafka.consumer.dlt.records.total > 0` |
| Consumer hits transient DB failure (`OptimisticLockingFailureException` on saga) | Retried 3 times then routed to DLT; usually saga state advances by then and re-poll absorbs as no-op via §4 of idempotency.md |
| Network partition between service and Kafka | Producer keeps outbox backlog; consumer pauses. Recovery is automatic on partition heal |
| Saga sweeper re-emits an event already published | Not a Kafka issue — sweeper writes a fresh outbox row; downstream consumer absorbs via state-machine guard |

### 3.6 No Distributed Transactions (T2)

- Outbox is local-DB-only. Kafka publish is a separate step that observes
  the committed outbox row. There is no XA transaction across DB and
  Kafka.
- Consumer commits offset only after the domain TX commits — at-least-once
  semantics are inherent.

---

## 4. PostgreSQL

### 4.1 Direction

Outbound (read + write). One logical DB per service —
`outbound_service_db`. Owned exclusively; no other service connects.

### 4.2 Connection

- HikariCP via Spring Boot.
- `spring.datasource.url=jdbc:postgresql://{host}:{port}/outbound_service_db`.
- Pool size: 20 connections (default), tunable via
  `spring.datasource.hikari.maximum-pool-size`.
- Connection timeout: 5s.
- Validation timeout: 5s.

### 4.3 Migrations

- Flyway, baseline V1 — see
  `apps/outbound-service/src/main/resources/db/migration/`.
- Naming: `V{n}__{description}.sql`.
- Repeatable: `R__{description}.sql` for views (none in v1).

### 4.4 Failure Modes

| Scenario | Behavior |
|---|---|
| DB connection pool exhausted | Request blocks up to `connection-timeout`; then 503 `SERVICE_UNAVAILABLE` |
| DB read replica lag (when using replica) | v1 reads from primary only; no replica involvement |
| DB master failover | Application reconnects via DNS / pgbouncer; brief 503 window. Outbox publisher resumes from where it stopped. Saga sweeper resumes on next tick |
| Migration failure on startup | Pod fails to start (CrashLoopBackOff). Manual rollback via Flyway `clean` + `migrate` |

### 4.5 No Direct ORM Access from Domain

Per Hexagonal architecture (`platform/architecture.md` and
`.claude/skills/backend/architecture/hexagonal/SKILL.md`): JPA entities
are package-private inside the persistence adapter. Domain models
(including `Order`, `OutboundSaga`, `Shipment`) are pure POJOs without
`@Entity` / `@Table` annotations.

---

## 5. Redis

### 5.1 Direction

Outbound (read + write). Used for:

- REST `Idempotency-Key` storage (24h TTL).
- (Future) Webhook ingest backpressure throttling — not v1.

### 5.2 Connection

- Spring Data Redis with Lettuce client.
- Single Redis instance in dev; sentinel cluster in staging/prod.
- Connection timeout: 2s.
- Command timeout: 1s.
- Key prefix convention:
  `outbound:idempotency:{method}:{path_hash}:{idempotency_key}`.

### 5.3 Failure Modes

| Scenario | Behavior (idempotency surface) |
|---|---|
| Redis unreachable during write | REST endpoint fails closed → 503 `SERVICE_UNAVAILABLE` (matches inventory-service / inbound-service convention) |
| Redis unreachable during read | Same |
| Redis evicts entry due to memory pressure | Treat as cache miss — request flows to use-case. Domain unique constraints (`order_no`, `outbound_saga.order_id`, `shipment.order_id`) backstop |

### 5.4 Choice of Failure Mode

`outbound-service` REST idempotency **fails closed** (matches inventory
and inbound). Justification: idempotency is a correctness boundary; a
missed dedupe lets a mutation execute twice. For outbound this is
particularly load-bearing — a duplicated `confirm-shipping` would
double-fire the saga's terminal step before the state-machine guard can
absorb it (Redis is the first line of defense; the state-machine guard is
the last).

---

## 6. Secret Manager

### 6.1 Direction

Outbound (read). Used for:

- Per-environment ERP order-webhook HMAC secrets (`erp-order-prod`,
  `erp-order-stg`, `erp-order-dr`).
- Future: outbound ERP API tokens (v2).

### 6.2 Provider

- v1 dev: env-var fallback (`ERP_ORDER_WEBHOOK_SECRET_<ENV>`) for local testing.
- v1 prod: AWS Secrets Manager (or equivalent — concrete provider chosen
  at deploy time).
- Refresh cadence: cached at boot; `SIGHUP`-style refresh via Actuator
  `RefreshScope` endpoint (admin-only).
- Rotation procedure: two-secret window — `current` and `previous` both
  acceptable during cut-over. Documented in
  [`specs/contracts/webhooks/erp-order-webhook.md`](../../contracts/webhooks/erp-order-webhook.md)
  § Security Notes.

### 6.3 Failure Modes

| Scenario | Behavior |
|---|---|
| Secret Manager unreachable at boot | Pod fails to start (no fallback in prod). Health check fails |
| Secret Manager unreachable at refresh | Old (cached) secret continues to work; refresh logs WARN. Ops triggers manual re-fetch |
| Secret value missing for declared `X-Erp-Source` | Webhook with that source gets 401 `WEBHOOK_SIGNATURE_INVALID` (no secret to compare against) |

---

## 7. Aggregated Resilience Policy

| Vendor | Timeout (connect / read) | Circuit Breaker | Retry (count, base, max) | Idempotency | Bulkhead | DLQ / Recovery |
|---|---|---|---|---|---|---|
| ERP webhook (in) | n/a (we receive) | n/a | n/a (ERP-side) | `X-Erp-Event-Id` 7d dedupe | n/a | inbox `FAILED` queue |
| Kafka producer | 5s / 30s | n/a (outbox absorbs) | 5 broker, exp-backoff | `eventId` (downstream) | Spring default | outbox stays unpublished |
| Kafka consumer | (broker session) | n/a | 3 in-process, [1,2,4]s | `eventId` 30d dedupe + saga state-machine guard | Spring default | `<topic>.DLT` |
| PostgreSQL | 5s / (statement) | n/a (failure → 5xx) | 0 (TX retry on 409 only) | n/a | HikariCP 20 | n/a |
| Redis | 2s / 1s | n/a (failure → 5xx) | 0 (fail-closed) | n/a | Lettuce default | n/a |
| Secret Manager | 3s / 5s | n/a (boot-only path) | 3, exp-backoff | n/a | n/a | n/a |

Bulkhead (`integration-heavy` I9): Kafka producer/consumer use Spring's
default thread pools, which are isolated from the HTTP server's request
threadpool. PostgreSQL has its own HikariCP pool. **No pool is shared**
across vendors.

---

## 8. Observability (Cross-Vendor Summary)

Per `rules/traits/integration-heavy.md` § Interaction with Common Rules:

| Metric | Vendor | Description |
|---|---|---|
| `outbound.webhook.received.total{result}` | ERP-in | Counter of webhook outcomes |
| `outbound.webhook.processing.lag.seconds` | ERP-in | Histogram of inbox-receipt-to-applied lag |
| `outbound.webhook.processing.failure.total{reason}` | ERP-in | Counter by domain failure code |
| `outbound.webhook.inbox.pending.count` | ERP-in | Gauge of PENDING inbox rows |
| `outbound.webhook.dedupe.hit.rate` | ERP-in | Computed metric |
| `outbound.outbox.pending.count` | Kafka-out | Gauge of unpublished outbox rows |
| `outbound.outbox.lag.seconds` | Kafka-out | Histogram of oldest unpublished row age |
| `outbound.outbox.publish.failure.total` | Kafka-out | Counter of publish failures |
| `outbound.consumer.received.total{topic, outcome}` | Kafka-in | Counter by topic + applied/duplicate/failed |
| `outbound.consumer.dlt.records.total{topic}` | Kafka-in | Counter; alerts at >0 |
| `outbound.event.dedupe.hit.rate` | Kafka-in | Computed metric |
| `outbound.event.dedupe.table.size` | Kafka-in | Gauge of dedupe row count |

Logs (structured JSON, INFO level — see
[`idempotency.md`](idempotency.md) § Observability for the precise event
keys).

Tracing (OTel):

- Webhook ingest creates a root span `webhook.erp.order.ingest` with
  `event.id` and `source` attributes.
- Background processor inherits the trace via `traceId` carried through
  the `erp_order_webhook_inbox` row.
- Outbound Kafka publish carries the trace via `traceparent` Kafka
  header.

---

## 9. Test Suite (per `integration-heavy` I10)

All external-integration paths must have failure-mode tests using fakes:

| Path | Test framework |
|---|---|
| ERP webhook ingest | Spring Boot integration test (controller called directly), no real ERP |
| Background processor | Testcontainers PostgreSQL + fake `MasterReadModel` snapshots |
| Kafka producer (outbox) | Testcontainers Kafka |
| Kafka consumer (inventory replies + master snapshots) | Testcontainers Kafka, poison-record case routes to DLT |
| Saga consumer + state-machine guard | Testcontainers Kafka — feed same event with fresh eventIds, assert no double-transition |
| Redis idempotency | Testcontainers Redis |
| Secret Manager | Mock `SecretRetriever` interface (port adapter) |

Tests for production vendor SDKs (e.g., AWS Secrets Manager client) use
LocalStack or equivalent — the service code does not reach real AWS
during test runs.

---

## 10. Per-Vendor Runbook Pointers

When an integration breaks in production, ops follows the per-vendor
runbook (stored in `docs/runbooks/<vendor>.md` of the deploying repo, NOT
in this spec):

- ERP webhook outage → `docs/runbooks/erp-order-webhook.md`
  - Drains PENDING inbox, escalates to ERP integration owner
- Kafka cluster outage → `docs/runbooks/kafka.md`
- PostgreSQL primary failover → `docs/runbooks/postgres.md`

These are operational documents, not specs. Linked here for completeness.

---

## 11. Not In v1

- Outbound webhook to ERP (push order ack)
- mTLS instead of HMAC for inbound webhook
- Multi-tenant ERP (one secret per customer-supplier instead of per-env)
- Carrier dispatch / rating / tracking — owned by the scm `logistics-service`
  (ADR-MONO-053 / ADR-MONO-052), not outbound-service
- Scanner / RFID adapter for picking confirmation
- Notification provider integration (handled by `notification-service`,
  not outbound-service)

---

## References

- `specs/services/outbound-service/architecture.md` — Dependencies,
  Webhook Reception
- `specs/contracts/webhooks/erp-order-webhook.md` — wire-level webhook
  contract
- `specs/services/outbound-service/idempotency.md` — REST + webhook +
  Kafka + saga dedupe strategies
- `specs/services/outbound-service/sagas/outbound-saga.md` — saga state
  transitions; failure paths
- `specs/contracts/events/outbound-events.md` — outbound Kafka schemas
- `specs/services/inbound-service/external-integrations.md` — sibling
  reference (same ERP webhook + Kafka + Postgres + Redis + Secret Manager
  structure)
- `rules/traits/integration-heavy.md` — I6, I10 (ERP webhook reception)
- `platform/api-gateway-policy.md` — webhook routing tier
- `platform/security-rules.md` — Secret Manager policy
- `platform/observability.md` — required metrics for integrations
- `messaging/outbox-pattern/SKILL.md`
- `messaging/idempotent-consumer/SKILL.md`
